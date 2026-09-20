package com.joaohouto.clusterlauncher.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.storage.StorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UsbStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_MEDIA_MOUNTED -> _isUsbConnected.value = true
            Intent.ACTION_MEDIA_UNMOUNTED, Intent.ACTION_MEDIA_EJECT, Intent.ACTION_MEDIA_REMOVED -> {
                _isUsbConnected.value = checkUsbStorage(context)
            }
        }
    }

    companion object {
        private val _isUsbConnected = MutableStateFlow(false)
        val isUsbConnected: StateFlow<Boolean> = _isUsbConnected.asStateFlow()

        fun checkInitialUsbState(context: Context) {
            _isUsbConnected.value = checkUsbStorage(context)
        }

        private fun checkUsbStorage(context: Context): Boolean {
            return try {
                val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
                val storageVolumes = sm?.storageVolumes ?: return false
                storageVolumes.any { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
            } catch (_: Exception) {
                false
            }
        }
    }
}
