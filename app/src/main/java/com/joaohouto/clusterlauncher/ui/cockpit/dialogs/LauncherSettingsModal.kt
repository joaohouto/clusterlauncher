package com.joaohouto.clusterlauncher.ui.cockpit.dialogs

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joaohouto.clusterlauncher.R
import com.joaohouto.clusterlauncher.data.location.GpsLocationRepository
import com.joaohouto.clusterlauncher.data.map.DownloadProgress
import com.joaohouto.clusterlauncher.data.map.MBTilesDownloader
import com.joaohouto.clusterlauncher.data.map.MBTilesRepository
import com.joaohouto.clusterlauncher.data.map.MapFileInfo
import com.joaohouto.clusterlauncher.data.map.TileProvider
import com.joaohouto.clusterlauncher.data.model.CarBrand
import com.joaohouto.clusterlauncher.media.MediaManager
import com.joaohouto.clusterlauncher.ui.theme.ALL_ACCENT_THEMES
import com.joaohouto.clusterlauncher.ui.theme.DeepMetallicBackground
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterAccent
import com.joaohouto.clusterlauncher.ui.theme.NeedleRed
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCard
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCardBorder
import com.joaohouto.clusterlauncher.ui.theme.TextDisabled
import com.joaohouto.clusterlauncher.ui.theme.TextPrimary
import com.joaohouto.clusterlauncher.ui.theme.TextSecondary
import com.joaohouto.clusterlauncher.utils.DefaultLauncherHelper
import kotlinx.coroutines.launch
import java.util.Locale

private enum class SettingsTab(val title: String, val icon: ImageVector) {
    MAPS("Mapas", Icons.Rounded.Map),
    VEHICLE("Veículo", Icons.Rounded.DirectionsCar),
    SYSTEM("Sistema", Icons.Rounded.Settings)
}

/**
 * Modal de configurações do Cockpit simplificado para uso veicular com alta legibilidade.
 * Interface moderna em abas com paleta contida e toques táteis confortáveis.
 */
@Composable
fun LauncherSettingsModal(
    selectedBrand: CarBrand,
    selectedAccentId: String = "needle_red",
    onSelectAccent: (String) -> Unit = {},
    showMapOnHome: Boolean = true,
    onToggleShowMap: (Boolean) -> Unit = {},
    isMapDarkMode: Boolean = false,
    onToggleMapDarkMode: (Boolean) -> Unit = {},
    onSelectBrandClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val mapState by MBTilesRepository.mapState.collectAsState()
    val downloadProgress by MBTilesDownloader.progressState.collectAsState()
    val locationState by GpsLocationRepository.locationState.collectAsState()
    val hasGpsPermission by GpsLocationRepository.hasPermission.collectAsState()
    val mediaState by MediaManager.mediaState.collectAsState()
    val isDefaultLauncher by DefaultLauncherHelper.isDefaultLauncher.collectAsState()

    var activeTab by remember { mutableStateOf(SettingsTab.MAPS) }
    var selectedRadiusKm by remember { mutableIntStateOf(25) }
    var selectedProvider by remember { mutableStateOf(TileProvider.OSM_STANDARD) }
    var mapToDelete by remember { mutableStateOf<MapFileInfo?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isImporting = true
            scope.launch {
                val result = MBTilesRepository.importMbtilesFromUri(context, uri)
                isImporting = false
                result.fold(
                    onSuccess = { file ->
                        Toast.makeText(context, "Mapa importado: ${file.name}", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { error ->
                        Toast.makeText(context, error.message ?: "Falha ao importar o mapa.", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, SurfaceCardBorder, RoundedCornerShape(20.dp)),
            color = DeepMetallicBackground
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Barra Lateral de Navegação (Menu Esquerdo)
                Column(
                    modifier = Modifier
                        .width(220.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF0A0C0F))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Cabeçalho do App
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "Configurações",
                                color = TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF1E222B)
                            ) {
                                Text(
                                    text = "v1.1",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Botões de Abas Principais
                        SettingsTab.entries.forEach { tab ->
                            val isSelected = activeTab == tab
                            val accent = LocalClusterAccent.current.primary
                            val bgColor = if (isSelected) accent.copy(alpha = 0.12f) else Color.Transparent
                            val contentColor = if (isSelected) accent else TextSecondary
                            val borderColor = if (isSelected) accent.copy(alpha = 0.4f) else Color.Transparent

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bgColor)
                                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                                    .clickable { activeTab = tab }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    tint = contentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = tab.title,
                                    color = contentColor,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Botão Fechar no rodapé da barra lateral
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Fechar",
                            tint = TextDisabled,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Fechar",
                            color = TextDisabled,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Divisor Vertical
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(SurfaceCardBorder)
                )

                // Painel de Conteúdo (Lado Direito)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(24.dp)
                ) {
                    when (activeTab) {
                        SettingsTab.MAPS -> MapsSettingsPanel(
                            mapState = mapState,
                            downloadProgress = downloadProgress,
                            locationHasFix = locationState.hasFix,
                            locationLat = locationState.latitude,
                            locationLon = locationState.longitude,
                            selectedRadiusKm = selectedRadiusKm,
                            onRadiusSelected = { selectedRadiusKm = it },
                            selectedProvider = selectedProvider,
                            onProviderSelected = { selectedProvider = it },
                            isImporting = isImporting,
                            isRefreshing = isRefreshing,
                            onImportClick = {
                                filePickerLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                            },
                            onRefreshClick = {
                                isRefreshing = true
                                scope.launch {
                                    MBTilesRepository.refreshAvailableMaps(context)
                                    isRefreshing = false
                                    Toast.makeText(context, "Lista atualizada", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onSelectMap = { mapInfo ->
                                scope.launch {
                                    MBTilesRepository.selectMap(context, mapInfo.file)
                                    Toast.makeText(context, "Mapa ativo: ${mapInfo.displayName}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDeleteMap = { mapToDelete = it },
                            onStartDownload = {
                                val lat = if (locationState.hasFix) locationState.latitude else -23.5505
                                val lon = if (locationState.hasFix) locationState.longitude else -46.6333
                                MBTilesDownloader.startDownload(
                                    context = context,
                                    centerLat = lat,
                                    centerLon = lon,
                                    radiusKm = selectedRadiusKm,
                                    provider = selectedProvider
                                )
                            },
                            onCancelDownload = {
                                MBTilesDownloader.cancelDownload(context)
                            }
                        )

                        SettingsTab.VEHICLE -> VehicleSettingsPanel(
                            selectedBrand = selectedBrand,
                            selectedAccentId = selectedAccentId,
                            onSelectAccent = onSelectAccent,
                            onSelectBrandClick = onSelectBrandClick
                        )

                        SettingsTab.SYSTEM -> SystemSettingsPanel(
                            showMapOnHome = showMapOnHome,
                            onToggleShowMap = onToggleShowMap,
                            isMapDarkMode = isMapDarkMode,
                            onToggleMapDarkMode = onToggleMapDarkMode,
                            isDefaultLauncher = isDefaultLauncher,
                            onSetDefaultLauncher = { DefaultLauncherHelper.requestSetDefaultLauncher(context) },
                            hasGpsPermission = hasGpsPermission,
                            isMediaPermissionGranted = mediaState.isPermissionGranted,
                            onOpenAppSettings = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            },
                            onOpenNotificationListenerSettings = {
                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }
        }
    }

    // Diálogo de confirmação para exclusão de mapa
    mapToDelete?.let { mapInfo ->
        AlertDialog(
            onDismissRequest = { mapToDelete = null },
            title = { Text("Excluir Mapa", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Deseja remover \"${mapInfo.displayName}\"? Essa ação libera espaço no dispositivo.", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val file = mapInfo.file
                        mapToDelete = null
                        scope.launch {
                            MBTilesRepository.deleteMap(context, file)
                        }
                    }
                ) {
                    Text("Excluir", color = NeedleRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { mapToDelete = null }) {
                    Text("Cancelar", color = TextSecondary)
                }
            },
            containerColor = Color(0xFF181A20),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/**
 * Painel da aba Mapas: lista limpa com seleção de mapas offline e download simplificado.
 */
@Composable
private fun MapsSettingsPanel(
    mapState: com.joaohouto.clusterlauncher.data.map.OfflineMapState,
    downloadProgress: DownloadProgress,
    locationHasFix: Boolean,
    locationLat: Double,
    locationLon: Double,
    selectedRadiusKm: Int,
    onRadiusSelected: (Int) -> Unit,
    selectedProvider: TileProvider,
    onProviderSelected: (TileProvider) -> Unit,
    isImporting: Boolean,
    isRefreshing: Boolean,
    onImportClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSelectMap: (MapFileInfo) -> Unit,
    onDeleteMap: (MapFileInfo) -> Unit,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit
) {
    val scrollState = rememberScrollState()
    val accent = LocalClusterAccent.current.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Topo da Aba: Título e Ações Rápidas
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Mapas Offline",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${mapState.availableMaps.size} arquivo(s) disponível(is)",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Botão Atualizar Lista
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onRefreshClick() },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E222B),
                    border = BorderStroke(1.dp, SurfaceCardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Atualizar",
                            tint = if (isRefreshing) LocalClusterAccent.current.primary else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRefreshing) "Buscando..." else "Atualizar",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Botão Importar .mbtiles
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onImportClick() },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E222B),
                    border = BorderStroke(1.dp, SurfaceCardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FileOpen,
                            contentDescription = "Importar",
                            tint = TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isImporting) "Importando..." else "Importar .mbtiles",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Lista de Mapas Armazenados
        if (mapState.availableMaps.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = SurfaceCard,
                border = BorderStroke(1.dp, SurfaceCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Map,
                        contentDescription = null,
                        tint = TextDisabled,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Nenhum mapa offline carregado",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Importe um arquivo .mbtiles ou utilize o download por GPS abaixo.",
                        color = TextDisabled,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                mapState.availableMaps.forEach { mapInfo ->
                    CleanMapItem(
                        mapInfo = mapInfo,
                        onSelect = { onSelectMap(mapInfo) },
                        onDelete = { onDeleteMap(mapInfo) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Seção: Download Automático por GPS (Simplificada)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Download da Região Atual (GPS)",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (locationHasFix) "Posição GPS pronta para download" else "Aguardando sinal GPS (usará fallback)",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    // Seletor de Raio
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(15, 25, 40).forEach { km ->
                            val isSelected = selectedRadiusKm == km
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onRadiusSelected(km) },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) accent.copy(alpha = 0.15f) else Color(0xFF101215),
                                border = BorderStroke(1.dp, if (isSelected) accent else SurfaceCardBorder)
                            ) {
                                Text(
                                    text = "${km} km",
                                    color = if (isSelected) TextPrimary else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                // Progresso e Ação de Download
                when (val progress = downloadProgress) {
                    is DownloadProgress.Downloading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Baixando: ${progress.current}/${progress.total} blocos (${progress.percentage}%)",
                                    color = TextPrimary,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "Cancelar",
                                    color = accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { onCancelDownload() }
                                )
                            }
                            LinearProgressIndicator(
                                progress = { progress.percentage / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = accent,
                                trackColor = Color(0xFF262930)
                            )
                        }
                    }
                    else -> {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onStartDownload() },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1E222B),
                            border = BorderStroke(1.dp, SurfaceCardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 11.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudDownload,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Iniciar Download Offline",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Item simplificado e elegante de mapa baixado na lista.
 */
@Composable
private fun CleanMapItem(
    mapInfo: MapFileInfo,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = LocalClusterAccent.current.primary
    val borderColor = if (mapInfo.isActive) accent else SurfaceCardBorder
    val bgColor = if (mapInfo.isActive) accent.copy(alpha = 0.12f) else Color(0xFF101215)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(10.dp))
            .clickable { onSelect() },
        shape = RoundedCornerShape(10.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Ícone indicador ativo / inativo
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (mapInfo.isActive) accent else Color.Transparent)
                        .border(1.5.dp, if (mapInfo.isActive) accent else TextDisabled, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (mapInfo.isActive) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = mapInfo.displayName,
                            color = if (mapInfo.isActive) TextPrimary else TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = if (mapInfo.isActive) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Tag discreta sem cores berrantes
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF222630),
                            border = BorderStroke(0.5.dp, Color(0xFF333A48))
                        ) {
                            Text(
                                text = if (mapInfo.isVector) "VETORIAL" else "RASTER",
                                color = Color(0xFFA0AABA),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = String.format(Locale.US, "%.1f MB • %s", mapInfo.sizeMb, mapInfo.formattedDate),
                        color = TextDisabled,
                        fontSize = 11.sp
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Excluir",
                    tint = TextDisabled,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Painel da aba Veículo: exibição da marca selecionada e personalização da cor de accent dos instrumentos.
 */
@Composable
private fun VehicleSettingsPanel(
    selectedBrand: CarBrand,
    selectedAccentId: String,
    onSelectAccent: (String) -> Unit,
    onSelectBrandClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = "Veículo & Aparência",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Personalize o emblema da montadora e a tonalidade de iluminação do cockpit.",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        // Cartão 1: Marca do Veículo
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF101215))
                        .border(1.dp, SurfaceCardBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = selectedBrand.iconRes),
                        contentDescription = selectedBrand.name,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = selectedBrand.name,
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Emblema ativo no Cockpit",
                        color = TextDisabled,
                        fontSize = 12.sp
                    )
                }

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectBrandClick() },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1E222B),
                    border = BorderStroke(1.dp, SurfaceCardBorder)
                ) {
                    Text(
                        text = "Trocar Marca do Veículo",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }
        }

        // Cartão 2: Cor de Destaque (Accent) - Idêntico ao ClusterPlayer
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_accent_title),
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.settings_accent_subtitle),
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Fileira de badges circulares horizontais
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ALL_ACCENT_THEMES.forEach { theme ->
                        val isSelected = theme.id.equals(selectedAccentId, ignoreCase = true)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectAccent(theme.id) }
                                .padding(vertical = 4.dp, horizontal = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(theme.primary, theme.dark)
                                        )
                                    )
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) Color.White else SurfaceCardBorder,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = stringResource(theme.nameRes),
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Painel da aba Sistema: switches de exibição do cockpit, launcher padrão e permissões.
 */
@Composable
private fun SystemSettingsPanel(
    showMapOnHome: Boolean,
    onToggleShowMap: (Boolean) -> Unit,
    isMapDarkMode: Boolean,
    onToggleMapDarkMode: (Boolean) -> Unit,
    isDefaultLauncher: Boolean,
    onSetDefaultLauncher: () -> Unit,
    hasGpsPermission: Boolean,
    isMediaPermissionGranted: Boolean,
    onOpenAppSettings: () -> Unit,
    onOpenNotificationListenerSettings: () -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val accent = LocalClusterAccent.current.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = "Preferências & Sistema",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Ajustes de visualização do Cockpit e integrações do Android.",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        // Cartão 1: Visualização do Cockpit
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Aparência do Cockpit",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                // Toggle: Exibir Mapa na Tela Inicial
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Exibir Mapa na Tela Inicial", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(text = "Mostra o card de mapa ao lado do reprodutor", color = TextDisabled, fontSize = 11.sp)
                    }
                    Switch(
                        checked = showMapOnHome,
                        onCheckedChange = onToggleShowMap,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accent,
                            uncheckedThumbColor = TextDisabled,
                            uncheckedTrackColor = Color(0xFF1E222B)
                        )
                    )
                }

                // Toggle: Inverter Cores do Mapa
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Tema Noturno de Alto Contraste", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(text = "Aplica filtro escuro esportivo no mapa raster", color = TextDisabled, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isMapDarkMode,
                        onCheckedChange = onToggleMapDarkMode,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accent,
                            uncheckedThumbColor = TextDisabled,
                            uncheckedTrackColor = Color(0xFF1E222B)
                        )
                    )
                }
            }
        }

        // Cartão 2: Launcher Padrão
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Rounded.Home,
                        contentDescription = null,
                        tint = if (isDefaultLauncher) Color(0xFF4CAF50) else TextDisabled,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Início Padrão do Veículo",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isDefaultLauncher) "ClusterLauncher está ativo como launcher padrão" else "Defina o ClusterLauncher como padrão ao ligar o carro",
                            color = if (isDefaultLauncher) Color(0xFF81C784) else TextDisabled,
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSetDefaultLauncher() },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E222B),
                    border = BorderStroke(1.dp, SurfaceCardBorder)
                ) {
                    Text(
                        text = if (isDefaultLauncher) "Alterar" else "Definir",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Cartão 3: Permissões Essenciais
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Permissões do Painel",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                // GPS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Acesso ao GPS", color = TextSecondary, fontSize = 13.sp)
                    if (hasGpsPermission) {
                        Text(text = "Concedida", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Surface(
                            modifier = Modifier.clickable { onOpenAppSettings() },
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1E222B),
                            border = BorderStroke(1.dp, SurfaceCardBorder)
                        ) {
                            Text(text = "Conceder", color = accent, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }

                // Notificações de Mídia
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Controle de Mídia", color = TextSecondary, fontSize = 13.sp)
                    if (isMediaPermissionGranted) {
                        Text(text = "Concedida", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Surface(
                            modifier = Modifier.clickable { onOpenNotificationListenerSettings() },
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1E222B),
                            border = BorderStroke(1.dp, SurfaceCardBorder)
                        ) {
                            Text(text = "Conceder", color = accent, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }
            }
        }

        // Link Portal do Projeto
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://joaohouto.github.io/clusterlauncher")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = Icons.Rounded.Language, contentDescription = null, tint = TextDisabled, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "joaohouto.github.io/clusterlauncher", color = TextDisabled, fontSize = 11.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(imageVector = Icons.Rounded.OpenInNew, contentDescription = null, tint = TextDisabled, modifier = Modifier.size(12.dp))
        }
    }
}
