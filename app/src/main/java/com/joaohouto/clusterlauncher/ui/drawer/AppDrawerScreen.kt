package com.joaohouto.clusterlauncher.ui.drawer

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.joaohouto.clusterlauncher.data.model.AppItem
import com.joaohouto.clusterlauncher.data.model.DockSlotType
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterAccent

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawerScreen(
    apps: List<AppItem>,
    onAppClick: (AppItem) -> Unit,
    onPinToDock: (DockSlotType, AppItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalClusterAccent.current.primary
    val appSlides = remember(apps) {
        if (apps.isEmpty()) listOf(emptyList()) else apps.chunked(10)
    }

    val pagerState = rememberPagerState(pageCount = { appSlides.size })

    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { slideIndex ->
            AppSlideScreen(
                apps = appSlides.getOrElse(slideIndex) { emptyList() },
                onAppClick = onAppClick,
                onPinToDock = onPinToDock
            )
        }

        if (appSlides.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in appSlides.indices) {
                    val isActive = i == pagerState.currentPage
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
    }
}
