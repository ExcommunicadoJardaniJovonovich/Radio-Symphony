package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.FavouriteSortOrder
import com.example.data.model.FavouriteStationItem
import com.example.data.model.RadioBand
import com.example.data.model.RadioStation
import com.example.playback.PlaybackStateSnapshot
import com.example.ui.components.StationListItem

@Composable
fun FavouritesScreen(
    favourites: List<FavouriteStationItem>,
    totalFavouritesCount: Int,
    searchQuery: String,
    sortOrder: FavouriteSortOrder,
    bandFilter: RadioBand?,
    playbackState: PlaybackStateSnapshot,
    onSearchQueryChange: (String) -> Unit,
    onSortOrderChange: (FavouriteSortOrder) -> Unit,
    onBandFilterChange: (RadioBand?) -> Unit,
    onPlayStation: (RadioStation) -> Unit,
    onTogglePlayPause: () -> Unit,
    onRemoveFavourite: (RadioStation) -> Unit,
    onExploreStations: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("favourites_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Favourite Stations",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "$totalFavouritesCount saved station${if (totalFavouritesCount == 1) "" else "s"} • Stored offline on this device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Offline notice when disconnected (favourites remain fully browseable!)
        if (!playbackState.isNetworkAvailable) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudOff,
                            contentDescription = "Offline mode",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = stringResource(R.string.status_offline),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        // Search within Favourites
        if (totalFavouritesCount > 0) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("favourites_search_input"),
                    placeholder = { Text("Search within favourites…") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search favourites"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Clear favourites search"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            }

            // Sort Controls (Recently Played / Alphabetical)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = sortOrder == FavouriteSortOrder.RECENTLY_PLAYED,
                        onClick = { onSortOrderChange(FavouriteSortOrder.RECENTLY_PLAYED) },
                        label = { Text("Recently Played") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("sort_recently_played")
                    )
                    FilterChip(
                        selected = sortOrder == FavouriteSortOrder.ALPHABETICAL,
                        onClick = { onSortOrderChange(FavouriteSortOrder.ALPHABETICAL) },
                        label = { Text("A–Z") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.SortByAlpha,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("sort_alphabetical")
                    )
                }
            }

            // Band Filter Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = bandFilter == null,
                        onClick = { onBandFilterChange(null) },
                        label = { Text("All") }
                    )
                    RadioBand.entries.forEach { band ->
                        FilterChip(
                            selected = bandFilter == band,
                            onClick = { onBandFilterChange(if (bandFilter == band) null else band) },
                            label = { Text(band.label) }
                        )
                    }
                }
            }
        }

        // Empty States
        if (totalFavouritesCount == 0) {
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("favourites_empty_state")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_empty_favourites_1795062414581),
                            contentDescription = "No favourite radio stations saved yet",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                        Text(
                            text = "No Favourite Stations Yet",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tap the heart icon on any AM, FM, or SW station to save it here. Your favourites stay available even offline and sync directly with the player.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onExploreStations,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("favourites_explore_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Explore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Explore Stations")
                        }
                    }
                }
            }
        } else if (favourites.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "No matching favourites",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Try clearing your search filter or switching frequency band.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(favourites, key = { it.station.id }) { fav ->
                val station = fav.station
                val isCurrent = playbackState.currentStation?.id == station.id
                StationListItem(
                    station = station,
                    isCurrentStation = isCurrent,
                    isPlaying = isCurrent && playbackState.isPlaying,
                    isBuffering = isCurrent && playbackState.isBuffering,
                    isFavourite = true,
                    onPlayClick = {
                        if (isCurrent) onTogglePlayPause() else onPlayStation(station)
                    },
                    onToggleFavourite = { onRemoveFavourite(station) }
                )
            }
        }
    }
}
