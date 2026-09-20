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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joaohouto.clusterlauncher.data.model.CarBrand
import com.joaohouto.clusterlauncher.data.model.DockSlot
import com.joaohouto.clusterlauncher.ui.cockpit.components.CarBrandEmblem
import com.joaohouto.clusterlauncher.ui.cockpit.components.ClockWidget
import com.joaohouto.clusterlauncher.ui.cockpit.components.QuickDockBar
import com.joaohouto.clusterlauncher.ui.cockpit.components.SystemStatusRow
import com.joaohouto.clusterlauncher.ui.cockpit.components.UniversalMediaCard

@Composable
fun CockpitScreen(
    dockSlots: List<DockSlot>,
    selectedCarBrand: CarBrand,
    onSlotLongClick: (DockSlot) -> Unit,
    onBrandClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Section: Instrument Cluster (45%) on Left, Media Player (55%) on Right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Instrument Panel (Brand Logo + Clock) (45%)
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.45f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CarBrandEmblem(
                    brand = selectedCarBrand,
                    onClick = onBrandClick,
                    size = 100.dp
                )

                Spacer(modifier = Modifier.height(24.dp))

                ClockWidget()
            }

            Spacer(modifier = Modifier.width(20.dp))

            // Right: Universal Media Widget and Peripheral Status (55%)
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.55f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                UniversalMediaCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 100.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                SystemStatusRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                )
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
