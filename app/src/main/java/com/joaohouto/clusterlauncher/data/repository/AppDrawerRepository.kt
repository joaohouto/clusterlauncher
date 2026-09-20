package com.joaohouto.clusterlauncher.data.repository

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.joaohouto.clusterlauncher.data.model.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppDrawerRepository(private val context: Context) {

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceAtLeast(1024)
    private val iconCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    @Volatile
    private var cachedApps: List<AppItem>? = null
    @Volatile
    private var cachedPackageSignatures: Set<String>? = null

    suspend fun getInstalledApps(forceReload: Boolean = false): List<AppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val ownPackage = context.packageName

        val currentPackages = resolveInfos
            .filter { it.activityInfo.packageName != ownPackage }
            .map { it.activityInfo.packageName }
            .toSet()

        // If packages haven't changed and we already have cached apps, return instantly without disk reads
        if (!forceReload && cachedApps != null && cachedPackageSignatures == currentPackages) {
            return@withContext cachedApps!!
        }

        val apps = resolveInfos
            .filter { it.activityInfo.packageName != ownPackage }
            .map { resolveInfo ->
                val pkgName = resolveInfo.activityInfo.packageName
                val label = resolveInfo.loadLabel(pm).toString()
                val cachedIcon = iconCache.get(pkgName)
                val icon = cachedIcon ?: try {
                    val drawable = resolveInfo.loadIcon(pm)
                    val bmp = drawableToRgb565Bitmap(drawable)
                    if (bmp != null) {
                        iconCache.put(pkgName, bmp)
                    }
                    bmp
                } catch (_: Exception) {
                    null
                }
                AppItem(
                    packageName = pkgName,
                    label = label,
                    icon = icon
                )
            }
            .sortedBy { it.label.lowercase() }

        cachedPackageSignatures = currentPackages
        cachedApps = apps
        apps
    }

    private fun drawableToRgb565Bitmap(drawable: Drawable): Bitmap? {
        return try {
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth.coerceIn(96, 192) else 144
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight.coerceIn(96, 192) else 144
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}
