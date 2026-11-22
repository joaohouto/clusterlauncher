package com.joaohouto.clusterlauncher.ui.cockpit.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joaohouto.clusterlauncher.R
import com.joaohouto.clusterlauncher.data.model.DockSlot
import com.joaohouto.clusterlauncher.data.model.DockSlotType
import com.joaohouto.clusterlauncher.data.receiver.BluetoothStateReceiver
import com.joaohouto.clusterlauncher.data.repository.AutomotivePackageResolver
import com.joaohouto.clusterlauncher.data.update.AppUpdateManager
import com.joaohouto.clusterlauncher.data.update.UpdateStatus
import com.joaohouto.clusterlauncher.ui.components.MetallicButton
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterAccent
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterColors
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCard
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCardBorder
import com.joaohouto.clusterlauncher.ui.theme.TextDisabled
import com.joaohouto.clusterlauncher.ui.theme.TextSecondary

/**
 * Barra lateral vertical estilo Android Auto / Android Automotive Rail.
 * Posicionada no canto esquerdo da tela inicial.
 * Contém exclusivamente botões com ícones (sem texto) de toque amplo:
 * - Botão Apps / Drawer
 * - Atalhos do QuickDock (ZLink, GPS, Rádio, Música)
 * - Atalho de Bluetooth com estado de conexão
 * - Atalho de Configurações do ClusterLauncher
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AndroidAutoVerticalRail(
    dockSlots: List<DockSlot>,
    onSlotLongClick: (DockSlot) -> Unit,
    onOpenDrawer: () -> Unit = {},
    isDrawerOpen: Boolean = false,
    onBluetoothClick: () -> Unit = {},
    onBluetoothLongClick: (() -> Unit)? = null,
    onSettingsClick: () -> Unit = {},
    onOpenPlayer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = LocalClusterAccent.current.primary
    val colors = LocalClusterColors.current
    val isBluetoothConnected by BluetoothStateReceiver.isBluetoothConnected.collectAsState()
    val updateStatus by AppUpdateManager.updateStatus.collectAsState()
    val hasUpdate = updateStatus is UpdateStatus.UpdateAvailable || updateStatus is UpdateStatus.ReadyToInstall

    val railShape = RoundedCornerShape(22.dp)
    val railBackgroundBrush = remember(colors) {
        Brush.verticalGradient(
            colors = listOf(
                colors.surfaceCard,
                colors.backgroundSecondary,
                colors.background
            )
        )
    }

    Column(
        modifier = modifier
            .width(82.dp)
            .fillMaxHeight()
            .clip(railShape)
            .background(railBackgroundBrush)
            .border(1.dp, colors.surfaceCardBorder, railShape)
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Seção Superior: Botão Apps (Drawer) / Retorno ao Painel
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = if (isDrawerOpen) 0.32f else 0.18f))
                .border(
                    width = if (isDrawerOpen) 2.dp else 1.5.dp,
                    color = accent.copy(alpha = if (isDrawerOpen) 0.9f else 0.6f),
                    shape = CircleShape
                )
                .clickable(onClick = onOpenDrawer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isDrawerOpen) Icons.Rounded.Dashboard else Icons.Rounded.Apps,
                contentDescription = stringResource(R.string.apps),
                tint = accent,
                modifier = Modifier.size(34.dp)
            )
        }

        // Seção Central: Botões do Dock (apenas ícones, sem texto)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            dockSlots.forEach { slot ->
                MetallicButton(
                    onClick = { launchSlotApp(context, slot, onOpenPlayer = onOpenPlayer) { onSlotLongClick(slot) } },
                    onLongClick = { onSlotLongClick(slot) },
                    icon = slot.icon,
                    text = null,
                    minSize = 60.dp,
                    iconSize = 30.dp,
                    contentDescription = stringResource(slot.titleRes),
                    modifier = Modifier.size(60.dp)
                )
            }
        }

        // Seção Inferior: Bluetooth + Configurações
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val btBorderColor = if (isBluetoothConnected) accent.copy(alpha = 0.75f) else colors.surfaceCardBorder
            val btBgColor = if (isBluetoothConnected) accent.copy(alpha = 0.18f) else colors.surfaceCard

            // Botão Bluetooth (apenas ícone)
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(btBgColor)
                    .border(1.dp, btBorderColor, CircleShape)
                    .combinedClickable(
                        onClick = onBluetoothClick,
                        onLongClick = onBluetoothLongClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isBluetoothConnected) Icons.Rounded.BluetoothConnected else Icons.Rounded.Bluetooth,
                    contentDescription = stringResource(R.string.bluetooth),
                    tint = if (isBluetoothConnected) accent else colors.textDisabled,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Botão Configurações (apenas ícone, com indicador de atualização quando disponível)
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (hasUpdate) accent.copy(alpha = 0.18f) else colors.surfaceCard)
                    .border(1.dp, if (hasUpdate) accent else colors.surfaceCardBorder, CircleShape)
                    .clickable(onClick = onSettingsClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.settings),
                    tint = if (hasUpdate) accent else colors.textSecondary,
                    modifier = Modifier.size(28.dp)
                )
                if (hasUpdate) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 8.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                }
            }
        }
    }
}

internal fun launchSlotApp(
    context: Context,
    slot: DockSlot,
    onOpenPlayer: () -> Unit = {},
    onFallbackPicker: () -> Unit
) {
    val targetPkg = slot.targetPackage
    if (slot.type == DockSlotType.Music && AutomotivePackageResolver.isInternalPlayerPackage(targetPkg)) {
        onOpenPlayer()
        return
    }

    if (!targetPkg.isNullOrEmpty()) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            return
        }
    }

    onFallbackPicker()
}
