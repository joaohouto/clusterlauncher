package com.joaohouto.clusterlauncher.data.model

import android.graphics.Bitmap
import android.net.Uri

data class MediaState(
    val title: String = "",
    val artist: String = "",
    val albumArtBitmap: Bitmap? = null,
    val albumArtUri: Uri? = null,
    val isPlaying: Boolean = false,
    val packageName: String? = null,
    val hasActiveSession: Boolean = false,
    val isPermissionGranted: Boolean = true
)
