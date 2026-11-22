package com.joaohouto.clusterlauncher.ui.cockpit.dialogs.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joaohouto.clusterlauncher.R
import com.joaohouto.clusterlauncher.data.model.EqualizerPreset
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterAccent
import com.joaohouto.clusterlauncher.ui.theme.MetallicIntermediate
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCard
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCardBorder
import com.joaohouto.clusterlauncher.ui.theme.TextPrimary
import com.joaohouto.clusterlauncher.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
internal fun AudioSettingsPanel(
    isLoudnessEnabled: Boolean,
    isAutoplayOnStart: Boolean,
    isKeepScreenOn: Boolean,
    isBtAutoPauseOnDisconnect: Boolean,
    currentAppVolumePercent: Int,
    currentAudioSyncOffsetMs: Int,
    isEqEnabled: Boolean,
    currentEqPreset: EqualizerPreset,
    currentEqBandsMb: List<Int>,
    onSelectAppVolume: (Int) -> Unit,
    onToggleLoudness: (Boolean) -> Unit,
    onToggleAutoplayOnStart: (Boolean) -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onToggleBtAutoPauseOnDisconnect: (Boolean) -> Unit,
    onSelectAudioSyncOffset: (Int) -> Unit,
    onToggleEqEnabled: (Boolean) -> Unit,
    onSelectEqPreset: (EqualizerPreset) -> Unit,
    onUpdateEqBandsMb: (List<Int>) -> Unit
) {
    val accent = LocalClusterAccent.current
    val scrollState = rememberScrollState()
    var volumeSliderValue by remember(currentAppVolumePercent) {
        mutableFloatStateOf(currentAppVolumePercent.toFloat())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = stringResource(R.string.settings_audio_category),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Parâmetros de processamento de som, equalização e comportamento do tocador integrado.",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        // 1. Equalizador de 5 Bandas + Presets Automotivos
        EqualizerSettingsCard(
            isEqEnabled = isEqEnabled,
            currentEqPreset = currentEqPreset,
            currentEqBandsMb = currentEqBandsMb,
            onToggleEqEnabled = onToggleEqEnabled,
            onSelectEqPreset = onSelectEqPreset,
            onUpdateEqBandsMb = onUpdateEqBandsMb
        )

        // 2. Normalização de Volume (EBU R128)
        AudioToggleCard(
            title = stringResource(R.string.settings_loudness_title),
            subtitle = stringResource(R.string.settings_loudness_subtitle),
            checked = isLoudnessEnabled,
            onCheckedChange = onToggleLoudness
        )

        // 3. Volume Geral do App
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_volume_title),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.settings_volume_subtitle),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Text(
                        text = stringResource(R.string.settings_volume_percent, volumeSliderValue.roundToInt()),
                        color = accent.primary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Slider(
                    value = volumeSliderValue,
                    onValueChange = { volumeSliderValue = it },
                    onValueChangeFinished = { onSelectAppVolume(volumeSliderValue.roundToInt()) },
                    valueRange = 10f..100f,
                    steps = 17,
                    colors = SliderDefaults.colors(
                        thumbColor = accent.primary,
                        activeTrackColor = accent.primary,
                        inactiveTrackColor = Color(0xFF1E222B)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 4. Autoplay ao iniciar
        AudioToggleCard(
            title = stringResource(R.string.settings_autoplay_title),
            subtitle = stringResource(R.string.settings_autoplay_subtitle),
            checked = isAutoplayOnStart,
            onCheckedChange = onToggleAutoplayOnStart
        )

        // 5. Pausar ao desconectar Bluetooth
        AudioToggleCard(
            title = "Pausar ao desconectar Bluetooth",
            subtitle = "Interrompe automaticamente o tocador integrado quando o dispositivo Bluetooth é desconectado.",
            checked = isBtAutoPauseOnDisconnect,
            onCheckedChange = onToggleBtAutoPauseOnDisconnect
        )

        // 6. Manter tela ligada (Keep Screen On)
        AudioToggleCard(
            title = stringResource(R.string.settings_screen_on_title),
            subtitle = stringResource(R.string.settings_screen_on_subtitle),
            checked = isKeepScreenOn,
            onCheckedChange = onToggleKeepScreenOn
        )

        // 7. Ajuste manual de latência de áudio (-500 ms a +500 ms, passo 50 ms)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            var syncSliderValue by remember(currentAudioSyncOffsetMs) {
                mutableFloatStateOf(currentAudioSyncOffsetMs.toFloat())
            }
            val snappedMs = ((syncSliderValue / 50f).roundToInt() * 50).coerceIn(-500, 500)
            val offsetLabel = if (snappedMs > 0) "+$snappedMs ms" else "$snappedMs ms"

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_audio_sync_title),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.settings_audio_sync_subtitle),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Text(
                        text = offsetLabel,
                        color = accent.primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                syncSliderValue = 0f
                                onSelectAudioSyncOffset(0)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Slider(
                    value = syncSliderValue,
                    onValueChange = { raw ->
                        syncSliderValue = ((raw / 50f).roundToInt() * 50).coerceIn(-500, 500).toFloat()
                    },
                    onValueChangeFinished = {
                        val finalMs = ((syncSliderValue / 50f).roundToInt() * 50).coerceIn(-500, 500)
                        onSelectAudioSyncOffset(finalMs)
                    },
                    valueRange = -500f..500f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = accent.primary,
                        activeTrackColor = accent.primary,
                        inactiveTrackColor = Color(0xFF1E222B)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun EqualizerSettingsCard(
    isEqEnabled: Boolean,
    currentEqPreset: EqualizerPreset,
    currentEqBandsMb: List<Int>,
    onToggleEqEnabled: (Boolean) -> Unit,
    onSelectEqPreset: (EqualizerPreset) -> Unit,
    onUpdateEqBandsMb: (List<Int>) -> Unit
) {
    val accent = LocalClusterAccent.current
    val effectiveBands = if (currentEqPreset == EqualizerPreset.CUSTOM) {
        currentEqBandsMb
    } else {
        currentEqPreset.defaultBandsMb
    }
    var localBandsMb by remember(currentEqPreset, currentEqBandsMb) {
        mutableStateOf(List(5) { idx -> effectiveBands.getOrElse(idx) { 0 } })
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Equalizador de 5 Bandas",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ajuste fino de graves, médios e agudos com presets automotivos.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Switch(
                    checked = isEqEnabled,
                    onCheckedChange = onToggleEqEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accent.primary,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = MetallicIntermediate,
                        uncheckedBorderColor = SurfaceCardBorder
                    )
                )
            }

            if (isEqEnabled) {
                // Presets horizontais
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EqualizerPreset.entries.forEach { preset ->
                        val isSelected = currentEqPreset == preset
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) accent.primary.copy(alpha = 0.2f) else Color(0xFF14171E))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) accent.primary else SurfaceCardBorder,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    onSelectEqPreset(preset)
                                    if (preset != EqualizerPreset.CUSTOM) {
                                        localBandsMb = preset.defaultBandsMb
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = preset.label,
                                color = if (isSelected) accent.primary else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                // Sliders das 5 bandas
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EqualizerPreset.BAND_LABELS.forEachIndexed { index, bandLabel ->
                        val currentMb = localBandsMb.getOrElse(index) { 0 }
                        val dbValue = currentMb / 100f
                        val formattedDb = if (dbValue > 0f) {
                            String.format("+%.1f dB", dbValue)
                        } else {
                            String.format("%.1f dB", dbValue)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = bandLabel,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(0.16f)
                            )

                            Slider(
                                value = currentMb.toFloat(),
                                onValueChange = { raw ->
                                    val snapped = ((raw / 50f).roundToInt() * 50)
                                        .coerceIn(EqualizerPreset.DEFAULT_MIN_MB, EqualizerPreset.DEFAULT_MAX_MB)
                                    val updated = localBandsMb.toMutableList()
                                    updated[index] = snapped
                                    localBandsMb = updated
                                },
                                onValueChangeFinished = {
                                    onSelectEqPreset(EqualizerPreset.CUSTOM)
                                    onUpdateEqBandsMb(localBandsMb)
                                },
                                valueRange = EqualizerPreset.DEFAULT_MIN_MB.toFloat()..EqualizerPreset.DEFAULT_MAX_MB.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = accent.primary,
                                    activeTrackColor = accent.primary,
                                    inactiveTrackColor = Color(0xFF1E222B)
                                ),
                                modifier = Modifier.weight(0.64f)
                            )

                            Text(
                                text = formattedDb,
                                color = if (currentMb != 0) accent.primary else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(0.20f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val accent = LocalClusterAccent.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accent.primary,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = MetallicIntermediate,
                    uncheckedBorderColor = SurfaceCardBorder
                )
            )
        }
    }
}
