package com.joaohouto.clusterlauncher.data.repository

import android.content.Context
import android.content.pm.PackageManager
import com.joaohouto.clusterlauncher.data.model.DockSlotType

object AutomotivePackageResolver {
    val KNOWN_MUSIC_PACKAGES = listOf(
        "com.joaohouto.clusterplayer",
        "com.spotify.music",
        "com.google.android.music"
    )

    val KNOWN_RADIO_PACKAGES = listOf(
        "com.syu.radio",
        "com.microntek.radio",
        "com.ts.radiostation",
        "com.car.radio",
        "com.yecon.radio",
        "com.autochips.radio"
    )

    val KNOWN_ZLINK_PACKAGES = listOf(
        "com.zjinnova.zlink5",
        "com.zjinnova.zlink",
        "com.zjinnova.zlink.auto",
        "com.syu.zlink",
        "com.autokit.apk",
        "com.carplay.zlink",
        "com.google.android.projection.gearhead",
        "com.ts.carlink",
        "com.syu.carlink"
    )

    val KNOWN_GPS_PACKAGES = listOf(
        "com.google.android.apps.maps",
        "com.waze",
        "com.here.app.maps"
    )

    fun resolveDefaultPackage(context: Context, slotType: DockSlotType): String? {
        val pm = context.packageManager
        val candidates = when (slotType) {
            DockSlotType.ZLink -> KNOWN_ZLINK_PACKAGES
            DockSlotType.Gps -> KNOWN_GPS_PACKAGES
            DockSlotType.Radio -> KNOWN_RADIO_PACKAGES
            DockSlotType.Music -> KNOWN_MUSIC_PACKAGES
            DockSlotType.Settings -> listOf("com.android.settings")
        }
        return candidates.firstOrNull { isPackageInstalled(pm, it) }
    }

    fun isPackageInstalled(pm: PackageManager, pkg: String): Boolean {
        return try {
            pm.getPackageInfo(pkg, 0)
            true
        } catch (_: Exception) {
            false
        }
    }
}
