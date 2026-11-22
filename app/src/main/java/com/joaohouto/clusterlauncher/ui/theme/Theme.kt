package com.joaohouto.clusterlauncher.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat

val LocalUiScale = compositionLocalOf { 1.0f }

@Composable
fun ClusterLauncherTheme(
    accentTheme: ClusterAccent = AccentNeedleRed,
    uiScalePercent: Int = 120,
    isDayHighContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = if (isDayHighContrast) DayHighContrastClusterPalette else NightClusterPalette

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val bgArgb = palette.background.toArgb()
                window.statusBarColor = bgArgb
                window.navigationBarColor = bgArgb
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    val colorScheme = remember(accentTheme, palette) {
        darkColorScheme(
            primary = accentTheme.primary,
            onPrimary = accentTheme.onPrimary,
            primaryContainer = accentTheme.dark,
            onPrimaryContainer = palette.textPrimary,
            secondary = palette.metallicIntermediate,
            onSecondary = palette.textPrimary,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surfaceCard,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.metallicIntermediate,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.surfaceCardBorder
        )
    }

    val scaleFactor = (uiScalePercent.coerceIn(75, 130) / 100f)
    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity, scaleFactor) {
        Density(
            density = baseDensity.density * scaleFactor,
            fontScale = baseDensity.fontScale
        )
    }

    CompositionLocalProvider(
        LocalClusterAccent provides accentTheme,
        LocalClusterColors provides palette,
        LocalUiScale provides scaleFactor,
        LocalDensity provides scaledDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
