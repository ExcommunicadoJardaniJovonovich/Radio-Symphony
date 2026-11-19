package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RadioBand
import com.example.data.model.RadioStation
import com.example.data.repository.RadioRepository
import com.example.playback.PlaybackStateSnapshot
import com.example.ui.components.BandBadge
import com.example.ui.components.StationLogoEmblem
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalRadioColors
import java.util.Locale
import kotlin.math.round

private data class BandRangeSpec(
    val min: Double,
    val max: Double,
    val step: Double,
    val defaultValue: Double,
    val decimals: Int
) {
    fun formatValue(value: Double): String {
        return String.format(Locale.US, "%.${decimals}f", value)
    }

    fun snap(value: Double): Double {
        val clamped = value.coerceIn(min, max)
        val steps = round((clamped - min) / step)
        return (min + steps * step).coerceIn(min, max)
    }
}

private fun RadioBand.rangeSpec(): BandRangeSpec = when (this) {
    RadioBand.FM -> BandRangeSpec(min = 87.5, max = 108.0, step = 0.1, defaultValue = 94.3, decimals = 1)
    RadioBand.AM -> BandRangeSpec(min = 531.0, max = 1701.0, step = 9.0, defaultValue = 630.0, decimals = 0)
    RadioBand.SW -> BandRangeSpec(min = 3.000, max = 26.000, step = 0.005, defaultValue = 9.425, decimals = 3)
}

@Composable
fun ManualTunerScreen(
    allStations: List<RadioStation>,
    customStations: List<RadioStation>,
    favouriteIds: Set<String>,
    playbackState: PlaybackStateSnapshot,
    onBack: () -> Unit,
    onPlayStation: (RadioStation) -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleFavourite: (RadioStation) -> Unit,
    onSaveCustomStation: (
        name: String,
        frequency: String,
        band: RadioBand,
        district: String,
        language: String,
        streamUrl: String,
        alsoFavourite: Boolean
    ) -> Unit,
    onDeleteCustomStation: (RadioStation) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val radioColors = LocalRadioColors.current

    var selectedBand by remember { mutableStateOf(playbackState.currentStation?.band ?: RadioBand.FM) }
    val spec = remember(selectedBand) { selectedBand.rangeSpec() }

    var tunedFrequency by remember(selectedBand) {
        val initialFromCurrent = playbackState.currentStation
            ?.takeIf { it.band == selectedBand }
            ?.let { RadioRepository.parseNumericFrequency(it) }
        mutableDoubleStateOf(spec.snap(initialFromCurrent ?: spec.defaultValue))
    }

    var showDirectFreqDialog by remember { mutableStateOf(false) }

    // Custom Station Form State
    var customName by remember { mutableStateOf("") }
    var customStreamUrl by remember { mutableStateOf("") }
    var customDistrict by remember { mutableStateOf("Kochi") }
    var customLanguage by remember { mutableStateOf("Malayalam") }
    var customAlsoFavourite by remember { mutableStateOf(true) }
    var formError by remember { mutableStateOf<String?>(null) }

    val formattedFreq = "${spec.formatValue(tunedFrequency)} ${selectedBand.unit}"

    val bandStationFrequencies = remember(allStations, selectedBand) {
        allStations
            .filter { it.band == selectedBand }
            .mapNotNull { RadioRepository.parseNumericFrequency(it) }
            .distinct()
            .sorted()
    }

    val matchResult = remember(allStations, selectedBand, tunedFrequency) {
        RadioRepository.findClosestStationForFrequency(allStations, selectedBand, tunedFrequency)
    }

    val bandAccent = when (selectedBand) {
        RadioBand.FM -> radioColors.fmBadge
        RadioBand.AM -> radioColors.amBadge
        RadioBand.SW -> radioColors.swBadge
    }

    if (showDirectFreqDialog) {
        var freqInput by remember { mutableStateOf(spec.formatValue(tunedFrequency)) }
        var freqDialogError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showDirectFreqDialog = false },
            title = { Text("Enter ${selectedBand.label} Frequency") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Range: ${spec.formatValue(spec.min)} – ${spec.formatValue(spec.max)} ${selectedBand.unit}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = freqInput,
                        onValueChange = {
                            freqInput = it
                            freqDialogError = null
                        },
                        singleLine = true,
                        label = { Text("Frequency (${selectedBand.unit})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = freqDialogError != null,
                        modifier = Modifier.testTag("direct_frequency_input")
                    )
                    if (freqDialogError != null) {
                        Text(
                            text = freqDialogError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = freqInput.trim().toDoubleOrNull()
                        if (parsed == null || parsed < spec.min || parsed > spec.max) {
                            freqDialogError = "Enter a number between ${spec.formatValue(spec.min)} and ${spec.formatValue(spec.max)}"
                        } else {
                            tunedFrequency = spec.snap(parsed)
                            showDirectFreqDialog = false
                        }
                    },
                    modifier = Modifier.testTag("direct_frequency_confirm")
                ) {
                    Text("Tune")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDirectFreqDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("manual_tuner_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("manual_tuner_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Manual Station Tuner",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Dial any AM / FM / SW frequency or add a custom stream URL",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Band Selector Chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RadioBand.entries.forEach { band ->
                    FilterChip(
                        selected = selectedBand == band,
                        onClick = { selectedBand = band },
                        label = { Text("${band.label} (${band.unit})") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tuner_band_${band.name.lowercase()}")
                    )
                }
            }
        }

        // Analog / Digital Radio Tuner Console Card
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.25.dp, bandAccent.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tuner_console_card")
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Signal Strength + Status Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BandBadge(band = selectedBand)
                            Text(
                                text = when {
                                    matchResult.isLocked -> "SIGNAL LOCKED"
                                    matchResult.signalStrength >= 2 -> "NEAR STATION"
                                    else -> "OPEN FREQUENCY"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (matchResult.isLocked) radioColors.successGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 5-bar Signal Meter
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            for (bar in 1..5) {
                                val lit = bar <= matchResult.signalStrength
                                Box(
                                    modifier = Modifier
                                        .width(5.dp)
                                        .height((6 + bar * 3).dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(
                                            when {
                                                lit && matchResult.isLocked -> radioColors.successGreen
                                                lit -> bandAccent
                                                else -> MaterialTheme.colorScheme.outlineVariant
                                            }
                                        )
                                )
                            }
                        }
                    }

                    // Large Digital Monospace Frequency Readout (Tap to Type)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDirectFreqDialog = true }
                            .testTag("tuner_frequency_readout")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp, horizontal = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = spec.formatValue(tunedFrequency),
                                    style = MaterialTheme.typography.displaySmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 44.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = bandAccent
                                )
                                Text(
                                    text = selectedBand.unit,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = "Enter exact frequency",
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Tap frequency to type an exact number",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Visual Frequency Scale Canvas with live station markers
                    val outlineColor = MaterialTheme.colorScheme.outlineVariant
                    val markerColor = radioColors.successGreen
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        val totalTicks = 44
                        val stepX = size.width / totalTicks
                        val midY = size.height * 0.52f

                        for (i in 0..totalTicks) {
                            val x = i * stepX
                            val major = i % 4 == 0
                            val h = if (major) 13.dp.toPx() else 6.dp.toPx()
                            drawLine(
                                color = if (major) outlineColor else outlineColor.copy(alpha = 0.5f),
                                start = Offset(x, midY - h),
                                end = Offset(x, midY + h * 0.55f),
                                strokeWidth = if (major) 2.5f else 1.5f,
                                cap = StrokeCap.Round
                            )
                        }

                        // Draw dots where known stations broadcast on this band
                        bandStationFrequencies.forEach { freq ->
                            val frac = ((freq - spec.min) / (spec.max - spec.min)).toFloat().coerceIn(0f, 1f)
                            drawCircle(
                                color = markerColor.copy(alpha = 0.85f),
                                radius = 3.5.dp.toPx(),
                                center = Offset(size.width * frac, size.height - 6.dp.toPx())
                            )
                        }

                        // Active tuning needle
                        val needleFrac = ((tunedFrequency - spec.min) / (spec.max - spec.min)).toFloat().coerceIn(0f, 1f)
                        val needleX = size.width * needleFrac
                        drawLine(
                            color = bandAccent,
                            start = Offset(needleX, 2.dp.toPx()),
                            end = Offset(needleX, size.height - 2.dp.toPx()),
                            strokeWidth = 3.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // Draggable Frequency Slider
                    Slider(
                        value = tunedFrequency.toFloat(),
                        onValueChange = { raw ->
                            tunedFrequency = spec.snap(raw.toDouble())
                        },
                        valueRange = spec.min.toFloat()..spec.max.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = bandAccent,
                            activeTrackColor = bandAccent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tuner_frequency_slider")
                    )

                    // Tactile Fine-Tune & Seek Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val found = RadioRepository.seekAdjacentFrequency(
                                    stations = allStations,
                                    band = selectedBand,
                                    currentFrequency = tunedFrequency,
                                    forward = false
                                )
                                if (found != null) {
                                    tunedFrequency = spec.snap(found.first)
                                    onPlayStation(found.second)
                                }
                            },
                            modifier = Modifier.testTag("tuner_seek_prev")
                        ) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "Seek previous station", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Seek")
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalIconButton(
                                onClick = { tunedFrequency = spec.snap(tunedFrequency - spec.step) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("tuner_step_down")
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = "Tune frequency down")
                            }

                            FilledTonalIconButton(
                                onClick = { tunedFrequency = spec.snap(tunedFrequency + spec.step) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("tuner_step_up")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Tune frequency up")
                            }
                        }

                        FilledTonalButton(
                            onClick = {
                                val found = RadioRepository.seekAdjacentFrequency(
                                    stations = allStations,
                                    band = selectedBand,
                                    currentFrequency = tunedFrequency,
                                    forward = true
                                )
                                if (found != null) {
                                    tunedFrequency = spec.snap(found.first)
                                    onPlayStation(found.second)
                                }
                            },
                            modifier = Modifier.testTag("tuner_seek_next")
                        ) {
                            Text("Seek")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Filled.SkipNext, contentDescription = "Seek next station", modifier = Modifier.size(18.dp))
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Matched Station or Open Frequency Action
                    val matchedStation = matchResult.station
                    if (matchedStation != null) {
                        val isCurrent = playbackState.currentStation?.id == matchedStation.id
                        val isFav = matchedStation.id in favouriteIds

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tuner_matched_station_card"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StationLogoEmblem(
                                station = matchedStation,
                                size = 50.dp,
                                isPlaying = isCurrent && playbackState.isPlaying
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = matchedStation.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${matchedStation.formattedFrequency} • ${matchedStation.district} • ${matchedStation.language}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = { onToggleFavourite(matchedStation) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Toggle favourite",
                                    tint = if (isFav) radioColors.heartActive else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = {
                                    if (isCurrent) onTogglePlayPause() else onPlayStation(matchedStation)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("tuner_play_matched_button")
                            ) {
                                Icon(
                                    imageVector = if (isCurrent && playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isCurrent && playbackState.isPlaying) "Pause" else "Play")
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "No online broadcast mapped at $formattedFreq yet. Use Seek to snap to the next active frequency, or add a custom stream below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Manual Custom Stream URL Entry Card
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_station_form_card")
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Add Custom Radio Station",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Save any direct MP3, AAC, Icecast, or HLS (.m3u8) stream at $formattedFreq",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = customName,
                        onValueChange = {
                            customName = it
                            formError = null
                        },
                        label = { Text("Station Name") },
                        placeholder = { Text("e.g. My Malayalam FM 94.3") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_station_name_input")
                    )

                    OutlinedTextField(
                        value = customStreamUrl,
                        onValueChange = {
                            customStreamUrl = it
                            formError = null
                        },
                        label = { Text("Stream URL (http:// or https://)") },
                        placeholder = { Text("https://example.com/live/stream.mp3") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_station_url_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = customDistrict,
                            onValueChange = { customDistrict = it },
                            label = { Text("District") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = customLanguage,
                            onValueChange = { customLanguage = it },
                            label = { Text("Language") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { customAlsoFavourite = !customAlsoFavourite }
                    ) {
                        Checkbox(
                            checked = customAlsoFavourite,
                            onCheckedChange = { customAlsoFavourite = it }
                        )
                        Text(
                            text = "Also add to Favourites",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (formError != null) {
                        Text(
                            text = formError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Button(
                        onClick = {
                            val trimmedName = customName.trim()
                            val trimmedUrl = customStreamUrl.trim()
                            when {
                                trimmedName.isEmpty() -> formError = "Please enter a station name."
                                !trimmedUrl.startsWith("http://", ignoreCase = true) &&
                                    !trimmedUrl.startsWith("https://", ignoreCase = true) ->
                                    formError = "Stream URL must start with http:// or https://"
                                else -> {
                                    onSaveCustomStation(
                                        trimmedName,
                                        "${spec.formatValue(tunedFrequency)} ${selectedBand.unit}",
                                        selectedBand,
                                        customDistrict,
                                        customLanguage,
                                        trimmedUrl,
                                        customAlsoFavourite
                                    )
                                    customName = ""
                                    customStreamUrl = ""
                                    formError = null
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_custom_station_button")
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save & Play at $formattedFreq")
                    }
                }
            }
        }

        // User's Saved Custom Stations List
        if (customStations.isNotEmpty()) {
            item {
                Text(
                    text = "Your Custom Tuned Stations (${customStations.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            items(customStations, key = { it.id }) { station ->
                val isCurrent = playbackState.currentStation?.id == station.id
                val isFav = station.id in favouriteIds
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(
                        width = if (isCurrent) 1.25.dp else 0.75.dp,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isCurrent) onTogglePlayPause() else onPlayStation(station)
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StationLogoEmblem(
                            station = station,
                            size = 46.dp,
                            isPlaying = isCurrent && playbackState.isPlaying
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = station.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${station.formattedFrequency} • ${station.district} • ${station.language}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { onToggleFavourite(station) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Toggle favourite",
                                tint = if (isFav) radioColors.heartActive else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onDeleteCustomStation(station) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("delete_custom_station_${station.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Delete custom station",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
