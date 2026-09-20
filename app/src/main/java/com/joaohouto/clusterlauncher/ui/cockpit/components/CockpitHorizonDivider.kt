package com.joaohouto.clusterlauncher.ui.cockpit.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun CockpitHorizonDivider(
    modifier: Modifier = Modifier,
    width: Dp = 300.dp,
    height: Dp = 48.dp
) {
    Canvas(
        modifier = modifier
            .width(width)
            .height(height)
    ) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f
        val center = Offset(w / 2f, centerY)

        // Elliptical atmosphere glow dimensions:
        // Horizontal radius = w * 0.44f (fades to 0 well before left/right edges)
        // Vertical radius = w * 0.44f * 0.16f (fades to 0 well before top/bottom edges)
        val glowRadius = w * 0.44f
        val verticalScale = 0.16f

        // 1. Upper Sky Atmospheric Glow (softly fades to 0 in all directions, both vertically and horizontally)
        clipRect(left = 0f, top = 0f, right = w, bottom = centerY) {
            scale(scaleX = 1f, scaleY = verticalScale, pivot = center) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x2E8290AC),
                            Color(0x1478869E),
                            Color.Transparent
                        ),
                        center = center,
                        radius = glowRadius
                    ),
                    radius = glowRadius,
                    center = center
                )
            }
        }

        // 2. Lower Water/Ground Depth Glow (softly fades to 0 in all directions, both vertically and horizontally)
        clipRect(left = 0f, top = centerY, right = w, bottom = h) {
            scale(scaleX = 1f, scaleY = verticalScale, pivot = center) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x3E000000),
                            Color(0x18000000),
                            Color.Transparent
                        ),
                        center = center,
                        radius = glowRadius
                    ),
                    radius = glowRadius,
                    center = center
                )
            }
        }

        // 3. Precision Metallic Horizon Line (zero opacity well before horizontal edges)
        val lineBrush = Brush.horizontalGradient(
            0.00f to Color.Transparent,
            0.14f to Color(0x00A0A8BA),
            0.28f to Color(0x35A0A8BA),
            0.42f to Color(0xB5CDD4E2),
            0.50f to Color(0xFFFFFFFF),
            0.58f to Color(0xB5CDD4E2),
            0.72f to Color(0x35A0A8BA),
            0.86f to Color(0x00A0A8BA),
            1.00f to Color.Transparent,
            startX = 0f,
            endX = w
        )

        drawLine(
            brush = lineBrush,
            start = Offset(0f, centerY),
            end = Offset(w, centerY),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}
