package com.joaohouto.clusterlauncher

import android.app.Application
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache

class ClusterLauncherApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
