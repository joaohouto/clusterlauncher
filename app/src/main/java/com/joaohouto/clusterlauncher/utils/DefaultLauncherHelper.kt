package com.joaohouto.clusterlauncher.utils

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DefaultLauncherHelper {

    private val _isDefaultLauncher = MutableStateFlow(false)
    val isDefaultLauncher: StateFlow<Boolean> = _isDefaultLauncher.asStateFlow()

    /**
     * Updates the StateFlow representing whether ClusterLauncher is the default home app.
     */
    fun updateDefaultLauncherState(context: Context) {
        _isDefaultLauncher.value = checkIsDefaultLauncher(context)
    }

    /**
     * Checks whether Cluster Launcher is currently designated as the default Home launcher.
     */
    private fun checkIsDefaultLauncher(context: Context): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.resolveActivity(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            val resolvedPackage = resolveInfo?.activityInfo?.packageName
            resolvedPackage != null && resolvedPackage == context.packageName
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Prompts the user to set this launcher as the default home application.
     * Uses RoleManager on Android 10+ (API 29+) with fallbacks for automotive units.
     */
    fun requestSetDefaultLauncher(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                try {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    return
                } catch (_: Exception) {}
            }
        }

        // Fallback 1: Home Settings
        try {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return
        } catch (_: Exception) {}

        // Fallback 2: Manage Default Apps
        try {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return
        } catch (_: Exception) {}

        // Fallback 3: Standard Settings
        try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
