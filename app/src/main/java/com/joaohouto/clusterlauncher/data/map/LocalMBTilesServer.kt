package com.joaohouto.clusterlauncher.data.map

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * Servidor HTTP loopback ultraleve (127.0.0.1) para servir blocos vetoriais PBF/MVT
 * de arquivos .mbtiles diretamente para o motor OpenGL do MapLibre Native.
 */
object LocalMBTilesServer {

    private const val TAG = "LocalMBTilesServer"
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(8) + SupervisorJob())

    private var serverSocket: ServerSocket? = null
    @Volatile
    private var isRunning = false

    var port: Int = 0
        private set

    @Volatile
    private var activeDatabase: SQLiteDatabase? = null
    @Volatile
    private var currentFile: File? = null
    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var sourceMinZoom: Int = 0
    @Volatile
    private var sourceMaxZoom: Int = 14

    // Cache LRU em memória para blocos PBF recentes (reduz I/O de disco SQLite em rotações e panning)
    private val tileCache = LruCache<Long, ByteArray>(400)
    // Cache LRU em memória para glifos de fonte PBF (evita ler do APK assets repetidamente)
    private val glyphCache = LruCache<String, ByteArray>(64)

    private fun getTileCacheKey(z: Int, x: Int, y: Int): Long {
        return (z.toLong() and 0x3FL shl 54) or ((x.toLong() and 0x1FFFFFFL) shl 27) or (y.toLong() and 0x1FFFFFFL)
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Define o arquivo .mbtiles fonte para o servidor e abre a conexão SQLite em modo somente-leitura.
     */
    @Synchronized
    fun setSource(file: File?, minZoom: Int? = null, maxZoom: Int? = null) {
        if (minZoom != null) sourceMinZoom = minZoom.coerceIn(0, 18)
        if (maxZoom != null) sourceMaxZoom = maxZoom.coerceIn(sourceMinZoom, 18)

        if (currentFile?.absolutePath == file?.absolutePath && activeDatabase?.isOpen == true) {
            return
        }
        currentFile = file
        synchronized(tileCache) {
            tileCache.evictAll()
        }
        try {
            activeDatabase?.close()
        } catch (_: Exception) {}
        activeDatabase = if (file != null && file.exists()) {
            try {
                val db = SQLiteDatabase.openDatabase(
                    file.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
                )
                // Otimiza leitura de arquivos MBTiles grandes (300MB+) em flash/eMMC automotivo
                runCatching {
                    db.rawQuery("PRAGMA mmap_size = 67108864", null)?.use { it.moveToFirst() }
                    db.rawQuery("PRAGMA cache_size = -8000", null)?.use { it.moveToFirst() }
                    db.rawQuery("PRAGMA temp_store = MEMORY", null)?.use { it.moveToFirst() }
                }
                if (minZoom == null || maxZoom == null) {
                    var parsedMin = 0
                    var parsedMax = 14
                    try {
                        val cursor = db.rawQuery("SELECT name, value FROM metadata WHERE LOWER(name) IN ('minzoom', 'maxzoom')", null)
                        while (cursor.moveToNext()) {
                            val k = cursor.getString(0)?.lowercase()
                            val v = cursor.getString(1)?.toIntOrNull()
                            if (k == "minzoom" && v != null) parsedMin = v
                            if (k == "maxzoom" && v != null) parsedMax = v
                        }
                        cursor.close()
                    } catch (_: Exception) {}
                    sourceMinZoom = parsedMin.coerceIn(0, 18)
                    sourceMaxZoom = parsedMax.coerceIn(sourceMinZoom, 18)
                }
                db
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao abrir SQLite para LocalMBTilesServer: ${file.absolutePath}", e)
                null
            }
        } else {
            null
        }
    }

    /**
     * Inicia o servidor HTTP loopback se ainda não estiver em execução.
     */
    @Synchronized
    fun start() {
        if (isRunning && serverSocket != null && !serverSocket!!.isClosed) return
        try {
            val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            serverSocket = socket
            port = socket.localPort
            isRunning = true
            Log.i(TAG, "Servidor MBTiles iniciado na porta loopback $port")

            Thread({
                while (isRunning) {
                    try {
                        val client = socket.accept()
                        scope.launch {
                            handleClient(client)
                        }
                    } catch (_: Exception) {
                        if (!isRunning) break
                    }
                }
            }, "LocalMBTilesAccept").apply {
                isDaemon = true
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar LocalMBTilesServer", e)
        }
    }

    /**
     * Encerra o servidor e libera conexões abertas.
     */
    @Synchronized
    fun stop() {
        isRunning = false
        synchronized(tileCache) {
            tileCache.evictAll()
        }
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        try {
            activeDatabase?.close()
        } catch (_: Exception) {}
        activeDatabase = null
        currentFile = null
    }

    private fun handleClient(client: Socket) {
        try {
            client.soTimeout = 5000
            client.tcpNoDelay = true
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val firstLine = reader.readLine() ?: return
            // Drena todos os cabeçalhos HTTP enviados pelo MapLibre antes de escrever a resposta,
            // evitando que o kernel Linux envie TCP RST ao fechar o socket com dados não lidos no buffer.
            while (true) {
                val headerLine = reader.readLine()
                if (headerLine.isNullOrEmpty()) break
            }
            val parts = firstLine.split(" ")
            if (parts.size < 2 || parts[0] != "GET") {
                client.close()
                return
            }

            val path = parts[1]
            if (path.startsWith("/tiles/")) {
                // Formato esperado: /tiles/{z}/{x}/{y}.pbf
                val trimmed = path.removePrefix("/tiles/").removeSuffix(".pbf")
                val coordParts = trimmed.split("/")
                if (coordParts.size == 3) {
                    val z = coordParts[0].toIntOrNull()
                    val x = coordParts[1].toIntOrNull()
                    val y = coordParts[2].toIntOrNull()

                    if (z != null && x != null && y != null) {
                        // Converte Y (XYZ do MapLibre) para TMS (MBTiles)
                        val tmsY = (1 shl z) - 1 - y
                        val tileData = fetchTileBytes(z, x, tmsY)
                        if (tileData != null && tileData.isNotEmpty()) {
                            sendResponse(client, tileData, isPbf = true)
                            return
                        }
                    }
                }
                send404(client)
            } else if (path.startsWith("/glyphs/")) {
                val cleanPath = URLDecoder.decode(path.removePrefix("/glyphs/"), "UTF-8")
                val cachedGlyph = synchronized(glyphCache) { glyphCache.get(cleanPath) }
                if (cachedGlyph != null && cachedGlyph.isNotEmpty()) {
                    sendResponse(client, cachedGlyph, isPbf = false, contentType = "application/x-protobuf")
                    return
                }
                val assetPath = "fonts/$cleanPath"
                val data = try {
                    appContext?.assets?.open(assetPath)?.readBytes()
                } catch (_: Exception) {
                    null
                }
                if (data != null && data.isNotEmpty()) {
                    synchronized(glyphCache) { glyphCache.put(cleanPath, data) }
                    sendResponse(client, data, isPbf = false, contentType = "application/x-protobuf")
                    return
                } else {
                    val fontName = cleanPath.substringBeforeLast("/")
                    val fallbackKey = "$fontName/0-255.pbf"
                    val cachedFallback = synchronized(glyphCache) { glyphCache.get(fallbackKey) }
                    if (cachedFallback != null && cachedFallback.isNotEmpty()) {
                        sendResponse(client, cachedFallback, isPbf = false, contentType = "application/x-protobuf")
                        return
                    }
                    val fallback = try {
                        appContext?.assets?.open("fonts/$fallbackKey")?.readBytes()
                    } catch (_: Exception) { null }
                    if (fallback != null && fallback.isNotEmpty()) {
                        synchronized(glyphCache) { glyphCache.put(fallbackKey, fallback) }
                        sendResponse(client, fallback, isPbf = false, contentType = "application/x-protobuf")
                        return
                    }
                }
                send404(client)
            } else if (path == "/style.json" || path == "/style-day.json") {
                val styleJson = buildCockpitStyle(port, isDayHighContrast = path == "/style-day.json")
                sendResponse(client, styleJson.toByteArray(Charsets.UTF_8), isPbf = false, contentType = "application/json")
            } else {
                send404(client)
            }
        } catch (_: Exception) {
            // Conexão encerrada pelo cliente ou timeout comum durante panning de mapa
        } finally {
            try {
                client.close()
            } catch (_: Exception) {}
        }
    }

    private fun fetchTileBytes(z: Int, x: Int, y: Int): ByteArray? {
        val cacheKey = getTileCacheKey(z, x, y)
        synchronized(tileCache) {
            tileCache.get(cacheKey)?.let { return it }
        }

        val db = activeDatabase ?: return null
        if (!db.isOpen) return null
        var cursor: android.database.Cursor? = null
        return try {
            cursor = db.rawQuery(
                "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ? LIMIT 1",
                arrayOf(z.toString(), x.toString(), y.toString())
            )
            if (cursor.moveToFirst()) {
                val data = cursor.getBlob(0)
                if (data != null && data.isNotEmpty()) {
                    synchronized(tileCache) {
                        tileCache.put(cacheKey, data)
                    }
                }
                data
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }

    private fun sendResponse(client: Socket, data: ByteArray, isPbf: Boolean, contentType: String = "application/x-protobuf") {
        val out = BufferedOutputStream(client.getOutputStream())
        val isGzip = data.size >= 2 && (data[0].toInt() and 0xFF == 0x1F) && (data[1].toInt() and 0xFF == 0x8B)

        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: $contentType\r\n")
            if (isPbf && isGzip) {
                append("Content-Encoding: gzip\r\n")
            }
            append("Access-Control-Allow-Origin: *\r\n")
            append("Cache-Control: public, max-age=86400\r\n")
            append("Content-Length: ${data.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        out.write(header.toByteArray(Charsets.US_ASCII))
        out.write(data)
        out.flush()
    }

    private fun send404(client: Socket) {
        val out = BufferedOutputStream(client.getOutputStream())
        val header = "HTTP/1.1 404 Not Found\r\nAccess-Control-Allow-Origin: *\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.US_ASCII))
        out.flush()
    }

    /**
     * Gera o estilo visual JSON esportivo automotivo escuro compatível com o schema OpenMapTiles.
     */
    fun buildCockpitDarkStyle(serverPort: Int): String =
        buildCockpitStyle(serverPort, isDayHighContrast = false)

    /**
     * Gera o estilo visual JSON automotivo (OpenMapTiles) com suporte a:
     * - Modo Noite ([isDayHighContrast] = false): escuro suave para conforto noturno.
     * - Modo Dia ([isDayHighContrast] = true): mantém fundo escuro grafite, porém com cinzas
     *   bem mais claros, ruas/rodovias/água/rótulos de alto contraste para leitura sob sol forte.
     */
    fun buildCockpitStyle(serverPort: Int, isDayHighContrast: Boolean): String {
        val styleName = if (isDayHighContrast) "Cluster Cockpit Day High Contrast" else "Cluster Cockpit Dark"
        val background = if (isDayHighContrast) "#181C24" else "#0E1014"
        val wood = if (isDayHighContrast) "#1D3126" else "#111814"
        val sand = if (isDayHighContrast) "#2C2820" else "#1A1813"
        val wetland = if (isDayHighContrast) "#192D33" else "#0E181B"
        val residential = if (isDayHighContrast) "#212734" else "#14171E"
        val commercial = if (isDayHighContrast) "#252C3B" else "#161922"
        val industrial = if (isDayHighContrast) "#232833" else "#15171D"
        val park = if (isDayHighContrast) "#21382C" else "#131918"
        val water = if (isDayHighContrast) "#1E3E66" else "#151F33"
        val aeroApron = if (isDayHighContrast) "#293040" else "#181C26"
        val aeroRunway = if (isDayHighContrast) "#8CA0C2" else "#53627E"
        val aeroTaxiway = if (isDayHighContrast) "#5D6E8A" else "#333D4F"
        val boundaryCountry = if (isDayHighContrast) "#6E7A94" else "#3B4254"
        val boundaryState = if (isDayHighContrast) "#505B73" else "#282E3D"
        val buildingShadow = if (isDayHighContrast) "#0E1118" else "#07080D"
        val buildingFill = if (isDayHighContrast) "#2E374A" else "#1E2432"
        val buildingOutline = if (isDayHighContrast) "#505E7A" else "#2E374A"
        val roadTunnel = if (isDayHighContrast) "#3B4457" else "#1C202B"
        val railwayBase = if (isDayHighContrast) "#7886A0" else "#424B5D"
        val railwayTies = if (isDayHighContrast) "#1E232E" else "#11141A"
        val roadMinor = if (isDayHighContrast) "#5E6B82" else "#28303F"
        val roadSecondary = if (isDayHighContrast) "#8090AD" else "#354054"
        val roadPrimary = if (isDayHighContrast) "#A4B6D4" else "#485670"
        val roadCasing = if (isDayHighContrast) "#131720" else "#0D0F14"
        val roadTrunk = if (isDayHighContrast) "#C4D3EC" else "#5A6A88"
        val roadMotorway = if (isDayHighContrast) "#E5EEFC" else "#6E80A3"
        val bridgeCasing = if (isDayHighContrast) "#0E1117" else "#07080B"
        val bridgeDeck = if (isDayHighContrast) "#AEBEDB" else "#52607A"
        val onewayColor = if (isDayHighContrast) "#D2DDF0" else "#70809C"
        val haloColor = if (isDayHighContrast) "#12151C" else "#0C0D10"
        val waterNameColor = if (isDayHighContrast) "#79A6D6" else "#3E5D80"
        val roadNameMajor = if (isDayHighContrast) "#FFFFFF" else "#DCE3F0"
        val roadNameMinor = if (isDayHighContrast) "#E8EEF8" else "#A0AABA"
        val roadRefColor = if (isDayHighContrast) "#FFFFFF" else "#E2E8F0"
        val aerodromeColor = if (isDayHighContrast) "#BDD0F0" else "#8EA3C7"
        val poiLabelColor = if (isDayHighContrast) "#F4F7FC" else "#E2E7F0"
        val placeCityColor = if (isDayHighContrast) "#FFFFFF" else "#F0F3F8"
        val placeSuburbColor = if (isDayHighContrast) "#CED8EA" else "#8C96A8"

        return """
        {
          "version": 8,
          "name": "$styleName",
          "glyphs": "http://127.0.0.1:$serverPort/glyphs/{fontstack}/{range}.pbf",
          "sources": {
            "openmaptiles": {
              "type": "vector",
              "tiles": [
                "http://127.0.0.1:$serverPort/tiles/{z}/{x}/{y}.pbf"
              ],
              "minzoom": $sourceMinZoom,
              "maxzoom": $sourceMaxZoom
            }
          },
          "layers": [
            {
              "id": "background",
              "type": "background",
              "paint": {
                "background-color": "$background"
              }
            },
            {
              "id": "landcover_wood",
              "type": "fill",
              "source": "openmaptiles",
              "source-layer": "landcover",
              "filter": ["in", "class", "wood", "forest", "scrub", "grass", "crop"],
              "paint": {
                "fill-color": "$wood",
                "fill-opacity": 0.85
              }
            },
            {
              "id": "landcover_sand",
              "type": "fill",
              "source": "openmaptiles",
              "source-layer": "landcover",
              "filter": ["==", "class", "sand"],
              "paint": {
                "fill-color": "$sand",
                "fill-opacity": 0.7
              }
            },
            {
              "id": "landcover_wetland",
              "type": "fill",
              "source": "openmaptiles",
              "source-layer": "landcover",
              "filter": ["==", "class", "wetland"],
              "paint": {
                "fill-color": "$wetland",
                "fill-opacity": 0.75
              }
            },
            {
              "id": "landuse_residential",
              "type": "fill",
              "source": "openmaptiles",
              "source-layer": "landuse",
              "filter": ["==", "class", "residential"],
              "paint": {
                "fill-color": "$residential",
                "fill-opacity": 0.85
              }
            },
            {
              "id": "landuse_commercial",
              "type": "fill",
              "source": "openmaptiles",
              "source-layer": "landuse",
              "filter": ["in", "class", "commercial", "retail"],
              "paint": {
                "fill-color": "$commercial",
                "fill-opacity": 0.75
              }
            },
            {
              "id": "landuse_industrial",
              "type": "fill",
              "source": "openmaptiles",
              "source-layer": "landuse",
              "filter": ["==", "class", "industrial"],
              "paint": {
                "fill-color": "$industrial",
                "fill-opacity": 0.75
              }
            },
            {
              "id": "park",
              "type": "fill",
              "source": "openmaptiles",
              "source-layer": "park",
              "paint": {
                "fill-color": "$park",
                "fill-opacity": 0.8
              }
            },
            {
              "id": "water",
              "type": "fill",
              "source": "openmaptiles",
              "source-layer": "water",
              "paint": {
                "fill-color": "$water"
              }
            },
            {
              "id": "waterway",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "waterway",
              "paint": {
                "line-color": "$water",
                "line-width": 1.8
              }
            },
            {
              "id": "aeroway_apron",
              "type": "fill",
              "source": "openmaptiles",
              "source-layer": "aeroway",
              "minzoom": 10,
              "filter": ["==", "class", "apron"],
              "paint": {
                "fill-color": "$aeroApron",
                "fill-opacity": 0.85
              }
            },
            {
              "id": "aeroway_runway",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "aeroway",
              "minzoom": 8,
              "filter": ["==", "class", "runway"],
              "paint": {
                "line-color": "$aeroRunway",
                "line-width": {
                  "base": 1.2,
                  "stops": [[8, 1.2], [11, 3.5], [14, 10.0], [17, 24.0]]
                }
              }
            },
            {
              "id": "aeroway_taxiway",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "aeroway",
              "minzoom": 11,
              "filter": ["==", "class", "taxiway"],
              "paint": {
                "line-color": "$aeroTaxiway",
                "line-width": {
                  "base": 1.2,
                  "stops": [[11, 1.0], [14, 3.5], [17, 8.0]]
                }
              }
            },
            {
              "id": "boundary_country",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "boundary",
              "filter": ["==", "admin_level", 2],
              "paint": {
                "line-color": "$boundaryCountry",
                "line-width": 1.4,
                "line-dasharray": [3, 2]
              }
            },
            {
              "id": "boundary_state",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "boundary",
              "filter": [">", "admin_level", 2],
              "paint": {
                "line-color": "$boundaryState",
                "line-width": 1.0,
                "line-dasharray": [2, 2]
              }
            },
            {
              "id": "building_shadow",
              "type": "fill",
              "source": "openmaptiles",
              "source-layer": "building",
              "minzoom": 13,
              "paint": {
                "fill-color": "$buildingShadow",
                "fill-opacity": {
                  "base": 1.0,
                  "stops": [[13, 0.25], [15, 0.45], [17, 0.6]]
                },
                "fill-translate": [1.5, 1.5],
                "fill-translate-anchor": "viewport"
              }
            },
            {
              "id": "building",
              "type": "fill",
              "source": "openmaptiles",
              "source-layer": "building",
              "minzoom": 13,
              "paint": {
                "fill-color": "$buildingFill",
                "fill-opacity": {
                  "base": 1.0,
                  "stops": [[13, 0.65], [15, 0.88], [17, 0.96]]
                }
              }
            },
            {
              "id": "building_outline",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "building",
              "minzoom": 13,
              "paint": {
                "line-color": "$buildingOutline",
                "line-width": {
                  "base": 1.2,
                  "stops": [[13, 0.5], [15, 0.9], [17, 1.3]]
                },
                "line-opacity": 0.9
              }
            },
            {
              "id": "road_tunnel",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "transportation",
              "filter": ["==", "brunnel", "tunnel"],
              "paint": {
                "line-color": "$roadTunnel",
                "line-dasharray": [3, 2],
                "line-width": {
                  "base": 1.2,
                  "stops": [[10, 1.4], [14, 3.5], [17, 7.5]]
                }
              }
            },
            {
              "id": "railway_base",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "transportation",
              "minzoom": 8,
              "filter": ["in", "class", "rail", "subway", "tram", "light_rail", "transit"],
              "paint": {
                "line-color": "$railwayBase",
                "line-width": {
                  "base": 1.2,
                  "stops": [[8, 0.8], [12, 1.8], [16, 3.5]]
                }
              }
            },
            {
              "id": "railway_ties",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "transportation",
              "minzoom": 12,
              "filter": ["in", "class", "rail", "subway", "tram", "light_rail", "transit"],
              "paint": {
                "line-color": "$railwayTies",
                "line-width": {
                  "base": 1.2,
                  "stops": [[12, 1.2], [16, 2.8]]
                },
                "line-dasharray": [2, 3]
              }
            },
            {
              "id": "road_minor",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "transportation",
              "filter": ["in", "class", "minor", "service", "residential", "track", "path"],
              "paint": {
                "line-color": "$roadMinor",
                "line-width": {
                  "base": 1.2,
                  "stops": [[10, 0.8], [14, 2.4], [17, 5.2]]
                }
              }
            },
            {
              "id": "road_secondary_tertiary",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "transportation",
              "filter": ["in", "class", "secondary", "tertiary"],
              "paint": {
                "line-color": "$roadSecondary",
                "line-width": {
                  "base": 1.2,
                  "stops": [[7, 0.9], [12, 2.5], [16, 6.5]]
                }
              }
            },
            {
              "id": "road_primary",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "transportation",
              "filter": ["in", "class", "primary", "primary_link"],
              "paint": {
                "line-color": "$roadPrimary",
                "line-width": {
                  "base": 1.2,
                  "stops": [[6, 1.8], [10, 3.5], [13, 6.0], [16, 10.5], [17, 13.0]]
                }
              }
            },
            {
              "id": "road_trunk_casing",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "transportation",
              "filter": ["in", "class", "trunk", "trunk_link"],
              "paint": {
                "line-color": "$roadCasing",
                "line-width": {
                  "base": 1.2,
                  "stops": [[5, 2.6], [9, 4.5], [12, 7.6], [15, 12.5], [17, 18.0]]
                }
              }
            },
            {
              "id": "road_trunk",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "transportation",
              "filter": ["in", "class", "trunk", "trunk_link"],
              "paint": {
                "line-color": "$roadTrunk",
                "line-width": {
                  "base": 1.2,
                  "stops": [[5, 1.8], [9, 3.4], [12, 5.8], [15, 10.2], [17, 14.5]]
                }
              }
            },
            {
              "id": "road_motorway_casing",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "transportation",
              "filter": ["in", "class", "motorway", "motorway_link"],
              "paint": {
                "line-color": "$roadCasing",
                "line-width": {
                  "base": 1.2,
                  "stops": [[5, 3.0], [9, 5.2], [12, 8.8], [15, 14.5], [17, 20.5]]
                }
              }
            },
            {
              "id": "road_motorway",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "transportation",
              "filter": ["in", "class", "motorway", "motorway_link"],
              "paint": {
                "line-color": "$roadMotorway",
                "line-width": {
                  "base": 1.2,
                  "stops": [[5, 2.2], [9, 4.0], [12, 6.8], [15, 12.0], [17, 17.0]]
                }
              }
            },
            {
              "id": "bridge_casing",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "transportation",
              "minzoom": 11,
              "filter": ["==", "brunnel", "bridge"],
              "paint": {
                "line-color": "$bridgeCasing",
                "line-width": {
                  "base": 1.2,
                  "stops": [[11, 8.5], [14, 14.0], [17, 22.0]]
                }
              }
            },
            {
              "id": "bridge_deck",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "transportation",
              "minzoom": 11,
              "filter": ["==", "brunnel", "bridge"],
              "paint": {
                "line-color": "$bridgeDeck",
                "line-width": {
                  "base": 1.2,
                  "stops": [[11, 6.0], [14, 11.0], [17, 17.5]]
                }
              }
            },
            {
              "id": "road_oneway",
              "type": "symbol",
              "source": "openmaptiles",
              "source-layer": "transportation",
              "minzoom": 15,
              "filter": ["==", "oneway", 1],
              "layout": {
                "symbol-placement": "line",
                "symbol-spacing": 200,
                "text-field": ">",
                "text-font": ["Noto Sans Bold"],
                "text-size": 11,
                "text-keep-upright": false,
                "text-rotation-alignment": "map"
              },
              "paint": {
                "text-color": "$onewayColor",
                "text-opacity": 0.9
              }
            },
            {
              "id": "road_oneway_rev",
              "type": "symbol",
              "source": "openmaptiles",
              "source-layer": "transportation",
              "minzoom": 15,
              "filter": ["==", "oneway", -1],
              "layout": {
                "symbol-placement": "line",
                "symbol-spacing": 200,
                "text-field": "<",
                "text-font": ["Noto Sans Bold"],
                "text-size": 11,
                "text-keep-upright": false,
                "text-rotation-alignment": "map"
              },
              "paint": {
                "text-color": "$onewayColor",
                "text-opacity": 0.9
              }
            },
            {
              "id": "poi_marker",
              "type": "circle",
              "source": "openmaptiles",
              "source-layer": "poi",
              "minzoom": 13,
              "filter": ["in", "class", "fuel", "charging_station", "parking", "hospital", "police", "restaurant", "fast_food", "cafe", "bank", "pharmacy", "supermarket", "grocery", "hotel", "lodging"],
              "paint": {
                "circle-radius": {
                  "base": 1.2,
                  "stops": [[13, 3.5], [15, 5.0], [17, 6.5]]
                },
                "circle-color": [
                  "match",
                  ["get", "class"],
                  ["fuel", "charging_station"], "#FF9800",
                  "parking", "#2979FF",
                  "hospital", "#FF3D00",
                  "police", "#3D5AFE",
                  ["restaurant", "fast_food", "cafe"], "#00E676",
                  "bank", "#FFD600",
                  "pharmacy", "#00BFA5",
                  ["supermarket", "grocery"], "#7C4DFF",
                  ["hotel", "lodging"], "#546E7A",
                  "#78909C"
                ],
                "circle-stroke-width": 1.5,
                "circle-stroke-color": "$haloColor"
              }
            },
            {
              "id": "water_name",
              "type": "symbol",
              "source": "openmaptiles",
              "source-layer": "water_name",
              "minzoom": 12,
              "layout": {
                "symbol-placement": "line",
                "text-field": "{name}",
                "text-font": ["Noto Sans Regular"],
                "text-size": 11,
                "text-letter-spacing": 0.05
              },
              "paint": {
                "text-color": "$waterNameColor",
                "text-halo-color": "$haloColor",
                "text-halo-width": 1.4
              }
            },
            {
              "id": "road_name_major",
              "type": "symbol",
              "source": "openmaptiles",
              "source-layer": "transportation_name",
              "minzoom": 11,
              "filter": ["in", "class", "motorway", "trunk", "primary"],
              "layout": {
                "symbol-placement": "line",
                "text-field": "{name}",
                "text-font": ["Noto Sans Bold"],
                "text-size": {
                  "base": 1.2,
                  "stops": [[11, 9.5], [14, 11.5], [17, 13.5]]
                },
                "text-letter-spacing": 0.05,
                "text-rotation-alignment": "map"
              },
              "paint": {
                "text-color": "$roadNameMajor",
                "text-halo-color": "$haloColor",
                "text-halo-width": 2.2
              }
            },
            {
              "id": "road_name_minor",
              "type": "symbol",
              "source": "openmaptiles",
              "source-layer": "transportation_name",
              "minzoom": 13,
              "filter": ["in", "class", "secondary", "tertiary", "minor", "service", "residential"],
              "layout": {
                "symbol-placement": "line",
                "text-field": "{name}",
                "text-font": ["Noto Sans Regular"],
                "text-size": {
                  "base": 1.2,
                  "stops": [[13, 9.0], [15, 11.0], [17, 12.5]]
                },
                "text-letter-spacing": 0.02,
                "text-rotation-alignment": "map"
              },
              "paint": {
                "text-color": "$roadNameMinor",
                "text-halo-color": "$haloColor",
                "text-halo-width": 1.8
              }
            },
            {
              "id": "road_ref",
              "type": "symbol",
              "source": "openmaptiles",
              "source-layer": "transportation_name",
              "minzoom": 9,
              "filter": ["has", "ref"],
              "layout": {
                "symbol-placement": "line",
                "text-field": "{ref}",
                "text-font": ["Noto Sans Bold"],
                "text-size": {
                  "base": 1.2,
                  "stops": [[9, 8.5], [12, 10.0], [15, 11.0]]
                },
                "text-letter-spacing": 0.08,
                "text-rotation-alignment": "map"
              },
              "paint": {
                "text-color": "$roadRefColor",
                "text-halo-color": "$haloColor",
                "text-halo-width": 2.2
              }
            },
            {
              "id": "aerodrome_label",
              "type": "symbol",
              "source": "openmaptiles",
              "source-layer": "aerodrome_label",
              "minzoom": 8,
              "layout": {
                "symbol-placement": "point",
                "text-field": ["case", ["has", "iata"], ["concat", ["get", "iata"], " · ", ["get", "name"]], ["get", "name"]],
                "text-font": ["Noto Sans Bold"],
                "text-size": {
                  "base": 1.2,
                  "stops": [[8, 9.5], [11, 11.5], [14, 13.5]]
                },
                "text-letter-spacing": 0.08
              },
              "paint": {
                "text-color": "$aerodromeColor",
                "text-halo-color": "$haloColor",
                "text-halo-width": 2.0
              }
            },
            {
              "id": "poi_label",
              "type": "symbol",
              "source": "openmaptiles",
              "source-layer": "poi",
              "minzoom": 14,
              "filter": ["in", "class", "fuel", "charging_station", "parking", "hospital", "police", "restaurant", "fast_food", "cafe", "bank", "pharmacy", "supermarket", "grocery", "hotel", "lodging"],
              "layout": {
                "symbol-placement": "point",
                "text-field": "{name}",
                "text-font": ["Noto Sans Regular"],
                "text-size": {
                  "base": 1.2,
                  "stops": [[14, 9.0], [16, 11.0]]
                },
                "text-offset": [0, 1.1],
                "text-anchor": "top",
                "text-optional": true,
                "text-letter-spacing": 0.03
              },
              "paint": {
                "text-color": "$poiLabelColor",
                "text-halo-color": "$haloColor",
                "text-halo-width": 1.8
              }
            },
            {
              "id": "place_city",
              "type": "symbol",
              "source": "openmaptiles",
              "source-layer": "place",
              "filter": ["in", "class", "city", "town"],
              "layout": {
                "symbol-placement": "point",
                "text-field": "{name}",
                "text-font": ["Noto Sans Bold"],
                "text-size": {
                  "base": 1.2,
                  "stops": [[4, 10.0], [8, 12.5], [12, 15.0]]
                },
                "text-transform": "uppercase",
                "text-letter-spacing": 0.12
              },
              "paint": {
                "text-color": "$placeCityColor",
                "text-halo-color": "$haloColor",
                "text-halo-width": 2.2
              }
            },
            {
              "id": "place_suburb",
              "type": "symbol",
              "source": "openmaptiles",
              "source-layer": "place",
              "minzoom": 12,
              "filter": ["in", "class", "suburb", "neighbourhood", "village"],
              "layout": {
                "symbol-placement": "point",
                "text-field": "{name}",
                "text-font": ["Noto Sans Regular"],
                "text-size": {
                  "base": 1.2,
                  "stops": [[12, 9.5], [15, 11.5]]
                },
                "text-letter-spacing": 0.05
              },
              "paint": {
                "text-color": "$placeSuburbColor",
                "text-halo-color": "$haloColor",
                "text-halo-width": 1.6
              }
            }
          ]
        }
        """.trimIndent()
    }
}
