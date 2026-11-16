package com.joaohouto.clusterlauncher.ui.components

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joaohouto.clusterlauncher.BuildConfig
import com.joaohouto.clusterlauncher.player.PlayerController
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterAccent
import com.joaohouto.clusterlauncher.ui.theme.TextSecondary
import com.joaohouto.clusterlauncher.ui.theme.TrackRail
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun ProgressBarSlider(
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean = false,
    trackKey: String? = null,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val accentColor = LocalClusterAccent.current.primary

    val validDuration = if (durationMs > 0) durationMs else 1L

    val context = LocalContext.current
    val playerController = remember(context) { PlayerController.getInstance(context) }

    var anchorPositionMs by remember(trackKey) { mutableLongStateOf(currentPositionMs) }
    var anchorRealtimeMs by remember(trackKey) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var livePositionMs by remember(trackKey) { mutableLongStateOf(currentPositionMs) }

    LaunchedEffect(trackKey) {
        anchorPositionMs = currentPositionMs
        anchorRealtimeMs = SystemClock.elapsedRealtime()
        livePositionMs = currentPositionMs
    }

    LaunchedEffect(currentPositionMs) {
        anchorPositionMs = currentPositionMs
        anchorRealtimeMs = SystemClock.elapsedRealtime()
        if (!isDragging) {
            if (currentPositionMs == 0L || kotlin.math.abs(currentPositionMs - livePositionMs) > 45L || !isPlaying) {
                livePositionMs = currentPositionMs
            }
        }
    }

    LaunchedEffect(isPlaying, validDuration, trackKey) {
        if (isPlaying) {
            anchorPositionMs = currentPositionMs
            anchorRealtimeMs = SystemClock.elapsedRealtime()
            livePositionMs = currentPositionMs
            while (isActive) {
                if (!isDragging) {
                    val elapsed = SystemClock.elapsedRealtime() - anchorRealtimeMs
                    livePositionMs = (anchorPositionMs + elapsed).coerceIn(0L, validDuration)
                }
                delay(100L)
            }
        } else {
            anchorPositionMs = currentPositionMs
            anchorRealtimeMs = SystemClock.elapsedRealtime()
            livePositionMs = currentPositionMs
        }
    }

    val currentFraction = if (isDragging) {
        dragFraction
    } else {
        (livePositionMs.toFloat() / validDuration.toFloat()).coerceIn(0f, 1f)
    }

    val displayedElapsedMs = if (isDragging) {
        (dragFraction * validDuration).toLong()
    } else {
        livePositionMs.coerceIn(0L, validDuration)
    }

    val remainingMs = (validDuration - displayedElapsedMs).coerceAtLeast(0L)
    val elapsedSec = displayedElapsedMs / 1000L
    val remainingSec = remainingMs / 1000L
    val formattedElapsed = remember(elapsedSec) { formatTimeSeconds(elapsedSec) }
    val formattedRemaining = remember(remainingSec) { "-${formatTimeSeconds(remainingSec)}" }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .pointerInput(validDuration) {
                    awaitEachGesture {
                        try {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val thumbRadius = 8.dp.toPx()
                            val usableWidth = (size.width - 2 * thumbRadius).coerceAtLeast(1f)
                            val initialFraction = ((down.position.x - thumbRadius) / usableWidth).coerceIn(0f, 1f)

                            isDragging = true
                            dragFraction = initialFraction

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.changedToUp()) {
                                    change.consume()
                                    break
                                }
                                if (change.positionChange() != Offset.Zero) {
                                    change.consume()
                                    val newFraction = ((change.position.x - thumbRadius) / usableWidth).coerceIn(0f, 1f)
                                    dragFraction = newFraction
                                }
                            }

                            val targetMs = (dragFraction * validDuration).toLong()
                            anchorPositionMs = targetMs
                            anchorRealtimeMs = SystemClock.elapsedRealtime()
                            livePositionMs = targetMs
                            onSeek(targetMs)
                        } finally {
                            isDragging = false
                        }
                    }
                }
        ) {
            val railHeight = 6.dp.toPx()
            val centerY = size.height / 2f
            val topY = centerY - (railHeight / 2f)
            val cornerRadius = CornerRadius(railHeight / 2f, railHeight / 2f)
            val thumbRadius = if (isDragging) 8.dp.toPx() else 6.dp.toPx()
            val usableWidth = (size.width - 2 * thumbRadius).coerceAtLeast(0f)

            drawRoundRect(
                color = TrackRail,
                topLeft = Offset(0f, topY),
                size = Size(size.width, railHeight),
                cornerRadius = cornerRadius
            )

            val thumbCenterX = thumbRadius + (usableWidth * currentFraction)

            if (thumbCenterX > 0f) {
                drawRoundRect(
                    color = accentColor,
                    topLeft = Offset(0f, topY),
                    size = Size(thumbCenterX, railHeight),
                    cornerRadius = cornerRadius
                )
            }

            drawCircle(
                color = Color.White,
                radius = thumbRadius,
                center = Offset(thumbCenterX, centerY)
            )
            drawCircle(
                color = accentColor,
                radius = thumbRadius - 2.dp.toPx(),
                center = Offset(thumbCenterX, centerY)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formattedElapsed,
                color = TextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = formattedRemaining,
                color = TextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun formatTimeSeconds(totalSecs: Long): String {
    val totalSeconds = totalSecs.coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val minStr = if (minutes < 10) "0$minutes" else minutes.toString()
    val secStr = if (seconds < 10) "0$seconds" else seconds.toString()
    return "$minStr:$secStr"
}
