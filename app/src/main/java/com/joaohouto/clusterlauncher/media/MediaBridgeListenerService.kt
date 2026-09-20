package com.joaohouto.clusterlauncher.media

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService

class MediaBridgeListenerService : NotificationListenerService() {

    private var mediaSessionManager: MediaSessionManager? = null
    private var componentName: ComponentName? = null

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        MediaManager.onSessionsChanged(controllers ?: emptyList())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        MediaManager.setPermissionGranted(true)
        val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        mediaSessionManager = msm
        val cn = ComponentName(this, MediaBridgeListenerService::class.java)
        componentName = cn

        if (msm != null) {
            try {
                msm.addOnActiveSessionsChangedListener(sessionsChangedListener, cn)
                val controllers = msm.getActiveSessions(cn)
                MediaManager.onSessionsChanged(controllers)
            } catch (_: SecurityException) {
                MediaManager.setPermissionGranted(false)
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        } catch (_: Exception) {}
        mediaSessionManager = null
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        } catch (_: Exception) {}
    }
}
