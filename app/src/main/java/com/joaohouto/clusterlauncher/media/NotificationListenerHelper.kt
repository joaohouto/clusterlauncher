package com.joaohouto.clusterlauncher.media

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object NotificationListenerHelper {
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val cn = ComponentName(context, MediaBridgeListenerService::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat?.contains(cn.flattenToString()) == true
    }
}
