package com.joaohouto.clusterlauncher.ui.cockpit

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joaohouto.clusterlauncher.media.MediaManager
import androidx.compose.runtime.collectAsState
import com.joaohouto.clusterlauncher.data.model.AppItem
import com.joaohouto.clusterlauncher.data.model.CarBrand
import com.joaohouto.clusterlauncher.data.model.CockpitLayoutPreset
import com.joaohouto.clusterlauncher.data.model.DockSlot
import com.joaohouto.clusterlauncher.data.model.DockSlotType
import com.joaohouto.clusterlauncher.ui.cockpit.components.AndroidAutoVerticalRail
import com.joaohouto.clusterlauncher.ui.cockpit.components.CarBrandEmblem
import com.joaohouto.clusterlauncher.ui.cockpit.components.ClockWidget
import com.joaohouto.clusterlauncher.ui.cockpit.components.FullBleedMediaCard
import com.joaohouto.clusterlauncher.ui.cockpit.components.OfflineMapCard
import com.joaohouto.clusterlauncher.ui.cockpit.components.QuickDockBar
import com.joaohouto.clusterlauncher.ui.cockpit.components.SystemStatusRow
import com.joaohouto.clusterlauncher.ui.cockpit.components.UniversalMediaCard
import com.joaohouto.clusterlauncher.ui.drawer.AppDrawerScreen

@Composable
fun CockpitScreen(
    dockSlots: List<DockSlot>,
    selectedCarBrand: CarBrand,
    onSlotLongClick: (DockSlot) -> Unit,
    onBrandClick: () -> Unit,
    onOpenDrawer: () -> Unit = {},
    onBluetoothClick: () -> Unit = {},
    onBluetoothLongClick: (() -> Unit)? = null,
    onSettingsClick: () -> Unit = {},
    onOpenFullScreenMap: () -> Unit = {},
    layoutPreset: CockpitLayoutPreset = CockpitLayoutPreset.CLASSIC,
    showMapOnHome: Boolean = true,
    isMapDarkMode: Boolean = false,
    onToggleMapDarkMode: (Boolean) -> Unit = {},
    mapFollowHeading: Boolean = false,
    onToggleMapFollowHeading: (Boolean) -> Unit = {},
    map3dPerspective: Boolean = false,
    isMapPaused: Boolean = false,
    fullScreenMode: Boolean = false,
    mediaCardPosition: String = "right",
    swapCockpitColumns: Boolean = false,
    hideMediaWhenNotPlaying: Boolean = false,
    apps: List<AppItem> = emptyList(),
    isAndroidAutoDrawerOpen: Boolean = false,
    onAppClick: (AppItem) -> Unit = {},
    onPinToDock: (DockSlotType, AppItem) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {

    val mediaState by MediaManager.mediaState.collectAsState()
    val showMediaCard = !hideMediaWhenNotPlaying || mediaState.isPlaying

    if (layoutPreset == CockpitLayoutPreset.ANDROID_AUTO) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(
                    start = 12.dp,
                    end = 16.dp,
                    top = if (fullScreenMode) 10.dp else 12.dp,
                    bottom = if (fullScreenMode) 10.dp else 12.dp
                ),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Barra Lateral Vertical Estilo Android Auto (Apenas Ícones)
            AndroidAutoVerticalRail(
                dockSlots = dockSlots,
                onSlotLongClick = onSlotLongClick,
                onOpenDrawer = onOpenDrawer,
                isDrawerOpen = isAndroidAutoDrawerOpen,
                onBluetoothClick = onBluetoothClick,
                onBluetoothLongClick = onBluetoothLongClick,
                onSettingsClick = onSettingsClick
            )

            Spacer(modifier = Modifier.width(14.dp))

            // Área de Conteúdo do Android Auto: Gaveta de Apps ou Split Screen (Mídia + Mapa)
            Crossfade(
                targetState = isAndroidAutoDrawerOpen,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                label = "AndroidAutoContent"
            ) { drawerOpen ->
                if (drawerOpen) {
                    AppDrawerScreen(
                        apps = apps,
                        onAppClick = onAppClick,
                        onPinToDock = onPinToDock
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when {
                            showMediaCard && showMapOnHome -> {
                                if (swapCockpitColumns) {
                                    OfflineMapCard(
                                        modifier = Modifier
                                            .weight(0.60f)
                                            .fillMaxHeight(),
                                        isDarkMode = isMapDarkMode,
                                        followHeading = mapFollowHeading,
                                        is3dPerspective = map3dPerspective,
                                        isMapPaused = isMapPaused,
                                        onMapClick = onOpenFullScreenMap
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    FullBleedMediaCard(
                                        modifier = Modifier
                                            .weight(0.40f)
                                            .fillMaxHeight()
                                    )
                                } else {
                                    FullBleedMediaCard(
                                        modifier = Modifier
                                            .weight(0.40f)
                                            .fillMaxHeight()
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    OfflineMapCard(
                                        modifier = Modifier
                                            .weight(0.60f)
                                            .fillMaxHeight(),
                                        isDarkMode = isMapDarkMode,
                                        followHeading = mapFollowHeading,
                                        is3dPerspective = map3dPerspective,
                                        isMapPaused = isMapPaused,
                                        onMapClick = onOpenFullScreenMap
                                    )
                                }
                            }
                            showMapOnHome -> {
                                OfflineMapCard(
                                    modifier = Modifier.fillMaxSize(),
                                    isDarkMode = isMapDarkMode,
                                    followHeading = mapFollowHeading,
                                    is3dPerspective = map3dPerspective,
                                    isMapPaused = isMapPaused,
                                    onMapClick = onOpenFullScreenMap
                                )
                            }
                            else -> {
                                FullBleedMediaCard(
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
        return
    }

    val isMediaOnLeft = mediaCardPosition == "left"
    val isMediaOnLeftAndVisible = isMediaOnLeft && showMediaCard

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = if (fullScreenMode) 14.dp else 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {

        // Top Section: Instrument Cluster and Map / Media columns (swappable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val instrumentCol: @Composable (Modifier) -> Unit = { colModifier ->
                Column(
                    modifier = colModifier.fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isMediaOnLeftAndVisible) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CarBrandEmblem(
                                brand = selectedCarBrand,
                                onClick = onBrandClick,
                                size = 72.dp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            ClockWidget(isCompact = true)

                            Spacer(modifier = Modifier.height(10.dp))

                            SystemStatusRow(
                                modifier = Modifier.fillMaxWidth(),
                                isLarge = true,
                                onBluetoothClick = onBluetoothClick,
                                onBluetoothLongClick = onBluetoothLongClick,
                                onSettingsClick = onSettingsClick
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        UniversalMediaCard(
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        CarBrandEmblem(
                            brand = selectedCarBrand,
                            onClick = onBrandClick,
                            size = 100.dp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        ClockWidget(isCompact = false)

                        Spacer(modifier = Modifier.height(16.dp))

                        SystemStatusRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                            onBluetoothClick = onBluetoothClick,
                            onBluetoothLongClick = onBluetoothLongClick,
                            onSettingsClick = onSettingsClick
                        )
                    }
                }
            }

            val mapCol: @Composable (Modifier) -> Unit = { colModifier ->
                Column(
                    modifier = colModifier.fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (showMapOnHome) {
                        OfflineMapCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            isDarkMode = isMapDarkMode,
                            followHeading = mapFollowHeading,
                            is3dPerspective = map3dPerspective,
                            isMapPaused = isMapPaused,
                            onMapClick = onOpenFullScreenMap
                        )
                    }

                    if (!isMediaOnLeft && showMediaCard) {
                        if (showMapOnHome) {
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        UniversalMediaCard(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            val instWeight = if (isMediaOnLeftAndVisible) 0.46f else 0.45f
            val mapWeight = if (isMediaOnLeftAndVisible) 0.54f else 0.55f

            if (swapCockpitColumns) {
                mapCol(Modifier.weight(mapWeight))
                Spacer(modifier = Modifier.width(20.dp))
                instrumentCol(Modifier.weight(instWeight))
            } else {
                instrumentCol(Modifier.weight(instWeight))
                Spacer(modifier = Modifier.width(20.dp))
                mapCol(Modifier.weight(mapWeight))
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bottom Section: Fixed 5-slot Quick Dock
        QuickDockBar(
            slots = dockSlots,
            onSlotLongClick = onSlotLongClick
        )
    }
}
