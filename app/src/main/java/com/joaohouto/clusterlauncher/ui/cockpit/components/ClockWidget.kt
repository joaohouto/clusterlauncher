package com.joaohouto.clusterlauncher.ui.cockpit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joaohouto.clusterlauncher.ui.theme.TextPrimary
import com.joaohouto.clusterlauncher.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ClockWidget(
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    var currentTime by remember { mutableStateOf(Date()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = remember(context) {
        var cur: android.content.Context? = context
        while (cur is android.content.ContextWrapper) {
            if (cur is androidx.lifecycle.LifecycleOwner) break
            cur = cur.baseContext
        }
        cur as? androidx.lifecycle.LifecycleOwner
    }

    // Instantly sync time on resume or when screen turns on / time changes
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, context) {
        currentTime = Date()

        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: android.content.Intent?) {
                currentTime = Date()
            }
        }

        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_TIME_TICK)
            addAction(android.content.Intent.ACTION_TIME_CHANGED)
            addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED)
            addAction(android.content.Intent.ACTION_SCREEN_ON)
            addAction(android.content.Intent.ACTION_USER_PRESENT)
        }
        try {
            context.registerReceiver(receiver, filter)
        } catch (_: Exception) {
            try {
                androidx.core.content.ContextCompat.registerReceiver(
                    context,
                    receiver,
                    filter,
                    androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
                )
            } catch (_: Exception) {}
        }

        val observer = lifecycleOwner?.let { owner ->
            val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME ||
                    event == androidx.lifecycle.Lifecycle.Event.ON_START
                ) {
                    currentTime = Date()
                }
            }
            owner.lifecycle.addObserver(obs)
            obs
        }

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
            if (observer != null) {
                lifecycleOwner?.lifecycle?.removeObserver(observer)
            }
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            currentTime = Date()
            val millis = System.currentTimeMillis()
            val delayToNextMinute = 60000L - (millis % 60000L) + 50L
            delay(delayToNextMinute.coerceAtLeast(500L))
        }
    }

    val formattedTime = remember(currentTime) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(currentTime)
    }
    val formattedDate = remember(currentTime) {
        val raw = SimpleDateFormat("EEEE, d 'de' MMMM", Locale.getDefault()).format(currentTime)
        raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = formattedTime,
            color = TextPrimary,
            fontSize = if (isCompact) 84.sp else 96.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = if (isCompact) 1.3.sp else 1.5.sp
        )
        Text(
            text = formattedDate,
            color = TextSecondary,
            fontSize = if (isCompact) 21.sp else 24.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 20.dp))

        CockpitHorizonDivider(
            width = if (isCompact) 340.dp else 420.dp,
            height = if (isCompact) 28.dp else 36.dp
        )
    }
}
