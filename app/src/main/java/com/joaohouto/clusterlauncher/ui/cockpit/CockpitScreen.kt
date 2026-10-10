package com.joaohouto.clusterlauncher.ui.cockpit

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
import com.joaohouto.clusterlauncher.data.model.CarBrand
import com.joaohouto.clusterlauncher.data.model.DockSlot
import com.joaohouto.clusterlauncher.ui.cockpit.components.CarBrandEmblem
import com.joaohouto.clusterlauncher.ui.cockpit.components.ClockWidget
import com.joaohouto.clusterlauncher.ui.cockpit.components.OfflineMapCard
import com.joaohouto.clusterlauncher.ui.cockpit.components.QuickDockBar
import com.joaohouto.clusterlauncher.ui.cockpit.components.SystemStatusRow
import com.joaohouto.clusterlauncher.ui.cockpit.components.UniversalMediaCard

@Composable
fun CockpitScreen(
    dockSlots: List<DockSlot>,
    selectedCarBrand: CarBrand,
    onSlotLongClick: (DockSlot) -> Unit,
    onBrandClick: () -> Unit,
    onBluetoothClick: () -> Unit = {},
    onBluetoothLongClick: (() -> Unit)? = null,
    onSettingsClick: () -> Unit = {},
    onOpenFullScreenMap: () -> Unit = {},
    showMapOnHome: Boolean = true,
    isMapDarkMode: Boolean = false,
    onToggleMapDarkMode: (Boolean) -> Unit = {},
    mapFollowHeading: Boolean = false,
    onToggleMapFollowHeading: (Boolean) -> Unit = {},
    fullScreenMode: Boolean = false,
    mediaCardPosition: String = "right",
    swapCockpitColumns: Boolean = false,
    hideMediaWhenNotPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {

    val mediaState by MediaManager.mediaState.collectAsState()
    val showMediaCard = !hideMediaWhenNotPlaying || mediaState.isPlaying
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
