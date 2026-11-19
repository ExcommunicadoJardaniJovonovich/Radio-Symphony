package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DistrictSummary
import com.example.data.model.LanguageCatalog
import com.example.data.model.LanguageRegionGroup
import com.example.data.model.LanguageSummary
import com.example.data.model.RadioBand
import com.example.data.model.RadioStation
import com.example.playback.PlaybackStateSnapshot
import com.example.ui.components.BandBadge
import com.example.ui.components.StationListItem
import com.example.ui.viewmodel.LanguageLoadUiState

private enum class BrowseMode(val label: String) {
    LANGUAGES("By Language"),
    DISTRICTS("By District")
}

@Composable
fun DistrictsScreen(
    languages: List<LanguageSummary>,
    selectedLanguage: String?,
    languageStations: List<RadioStation>,
    languageLoadState: LanguageLoadUiState,
    districts: List<DistrictSummary>,
    selectedDistrict: String?,
    districtStations: List<RadioStation>,
    favouriteIds: Set<String>,
    playbackState: PlaybackStateSnapshot,
    onSelectLanguage: (String?) -> Unit,
    onRefreshLanguage: (String) -> Unit,
    onSelectDistrict: (String?) -> Unit,
    onPlayStation: (RadioStation) -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleFavourite: (RadioStation) -> Unit,
    modifier: Modifier = Modifier
) {
    var browseMode by rememberSaveable { mutableStateOf(BrowseMode.LANGUAGES) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    // 1. Language Detail Drill-Down
    if (selectedLanguage != null) {
        BackHandler { onSelectLanguage(null) }
        var detailBandFilter by remember(selectedLanguage) { mutableStateOf<RadioBand?>(null) }
        val filteredStations = if (detailBandFilter == null) {
            languageStations
        } else {
            languageStations.filter { it.band == detailBandFilter }
        }
        val langEntry = LanguageCatalog.findEntry(selectedLanguage)
        val isLoadingThisLanguage = languageLoadState is LanguageLoadUiState.Loading &&
            languageLoadState.language.equals(selectedLanguage, ignoreCase = true)

        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .testTag("language_detail_screen"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { onSelectLanguage(null) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("language_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to languages"
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = selectedLanguage,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (langEntry != null && langEntry.nativeName != selectedLanguage) {
                                Text(
                                    text = langEntry.nativeName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = if (isLoadingThisLanguage) {
                                "Loading live $selectedLanguage stations from Radio Garden & Directory…"
                            } else {
                                "${languageStations.size} live station${if (languageStations.size == 1) "" else "s"} in $selectedLanguage"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { onRefreshLanguage(selectedLanguage) },
                        enabled = !isLoadingThisLanguage,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("language_refresh_button")
                    ) {
                        if (isLoadingThisLanguage) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh $selectedLanguage stations",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Band filter chips for this language
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = detailBandFilter == null,
                            onClick = { detailBandFilter = null },
                            label = { Text("All (${languageStations.size})") }
                        )
                    }
                    items(RadioBand.entries) { band ->
                        val count = languageStations.count { it.band == band }
                        FilterChip(
                            selected = detailBandFilter == band,
                            onClick = { detailBandFilter = if (detailBandFilter == band) null else band },
                            label = { Text("${band.label} ($count)") }
                        )
                    }
                }
            }

            if (languageLoadState is LanguageLoadUiState.Error &&
                languageLoadState.language.equals(selectedLanguage, ignoreCase = true)
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = languageLoadState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            if (filteredStations.isEmpty() && !isLoadingThisLanguage) {
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
                                text = "No stations loaded yet for $selectedLanguage",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap the refresh icon above to fetch live $selectedLanguage stations from Radio Garden & Radio-Browser.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredStations, key = { it.id }) { station ->
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
        return
    }

    // 2. District Detail Drill-Down
    if (selectedDistrict != null) {
        BackHandler { onSelectDistrict(null) }

        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .testTag("district_detail_screen"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { onSelectDistrict(null) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("district_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to districts"
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = selectedDistrict,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${districtStations.size} live station${if (districtStations.size == 1) "" else "s"} broadcasting",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(districtStations, key = { it.id }) { station ->
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
        return
    }

    // 3. Main Organized Languages & Districts Directory
    val q = searchQuery.trim().lowercase()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("districts_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (browseMode == BrowseMode.LANGUAGES) "Every Language" else "Districts & Regions",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (browseMode == BrowseMode.LANGUAGES) {
                            "Browse South Indian, Indian & World radio organized by language"
                        } else {
                            "Explore regional AM, FM, and Shortwave broadcasts by city or district"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    BrowseMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = browseMode == mode,
                            onClick = {
                                browseMode = mode
                                searchQuery = ""
                            },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = BrowseMode.entries.size
                            ),
                            modifier = Modifier.testTag("browse_mode_${mode.name.lowercase()}")
                        ) {
                            Text(mode.label)
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("browse_search_input"),
                    placeholder = {
                        Text(
                            if (browseMode == BrowseMode.LANGUAGES) {
                                "Filter languages (Malayalam, Tamil, Hindi, Arabic…)"
                            } else {
                                "Filter districts or cities (Kochi, Dubai, London…)"
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Clear filter"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }

        if (browseMode == BrowseMode.LANGUAGES) {
            val filteredLanguages = languages.filter { summary ->
                q.isEmpty() ||
                    summary.language.lowercase().contains(q) ||
                    summary.nativeName.lowercase().contains(q) ||
                    summary.regionGroup.label.lowercase().contains(q)
            }

            LanguageRegionGroup.entries.forEach { group ->
                val itemsInGroup = filteredLanguages.filter { it.regionGroup == group }
                if (itemsInGroup.isNotEmpty()) {
                    item(key = "header_${group.name}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = group.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${itemsInGroup.size} languages",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(itemsInGroup, key = { "lang_${it.language}" }) { summary ->
                        LanguageSummaryCard(
                            summary = summary,
                            onClick = { onSelectLanguage(summary.language) }
                        )
                    }
                }
            }
        } else {
            val filteredDistricts = districts.filter { summary ->
                q.isEmpty() ||
                    summary.district.lowercase().contains(q) ||
                    summary.languages.any { it.lowercase().contains(q) }
            }

            items(filteredDistricts, key = { "dist_${it.district}" }) { summary ->
                DistrictSummaryCard(
                    summary = summary,
                    onClick = { onSelectDistrict(summary.district) }
                )
            }
        }
    }
}

@Composable
private fun LanguageSummaryCard(
    summary: LanguageSummary,
    onClick: () -> Unit
) {
    val accent = Color(summary.accentColorHex)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("language_card_${summary.language.lowercase()}"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accent.copy(alpha = 0.16f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Language,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = summary.language,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (summary.nativeName != summary.language) {
                        Text(
                            text = summary.nativeName,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                            color = accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    summary.bands.forEach { band ->
                        BandBadge(band = band)
                    }
                    Text(
                        text = when {
                            summary.districts.isNotEmpty() -> summary.districts.joinToString(" • ")
                            else -> "Tap to load live HD streams"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (summary.stationCount > 0) "${summary.stationCount}" else "Live",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (summary.stationCount > 0) MaterialTheme.colorScheme.primary else accent
                )
                Text(
                    text = when (summary.stationCount) {
                        0 -> "discover"
                        1 -> "station"
                        else -> "stations"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open ${summary.language}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DistrictSummaryCard(
    summary: DistrictSummary,
    onClick: () -> Unit
) {
    val accent = Color(summary.accentColorHex)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("district_card_${summary.district}"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accent.copy(alpha = 0.16f),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationCity,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.padding(11.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = summary.district,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    summary.bands.forEach { band ->
                        BandBadge(band = band)
                    }
                    Text(
                        text = summary.languages.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${summary.stationCount}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (summary.stationCount == 1) "station" else "stations",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open ${summary.district}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
