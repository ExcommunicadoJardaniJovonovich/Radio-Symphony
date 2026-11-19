package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.RadioPreferences
import com.example.data.model.AppThemeMode
import com.example.data.model.DistrictSummary
import com.example.data.model.EqualizerCatalog
import com.example.data.model.EqualizerPreset
import com.example.data.model.EqualizerSettings
import com.example.data.model.FavouriteSortOrder
import com.example.data.model.FavouriteStationItem
import com.example.data.model.LanguageCatalog
import com.example.data.model.LanguageSummary
import com.example.data.model.RadioBand
import com.example.data.model.RadioStation
import com.example.data.repository.RadioRepository
import com.example.playback.PlaybackStateSnapshot
import com.example.playback.RadioPlaybackManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DirectorySearchUiState {
    data object Idle : DirectorySearchUiState
    data object Loading : DirectorySearchUiState
    data class Success(
        val results: List<RadioStation>,
        val query: String,
        val sourceLabel: String = "Radio Garden & Global"
    ) : DirectorySearchUiState
    data class Error(val message: String) : DirectorySearchUiState
}

sealed interface MalayalamSyncUiState {
    data object Idle : MalayalamSyncUiState
    data object Syncing : MalayalamSyncUiState
    data class Synced(val liveCount: Int) : MalayalamSyncUiState
    data class Error(val message: String) : MalayalamSyncUiState
}

sealed interface LanguageLoadUiState {
    data object Idle : LanguageLoadUiState
    data class Loading(val language: String) : LanguageLoadUiState
    data class Loaded(val language: String, val count: Int) : LanguageLoadUiState
    data class Error(val language: String, val message: String) : LanguageLoadUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class RadioViewModel(
    private val repository: RadioRepository,
    private val playbackManager: RadioPlaybackManager,
    private val preferences: RadioPreferences
) : ViewModel() {

    val playbackState: StateFlow<PlaybackStateSnapshot> = playbackManager.state

    val catalogStations: StateFlow<List<RadioStation>> = repository.allCatalogStations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.curatedStations)

    val customStations: StateFlow<List<RadioStation>> = repository.customStations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _malayalamSyncState = MutableStateFlow<MalayalamSyncUiState>(MalayalamSyncUiState.Idle)
    val malayalamSyncState: StateFlow<MalayalamSyncUiState> = _malayalamSyncState.asStateFlow()

    val favouriteIds: StateFlow<Set<String>> = repository.favouriteIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val themeMode: StateFlow<AppThemeMode> = preferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppThemeMode.DARK)

    val autoReconnectEnabled: StateFlow<Boolean> = preferences.autoReconnectEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val restoreLastStationEnabled: StateFlow<Boolean> = preferences.restoreLastStationEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val favouriteSortOrder: StateFlow<FavouriteSortOrder> = preferences.favouriteSortOrder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavouriteSortOrder.RECENTLY_PLAYED)

    val equalizerSettings: StateFlow<EqualizerSettings> = preferences.equalizerSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EqualizerSettings())

    // Home band & language filters
    private val _homeBandFilter = MutableStateFlow<RadioBand?>(null)
    val homeBandFilter: StateFlow<RadioBand?> = _homeBandFilter.asStateFlow()

    private val _homeLanguageFilter = MutableStateFlow<String?>(null)
    val homeLanguageFilter: StateFlow<String?> = _homeLanguageFilter.asStateFlow()

    // Discover filters
    private val _discoverQuery = MutableStateFlow("")
    val discoverQuery: StateFlow<String> = _discoverQuery.asStateFlow()

    private val _discoverBandFilter = MutableStateFlow<RadioBand?>(null)
    val discoverBandFilter: StateFlow<RadioBand?> = _discoverBandFilter.asStateFlow()

    private val _discoverLanguageFilter = MutableStateFlow<String?>(null)
    val discoverLanguageFilter: StateFlow<String?> = _discoverLanguageFilter.asStateFlow()

    private val _directorySearchState = MutableStateFlow<DirectorySearchUiState>(DirectorySearchUiState.Idle)
    val directorySearchState: StateFlow<DirectorySearchUiState> = _directorySearchState.asStateFlow()

    // Districts & Languages browser state
    private val _selectedDistrict = MutableStateFlow<String?>(null)
    val selectedDistrict: StateFlow<String?> = _selectedDistrict.asStateFlow()

    private val _selectedLanguageDetail = MutableStateFlow<String?>(null)
    val selectedLanguageDetail: StateFlow<String?> = _selectedLanguageDetail.asStateFlow()

    private val _loadedLanguageStations = MutableStateFlow<Map<String, List<RadioStation>>>(emptyMap())

    private val _languageLoadState = MutableStateFlow<LanguageLoadUiState>(LanguageLoadUiState.Idle)
    val languageLoadState: StateFlow<LanguageLoadUiState> = _languageLoadState.asStateFlow()

    // Favourites search & band filter
    private val _favouritesQuery = MutableStateFlow("")
    val favouritesQuery: StateFlow<String> = _favouritesQuery.asStateFlow()

    private val _favouritesBandFilter = MutableStateFlow<RadioBand?>(null)
    val favouritesBandFilter: StateFlow<RadioBand?> = _favouritesBandFilter.asStateFlow()

    // Full-screen player expansion
    private val _isPlayerExpanded = MutableStateFlow(false)
    val isPlayerExpanded: StateFlow<Boolean> = _isPlayerExpanded.asStateFlow()

    // Snackbar feedback messages
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    val allFavourites: StateFlow<List<FavouriteStationItem>> = favouriteSortOrder
        .flatMapLatest { sortOrder -> repository.observeFavourites(sortOrder) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filteredFavourites: StateFlow<List<FavouriteStationItem>> = combine(
        allFavourites,
        favouritesQuery,
        favouritesBandFilter
    ) { list, query, band ->
        repository.filterFavourites(list, query, band)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allKnownStations: StateFlow<List<RadioStation>> = combine(
        catalogStations,
        _loadedLanguageStations,
        directorySearchState,
        allFavourites
    ) { catalog, byLang, dirState, favs ->
        buildList {
            addAll(catalog)
            byLang.values.forEach { addAll(it) }
            if (dirState is DirectorySearchUiState.Success) addAll(dirState.results)
            addAll(favs.map { it.station })
        }.distinctBy { it.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.curatedStations)

    val availableLanguages: StateFlow<List<String>> = allKnownStations
        .map { stations -> repository.getAvailableLanguages(stations) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            repository.getAvailableLanguages(repository.curatedStations)
        )

    val languageSummaries: StateFlow<List<LanguageSummary>> = allKnownStations
        .map { stations -> repository.getLanguageSummaries(stations) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            repository.getLanguageSummaries(repository.curatedStations)
        )

    val districtSummaries: StateFlow<List<DistrictSummary>> = allKnownStations
        .map { stations -> repository.getDistrictSummaries(stations) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            repository.getDistrictSummaries(repository.curatedStations)
        )

    val filteredDiscoverStations: StateFlow<List<RadioStation>> = combine(
        allKnownStations,
        discoverQuery,
        discoverBandFilter,
        discoverLanguageFilter,
        directorySearchState
    ) { catalog, query, band, language, dirState ->
        val q = query.trim().lowercase()
        val liveMatches = if (dirState is DirectorySearchUiState.Success &&
            dirState.query.trim().equals(q, ignoreCase = true)
        ) {
            dirState.results.filter { station ->
                val matchesBand = band == null || station.band == band
                val matchesLang = language == null ||
                    LanguageCatalog.normalizeLanguage(station.language).equals(language, ignoreCase = true)
                matchesBand && matchesLang
            }
        } else {
            emptyList()
        }

        val localMatches = catalog.filter { station ->
            val matchesBand = band == null || station.band == band
            val matchesLang = language == null ||
                LanguageCatalog.normalizeLanguage(station.language).equals(language, ignoreCase = true)
            val matchesQuery = q.isEmpty() ||
                station.name.lowercase().contains(q) ||
                station.frequency.lowercase().contains(q) ||
                station.district.lowercase().contains(q) ||
                station.language.lowercase().contains(q) ||
                station.genre.lowercase().contains(q) ||
                station.tagline.lowercase().contains(q) ||
                station.band.label.lowercase().contains(q)
            matchesBand && matchesLang && matchesQuery
        }

        (liveMatches + localMatches).distinctBy { it.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.curatedStations)

    init {
        refreshAllMalayalamStations(silent = true)
    }

    fun refreshAllMalayalamStations(silent: Boolean = false) {
        if (_malayalamSyncState.value is MalayalamSyncUiState.Syncing) return
        if (!playbackState.value.isNetworkAvailable) {
            if (!silent) {
                _malayalamSyncState.value = MalayalamSyncUiState.Error(
                    "Offline — showing saved & built-in Malayalam stations."
                )
            }
            return
        }

        viewModelScope.launch {
            _malayalamSyncState.value = MalayalamSyncUiState.Syncing
            repository.refreshAllMalayalamStations()
                .onSuccess { liveStations ->
                    _malayalamSyncState.value = MalayalamSyncUiState.Synced(liveStations.size)
                    if (!silent) {
                        _userMessage.value = "Loaded ${liveStations.size} live Malayalam & FM stations"
                    }
                }
                .onFailure { err ->
                    _malayalamSyncState.value = MalayalamSyncUiState.Error(
                        "Live directory sync failed: ${err.localizedMessage ?: "Check connection"}"
                    )
                }
        }
    }

    fun stationsForDistrict(district: String): List<RadioStation> {
        return allKnownStations.value
            .filter { it.district.equals(district, ignoreCase = true) }
    }

    fun stationsForLanguage(language: String): List<RadioStation> {
        val canonical = LanguageCatalog.normalizeLanguage(language)
        return allKnownStations.value
            .filter { LanguageCatalog.normalizeLanguage(it.language).equals(canonical, ignoreCase = true) }
    }

    fun playStation(station: RadioStation, expandPlayer: Boolean = false) {
        playbackManager.playStation(station)
        if (expandPlayer) {
            _isPlayerExpanded.value = true
        }
    }

    fun togglePlayPause() {
        playbackManager.togglePlayPause()
    }

    fun stopPlayback() {
        playbackManager.stopPlayback()
    }

    fun retryPlayback() {
        playbackManager.retryCurrentStation()
    }

    fun playNextFavourite() {
        val favs = allFavourites.value.map { it.station }
        playbackManager.playNextFavourite(favs, catalogStations.value)
    }

    fun playPreviousFavourite() {
        val favs = allFavourites.value.map { it.station }
        playbackManager.playPreviousFavourite(favs, catalogStations.value)
    }

    fun toggleFavourite(station: RadioStation) {
        viewModelScope.launch {
            val added = repository.toggleFavourite(station)
            _userMessage.value = if (added) {
                "Added ${station.name} to Favourites"
            } else {
                "Removed ${station.name} from Favourites"
            }
        }
    }

    fun saveAndPlayCustomStation(
        name: String,
        frequency: String,
        band: RadioBand,
        district: String,
        language: String,
        streamUrl: String,
        alsoFavourite: Boolean
    ) {
        viewModelScope.launch {
            val saved = repository.saveCustomStation(
                name = name,
                frequency = frequency,
                band = band,
                district = district,
                language = language,
                streamUrl = streamUrl,
                alsoFavourite = alsoFavourite
            )
            playbackManager.playStation(saved)
            _userMessage.value = "Tuned & saved ${saved.name} (${saved.formattedFrequency})"
        }
    }

    fun deleteCustomStation(station: RadioStation) {
        viewModelScope.launch {
            repository.deleteCustomStation(station.id)
            _userMessage.value = "Deleted custom station ${station.name}"
        }
    }

    fun setHomeBandFilter(band: RadioBand?) {
        _homeBandFilter.value = band
    }

    fun setHomeLanguageFilter(language: String?) {
        _homeLanguageFilter.value = language
        if (language != null && !language.equals("Malayalam", ignoreCase = true)) {
            val canonical = LanguageCatalog.normalizeLanguage(language)
            if (_loadedLanguageStations.value[canonical].isNullOrEmpty()) {
                loadStationsForLanguage(canonical, silent = true)
            }
        }
    }

    fun selectLanguageDetail(language: String?) {
        _selectedLanguageDetail.value = language
        if (language != null) {
            val canonical = LanguageCatalog.normalizeLanguage(language)
            val alreadyLoaded = canonical.equals("Malayalam", ignoreCase = true) ||
                !_loadedLanguageStations.value[canonical].isNullOrEmpty()
            if (!alreadyLoaded) {
                loadStationsForLanguage(canonical, silent = true)
            }
        }
    }

    fun loadStationsForLanguage(language: String, silent: Boolean = false) {
        val canonical = LanguageCatalog.normalizeLanguage(language)
        if (!playbackState.value.isNetworkAvailable) {
            if (!silent) {
                _languageLoadState.value = LanguageLoadUiState.Error(
                    canonical,
                    "Offline — showing saved $canonical stations."
                )
            }
            return
        }
        viewModelScope.launch {
            _languageLoadState.value = LanguageLoadUiState.Loading(canonical)
            repository.loadStationsForLanguage(canonical)
                .onSuccess { stations ->
                    _loadedLanguageStations.value = _loadedLanguageStations.value + (canonical to stations)
                    _languageLoadState.value = LanguageLoadUiState.Loaded(canonical, stations.size)
                    if (!silent) {
                        _userMessage.value = "Loaded ${stations.size} live $canonical stations"
                    }
                }
                .onFailure { err ->
                    _languageLoadState.value = LanguageLoadUiState.Error(
                        canonical,
                        "Unable to load live $canonical directory: ${err.localizedMessage ?: "Network error"}"
                    )
                }
        }
    }

    fun setDiscoverQuery(query: String) {
        _discoverQuery.value = query
    }

    fun setDiscoverBandFilter(band: RadioBand?) {
        _discoverBandFilter.value = band
    }

    fun setDiscoverLanguageFilter(language: String?) {
        _discoverLanguageFilter.value = language
    }

    fun searchLiveDirectory() {
        val query = _discoverQuery.value.trim().ifBlank { "malayalam" }
        if (_discoverQuery.value.isBlank()) {
            _discoverQuery.value = query
        }
        if (!playbackState.value.isNetworkAvailable) {
            _directorySearchState.value = DirectorySearchUiState.Error(
                "Offline — connect to the internet to search Radio Garden & the global directory."
            )
            return
        }
        viewModelScope.launch {
            _directorySearchState.value = DirectorySearchUiState.Loading
            repository.searchLiveDirectory(query)
                .onSuccess { results ->
                    _directorySearchState.value = DirectorySearchUiState.Success(
                        results = results,
                        query = query,
                        sourceLabel = "Radio Garden & Global"
                    )
                    if (results.isEmpty()) {
                        _userMessage.value = "No live stations matched \"$query\""
                    } else {
                        _userMessage.value = "Found ${results.size} live stations for \"$query\""
                    }
                }
                .onFailure { err ->
                    _directorySearchState.value = DirectorySearchUiState.Error(
                        "Unable to reach live directory: ${err.localizedMessage ?: "Network error"}"
                    )
                }
        }
    }

    fun searchRadioGarden(queryOverride: String? = null) {
        val query = (queryOverride ?: _discoverQuery.value).trim().ifBlank { "Kochi" }
        _discoverQuery.value = query
        // Clear language filter when exploring a Radio Garden city so all city stations appear
        if (queryOverride != null) {
            _discoverLanguageFilter.value = null
            _discoverBandFilter.value = null
        }
        if (!playbackState.value.isNetworkAvailable) {
            _directorySearchState.value = DirectorySearchUiState.Error(
                "Offline — connect to the internet to browse Radio Garden stations."
            )
            return
        }
        viewModelScope.launch {
            _directorySearchState.value = DirectorySearchUiState.Loading
            repository.searchRadioGarden(query)
                .onSuccess { results ->
                    _directorySearchState.value = DirectorySearchUiState.Success(
                        results = results,
                        query = query,
                        sourceLabel = "Radio Garden"
                    )
                    if (results.isEmpty()) {
                        _userMessage.value = "No Radio Garden stations found for \"$query\""
                    } else {
                        _userMessage.value = "Loaded ${results.size} Radio Garden stations for \"$query\""
                    }
                }
                .onFailure { err ->
                    _directorySearchState.value = DirectorySearchUiState.Error(
                        "Unable to reach Radio Garden: ${err.localizedMessage ?: "Network error"}"
                    )
                }
        }
    }

    fun selectDistrict(district: String?) {
        _selectedDistrict.value = district
    }

    fun setFavouritesQuery(query: String) {
        _favouritesQuery.value = query
    }

    fun setFavouritesBandFilter(band: RadioBand?) {
        _favouritesBandFilter.value = band
    }

    fun setFavouriteSortOrder(order: FavouriteSortOrder) {
        viewModelScope.launch {
            preferences.setFavouriteSortOrder(order)
        }
    }

    fun expandPlayer() {
        if (playbackState.value.currentStation != null) {
            _isPlayerExpanded.value = true
        }
    }

    fun collapsePlayer() {
        _isPlayerExpanded.value = false
    }

    fun setAutoReconnect(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoReconnect(enabled) }
    }

    fun setRestoreLastStation(enabled: Boolean) {
        viewModelScope.launch { preferences.setRestoreLastStation(enabled) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        val updated = equalizerSettings.value.copy(enabled = enabled)
        viewModelScope.launch { preferences.saveEqualizerSettings(updated) }
    }

    fun selectEqualizerPreset(preset: EqualizerPreset) {
        val updated = equalizerSettings.value.copy(
            enabled = true,
            presetId = preset.id,
            bandGainsDb = preset.bandGainsDb,
            bassBoostStrength = preset.bassBoostStrength,
            virtualizerStrength = preset.virtualizerStrength,
            loudnessGainMb = preset.loudnessGainMb
        )
        viewModelScope.launch { preferences.saveEqualizerSettings(updated) }
        _userMessage.value = "Equalizer preset: ${preset.name}"
    }

    fun setEqualizerBandGain(bandIndex: Int, gainDb: Float) {
        val current = equalizerSettings.value
        val updatedBands = current.bandGainsDb.toMutableList()
        if (bandIndex in updatedBands.indices) {
            updatedBands[bandIndex] = gainDb.coerceIn(EqualizerSettings.MIN_GAIN_DB, EqualizerSettings.MAX_GAIN_DB)
        }
        val updated = current.copy(
            enabled = true,
            presetId = EqualizerCatalog.PRESET_CUSTOM,
            bandGainsDb = updatedBands
        )
        viewModelScope.launch { preferences.saveEqualizerSettings(updated) }
    }

    fun setEqualizerPreamp(preampDb: Float) {
        val updated = equalizerSettings.value.copy(enabled = true, preampDb = preampDb)
        viewModelScope.launch { preferences.saveEqualizerSettings(updated) }
    }

    fun setEqualizerBassBoost(strength: Int) {
        val updated = equalizerSettings.value.copy(enabled = true, bassBoostStrength = strength)
        viewModelScope.launch { preferences.saveEqualizerSettings(updated) }
    }

    fun setEqualizerVirtualizer(strength: Int) {
        val updated = equalizerSettings.value.copy(enabled = true, virtualizerStrength = strength)
        viewModelScope.launch { preferences.saveEqualizerSettings(updated) }
    }

    fun setEqualizerLoudness(gainMb: Int) {
        val updated = equalizerSettings.value.copy(enabled = true, loudnessGainMb = gainMb)
        viewModelScope.launch { preferences.saveEqualizerSettings(updated) }
    }

    fun setEqualizerLimiterEnabled(enabled: Boolean) {
        val updated = equalizerSettings.value.copy(limiterEnabled = enabled)
        viewModelScope.launch { preferences.saveEqualizerSettings(updated) }
    }

    fun resetEqualizerFlat() {
        val flat = EqualizerCatalog.findPreset(EqualizerCatalog.PRESET_FLAT)
        val updated = equalizerSettings.value.copy(
            presetId = flat.id,
            bandGainsDb = flat.bandGainsDb,
            preampDb = 0f,
            bassBoostStrength = 0,
            virtualizerStrength = 0,
            loudnessGainMb = 0,
            limiterEnabled = true
        )
        viewModelScope.launch { preferences.saveEqualizerSettings(updated) }
        _userMessage.value = "Equalizer reset to Flat Reference"
    }

    fun setSleepTimer(minutes: Int?) {
        playbackManager.setSleepTimer(minutes)
        _userMessage.value = if (minutes != null && minutes > 0) {
            "Sleep timer set for $minutes minutes"
        } else {
            "Sleep timer cleared"
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    companion object {
        fun factory(
            repository: RadioRepository,
            playbackManager: RadioPlaybackManager,
            preferences: RadioPreferences
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return RadioViewModel(repository, playbackManager, preferences) as T
            }
        }
    }
}
