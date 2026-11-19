package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.ui.components.FullScreenRadioPlayer
import com.example.ui.components.MiniRadioPlayer
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.DistrictsScreen
import com.example.ui.screens.EqualizerScreen
import com.example.ui.screens.FavouritesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ManualTunerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.viewmodel.RadioViewModel

private const val ROUTE_MANUAL_TUNER = "manual_tuner"
private const val ROUTE_EQUALIZER = "equalizer"

enum class RadioDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    DISCOVER("discover", R.string.nav_discover, Icons.Filled.Explore, Icons.Outlined.Explore),
    DISTRICTS("districts", R.string.nav_districts, Icons.Filled.LocationCity, Icons.Outlined.LocationCity),
    FAVOURITES("favourites", R.string.nav_favourites, Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun RadioSymphonyRoot(
    viewModel: RadioViewModel,
    openFullPlayerOnLaunch: Boolean = false
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: RadioDestination.HOME.route

    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val catalogStations by viewModel.catalogStations.collectAsStateWithLifecycle()
    val allKnownStations by viewModel.allKnownStations.collectAsStateWithLifecycle()
    val customStations by viewModel.customStations.collectAsStateWithLifecycle()
    val malayalamSyncState by viewModel.malayalamSyncState.collectAsStateWithLifecycle()
    val availableLanguages by viewModel.availableLanguages.collectAsStateWithLifecycle()
    val languageSummaries by viewModel.languageSummaries.collectAsStateWithLifecycle()
    val selectedLanguageDetail by viewModel.selectedLanguageDetail.collectAsStateWithLifecycle()
    val languageLoadState by viewModel.languageLoadState.collectAsStateWithLifecycle()
    val favouriteIds by viewModel.favouriteIds.collectAsStateWithLifecycle()
    val allFavourites by viewModel.allFavourites.collectAsStateWithLifecycle()
    val filteredFavourites by viewModel.filteredFavourites.collectAsStateWithLifecycle()
    val homeBandFilter by viewModel.homeBandFilter.collectAsStateWithLifecycle()
    val homeLanguageFilter by viewModel.homeLanguageFilter.collectAsStateWithLifecycle()
    val discoverStations by viewModel.filteredDiscoverStations.collectAsStateWithLifecycle()
    val discoverQuery by viewModel.discoverQuery.collectAsStateWithLifecycle()
    val discoverBandFilter by viewModel.discoverBandFilter.collectAsStateWithLifecycle()
    val discoverLanguageFilter by viewModel.discoverLanguageFilter.collectAsStateWithLifecycle()
    val directorySearchState by viewModel.directorySearchState.collectAsStateWithLifecycle()
    val districtSummaries by viewModel.districtSummaries.collectAsStateWithLifecycle()
    val selectedDistrict by viewModel.selectedDistrict.collectAsStateWithLifecycle()
    val favouritesQuery by viewModel.favouritesQuery.collectAsStateWithLifecycle()
    val favouritesBandFilter by viewModel.favouritesBandFilter.collectAsStateWithLifecycle()
    val favouriteSortOrder by viewModel.favouriteSortOrder.collectAsStateWithLifecycle()
    val isPlayerExpanded by viewModel.isPlayerExpanded.collectAsStateWithLifecycle()
    val autoReconnectEnabled by viewModel.autoReconnectEnabled.collectAsStateWithLifecycle()
    val restoreLastStationEnabled by viewModel.restoreLastStationEnabled.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val equalizerSettings by viewModel.equalizerSettings.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var askedNotificationOnce by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Media playback works regardless; notification shows when granted */ }

    fun ensureNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedNotificationOnce) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                askedNotificationOnce = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(openFullPlayerOnLaunch) {
        if (openFullPlayerOnLaunch) {
            viewModel.expandPlayer()
        }
    }

    LaunchedEffect(userMessage) {
        val message = userMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
        viewModel.clearUserMessage()
    }

    fun navigateTo(destination: RadioDestination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val currentStation = playbackState.currentStation
    val isCurrentFavourite = currentStation != null && currentStation.id in favouriteIds

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 600.dp

            val contentNavHost: @Composable (Modifier) -> Unit = { hostModifier ->
                NavHost(
                    navController = navController,
                    startDestination = RadioDestination.HOME.route,
                    modifier = hostModifier
                ) {
                    composable(RadioDestination.HOME.route) {
                        HomeScreen(
                            stations = allKnownStations,
                            malayalamSyncState = malayalamSyncState,
                            favourites = allFavourites,
                            favouriteIds = favouriteIds,
                            selectedBand = homeBandFilter,
                            selectedLanguage = homeLanguageFilter,
                            availableLanguages = availableLanguages,
                            playbackState = playbackState,
                            equalizerSettings = equalizerSettings,
                            onSelectBand = viewModel::setHomeBandFilter,
                            onSelectLanguage = viewModel::setHomeLanguageFilter,
                            onRefreshMalayalam = { viewModel.refreshAllMalayalamStations(silent = false) },
                            onOpenManualTuner = { navController.navigate(ROUTE_MANUAL_TUNER) },
                            onOpenEqualizer = {
                                navController.navigate(ROUTE_EQUALIZER) {
                                    launchSingleTop = true
                                }
                            },
                            onPlayStation = { station ->
                                ensureNotificationPermissionIfNeeded()
                                viewModel.playStation(station)
                            },
                            onTogglePlayPause = {
                                ensureNotificationPermissionIfNeeded()
                                viewModel.togglePlayPause()
                            },
                            onToggleFavourite = viewModel::toggleFavourite,
                            onOpenFavouritesTab = { navigateTo(RadioDestination.FAVOURITES) },
                            onExpandPlayer = viewModel::expandPlayer
                        )
                    }
                    composable(RadioDestination.DISCOVER.route) {
                        DiscoverScreen(
                            stations = discoverStations,
                            favouriteIds = favouriteIds,
                            query = discoverQuery,
                            selectedBand = discoverBandFilter,
                            selectedLanguage = discoverLanguageFilter,
                            availableLanguages = availableLanguages,
                            directorySearchState = directorySearchState,
                            playbackState = playbackState,
                            onQueryChange = viewModel::setDiscoverQuery,
                            onBandChange = viewModel::setDiscoverBandFilter,
                            onLanguageChange = viewModel::setDiscoverLanguageFilter,
                            onSearchLiveDirectory = viewModel::searchLiveDirectory,
                            onSearchRadioGarden = viewModel::searchRadioGarden,
                            onOpenManualTuner = { navController.navigate(ROUTE_MANUAL_TUNER) },
                            onPlayStation = { station ->
                                ensureNotificationPermissionIfNeeded()
                                viewModel.playStation(station)
                            },
                            onTogglePlayPause = {
                                ensureNotificationPermissionIfNeeded()
                                viewModel.togglePlayPause()
                            },
                            onToggleFavourite = viewModel::toggleFavourite
                        )
                    }
                    composable(ROUTE_MANUAL_TUNER) {
                        ManualTunerScreen(
                            allStations = catalogStations,
                            customStations = customStations,
                            favouriteIds = favouriteIds,
                            playbackState = playbackState,
                            onBack = { navController.popBackStack() },
                            onPlayStation = { station ->
                                ensureNotificationPermissionIfNeeded()
                                viewModel.playStation(station)
                            },
                            onTogglePlayPause = {
                                ensureNotificationPermissionIfNeeded()
                                viewModel.togglePlayPause()
                            },
                            onToggleFavourite = viewModel::toggleFavourite,
                            onSaveCustomStation = { name, freq, band, district, language, url, alsoFav ->
                                ensureNotificationPermissionIfNeeded()
                                viewModel.saveAndPlayCustomStation(
                                    name = name,
                                    frequency = freq,
                                    band = band,
                                    district = district,
                                    language = language,
                                    streamUrl = url,
                                    alsoFavourite = alsoFav
                                )
                            },
                            onDeleteCustomStation = viewModel::deleteCustomStation
                        )
                    }
                    composable(RadioDestination.DISTRICTS.route) {
                        val stationsInLanguage = selectedLanguageDetail?.let {
                            viewModel.stationsForLanguage(it)
                        }.orEmpty()
                        val stationsInDistrict = selectedDistrict?.let {
                            viewModel.stationsForDistrict(it)
                        }.orEmpty()
                        DistrictsScreen(
                            languages = languageSummaries,
                            selectedLanguage = selectedLanguageDetail,
                            languageStations = stationsInLanguage,
                            languageLoadState = languageLoadState,
                            districts = districtSummaries,
                            selectedDistrict = selectedDistrict,
                            districtStations = stationsInDistrict,
                            favouriteIds = favouriteIds,
                            playbackState = playbackState,
                            onSelectLanguage = viewModel::selectLanguageDetail,
                            onRefreshLanguage = { lang -> viewModel.loadStationsForLanguage(lang, silent = false) },
                            onSelectDistrict = viewModel::selectDistrict,
                            onPlayStation = { station ->
                                ensureNotificationPermissionIfNeeded()
                                viewModel.playStation(station)
                            },
                            onTogglePlayPause = {
                                ensureNotificationPermissionIfNeeded()
                                viewModel.togglePlayPause()
                            },
                            onToggleFavourite = viewModel::toggleFavourite
                        )
                    }
                    composable(RadioDestination.FAVOURITES.route) {
                        FavouritesScreen(
                            favourites = filteredFavourites,
                            totalFavouritesCount = allFavourites.size,
                            searchQuery = favouritesQuery,
                            sortOrder = favouriteSortOrder,
                            bandFilter = favouritesBandFilter,
                            playbackState = playbackState,
                            onSearchQueryChange = viewModel::setFavouritesQuery,
                            onSortOrderChange = viewModel::setFavouriteSortOrder,
                            onBandFilterChange = viewModel::setFavouritesBandFilter,
                            onPlayStation = { station ->
                                ensureNotificationPermissionIfNeeded()
                                viewModel.playStation(station)
                            },
                            onTogglePlayPause = {
                                ensureNotificationPermissionIfNeeded()
                                viewModel.togglePlayPause()
                            },
                            onRemoveFavourite = viewModel::toggleFavourite,
                            onExploreStations = { navigateTo(RadioDestination.DISCOVER) }
                        )
                    }
                    composable(RadioDestination.SETTINGS.route) {
                        SettingsScreen(
                            autoReconnectEnabled = autoReconnectEnabled,
                            restoreLastStationEnabled = restoreLastStationEnabled,
                            themeMode = themeMode,
                            equalizerSettings = equalizerSettings,
                            onAutoReconnectChange = viewModel::setAutoReconnect,
                            onRestoreLastStationChange = viewModel::setRestoreLastStation,
                            onThemeModeChange = viewModel::setThemeMode,
                            onOpenEqualizer = { navController.navigate(ROUTE_EQUALIZER) },
                            onRequestNotificationPermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        )
                    }
                    composable(ROUTE_EQUALIZER) {
                        EqualizerScreen(
                            settings = equalizerSettings,
                            playbackState = playbackState,
                            onBack = { navController.popBackStack() },
                            onToggleEnabled = viewModel::setEqualizerEnabled,
                            onSelectPreset = viewModel::selectEqualizerPreset,
                            onBandGainChange = viewModel::setEqualizerBandGain,
                            onPreampChange = viewModel::setEqualizerPreamp,
                            onBassBoostChange = viewModel::setEqualizerBassBoost,
                            onVirtualizerChange = viewModel::setEqualizerVirtualizer,
                            onLoudnessChange = viewModel::setEqualizerLoudness,
                            onLimiterToggle = viewModel::setEqualizerLimiterEnabled,
                            onResetFlat = viewModel::resetEqualizerFlat
                        )
                    }
                }
            }

            if (isWideScreen) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    NavigationRail(
                        modifier = Modifier
                            .fillMaxHeight()
                            .navigationBarsPadding(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        RadioDestination.entries.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }

                    Scaffold(
                        modifier = Modifier.weight(1f),
                        contentWindowInsets = WindowInsets.safeDrawing,
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        bottomBar = {
                            if (currentStation != null) {
                                MiniRadioPlayer(
                                    playbackState = playbackState,
                                    isFavourite = isCurrentFavourite,
                                    equalizerEnabled = equalizerSettings.enabled,
                                    onTogglePlayPause = {
                                        ensureNotificationPermissionIfNeeded()
                                        viewModel.togglePlayPause()
                                    },
                                    onToggleFavourite = { viewModel.toggleFavourite(currentStation) },
                                    onOpenEqualizer = {
                                        navController.navigate(ROUTE_EQUALIZER) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onExpandPlayer = viewModel::expandPlayer,
                                    modifier = Modifier.navigationBarsPadding()
                                )
                            }
                        }
                    ) { innerPadding ->
                        contentNavHost(Modifier.fillMaxSize().padding(innerPadding))
                    }
                }
            } else {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        Column {
                            if (currentStation != null) {
                                MiniRadioPlayer(
                                    playbackState = playbackState,
                                    isFavourite = isCurrentFavourite,
                                    equalizerEnabled = equalizerSettings.enabled,
                                    onTogglePlayPause = {
                                        ensureNotificationPermissionIfNeeded()
                                        viewModel.togglePlayPause()
                                    },
                                    onToggleFavourite = { viewModel.toggleFavourite(currentStation) },
                                    onOpenEqualizer = {
                                        navController.navigate(ROUTE_EQUALIZER) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onExpandPlayer = viewModel::expandPlayer
                                )
                            }
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                RadioDestination.entries.forEach { dest ->
                                    val selected = currentRoute == dest.route
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navigateTo(dest) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = stringResource(dest.labelRes)
                                            )
                                        },
                                        label = { Text(stringResource(dest.labelRes)) },
                                        alwaysShowLabel = true,
                                        modifier = Modifier.testTag("nav_${dest.route}")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    contentNavHost(Modifier.fillMaxSize().padding(innerPadding))
                }
            }
        }

        // Full-Screen Radio Player Overlay
        AnimatedVisibility(
            visible = isPlayerExpanded && currentStation != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            FullScreenRadioPlayer(
                playbackState = playbackState,
                isFavourite = isCurrentFavourite,
                favouritesCount = allFavourites.size,
                equalizerEnabled = equalizerSettings.enabled,
                onCollapse = viewModel::collapsePlayer,
                onOpenEqualizer = {
                    viewModel.collapsePlayer()
                    navController.navigate(ROUTE_EQUALIZER) {
                        launchSingleTop = true
                    }
                },
                onTogglePlayPause = {
                    ensureNotificationPermissionIfNeeded()
                    viewModel.togglePlayPause()
                },
                onStop = viewModel::stopPlayback,
                onRetry = viewModel::retryPlayback,
                onToggleFavourite = { currentStation?.let { viewModel.toggleFavourite(it) } },
                onPreviousFavourite = viewModel::playPreviousFavourite,
                onNextFavourite = viewModel::playNextFavourite,
                onSetSleepTimer = viewModel::setSleepTimer
            )
        }
    }
}
