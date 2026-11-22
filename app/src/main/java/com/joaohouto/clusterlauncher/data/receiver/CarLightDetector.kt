package com.joaohouto.clusterlauncher.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Detector reativo da iluminação do veículo (farol / modo Dia-Noite).
 *
 * Combina duas fontes:
 * 1. Padrão Android (`Configuration.UI_MODE_NIGHT_MASK`), usado pelo Android Automotive,
 *    Android Auto, multimídias que sincronizam o fio ILL com o tema do sistema e agendamento solar.
 * 2. Broadcasts de fio ILL / farol de multimídias Android automotivas (FYT/SYU, Topway/TS10,
 *    MTC/MTCD, XYAuto e intents padrão de headlight).
 */
object CarLightDetector {

    private const val TAG = "CarLightDetector"

    private val ILL_ACTIONS = listOf(
        "android.intent.action.HEADLIGHT_STATE_CHANGED",
        "com.fyt.ill",
        "com.syu.ms.ill",
        "com.ts.MainUI.ILL",
        "com.xyauto.broadcast.ILL",
        "com.microntek.ill",
        "com.tw.ill"
    )

    private val _isSystemNightMode = MutableStateFlow(false)
    val isSystemNightMode: StateFlow<Boolean> = _isSystemNightMode.asStateFlow()

    /**
     * Estado direto do farol (fio ILL) quando reportado por broadcast automotivo.
     * `null` indica que a multimídia não enviou broadcast proprietário nesta sessão
     * (nesse caso usa-se [isSystemNightMode]).
     */
    private val _headlightOn = MutableStateFlow<Boolean?>(null)
    val headlightOn: StateFlow<Boolean?> = _headlightOn.asStateFlow()

    private var registeredReceiver: BroadcastReceiver? = null

    fun updateFromConfiguration(config: Configuration) {
        val isNight = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (_isSystemNightMode.value != isNight) {
            Log.d(TAG, "System uiMode night changed: isNight=$isNight")
            _isSystemNightMode.value = isNight
        }
    }

    fun register(context: Context) {
        updateFromConfiguration(context.resources.configuration)
        if (registeredReceiver != null) return

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent == null) return
                val parsed = parseHeadlightIntent(intent)
                if (parsed != null) {
                    Log.d(TAG, "Headlight ILL broadcast (${intent.action}): headlightOn=$parsed")
                    _headlightOn.value = parsed
                }
            }
        }

        try {
            val filter = IntentFilter().apply {
                ILL_ACTIONS.forEach { addAction(it) }
            }
            ContextCompat.registerReceiver(
                context.applicationContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            registeredReceiver = receiver
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register CarLightDetector receiver", e)
        }
    }

    fun unregister(context: Context) {
        val receiver = registeredReceiver ?: return
        try {
            context.applicationContext.unregisterReceiver(receiver)
        } catch (_: Exception) {
        } finally {
            registeredReceiver = null
        }
    }

    internal fun parseHeadlightIntent(intent: Intent): Boolean? {
        val extras = intent.extras ?: return null
        val candidateKeys = listOf("ill", "ILL", "headlight", "state", "status", "value", "on", "night")
        for (key in candidateKeys) {
            if (!extras.containsKey(key)) continue
            @Suppress("DEPRECATION")
            val raw = extras.get(key)
            when (raw) {
                is Boolean -> return raw
                is Int -> return raw != 0
                is String -> {
                    val lower = raw.trim().lowercase()
                    if (lower in listOf("1", "true", "on", "night")) return true
                    if (lower in listOf("0", "false", "off", "day")) return false
                }
            }
        }
        return null
    }
}
