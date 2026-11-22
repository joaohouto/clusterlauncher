package com.joaohouto.clusterlauncher.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// Automotive Cluster Palette (Base / Night defaults)
val DeepMetallicBackground = Color(0xFF0B0C0E)
val DeepMetallicBackgroundSecondary = Color(0xFF121316)
val SurfaceCard = Color(0xFF181A1E)
val SurfaceCardBorder = Color(0xFF262930)
val MetallicIntermediate = Color(0xFF2E323A)
val MetallicBorder = Color(0xFF3A3F47)
val NeedleRed = Color(0xFFE61924)
val NeedleRedDark = Color(0xFFB0121B)
val NeedleRedGlow = Color(0x33E61924)

// Typography (Base / Night defaults)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF9A9DA6)
val TextDisabled = Color(0xFF555963)

// Progress Bar
val TrackRail = Color(0xFF262930)
val TrackFill = Color(0xFFE61924)

/**
 * Paleta dinâmica do Cockpit para alternância entre Modo Noite (escuro suave)
 * e Modo Dia (alto contraste mantendo o aspecto e fundo escuro).
 */
@Immutable
data class ClusterColorPalette(
    val isDayHighContrast: Boolean,
    val background: Color,
    val backgroundSecondary: Color,
    val surfaceCard: Color,
    val surfaceCardElevated: Color,
    val surfaceCardBorder: Color,
    val metallicIntermediate: Color,
    val metallicBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val trackRail: Color,
    val sidebarBackground: Color,
    val floatingOverlaySurface: Color,
    val mapBackgroundHex: String,
    val mapVignetteAlpha: Float
)

val NightClusterPalette = ClusterColorPalette(
    isDayHighContrast = false,
    background = Color(0xFF0B0C0E),
    backgroundSecondary = Color(0xFF121316),
    surfaceCard = Color(0xFF181A1E),
    surfaceCardElevated = Color(0xFF1E222B),
    surfaceCardBorder = Color(0xFF262930),
    metallicIntermediate = Color(0xFF2E323A),
    metallicBorder = Color(0xFF3A3F47),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF9A9DA6),
    textDisabled = Color(0xFF555963),
    trackRail = Color(0xFF262930),
    sidebarBackground = Color(0xFF0A0C0F),
    floatingOverlaySurface = Color(0xEE181A1F),
    mapBackgroundHex = "#0E1014",
    mapVignetteAlpha = 0.22f
)

val DayHighContrastClusterPalette = ClusterColorPalette(
    isDayHighContrast = true,
    background = Color(0xFF14171D),
    backgroundSecondary = Color(0xFF1D2129),
    surfaceCard = Color(0xFF242933),
    surfaceCardElevated = Color(0xFF2F3643),
    surfaceCardBorder = Color(0xFF4A5466),
    metallicIntermediate = Color(0xFF3B4352),
    metallicBorder = Color(0xFF5D687D),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFD2D9E6),
    textDisabled = Color(0xFF949EB0),
    trackRail = Color(0xFF4A5466),
    sidebarBackground = Color(0xFF181C24),
    floatingOverlaySurface = Color(0xF2262C37),
    mapBackgroundHex = "#181C24",
    mapVignetteAlpha = 0.0f
)

val LocalClusterColors = compositionLocalOf { NightClusterPalette }
