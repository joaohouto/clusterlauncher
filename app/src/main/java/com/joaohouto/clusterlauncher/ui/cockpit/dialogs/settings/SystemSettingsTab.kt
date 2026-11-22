package com.joaohouto.clusterlauncher.ui.cockpit.dialogs.settings


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


@Composable
internal fun SystemSettingsPanel(
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
