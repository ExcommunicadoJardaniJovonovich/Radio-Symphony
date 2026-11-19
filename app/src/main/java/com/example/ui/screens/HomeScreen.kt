package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.EqualizerCatalog
import com.example.data.model.EqualizerSettings
import com.example.data.model.FavouriteStationItem
import com.example.data.model.RadioBand
import com.example.data.model.RadioStation
import com.example.playback.PlaybackStateSnapshot
import com.example.ui.components.BandBadge
import com.example.ui.components.StationListItem
import com.example.ui.components.StationLogoEmblem
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalRadioColors
import com.example.ui.viewmodel.MalayalamSyncUiState

@Composable
fun HomeScreen(
    stations: List<RadioStation>,
    malayalamSyncState: MalayalamSyncUiState,
    favourites: List<FavouriteStationItem>,
    favouriteIds: Set<String>,
    selectedBand: RadioBand?,
    selectedLanguage: String?,
    availableLanguages: List<String>,
    playbackState: PlaybackStateSnapshot,
    equalizerSettings: EqualizerSettings = EqualizerSettings(),
    onSelectBand: (RadioBand?) -> Unit,
    onSelectLanguage: (String?) -> Unit,
    onRefreshMalayalam: () -> Unit,
    onOpenManualTuner: () -> Unit,
    onOpenEqualizer: () -> Unit = {},
    onPlayStation: (RadioStation) -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleFavourite: (RadioStation) -> Unit,
    onOpenFavouritesTab: () -> Unit,
    onExpandPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    val languageFilteredStations = if (selectedLanguage == null) {
        stations
    } else {
        stations.filter {
            com.example.data.model.LanguageCatalog.normalizeLanguage(it.language)
                .equals(selectedLanguage, ignoreCase = true)
        }
    }
    val visibleStations = if (selectedBand == null) {
        languageFilteredStations
    } else {
        languageFilteredStations.filter { it.band == selectedBand }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Brand Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF061229),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_radio_symphony_emblem),
                            contentDescription = "Radio Symphony logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Column {
                        Text(
                            text = "Radio Symphony",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "All Malayalam • Best Audio Quality",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (playbackState.isNetworkAvailable) {
                        radioColors.successGreen.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (playbackState.isNetworkAvailable) Icons.Filled.Wifi else Icons.Filled.CloudOff,
                            contentDescription = if (playbackState.isNetworkAvailable) "Online" else "Offline",
                            modifier = Modifier.size(14.dp),
                            tint = if (playbackState.isNetworkAvailable) radioColors.successGreen else MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = if (playbackState.isNetworkAvailable) "ON AIR" else "OFFLINE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (playbackState.isNetworkAvailable) radioColors.successGreen else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // Studio Quick Actions: Equalizer & Manual Tuner
        item {
            val eqPresetLabel = EqualizerCatalog.presetDisplayName(equalizerSettings.presetId)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenEqualizer)
                        .testTag("home_equalizer_button"),
                    shape = RoundedCornerShape(16.dp),
                    color = if (equalizerSettings.enabled) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    border = BorderStroke(
                        width = if (equalizerSettings.enabled) 1.25.dp else 1.dp,
                        color = if (equalizerSettings.enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (equalizerSettings.enabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.GraphicEq,
                                    contentDescription = "Open Studio Equalizer",
                                    tint = if (equalizerSettings.enabled) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Equalizer",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (equalizerSettings.enabled) "ON • $eqPresetLabel" else "10-Band Hi-Fi DSP",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = if (equalizerSettings.enabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenManualTuner)
                        .testTag("home_manual_tuner_button"),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Tune,
                                    contentDescription = "Open Manual Tuner",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Manual Tuner",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "FM • AM • SW Dial",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Studio Hero Banner
        if (stations.isNotEmpty()) {
            item {
                val activeStation = playbackState.currentStation ?: stations.first()
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(196.dp)
                        .clickable {
                            if (playbackState.currentStation != null) {
                                onExpandPlayer()
                            } else {
                                onPlayStation(activeStation)
                            }
                        }
                        .testTag("home_hero_card"),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_radio_hero_1795062413981),
                            contentDescription = "Radio Symphony broadcast studio",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0x550B0F16),
                                            Color(0xCC0B0F16),
                                            Color(0xF00B0F16)
                                        )
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BandBadge(band = activeStation.band)
                                Text(
                                    text = if (playbackState.currentStation != null) "LAST TUNED STATION" else "FEATURED MALAYALAM LIVE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color(0xFFFFD89B)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = activeStation.name,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${activeStation.formattedFrequency} • ${activeStation.district} • ${activeStation.language}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFD0D7E3),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Button(
                                    onClick = {
                                        if (playbackState.currentStation?.id == activeStation.id) {
                                            onTogglePlayPause()
                                        } else {
                                            onPlayStation(activeStation)
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.testTag("hero_tune_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (playbackState.isPlaying && playbackState.currentStation?.id == activeStation.id) {
                                            "Live"
                                        } else {
                                            "Tune In"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick Favourites Strip
        if (favourites.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = radioColors.heartActive,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Quick Favourites",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        TextButton(onClick = onOpenFavouritesTab) {
                            Text("View All (${favourites.size})")
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(favourites.take(8), key = { it.station.id }) { fav ->
                            val isCurrent = playbackState.currentStation?.id == fav.station.id
                            Surface(
                                modifier = Modifier
                                    .width(148.dp)
                                    .clickable { onPlayStation(fav.station) }
                                    .testTag("home_fav_chip_${fav.station.id}"),
                                shape = RoundedCornerShape(18.dp),
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                                border = BorderStroke(
                                    width = if (isCurrent) 1.25.dp else 0.75.dp,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        StationLogoEmblem(
                                            station = fav.station,
                                            size = 42.dp,
                                            isPlaying = isCurrent && playbackState.isPlaying
                                        )
                                        BandBadge(band = fav.station.band)
                                    }
                                    Text(
                                        text = fav.station.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = fav.station.frequency,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // All Malayalam Stations Header + Refresh + Band Selector Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedLanguage?.let { "$it Radio Stations" } ?: "Stations by Language",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        val hdCount = languageFilteredStations.count { it.isHighQuality }
                        val subtitle = when (malayalamSyncState) {
                            is MalayalamSyncUiState.Syncing -> "Syncing highest-bitrate streams across languages…"
                            is MalayalamSyncUiState.Synced -> "${languageFilteredStations.size} stations • $hdCount HD/Hi-Fi streams"
                            is MalayalamSyncUiState.Error -> "${languageFilteredStations.size} stations ready ($hdCount HD) • Tap refresh"
                            MalayalamSyncUiState.Idle -> "${languageFilteredStations.size} stations • $hdCount HD/Hi-Fi streams"
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onRefreshMalayalam,
                        enabled = malayalamSyncState !is MalayalamSyncUiState.Syncing,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_refresh_malayalam_button")
                    ) {
                        if (malayalamSyncState is MalayalamSyncUiState.Syncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh stations",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Language Filter Chips
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedLanguage == null,
                            onClick = { onSelectLanguage(null) },
                            label = { Text("All Languages (${stations.size})") },
                            modifier = Modifier.testTag("home_lang_all")
                        )
                    }
                    items(availableLanguages) { language ->
                        val count = stations.count {
                            com.example.data.model.LanguageCatalog.normalizeLanguage(it.language)
                                .equals(language, ignoreCase = true)
                        }
                        FilterChip(
                            selected = selectedLanguage.equals(language, ignoreCase = true),
                            onClick = {
                                onSelectLanguage(if (selectedLanguage.equals(language, ignoreCase = true)) null else language)
                            },
                            label = { Text("$language ($count)") },
                            modifier = Modifier.testTag("home_lang_${language.lowercase()}")
                        )
                    }
                }

                // Band Filter Chips
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedBand == null,
                            onClick = { onSelectBand(null) },
                            label = { Text("All (${languageFilteredStations.size})") },
                            modifier = Modifier.testTag("home_band_all")
                        )
                    }
                    items(RadioBand.entries) { band ->
                        val count = languageFilteredStations.count { it.band == band }
                        FilterChip(
                            selected = selectedBand == band,
                            onClick = { onSelectBand(if (selectedBand == band) null else band) },
                            label = { Text("${band.label} ($count)") },
                            modifier = Modifier.testTag("home_band_${band.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // Station List
        items(visibleStations, key = { it.id }) { station ->
            val isCurrent = playbackState.currentStation?.id == station.id
            StationListItem(
                station = station,
                isCurrentStation = isCurrent,
                isPlaying = isCurrent && playbackState.isPlaying,
                isBuffering = isCurrent && playbackState.isBuffering,
                isFavourite = station.id in favouriteIds,
                onPlayClick = {
                    if (isCurrent) onTogglePlayPause() else onPlayStation(station)
                },
                onToggleFavourite = { onToggleFavourite(station) }
            )
        }
    }
}
