package com.joaohouto.clusterlauncher.data.model

enum class CockpitLayoutPreset(val id: String) {
    CLASSIC("classic"),
    ANDROID_AUTO("android_auto");

    companion object {
        fun fromId(id: String?): CockpitLayoutPreset {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CLASSIC
        }
    }
}
