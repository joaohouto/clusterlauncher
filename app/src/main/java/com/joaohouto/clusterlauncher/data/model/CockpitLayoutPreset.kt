package com.joaohouto.clusterlauncher.data.model

enum class CockpitLayoutPreset(val id: String) {
    ANDROID_AUTO("android_auto");

    companion object {
        fun fromId(id: String?): CockpitLayoutPreset {
            return ANDROID_AUTO
        }
    }
}
