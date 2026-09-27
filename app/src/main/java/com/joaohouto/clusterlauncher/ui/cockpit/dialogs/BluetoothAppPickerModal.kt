package com.joaohouto.clusterlauncher.ui.cockpit.dialogs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joaohouto.clusterlauncher.R
import com.joaohouto.clusterlauncher.data.model.AppItem
import com.joaohouto.clusterlauncher.ui.theme.DeepMetallicBackground
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterAccent
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCard
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCardBorder
import com.joaohouto.clusterlauncher.ui.theme.TextPrimary
import com.joaohouto.clusterlauncher.ui.theme.TextSecondary

@Composable
fun BluetoothAppPickerModal(
    apps: List<AppItem>,
    currentPackage: String?,
    onAppSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val accent = LocalClusterAccent.current.primary

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize(0.88f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, SurfaceCardBorder, RoundedCornerShape(16.dp)),
            color = DeepMetallicBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.select_bluetooth_app_title),
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.bluetooth_app_subtitle),
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Fechar",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Opção de Detecção Automática no topo
                val isAutoSelected = currentPackage.isNullOrEmpty()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceCard)
                        .border(
                            width = 1.dp,
                            color = if (isAutoSelected) accent else SurfaceCardBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onAppSelected(null) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = if (isAutoSelected) accent else TextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.bluetooth_app_auto),
                            color = if (isAutoSelected) accent else TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.bluetooth_app_auto_desc),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    if (isAutoSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selecionado",
                            tint = accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Grid de Aplicativos Instalados
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = apps,
                        key = { it.packageName },
                        contentType = { "bt_picker_item" }
                    ) { app ->
                        val isSelected = app.packageName == currentPackage
                        BluetoothAppPickerItem(
                            app = app,
                            isSelected = isSelected,
                            accentColor = accent,
                            onClick = { onAppSelected(app.packageName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BluetoothAppPickerItem(
    app: AppItem,
    isSelected: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .border(
                width = 1.dp,
                color = if (isSelected) accentColor else SurfaceCardBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (app.icon != null) {
            Image(
                bitmap = app.icon.asImageBitmap(),
                contentDescription = app.label,
                modifier = Modifier.size(48.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceCardBorder)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = app.label,
            color = if (isSelected) accentColor else TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
