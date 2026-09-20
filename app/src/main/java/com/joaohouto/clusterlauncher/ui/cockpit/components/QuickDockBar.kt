package com.joaohouto.clusterlauncher.ui.cockpit.components

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joaohouto.clusterlauncher.data.model.DockSlot
import com.joaohouto.clusterlauncher.data.model.DockSlotType
import com.joaohouto.clusterlauncher.ui.components.MetallicButton

@Composable
fun QuickDockBar(
    slots: List<DockSlot>,
    onSlotLongClick: (DockSlot) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(118.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        slots.forEachIndexed { index, slot ->
            MetallicButton(
                onClick = { launchSlotApp(context, slot) { onSlotLongClick(slot) } },
                onLongClick = { onSlotLongClick(slot) },
                icon = slot.icon,
                text = stringResource(slot.titleRes),
                minSize = 118.dp,
                iconSize = 46.dp,
                textSize = 17.sp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )

            if (index < slots.size - 1) {
                Spacer(modifier = Modifier.width(12.dp))
            }
        }
    }
}

private fun launchSlotApp(
    context: Context,
    slot: DockSlot,
    onFallbackPicker: () -> Unit
) {
    val targetPkg = slot.targetPackage
    if (slot.type == DockSlotType.Settings && (targetPkg == null || targetPkg == "com.android.settings")) {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        } catch (_: Exception) {}
    }

    if (!targetPkg.isNullOrEmpty()) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            return
        }
    }

    // If package not found or not set, trigger slot picker
    onFallbackPicker()
}
