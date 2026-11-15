package com.joaohouto.clusterlauncher.data.model

import android.net.Uri
import androidx.compose.runtime.Immutable
import java.util.Locale

@Immutable
data class Folder(
    val path: String,
    val name: String,
    val trackCount: Int
) {
    val isUsbOrExternal: Boolean
        get() {
            val lower = path.lowercase(Locale.ROOT)
            if (lower.startsWith("/storage/emulated") || lower.startsWith("/sdcard")) {
                return false
            }
            return lower.startsWith("/mnt/usb") ||
                lower.startsWith("/mnt/udisk") ||
                lower.startsWith("/mnt/extsd") ||
                lower.startsWith("/mnt/external_sd") ||
                lower.startsWith("/mnt/media_rw") ||
                lower.startsWith("/storage/")
        }
}

@Immutable
data class Track(
    val id: Long = 0,
    val uri: String,
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val folderPath: String,
    val trackNumber: Int = 0
) {
    val contentUri: Uri
        get() = Uri.parse(uri)
}
