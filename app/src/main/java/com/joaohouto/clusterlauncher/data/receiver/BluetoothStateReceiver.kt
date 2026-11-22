package com.joaohouto.clusterlauncher.data.receiver

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.joaohouto.clusterlauncher.data.local.PreferencesDataStore
import com.joaohouto.clusterlauncher.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BluetoothStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val wasConnected = _isBluetoothConnected.value
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> _isBluetoothConnected.value = true
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val stillConnected = checkBluetoothConnected(context)
                _isBluetoothConnected.value = stillConnected
                if (wasConnected && !stillConnected) {
                    maybeAutoPauseOnDisconnect(context)
                }
            }
            BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.STATE_DISCONNECTED)
                val nowConnected = (state == BluetoothAdapter.STATE_CONNECTED)
                _isBluetoothConnected.value = nowConnected
                if (wasConnected && !nowConnected) {
                    maybeAutoPauseOnDisconnect(context)
                }
            }
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)
                if (state != BluetoothAdapter.STATE_ON) {
                    _isBluetoothConnected.value = false
                    if (wasConnected) {
                        maybeAutoPauseOnDisconnect(context)
                    }
                }
            }
        }
    }

    private fun maybeAutoPauseOnDisconnect(context: Context) {
        val appContext = context.applicationContext
        receiverScope.launch {
            val enabled = runCatching {
                PreferencesDataStore.getInstance(appContext).isBtAutoPauseOnDisconnectEnabled()
            }.getOrDefault(false)
            if (enabled) {
                val controller = PlayerController.getInstance(appContext)
                if (controller.uiState.value.isPlaying) {
                    controller.playPause()
                }
            }
        }
    }

    companion object {
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val _isBluetoothConnected = MutableStateFlow(false)
        val isBluetoothConnected: StateFlow<Boolean> = _isBluetoothConnected.asStateFlow()

        fun checkInitialBluetoothState(context: Context) {
            _isBluetoothConnected.value = checkBluetoothConnected(context)
        }

        @SuppressLint("MissingPermission")
        private fun checkBluetoothConnected(context: Context): Boolean {
            return try {
                @Suppress("DEPRECATION")
                val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
                if (!adapter.isEnabled) return false
                val a2dp = adapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
                val headset = adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
                a2dp || headset
            } catch (_: SecurityException) {
                false
            } catch (_: Exception) {
                false
            }
        }
    }
}
