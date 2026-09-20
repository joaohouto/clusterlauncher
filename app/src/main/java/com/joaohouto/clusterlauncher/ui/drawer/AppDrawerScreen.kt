package com.joaohouto.clusterlauncher.ui.drawer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.joaohouto.clusterlauncher.data.model.AppItem
import com.joaohouto.clusterlauncher.data.model.DockSlotType

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawerScreen(
    apps: List<AppItem>,
    onAppClick: (AppItem) -> Unit,
    onPinToDock: (DockSlotType, AppItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val appSlides = remember(apps) {
        if (apps.isEmpty()) listOf(emptyList()) else apps.chunked(10)
    }

    val pagerState = rememberPagerState(pageCount = { appSlides.size })

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize()
    ) { slideIndex ->
        AppSlideScreen(
            apps = appSlides.getOrElse(slideIndex) { emptyList() },
            onAppClick = onAppClick,
            onPinToDock = onPinToDock
        )
    }
}
