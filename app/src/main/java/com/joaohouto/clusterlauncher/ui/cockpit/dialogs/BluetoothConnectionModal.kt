package com.joaohouto.clusterlauncher.ui.cockpit.dialogs

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.joaohouto.clusterlauncher.R
import com.joaohouto.clusterlauncher.data.bluetooth.BluetoothConnectionManager
import com.joaohouto.clusterlauncher.data.bluetooth.BtDeviceCategory
import com.joaohouto.clusterlauncher.data.bluetooth.BtDeviceItem
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterAccent
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterColors
import com.joaohouto.clusterlauncher.ui.theme.LocalUiScale
import kotlinx.coroutines.delay

/**
 * Interface própria de gerenciamento e conexão Bluetooth com visual do Cockpit.
 * Mantém botão de fallback para abrir o app Bluetooth nativo da multimídia (centrais MCU).
 */
@Composable
fun BluetoothConnectionModal(
    fullScreenMode: Boolean = false,
    onOpenSystemBluetooth: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val state by BluetoothConnectionManager.uiState.collectAsState()
    val accent = LocalClusterAccent.current.primary
    val colors = LocalClusterColors.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        BluetoothConnectionManager.startObserving(context)
    }

    val btActionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        BluetoothConnectionManager.refreshState(context)
    }

    DisposableEffect(Unit) {
        BluetoothConnectionManager.startObserving(context)
        onDispose {
            BluetoothConnectionManager.stopObserving(context)
        }
    }

    LaunchedEffect(state.statusMessage) {
        if (!state.statusMessage.isNullOrBlank()) {
            delay(4500L)
            BluetoothConnectionManager.clearStatusMessage()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { win ->
                val insetsController = WindowCompat.getInsetsController(win, win.decorView)
                if (fullScreenMode) {
                    insetsController.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    insetsController.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        val baseDensity = LocalDensity.current
        val uiScale = LocalUiScale.current
        val dialogScaledDensity = remember(baseDensity.density, baseDensity.fontScale, uiScale) {
            Density(
                density = baseDensity.density * uiScale,
                fontScale = baseDensity.fontScale
            )
        }

        CompositionLocalProvider(LocalDensity provides dialogScaledDensity) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .fillMaxHeight(0.86f)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, colors.surfaceCardBorder, RoundedCornerShape(20.dp)),
                color = colors.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Cabeçalho
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (state.isEnabled) accent.copy(alpha = 0.18f) else colors.surfaceCard)
                                    .border(
                                        1.dp,
                                        if (state.isEnabled) accent.copy(alpha = 0.6f) else colors.surfaceCardBorder,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (state.isEnabled) Icons.Rounded.BluetoothConnected else Icons.Rounded.BluetoothDisabled,
                                    contentDescription = null,
                                    tint = if (state.isEnabled) accent else colors.textDisabled,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = stringResource(R.string.bt_modal_title),
                                    color = colors.textPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                val subtitle = when {
                                    !state.isSupported -> stringResource(R.string.bt_modal_unsupported_short)
                                    !state.hasPermissions -> stringResource(R.string.bt_modal_permission_short)
                                    !state.isEnabled -> stringResource(R.string.bt_modal_disabled_short)
                                    state.localName.isNotBlank() -> stringResource(R.string.bt_modal_visible_as, state.localName)
                                    else -> stringResource(R.string.bt_modal_ready)
                                }
                                Text(
                                    text = subtitle,
                                    color = colors.textSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (state.isSupported && state.hasPermissions && state.isEnabled) {
                                // Tornar visível para pareamento
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            runCatching {
                                                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                                                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
                                                }
                                                btActionLauncher.launch(intent)
                                            }
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (state.isDiscoverable) accent.copy(alpha = 0.18f) else colors.surfaceCard,
                                    border = BorderStroke(
                                        1.dp,
                                        if (state.isDiscoverable) accent else colors.surfaceCardBorder
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Visibility,
                                            contentDescription = null,
                                            tint = if (state.isDiscoverable) accent else colors.textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = stringResource(
                                                if (state.isDiscoverable) R.string.bt_modal_discoverable_on
                                                else R.string.bt_modal_make_discoverable
                                            ),
                                            color = if (state.isDiscoverable) accent else colors.textPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            if (state.isSupported && state.hasPermissions) {
                                Switch(
                                    checked = state.isEnabled,
                                    onCheckedChange = { enable ->
                                        if (enable) {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                runCatching {
                                                    btActionLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                                }
                                            } else if (!BluetoothConnectionManager.setBluetoothEnabledLegacy(context, true)) {
                                                runCatching {
                                                    btActionLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                                }
                                            }
                                        } else {
                                            if (!BluetoothConnectionManager.setBluetoothEnabledLegacy(context, false)) {
                                                onOpenSystemBluetooth()
                                            }
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = accent,
                                        uncheckedThumbColor = colors.textDisabled,
                                        uncheckedTrackColor = colors.surfaceCard
                                    )
                                )
                            }

                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.btn_close),
                                    tint = colors.textSecondary
                                )
                            }
                        }
                    }

                    // Mensagem de status temporária
                    state.statusMessage?.let { msg ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = accent.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = msg,
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }

                    // Corpo principal
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        when {
                            !state.isSupported -> {
                                BtCenterNoticeCard(
                                    icon = Icons.Rounded.BluetoothDisabled,
                                    title = stringResource(R.string.bt_modal_unsupported_title),
                                    description = stringResource(R.string.bt_modal_unsupported_desc),
                                    actionLabel = stringResource(R.string.bt_modal_open_system),
                                    onAction = onOpenSystemBluetooth
                                )
                            }

                            !state.hasPermissions -> {
                                BtCenterNoticeCard(
                                    icon = Icons.Rounded.Bluetooth,
                                    title = stringResource(R.string.bt_modal_permission_title),
                                    description = stringResource(R.string.bt_modal_permission_desc),
                                    actionLabel = stringResource(R.string.btn_grant),
                                    onAction = {
                                        permissionLauncher.launch(BluetoothConnectionManager.requiredPermissions())
                                    }
                                )
                            }

                            !state.isEnabled -> {
                                BtCenterNoticeCard(
                                    icon = Icons.Rounded.BluetoothDisabled,
                                    title = stringResource(R.string.bt_modal_off_title),
                                    description = stringResource(R.string.bt_modal_off_desc),
                                    actionLabel = stringResource(R.string.bt_modal_turn_on),
                                    onAction = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ||
                                            !BluetoothConnectionManager.setBluetoothEnabledLegacy(context, true)
                                        ) {
                                            runCatching {
                                                btActionLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                            }
                                        }
                                    }
                                )
                            }

                            else -> {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // Coluna Esquerda: Dispositivos Pareados
                                    Surface(
                                        modifier = Modifier
                                            .weight(1.15f)
                                            .fillMaxHeight(),
                                        shape = RoundedCornerShape(14.dp),
                                        color = colors.surfaceCard,
                                        border = BorderStroke(1.dp, colors.surfaceCardBorder)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.bt_modal_paired_devices),
                                                    color = colors.textPrimary,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                IconButton(
                                                    onClick = { BluetoothConnectionManager.refreshState(context) },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Refresh,
                                                        contentDescription = stringResource(R.string.btn_refresh),
                                                        tint = colors.textSecondary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }

                                            if (state.pairedDevices.isEmpty()) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.bt_modal_no_paired),
                                                        color = colors.textDisabled,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                            } else {
                                                LazyColumn(
                                                    modifier = Modifier.fillMaxSize(),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    items(
                                                        items = state.pairedDevices,
                                                        key = { it.address }
                                                    ) { device ->
                                                        PairedDeviceRow(
                                                            device = device,
                                                            onToggleConnect = {
                                                                if (device.isConnected) {
                                                                    BluetoothConnectionManager.disconnectDevice(context, device)
                                                                } else {
                                                                    BluetoothConnectionManager.connectDevice(context, device)
                                                                }
                                                            },
                                                            onUnpair = {
                                                                BluetoothConnectionManager.unpairDevice(context, device)
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Coluna Direita: Dispositivos Próximos (Descoberta)
                                    Surface(
                                        modifier = Modifier
                                            .weight(0.85f)
                                            .fillMaxHeight(),
                                        shape = RoundedCornerShape(14.dp),
                                        color = colors.surfaceCard,
                                        border = BorderStroke(1.dp, colors.surfaceCardBorder)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.bt_modal_nearby_devices),
                                                    color = colors.textPrimary,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )

                                                Surface(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            if (state.isDiscovering) {
                                                                BluetoothConnectionManager.stopDiscovery(context)
                                                            } else {
                                                                BluetoothConnectionManager.startDiscovery(context)
                                                            }
                                                        },
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (state.isDiscovering) accent.copy(alpha = 0.18f) else colors.surfaceCardElevated,
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (state.isDiscovering) accent else colors.surfaceCardBorder
                                                    )
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        if (state.isDiscovering) {
                                                            CircularProgressIndicator(
                                                                color = accent,
                                                                modifier = Modifier.size(13.dp),
                                                                strokeWidth = 2.dp
                                                            )
                                                        } else {
                                                            Icon(
                                                                imageVector = Icons.AutoMirrored.Rounded.BluetoothSearching,
                                                                contentDescription = null,
                                                                tint = accent,
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                        }
                                                        Text(
                                                            text = stringResource(
                                                                if (state.isDiscovering) R.string.bt_modal_stop_scan
                                                                else R.string.bt_modal_scan
                                                            ),
                                                            color = if (state.isDiscovering) accent else colors.textPrimary,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }
                                            }

                                            if (state.discoveredDevices.isEmpty()) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = stringResource(
                                                            if (state.isDiscovering) R.string.bt_modal_scanning_hint
                                                            else R.string.bt_modal_tap_scan_hint
                                                        ),
                                                        color = colors.textDisabled,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            } else {
                                                LazyColumn(
                                                    modifier = Modifier.fillMaxSize(),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    items(
                                                        items = state.discoveredDevices,
                                                        key = { it.address }
                                                    ) { device ->
                                                        DiscoveredDeviceRow(
                                                            device = device,
                                                            onPair = {
                                                                BluetoothConnectionManager.pairDevice(context, device)
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Rodapé: Fallback para Bluetooth nativo da multimídia (centrais MCU)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.bt_modal_mcu_hint),
                            color = colors.textDisabled,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onOpenSystemBluetooth() },
                            shape = RoundedCornerShape(10.dp),
                            color = colors.surfaceCard,
                            border = BorderStroke(1.dp, colors.surfaceCardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.bt_modal_open_system),
                                    color = colors.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = null,
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PairedDeviceRow(
    device: BtDeviceItem,
    onToggleConnect: () -> Unit,
    onUnpair: () -> Unit
) {
    val accent = LocalClusterAccent.current.primary
    val colors = LocalClusterColors.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggleConnect),
        shape = RoundedCornerShape(12.dp),
        color = if (device.isConnected) accent.copy(alpha = 0.14f) else colors.backgroundSecondary,
        border = BorderStroke(
            width = 1.dp,
            color = if (device.isConnected) accent.copy(alpha = 0.7f) else colors.surfaceCardBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = categoryIcon(device.category),
                contentDescription = null,
                tint = if (device.isConnected) accent else colors.textSecondary,
                modifier = Modifier.size(22.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (device.isConnected) {
                        stringResource(R.string.bt_device_connected)
                    } else {
                        stringResource(R.string.bt_device_paired)
                    },
                    color = if (device.isConnected) accent else colors.textDisabled,
                    fontSize = 11.sp,
                    fontWeight = if (device.isConnected) FontWeight.Bold else FontWeight.Normal
                )
            }

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleConnect),
                shape = RoundedCornerShape(8.dp),
                color = if (device.isConnected) colors.surfaceCardElevated else accent.copy(alpha = 0.18f),
                border = BorderStroke(
                    1.dp,
                    if (device.isConnected) colors.surfaceCardBorder else accent.copy(alpha = 0.55f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = if (device.isConnected) Icons.Rounded.LinkOff else Icons.Rounded.Link,
                        contentDescription = null,
                        tint = if (device.isConnected) colors.textPrimary else accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(
                            if (device.isConnected) R.string.bt_btn_disconnect else R.string.bt_btn_connect
                        ),
                        color = if (device.isConnected) colors.textPrimary else accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            IconButton(
                onClick = onUnpair,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.bt_btn_unpair),
                    tint = colors.textDisabled,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun DiscoveredDeviceRow(
    device: BtDeviceItem,
    onPair: () -> Unit
) {
    val accent = LocalClusterAccent.current.primary
    val colors = LocalClusterColors.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onPair),
        shape = RoundedCornerShape(12.dp),
        color = colors.backgroundSecondary,
        border = BorderStroke(1.dp, colors.surfaceCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = categoryIcon(device.category),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (device.isBonding) stringResource(R.string.bt_device_pairing) else device.address,
                    color = if (device.isBonding) accent else colors.textDisabled,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accent.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
            ) {
                Text(
                    text = stringResource(R.string.bt_btn_pair),
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun BtCenterNoticeCard(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    val accent = LocalClusterAccent.current.primary
    val colors = LocalClusterColors.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(14.dp),
        color = colors.surfaceCard,
        border = BorderStroke(1.dp, colors.surfaceCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                color = colors.textSecondary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(18.dp))
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onAction),
                shape = RoundedCornerShape(10.dp),
                color = accent.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, accent)
            ) {
                Text(
                    text = actionLabel,
                    color = accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
    }
}

private fun categoryIcon(category: BtDeviceCategory): ImageVector {
    return when (category) {
        BtDeviceCategory.PHONE -> Icons.Rounded.Smartphone
        BtDeviceCategory.AUDIO -> Icons.Rounded.Headphones
        BtDeviceCategory.COMPUTER -> Icons.Rounded.Computer
        BtDeviceCategory.OTHER -> Icons.Rounded.Bluetooth
    }
}
