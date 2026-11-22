package com.joaohouto.clusterlauncher.ui.cockpit.dialogs

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.joaohouto.clusterlauncher.BuildConfig
import com.joaohouto.clusterlauncher.R
import com.joaohouto.clusterlauncher.data.local.PreferencesDataStore
import com.joaohouto.clusterlauncher.data.location.GpsLocationRepository
import com.joaohouto.clusterlauncher.data.map.MBTilesDownloader
import com.joaohouto.clusterlauncher.data.map.MBTilesRepository
import com.joaohouto.clusterlauncher.data.map.MapFileInfo
import com.joaohouto.clusterlauncher.data.map.TileProvider
import com.joaohouto.clusterlauncher.data.model.CarBrand
import com.joaohouto.clusterlauncher.data.model.EqualizerPreset
import com.joaohouto.clusterlauncher.data.update.AppUpdateManager
import com.joaohouto.clusterlauncher.data.update.UpdateStatus
import com.joaohouto.clusterlauncher.media.MediaManager
import com.joaohouto.clusterlauncher.ui.cockpit.dialogs.settings.AudioSettingsPanel
import com.joaohouto.clusterlauncher.ui.cockpit.dialogs.settings.LyricsSettingsPanel
import com.joaohouto.clusterlauncher.ui.cockpit.dialogs.settings.MapsSettingsPanel
import com.joaohouto.clusterlauncher.ui.cockpit.dialogs.settings.SystemSettingsPanel
import com.joaohouto.clusterlauncher.ui.cockpit.dialogs.settings.VehicleSettingsPanel
import com.joaohouto.clusterlauncher.ui.theme.DeepMetallicBackground
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterAccent
import com.joaohouto.clusterlauncher.ui.theme.LocalUiScale
import com.joaohouto.clusterlauncher.ui.theme.NeedleRed
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCardBorder
import com.joaohouto.clusterlauncher.ui.theme.TextDisabled
import com.joaohouto.clusterlauncher.ui.theme.TextPrimary
import com.joaohouto.clusterlauncher.ui.theme.TextSecondary
import com.joaohouto.clusterlauncher.utils.DefaultLauncherHelper
import kotlinx.coroutines.launch

enum class SettingsTab(val titleRes: Int, val icon: ImageVector) {
    MAPS(R.string.tab_maps, Icons.Rounded.Map),
    AUDIO(R.string.tab_audio, Icons.AutoMirrored.Rounded.VolumeUp),
    LYRICS(R.string.tab_lyrics, Icons.Rounded.GraphicEq),
    VEHICLE(R.string.tab_vehicle, Icons.Rounded.DirectionsCar),
    SYSTEM(R.string.tab_system, Icons.Rounded.Settings)
}

/**
 * Modal de configurações do Cockpit simplificado para uso veicular com alta legibilidade.
 * Cada aba é modularizada em seu próprio arquivo no pacote [settings].
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
    val isBtAutoPauseOnDisconnect by preferencesDataStore.btAutoPauseOnDisconnectFlow.collectAsState(initial = true)
    val isEqEnabled by preferencesDataStore.eqEnabledFlow.collectAsState(initial = false)
    val eqPreset by preferencesDataStore.eqPresetFlow.collectAsState(initial = EqualizerPreset.FLAT)
    val eqBandsMb by preferencesDataStore.eqBandLevelsFlow.collectAsState(initial = listOf(0, 0, 0, 0, 0))
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
                                isBtAutoPauseOnDisconnect = isBtAutoPauseOnDisconnect,
                                currentAppVolumePercent = appVolumePercent,
                                currentAudioSyncOffsetMs = audioSyncOffsetMs,
                                isEqEnabled = isEqEnabled,
                                currentEqPreset = eqPreset,
                                currentEqBandsMb = eqBandsMb,
                                onSelectAppVolume = { scope.launch { preferencesDataStore.saveAppVolumePercent(it) } },
                                onToggleLoudness = { scope.launch { preferencesDataStore.saveLoudnessBoost(it) } },
                                onToggleAutoplayOnStart = { scope.launch { preferencesDataStore.saveAutoplayOnStart(it) } },
                                onToggleKeepScreenOn = { scope.launch { preferencesDataStore.saveKeepScreenOn(it) } },
                                onToggleBtAutoPauseOnDisconnect = { scope.launch { preferencesDataStore.saveBtAutoPauseOnDisconnect(it) } },
                                onSelectAudioSyncOffset = { scope.launch { preferencesDataStore.saveAudioSyncOffsetMs(it) } },
                                onToggleEqEnabled = { scope.launch { preferencesDataStore.saveEqEnabled(it) } },
                                onSelectEqPreset = { scope.launch { preferencesDataStore.saveEqPreset(it) } },
                                onUpdateEqBandsMb = { scope.launch { preferencesDataStore.saveEqBandLevels(it) } }
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
