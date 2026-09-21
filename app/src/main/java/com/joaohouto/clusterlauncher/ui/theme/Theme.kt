package com.joaohouto.clusterlauncher.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

private val AutomotiveColorScheme = darkColorScheme(
    primary = NeedleRed,
    onPrimary = TextPrimary,
    primaryContainer = NeedleRedDark,
    onPrimaryContainer = TextPrimary,
    secondary = MetallicIntermediate,
    onSecondary = TextPrimary,
    background = DeepMetallicBackground,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = MetallicIntermediate,
    onSurfaceVariant = TextSecondary,
    outline = SurfaceCardBorder
)

@Composable
fun ClusterLauncherTheme(
    accentTheme: ClusterAccent = AccentNeedleRed,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = DeepMetallicBackground.toArgb()
                window.navigationBarColor = DeepMetallicBackground.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    val colorScheme = remember(accentTheme) {
        AutomotiveColorScheme.copy(
            primary = accentTheme.primary,
            primaryContainer = accentTheme.dark
        )
    }

    CompositionLocalProvider(LocalClusterAccent provides accentTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}