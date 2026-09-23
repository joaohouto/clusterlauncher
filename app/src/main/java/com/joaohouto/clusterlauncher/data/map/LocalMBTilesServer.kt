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
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

    // Cache LRU em memória para blocos PBF recentes (reduz I/O de disco SQLite em rotações e panning)
    private val tileCache = LruCache<Long, ByteArray>(250)

    private fun getTileCacheKey(z: Int, x: Int, y: Int): Long {
        return (z.toLong() and 0x3FL shl 54) or ((x.toLong() and 0x1FFFFFFL) shl 27) or (y.toLong() and 0x1FFFFFFL)
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Define o arquivo .mbtiles fonte para o servidor e abre a conexão SQLite em modo somente-leitura.
     */
    fun setSource(file: File?) {
        if (currentFile?.absolutePath == file?.absolutePath && activeDatabase?.isOpen == true) return
        currentFile = file
        synchronized(tileCache) {
            tileCache.evictAll()
        }
        try {
            activeDatabase?.close()
        } catch (_: Exception) {}
        activeDatabase = if (file != null && file.exists()) {
            try {
                SQLiteDatabase.openDatabase(
                    file.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
                )
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

            scope.launch {
                while (isRunning) {
                    try {
                        val client = socket.accept()
                        scope.launch {
                            handleClient(client)
                        }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
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
            client.soTimeout = 4000
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val firstLine = reader.readLine() ?: return
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
                val assetPath = "fonts/$cleanPath"
                val data = try {
                    appContext?.assets?.open(assetPath)?.readBytes()
                } catch (_: Exception) {
                    null
                }
                if (data != null && data.isNotEmpty()) {
                    sendResponse(client, data, isPbf = false, contentType = "application/x-protobuf")
                    return
                } else {
                    val fontName = cleanPath.substringBeforeLast("/")
                    val fallback = try {
                        appContext?.assets?.open("fonts/$fontName/0-255.pbf")?.readBytes()
                    } catch (_: Exception) { null }
                    if (fallback != null && fallback.isNotEmpty()) {
                        sendResponse(client, fallback, isPbf = false, contentType = "application/x-protobuf")
                        return
                    }
                }
                send404(client)
            } else if (path == "/style.json") {
                val styleJson = buildCockpitDarkStyle(port)
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
    fun buildCockpitDarkStyle(serverPort: Int): String {
        return """
        {
          "version": 8,
          "name": "Cluster Cockpit Dark",
          "glyphs": "http://127.0.0.1:$serverPort/glyphs/{fontstack}/{range}.pbf",
          "sources": {
            "openmaptiles": {
              "type": "vector",
              "tiles": [
                "http://127.0.0.1:$serverPort/tiles/{z}/{x}/{y}.pbf"
              ],
              "minzoom": 0,
              "maxzoom": 14
            }
          },
          "layers": [
            {
              "id": "background",
              "type": "background",
              "paint": {
                "background-color": "#0E1014"
              }
            },
            {
              "id": "landcover_wood",
              "type": "fill",
              "source": "openmaptiles",
              "source-layer": "landcover",
              "filter": ["in", "class", "wood", "forest", "scrub", "grass", "crop"],
              "paint": {
                "fill-color": "#111814",
                "fill-opacity": 0.8
              }
            },
            {
              "id": "landcover_sand",
              "type": "fill",
              "source": "openmaptiles",
              "source-layer": "landcover",
              "filter": ["==", "class", "sand"],
              "paint": {
                "fill-color": "#1A1813",
                "fill-opacity": 0.65
              }
            },
            {
              "id": "landcover_wetland",
              "type": "fill",
              "source": "openmaptiles",
              "source-layer": "landcover",
              "filter": ["==", "class", "wetland"],
              "paint": {
                "fill-color": "#0E181B",
                "fill-opacity": 0.7
              }
            },
            {
              "id": "landuse_residential",
              "type": "fill",
              "source": "openmaptiles",
              "source-layer": "landuse",
              "filter": ["==", "class", "residential"],
              "paint": {
                "fill-color": "#14171E",
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
                "fill-color": "#161922",
                "fill-opacity": 0.7
              }
            },
            {
              "id": "landuse_industrial",
              "type": "fill",
              "source": "openmaptiles",
              "source-layer": "landuse",
              "filter": ["==", "class", "industrial"],
              "paint": {
                "fill-color": "#15171D",
                "fill-opacity": 0.7
              }
            },
            {
              "id": "park",
              "type": "fill",
              "source": "openmaptiles",
              "source-layer": "park",
              "paint": {
                "fill-color": "#131918",
                "fill-opacity": 0.75
              }
            },
            {
              "id": "water",
              "type": "fill",
              "source": "openmaptiles",
              "source-layer": "water",
              "paint": {
                "fill-color": "#151F33"
              }
            },
            {
              "id": "waterway",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "waterway",
              "paint": {
                "line-color": "#151F33",
                "line-width": 1.5
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
                "fill-color": "#181C26",
                "fill-opacity": 0.8
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
                "line-color": "#53627E",
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
                "line-color": "#333D4F",
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
                "line-color": "#3B4254",
                "line-width": 1.2,
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
                "line-color": "#282E3D",
                "line-width": 0.8,
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
                "fill-color": "#07080D",
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
                "fill-color": "#1E2432",
                "fill-opacity": {
                  "base": 1.0,
                  "stops": [[13, 0.6], [15, 0.85], [17, 0.95]]
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
                "line-color": "#2E374A",
                "line-width": {
                  "base": 1.2,
                  "stops": [[13, 0.4], [15, 0.8], [17, 1.2]]
                },
                "line-opacity": 0.85
              }
            },
            {
              "id": "road_tunnel",
              "type": "line",
              "source": "openmaptiles",
              "source-layer": "transportation",
              "filter": ["==", "brunnel", "tunnel"],
              "paint": {
                "line-color": "#1C202B",
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
                "line-color": "#424B5D",
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
                "line-color": "#11141A",
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
                "line-color": "#28303F",
                "line-width": {
                  "base": 1.2,
                  "stops": [[10, 0.7], [14, 2.2], [17, 4.8]]
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
                "line-color": "#354054",
                "line-width": {
                  "base": 1.2,
                  "stops": [[7, 0.8], [12, 2.2], [16, 6.0]]
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
                "line-color": "#485670",
                "line-width": {
                  "base": 1.2,
                  "stops": [[6, 1.6], [10, 3.2], [13, 5.5], [16, 10.0], [17, 12.5]]
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
                "line-color": "#0D0F14",
                "line-width": {
                  "base": 1.2,
                  "stops": [[5, 2.4], [9, 4.2], [12, 7.2], [15, 12.0], [17, 17.5]]
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
                "line-color": "#5A6A88",
                "line-width": {
                  "base": 1.2,
                  "stops": [[5, 1.6], [9, 3.2], [12, 5.5], [15, 9.8], [17, 14.0]]
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
                "line-color": "#0D0F14",
                "line-width": {
                  "base": 1.2,
                  "stops": [[5, 2.8], [9, 5.0], [12, 8.5], [15, 14.0], [17, 20.0]]
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
                "line-color": "#6E80A3",
                "line-width": {
                  "base": 1.2,
                  "stops": [[5, 2.0], [9, 3.8], [12, 6.5], [15, 11.5], [17, 16.5]]
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
                "line-color": "#07080B",
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
                "line-color": "#52607A",
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
                "text-color": "#70809C",
                "text-opacity": 0.85
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
                "text-color": "#70809C",
                "text-opacity": 0.85
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
                "circle-stroke-color": "#0C0D10"
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
                "text-color": "#3E5D80",
                "text-halo-color": "#0C0D10",
                "text-halo-width": 1.2
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
                "text-color": "#DCE3F0",
                "text-halo-color": "#0C0D10",
                "text-halo-width": 2.0
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
                "text-color": "#A0AABA",
                "text-halo-color": "#0C0D10",
                "text-halo-width": 1.5
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
                "text-color": "#E2E8F0",
                "text-halo-color": "#0C0D10",
                "text-halo-width": 2.0
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
                "text-color": "#8EA3C7",
                "text-halo-color": "#0C0D10",
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
                "text-color": "#E2E7F0",
                "text-halo-color": "#0C0D10",
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
                "text-color": "#F0F3F8",
                "text-halo-color": "#0C0D10",
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
                "text-color": "#8C96A8",
                "text-halo-color": "#0C0D10",
                "text-halo-width": 1.5
              }
            }
          ]
        }
        """.trimIndent()
    }
}
