package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.data.model.RadioBand
import com.example.data.model.RadioStation
import com.example.playback.PlaybackStateSnapshot
import com.example.ui.components.StationListItem
import com.example.ui.viewmodel.DirectorySearchUiState

private val RADIO_GARDEN_QUICK_CITIES = listOf(
    "Kochi",
    "Thiruvananthapuram",
    "Kozhikode",
    "Thrissur",
    "Kannur",
    "Dubai",
    "Doha",
    "Mumbai",
    "London",
    "Tokyo"
)

@Composable
fun DiscoverScreen(
    stations: List<RadioStation>,
    favouriteIds: Set<String>,
    query: String,
    selectedBand: RadioBand?,
    selectedLanguage: String?,
    availableLanguages: List<String>,
    directorySearchState: DirectorySearchUiState,
    playbackState: PlaybackStateSnapshot,
    onQueryChange: (String) -> Unit,
    onBandChange: (RadioBand?) -> Unit,
    onLanguageChange: (String?) -> Unit,
    onSearchLiveDirectory: () -> Unit,
    onSearchRadioGarden: (String?) -> Unit,
    onOpenManualTuner: () -> Unit,
    onPlayStation: (RadioStation) -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleFavourite: (RadioStation) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("discover_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Discover & Radio Garden",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Search live Radio Garden cities, AM/FM/SW streams, or tune manually",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onOpenManualTuner,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("discover_manual_tuner_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Manual Tuner",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Tuner")
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("discover_search_input"),
                placeholder = { Text("Search stations or Radio Garden cities (Kochi, London)…") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search stations"
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchLiveDirectory() })
            )
        }

        // Radio Garden Live Cities Quick Strip
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                            imageVector = Icons.Filled.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Radio Garden Cities",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    FilledTonalButton(
                        onClick = { onSearchRadioGarden(null) },
                        enabled = directorySearchState !is DirectorySearchUiState.Loading,
                        modifier = Modifier.testTag("discover_radio_garden_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        if (directorySearchState is DirectorySearchUiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(15.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Public,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Radio Garden")
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(RADIO_GARDEN_QUICK_CITIES) { city ->
                        val isActiveCity = directorySearchState is DirectorySearchUiState.Success &&
                            directorySearchState.query.equals(city, ignoreCase = true)
                        FilterChip(
                            selected = isActiveCity,
                            onClick = { onSearchRadioGarden(city) },
                            label = { Text(city) },
                            modifier = Modifier.testTag("radio_garden_city_${city.lowercase()}")
                        )
                    }
                }
            }
        }

        // Band Filters + Global All-Directory Search Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = selectedBand == null,
                        onClick = { onBandChange(null) },
                        label = { Text("All") }
                    )
                    RadioBand.entries.forEach { band ->
                        FilterChip(
                            selected = selectedBand == band,
                            onClick = { onBandChange(if (selectedBand == band) null else band) },
                            label = { Text(band.label) }
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onSearchLiveDirectory,
                    enabled = directorySearchState !is DirectorySearchUiState.Loading,
                    modifier = Modifier.testTag("discover_live_directory_button"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("All Live")
                }
            }
        }

        // Language Chips
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedLanguage == null,
                        onClick = { onLanguageChange(null) },
                        label = { Text("All Languages") }
                    )
                }
                items(availableLanguages) { language ->
                    FilterChip(
                        selected = selectedLanguage == language,
                        onClick = {
                            onLanguageChange(if (selectedLanguage == language) null else language)
                        },
                        label = { Text(language) }
                    )
                }
            }
        }

        // Live Directory / Radio Garden Status Message
        if (directorySearchState is DirectorySearchUiState.Error) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = directorySearchState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        } else if (directorySearchState is DirectorySearchUiState.Success) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("discover_live_results_banner")
                ) {
                    Text(
                        text = "${directorySearchState.sourceLabel} • ${directorySearchState.results.size} live stations for \"${directorySearchState.query}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        if (stations.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No matching stations locally",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tap Radio Garden or pick any city above to stream live stations worldwide.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(stations, key = { it.id }) { station ->
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
}
