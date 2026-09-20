package com.joaohouto.clusterlauncher.data.model

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.joaohouto.clusterlauncher.R

enum class DockSlotType(val id: Int, @param:StringRes val defaultTitleRes: Int, val defaultIcon: ImageVector) {
    ZLink(4, R.string.slot_zlink, Icons.Rounded.DirectionsCar),
    Gps(2, R.string.slot_gps, Icons.Rounded.Navigation),
    Radio(3, R.string.slot_radio, Icons.Rounded.Radio),
    Music(1, R.string.slot_music, Icons.Rounded.MusicNote),
    Settings(5, R.string.slot_settings, Icons.Rounded.Settings);

    companion object {
        fun fromId(id: Int): DockSlotType = entries.firstOrNull { it.id == id } ?: ZLink
    }
}

data class DockSlot(
    val type: DockSlotType,
    val targetPackage: String? = null
) {
    val id: Int get() = type.id
    val icon: ImageVector get() = type.defaultIcon
    @get:StringRes
    val titleRes: Int get() = type.defaultTitleRes
}
