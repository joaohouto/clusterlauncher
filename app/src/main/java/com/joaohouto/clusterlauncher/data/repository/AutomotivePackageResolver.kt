package com.joaohouto.clusterlauncher.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.joaohouto.clusterlauncher.R
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

    val KNOWN_BLUETOOTH_PACKAGES = listOf(
        // FYT / Joying / Teyes / Mekede / Navifly (UIS7862, UIS8581, etc.)
        "com.syu.bt",
        "com.syu.bluetooth",
        // Topway / TS10 / TS18 / TS7 / TS8 / TS9
        "com.ts.bt",
        "com.ts.main.bt",
        "com.ts.bluetooth",
        // Microntek / Rockchip / PX3 / PX5 / PX6 / Hal9k / Dasaita / Witson
        "com.microntek.bluetooth",
        "com.mtc.bluetooth",
        // XYAuto / Gongban / YT9216 / YT9217 / YT9218 / YT5760 / AC8227L
        "com.xyauto.bluetooth",
        "com.xyauto.bt",
        // Autochips / AC8257 / Junsun / Hizpo / Ossuret
        "com.autochips.bluetooth",
        "com.autochips.bt",
        // Allwinner / Softwinner
        "com.allwinner.bluetooth",
        "com.softwinner.bluetooth",
        // Nowada / NWD
        "com.nwd.bluetooth",
        "com.nwd.bt",
        // FlyAudio
        "com.flyaudio.bluetooth",
        "com.flyaudio.bt",
        // Ark / SoFIA
        "com.ark.bluetooth",
        // Outros sistemas automotivos comuns (TW, Car, Link, Zoulan, ECarX, CarLetter)
        "com.tw.bt",
        "com.car.bt",
        "com.link.bluetooth",
        "com.zoulan.bluetooth",
        "com.android.ecarx.bluetooth",
        "cn.manstep.phonemirrorbox",
        "com.carletter.carbt",
        // Android Automotive OS / Google Car
        "com.android.car.dialer",
        "com.android.car.settings"
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

    fun getLaunchIntent(pm: PackageManager, pkg: String): Intent? {
        val launchIntent = pm.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) return launchIntent

        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            `package` = pkg
        }
        val resolveList = pm.queryIntentActivities(mainIntent, 0)
        if (resolveList.isNotEmpty()) {
            val target = resolveList.first().activityInfo
            return Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(target.packageName, target.name)
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
        }
        return null
    }

    fun launchBluetooth(context: Context, preferredPackage: String? = null) {
        val pm = context.packageManager

        // 1. App explicitamente configurado pelo usuário nas preferências
        if (!preferredPackage.isNullOrEmpty()) {
            val intent = getLaunchIntent(pm, preferredPackage)
            if (intent != null) {
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return
                } catch (e: Exception) {
                    Log.e("AutomotiveResolver", "Falha ao abrir app Bluetooth configurado: $preferredPackage", e)
                }
            }
        }

        // 2. Busca entre os pacotes conhecidos de multimídias automotivas
        for (pkg in KNOWN_BLUETOOTH_PACKAGES) {
            if (isPackageInstalled(pm, pkg)) {
                val intent = getLaunchIntent(pm, pkg)
                if (intent != null) {
                    try {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        return
                    } catch (e: Exception) {
                        Log.e("AutomotiveResolver", "Falha ao abrir pacote automotivo: $pkg", e)
                    }
                }
            }
        }

        // 3. Procura dinamicamente nos apps com launcher instalados no sistema
        try {
            val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(launcherIntent, 0)
            val ownPackage = context.packageName

            // Prioridade 3a: Busca exata pelo nome do app (ex: "Bluetooth", "BT", "BT Phone", "Telefone", "Conexão Bluetooth")
            val exactLabelMatch = resolveInfos.firstOrNull { info ->
                val pkg = info.activityInfo.packageName
                if (pkg == ownPackage) return@firstOrNull false
                val label = info.loadLabel(pm).toString().trim()
                label.equals("Bluetooth", ignoreCase = true) ||
                label.equals("BT", ignoreCase = true) ||
                label.equals("BT Phone", ignoreCase = true) ||
                label.equals("Telefone BT", ignoreCase = true) ||
                label.equals("Car Bluetooth", ignoreCase = true) ||
                label.equals("Conexão Bluetooth", ignoreCase = true) ||
                label.equals("Telefone Bluetooth", ignoreCase = true)
            }
            if (exactLabelMatch != null) {
                val intent = getLaunchIntent(pm, exactLabelMatch.activityInfo.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return
                }
            }

            // Prioridade 3b: Match parcial no pacote ou no label
            val fuzzyMatch = resolveInfos.firstOrNull { info ->
                val pkg = info.activityInfo.packageName
                if (pkg == ownPackage || pkg.startsWith("com.android.bluetoothmidiservice")) return@firstOrNull false
                val label = info.loadLabel(pm).toString().trim().lowercase()
                val pkgLower = pkg.lowercase()

                pkgLower.contains(".bt") ||
                pkgLower.contains("bluetooth") ||
                pkgLower.contains("carbt") ||
                label.contains("bluetooth") ||
                label.startsWith("bt ") ||
                label.endsWith(" bt")
            }
            if (fuzzyMatch != null) {
                val intent = getLaunchIntent(pm, fuzzyMatch.activityInfo.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return
                }
            }
        } catch (e: Exception) {
            Log.e("AutomotiveResolver", "Falha ao buscar launcher apps para Bluetooth", e)
        }

        // 4. Fallback padrão: Tela de configurações de Bluetooth do Android (AOSP / OEM)
        try {
            val settingsIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (settingsIntent.resolveActivity(pm) != null) {
                context.startActivity(settingsIntent)
                return
            }
        } catch (e: Exception) {
            Log.e("AutomotiveResolver", "Falha ao iniciar ACTION_BLUETOOTH_SETTINGS", e)
        }

        // 5. Último fallback: Tela geral de configurações
        try {
            val generalSettings = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(generalSettings)
        } catch (e: Exception) {
            Log.e("AutomotiveResolver", "Falha ao iniciar ACTION_SETTINGS", e)
            Toast.makeText(context, context.getString(R.string.error_bluetooth_app_not_found), Toast.LENGTH_SHORT).show()
        }
    }

    fun resolveBluetoothAppInfo(context: Context, preferredPackage: String?): String {
        val pm = context.packageManager
        if (!preferredPackage.isNullOrEmpty()) {
            try {
                val appInfo = pm.getApplicationInfo(preferredPackage, 0)
                return pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                return preferredPackage
            }
        }
        for (pkg in KNOWN_BLUETOOTH_PACKAGES) {
            if (isPackageInstalled(pm, pkg)) {
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    return "${pm.getApplicationLabel(appInfo)} ($pkg)"
                } catch (_: Exception) {
                    return pkg
                }
            }
        }
        return context.getString(R.string.bluetooth_app_auto)
    }
}

