package com.joaohouto.clusterlauncher.data.receiver

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BluetoothStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> _isBluetoothConnected.value = true
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> _isBluetoothConnected.value = checkBluetoothConnected(context)
            BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.STATE_DISCONNECTED)
                _isBluetoothConnected.value = (state == BluetoothAdapter.STATE_CONNECTED)
            }
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)
                if (state != BluetoothAdapter.STATE_ON) {
                    _isBluetoothConnected.value = false
                }
            }
        }
    }

    companion object {
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
