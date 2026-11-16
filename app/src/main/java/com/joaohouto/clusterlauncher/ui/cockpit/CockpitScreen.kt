package com.joaohouto.clusterlauncher.ui.cockpit

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joaohouto.clusterlauncher.data.model.AppItem
import com.joaohouto.clusterlauncher.data.model.DockSlot
import com.joaohouto.clusterlauncher.data.model.DockSlotType
import com.joaohouto.clusterlauncher.media.MediaManager
import com.joaohouto.clusterlauncher.ui.cockpit.components.AndroidAutoVerticalRail
import com.joaohouto.clusterlauncher.ui.cockpit.components.FullBleedMediaCard
import com.joaohouto.clusterlauncher.ui.cockpit.components.OfflineMapCard
import com.joaohouto.clusterlauncher.ui.drawer.AppDrawerScreen

@Composable
fun CockpitScreen(
    dockSlots: List<DockSlot>,
    onSlotLongClick: (DockSlot) -> Unit,
    onOpenDrawer: () -> Unit = {},
    onBluetoothClick: () -> Unit = {},
    onBluetoothLongClick: (() -> Unit)? = null,
    onSettingsClick: () -> Unit = {},
    onOpenFullScreenMap: () -> Unit = {},
    onOpenPlayer: () -> Unit = {},
    showMapOnHome: Boolean = true,
    isMapDarkMode: Boolean = true,
    onToggleMapDarkMode: (Boolean) -> Unit = {},
    mapFollowHeading: Boolean = true,
    onToggleMapFollowHeading: (Boolean) -> Unit = {},
    map3dPerspective: Boolean = true,
    isMapPaused: Boolean = false,
    fullScreenMode: Boolean = false,
    swapCockpitColumns: Boolean = false,
    hideMediaWhenNotPlaying: Boolean = false,
    isMediaCardDismissed: Boolean = false,
    onDismissMediaCard: () -> Unit = {},
    apps: List<AppItem> = emptyList(),
    isAndroidAutoDrawerOpen: Boolean = false,
    onAppClick: (AppItem) -> Unit = {},
    onPinToDock: (DockSlotType, AppItem) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val mediaState by MediaManager.mediaState.collectAsState()
    val showMediaCard = (!hideMediaWhenNotPlaying || mediaState.isPlaying) && !isMediaCardDismissed

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
            onSettingsClick = onSettingsClick,
            onOpenPlayer = onOpenPlayer
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
                                    onOpenPlayer = onOpenPlayer,
                                    onCloseCard = onDismissMediaCard,
                                    modifier = Modifier
                                        .weight(0.40f)
                                        .fillMaxHeight()
                                )
                            } else {
                                FullBleedMediaCard(
                                    onOpenPlayer = onOpenPlayer,
                                    onCloseCard = onDismissMediaCard,
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
                                onOpenPlayer = onOpenPlayer,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}
