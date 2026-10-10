package com.joaohouto.clusterlauncher.ui.cockpit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joaohouto.clusterlauncher.R
import com.joaohouto.clusterlauncher.data.receiver.BluetoothStateReceiver
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterAccent
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCard
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCardBorder
import com.joaohouto.clusterlauncher.ui.theme.TextDisabled
import com.joaohouto.clusterlauncher.ui.theme.TextPrimary
import com.joaohouto.clusterlauncher.ui.theme.TextSecondary

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemStatusRow(
    modifier: Modifier = Modifier,
    isLarge: Boolean = false,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(if (isLarge) 12.dp else 10.dp, Alignment.CenterHorizontally),
    onBluetoothClick: () -> Unit = {},
    onBluetoothLongClick: (() -> Unit)? = null,
    onSettingsClick: () -> Unit = {}
) {
    val isBluetoothConnected by BluetoothStateReceiver.isBluetoothConnected.collectAsState()

    FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatusBadge(
            icon = if (isBluetoothConnected) Icons.Rounded.BluetoothConnected else Icons.Rounded.Bluetooth,
            label = if (isBluetoothConnected) stringResource(R.string.bluetooth_connected) else stringResource(R.string.bluetooth_disconnected),
            isActive = isBluetoothConnected,
            isLarge = isLarge,
            onClick = onBluetoothClick,
            onLongClick = onBluetoothLongClick
        )
        ActionBadge(
            icon = Icons.Rounded.Settings,
            label = stringResource(R.string.settings),
            isLarge = isLarge,
            onClick = onSettingsClick
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StatusBadge(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    isLarge: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val accent = LocalClusterAccent.current.primary
    val tint = if (isActive) accent else TextDisabled
    val textColor = if (isActive) TextPrimary else TextDisabled
    val shapeRadius = if (isLarge) 24.dp else 20.dp
    val borderColor = if (isActive) accent.copy(alpha = 0.5f) else SurfaceCardBorder

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(shapeRadius))
            .background(SurfaceCard)
            .border(1.dp, borderColor, RoundedCornerShape(shapeRadius))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(
                horizontal = if (isLarge) 16.dp else 12.dp,
                vertical = if (isLarge) 9.dp else 6.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(if (isLarge) 26.dp else 22.dp)
        )
        Spacer(modifier = Modifier.width(if (isLarge) 10.dp else 8.dp))
        Text(
            text = label,
            color = textColor,
            fontSize = if (isLarge) 16.sp else 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ActionBadge(
    icon: ImageVector,
    label: String,
    isLarge: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalClusterAccent.current.primary
    val shapeRadius = if (isLarge) 24.dp else 20.dp

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(shapeRadius))
            .background(SurfaceCard)
            .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(shapeRadius))
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (isLarge) 16.dp else 12.dp,
                vertical = if (isLarge) 9.dp else 6.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = accent,
            modifier = Modifier.size(if (isLarge) 26.dp else 22.dp)
        )
        Spacer(modifier = Modifier.width(if (isLarge) 10.dp else 8.dp))
        Text(
            text = label,
            color = TextPrimary,
            fontSize = if (isLarge) 16.sp else 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

