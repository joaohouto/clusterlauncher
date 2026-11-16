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
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joaohouto.clusterlauncher.BuildConfig
import com.joaohouto.clusterlauncher.R
import com.joaohouto.clusterlauncher.data.local.PreferencesDataStore
import com.joaohouto.clusterlauncher.data.location.GpsLocationRepository
import com.joaohouto.clusterlauncher.data.map.DownloadProgress
import com.joaohouto.clusterlauncher.data.map.MBTilesDownloader
import com.joaohouto.clusterlauncher.data.map.MBTilesRepository
import com.joaohouto.clusterlauncher.data.map.MapFileInfo
import com.joaohouto.clusterlauncher.data.map.TileProvider
import com.joaohouto.clusterlauncher.data.model.CarBrand
import com.joaohouto.clusterlauncher.data.model.CockpitLayoutPreset
import com.joaohouto.clusterlauncher.data.repository.AutomotivePackageResolver
import com.joaohouto.clusterlauncher.data.update.AppUpdateManager
import com.joaohouto.clusterlauncher.data.update.UpdateStatus
import com.joaohouto.clusterlauncher.media.MediaManager
import com.joaohouto.clusterlauncher.ui.theme.ALL_ACCENT_THEMES
import com.joaohouto.clusterlauncher.ui.theme.DeepMetallicBackground
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterAccent
import com.joaohouto.clusterlauncher.ui.theme.LocalUiScale
import com.joaohouto.clusterlauncher.ui.theme.MetallicIntermediate
import com.joaohouto.clusterlauncher.ui.theme.NeedleRed
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCard
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCardBorder
import com.joaohouto.clusterlauncher.ui.theme.TextDisabled
import com.joaohouto.clusterlauncher.ui.theme.TextPrimary
import com.joaohouto.clusterlauncher.ui.theme.TextSecondary
import com.joaohouto.clusterlauncher.utils.DefaultLauncherHelper
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

enum class SettingsTab(val titleRes: Int, val icon: ImageVector) {
    MAPS(R.string.tab_maps, Icons.Rounded.Map),
    AUDIO(R.string.tab_audio, Icons.AutoMirrored.Rounded.VolumeUp),
    LYRICS(R.string.tab_lyrics, Icons.Rounded.GraphicEq),
    VEHICLE(R.string.tab_vehicle, Icons.Rounded.DirectionsCar),
    SYSTEM(R.string.tab_system, Icons.Rounded.Settings)
}

/**
 * Modal de configurações do Cockpit simplificado para uso veicular com alta legibilidade.
 * Interface moderna em abas com paleta contida e toques táteis confortáveis.
 */
@Composable
fun LauncherSettingsModal(
    selectedBrand: CarBrand,
    initialTab: SettingsTab = SettingsTab.MAPS,
    selectedAccentId: String = "needle_red",
    onSelectAccent: (String) -> Unit = {},
    showMapOnHome: Boolean = true,
    onToggleShowMap: (Boolean) -> Unit = {},
    isMapDarkMode: Boolean = true,
    onToggleMapDarkMode: (Boolean) -> Unit = {},
    mapFollowHeading: Boolean = true,
    onToggleMapFollowHeading: (Boolean) -> Unit = {},
    map3dPerspective: Boolean = true,
    onToggleMap3dPerspective: (Boolean) -> Unit = {},
    fullScreenMode: Boolean = false,
    onToggleFullScreenMode: (Boolean) -> Unit = {},
    swapCockpitColumns: Boolean = false,
    onToggleSwapCockpitColumns: (Boolean) -> Unit = {},
    hideMediaWhenNotPlaying: Boolean = false,
    onToggleHideMediaWhenNotPlaying: (Boolean) -> Unit = {},
    uiScalePercent: Int = 120,
    onSelectUiScalePercent: (Int) -> Unit = {},
    bluetoothPackage: String? = null,
    onSelectBluetoothPackage: (String?) -> Unit = {},
    onOpenBluetoothPicker: () -> Unit = {},
    onTestBluetooth: () -> Unit = {},
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
    val preferencesDataStore = remember(context) { PreferencesDataStore.getInstance(context) }
    val appVolumePercent by preferencesDataStore.appVolumePercentFlow.collectAsState(initial = 100)
    val isLoudnessEnabled by preferencesDataStore.loudnessBoostFlow.collectAsState(initial = true)
    val isKeepScreenOn by preferencesDataStore.keepScreenOnFlow.collectAsState(initial = true)
    val isShowLyrics by preferencesDataStore.showLyricsFlow.collectAsState(initial = true)
    val lyricsOffsetMs by preferencesDataStore.lyricsOffsetMsFlow.collectAsState(initial = 0)
    val audioSyncOffsetMs by preferencesDataStore.audioSyncOffsetMsFlow.collectAsState(initial = 0)
    val isAutoplayOnStart by preferencesDataStore.autoplayOnStartFlow.collectAsState(initial = true)
    val autoCheckUpdates by preferencesDataStore.autoCheckUpdatesFlow.collectAsState(initial = true)
    val updateStatus by AppUpdateManager.updateStatus.collectAsState()
    val hasUpdateBadge = updateStatus is UpdateStatus.UpdateAvailable || updateStatus is UpdateStatus.ReadyToInstall

    var activeTab by remember(initialTab) { mutableStateOf(initialTab) }
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
                        Toast.makeText(context, "Mapa carregado: ${file.name}", Toast.LENGTH_SHORT).show()
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
                                    text = "v${BuildConfig.VERSION_NAME}",
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
                                    contentDescription = stringResource(tab.titleRes),
                                    tint = contentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(tab.titleRes),
                                    color = contentColor,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (tab == SettingsTab.SYSTEM && hasUpdateBadge) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(accent)
                                    )
                                }
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
                            },
                            mapFollowHeading = mapFollowHeading,
                            onToggleMapFollowHeading = onToggleMapFollowHeading,
                            map3dPerspective = map3dPerspective,
                            onToggleMap3dPerspective = onToggleMap3dPerspective
                        )

                        SettingsTab.AUDIO -> AudioSettingsPanel(
                            isLoudnessEnabled = isLoudnessEnabled,
                            isAutoplayOnStart = isAutoplayOnStart,
                            isKeepScreenOn = isKeepScreenOn,
                            currentAppVolumePercent = appVolumePercent,
                            currentAudioSyncOffsetMs = audioSyncOffsetMs,
                            onSelectAppVolume = { scope.launch { preferencesDataStore.saveAppVolumePercent(it) } },
                            onToggleLoudness = { scope.launch { preferencesDataStore.saveLoudnessBoost(it) } },
                            onToggleAutoplayOnStart = { scope.launch { preferencesDataStore.saveAutoplayOnStart(it) } },
                            onToggleKeepScreenOn = { scope.launch { preferencesDataStore.saveKeepScreenOn(it) } },
                            onSelectAudioSyncOffset = { scope.launch { preferencesDataStore.saveAudioSyncOffsetMs(it) } }
                        )

                        SettingsTab.LYRICS -> LyricsSettingsPanel(
                            isShowLyrics = isShowLyrics,
                            currentLyricsOffsetMs = lyricsOffsetMs,
                            onToggleShowLyrics = { scope.launch { preferencesDataStore.saveShowLyrics(it) } },
                            onSelectLyricsOffset = { scope.launch { preferencesDataStore.saveLyricsOffsetMs(it) } }
                        )

                        SettingsTab.VEHICLE -> VehicleSettingsPanel(
                            selectedBrand = selectedBrand,
                            selectedAccentId = selectedAccentId,
                            onSelectAccent = onSelectAccent,
                            onSelectBrandClick = onSelectBrandClick
                        )

                        SettingsTab.SYSTEM -> SystemSettingsPanel(
                            autoCheckUpdates = autoCheckUpdates,
                            onToggleAutoCheckUpdates = { scope.launch { preferencesDataStore.saveAutoCheckUpdates(it) } },
                            updateStatus = updateStatus,
                            onCheckUpdatesNow = { AppUpdateManager.checkForUpdates(context, isManual = true) },
                            onDownloadAndInstallUpdate = { release -> AppUpdateManager.downloadAndInstall(context, release) },
                            onInstallReadyApk = { file -> AppUpdateManager.installApk(context, file) },
                            showMapOnHome = showMapOnHome,
                            onToggleShowMap = onToggleShowMap,
                            isMapDarkMode = isMapDarkMode,
                            onToggleMapDarkMode = onToggleMapDarkMode,
                            mapFollowHeading = mapFollowHeading,
                            onToggleMapFollowHeading = onToggleMapFollowHeading,
                            map3dPerspective = map3dPerspective,
                            onToggleMap3dPerspective = onToggleMap3dPerspective,
                            fullScreenMode = fullScreenMode,
                            onToggleFullScreenMode = onToggleFullScreenMode,
                            swapCockpitColumns = swapCockpitColumns,
                            onToggleSwapCockpitColumns = onToggleSwapCockpitColumns,
                            hideMediaWhenNotPlaying = hideMediaWhenNotPlaying,
                            onToggleHideMediaWhenNotPlaying = onToggleHideMediaWhenNotPlaying,
                            uiScalePercent = uiScalePercent,
                            onSelectUiScalePercent = onSelectUiScalePercent,
                            isDefaultLauncher = isDefaultLauncher,
                            onSetDefaultLauncher = { DefaultLauncherHelper.requestSetDefaultLauncher(context) },
                            bluetoothPackage = bluetoothPackage,
                            onSelectBluetoothPackage = onSelectBluetoothPackage,
                            onOpenBluetoothPicker = onOpenBluetoothPicker,
                            onTestBluetooth = onTestBluetooth,
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
    onCancelDownload: () -> Unit,
    mapFollowHeading: Boolean,
    onToggleMapFollowHeading: (Boolean) -> Unit,
    map3dPerspective: Boolean = false,
    onToggleMap3dPerspective: (Boolean) -> Unit = {}
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

        // Configuração de Orientação e Perspectiva do Mapa
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Travar Indicador no Topo (Heading-Up)",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Seta fixa apontando para cima e mapa rotacionando com o deslocamento do carro",
                            color = TextDisabled,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = mapFollowHeading,
                        onCheckedChange = onToggleMapFollowHeading,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accent,
                            uncheckedThumbColor = TextDisabled,
                            uncheckedTrackColor = Color(0xFF1E222B)
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.map_3d_perspective),
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.map_3d_perspective_desc),
                            color = TextDisabled,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = map3dPerspective,
                        onCheckedChange = onToggleMap3dPerspective,
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
                text = "Personalize o emblema da montadora e iluminação do cockpit.",
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
                                        tint = theme.onPrimary,
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
    autoCheckUpdates: Boolean = true,
    onToggleAutoCheckUpdates: (Boolean) -> Unit = {},
    updateStatus: UpdateStatus = UpdateStatus.Idle,
    onCheckUpdatesNow: () -> Unit = {},
    onDownloadAndInstallUpdate: (com.joaohouto.clusterlauncher.data.update.ReleaseInfo) -> Unit = {},
    onInstallReadyApk: (java.io.File) -> Unit = {},
    showMapOnHome: Boolean,
    onToggleShowMap: (Boolean) -> Unit,
    isMapDarkMode: Boolean,
    onToggleMapDarkMode: (Boolean) -> Unit,
    mapFollowHeading: Boolean,
    onToggleMapFollowHeading: (Boolean) -> Unit,
    map3dPerspective: Boolean = true,
    onToggleMap3dPerspective: (Boolean) -> Unit = {},
    fullScreenMode: Boolean,
    onToggleFullScreenMode: (Boolean) -> Unit,
    swapCockpitColumns: Boolean = false,
    onToggleSwapCockpitColumns: (Boolean) -> Unit = {},
    hideMediaWhenNotPlaying: Boolean = false,
    onToggleHideMediaWhenNotPlaying: (Boolean) -> Unit = {},
    uiScalePercent: Int = 100,
    onSelectUiScalePercent: (Int) -> Unit = {},
    isDefaultLauncher: Boolean,
    onSetDefaultLauncher: () -> Unit,
    bluetoothPackage: String? = null,
    onSelectBluetoothPackage: (String?) -> Unit = {},
    onOpenBluetoothPicker: () -> Unit = {},
    onTestBluetooth: () -> Unit = {},
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
                text = stringResource(R.string.system_title),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.system_preferences_subtitle),
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        // Cartão: Atualizações do Aplicativo (GitHub Releases)
        AppUpdatesCard(
            autoCheckUpdates = autoCheckUpdates,
            onToggleAutoCheckUpdates = onToggleAutoCheckUpdates,
            updateStatus = updateStatus,
            onCheckUpdatesNow = onCheckUpdatesNow,
            onDownloadAndInstallUpdate = onDownloadAndInstallUpdate,
            onInstallReadyApk = onInstallReadyApk,
            accent = accent
        )

        // Cartão 1: Visualização do Cockpit
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = stringResource(R.string.appearance_cockpit_title),
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
                        Text(text = stringResource(R.string.show_map_on_home), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(text = stringResource(R.string.show_map_on_home_desc), color = TextDisabled, fontSize = 11.sp)
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

                // Toggle: Travar Indicador no Topo (Heading-Up)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.map_follow_heading), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(text = stringResource(R.string.map_follow_heading_desc), color = TextDisabled, fontSize = 11.sp)
                    }
                    Switch(
                        checked = mapFollowHeading,
                        onCheckedChange = onToggleMapFollowHeading,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accent,
                            uncheckedThumbColor = TextDisabled,
                            uncheckedTrackColor = Color(0xFF1E222B)
                        )
                    )
                }

                // Toggle: Visão 3D de Navegação (Estilo Android Auto)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.map_3d_perspective), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(text = stringResource(R.string.map_3d_perspective_desc), color = TextDisabled, fontSize = 11.sp)
                    }
                    Switch(
                        checked = map3dPerspective,
                        onCheckedChange = onToggleMap3dPerspective,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accent,
                            uncheckedThumbColor = TextDisabled,
                            uncheckedTrackColor = Color(0xFF1E222B)
                        )
                    )
                }

                // Toggle: Modo Tela Cheia Imersivo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.fullscreen_mode), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(text = stringResource(R.string.fullscreen_mode_desc), color = TextDisabled, fontSize = 11.sp)
                    }
                    Switch(
                        checked = fullScreenMode,
                        onCheckedChange = onToggleFullScreenMode,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accent,
                            uncheckedThumbColor = TextDisabled,
                            uncheckedTrackColor = Color(0xFF1E222B)
                        )
                    )
                }

                // Toggle: Inverter Lados do Cockpit (Swap Columns)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.swap_columns_title),
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.swap_columns_desc),
                            color = TextDisabled,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = swapCockpitColumns,
                        onCheckedChange = onToggleSwapCockpitColumns,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accent,
                            uncheckedThumbColor = TextDisabled,
                            uncheckedTrackColor = Color(0xFF1E222B)
                        )
                    )
                }

                // Toggle: Ocultar Card de Mídia se Inativo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.hide_media_not_playing_title),
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.hide_media_not_playing_desc),
                            color = TextDisabled,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = hideMediaWhenNotPlaying,
                        onCheckedChange = onToggleHideMediaWhenNotPlaying,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accent,
                            uncheckedThumbColor = TextDisabled,
                            uncheckedTrackColor = Color(0xFF1E222B)
                        )
                    )
                }

                // Seletor: Escala da Interface (UI Scale)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.ui_scale_title),
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.ui_scale_desc),
                                color = TextDisabled,
                                fontSize = 11.sp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF101215),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "$uiScalePercent%",
                                color = accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onSelectUiScalePercent((uiScalePercent - 5).coerceIn(75, 130)) },
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1E222B),
                            border = BorderStroke(1.dp, SurfaceCardBorder)
                        ) {
                            Text(
                                text = "-5%",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        val scalePresets = listOf(80, 90, 100, 110, 120)
                        scalePresets.forEach { preset ->
                            val isSelected = uiScalePercent == preset
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onSelectUiScalePercent(preset) },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) accent.copy(alpha = 0.16f) else Color(0xFF101215),
                                border = BorderStroke(1.dp, if (isSelected) accent else SurfaceCardBorder)
                            ) {
                                Text(
                                    text = "$preset%",
                                    color = if (isSelected) TextPrimary else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onSelectUiScalePercent((uiScalePercent + 5).coerceIn(75, 130)) },
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1E222B),
                            border = BorderStroke(1.dp, SurfaceCardBorder)
                        ) {
                            Text(
                                text = "+5%",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
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
                            text = stringResource(R.string.default_launcher_title),
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(if (isDefaultLauncher) R.string.default_launcher_active_desc else R.string.default_launcher_inactive_desc),
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
                        text = stringResource(if (isDefaultLauncher) R.string.btn_change else R.string.btn_set),
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Cartão: Conectividade Bluetooth da Multimídia
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
                    text = stringResource(R.string.bluetooth_app_title),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Rounded.Bluetooth,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            val displayLabel = remember(bluetoothPackage) {
                                AutomotivePackageResolver.resolveBluetoothAppInfo(context, bluetoothPackage)
                            }
                            Text(
                                text = stringResource(R.string.bluetooth_app_subtitle),
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = displayLabel,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!bluetoothPackage.isNullOrEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelectBluetoothPackage(null) },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1E222B),
                                border = BorderStroke(1.dp, SurfaceCardBorder)
                            ) {
                                Text(
                                    text = stringResource(R.string.btn_auto),
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenBluetoothPicker() },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E222B),
                            border = BorderStroke(1.dp, SurfaceCardBorder)
                        ) {
                            Text(
                                text = stringResource(R.string.btn_change),
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }

                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onTestBluetooth() },
                            shape = RoundedCornerShape(8.dp),
                            color = accent.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = stringResource(R.string.btn_open),
                                color = accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
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
                    text = stringResource(R.string.permissions_panel_title),
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
                    Text(text = stringResource(R.string.permission_location), color = TextSecondary, fontSize = 13.sp)
                    if (hasGpsPermission) {
                        Text(text = stringResource(R.string.permission_granted), color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Surface(
                            modifier = Modifier.clickable { onOpenAppSettings() },
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1E222B),
                            border = BorderStroke(1.dp, SurfaceCardBorder)
                        ) {
                            Text(text = stringResource(R.string.btn_grant), color = accent, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }

                // Notificações de Mídia
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.permission_media), color = TextSecondary, fontSize = 13.sp)
                    if (isMediaPermissionGranted) {
                        Text(text = stringResource(R.string.permission_granted), color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Surface(
                            modifier = Modifier.clickable { onOpenNotificationListenerSettings() },
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1E222B),
                            border = BorderStroke(1.dp, SurfaceCardBorder)
                        ) {
                            Text(text = stringResource(R.string.btn_grant), color = accent, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
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
            Icon(imageVector = Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = TextDisabled, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun AudioSettingsPanel(
    isLoudnessEnabled: Boolean,
    isAutoplayOnStart: Boolean,
    isKeepScreenOn: Boolean,
    currentAppVolumePercent: Int,
    currentAudioSyncOffsetMs: Int,
    onSelectAppVolume: (Int) -> Unit,
    onToggleLoudness: (Boolean) -> Unit,
    onToggleAutoplayOnStart: (Boolean) -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onSelectAudioSyncOffset: (Int) -> Unit
) {
    val accent = LocalClusterAccent.current
    val scrollState = rememberScrollState()
    var volumeSliderValue by remember(currentAppVolumePercent) {
        mutableFloatStateOf(currentAppVolumePercent.toFloat())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = stringResource(R.string.settings_audio_category),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Parâmetros de processamento de som e comportamento do tocador integrado.",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        // 1. Normalização de Volume (EBU R128)
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_loudness_title),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.settings_loudness_subtitle),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Switch(
                    checked = isLoudnessEnabled,
                    onCheckedChange = onToggleLoudness,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accent.primary,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = MetallicIntermediate,
                        uncheckedBorderColor = SurfaceCardBorder
                    )
                )
            }
        }

        // 2. Volume Geral do App
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_volume_title),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.settings_volume_subtitle),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Text(
                        text = stringResource(R.string.settings_volume_percent, volumeSliderValue.roundToInt()),
                        color = accent.primary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Slider(
                    value = volumeSliderValue,
                    onValueChange = { volumeSliderValue = it },
                    onValueChangeFinished = { onSelectAppVolume(volumeSliderValue.roundToInt()) },
                    valueRange = 10f..100f,
                    steps = 17,
                    colors = SliderDefaults.colors(
                        thumbColor = accent.primary,
                        activeTrackColor = accent.primary,
                        inactiveTrackColor = Color(0xFF1E222B)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 3. Autoplay on Launch
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_autoplay_title),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.settings_autoplay_subtitle),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Switch(
                    checked = isAutoplayOnStart,
                    onCheckedChange = onToggleAutoplayOnStart,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accent.primary,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = MetallicIntermediate,
                        uncheckedBorderColor = SurfaceCardBorder
                    )
                )
            }
        }

        // 4. Manter tela ligada (Keep Screen On)
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_screen_on_title),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.settings_screen_on_subtitle),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Switch(
                    checked = isKeepScreenOn,
                    onCheckedChange = onToggleKeepScreenOn,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accent.primary,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = MetallicIntermediate,
                        uncheckedBorderColor = SurfaceCardBorder
                    )
                )
            }
        }

        // 5. Ajuste manual de latência de áudio (-500 ms a +500 ms, passo 50 ms)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            var syncSliderValue by remember(currentAudioSyncOffsetMs) {
                mutableFloatStateOf(currentAudioSyncOffsetMs.toFloat())
            }
            val snappedMs = ((syncSliderValue / 50f).roundToInt() * 50).coerceIn(-500, 500)
            val offsetLabel = if (snappedMs > 0) "+$snappedMs ms" else "$snappedMs ms"

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_audio_sync_title),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.settings_audio_sync_subtitle),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Text(
                        text = offsetLabel,
                        color = accent.primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                syncSliderValue = 0f
                                onSelectAudioSyncOffset(0)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Slider(
                    value = syncSliderValue,
                    onValueChange = { raw ->
                        syncSliderValue = ((raw / 50f).roundToInt() * 50).coerceIn(-500, 500).toFloat()
                    },
                    onValueChangeFinished = {
                        val finalMs = ((syncSliderValue / 50f).roundToInt() * 50).coerceIn(-500, 500)
                        onSelectAudioSyncOffset(finalMs)
                    },
                    valueRange = -500f..500f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = accent.primary,
                        activeTrackColor = accent.primary,
                        inactiveTrackColor = Color(0xFF1E222B)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun LyricsSettingsPanel(
    isShowLyrics: Boolean,
    currentLyricsOffsetMs: Int,
    onToggleShowLyrics: (Boolean) -> Unit,
    onSelectLyricsOffset: (Int) -> Unit
) {
    val accent = LocalClusterAccent.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = "Letras & Sincronia",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Exibição de letras .LRC e calibração de timing para áudio veicular.",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        // 1. Switch Exibir Letras
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_lyrics_title),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.settings_lyrics_subtitle),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Switch(
                    checked = isShowLyrics,
                    onCheckedChange = onToggleShowLyrics,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accent.primary,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = MetallicIntermediate,
                        uncheckedBorderColor = SurfaceCardBorder
                    )
                )
            }
        }

        // 2. Ajuste Fino de Sincronia / Latência (Offset em ms)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_lyrics_sync_title),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.settings_lyrics_sync_subtitle),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    val offsetText = if (currentLyricsOffsetMs > 0) "+$currentLyricsOffsetMs ms" else "$currentLyricsOffsetMs ms"
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF101215),
                        border = BorderStroke(1.dp, accent.primary.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = offsetText,
                            color = accent.primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectLyricsOffset(currentLyricsOffsetMs - 100) },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E222B),
                        border = BorderStroke(1.dp, SurfaceCardBorder)
                    ) {
                        Text(
                            text = "-100 ms (Adiantar)",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectLyricsOffset(0) },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E222B),
                        border = BorderStroke(1.dp, SurfaceCardBorder)
                    ) {
                        Text(
                            text = "0 ms (Zerar)",
                            color = if (currentLyricsOffsetMs == 0) accent.primary else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectLyricsOffset(currentLyricsOffsetMs + 100) },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E222B),
                        border = BorderStroke(1.dp, SurfaceCardBorder)
                    ) {
                        Text(
                            text = "+100 ms (Atrasar)",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }

        // 3. Informações sobre Arquivos LRC
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Suporte a Arquivos de Letras (.LRC)",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.settings_lyrics_lrc_info),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun AppUpdatesCard(
    autoCheckUpdates: Boolean,
    onToggleAutoCheckUpdates: (Boolean) -> Unit,
    updateStatus: UpdateStatus,
    onCheckUpdatesNow: () -> Unit,
    onDownloadAndInstallUpdate: (com.joaohouto.clusterlauncher.data.update.ReleaseInfo) -> Unit,
    onInstallReadyApk: (java.io.File) -> Unit,
    accent: Color
) {
    val hasUpdate = updateStatus is UpdateStatus.UpdateAvailable || updateStatus is UpdateStatus.ReadyToInstall

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, if (hasUpdate) accent else SurfaceCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdateAlt,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.update_section_title),
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.update_current_version, BuildConfig.VERSION_NAME),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                when (updateStatus) {
                    is UpdateStatus.Checking -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                color = accent,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = stringResource(R.string.update_status_checking),
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                    is UpdateStatus.UpdateAvailable -> {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onDownloadAndInstallUpdate(updateStatus.release) },
                            shape = RoundedCornerShape(8.dp),
                            color = accent,
                            border = BorderStroke(1.dp, accent)
                        ) {
                            Text(
                                text = stringResource(R.string.update_btn_download_install, updateStatus.release.tagName),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                            )
                        }
                    }
                    is UpdateStatus.ReadyToInstall -> {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onInstallReadyApk(updateStatus.apkFile) },
                            shape = RoundedCornerShape(8.dp),
                            color = accent,
                            border = BorderStroke(1.dp, accent)
                        ) {
                            Text(
                                text = stringResource(R.string.update_btn_install_ready, updateStatus.release.tagName),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                            )
                        }
                    }
                    is UpdateStatus.Downloading -> {
                        Text(
                            text = stringResource(
                                R.string.update_status_downloading,
                                updateStatus.release.tagName,
                                updateStatus.progressPercent
                            ),
                            color = accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    else -> {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onCheckUpdatesNow() },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E222B),
                            border = BorderStroke(1.dp, SurfaceCardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    tint = TextPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.update_btn_check),
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            when (updateStatus) {
                is UpdateStatus.UpToDate -> {
                    Text(
                        text = stringResource(R.string.update_status_up_to_date, updateStatus.currentVersion),
                        color = Color(0xFF81C784),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                is UpdateStatus.Error -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = updateStatus.message,
                            color = NeedleRed,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (updateStatus.release != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onDownloadAndInstallUpdate(updateStatus.release) },
                                shape = RoundedCornerShape(8.dp),
                                color = accent.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, accent)
                            ) {
                                Text(
                                    text = "Tentar novamente",
                                    color = accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
                is UpdateStatus.Downloading -> {
                    val progressFraction = (updateStatus.progressPercent / 100f).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = accent,
                        trackColor = Color(0xFF262930)
                    )
                }
                is UpdateStatus.UpdateAvailable -> {
                    if (updateStatus.release.changelog.isNotBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF101215),
                            border = BorderStroke(1.dp, SurfaceCardBorder)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = stringResource(R.string.update_status_available, updateStatus.release.title),
                                    color = accent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = updateStatus.release.changelog,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    maxLines = 8,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                else -> {}
            }

            // Switch: Buscar atualizações automaticamente ao iniciar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.update_auto_check_title),
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.update_auto_check_subtitle),
                        color = TextDisabled,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = autoCheckUpdates,
                    onCheckedChange = onToggleAutoCheckUpdates,
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
}
