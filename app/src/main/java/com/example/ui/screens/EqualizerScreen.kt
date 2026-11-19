package com.example.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.example.data.model.EqualizerCatalog
import com.example.data.model.EqualizerPreset
import com.example.data.model.EqualizerSettings
import com.example.playback.PlaybackStateSnapshot
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalRadioColors
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun EqualizerScreen(
    settings: EqualizerSettings,
    playbackState: PlaybackStateSnapshot,
    onBack: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectPreset: (EqualizerPreset) -> Unit,
    onBandGainChange: (bandIndex: Int, gainDb: Float) -> Unit,
    onPreampChange: (Float) -> Unit,
    onBassBoostChange: (Int) -> Unit,
    onVirtualizerChange: (Int) -> Unit,
    onLoudnessChange: (Int) -> Unit,
    onLimiterToggle: (Boolean) -> Unit,
    onResetFlat: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val radioColors = LocalRadioColors.current
    val dspLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        "10-Band Studio DSP • 32-Bit Floating Limiter"
    } else {
        "Hardware AudioFX Equalizer"
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("equalizer_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header with Back, Master Toggle & Reset
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("equalizer_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Studio Equalizer",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = dspLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = if (settings.enabled) radioColors.successGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onResetFlat,
                    modifier = Modifier.testTag("equalizer_reset_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Flat")
                }
            }
        }

        // Master Enable & Active Station Status Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    width = if (settings.enabled) 1.25.dp else 0.75.dp,
                    color = if (settings.enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                    else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (settings.enabled) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.GraphicEq,
                                    contentDescription = null,
                                    tint = if (settings.enabled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Column {
                            Text(
                                text = if (settings.enabled) {
                                    "EQ Active • ${EqualizerCatalog.presetDisplayName(settings.presetId)}"
                                } else {
                                    "Equalizer Bypassed"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = playbackState.currentStation?.let {
                                    "Tuning live audio for ${it.name}"
                                } ?: "Start any station to hear changes in real time",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = settings.enabled,
                        onCheckedChange = onToggleEnabled,
                        modifier = Modifier.testTag("equalizer_master_switch")
                    )
                }
            }
        }

        // Live Frequency Response Curve Visualizer
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FREQUENCY RESPONSE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "31 Hz – 16 kHz (±12 dB)",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    FrequencyCurveCanvas(
                        bandGainsDb = settings.bandGainsDb,
                        enabled = settings.enabled,
                        primaryColor = MaterialTheme.colorScheme.primary,
                        accentColor = radioColors.successGreen,
                        gridColor = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(126.dp)
                            .testTag("equalizer_curve_canvas")
                    )

                    // Active Preset Description
                    val currentPresetObj = EqualizerCatalog.presets.firstOrNull { it.id == settings.presetId }
                    Text(
                        text = currentPresetObj?.description
                            ?: "Custom 10-band studio response curve saved automatically",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Audiophile Presets Strip
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Studio & Radio Presets",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(EqualizerCatalog.presets, key = { it.id }) { preset ->
                        val isSelected = settings.presetId == preset.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectPreset(preset) },
                            label = { Text(preset.name) },
                            modifier = Modifier.testTag("eq_preset_${preset.id}")
                        )
                    }
                }
            }
        }

        // 10-Band Vertical Studio Fader Console
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "10-Band Graphic Equalizer",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Swipe bands →",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(EqualizerCatalog.bands, key = { it.index }) { band ->
                            val gain = settings.bandGainsDb.getOrElse(band.index) { 0f }
                            VerticalBandFaderColumn(
                                label = band.shortLabel,
                                gainDb = gain,
                                enabled = settings.enabled,
                                onGainChange = { newGain ->
                                    val snapped = (newGain * 2f).roundToInt() / 2f
                                    onBandGainChange(band.index, snapped)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Studio Audio Enhancement Rack (Preamp, Bass Boost, 3D Virtualizer, Loudness, Limiter)
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Hi-Fi Audio Enhancement",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    EffectSliderRow(
                        icon = Icons.Filled.Tune,
                        title = "Preamp Input Gain",
                        valueLabel = formatSignedDb(settings.preampDb),
                        value = settings.preampDb,
                        valueRange = EqualizerSettings.MIN_PREAMP_DB..EqualizerSettings.MAX_PREAMP_DB,
                        enabled = settings.enabled,
                        testTag = "eq_preamp_slider",
                        onValueChange = { onPreampChange((it * 2f).roundToInt() / 2f) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    EffectSliderRow(
                        icon = Icons.Filled.Speaker,
                        title = "Deep Bass Boost",
                        valueLabel = "${settings.bassBoostStrength / 10}%",
                        value = settings.bassBoostStrength.toFloat(),
                        valueRange = 0f..EqualizerSettings.MAX_EFFECT_STRENGTH.toFloat(),
                        enabled = settings.enabled,
                        testTag = "eq_bass_boost_slider",
                        onValueChange = { onBassBoostChange(it.roundToInt()) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    EffectSliderRow(
                        icon = Icons.Filled.SurroundSound,
                        title = "3D Surround Soundstage",
                        valueLabel = "${settings.virtualizerStrength / 10}%",
                        value = settings.virtualizerStrength.toFloat(),
                        valueRange = 0f..EqualizerSettings.MAX_EFFECT_STRENGTH.toFloat(),
                        enabled = settings.enabled,
                        testTag = "eq_virtualizer_slider",
                        onValueChange = { onVirtualizerChange(it.roundToInt()) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    EffectSliderRow(
                        icon = Icons.Filled.VolumeUp,
                        title = "Loudness Enhancer (Quiet AM/SW)",
                        valueLabel = "+${String.format(Locale.US, "%.1f", settings.loudnessGainMb / 100f)} dB",
                        value = settings.loudnessGainMb.toFloat(),
                        valueRange = 0f..EqualizerSettings.MAX_LOUDNESS_MB.toFloat(),
                        enabled = settings.enabled,
                        testTag = "eq_loudness_slider",
                        onValueChange = { onLoudnessChange(it.roundToInt()) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = null,
                                tint = radioColors.successGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Anti-Clipping Studio Limiter",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Automatically manages headroom to prevent distortion at high bass or treble boost",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = settings.limiterEnabled,
                            onCheckedChange = onLimiterToggle,
                            modifier = Modifier.testTag("eq_limiter_switch")
                        )
                    }
                }
            }
        }

        // Listening Tip
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Headphones,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Your EQ profile stays active during background playback, screen-off listening, Bluetooth headphones, and car audio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun VerticalBandFaderColumn(
    label: String,
    gainDb: Float,
    enabled: Boolean,
    onGainChange: (Float) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .width(54.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(vertical = 8.dp, horizontal = 2.dp)
    ) {
        Text(
            text = formatShortSignedDb(gainDb),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                gainDb > 0.1f -> MaterialTheme.colorScheme.primary
                gainDb < -0.1f -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1
        )

        Slider(
            value = gainDb,
            onValueChange = onGainChange,
            valueRange = EqualizerSettings.MIN_GAIN_DB..EqualizerSettings.MAX_GAIN_DB,
            colors = SliderDefaults.colors(
                thumbColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                activeTrackColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            modifier = Modifier
                .graphicsLayer {
                    rotationZ = 270f
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        Constraints(
                            minWidth = constraints.minHeight,
                            maxWidth = constraints.maxHeight,
                            minHeight = constraints.minWidth,
                            maxHeight = constraints.maxWidth
                        )
                    )
                    layout(placeable.height, placeable.width) {
                        placeable.place(0, placeable.width)
                    }
                }
                .width(168.dp)
                .height(48.dp)
                .testTag("eq_band_slider_$label")
        )

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
private fun FrequencyCurveCanvas(
    bandGainsDb: List<Float>,
    enabled: Boolean,
    primaryColor: Color,
    accentColor: Color,
    gridColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padX = 12.dp.toPx()
        val padY = 12.dp.toPx()
        val usableW = (w - padX * 2).coerceAtLeast(1f)
        val usableH = (h - padY * 2).coerceAtLeast(1f)
        val midY = padY + usableH / 2f

        // Horizontal reference lines (+12 dB, +6 dB, 0 dB, -6 dB, -12 dB)
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEachIndexed { idx, frac ->
            val y = padY + usableH * frac
            drawLine(
                color = if (idx == 2) gridColor.copy(alpha = 0.85f) else gridColor.copy(alpha = 0.35f),
                start = Offset(padX, y),
                end = Offset(w - padX, y),
                strokeWidth = if (idx == 2) 1.5.dp.toPx() else 0.8.dp.toPx()
            )
        }

        val count = EqualizerCatalog.BAND_COUNT
        val points = List(count) { i ->
            val gain = if (enabled) bandGainsDb.getOrElse(i) { 0f } else 0f
            val normalized = ((gain - EqualizerSettings.MIN_GAIN_DB) /
                (EqualizerSettings.MAX_GAIN_DB - EqualizerSettings.MIN_GAIN_DB)).coerceIn(0f, 1f)
            val x = padX + usableW * (i.toFloat() / (count - 1).toFloat())
            val y = padY + usableH * (1f - normalized)
            Offset(x, y)
        }

        val strokePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 0 until points.lastIndex) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val midX = (p0.x + p1.x) / 2f
                cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
            }
        }

        val fillPath = Path().apply {
            addPath(strokePath)
            lineTo(points.last().x, midY)
            lineTo(points.first().x, midY)
            close()
        }

        val lineColor = if (enabled) primaryColor else gridColor
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    lineColor.copy(alpha = if (enabled) 0.34f else 0.10f),
                    accentColor.copy(alpha = if (enabled) 0.08f else 0.02f),
                    Color.Transparent
                )
            )
        )

        drawPath(
            path = strokePath,
            brush = Brush.horizontalGradient(
                colors = if (enabled) listOf(primaryColor, accentColor) else listOf(gridColor, gridColor)
            ),
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        points.forEach { pt ->
            drawCircle(
                color = if (enabled) primaryColor else gridColor,
                radius = 4.dp.toPx(),
                center = pt
            )
        }
    }
}

@Composable
private fun EffectSliderRow(
    icon: ImageVector,
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    testTag: String,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )
    }
}

private fun formatSignedDb(db: Float): String {
    val prefix = if (db > 0f) "+" else ""
    return "$prefix${String.format(Locale.US, "%.1f", db)} dB"
}

private fun formatShortSignedDb(db: Float): String {
    val prefix = if (db > 0.05f) "+" else ""
    return if (db % 1f == 0f) {
        "$prefix${db.toInt()}"
    } else {
        "$prefix${String.format(Locale.US, "%.1f", db)}"
    }
}
