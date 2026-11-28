package com.joaohouto.clusterlauncher.ui.drawer

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joaohouto.clusterlauncher.R
import com.joaohouto.clusterlauncher.data.model.AppItem
import com.joaohouto.clusterlauncher.data.model.DockSlotType
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterColors
import com.joaohouto.clusterlauncher.ui.theme.NeedleRed
import com.joaohouto.clusterlauncher.ui.theme.TextPrimary
import com.joaohouto.clusterlauncher.ui.theme.TextSecondary

@Composable
fun AppSlideScreen(
    apps: List<AppItem>,
    onAppClick: (AppItem) -> Unit,
    onPinToDock: (DockSlotType, AppItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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

    // Modal de opções do aplicativo (Informações / Desinstalar)
    selectedAppForPinning?.let { app ->
        val colors = LocalClusterColors.current
        AlertDialog(
            onDismissRequest = { selectedAppForPinning = null },
            containerColor = colors.surfaceCard,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(18.dp),
            title = {
                Text(
                    text = app.label,
                    color = colors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val actionShape = RoundedCornerShape(12.dp)

                    // Botão Informações do Aplicativo (Configurações do Android)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(actionShape)
                            .background(colors.backgroundSecondary)
                            .border(BorderStroke(1.dp, colors.surfaceCardBorder), actionShape)
                            .clickable {
                                selectedAppForPinning = null
                                try {
                                    val detailsIntent = Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", app.packageName, null)
                                    ).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(detailsIntent)
                                } catch (_: Exception) {}
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.app_info),
                            color = colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Botão Desinstalar Aplicativo
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(actionShape)
                            .background(colors.backgroundSecondary)
                            .border(BorderStroke(1.dp, colors.surfaceCardBorder), actionShape)
                            .clickable {
                                selectedAppForPinning = null
                                try {
                                    val uninstallIntent = Intent(
                                        Intent.ACTION_DELETE,
                                        Uri.fromParts("package", app.packageName, null)
                                    ).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(uninstallIntent)
                                } catch (_: Exception) {
                                    try {
                                        val detailsIntent = Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", app.packageName, null)
                                        ).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(detailsIntent)
                                    } catch (_: Exception) {}
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = NeedleRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.uninstall_app),
                            color = NeedleRed,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedAppForPinning = null }) {
                    Text(text = stringResource(R.string.btn_cancel), color = colors.textSecondary)
                }
            }
        )
    }
}
