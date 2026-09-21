package com.joaohouto.clusterlauncher.ui.drawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joaohouto.clusterlauncher.data.model.AppItem
import com.joaohouto.clusterlauncher.data.model.DockSlotType
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterAccent
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCard
import com.joaohouto.clusterlauncher.ui.theme.TextPrimary
import com.joaohouto.clusterlauncher.ui.theme.TextSecondary

@Composable
fun AppSlideScreen(
    apps: List<AppItem>,
    onAppClick: (AppItem) -> Unit,
    onPinToDock: (DockSlotType, AppItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedAppForPinning by remember { mutableStateOf<AppItem?>(null) }

    val row1 = remember(apps) { apps.take(5) }
    val row2 = remember(apps) { apps.drop(5).take(5) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Row 1 (up to 5 apps)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (col in 0 until 5) {
                val app = row1.getOrNull(col)
                if (app != null) {
                    AppGridItem(
                        app = app,
                        onClick = { onAppClick(app) },
                        onLongClick = { selectedAppForPinning = app },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // Row 2 (up to 5 apps)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (col in 0 until 5) {
                val app = row2.getOrNull(col)
                if (app != null) {
                    AppGridItem(
                        app = app,
                        onClick = { onAppClick(app) },
                        onLongClick = { selectedAppForPinning = app },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }

    // Modal to pin app to a Quick Dock slot
    selectedAppForPinning?.let { app ->
        AlertDialog(
            onDismissRequest = { selectedAppForPinning = null },
            containerColor = SurfaceCard,
            title = {
                Text(
                    text = "Fixar ${app.label} na Quick Dock",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Escolha em qual slot fixar:",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    DockSlotType.entries.forEach { slotType ->
                        TextButton(
                            onClick = {
                                onPinToDock(slotType, app)
                                selectedAppForPinning = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Slot: ${stringResource(slotType.defaultTitleRes)}",
                                color = LocalClusterAccent.current.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedAppForPinning = null }) {
                    Text(text = "Cancelar", color = TextSecondary)
                }
            }
        )
    }
}
