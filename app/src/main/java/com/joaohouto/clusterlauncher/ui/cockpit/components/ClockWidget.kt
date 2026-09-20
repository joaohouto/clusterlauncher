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
    modifier: Modifier = Modifier
) {
    var currentTime by remember { mutableStateOf(Date()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            currentTime = Date()
            val millis = System.currentTimeMillis()
            val delayToNextMinute = 60000L - (millis % 60000L)
            delay(delayToNextMinute.coerceAtLeast(1000L))
        }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE, d 'de' MMMM", Locale.getDefault()) }

    val formattedTime = remember(currentTime) { timeFormat.format(currentTime) }
    val formattedDate = remember(currentTime) {
        val raw = dateFormat.format(currentTime)
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
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.5.sp
        )
        Text(
            text = formattedDate,
            color = TextSecondary,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(32.dp))

        CockpitHorizonDivider(
            width = 340.dp
        )
    }
}
