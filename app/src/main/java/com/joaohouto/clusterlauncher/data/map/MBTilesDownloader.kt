package com.joaohouto.clusterlauncher.data.map

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.floor

sealed class DownloadProgress {
    object Idle : DownloadProgress()
    data class Downloading(val current: Int, val total: Int, val percentage: Int) : DownloadProgress()
    data class Completed(val file: File, val sizeMb: Float) : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
}

data class TileCoord(val zoom: Int, val x: Int, val y: Int)

enum class TileProvider(
    val id: String,
    val displayName: String,
    val urlTemplate: String
) {
    OSM_STANDARD(
        "osm_standard",
        "OpenStreetMap",
        "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    ),
    ESRI_DARK(
        "esri_dark",
        "Esri Dark Canvas",
        "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}"
    ),
    CUSTOM(
        "custom",
        "Personalizado",
        ""
    )
}

/**
 * Motor de download de blocos de mapa e empacotamento em tempo real no formato MBTiles (SQLite).
 * Cada download gera um arquivo único identificado por provedor, raio e timestamp, prevenindo sobreposição.
 */
object MBTilesDownloader {

    private const val TAG = "MBTilesDownloader"
    private const val USER_AGENT = "ClusterLauncher/1.1.0 (Android Automotive; contact@joaohouto.com)"
    private const val MAX_CONCURRENT_DOWNLOADS = 3

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentJob: Job? = null

    private val _progressState = MutableStateFlow<DownloadProgress>(DownloadProgress.Idle)
    val progressState: StateFlow<DownloadProgress> = _progressState.asStateFlow()

    /**
     * Calcula os blocos necessários para cobrir um raio em km a partir de uma coordenada central.
     * Cobre os níveis de zoom 13 a 16 (visão rodoviária e urbana detalhada).
     */
    fun computeTilesForRegion(centerLat: Double, centerLon: Double, radiusKm: Int): List<TileCoord> {
        val latDelta = radiusKm / 111.0
        val cosLat = cos(Math.toRadians(centerLat)).coerceAtLeast(0.01)
        val lonDelta = radiusKm / (111.0 * cosLat)

        val minLat = (centerLat - latDelta).coerceIn(-85.0511, 85.0511)
        val maxLat = (centerLat + latDelta).coerceIn(-85.0511, 85.0511)
        val minLon = (centerLon - lonDelta).coerceIn(-180.0, 180.0)
        val maxLon = (centerLon + lonDelta).coerceIn(-180.0, 180.0)

        val tiles = mutableListOf<TileCoord>()
        for (z in 13..16) {
            val minX = floor(SlippyMapUtils.lonToTileX(minLon, z)).toInt()
            val maxX = floor(SlippyMapUtils.lonToTileX(maxLon, z)).toInt()
            val minY = floor(SlippyMapUtils.latToTileY(maxLat, z)).toInt()
            val maxY = floor(SlippyMapUtils.latToTileY(minLat, z)).toInt()

            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    tiles.add(TileCoord(z, x, y))
                }
            }
        }
        return tiles
    }

    /**
     * Inicia o download e a geração do arquivo MBTiles para a região especificada.
     */
    fun startDownload(
        context: Context,
        centerLat: Double,
        centerLon: Double,
        radiusKm: Int,
        provider: TileProvider = TileProvider.OSM_STANDARD,
        customUrl: String = ""
    ) {
        cancelDownload(context)

        val template = if (provider == TileProvider.CUSTOM && customUrl.isNotBlank()) {
            customUrl.trim()
        } else {
            provider.urlTemplate
        }

        currentJob = scope.launch {
            var db: SQLiteDatabase? = null
            var tmpFile: File? = null

            try {
                val tilesToDownload = computeTilesForRegion(centerLat, centerLon, radiusKm)
                val totalCount = tilesToDownload.size

                if (totalCount == 0) {
                    _progressState.value = DownloadProgress.Error("Coordenadas inválidas para download.")
                    return@launch
                }

                _progressState.value = DownloadProgress.Downloading(0, totalCount, 0)

                val mapsDir = context.getExternalFilesDir("maps") ?: File(context.filesDir, "maps")
                if (!mapsDir.exists()) mapsDir.mkdirs()

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                tmpFile = File(mapsDir, "download_tmp_${timestamp}.mbtiles")
                if (tmpFile.exists()) tmpFile.delete()

                val targetFileName = "map_${provider.id}_${radiusKm}km_${timestamp}.mbtiles"
                val targetFile = File(mapsDir, targetFileName)

                // Cria banco SQLite nativo e estrutura padrão MBTiles
                db = SQLiteDatabase.openOrCreateDatabase(tmpFile, null)
                db.execSQL("CREATE TABLE IF NOT EXISTS metadata (name TEXT, value TEXT);")
                db.execSQL("CREATE TABLE IF NOT EXISTS tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB);")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS tile_index ON tiles (zoom_level, tile_column, tile_row);")

                // Grava metadados informativos
                val insertMeta = db.compileStatement("INSERT OR REPLACE INTO metadata (name, value) VALUES (?, ?)")
                fun writeMeta(name: String, value: String) {
                    insertMeta.bindString(1, name)
                    insertMeta.bindString(2, value)
                    insertMeta.executeInsert()
                }
                writeMeta("name", "${provider.displayName} (${radiusKm} km)")
                writeMeta("format", "png")
                writeMeta("minzoom", "13")
                writeMeta("maxzoom", "16")
                writeMeta("type", "baselayer")
                writeMeta("provider", provider.displayName)
                writeMeta("radius", "$radiusKm km")
                writeMeta("center_lat", centerLat.toString())
                writeMeta("center_lon", centerLon.toString())
                insertMeta.close()

                val insertTile: SQLiteStatement = db.compileStatement(
                    "INSERT OR REPLACE INTO tiles (zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?)"
                )

                val semaphore = Semaphore(MAX_CONCURRENT_DOWNLOADS)
                var downloadedCount = 0

                db.beginTransaction()
                var tilesInCurrentTx = 0

                for (coord in tilesToDownload) {
                    if (!isActive) throw CancellationException()

                    val tileBytes = semaphore.withPermit {
                        fetchTileBytes(template, coord.zoom, coord.x, coord.y)
                    }

                    if (tileBytes != null && tileBytes.isNotEmpty()) {
                        val tmsY = (1 shl coord.zoom) - 1 - coord.y
                        synchronized(db) {
                            insertTile.bindLong(1, coord.zoom.toLong())
                            insertTile.bindLong(2, coord.x.toLong())
                            insertTile.bindLong(3, tmsY.toLong())
                            insertTile.bindBlob(4, tileBytes)
                            insertTile.executeInsert()
                        }
                    }

                    downloadedCount++
                    tilesInCurrentTx++

                    // Comita transações a cada 30 blocos para máxima performance
                    if (tilesInCurrentTx >= 30) {
                        synchronized(db) {
                            db.setTransactionSuccessful()
                            db.endTransaction()
                            db.beginTransaction()
                        }
                        tilesInCurrentTx = 0
                    }

                    val percentage = ((downloadedCount * 100) / totalCount)
                    _progressState.value = DownloadProgress.Downloading(downloadedCount, totalCount, percentage)
                }

                synchronized(db) {
                    db.setTransactionSuccessful()
                    db.endTransaction()
                }
                insertTile.close()
                db.close()
                db = null

                // Renomeia o arquivo temporário para o destino final exclusivo
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                tmpFile.renameTo(targetFile)

                val sizeMb = (targetFile.length() / (1024f * 1024f))
                _progressState.value = DownloadProgress.Completed(targetFile, sizeMb)

                // Ativa imediatamente o novo mapa no repositório de mapas
                MBTilesRepository.selectMap(context, targetFile)
                Log.i(TAG, "Download concluído com sucesso: ${targetFile.name} (${sizeMb} MB)")

            } catch (e: CancellationException) {
                Log.d(TAG, "Download de mapa cancelado pelo usuário.")
                _progressState.value = DownloadProgress.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Erro durante download do mapa", e)
                _progressState.value = DownloadProgress.Error(e.message ?: "Falha no download.")
            } finally {
                try {
                    db?.close()
                } catch (_: Exception) {}
                if (tmpFile != null && tmpFile.exists() && _progressState.value !is DownloadProgress.Completed) {
                    tmpFile.delete()
                }
            }
        }
    }

    /**
     * Baixa um bloco individual via HttpURLConnection com User-Agent apropriado.
     */
    private suspend fun fetchTileBytes(
        template: String,
        zoom: Int,
        x: Int,
        y: Int
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val urlStr = template
                .replace("{z}", zoom.toString())
                .replace("{x}", x.toString())
                .replace("{y}", y.toString())
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.instanceFollowRedirects = true

            if (conn.responseCode == 200) {
                val bytes = conn.inputStream.use { it.readBytes() }
                conn.disconnect()
                bytes
            } else {
                conn.disconnect()
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Cancela qualquer download em andamento.
     */
    fun cancelDownload(context: Context) {
        currentJob?.cancel()
        currentJob = null
        _progressState.value = DownloadProgress.Idle

        scope.launch {
            val mapsDir = context.getExternalFilesDir("maps") ?: return@launch
            mapsDir.listFiles { _, name -> name.startsWith("download_tmp") }?.forEach { it.delete() }
        }
    }

    /**
     * Exclui o mapa local ativo.
     */
    fun deleteActiveMap(context: Context) {
        MBTilesRepository.deleteActiveMap(context)
    }
}
