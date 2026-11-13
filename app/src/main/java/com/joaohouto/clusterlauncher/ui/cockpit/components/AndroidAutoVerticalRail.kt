package com.joaohouto.clusterlauncher.ui.cockpit.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joaohouto.clusterlauncher.R
import com.joaohouto.clusterlauncher.data.model.DockSlot
import com.joaohouto.clusterlauncher.data.receiver.BluetoothStateReceiver
import com.joaohouto.clusterlauncher.ui.components.MetallicButton
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterAccent
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCard
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCardBorder
import com.joaohouto.clusterlauncher.ui.theme.TextDisabled
import com.joaohouto.clusterlauncher.ui.theme.TextPrimary
import com.joaohouto.clusterlauncher.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Barra lateral vertical estilo Android Auto / Android Automotive Rail.
 * Posicionada no canto esquerdo da tela inicial no modo Android Auto.
 * Contém exclusivamente botões com ícones (sem texto) de toque amplo:
 * - Botão Apps / Drawer
 * - Atalhos do QuickDock (apenas ícones)
 * - Atalho de Bluetooth com estado de conexão
 * - Atalho de Configurações
 * - Relógio digital compacto na base
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = LocalClusterAccent.current.primary
    val isBluetoothConnected by BluetoothStateReceiver.isBluetoothConnected.collectAsState()

    // Relógio digital compacto
    var currentTimeString by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            currentTimeString = timeFormat.format(Date())
            delay(1000L)
        }
    }

    val railShape = RoundedCornerShape(22.dp)
    val railBackgroundBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF14171D),
                Color(0xFF0F1115),
                Color(0xFF0B0C0F)
            )
        )
    }

    Column(
        modifier = modifier
            .width(82.dp)
            .fillMaxHeight()
            .clip(railShape)
            .background(railBackgroundBrush)
            .border(1.dp, SurfaceCardBorder, railShape)
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
                    onClick = { launchSlotApp(context, slot) { onSlotLongClick(slot) } },
                    onLongClick = { onSlotLongClick(slot) },
                    icon = slot.icon,
                    text = null, // Sem texto
                    minSize = 60.dp,
                    iconSize = 30.dp,
                    contentDescription = stringResource(slot.titleRes),
                    modifier = Modifier.size(60.dp)
                )
            }
        }

        // Seção Inferior: Bluetooth + Configurações + Relógio
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val btBorderColor = if (isBluetoothConnected) accent.copy(alpha = 0.7f) else SurfaceCardBorder
            val btBgColor = if (isBluetoothConnected) accent.copy(alpha = 0.16f) else SurfaceCard

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
                    tint = if (isBluetoothConnected) accent else TextDisabled,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Botão Configurações (apenas ícone)
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(SurfaceCard)
                    .border(1.dp, SurfaceCardBorder, CircleShape)
                    .clickable(onClick = onSettingsClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.settings),
                    tint = TextSecondary,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Relógio compacto no rodapé
            if (currentTimeString.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = currentTimeString,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
