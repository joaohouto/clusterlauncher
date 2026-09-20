package com.joaohouto.clusterlauncher.data.map

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
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
 * Representa um bloco de mapa posicionado na tela.
 */
data class RenderableTile(
    val bitmap: Bitmap,
    val screenX: Float,
    val screenY: Float
)

/**
 * Informações sobre um arquivo MBTiles salvo no dispositivo.
 */
data class MapFileInfo(
    val file: File,
    val displayName: String,
    val sizeMb: Float,
    val formattedDate: String,
    val isActive: Boolean
)

/**
 * Estado do mapa offline e lista de arquivos disponíveis.
 */
data class OfflineMapState(
    val isMapLoaded: Boolean = false,
    val mapFileName: String? = null,
    val mapDisplayName: String? = null,
    val minZoom: Int = 10,
    val maxZoom: Int = 20,
    val defaultZoom: Int = 19,
    val availableMaps: List<MapFileInfo> = emptyList()
)

/**
 * Repositório para gerenciar múltiplos mapas MBTiles e carregar blocos usando SQLite nativo do Android.
 * Suporta alternar entre múltiplos mapas baixados e cache LRU estrito (~6MB RAM max).
 */
object MBTilesRepository {

    private const val TAG = "MBTilesRepository"
    private const val MAX_CACHE_ENTRIES = 24
    private const val PREFS_NAME = "cluster_map_prefs"
    private const val PREF_ACTIVE_MAP_NAME = "active_map_file_name"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _mapState = MutableStateFlow(OfflineMapState())
    val mapState: StateFlow<OfflineMapState> = _mapState.asStateFlow()

    private var activeDatabase: SQLiteDatabase? = null
    private var activeFile: File? = null

    // Cache LRU em memória (apenas 24 bitmaps de 256x256)
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
            _mapState.value = OfflineMapState(isMapLoaded = false)
        }
    }

    /**
     * Escaneia pastas do sistema para encontrar todos os arquivos .mbtiles válidos.
     */
    fun scanAvailableFiles(context: Context): List<File> {
        val searchDirs = mutableListOf<File>()

        context.getExternalFilesDir("maps")?.let { searchDirs.add(it) }
        searchDirs.add(File(context.filesDir, "maps"))

        try {
            val externalStorage = Environment.getExternalStorageDirectory()
            searchDirs.add(File(externalStorage, "Maps"))
            searchDirs.add(File(externalStorage, "maps"))
        } catch (_: Exception) {}

        val foundFiles = mutableListOf<File>()
        for (dir in searchDirs) {
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles { _, name ->
                    name.endsWith(".mbtiles", ignoreCase = true) && !name.startsWith("download_tmp")
                }
                if (files != null) {
                    foundFiles.addAll(files)
                }
            }
        }

        return foundFiles.distinctBy { it.canonicalPath }.sortedByDescending { it.lastModified() }
    }

    /**
     * Atualiza a lista de mapas baixados e abre o mapa selecionado.
     */
    suspend fun refreshAvailableMaps(context: Context) = withContext(Dispatchers.IO) {
        val files = scanAvailableFiles(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedActiveName = prefs.getString(PREF_ACTIVE_MAP_NAME, null)

        // Determina qual arquivo deve ser aberto
        val targetFile = when {
            activeFile != null && activeFile!!.exists() -> activeFile
            !savedActiveName.isNullOrEmpty() -> files.find { it.name == savedActiveName } ?: files.firstOrNull()
            else -> files.firstOrNull()
        }

        if (targetFile != null && targetFile.exists()) {
            if (activeFile?.absolutePath != targetFile.absolutePath || activeDatabase == null) {
                openMapFileInternal(targetFile)
            }
        } else {
            close()
        }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val mapInfoList = files.map { file ->
            val sizeMb = file.length() / (1024f * 1024f)
            val displayName = readMapDisplayName(file)
            val isActive = (activeFile?.name == file.name)
            MapFileInfo(
                file = file,
                displayName = displayName,
                sizeMb = sizeMb,
                formattedDate = dateFormat.format(Date(file.lastModified())),
                isActive = isActive
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
            prefs.edit().putString(PREF_ACTIVE_MAP_NAME, file.name).apply()

            openMapFileInternal(file)
            refreshAvailableMaps(context)
        }
    }

    /**
     * Exclui um arquivo de mapa específico e libera recursos caso seja o mapa ativo.
     */
    fun deleteMap(context: Context, file: File) {
        scope.launch {
            if (activeFile?.absolutePath == file.absolutePath) {
                close()
            }

            if (file.exists()) {
                file.delete()
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getString(PREF_ACTIVE_MAP_NAME, null) == file.name) {
                prefs.edit().remove(PREF_ACTIVE_MAP_NAME).apply()
            }

            refreshAvailableMaps(context)
        }
    }

    /**
     * Exclui o mapa ativo atual.
     */
    fun deleteActiveMap(context: Context) {
        activeFile?.let { file ->
            deleteMap(context, file)
        }
    }

    /**
     * Valida se o arquivo informado é um banco SQLite válido contendo a tabela obrigatória 'tiles'.
     */
    fun isValidMbtiles(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        var db: SQLiteDatabase? = null
        return try {
            db = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
            val cursor = db.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name='tiles'",
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

    /**
     * Importa um arquivo MBTiles selecionado pelo usuário através do SAF (Storage Access Framework).
     * Copia para a pasta do app, valida a integridade do SQLite e o ativa imediatamente.
     */
    suspend fun importMbtilesFromUri(context: Context, uri: Uri): Result<File> = withContext(Dispatchers.IO) {
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

            var targetFile = File(mapsDir, fileName)
            if (targetFile.exists()) {
                val base = fileName.removeSuffix(".mbtiles")
                targetFile = File(mapsDir, "${base}_${System.currentTimeMillis()}.mbtiles")
            }
            destinationFile = targetFile

            // Copia o fluxo de dados para o arquivo local
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                targetFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return@withContext Result.failure(Exception("Não foi possível acessar o arquivo selecionado."))

            // Validação de integridade do MBTiles
            if (!isValidMbtiles(targetFile)) {
                targetFile.delete()
                return@withContext Result.failure(Exception("O arquivo selecionado não é um arquivo .mbtiles válido."))
            }

            // Ativa o mapa importado
            selectMap(context, targetFile)
            Log.i(TAG, "Mapa MBTiles importado com sucesso: ${targetFile.name} (${targetFile.length() / (1024 * 1024)} MB)")
            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao importar MBTiles de URI: $uri", e)
            destinationFile?.let { if (it.exists()) it.delete() }
            Result.failure(e)
        }
    }

    /**
     * Lê o nome amigável do mapa armazenado na tabela 'metadata' do MBTiles.
     */
    private fun readMapDisplayName(file: File): String {
        return try {
            val db = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
            var name: String? = null
            val cursor = db.rawQuery("SELECT value FROM metadata WHERE name = 'name' LIMIT 1", null)
            if (cursor.moveToFirst()) {
                name = cursor.getString(0)
            }
            cursor.close()
            db.close()
            if (!name.isNullOrBlank()) name else cleanFileName(file.name)
        } catch (_: Exception) {
            cleanFileName(file.name)
        }
    }

    private fun cleanFileName(fileName: String): String {
        return fileName
            .removeSuffix(".mbtiles")
            .replace("map_", "")
            .replace("_", " ")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    /**
     * Abre um arquivo .mbtiles via SQLiteDatabase nativo em modo somente-leitura.
     */
    fun openMapFile(file: File) {
        openMapFileInternal(file)
    }

    private fun openMapFileInternal(file: File) {
        try {
            close()
            val db = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
            activeDatabase = db
            activeFile = file

            var minZoom = 10
            var maxZoom = 20
            var mapName: String? = null

            // Lê metadados opcionais do MBTiles
            try {
                val cursor = db.rawQuery("SELECT name, value FROM metadata", null)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    val value = cursor.getString(1)
                    when (name.lowercase()) {
                        "name" -> mapName = value
                        "minzoom" -> minZoom = value.toIntOrNull() ?: minZoom
                        "maxzoom" -> maxZoom = value.toIntOrNull() ?: maxZoom
                    }
                }
                cursor.close()
            } catch (e: Exception) {
                Log.d(TAG, "Metadados padrão não encontrados, usando limites default", e)
            }

            val targetZoom = 19.coerceIn(minZoom, maxZoom)
            _mapState.value = _mapState.value.copy(
                isMapLoaded = true,
                mapFileName = file.name,
                mapDisplayName = mapName ?: cleanFileName(file.name),
                minZoom = minZoom,
                maxZoom = maxZoom,
                defaultZoom = targetZoom
            )
            Log.i(TAG, "Mapa MBTiles carregado com sucesso: ${file.name} (Zoom: $minZoom..$maxZoom)")
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao abrir banco MBTiles: ${file.absolutePath}", e)
            _mapState.value = _mapState.value.copy(isMapLoaded = false)
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
        viewHeight: Float
    ): List<RenderableTile> = withContext(Dispatchers.IO) {
        val db = activeDatabase ?: return@withContext emptyList()
        val tileSize = SlippyMapUtils.TILE_SIZE

        val xExact = SlippyMapUtils.lonToTileX(lon, zoom)
        val yExact = SlippyMapUtils.latToTileY(lat, zoom)

        val centerTileX = floor(xExact).toInt()
        val centerTileY = floor(yExact).toInt()

        // Posição na tela do canto superior esquerdo do bloco central
        val subOffsetX = ((xExact - centerTileX) * tileSize).toFloat()
        val subOffsetY = ((yExact - centerTileY) * tileSize).toFloat()

        val centerScreenX = (viewWidth / 2f) - subOffsetX
        val centerScreenY = (viewHeight / 2f) - subOffsetY

        val minDx = floor((0f - centerScreenX) / tileSize).toInt()
        val maxDx = ceil((viewWidth - centerScreenX) / tileSize).toInt()
        val minDy = floor((0f - centerScreenY) / tileSize).toInt()
        val maxDy = ceil((viewHeight - centerScreenY) / tileSize).toInt()

        val tiles = mutableListOf<RenderableTile>()

        for (dx in minDx..maxDx) {
            for (dy in minDy..maxDy) {
                val tileX = centerTileX + dx
                val tileY = centerTileY + dy

                val screenX = centerScreenX + (dx * tileSize)
                val screenY = centerScreenY + (dy * tileSize)

                val bitmap = loadTileBitmap(db, zoom, tileX, tileY)
                if (bitmap != null) {
                    tiles.add(RenderableTile(bitmap, screenX, screenY))
                }
            }
        }

        tiles
    }

    /**
     * Carrega a imagem de um bloco a partir do cache ou do banco SQLite nativo.
     */
    private fun loadTileBitmap(db: SQLiteDatabase, zoom: Int, tileX: Int, tileY: Int): Bitmap? {
        val cacheKey = "$zoom/$tileX/$tileY"
        val cached = memoryCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        // Especificação MBTiles usa coordenadas TMS para Y: (2^zoom - 1) - tileY
        val tmsY = (1 shl zoom) - 1 - tileY

        return try {
            val cursor = db.rawQuery(
                "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ? LIMIT 1",
                arrayOf(zoom.toString(), tileX.toString(), tmsY.toString())
            )
            var bitmap: Bitmap? = null
            if (cursor.moveToFirst()) {
                val blob = cursor.getBlob(0)
                if (blob != null && blob.isNotEmpty()) {
                    bitmap = BitmapFactory.decodeByteArray(blob, 0, blob.size)
                    if (bitmap != null) {
                        memoryCache.put(cacheKey, bitmap)
                    }
                }
            }
            cursor.close()
            bitmap
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao ler bloco ($zoom, $tileX, $tileY)", e)
            null
        }
    }

    /**
     * Fecha o banco de dados aberto.
     */
    fun close() {
        try {
            activeDatabase?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao fechar MBTiles database", e)
        } finally {
            activeDatabase = null
            activeFile = null
            memoryCache.evictAll()
            _mapState.value = _mapState.value.copy(isMapLoaded = false, mapFileName = null, mapDisplayName = null)
        }
    }
}
