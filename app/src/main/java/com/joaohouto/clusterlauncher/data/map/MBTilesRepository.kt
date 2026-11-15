package com.joaohouto.clusterlauncher.data.map

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.system.Os
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Representa um bloco de mapa posicionado na tela com coordenadas relativas e absolutas.
 */
data class RenderableTile(
    val bitmap: Bitmap,
    val screenX: Float,
    val screenY: Float,
    val relX: Float = 0f,
    val relY: Float = 0f,
    val tileX: Int = 0,
    val tileY: Int = 0,
    val zoom: Int = 0
)

/**
 * Informações sobre um arquivo MBTiles salvo no dispositivo.
 */
data class MapFileInfo(
    val file: File,
    val displayName: String,
    val sizeMb: Float,
    val formattedDate: String,
    val isActive: Boolean,
    val isVector: Boolean = false
)

/**
 * Estado do mapa offline e lista de arquivos disponíveis.
 */
data class OfflineMapState(
    val isMapLoaded: Boolean = false,
    val activeFile: File? = null,
    val mapFileName: String? = null,
    val mapDisplayName: String? = null,
    val minZoom: Int = 0,
    val maxZoom: Int = 22,
    val defaultZoom: Int = 17,
    val isVectorMap: Boolean = false,
    val centerLat: Double? = null,
    val centerLon: Double? = null,
    val availableMaps: List<MapFileInfo> = emptyList()
)

private data class CachedMbtilesMeta(
    val lastModified: Long,
    val length: Long,
    val displayName: String,
    val isVector: Boolean,
    val minZoom: Int,
    val maxZoom: Int,
    val centerLat: Double?,
    val centerLon: Double?
)

/**
 * Repositório para gerenciar múltiplos mapas MBTiles e carregar blocos usando SQLite nativo do Android.
 * Suporta leitura direta do arquivo original no armazenamento (sem duplicar arquivos grandes de 2GB+).
 */
object MBTilesRepository {

    private const val TAG = "MBTilesRepository"
    private const val MAX_CACHE_ENTRIES = 64
    private const val PREFS_NAME = "cluster_map_prefs"
    private const val PREF_ACTIVE_MAP_NAME = "active_map_file_name"
    private const val PREF_ACTIVE_MAP_PATH = "active_map_file_path"
    private const val PREF_LINKED_MAP_PATHS = "linked_external_map_paths"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _mapState = MutableStateFlow(OfflineMapState())
    val mapState: StateFlow<OfflineMapState> = _mapState.asStateFlow()

    private var activeDatabase: SQLiteDatabase? = null
    private var activeFile: File? = null
    private val metaCache = mutableMapOf<String, CachedMbtilesMeta>()

    private val memoryCache = object : LruCache<String, Bitmap>(MAX_CACHE_ENTRIES) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    /**
     * Inicializa o repositório, descobre todos os arquivos .mbtiles e abre o mapa ativo.
     */
    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        try {
            refreshAvailableMaps(context)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao inicializar MBTilesRepository", e)
        }
    }

    private fun isAppPrivateMapsDir(context: Context, file: File): Boolean {
        val extDir = context.getExternalFilesDir("maps")?.absolutePath
        val intDir = File(context.filesDir, "maps").absolutePath
        val parentPath = file.parentFile?.absolutePath ?: return false
        return parentPath == extDir || parentPath == intDir
    }

    /**
     * Escaneia pastas do sistema e caminhos vinculados para encontrar todos os arquivos .mbtiles válidos,
     * removendo cópias duplicadas na pasta de dados privada do app quando o arquivo original externo existir.
     */
    fun scanAvailableFiles(context: Context): List<File> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val linkedPaths = prefs.getStringSet(PREF_LINKED_MAP_PATHS, emptySet()).orEmpty().toMutableSet()

        val externalSearchDirs = mutableListOf<File>()
        try {
            val externalStorage = Environment.getExternalStorageDirectory()
            externalSearchDirs.add(File(externalStorage, "Maps"))
            externalSearchDirs.add(File(externalStorage, "maps"))
            externalSearchDirs.add(File(externalStorage, "Download"))
            externalSearchDirs.add(File(externalStorage, "Downloads"))
            externalSearchDirs.add(externalStorage)
            val downloadPublic = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadPublic != null) externalSearchDirs.add(downloadPublic)
        } catch (_: Exception) {}

        val externalFiles = mutableListOf<File>()
        for (path in linkedPaths) {
            val f = File(path)
            if (f.exists() && f.isFile && f.name.endsWith(".mbtiles", ignoreCase = true) && isValidMbtiles(f)) {
                externalFiles.add(f)
            }
        }

        for (dir in externalSearchDirs) {
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles { _, name ->
                    name.endsWith(".mbtiles", ignoreCase = true) && !name.startsWith("download_tmp")
                }
                if (files != null) {
                    externalFiles.addAll(files.filter { isValidMbtiles(it) })
                }
            }
        }

        val distinctExternal = externalFiles.distinctBy { file -> runCatching { file.canonicalPath }.getOrElse { file.absolutePath } }

        val privateDirs = listOfNotNull(
            context.getExternalFilesDir("maps"),
            File(context.filesDir, "maps")
        )
        val privateFiles = mutableListOf<File>()
        for (dir in privateDirs) {
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles { _, name ->
                    name.endsWith(".mbtiles", ignoreCase = true) && !name.startsWith("download_tmp")
                }
                if (files != null) {
                    for (privFile in files) {
                        // Se existe um arquivo externo grande (>= 10 MB) com exatamente o mesmo tamanho,
                        // esta cópia dentro dos dados do app é uma duplicata criada por importação antiga:
                        // removemos a duplicata para liberar armazenamento!
                        val duplicateOfExternal = privFile.length() >= 10L * 1024L * 1024L &&
                            distinctExternal.any { ext ->
                                ext.absolutePath != privFile.absolutePath && ext.length() == privFile.length()
                            }
                        if (duplicateOfExternal) {
                            Log.i(TAG, "Removendo cópia duplicada em dados do app para liberar espaço: ${privFile.absolutePath}")
                            if (activeFile?.absolutePath == privFile.absolutePath) {
                                closeActiveDbOnly()
                            }
                            runCatching { privFile.delete() }
                        } else if (isValidMbtiles(privFile)) {
                            privateFiles.add(privFile)
                        }
                    }
                }
            }
        }

        val allFiles = (distinctExternal + privateFiles)
            .distinctBy { file -> runCatching { file.canonicalPath }.getOrElse { file.absolutePath } }
            .sortedByDescending { it.lastModified() }

        return allFiles
    }

    /**
     * Atualiza a lista de mapas baixados e abre o mapa selecionado.
     */
    suspend fun refreshAvailableMaps(context: Context) = withContext(Dispatchers.IO) {
        val files = scanAvailableFiles(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedActivePath = prefs.getString(PREF_ACTIVE_MAP_PATH, null)
        val savedActiveName = prefs.getString(PREF_ACTIVE_MAP_NAME, null)

        val targetFile = when {
            activeFile != null && activeFile!!.exists() && files.any { it.absolutePath == activeFile!!.absolutePath } -> activeFile
            !savedActivePath.isNullOrEmpty() -> files.find { it.absolutePath == savedActivePath }
                ?: files.find { it.name == savedActiveName }
                ?: files.firstOrNull()
            !savedActiveName.isNullOrEmpty() -> files.find { it.name == savedActiveName } ?: files.firstOrNull()
            else -> files.firstOrNull()
        }

        if (targetFile != null && targetFile.exists()) {
            val isAlreadyOpen = activeFile?.absolutePath == targetFile.absolutePath &&
                (activeDatabase?.isOpen == true || _mapState.value.isMapLoaded)
            if (!isAlreadyOpen) {
                openMapFileInternal(targetFile)
            }
        } else {
            close()
        }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val mapInfoList = files.map { file ->
            val meta = getOrReadMetadata(file)
            val sizeMb = file.length() / (1024f * 1024f)
            val isActive = (activeFile?.absolutePath == file.absolutePath)
            MapFileInfo(
                file = file,
                displayName = meta.displayName,
                sizeMb = sizeMb,
                formattedDate = dateFormat.format(Date(file.lastModified())),
                isActive = isActive,
                isVector = meta.isVector
            )
        }

        _mapState.value = _mapState.value.copy(
            availableMaps = mapInfoList
        )
    }

    /**
     * Alterna para o arquivo de mapa escolhido pelo usuário.
     */
    fun selectMap(context: Context, file: File) {
        scope.launch {
            if (!file.exists()) return@launch

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(PREF_ACTIVE_MAP_NAME, file.name)
                .putString(PREF_ACTIVE_MAP_PATH, file.absolutePath)
                .apply()

            openMapFileInternal(file)
            refreshAvailableMaps(context)
        }
    }

    /**
     * Exclui um arquivo de mapa específico (se estiver na pasta privada do app) ou desvincula (se for externo).
     */
    fun deleteMap(context: Context, file: File) {
        scope.launch {
            if (activeFile?.absolutePath == file.absolutePath) {
                close()
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val linked = prefs.getStringSet(PREF_LINKED_MAP_PATHS, emptySet()).orEmpty().toMutableSet()
            linked.remove(file.absolutePath)

            val editor = prefs.edit().putStringSet(PREF_LINKED_MAP_PATHS, linked)
            if (prefs.getString(PREF_ACTIVE_MAP_NAME, null) == file.name) {
                editor.remove(PREF_ACTIVE_MAP_NAME)
                editor.remove(PREF_ACTIVE_MAP_PATH)
            }
            editor.apply()

            // Só exclui fisicamente o arquivo se ele pertencer à pasta interna/privada do app
            // ou se o usuário deletou explicitamente. Se estiver na pasta privada do app, libera os GBs imediatamente.
            if (isAppPrivateMapsDir(context, file) && file.exists()) {
                file.delete()
            } else if (file.exists()) {
                file.delete()
            }

            metaCache.remove(file.absolutePath)
            refreshAvailableMaps(context)
        }
    }

    fun deleteActiveMap(context: Context) {
        activeFile?.let { file ->
            deleteMap(context, file)
        }
    }

    enum class TileFormat {
        RASTER,
        VECTOR_PBF,
        UNKNOWN
    }

    /**
     * Valida se o arquivo informado é um banco SQLite válido contendo a tabela ou visão obrigatória 'tiles'.
     */
    fun isValidMbtiles(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        synchronized(metaCache) {
            val cached = metaCache[file.absolutePath]
            if (cached != null && cached.lastModified == file.lastModified() && cached.length == file.length()) {
                return true
            }
        }
        var db: SQLiteDatabase? = null
        return try {
            db = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
            val cursor = db.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type IN ('table', 'view') AND name='tiles' LIMIT 1",
                null
            )
            val hasTiles = cursor.moveToFirst()
            cursor.close()
            hasTiles
        } catch (_: Exception) {
            false
        } finally {
            try {
                db?.close()
            } catch (_: Exception) {}
        }
    }

    private fun getOrReadMetadata(file: File): CachedMbtilesMeta {
        synchronized(metaCache) {
            val cached = metaCache[file.absolutePath]
            if (cached != null && cached.lastModified == file.lastModified() && cached.length == file.length()) {
                return cached
            }
        }

        var displayName: String? = null
        var formatStr: String? = null
        var minZoom = 0
        var maxZoom = 14
        var centerLat: Double? = null
        var centerLon: Double? = null
        var isVector = false

        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
            try {
                val cursor = db.rawQuery("SELECT name, value FROM metadata", null)
                var boundsStr: String? = null
                var centerStr: String? = null
                while (cursor.moveToNext()) {
                    val key = cursor.getString(0)?.lowercase()?.trim() ?: continue
                    val value = cursor.getString(1)?.trim() ?: continue
                    when (key) {
                        "name" -> if (value.isNotBlank()) displayName = value
                        "format" -> formatStr = value.lowercase()
                        "minzoom" -> minZoom = value.toIntOrNull() ?: minZoom
                        "maxzoom" -> maxZoom = value.toIntOrNull() ?: maxZoom
                        "center" -> centerStr = value
                        "bounds" -> boundsStr = value
                    }
                }
                cursor.close()

                // Parseia 'center' (lon,lat[,zoom]) ou 'bounds' (minLon,minLat,maxLon,maxLat)
                if (!centerStr.isNullOrEmpty()) {
                    val parts = centerStr.split(",").mapNotNull { it.trim().toDoubleOrNull() }
                    if (parts.size >= 2) {
                        val lon = parts[0]
                        val lat = parts[1]
                        if (lat in -90.0..90.0 && lon in -180.0..180.0) {
                            centerLat = lat
                            centerLon = lon
                        }
                    }
                }
                if ((centerLat == null || centerLon == null) && !boundsStr.isNullOrEmpty()) {
                    val parts = boundsStr.split(",").mapNotNull { it.trim().toDoubleOrNull() }
                    if (parts.size >= 4) {
                        val minLon = parts[0]
                        val minLat = parts[1]
                        val maxLon = parts[2]
                        val maxLat = parts[3]
                        val lat = (minLat + maxLat) / 2.0
                        val lon = (minLon + maxLon) / 2.0
                        if (lat in -90.0..90.0 && lon in -180.0..180.0) {
                            centerLat = lat
                            centerLon = lon
                        }
                    }
                }
            } catch (_: Exception) {}

            isVector = when (formatStr) {
                "pbf", "mvt" -> true
                "png", "jpg", "jpeg", "webp" -> false
                else -> detectTileFormatFromFirstBlob(db) == TileFormat.VECTOR_PBF
            }
        } catch (_: Exception) {
        } finally {
            try {
                db?.close()
            } catch (_: Exception) {}
        }

        val entry = CachedMbtilesMeta(
            lastModified = file.lastModified(),
            length = file.length(),
            displayName = displayName ?: cleanFileName(file.name),
            isVector = isVector,
            minZoom = minZoom.coerceIn(0, 18),
            maxZoom = maxZoom.coerceIn(minZoom.coerceIn(0, 18), 18),
            centerLat = centerLat,
            centerLon = centerLon
        )
        synchronized(metaCache) {
            metaCache[file.absolutePath] = entry
        }
        return entry
    }

    private fun detectTileFormatFromFirstBlob(db: SQLiteDatabase): TileFormat {
        return try {
            val tileCursor = db.rawQuery(
                "SELECT tile_data FROM tiles LIMIT 1",
                null
            )
            val fmt = if (tileCursor.moveToFirst()) {
                val blob = tileCursor.getBlob(0)
                if (blob != null && blob.isNotEmpty()) {
                    val isPng = blob.size >= 4 && (blob[0].toInt() and 0xFF == 0x89) && (blob[1].toInt() and 0xFF == 0x50)
                    val isJpg = blob.size >= 2 && (blob[0].toInt() and 0xFF == 0xFF) && (blob[1].toInt() and 0xFF == 0xD8)
                    val isWebp = blob.size >= 12 && (blob[0].toInt().toChar() == 'R') && (blob[1].toInt().toChar() == 'I')
                    val isGzip = blob.size >= 2 && (blob[0].toInt() and 0xFF == 0x1F) && (blob[1].toInt() and 0xFF == 0x8B)
                    when {
                        isPng || isJpg || isWebp -> TileFormat.RASTER
                        isGzip -> TileFormat.VECTOR_PBF
                        else -> TileFormat.VECTOR_PBF
                    }
                } else {
                    TileFormat.UNKNOWN
                }
            } else {
                TileFormat.UNKNOWN
            }
            tileCursor.close()
            fmt
        } catch (_: Exception) {
            TileFormat.UNKNOWN
        }
    }

    fun checkMbtilesTileFormat(file: File): TileFormat {
        if (!file.exists() || file.length() == 0L) return TileFormat.UNKNOWN
        val meta = getOrReadMetadata(file)
        return if (meta.isVector) TileFormat.VECTOR_PBF else TileFormat.RASTER
    }

    /**
     * Resolve o arquivo físico real no armazenamento a partir de uma URI do SAF (Storage Access Framework),
     * evitando copiar/duplicar arquivos gigantescos (ex: 2GB+) para a pasta privada do app.
     */
    private fun resolveDirectFileFromUri(context: Context, uri: Uri): File? {
        // 1. Esquema file:// direto
        if (uri.scheme.equals("file", ignoreCase = true)) {
            val path = uri.path
            if (!path.isNullOrEmpty()) {
                val f = File(path)
                if (f.exists() && f.canRead()) return f
            }
        }

        // 2. Resolução via symlink do descritor de arquivo Linux (/proc/self/fd/<fd>)
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val realPath = Os.readlink("/proc/self/fd/${pfd.fd}")
                if (!realPath.isNullOrEmpty() && !realPath.startsWith("/proc/")) {
                    val candidate = File(realPath)
                    if (candidate.exists() && candidate.isFile && candidate.canRead()) {
                        return candidate
                    }
                    // Caso o caminho retornado seja /mnt/user/0/emulated/0/... ou /data/media/0/..., converte para /storage/emulated/0/...
                    val normalized = realPath
                        .replaceFirst(Regex("^/mnt/user/\\d+/emulated/0/"), "/storage/emulated/0/")
                        .replaceFirst(Regex("^/mnt/runtime/[^/]+/emulated/0/"), "/storage/emulated/0/")
                        .replaceFirst(Regex("^/data/media/0/"), "/storage/emulated/0/")
                    val normFile = File(normalized)
                    if (normFile.exists() && normFile.isFile && normFile.canRead()) {
                        return normFile
                    }
                }
            }
        } catch (_: Exception) {}

        // 3. Resolução via DocumentsContract (com.android.externalstorage.documents / downloads)
        try {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                when (uri.authority) {
                    "com.android.externalstorage.documents" -> {
                        val split = docId.split(":", limit = 2)
                        if (split.size == 2) {
                            val type = split[0]
                            val relPath = split[1]
                            if (type.equals("primary", ignoreCase = true)) {
                                val f = File(Environment.getExternalStorageDirectory(), relPath)
                                if (f.exists() && f.canRead()) return f
                            } else {
                                val candidates = listOf(
                                    File("/storage/$type/$relPath"),
                                    File("/mnt/media_rw/$type/$relPath")
                                )
                                candidates.firstOrNull { it.exists() && it.canRead() }?.let { return it }
                            }
                        }
                    }
                    "com.android.providers.downloads.documents" -> {
                        if (docId.startsWith("raw:")) {
                            val f = File(docId.removePrefix("raw:"))
                            if (f.exists() && f.canRead()) return f
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 4. Fallback: busca pelo nome e tamanho exato nas pastas padrão (Download, Maps, etc.)
        try {
            var displayName: String? = null
            var fileSize: Long = -1L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx != -1) displayName = cursor.getString(nameIdx)
                    if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) fileSize = cursor.getLong(sizeIdx)
                }
            }
            if (!displayName.isNullOrEmpty()) {
                val ext = Environment.getExternalStorageDirectory()
                val dirs = listOfNotNull(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    File(ext, "Download"),
                    File(ext, "Downloads"),
                    File(ext, "Maps"),
                    File(ext, "maps"),
                    ext
                )
                for (d in dirs) {
                    val candidate = File(d, displayName!!)
                    if (candidate.exists() && candidate.canRead() && (fileSize <= 0L || candidate.length() == fileSize)) {
                        return candidate
                    }
                }
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * Importa ou vincula um arquivo MBTiles selecionado pelo usuário através do SAF.
     * Se o arquivo já estiver acessível no armazenamento do dispositivo, usa-o diretamente no local
     * sem duplicar espaço nos dados do aplicativo.
     */
    suspend fun importMbtilesFromUri(context: Context, uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        // 1. Tenta usar o arquivo diretamente do local original sem copiar/duplicar
        val directFile = resolveDirectFileFromUri(context, uri)
        if (directFile != null && directFile.exists() && directFile.canRead()) {
            if (!isValidMbtiles(directFile)) {
                return@withContext Result.failure(Exception("O arquivo selecionado não é um banco MBTiles SQLite válido."))
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val linked = prefs.getStringSet(PREF_LINKED_MAP_PATHS, emptySet()).orEmpty().toMutableSet()
            linked.add(directFile.absolutePath)
            prefs.edit().putStringSet(PREF_LINKED_MAP_PATHS, linked).apply()

            // Remove eventuais cópias duplicadas antigas que estavam na pasta privada do app
            val privateDirs = listOfNotNull(
                context.getExternalFilesDir("maps"),
                File(context.filesDir, "maps")
            )
            for (dir in privateDirs) {
                if (dir.exists() && dir.isDirectory && dir.absolutePath != directFile.parentFile?.absolutePath) {
                    dir.listFiles()?.forEach { privFile ->
                        if (privFile.isFile && (privFile.name == directFile.name || privFile.length() == directFile.length())) {
                            Log.i(TAG, "Limpando cópia duplicada de ${privFile.name} da pasta interna do app")
                            runCatching { privFile.delete() }
                        }
                    }
                }
            }

            selectMap(context, directFile)
            Log.i(TAG, "Mapa MBTiles vinculado diretamente (0 bytes duplicados): ${directFile.absolutePath}")
            return@withContext Result.success(directFile)
        }

        // 2. Fallback: caso o URI venha de uma nuvem/provedor sem arquivo local acessível, copia para a pasta de mapas
        var destinationFile: File? = null
        try {
            var originalName: String? = null
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            originalName = cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Não foi possível resolver nome do arquivo do URI", e)
            }

            var fileName = originalName ?: "imported_${System.currentTimeMillis()}.mbtiles"
            if (!fileName.endsWith(".mbtiles", ignoreCase = true)) {
                fileName = "$fileName.mbtiles"
            }

            val mapsDir = context.getExternalFilesDir("maps") ?: File(context.filesDir, "maps")
            if (!mapsDir.exists()) mapsDir.mkdirs()

            // Se já existir arquivo de mesmo nome na pasta, sobrescreve ao invés de criar múltiplas cópias
            val targetFile = File(mapsDir, fileName)
            if (targetFile.exists()) {
                if (activeFile?.absolutePath == targetFile.absolutePath) {
                    closeActiveDbOnly()
                }
                targetFile.delete()
            }
            destinationFile = targetFile

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                targetFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return@withContext Result.failure(Exception("Não foi possível acessar o arquivo selecionado."))

            if (!isValidMbtiles(targetFile)) {
                targetFile.delete()
                return@withContext Result.failure(Exception("O arquivo selecionado não é um banco MBTiles SQLite válido."))
            }

            selectMap(context, targetFile)
            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao importar MBTiles de URI: $uri", e)
            destinationFile?.let { if (it.exists()) it.delete() }
            Result.failure(e)
        }
    }

    private fun cleanFileName(fileName: String): String {
        return fileName
            .removeSuffix(".mbtiles")
            .replace("map_", "")
            .replace("_", " ")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    fun openMapFile(file: File) {
        openMapFileInternal(file)
    }

    private fun closeActiveDbOnly() {
        try {
            activeDatabase?.close()
        } catch (_: Exception) {}
        activeDatabase = null
        memoryCache.evictAll()
    }

    private fun openMapFileInternal(file: File) {
        try {
            closeActiveDbOnly()

            val meta = getOrReadMetadata(file)
            activeFile = file

            if (!meta.isVector) {
                activeDatabase = SQLiteDatabase.openDatabase(
                    file.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
                )
            } else {
                LocalMBTilesServer.setSource(file, minZoom = meta.minZoom, maxZoom = meta.maxZoom)
                LocalMBTilesServer.start()
            }

            val effectiveMaxZoom = maxOf(meta.maxZoom + 4, 21).coerceAtMost(22)
            val targetZoom = 17.coerceIn(meta.minZoom, effectiveMaxZoom)

            _mapState.value = _mapState.value.copy(
                isMapLoaded = true,
                activeFile = file,
                mapFileName = file.name,
                mapDisplayName = meta.displayName,
                minZoom = meta.minZoom,
                maxZoom = effectiveMaxZoom,
                defaultZoom = targetZoom,
                isVectorMap = meta.isVector,
                centerLat = meta.centerLat,
                centerLon = meta.centerLon
            )
            Log.i(
                TAG,
                "Mapa MBTiles ativo: ${file.name} (Tipo: ${if (meta.isVector) "VETORIAL" else "RASTER"}, " +
                    "Zoom nativo: ${meta.minZoom}..${meta.maxZoom}, Centro: ${meta.centerLat},${meta.centerLon})"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao abrir banco MBTiles: ${file.absolutePath}", e)
            _mapState.value = _mapState.value.copy(isMapLoaded = false, activeFile = null)
        }
    }

    /**
     * Obtém os blocos visíveis ao redor da coordenada do veículo.
     */
    suspend fun getVisibleTiles(
        lat: Double,
        lon: Double,
        zoom: Int,
        viewWidth: Float,
        viewHeight: Float,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        bufferPx: Float = 0f
    ): List<RenderableTile> = withContext(Dispatchers.IO) {
        val db = activeDatabase ?: return@withContext emptyList()
        val tileSize = SlippyMapUtils.TILE_SIZE

        val xExact = SlippyMapUtils.lonToTileX(lon, zoom)
        val yExact = SlippyMapUtils.latToTileY(lat, zoom)

        val centerTileX = floor(xExact).toInt()
        val centerTileY = floor(yExact).toInt()

        val subOffsetX = ((xExact - centerTileX) * tileSize).toFloat()
        val subOffsetY = ((yExact - centerTileY) * tileSize).toFloat()

        val centerScreenX = (viewWidth / 2f) - subOffsetX + offsetX
        val centerScreenY = (viewHeight / 2f) - subOffsetY + offsetY

        val minDx = floor((0f - bufferPx - centerScreenX) / tileSize).toInt()
        val maxDx = ceil((viewWidth + bufferPx - centerScreenX) / tileSize).toInt()
        val minDy = floor((0f - bufferPx - centerScreenY) / tileSize).toInt()
        val maxDy = ceil((viewHeight + bufferPx - centerScreenY) / tileSize).toInt()

        val tiles = mutableListOf<RenderableTile>()

        for (dx in minDx..maxDx) {
            for (dy in minDy..maxDy) {
                val tileX = centerTileX + dx
                val tileY = centerTileY + dy

                val screenX = centerScreenX + (dx * tileSize)
                val screenY = centerScreenY + (dy * tileSize)
                val relX = ((tileX - xExact) * tileSize).toFloat()
                val relY = ((tileY - yExact) * tileSize).toFloat()

                val bitmap = loadTileBitmap(db, zoom, tileX, tileY)
                if (bitmap != null) {
                    tiles.add(
                        RenderableTile(
                            bitmap = bitmap,
                            screenX = screenX,
                            screenY = screenY,
                            relX = relX,
                            relY = relY,
                            tileX = tileX,
                            tileY = tileY,
                            zoom = zoom
                        )
                    )
                }
            }
        }

        tiles
    }

    private fun loadTileBitmap(db: SQLiteDatabase, zoom: Int, tileX: Int, tileY: Int): Bitmap? {
        val cacheKey = "$zoom/$tileX/$tileY"
        val cached = memoryCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        val tmsY = (1 shl zoom) - 1 - tileY
        try {
            val cursor = db.rawQuery(
                "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ? LIMIT 1",
                arrayOf(zoom.toString(), tileX.toString(), tmsY.toString())
            )
            if (cursor.moveToFirst()) {
                val blob = cursor.getBlob(0)
                cursor.close()
                if (blob != null && blob.isNotEmpty()) {
                    val bitmap = BitmapFactory.decodeByteArray(blob, 0, blob.size)
                    if (bitmap != null) {
                        memoryCache.put(cacheKey, bitmap)
                        return bitmap
                    }
                }
            } else {
                cursor.close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao ler bloco ($zoom, $tileX, $tileY)", e)
        }

        for (delta in 1..5) {
            val parentZoom = zoom - delta
            if (parentZoom < 0) break
            val parentX = tileX shr delta
            val parentY = tileY shr delta
            val parentBitmap = loadTileBitmap(db, parentZoom, parentX, parentY) ?: continue

            val factor = 1 shl delta
            val subWidth = parentBitmap.width / factor
            val subHeight = parentBitmap.height / factor
            if (subWidth <= 0 || subHeight <= 0) continue

            val subX = (tileX and (factor - 1)) * subWidth
            val subY = (tileY and (factor - 1)) * subHeight

            try {
                val cropped = Bitmap.createBitmap(parentBitmap, subX, subY, subWidth, subHeight)
                val upscaled = Bitmap.createScaledBitmap(cropped, 256, 256, true)
                if (cropped != upscaled && !cropped.isRecycled && cropped != parentBitmap) {
                    cropped.recycle()
                }
                memoryCache.put(cacheKey, upscaled)
                return upscaled
            } catch (e: Exception) {
                Log.w(TAG, "Falha no upscale do bloco overzoom ($zoom, $tileX, $tileY)", e)
            }
        }

        return null
    }

    /**
     * Fecha o banco de dados aberto.
     */
    fun close() {
        closeActiveDbOnly()
        activeFile = null
        LocalMBTilesServer.setSource(null)
        _mapState.value = _mapState.value.copy(
            isMapLoaded = false,
            activeFile = null,
            mapFileName = null,
            mapDisplayName = null,
            isVectorMap = false,
            centerLat = null,
            centerLon = null
        )
    }

    fun getActiveFile(): File? = activeFile
}
