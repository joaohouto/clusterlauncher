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
import androidx.compose.material.icons.rounded.Usb
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
import com.joaohouto.clusterlauncher.data.receiver.UsbStateReceiver
import com.joaohouto.clusterlauncher.ui.theme.NeedleRed
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCard
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCardBorder
import com.joaohouto.clusterlauncher.ui.theme.TextDisabled
import com.joaohouto.clusterlauncher.ui.theme.TextPrimary
import com.joaohouto.clusterlauncher.ui.theme.TextSecondary

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemStatusRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    onSettingsClick: () -> Unit = {}
) {
    val isUsbConnected by UsbStateReceiver.isUsbConnected.collectAsState()
    val isBluetoothConnected by BluetoothStateReceiver.isBluetoothConnected.collectAsState()

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatusBadge(
            icon = Icons.Rounded.Usb,
            label = if (isUsbConnected) stringResource(R.string.usb_connected) else stringResource(R.string.usb_disconnected),
            isActive = isUsbConnected
        )
        StatusBadge(
            icon = if (isBluetoothConnected) Icons.Rounded.BluetoothConnected else Icons.Rounded.Bluetooth,
            label = if (isBluetoothConnected) stringResource(R.string.bluetooth_connected) else stringResource(R.string.bluetooth_disconnected),
            isActive = isBluetoothConnected
        )
        ActionBadge(
            icon = Icons.Rounded.Settings,
            label = stringResource(R.string.settings),
            onClick = onSettingsClick
        )
    }
}

@Composable
private fun StatusBadge(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val tint = if (isActive) NeedleRed else TextDisabled
    val textColor = if (isActive) TextPrimary else TextDisabled

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCard)
            .border(1.dp, SurfaceCardBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ActionBadge(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCard)
            .border(1.dp, NeedleRed.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = NeedleRed,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

