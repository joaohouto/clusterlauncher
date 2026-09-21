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

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val current = _locationState.value
            val rawSpeedKmh = if (location.hasSpeed()) (location.speed * 3.6f) else 0f
            // Filtra oscilação mínima de GPS estático (abaixo de 2.0 km/h o carro está essencialmente parado)
            val speedKmh = if (rawSpeedKmh >= 2.0f) rawSpeedKmh else 0f

            // Atualiza o rumo (bearing) somente se o veículo estiver em deslocamento real (>= 3.0 km/h).
            // Com o carro parado, o chip de GPS gera ruído aleatório de azimute; mantemos o último rumo fixo.
            val newBearing = if (speedKmh >= 3.0f && location.hasBearing() && location.bearing != 0f) {
                val target = location.bearing
                var diff = (target - current.bearing) % 360f
                if (diff > 180f) diff -= 360f
                if (diff < -180f) diff += 360f
                if (kotlin.math.abs(diff) < 2.5f) {
                    current.bearing
                } else {
                    (current.bearing + diff * 0.35f + 360f) % 360f
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

            // Tenta obter a última localização conhecida para inicialização rápida
            val lastGps = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNetwork = try {
                manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } catch (_: Exception) {
                null
            }
            val initial = lastGps ?: lastNetwork
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
