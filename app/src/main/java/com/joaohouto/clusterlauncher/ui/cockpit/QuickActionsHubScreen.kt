package com.joaohouto.clusterlauncher.ui.cockpit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joaohouto.clusterlauncher.R
import com.joaohouto.clusterlauncher.data.model.DockSlot
import com.joaohouto.clusterlauncher.data.receiver.BluetoothStateReceiver
import com.joaohouto.clusterlauncher.ui.cockpit.components.launchSlotApp
import com.joaohouto.clusterlauncher.ui.components.MetallicButton
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterAccent
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCard
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCardBorder
import com.joaohouto.clusterlauncher.ui.theme.TextDisabled
import com.joaohouto.clusterlauncher.ui.theme.TextPrimary
import com.joaohouto.clusterlauncher.ui.theme.TextSecondary

/**
 * Segunda página do padrão Android Auto: reúne os 5 atalhos da QuickDock
 * e os botões de controle do sistema (Bluetooth e Configurações do Launcher)
 * em botões amplos e táteis.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickActionsHubScreen(
    dockSlots: List<DockSlot>,
    onSlotLongClick: (DockSlot) -> Unit,
    onBluetoothClick: () -> Unit,
    onBluetoothLongClick: (() -> Unit)? = null,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isBluetoothConnected by BluetoothStateReceiver.isBluetoothConnected.collectAsState()
    val accent = LocalClusterAccent.current.primary

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Fileira Principal: 5 botões grandes da QuickDock
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.58f),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            dockSlots.forEach { slot ->
                MetallicButton(
                    onClick = { launchSlotApp(context, slot) { onSlotLongClick(slot) } },
                    onLongClick = { onSlotLongClick(slot) },
                    icon = slot.icon,
                    text = stringResource(slot.titleRes),
                    minSize = 120.dp,
                    iconSize = 76.dp,
                    textSize = 18.sp,
                    verticalLayout = true,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }

        // Fileira Inferior: Bluetooth + Configurações do Launcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.42f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val cardShape = RoundedCornerShape(18.dp)
            val btBorderColor = if (isBluetoothConnected) accent.copy(alpha = 0.55f) else SurfaceCardBorder
            val cardBrush = Brush.verticalGradient(
                colors = listOf(Color(0xFF1A1D24), Color(0xFF121418))
            )

            // Card Bluetooth
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(cardShape)
                    .background(cardBrush)
                    .border(1.dp, btBorderColor, cardShape)
                    .combinedClickable(
                        onClick = onBluetoothClick,
                        onLongClick = onBluetoothLongClick
                    )
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(if (isBluetoothConnected) accent.copy(alpha = 0.16f) else SurfaceCard)
                        .border(1.dp, btBorderColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isBluetoothConnected) Icons.Rounded.BluetoothConnected else Icons.Rounded.Bluetooth,
                        contentDescription = null,
                        tint = if (isBluetoothConnected) accent else TextDisabled,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.width(18.dp))

                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = if (isBluetoothConnected) stringResource(R.string.bluetooth_connected) else stringResource(R.string.bluetooth_disconnected),
                        color = if (isBluetoothConnected) TextPrimary else TextSecondary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.bluetooth_app_subtitle),
                        color = TextDisabled,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Card Configurações do Cockpit
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(cardShape)
                    .background(cardBrush)
                    .border(1.dp, accent.copy(alpha = 0.55f), cardShape)
                    .clickable(onClick = onSettingsClick)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f))
                        .border(1.dp, accent.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.settings),
                        tint = accent,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.width(18.dp))

                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.layout_preset_subtitle),
                        color = TextSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
