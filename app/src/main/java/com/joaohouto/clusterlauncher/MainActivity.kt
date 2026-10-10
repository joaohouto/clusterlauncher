package com.joaohouto.clusterlauncher

import android.content.Intent
import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.joaohouto.clusterlauncher.data.model.DockSlot
import com.joaohouto.clusterlauncher.data.receiver.BluetoothStateReceiver
import com.joaohouto.clusterlauncher.data.receiver.UsbStateReceiver
import com.joaohouto.clusterlauncher.media.MediaManager
import com.joaohouto.clusterlauncher.media.NotificationListenerHelper
import com.joaohouto.clusterlauncher.ui.cockpit.CockpitScreen
import com.joaohouto.clusterlauncher.ui.cockpit.dialogs.BluetoothAppPickerModal
import com.joaohouto.clusterlauncher.ui.cockpit.dialogs.CarBrandPickerModal
import com.joaohouto.clusterlauncher.ui.cockpit.dialogs.FullScreenMapModal
import com.joaohouto.clusterlauncher.ui.cockpit.dialogs.LauncherSettingsModal
import com.joaohouto.clusterlauncher.ui.cockpit.dialogs.SlotAppPickerModal
import com.joaohouto.clusterlauncher.ui.drawer.AppDrawerViewModel
import com.joaohouto.clusterlauncher.ui.drawer.AppSlideScreen
import com.joaohouto.clusterlauncher.ui.theme.ClusterLauncherTheme
import com.joaohouto.clusterlauncher.ui.theme.DeepMetallicBackground
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterAccent
import com.joaohouto.clusterlauncher.ui.theme.getAccentThemeById
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
fun applyImmersiveMode(window: Window, isFullScreen: Boolean) {
    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
    if (isFullScreen) {
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
    } else {
        insetsController.show(WindowInsetsCompat.Type.systemBars())
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }
}

class MainActivity : ComponentActivity() {

    private val appDrawerViewModel: AppDrawerViewModel by viewModels()
    private var scrollToCockpitTrigger by mutableIntStateOf(0)
    private var isFullScreenMapOpen by mutableStateOf(false)

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
        com.joaohouto.clusterlauncher.utils.DefaultLauncherHelper.updateDefaultLauncherState(this)
        com.joaohouto.clusterlauncher.ui.cockpit.components.OsmdroidMapHelper.initOsmdroid(this)
        com.joaohouto.clusterlauncher.ui.cockpit.components.MapLibreMapHelper.initMapLibre(this)

        setContent {
            val accentThemeId by appDrawerViewModel.accentThemeId.collectAsState()
            val currentAccent = remember(accentThemeId) { getAccentThemeById(accentThemeId) }
            val fullScreenMode by appDrawerViewModel.fullScreenMode.collectAsState()

            LaunchedEffect(fullScreenMode, isFullScreenMapOpen) {
                applyImmersiveMode(window, fullScreenMode || isFullScreenMapOpen)
            }

            ClusterLauncherTheme(accentTheme = currentAccent) {
                LauncherMainContent(
                    viewModel = appDrawerViewModel,
                    scrollToCockpitTrigger = scrollToCockpitTrigger,
                    fullScreenMode = fullScreenMode,
                    showFullScreenMap = isFullScreenMapOpen,
                    onOpenFullScreenMap = { isFullScreenMapOpen = true },
                    onCloseFullScreenMap = { isFullScreenMapOpen = false }
                )
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
        appDrawerViewModel.loadApps()
        com.joaohouto.clusterlauncher.utils.DefaultLauncherHelper.updateDefaultLauncherState(this)
        com.joaohouto.clusterlauncher.data.location.GpsLocationRepository.checkPermission(this)
        if (com.joaohouto.clusterlauncher.data.location.GpsLocationRepository.hasPermission.value) {
            com.joaohouto.clusterlauncher.data.location.GpsLocationRepository.startListening(this)
        }
    }

    override fun onPause() {
        super.onPause()
        com.joaohouto.clusterlauncher.data.location.GpsLocationRepository.stopListening()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Whenever Home intent or new intent is received, scroll back to cockpit
        isFullScreenMapOpen = false
        scrollToCockpitTrigger++
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LauncherMainContent(
    viewModel: AppDrawerViewModel,
    scrollToCockpitTrigger: Int,
    fullScreenMode: Boolean = false,
    showFullScreenMap: Boolean = false,
    onOpenFullScreenMap: () -> Unit = {},
    onCloseFullScreenMap: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val apps by viewModel.apps.collectAsState()
    val dockSlots by viewModel.dockSlots.collectAsState()
    val selectedCarBrand by viewModel.carBrand.collectAsState()
    val bluetoothPackage by viewModel.bluetoothPackage.collectAsState()
    val showMapOnHome by viewModel.showMapOnHome.collectAsState()
    val mapDarkMode by viewModel.mapDarkMode.collectAsState()
    val mapFollowHeading by viewModel.mapFollowHeading.collectAsState()
    val accentThemeId by viewModel.accentThemeId.collectAsState()
    val mediaCardPosition by viewModel.mediaCardPosition.collectAsState()
    val swapCockpitColumns by viewModel.swapCockpitColumns.collectAsState()
    val hideMediaWhenNotPlaying by viewModel.hideMediaWhenNotPlaying.collectAsState()
    val accent = LocalClusterAccent.current.primary

    val appSlides = remember(apps) {
        if (apps.isEmpty()) listOf(emptyList()) else apps.chunked(10)
    }

    val totalAppSlides = appSlides.size
    val pageCount = 1 + totalAppSlides
    val pagerState = rememberPagerState(pageCount = { pageCount })

    var activeSlotForPicker by remember { mutableStateOf<DockSlot?>(null) }
    var showBrandPicker by remember { mutableStateOf(false) }
    var showSettingsModal by remember { mutableStateOf(false) }
    var showBluetoothPicker by remember { mutableStateOf(false) }

    // React to Home button or onNewIntent
    LaunchedEffect(scrollToCockpitTrigger) {
        if (scrollToCockpitTrigger > 0 && pagerState.currentPage != 0) {
            pagerState.animateScrollToPage(0)
        }
    }

    // BackHandler: if on full screen map, close it; if on Drawer, return to Cockpit
    BackHandler(enabled = showFullScreenMap || pagerState.currentPage != 0) {
        if (showFullScreenMap) {
            onCloseFullScreenMap()
        } else {
            coroutineScope.launch {
                pagerState.animateScrollToPage(0)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepMetallicBackground)
            .then(
                if (fullScreenMode || showFullScreenMap) Modifier else Modifier.safeDrawingPadding()
            )
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !showFullScreenMap,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            if (page == 0) {
                CockpitScreen(
                    dockSlots = dockSlots,
                    selectedCarBrand = selectedCarBrand,
                    onSlotLongClick = { slot -> activeSlotForPicker = slot },
                    onBrandClick = { showBrandPicker = true },
                    onBluetoothClick = { viewModel.launchBluetooth(context) },
                    onBluetoothLongClick = { showBluetoothPicker = true },
                    onSettingsClick = { showSettingsModal = true },
                    onOpenFullScreenMap = onOpenFullScreenMap,
                    showMapOnHome = showMapOnHome,
                    isMapDarkMode = mapDarkMode,
                    onToggleMapDarkMode = { viewModel.setMapDarkMode(it) },
                    mapFollowHeading = mapFollowHeading,
                    onToggleMapFollowHeading = { viewModel.setMapFollowHeading(it) },
                    fullScreenMode = fullScreenMode,
                    mediaCardPosition = mediaCardPosition,
                    swapCockpitColumns = swapCockpitColumns,
                    hideMediaWhenNotPlaying = hideMediaWhenNotPlaying
                )
            } else {
                val slideIndex = page - 1
                val slideApps = appSlides.getOrElse(slideIndex) { emptyList() }
                AppSlideScreen(
                    apps = slideApps,
                    onAppClick = { app -> viewModel.launchApp(viewModel.getApplication(), app.packageName) },
                    onPinToDock = { slotType, app -> viewModel.assignAppToSlot(slotType, app.packageName) }
                )
            }
        }

        // Slide pagination indicator dots (only visible when outside Cockpit)
        if (pagerState.currentPage > 0 && totalAppSlides > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val activeSlideIndex = (pagerState.currentPage - 1).coerceIn(0, totalAppSlides - 1)
                for (i in 0 until totalAppSlides) {
                    val isActive = i == activeSlideIndex
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(6.dp)
                            .width(if (isActive) 24.dp else 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isActive) accent else Color(0xFF383B44))
                    )
                }
            }
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
                selectedAccentId = accentThemeId,
                onSelectAccent = { viewModel.selectAccentTheme(it) },
                showMapOnHome = showMapOnHome,
                onToggleShowMap = { viewModel.setShowMapOnHome(it) },
                isMapDarkMode = mapDarkMode,
                onToggleMapDarkMode = { viewModel.setMapDarkMode(it) },
                mapFollowHeading = mapFollowHeading,
                onToggleMapFollowHeading = { viewModel.setMapFollowHeading(it) },
                fullScreenMode = fullScreenMode,
                onToggleFullScreenMode = { viewModel.setFullScreenMode(it) },
                mediaCardPosition = mediaCardPosition,
                onSelectMediaCardPosition = { viewModel.setMediaCardPosition(it) },
                swapCockpitColumns = swapCockpitColumns,
                onToggleSwapCockpitColumns = { viewModel.setSwapCockpitColumns(it) },
                hideMediaWhenNotPlaying = hideMediaWhenNotPlaying,
                onToggleHideMediaWhenNotPlaying = { viewModel.setHideMediaWhenNotPlaying(it) },
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
                fullScreenMode = fullScreenMode || showFullScreenMap,
                onToggleDarkMode = { viewModel.setMapDarkMode(it) },
                onDismiss = onCloseFullScreenMap
            )
        }
    }
}