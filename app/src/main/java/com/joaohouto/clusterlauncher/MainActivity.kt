package com.joaohouto.clusterlauncher

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.joaohouto.clusterlauncher.data.local.AppDatabase
import com.joaohouto.clusterlauncher.data.local.PreferencesDataStore
import com.joaohouto.clusterlauncher.data.map.MBTilesRepository
import com.joaohouto.clusterlauncher.data.model.DisplayMode
import com.joaohouto.clusterlauncher.data.model.DockSlot
import com.joaohouto.clusterlauncher.data.receiver.BluetoothStateReceiver
import com.joaohouto.clusterlauncher.data.receiver.CarLightDetector
import com.joaohouto.clusterlauncher.data.receiver.UsbStateReceiver
import com.joaohouto.clusterlauncher.data.repository.MusicRepository
import com.joaohouto.clusterlauncher.data.update.AppUpdateManager
import com.joaohouto.clusterlauncher.media.MediaManager
import com.joaohouto.clusterlauncher.media.NotificationListenerHelper
import com.joaohouto.clusterlauncher.player.PlayerController
import com.joaohouto.clusterlauncher.ui.cockpit.CockpitScreen
import com.joaohouto.clusterlauncher.ui.cockpit.dialogs.BluetoothAppPickerModal
import com.joaohouto.clusterlauncher.ui.cockpit.dialogs.CarBrandPickerModal
import com.joaohouto.clusterlauncher.ui.cockpit.dialogs.FullScreenMapModal
import com.joaohouto.clusterlauncher.ui.cockpit.dialogs.LauncherSettingsModal
import com.joaohouto.clusterlauncher.ui.cockpit.dialogs.SettingsTab
import com.joaohouto.clusterlauncher.ui.cockpit.dialogs.SlotAppPickerModal
import com.joaohouto.clusterlauncher.ui.drawer.AppDrawerViewModel
import com.joaohouto.clusterlauncher.ui.drawer.AppSlideScreen
import com.joaohouto.clusterlauncher.ui.player.HomeViewModel
import com.joaohouto.clusterlauncher.ui.player.IntegratedPlayerScreen
import com.joaohouto.clusterlauncher.ui.player.PlayerViewModel
import com.joaohouto.clusterlauncher.ui.theme.ClusterLauncherTheme
import com.joaohouto.clusterlauncher.ui.theme.DeepMetallicBackground
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterAccent
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterColors
import com.joaohouto.clusterlauncher.ui.theme.getAccentThemeById
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
fun applyImmersiveMode(window: Window, isFullScreen: Boolean) {
    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
    if (isFullScreen) {
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    } else {
        insetsController.show(WindowInsetsCompat.Type.systemBars())
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }
}

class MainActivity : ComponentActivity() {

    private val appDrawerViewModel: AppDrawerViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()

    private var scrollToCockpitTrigger by mutableIntStateOf(0)
    private var isFullScreenMapOpen by mutableStateOf(false)
    private var isPlayerOpen by mutableStateOf(false)

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == Intent.ACTION_PACKAGE_REMOVED ||
                action == Intent.ACTION_PACKAGE_ADDED ||
                action == Intent.ACTION_PACKAGE_REPLACED
            ) {
                appDrawerViewModel.loadApps(forceReload = true)
            }
        }
    }

    private val requestAudioPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        checkManageExternalStorageAndScan()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val darkColor = DeepMetallicBackground.toArgb()
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(darkColor))
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(darkColor),
            navigationBarStyle = SystemBarStyle.dark(darkColor)
        )

        UsbStateReceiver.checkInitialUsbState(this)
        BluetoothStateReceiver.checkInitialBluetoothState(this)
        CarLightDetector.register(this)
        com.joaohouto.clusterlauncher.data.location.GpsLocationRepository.restoreLastLocation(this)
        com.joaohouto.clusterlauncher.utils.DefaultLauncherHelper.updateDefaultLauncherState(this)
        com.joaohouto.clusterlauncher.ui.cockpit.components.OsmdroidMapHelper.initOsmdroid(this)
        com.joaohouto.clusterlauncher.ui.cockpit.components.MapLibreMapHelper.initMapLibre(this)
        lifecycleScope.launch(Dispatchers.IO) {
            MBTilesRepository.initialize(this@MainActivity)
        }

        try {
            val pkgFilter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            ContextCompat.registerReceiver(
                this,
                packageChangeReceiver,
                pkgFilter,
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (e: Exception) {
            Log.w("MainActivity", "Failed to register packageChangeReceiver", e)
        }

        MediaManager.init(this)
        PlayerController.getInstance(this).initialize()
        checkAndRequestAudioStoragePermissions()

        val preferencesDataStore = PreferencesDataStore.getInstance(this)

        setContent {
            val accentThemeId by appDrawerViewModel.accentThemeId.collectAsState()
            val currentAccent = remember(accentThemeId) { getAccentThemeById(accentThemeId) }
            val fullScreenMode by appDrawerViewModel.fullScreenMode.collectAsState()
            val uiScalePercent by appDrawerViewModel.uiScalePercent.collectAsState()
            val displayMode by appDrawerViewModel.displayMode.collectAsState()
            val isSystemNight by CarLightDetector.isSystemNightMode.collectAsState()
            val headlightOn by CarLightDetector.headlightOn.collectAsState()
            val isDayHighContrast = remember(displayMode, isSystemNight, headlightOn) {
                DisplayMode.resolveIsDayHighContrast(displayMode, isSystemNight, headlightOn)
            }
            val isKeepScreenOn by preferencesDataStore.keepScreenOnFlow.collectAsState(initial = true)
            val isShowLyrics by preferencesDataStore.showLyricsFlow.collectAsState(initial = true)
            val autoCheckUpdates by preferencesDataStore.autoCheckUpdatesFlow.collectAsState(initial = true)

            LaunchedEffect(autoCheckUpdates) {
                if (autoCheckUpdates) {
                    AppUpdateManager.checkForUpdates(this@MainActivity, isManual = false)
                }
            }

            LaunchedEffect(fullScreenMode, isFullScreenMapOpen) {
                applyImmersiveMode(window, fullScreenMode || isFullScreenMapOpen)
            }

            LaunchedEffect(isKeepScreenOn) {
                if (isKeepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            ClusterLauncherTheme(
                accentTheme = currentAccent,
                uiScalePercent = uiScalePercent,
                isDayHighContrast = isDayHighContrast
            ) {
                LauncherMainContent(
                    viewModel = appDrawerViewModel,
                    homeViewModel = homeViewModel,
                    playerViewModel = playerViewModel,
                    showLyrics = isShowLyrics,
                    isDayHighContrast = isDayHighContrast,
                    scrollToCockpitTrigger = scrollToCockpitTrigger,
                    fullScreenMode = fullScreenMode,
                    showFullScreenMap = isFullScreenMapOpen,
                    onOpenFullScreenMap = { isFullScreenMapOpen = true },
                    onCloseFullScreenMap = { isFullScreenMapOpen = false },
                    showPlayer = isPlayerOpen,
                    onOpenPlayer = { isPlayerOpen = true },
                    onClosePlayer = { isPlayerOpen = false }
                )
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        CarLightDetector.updateFromConfiguration(newConfig)
    }

    override fun onDestroy() {
        super.onDestroy()
        CarLightDetector.unregister(this)
        try {
            unregisterReceiver(packageChangeReceiver)
        } catch (_: Exception) {
        }
    }

    private fun checkAndRequestAudioStoragePermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            requestAudioPermissionsLauncher.launch(permissions.toTypedArray())
        } else {
            checkManageExternalStorageAndScan()
        }
    }

    private fun checkManageExternalStorageAndScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        startActivity(intent)
                    } catch (e2: Exception) {
                        Log.w("MainActivity", "Cannot launch MANAGE_ALL_FILES_ACCESS_PERMISSION", e2)
                    }
                }
            }
        }
        triggerScanIfEmpty()
    }

    private fun triggerScanIfEmpty() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(this@MainActivity)
                if (db.folderDao().getFolderCount() == 0) {
                    Log.d("MainActivity", "Database empty. Triggering initial media scan.")
                    MusicRepository.getInstance(this@MainActivity).triggerScan(clearOld = false)
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Error checking folder count", e)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveMode(window, appDrawerViewModel.fullScreenMode.value || isFullScreenMapOpen)
        }
    }

    override fun onResume() {
        super.onResume()
        val hasPermission = NotificationListenerHelper.isNotificationListenerEnabled(this)
        MediaManager.setPermissionGranted(hasPermission)
        AppUpdateManager.onActivityResumed(this)
        appDrawerViewModel.loadApps()
        com.joaohouto.clusterlauncher.utils.DefaultLauncherHelper.updateDefaultLauncherState(this)
        com.joaohouto.clusterlauncher.data.location.GpsLocationRepository.checkPermission(this)
        if (com.joaohouto.clusterlauncher.data.location.GpsLocationRepository.hasPermission.value) {
            com.joaohouto.clusterlauncher.data.location.GpsLocationRepository.startListening(this)
        }
        if (!MBTilesRepository.mapState.value.isMapLoaded) {
            lifecycleScope.launch(Dispatchers.IO) {
                MBTilesRepository.initialize(this@MainActivity)
            }
        }
        triggerScanIfEmpty()
    }

    override fun onPause() {
        super.onPause()
        com.joaohouto.clusterlauncher.data.location.GpsLocationRepository.stopListening()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        isFullScreenMapOpen = false
        isPlayerOpen = false
        scrollToCockpitTrigger++
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_CHANNEL_UP -> {
                    MediaManager.skipToNext()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    MediaManager.skipToPrevious()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_HEADSETHOOK -> {
                    MediaManager.playPause()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

@Composable
private fun LauncherMainContent(
    viewModel: AppDrawerViewModel,
    homeViewModel: HomeViewModel,
    playerViewModel: PlayerViewModel,
    showLyrics: Boolean,
    isDayHighContrast: Boolean = false,
    scrollToCockpitTrigger: Int,
    fullScreenMode: Boolean = false,
    showFullScreenMap: Boolean = false,
    onOpenFullScreenMap: () -> Unit = {},
    onCloseFullScreenMap: () -> Unit = {},
    showPlayer: Boolean = false,
    onOpenPlayer: () -> Unit = {},
    onClosePlayer: () -> Unit = {}
) {
    val context = LocalContext.current

    val apps by viewModel.apps.collectAsState()
    val dockSlots by viewModel.dockSlots.collectAsState()
    val selectedCarBrand by viewModel.carBrand.collectAsState()
    val bluetoothPackage by viewModel.bluetoothPackage.collectAsState()
    val showMapOnHome by viewModel.showMapOnHome.collectAsState()
    val mapDarkMode by viewModel.mapDarkMode.collectAsState()
    val mapFollowHeading by viewModel.mapFollowHeading.collectAsState()
    val map3dPerspective by viewModel.map3dPerspective.collectAsState()
    val accentThemeId by viewModel.accentThemeId.collectAsState()
    val swapCockpitColumns by viewModel.swapCockpitColumns.collectAsState()
    val hideMediaWhenNotPlaying by viewModel.hideMediaWhenNotPlaying.collectAsState()
    val uiScalePercent by viewModel.uiScalePercent.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()
    val colors = LocalClusterColors.current

    var isAndroidAutoDrawerOpen by remember { mutableStateOf(false) }
    var isMediaCardDismissed by remember { mutableStateOf(false) }

    var activeSlotForPicker by remember { mutableStateOf<DockSlot?>(null) }
    var showBrandPicker by remember { mutableStateOf(false) }
    var showSettingsModal by remember { mutableStateOf(false) }
    var settingsInitialTab by remember { mutableStateOf(SettingsTab.MAPS) }
    var showBluetoothPicker by remember { mutableStateOf(false) }

    LaunchedEffect(scrollToCockpitTrigger) {
        if (scrollToCockpitTrigger > 0) {
            isAndroidAutoDrawerOpen = false
            isMediaCardDismissed = false
        }
    }

    BackHandler(enabled = !showPlayer && (showFullScreenMap || isAndroidAutoDrawerOpen)) {
        when {
            showFullScreenMap -> onCloseFullScreenMap()
            isAndroidAutoDrawerOpen -> isAndroidAutoDrawerOpen = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .then(
                if (fullScreenMode || showFullScreenMap) Modifier else Modifier.safeDrawingPadding()
            )
    ) {
        CockpitScreen(
            dockSlots = dockSlots,
            onSlotLongClick = { slot -> activeSlotForPicker = slot },
            onOpenDrawer = {
                isAndroidAutoDrawerOpen = !isAndroidAutoDrawerOpen
            },
            onBluetoothClick = { viewModel.launchBluetooth(context) },
            onBluetoothLongClick = { showBluetoothPicker = true },
            onSettingsClick = {
                settingsInitialTab = if (AppUpdateManager.hasPendingUpdate) {
                    SettingsTab.SYSTEM
                } else {
                    SettingsTab.MAPS
                }
                showSettingsModal = true
            },
            onOpenFullScreenMap = onOpenFullScreenMap,
            onOpenPlayer = {
                isMediaCardDismissed = false
                onOpenPlayer()
            },
            showMapOnHome = showMapOnHome,
            isMapDarkMode = mapDarkMode,
            onToggleMapDarkMode = { viewModel.setMapDarkMode(it) },
            mapFollowHeading = mapFollowHeading,
            onToggleMapFollowHeading = { viewModel.setMapFollowHeading(it) },
            map3dPerspective = map3dPerspective,
            isMapPaused = showFullScreenMap || showPlayer || isAndroidAutoDrawerOpen,
            fullScreenMode = fullScreenMode,
            swapCockpitColumns = swapCockpitColumns,
            hideMediaWhenNotPlaying = hideMediaWhenNotPlaying,
            isMediaCardDismissed = isMediaCardDismissed,
            onDismissMediaCard = {
                MediaManager.stopAndClearQueue()
                isMediaCardDismissed = true
            },
            apps = apps,
            isAndroidAutoDrawerOpen = isAndroidAutoDrawerOpen,
            onAppClick = { app -> viewModel.launchApp(viewModel.getApplication(), app.packageName) },
            onPinToDock = { slotType, app -> viewModel.assignAppToSlot(slotType, app.packageName) }
        )

        // Integrated Full-Screen Player Overlay
        AnimatedVisibility(
            visible = showPlayer,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            IntegratedPlayerScreen(
                homeViewModel = homeViewModel,
                playerViewModel = playerViewModel,
                showLyrics = showLyrics,
                onCloseToCockpit = onClosePlayer,
                onOpenSettings = {
                    settingsInitialTab = SettingsTab.AUDIO
                    showSettingsModal = true
                }
            )
        }

        activeSlotForPicker?.let { slot ->
            SlotAppPickerModal(
                slotType = slot.type,
                apps = apps,
                onAppSelected = { app ->
                    viewModel.assignAppToSlot(slot.type, app.packageName)
                    activeSlotForPicker = null
                },
                onDismiss = { activeSlotForPicker = null }
            )
        }

        if (showBrandPicker) {
            CarBrandPickerModal(
                selectedBrand = selectedCarBrand,
                onBrandSelected = { brand ->
                    viewModel.selectCarBrand(brand)
                    showBrandPicker = false
                },
                onDismiss = { showBrandPicker = false }
            )
        }

        if (showSettingsModal) {
            LauncherSettingsModal(
                selectedBrand = selectedCarBrand,
                initialTab = settingsInitialTab,
                selectedAccentId = accentThemeId,
                onSelectAccent = { viewModel.selectAccentTheme(it) },
                displayMode = displayMode,
                isDayHighContrast = isDayHighContrast,
                onSelectDisplayMode = { viewModel.setDisplayMode(it) },
                showMapOnHome = showMapOnHome,
                onToggleShowMap = { viewModel.setShowMapOnHome(it) },
                isMapDarkMode = mapDarkMode,
                onToggleMapDarkMode = { viewModel.setMapDarkMode(it) },
                mapFollowHeading = mapFollowHeading,
                onToggleMapFollowHeading = { viewModel.setMapFollowHeading(it) },
                map3dPerspective = map3dPerspective,
                onToggleMap3dPerspective = { viewModel.setMap3dPerspective(it) },
                fullScreenMode = fullScreenMode,
                onToggleFullScreenMode = { viewModel.setFullScreenMode(it) },
                swapCockpitColumns = swapCockpitColumns,
                onToggleSwapCockpitColumns = { viewModel.setSwapCockpitColumns(it) },
                hideMediaWhenNotPlaying = hideMediaWhenNotPlaying,
                onToggleHideMediaWhenNotPlaying = { viewModel.setHideMediaWhenNotPlaying(it) },
                uiScalePercent = uiScalePercent,
                onSelectUiScalePercent = { viewModel.setUiScalePercent(it) },
                bluetoothPackage = bluetoothPackage,
                onSelectBluetoothPackage = { viewModel.setBluetoothPackage(it) },
                onOpenBluetoothPicker = { showBluetoothPicker = true },
                onTestBluetooth = { viewModel.launchBluetooth(context) },
                onSelectBrandClick = {
                    showSettingsModal = false
                    showBrandPicker = true
                },
                onDismiss = { showSettingsModal = false }
            )
        }

        if (showBluetoothPicker) {
            BluetoothAppPickerModal(
                apps = apps,
                currentPackage = bluetoothPackage,
                onAppSelected = { pkg ->
                    viewModel.setBluetoothPackage(pkg)
                    showBluetoothPicker = false
                },
                onDismiss = { showBluetoothPicker = false }
            )
        }

        if (showFullScreenMap) {
            FullScreenMapModal(
                initialDarkMode = mapDarkMode,
                followHeading = mapFollowHeading,
                is3dPerspective = map3dPerspective,
                fullScreenMode = fullScreenMode || showFullScreenMap,
                onToggleDarkMode = { viewModel.setMapDarkMode(it) },
                onDismiss = onCloseFullScreenMap
            )
        }
    }
}
