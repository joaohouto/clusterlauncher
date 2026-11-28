package com.joaohouto.clusterlauncher.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterAccent
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterColors
import com.joaohouto.clusterlauncher.ui.theme.MetallicIntermediate
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCard
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCardBorder
import com.joaohouto.clusterlauncher.ui.theme.TextPrimary

enum class MetallicButtonStyle {
    Standard,
    Accent,
    Outlined
}

private val ButtonShape = RoundedCornerShape(18.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MetallicButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    style: MetallicButtonStyle = MetallicButtonStyle.Standard,
    icon: ImageVector? = null,
    text: String? = null,
    isActive: Boolean = false,
    isLoading: Boolean = false,
    minSize: Dp = 56.dp,
    iconSize: Dp = 26.dp,
    textSize: TextUnit = 14.sp,
    verticalLayout: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    contentDescription: String? = null
) {
    val accent = LocalClusterAccent.current
    val colors = LocalClusterColors.current

    val backgroundBrush = remember(style, isActive, accent, colors) {
        when (style) {
            MetallicButtonStyle.Accent -> Brush.verticalGradient(
                colors = listOf(accent.primary, accent.dark)
            )
            MetallicButtonStyle.Standard -> if (isActive) {
                Brush.verticalGradient(listOf(colors.metallicIntermediate, colors.surfaceCard))
            } else {
                Brush.verticalGradient(listOf(colors.surfaceCardElevated, colors.backgroundSecondary))
            }
            MetallicButtonStyle.Outlined -> Brush.verticalGradient(
                colors = listOf(colors.surfaceCard, colors.surfaceCard)
            )
        }
    }

    val borderStroke = remember(style, isActive, accent, colors) {
        when {
            isActive -> BorderStroke(1.5.dp, accent.primary)
            style == MetallicButtonStyle.Accent -> BorderStroke(1.dp, accent.primary.copy(alpha = 0.8f))
            else -> BorderStroke(1.dp, colors.surfaceCardBorder)
        }
    }

    val contentColor = when {
        isActive -> accent.primary
        style == MetallicButtonStyle.Accent -> accent.onPrimary
        else -> colors.textPrimary
    }

    Surface(
        modifier = modifier
            .defaultMinSize(minWidth = minSize, minHeight = minSize)
            .clip(ButtonShape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(color = if (style == MetallicButtonStyle.Accent) accent.onPrimary else accent.primary),
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = ButtonShape,
        border = borderStroke,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .background(backgroundBrush)
                .defaultMinSize(minWidth = minSize, minHeight = minSize)
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            if (verticalLayout) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val hasLeadingVisual = isLoading || icon != null
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = if (style == MetallicButtonStyle.Accent) accent.onPrimary else accent.primary,
                            modifier = Modifier.size((iconSize - 4.dp).coerceAtLeast(16.dp)),
                            strokeWidth = 2.5.dp
                        )
                    } else if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = contentDescription ?: text,
                            tint = contentColor,
                            modifier = Modifier.size(iconSize)
                        )
                    }
                    if (hasLeadingVisual && !text.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                    if (!text.isNullOrEmpty()) {
                        Text(
                            text = text,
                            color = contentColor,
                            fontSize = textSize,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val hasLeadingVisual = isLoading || icon != null
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = if (style == MetallicButtonStyle.Accent) accent.onPrimary else accent.primary,
                            modifier = Modifier.size((iconSize - 4.dp).coerceAtLeast(16.dp)),
                            strokeWidth = 2.5.dp
                        )
                    } else if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = contentDescription ?: text,
                            tint = contentColor,
                            modifier = Modifier.size(iconSize)
                        )
                    }
                    if (hasLeadingVisual && !text.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    if (!text.isNullOrEmpty()) {
                        Text(
                            text = text,
                            color = contentColor,
                            fontSize = textSize,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                    }
                }
            }
        }
    }
}
