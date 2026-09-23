package com.joaohouto.clusterlauncher.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repositório para gerenciar a telemetria do GPS nativo do veículo via LocationManager.
 * Opera 100% offline e sem dependências do Google Play Services.
 */
object GpsLocationRepository {

    private const val TAG = "GpsLocationRepository"

    private val _locationState = MutableStateFlow(GpsLocationData())
    val locationState: StateFlow<GpsLocationData> = _locationState.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private var locationManager: LocationManager? = null
    private var isListening = false
    private var gnssCallback: GnssStatus.Callback? = null
    private var lastGpsFixTime = 0L
    private var lastMovementLat = 0.0
    private var lastMovementLon = 0.0

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val isGps = location.provider == LocationManager.GPS_PROVIDER
            val now = System.currentTimeMillis()

            if (isGps) {
                lastGpsFixTime = now
            } else {
                // É NETWORK_PROVIDER ou PASSIVE_PROVIDER.
                // Se tivemos sinal GPS válido nos últimos 20 segundos, ignora totalmente o provedor de rede
                // para evitar saltos bruscos para o ponto inicial da viagem (célula celular ou roteador Wi-Fi).
                if (now - lastGpsFixTime < 20_000L) {
                    return
                }
                // Se não há GPS há mais de 20s, só aceita rede se tiver precisão razoável (<= 150m)
                if (location.hasAccuracy() && location.accuracy > 150f) {
                    return
                }
            }

            val current = _locationState.value
            // Rejeita updates com timestamp desatualizado em relação ao estado corrente
            if (current.hasFix && location.time < current.timestamp - 1000L) {
                return
            }

            val rawSpeedKmh = if (location.hasSpeed()) (location.speed * 3.6f) else 0f
            var speedKmh = if (rawSpeedKmh >= 2.0f) rawSpeedKmh else 0f

            // Calcula o vetor de movimentação real do veículo entre coordenadas consecutivas
            var movementBearing: Float? = null
            if (lastMovementLat != 0.0 && lastMovementLon != 0.0) {
                val distanceResults = FloatArray(2)
                Location.distanceBetween(
                    lastMovementLat,
                    lastMovementLon,
                    location.latitude,
                    location.longitude,
                    distanceResults
                )
                val distanceMeters = distanceResults[0]
                if (distanceMeters >= 2.5f) {
                    // Deslocamento significativo detectado: calcula o rumo vetorial real
                    movementBearing = (distanceResults[1] + 360f) % 360f
                    lastMovementLat = location.latitude
                    lastMovementLon = location.longitude

                    // Se o chip GPS não forneceu velocidade direta, estima pela distância e tempo
                    if (speedKmh == 0f && current.timestamp > 0 && location.time > current.timestamp) {
                        val deltaSec = (location.time - current.timestamp) / 1000f
                        if (deltaSec in 0.5f..5.0f) {
                            val computedSpeed = (distanceMeters / deltaSec) * 3.6f
                            if (computedSpeed >= 2.0f) {
                                speedKmh = computedSpeed
                            }
                        }
                    }
                }
            } else {
                lastMovementLat = location.latitude
                lastMovementLon = location.longitude
            }

            // Alvo de rumo: prioriza o rumo nativo de hardware se válido; caso contrário, usa o deslocamento real
            val targetBearingCandidate = when {
                location.hasBearing() && location.bearing != 0f -> location.bearing
                movementBearing != null -> movementBearing
                else -> null
            }

            // Atualiza o rumo (bearing) somente se o veículo estiver em deslocamento real (>= 2.5 km/h).
            // Com o carro parado, o chip de GPS gera ruído aleatório de azimute; mantemos o último rumo fixo.
            val newBearing = if (speedKmh >= 2.5f && targetBearingCandidate != null) {
                val target = targetBearingCandidate
                if (current.bearing == 0f) {
                    // Primeiro rumo detectado após inicialização: assume imediatamente sem lag do Norte
                    target
                } else {
                    var diff = (target - current.bearing) % 360f
                    if (diff > 180f) diff -= 360f
                    if (diff < -180f) diff += 360f
                    val absDiff = kotlin.math.abs(diff)
                    if (absDiff < 1.5f) {
                        current.bearing
                    } else {
                        // Fator de suavização adaptativo:
                        // - Oscilações pequenas (< 15°): suave (0.35f) para filtrar ruídos em linha reta
                        // - Curvas médias (15° a 45°): responsivo (0.65f)
                        // - Curvas acentuadas ou retorno (>= 45°): resposta rápida (0.90f) ao volante
                        val factor = when {
                            absDiff >= 45f -> 0.90f
                            absDiff >= 15f -> 0.65f
                            else -> 0.35f
                        }
                        (current.bearing + diff * factor + 360f) % 360f
                    }
                }
            } else {
                current.bearing
            }

            _locationState.value = current.copy(
                latitude = location.latitude,
                longitude = location.longitude,
                bearing = newBearing,
                speedKmh = speedKmh,
                altitude = if (location.hasAltitude()) location.altitude else current.altitude,
                hasFix = true,
                timestamp = location.time
            )
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) {
                _locationState.value = _locationState.value.copy(hasFix = false)
            }
        }
    }

    /**
     * Atualiza o estado da permissão de localização.
     */
    fun checkPermission(context: Context): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        _hasPermission.value = granted
        return granted
    }

    /**
     * Inicia a escuta das atualizações de GPS.
     */
    fun startListening(context: Context) {
        if (isListening) return

        if (!checkPermission(context)) {
            Log.d(TAG, "Permissão de localização não concedida.")
            return
        }

        try {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (manager == null) {
                Log.w(TAG, "LocationManager indisponível.")
                return
            }
            locationManager = manager

            // Tenta obter a última localização conhecida somente se não tivermos uma localização ativa recente
            val current = _locationState.value
            val isCurrentFixValid = current.hasFix && current.latitude != 0.0 &&
                    (System.currentTimeMillis() - current.timestamp < 120_000L)

            if (!isCurrentFixValid) {
                val lastGps = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val isLastGpsFresh = lastGps != null && (System.currentTimeMillis() - lastGps.time < 30 * 60 * 1000L)

                val initial = if (isLastGpsFresh) {
                    lastGps
                } else {
                    val lastNetwork = try {
                        manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    } catch (_: Exception) {
                        null
                    }
                    if (lastNetwork != null && (!lastNetwork.hasAccuracy() || lastNetwork.accuracy <= 150f)) {
                        lastNetwork
                    } else {
                        lastGps
                    }
                }

                if (initial != null) {
                    _locationState.value = _locationState.value.copy(
                        latitude = initial.latitude,
                        longitude = initial.longitude,
                        bearing = if (initial.hasBearing()) initial.bearing else 0f,
                        speedKmh = if (initial.hasSpeed()) (initial.speed * 3.6f) else 0f,
                        altitude = initial.altitude,
                        hasFix = true,
                        timestamp = initial.time
                    )
                    lastMovementLat = initial.latitude
                    lastMovementLon = initial.longitude
                }
            }

            // Registra updates a cada 1000ms (1 segundo) e 0 metros
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                manager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0f,
                    locationListener
                )
            }

            // Registra também network/passivo se disponível para fallback rápido
            try {
                if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    manager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        3000L,
                        0f,
                        locationListener
                    )
                }
            } catch (_: Exception) {
                // Network provider pode não existir em multimídias AOSP
            }

            // Callback GNSS para contagem de satélites (API 24+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                gnssCallback = object : GnssStatus.Callback() {
                    override fun onSatelliteStatusChanged(status: GnssStatus) {
                        var usedCount = 0
                        for (i in 0 until status.satelliteCount) {
                            if (status.usedInFix(i)) {
                                usedCount++
                            }
                        }
                        _locationState.value = _locationState.value.copy(
                            satellitesCount = usedCount,
                            hasFix = usedCount > 0 || _locationState.value.hasFix
                        )
                    }
                }
                manager.registerGnssStatusCallback(context.mainExecutor, gnssCallback!!)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                gnssCallback = object : GnssStatus.Callback() {
                    override fun onSatelliteStatusChanged(status: GnssStatus) {
                        var usedCount = 0
                        for (i in 0 until status.satelliteCount) {
                            if (status.usedInFix(i)) {
                                usedCount++
                            }
                        }
                        _locationState.value = _locationState.value.copy(
                            satellitesCount = usedCount,
                            hasFix = usedCount > 0 || _locationState.value.hasFix
                        )
                    }
                }
                @Suppress("DEPRECATION")
                manager.registerGnssStatusCallback(gnssCallback!!)
            }

            isListening = true
            Log.d(TAG, "GPS Listener iniciado com sucesso.")
        } catch (e: SecurityException) {
            Log.e(TAG, "Erro de segurança ao iniciar GPS", e)
            _hasPermission.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar GPS", e)
        }
    }

    /**
     * Interrompe a escuta do GPS para economizar energia/processamento.
     */
    fun stopListening() {
        if (!isListening) return
        try {
            locationManager?.removeUpdates(locationListener)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssCallback != null) {
                locationManager?.unregisterGnssStatusCallback(gnssCallback!!)
                gnssCallback = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao parar GPS Listener", e)
        } finally {
            isListening = false
            Log.d(TAG, "GPS Listener desativado.")
        }
    }
}
