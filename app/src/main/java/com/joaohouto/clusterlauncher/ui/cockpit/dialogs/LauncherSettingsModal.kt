package com.joaohouto.clusterlauncher.ui.cockpit.dialogs

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PermDeviceInformation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import com.joaohouto.clusterlauncher.ui.theme.DeepMetallicBackground
import com.joaohouto.clusterlauncher.ui.theme.NeedleRed
import com.joaohouto.clusterlauncher.ui.theme.NeedleRedGlow
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCard
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCardBorder
import com.joaohouto.clusterlauncher.ui.theme.TextDisabled
import com.joaohouto.clusterlauncher.ui.theme.TextPrimary
import com.joaohouto.clusterlauncher.ui.theme.TextSecondary
import com.joaohouto.clusterlauncher.utils.DefaultLauncherHelper
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LauncherSettingsModal(
    selectedBrand: CarBrand,
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

    var selectedRadiusKm by remember { mutableIntStateOf(25) }
    var selectedProvider by remember { mutableStateOf(TileProvider.OSM_STANDARD) }
    var customUrl by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }

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
                        Toast.makeText(
                            context,
                            "Mapa importado: ${file.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            context,
                            error.message ?: "Falha ao importar o mapa.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }
    }

    val leftScrollState = rememberScrollState()
    val rightScrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.94f)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, SurfaceCardBorder, RoundedCornerShape(20.dp)),
            color = DeepMetallicBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Barra Superior do Modal
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0x22E61924))
                                .border(1.dp, NeedleRedGlow, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                tint = NeedleRed,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.settings_title),
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SurfaceCard,
                            border = BorderStroke(1.dp, SurfaceCardBorder)
                        ) {
                            Text(
                                text = "v1.1.0",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.btn_cancel),
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Corpo em Duas Colunas
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Coluna 1: Mapa Offline (54% de largura)
                    Surface(
                        modifier = Modifier
                            .weight(0.54f)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceCard,
                        border = BorderStroke(1.dp, SurfaceCardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .verticalScroll(leftScrollState),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Map,
                                    contentDescription = null,
                                    tint = NeedleRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.map_offline_title),
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // 1. Controle de Visibilidade do Mapa no Cockpit
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF101215))
                                    .border(1.dp, SurfaceCardBorder, RoundedCornerShape(10.dp))
                                    .clickable { onToggleShowMap(!showMapOnHome) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Exibir Mapa no Cockpit",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )

                                Switch(
                                    checked = showMapOnHome,
                                    onCheckedChange = { onToggleShowMap(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = NeedleRed,
                                        uncheckedThumbColor = TextDisabled,
                                        uncheckedTrackColor = SurfaceCard
                                    )
                                )
                            }

                            // 2. Inverter cores do mapa
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF101215))
                                    .border(1.dp, SurfaceCardBorder, RoundedCornerShape(10.dp))
                                    .clickable { onToggleMapDarkMode(!isMapDarkMode) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Inverter cores do mapa",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )

                                Switch(
                                    checked = isMapDarkMode,
                                    onCheckedChange = { onToggleMapDarkMode(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = NeedleRed,
                                        uncheckedThumbColor = TextDisabled,
                                        uncheckedTrackColor = SurfaceCard
                                    )
                                )
                            }

                            // 3. Seção: Mapas Baixados (Permite alternar entre múltiplos mapas e importar)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Mapas Baixados (${mapState.availableMaps.size}):",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(enabled = !isImporting) {
                                            try {
                                                filePickerLauncher.launch(arrayOf("*/*"))
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Não foi possível abrir o seletor de arquivos", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0x22E61924),
                                    border = BorderStroke(1.dp, NeedleRed.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.FileOpen,
                                            contentDescription = null,
                                            tint = NeedleRed,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isImporting) "Importando..." else "Importar .mbtiles",
                                            color = NeedleRed,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            if (mapState.availableMaps.isEmpty()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF101215),
                                    border = BorderStroke(1.dp, SurfaceCardBorder)
                                ) {
                                    Text(
                                        text = "Nenhum mapa offline baixado ainda. Use a área abaixo para baixar blocos da sua região.",
                                        color = TextDisabled,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    mapState.availableMaps.forEach { mapInfo ->
                                        DownloadedMapItem(
                                            mapInfo = mapInfo,
                                            onSelect = { MBTilesRepository.selectMap(context, mapInfo.file) },
                                            onDelete = { MBTilesRepository.deleteMap(context, mapInfo.file) }
                                        )
                                    }
                                }
                            }

                            // 4. Seção: Baixar Novo Mapa
                            Text(
                                text = "Baixar Novo Mapa:",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TileProvider.entries.forEach { provider ->
                                    ProviderChip(
                                        label = provider.displayName,
                                        isSelected = selectedProvider == provider,
                                        onClick = { selectedProvider = provider }
                                    )
                                }
                            }

                            if (selectedProvider == TileProvider.CUSTOM) {
                                OutlinedTextField(
                                    value = customUrl,
                                    onValueChange = { customUrl = it },
                                    placeholder = {
                                        Text("https://.../{z}/{x}/{y}.png", color = TextDisabled, fontSize = 11.sp)
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(color = TextPrimary, fontSize = 12.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NeedleRed,
                                        unfocusedBorderColor = SurfaceCardBorder
                                    )
                                )
                            }

                            // Seletor de Raio
                            Text(
                                text = stringResource(R.string.map_radius),
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RadiusChip(
                                    label = "15 km",
                                    sublabel = "~8 MB",
                                    isSelected = selectedRadiusKm == 15,
                                    onClick = { selectedRadiusKm = 15 },
                                    modifier = Modifier.weight(1f)
                                )
                                RadiusChip(
                                    label = "25 km",
                                    sublabel = "~18 MB",
                                    isSelected = selectedRadiusKm == 25,
                                    onClick = { selectedRadiusKm = 25 },
                                    modifier = Modifier.weight(1f)
                                )
                                RadiusChip(
                                    label = "40 km",
                                    sublabel = "~42 MB",
                                    isSelected = selectedRadiusKm == 40,
                                    onClick = { selectedRadiusKm = 40 },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Área de Ação e Progresso de Download
                            when (val progress = downloadProgress) {
                                is DownloadProgress.Downloading -> {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = stringResource(
                                                    R.string.downloading_tiles,
                                                    progress.current,
                                                    progress.total,
                                                    progress.percentage
                                                ),
                                                color = TextPrimary,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = stringResource(R.string.btn_cancel),
                                                color = NeedleRed,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.clickable {
                                                    MBTilesDownloader.cancelDownload(context)
                                                }
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { progress.percentage / 100f },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            color = NeedleRed,
                                            trackColor = Color(0xFF262930)
                                        )
                                    }
                                }
                                is DownloadProgress.Completed -> {
                                    Text(
                                        text = stringResource(R.string.download_complete),
                                        color = Color(0xFF4CAF50),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    DownloadButton(
                                        text = stringResource(R.string.btn_download_map),
                                        onClick = {
                                            val lat = if (locationState.hasFix) locationState.latitude else -23.5505
                                            val lon = if (locationState.hasFix) locationState.longitude else -46.6333
                                            MBTilesDownloader.startDownload(
                                                context = context,
                                                centerLat = lat,
                                                centerLon = lon,
                                                radiusKm = selectedRadiusKm,
                                                provider = selectedProvider,
                                                customUrl = customUrl
                                            )
                                        }
                                    )
                                }
                                is DownloadProgress.Error -> {
                                    Text(
                                        text = stringResource(R.string.download_error),
                                        color = NeedleRed,
                                        fontSize = 12.sp
                                    )
                                    DownloadButton(
                                        text = stringResource(R.string.btn_download_map),
                                        onClick = {
                                            val lat = if (locationState.hasFix) locationState.latitude else -23.5505
                                            val lon = if (locationState.hasFix) locationState.longitude else -46.6333
                                            MBTilesDownloader.startDownload(
                                                context = context,
                                                centerLat = lat,
                                                centerLon = lon,
                                                radiusKm = selectedRadiusKm,
                                                provider = selectedProvider,
                                                customUrl = customUrl
                                            )
                                        }
                                    )
                                }
                                is DownloadProgress.Idle -> {
                                    DownloadButton(
                                        text = stringResource(R.string.btn_download_map),
                                        onClick = {
                                            val lat = if (locationState.hasFix) locationState.latitude else -23.5505
                                            val lon = if (locationState.hasFix) locationState.longitude else -46.6333
                                            MBTilesDownloader.startDownload(
                                                context = context,
                                                centerLat = lat,
                                                centerLon = lon,
                                                radiusKm = selectedRadiusKm,
                                                provider = selectedProvider,
                                                customUrl = customUrl
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Coluna 2: Veículo, Sistema & Link do Projeto (46% de largura)
                    Column(
                        modifier = Modifier
                            .weight(0.46f)
                            .fillMaxHeight()
                            .verticalScroll(rightScrollState),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Card 1: Veículo & Aparência
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = SurfaceCard,
                            border = BorderStroke(1.dp, SurfaceCardBorder)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.DirectionsCar,
                                        contentDescription = null,
                                        tint = NeedleRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.vehicle_title),
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Image(
                                            painter = painterResource(id = selectedBrand.iconRes),
                                            contentDescription = selectedBrand.name,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = selectedBrand.name,
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    ActionChip(
                                        label = stringResource(R.string.change_brand),
                                        onClick = onSelectBrandClick
                                    )
                                }
                            }
                        }

                        // Card 2: Sistema & Permissões
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = SurfaceCard,
                            border = BorderStroke(1.dp, SurfaceCardBorder)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.PermDeviceInformation,
                                        contentDescription = null,
                                        tint = NeedleRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.system_title),
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Item: Launcher Padrão
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.Home,
                                            contentDescription = null,
                                            tint = if (isDefaultLauncher) Color(0xFF4CAF50) else TextDisabled,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isDefaultLauncher) {
                                                stringResource(R.string.default_launcher_status_active)
                                            } else {
                                                stringResource(R.string.default_launcher_status_inactive)
                                            },
                                            color = if (isDefaultLauncher) TextSecondary else TextPrimary,
                                            fontSize = 13.sp
                                        )
                                    }

                                    if (!isDefaultLauncher) {
                                        ActionChip(
                                            label = stringResource(R.string.set_as_default_launcher),
                                            onClick = { DefaultLauncherHelper.requestSetDefaultLauncher(context) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Item: Permissão de Mídia
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.permission_media),
                                        color = TextSecondary,
                                        fontSize = 13.sp
                                    )

                                    if (mediaState.isPermissionGranted) {
                                        Text(
                                            text = stringResource(R.string.permission_granted),
                                            color = Color(0xFF4CAF50),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    } else {
                                        ActionChip(
                                            label = stringResource(R.string.permission_denied),
                                            onClick = {
                                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Item: Permissão de GPS
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.permission_location),
                                        color = TextSecondary,
                                        fontSize = 13.sp
                                    )

                                    if (hasGpsPermission) {
                                        Text(
                                            text = stringResource(R.string.permission_granted),
                                            color = Color(0xFF4CAF50),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    } else {
                                        ActionChip(
                                            label = stringResource(R.string.permission_denied),
                                            onClick = {
                                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                    data = Uri.fromParts("package", context.packageName, null)
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Card 3: Link Oficial para a Página do Projeto
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(BorderStroke(1.dp, SurfaceCardBorder), RoundedCornerShape(16.dp))
                                .clickable {
                                    try {
                                        val intent = Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://joaohouto.github.io/clusterlauncher")
                                        ).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                },
                            shape = RoundedCornerShape(16.dp),
                            color = SurfaceCard
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(0x22E61924)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Language,
                                            contentDescription = null,
                                            tint = NeedleRed,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Portal do ClusterLauncher",
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "joaohouto.github.io/clusterlauncher",
                                            color = NeedleRed,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = Icons.Rounded.OpenInNew,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
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
 * Item individual para exibir um mapa baixado, permitindo seleção e exclusão.
 */
@Composable
private fun DownloadedMapItem(
    mapInfo: MapFileInfo,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val borderColor = if (mapInfo.isActive) NeedleRed else SurfaceCardBorder
    val bgColor = if (mapInfo.isActive) Color(0x22E61924) else Color(0xFF101215)

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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Indicador de seleção
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (mapInfo.isActive) NeedleRed else Color.Transparent)
                        .border(1.5.dp, if (mapInfo.isActive) NeedleRed else TextDisabled, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (mapInfo.isActive) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = mapInfo.displayName,
                        color = if (mapInfo.isActive) TextPrimary else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (mapInfo.isActive) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f MB • %s", mapInfo.sizeMb, mapInfo.formattedDate),
                        color = TextDisabled,
                        fontSize = 10.sp
                    )
                }
            }

            // Botão de Excluir
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.btn_delete_map),
                    tint = TextDisabled,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ProviderChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) NeedleRed else SurfaceCardBorder
    val bgColor = if (isSelected) Color(0x33E61924) else Color(0xFF101215)

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = label,
            color = if (isSelected) TextPrimary else TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun RadiusChip(
    label: String,
    sublabel: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) NeedleRed else SurfaceCardBorder
    val bgColor = if (isSelected) Color(0x33E61924) else Color(0xFF101215)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = if (isSelected) TextPrimary else TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
        Text(
            text = sublabel,
            color = if (isSelected) NeedleRed else TextDisabled,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun DownloadButton(
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NeedleRed)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.CloudDownload,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ActionChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = Color(0x22E61924),
        border = BorderStroke(1.dp, NeedleRed.copy(alpha = 0.5f))
    ) {
        Text(
            text = label,
            color = NeedleRed,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
