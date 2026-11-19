package com.example.data.repository

import com.example.data.local.CachedStationEntity
import com.example.data.local.CustomStationEntity
import com.example.data.local.FavouriteStationEntity
import com.example.data.local.PlaybackStateEntity
import com.example.data.local.RadioDao
import com.example.data.model.CuratedStations
import com.example.data.model.DistrictSummary
import com.example.data.model.FavouriteSortOrder
import com.example.data.model.FavouriteStationItem
import com.example.data.model.LanguageCatalog
import com.example.data.model.LanguageSummary
import com.example.data.model.RadioBand
import com.example.data.model.RadioStation
import com.example.data.remote.RadioBrowserApi
import com.example.data.remote.RadioBrowserStationDto
import com.example.data.remote.RadioGardenApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class FrequencyMatchResult(
    val station: RadioStation?,
    val signalStrength: Int, // 0..5 bars
    val isLocked: Boolean
)

class RadioRepository(
    private val radioDao: RadioDao,
    private val apiMirrors: List<RadioBrowserApi> = RadioBrowserApi.createMirrors(),
    private val radioGardenApi: RadioGardenApi = RadioGardenApi.create()
) {
    val curatedStations: List<RadioStation> = CuratedStations.stations

    val customStations: Flow<List<RadioStation>> = radioDao.observeCustomStations()
        .map { entities -> entities.map { it.toStation() } }

    val cachedLiveStations: Flow<List<RadioStation>> = radioDao.observeCachedStations()
        .map { entities -> entities.map { it.toStation() } }

    /**
     * Unified catalog combining user custom stations, verified live Malayalam stations
     * cached in Room, and the offline-ready curated Akashvani & Kerala list.
     */
    val allCatalogStations: Flow<List<RadioStation>> = combine(
        customStations,
        cachedLiveStations
    ) { custom, liveCached ->
        mergeCatalog(custom, liveCached, curatedStations)
    }

    val favouriteIds: Flow<Set<String>> = radioDao.observeFavouriteIds()
        .map { it.toSet() }

    val lastPlayedStation: Flow<RadioStation?> = radioDao.observeLastPlayedState()
        .map { it?.toStation() }

    fun observeFavourites(sortOrder: FavouriteSortOrder): Flow<List<FavouriteStationItem>> {
        val source = when (sortOrder) {
            FavouriteSortOrder.ALPHABETICAL -> radioDao.observeFavouritesAlphabetical()
            FavouriteSortOrder.RECENTLY_PLAYED -> radioDao.observeFavouritesRecentlyPlayed()
        }
        return source.map { entities -> entities.map { it.toDomainItem() } }
    }

    suspend fun getAllFavouriteStationsOnce(): List<RadioStation> {
        return radioDao.getAllFavouritesOnce().map { it.toDomainItem().station }
    }

    suspend fun isFavourite(stationId: String): Boolean {
        return radioDao.getFavouriteById(stationId) != null
    }

    suspend fun addToFavourites(station: RadioStation, nowMs: Long = System.currentTimeMillis()) {
        val existing = radioDao.getFavouriteById(station.id)
        val entity = FavouriteStationEntity.fromStation(
            station = station,
            addedAt = existing?.addedAt ?: nowMs,
            lastPlayedAt = existing?.lastPlayedAt ?: 0L
        )
        radioDao.upsertFavourite(entity)
    }

    suspend fun removeFromFavourites(stationId: String) {
        radioDao.deleteFavouriteById(stationId)
    }

    /**
     * Toggles favourite status and returns true if the station is now in favourites.
     */
    suspend fun toggleFavourite(station: RadioStation, nowMs: Long = System.currentTimeMillis()): Boolean {
        val existing = radioDao.getFavouriteById(station.id)
        return if (existing != null) {
            radioDao.deleteFavouriteById(station.id)
            false
        } else {
            radioDao.upsertFavourite(
                FavouriteStationEntity.fromStation(
                    station = station,
                    addedAt = nowMs,
                    lastPlayedAt = 0L
                )
            )
            true
        }
    }

    suspend fun saveCustomStation(
        name: String,
        frequency: String,
        band: RadioBand,
        district: String,
        language: String,
        streamUrl: String,
        alsoFavourite: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): RadioStation {
        val cleanName = name.trim()
        val cleanUrl = streamUrl.trim()
        val cleanDistrict = district.trim().ifBlank { "Kerala" }
        val cleanLanguage = language.trim().ifBlank { "Malayalam" }
        val cleanFreq = frequency.trim().ifBlank { "Live ${band.label}" }

        val words = cleanName.split(" ").filter { it.isNotBlank() }
        val logoCode = when {
            words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase()
            cleanName.length >= 2 -> cleanName.take(2).uppercase()
            else -> band.label
        }
        val color = when (band) {
            RadioBand.FM -> 0xFFF5A623L
            RadioBand.AM -> 0xFF34D399L
            RadioBand.SW -> 0xFF38BDF8L
        }

        val station = RadioStation(
            id = "custom_${nowMs}_${abs(cleanUrl.hashCode())}",
            name = cleanName,
            frequency = cleanFreq,
            band = band,
            district = cleanDistrict,
            language = cleanLanguage,
            genre = "Custom Tuned",
            streamUrl = cleanUrl,
            accentColorHex = color,
            logoCode = logoCode,
            tagline = "Custom tuned station • $cleanDistrict"
        )

        radioDao.upsertCustomStation(CustomStationEntity.fromStation(station, createdAt = nowMs))
        if (alsoFavourite) {
            addToFavourites(station, nowMs = nowMs)
        }
        return station
    }

    suspend fun deleteCustomStation(stationId: String) {
        radioDao.deleteCustomStationById(stationId)
        radioDao.deleteFavouriteById(stationId)
    }

    suspend fun recordStationPlayed(station: RadioStation, nowMs: Long = System.currentTimeMillis()) {
        radioDao.upsertLastPlayedState(PlaybackStateEntity.fromStation(station, nowMs))
        radioDao.updateFavouriteLastPlayed(station.id, nowMs)
    }

    suspend fun getLastPlayedStationOnce(): RadioStation? {
        return radioDao.getLastPlayedStateOnce()?.toStation()
    }

    /**
     * Fetches ALL available verified Malayalam & Kerala radio stations from both the
     * Radio-Browser network and Radio Garden (channels + Kerala city pages), picks the
     * highest audio quality stream for every station (with Radio Garden/2nd-best stored
     * as fallback), and caches them locally in Room.
     */
    suspend fun refreshAllMalayalamStations(): Result<List<RadioStation>> = withContext(Dispatchers.IO) {
        coroutineScope {
            val radioGardenDeferred = async {
                runCatching { fetchMalayalamRadioGardenStations() }.getOrDefault(emptyList())
            }

            var radioBrowserStations: List<RadioStation> = emptyList()
            var lastError: Throwable? = null

            for (api in apiMirrors) {
                val attempt = runCatching {
                    val combinedDtos: List<RadioBrowserStationDto> = coroutineScope {
                        val byLang = async { runCatching { api.getStationsByLanguage("malayalam", limit = 500) }.getOrDefault(emptyList()) }
                        val byLangSearch = async { runCatching { api.searchStationsByLanguageAndCountry(language = "malayalam", limit = 500) }.getOrDefault(emptyList()) }
                        val byTagMal = async { runCatching { api.getStationsByTag("malayalam", limit = 400) }.getOrDefault(emptyList()) }
                        val byTagKerala = async { runCatching { api.getStationsByTag("kerala", limit = 300) }.getOrDefault(emptyList()) }
                        val byNameMal = async { runCatching { api.searchStations(name = "malayalam", limit = 300) }.getOrDefault(emptyList()) }
                        val byNameKerala = async { runCatching { api.searchStations(name = "kerala", limit = 200) }.getOrDefault(emptyList()) }
                        val byClubFm = async { runCatching { api.searchStations(name = "club fm", limit = 30) }.getOrDefault(emptyList()) }
                        val byMango = async { runCatching { api.searchStations(name = "radio mango", limit = 30) }.getOrDefault(emptyList()) }
                        val bySuno = async { runCatching { api.searchStations(name = "suno", limit = 25) }.getOrDefault(emptyList()) }
                        val byHit = async { runCatching { api.searchStations(name = "hit 96.7", limit = 25) }.getOrDefault(emptyList()) }
                        val byGold = async { runCatching { api.searchStations(name = "gold 101.3", limit = 20) }.getOrDefault(emptyList()) }
                        val byMazhavil = async { runCatching { api.searchStations(name = "mazhavil", limit = 20) }.getOrDefault(emptyList()) }
                        val byKochi = async { runCatching { api.searchStations(name = "kochi", limit = 40) }.getOrDefault(emptyList()) }
                        val byMalabar = async { runCatching { api.searchStations(name = "malabar", limit = 30) }.getOrDefault(emptyList()) }
                        val byGanam = async { runCatching { api.searchStations(name = "ganam", limit = 30) }.getOrDefault(emptyList()) }
                        val byAir = async { runCatching { api.searchStations(name = "air ", limit = 150) }.getOrDefault(emptyList()) }
                        val byAkashvani = async { runCatching { api.searchStations(name = "akashvani", limit = 100) }.getOrDefault(emptyList()) }

                        val malayalamOrKeralaFilter: (RadioBrowserStationDto) -> Boolean = { dto ->
                            val text = "${dto.name} ${dto.language} ${dto.tags} ${dto.state}".lowercase()
                            text.contains("malayalam") ||
                                text.contains("kerala") ||
                                text.contains("kochi") ||
                                text.contains("cochin") ||
                                text.contains("trivandrum") ||
                                text.contains("thiruvananthapuram") ||
                                text.contains("kozhikode") ||
                                text.contains("calicut") ||
                                text.contains("thrissur") ||
                                text.contains("kannur") ||
                                text.contains("manjeri") ||
                                text.contains("devikulam") ||
                                text.contains("wayanad") ||
                                text.contains("kottayam") ||
                                text.contains("kollam") ||
                                text.contains("malabar") ||
                                text.contains("club fm") ||
                                text.contains("radio mango") ||
                                text.contains("suno") ||
                                text.contains("hit 96.7") ||
                                text.contains("gold 101.3") ||
                                text.contains("mazhavil") ||
                                text.contains("ganam")
                        }

                        byLang.await() +
                            byLangSearch.await() +
                            byTagMal.await() +
                            byTagKerala.await() +
                            byNameMal.await() +
                            byNameKerala.await() +
                            byClubFm.await().filter(malayalamOrKeralaFilter) +
                            byMango.await() +
                            bySuno.await().filter(malayalamOrKeralaFilter) +
                            byHit.await().filter(malayalamOrKeralaFilter) +
                            byGold.await().filter(malayalamOrKeralaFilter) +
                            byMazhavil.await() +
                            byKochi.await().filter(malayalamOrKeralaFilter) +
                            byMalabar.await().filter(malayalamOrKeralaFilter) +
                            byGanam.await().filter(malayalamOrKeralaFilter) +
                            byAir.await().filter(malayalamOrKeralaFilter) +
                            byAkashvani.await().filter(malayalamOrKeralaFilter)
                    }

                    RadioBrowserApi.pickBestQualityStations(
                        dtos = combinedDtos,
                        forceMalayalamIfBlank = true
                    )
                }

                if (attempt.isSuccess && attempt.getOrDefault(emptyList()).isNotEmpty()) {
                    radioBrowserStations = attempt.getOrDefault(emptyList())
                    break
                } else {
                    lastError = attempt.exceptionOrNull()
                }
            }

            val radioGardenStations = radioGardenDeferred.await()
            val merged = mergeRadioBrowserAndGarden(radioBrowserStations, radioGardenStations)

            if (merged.isEmpty()) {
                return@coroutineScope Result.failure(
                    lastError ?: IllegalStateException("Unable to reach Radio-Browser or Radio Garden")
                )
            }

            val now = System.currentTimeMillis()
            val entities = merged.mapIndexed { idx, station ->
                CachedStationEntity.fromStation(station, rankOrder = idx, updatedAt = now)
            }
            radioDao.replaceCachedStations(entities)
            Result.success(merged)
        }
    }

    /**
     * Fetches Malayalam & Kerala radio stations directly from Radio Garden by querying
     * Malayalam keywords and major Kerala cities, then expanding matched Kerala city pages.
     */
    suspend fun fetchMalayalamRadioGardenStations(): List<RadioStation> = withContext(Dispatchers.IO) {
        coroutineScope {
            val queries = listOf(
                "malayalam",
                "kerala",
                "kochi",
                "thiruvananthapuram",
                "kozhikode",
                "thrissur",
                "kannur",
                "kottayam",
                "kollam",
                "wayanad",
                "malappuram",
                "alappuzha"
            )

            val searchResults = queries.map { q ->
                async {
                    runCatching {
                        RadioGardenApi.parseSearchHits(radioGardenApi.search(q), forceMalayalamIfKerala = true)
                    }.getOrDefault(emptyList<RadioStation>() to emptyList())
                }
            }.awaitAll()

            val directStations = searchResults.flatMap { it.first }
            val keralaPlaces = searchResults.flatMap { it.second }
                .distinctBy { it.placeId }
                .filter { place ->
                    val text = "${place.title} ${place.subtitle}".lowercase()
                    text.contains("india") || text.contains("kerala")
                }
                .take(12)

            val placeStations = keralaPlaces.map { place ->
                async {
                    runCatching {
                        RadioGardenApi.parsePlaceChannels(
                            response = radioGardenApi.getPlaceChannels(place.placeId),
                            fallbackPlaceTitle = place.title,
                            fallbackCountryTitle = place.subtitle,
                            forceMalayalamIfKerala = true
                        )
                    }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten()

            (directStations + placeStations).distinctBy { it.id }
        }
    }

    /**
     * Searches Radio Garden live by station name, city, or country, and expands matching
     * city pages so users can listen to all stations from any Radio Garden location worldwide.
     */
    suspend fun searchRadioGarden(query: String): Result<List<RadioStation>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim().ifBlank { "Kochi" }
        runCatching {
            coroutineScope {
                val (directStations, matchedPlaces) = RadioGardenApi.parseSearchHits(
                    response = radioGardenApi.search(trimmed),
                    forceMalayalamIfKerala = true
                )

                val cityStations = matchedPlaces.take(3).map { place ->
                    async {
                        runCatching {
                            RadioGardenApi.parsePlaceChannels(
                                response = radioGardenApi.getPlaceChannels(place.placeId),
                                fallbackPlaceTitle = place.title,
                                fallbackCountryTitle = place.subtitle,
                                forceMalayalamIfKerala = true
                            )
                        }.getOrDefault(emptyList())
                    }
                }.awaitAll().flatten()

                (directStations + cityStations).distinctBy { it.id }
            }
        }
    }

    suspend fun searchLiveDirectory(query: String): Result<List<RadioStation>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim().ifBlank { "malayalam" }
        coroutineScope {
            val gardenDeferred = async { searchRadioGarden(trimmed).getOrDefault(emptyList()) }

            var rbResults: List<RadioStation> = emptyList()
            var lastError: Throwable? = null
            for (api in apiMirrors) {
                val attempt = runCatching {
                    val dtos = api.searchStations(name = trimmed, limit = 100)
                    RadioBrowserApi.pickBestQualityStations(dtos)
                }
                if (attempt.isSuccess) {
                    rbResults = attempt.getOrDefault(emptyList())
                    break
                }
                lastError = attempt.exceptionOrNull()
            }

            val gardenResults = gardenDeferred.await()
            val merged = mergeRadioBrowserAndGarden(rbResults, gardenResults)
            if (merged.isNotEmpty() || lastError == null) {
                Result.success(merged)
            } else {
                Result.failure(lastError)
            }
        }
    }

    private fun mergeRadioBrowserAndGarden(
        radioBrowser: List<RadioStation>,
        radioGarden: List<RadioStation>
    ): List<RadioStation> {
        fun normalizeKey(name: String): String = name.lowercase()
            .replace(Regex("\\b(hd|hq|high|low|mobile|stream|live|radio|fm|am)\\b"), "")
            .replace(Regex("[^a-z0-9]"), "")
            .ifBlank { name.lowercase().replace(Regex("[^a-z0-9]"), "") }

        val gardenByName = radioGarden.associateBy { normalizeKey(it.name) }
        val usedGardenIds = HashSet<String>()

        val enrichedBrowser = radioBrowser.map { rb ->
            val key = normalizeKey(rb.name)
            val rgMatch = gardenByName[key]
            if (rgMatch != null) {
                usedGardenIds.add(rgMatch.id)
                if (rb.fallbackStreamUrl.isBlank()) {
                    rb.copy(fallbackStreamUrl = rgMatch.streamUrl)
                } else {
                    rb
                }
            } else {
                rb
            }
        }

        val exclusiveGarden = radioGarden.filter { it.id !in usedGardenIds }
        return enrichedBrowser + exclusiveGarden
    }

    /**
     * Fetches ALL live stations for any selected language (e.g. Malayalam, Tamil, Hindi,
     * English, Arabic, Spanish, Japanese, etc.) from both Radio-Browser and Radio Garden
     * in parallel, ranked by highest audio quality with automatic fallback URLs.
     */
    suspend fun loadStationsForLanguage(language: String): Result<List<RadioStation>> = withContext(Dispatchers.IO) {
        val canonical = LanguageCatalog.normalizeLanguage(language)
        if (canonical.equals("Malayalam", ignoreCase = true)) {
            return@withContext refreshAllMalayalamStations()
        }

        val query = LanguageCatalog.findEntry(canonical)?.apiQuery ?: canonical.lowercase()
        coroutineScope {
            val gardenDeferred = async {
                searchRadioGarden(query).getOrDefault(emptyList()).map { st ->
                    if (st.language == "Multilingual") st.copy(language = canonical) else st
                }
            }

            var rbResults: List<RadioStation> = emptyList()
            var lastError: Throwable? = null
            for (api in apiMirrors) {
                val attempt = runCatching {
                    val combinedDtos = coroutineScope {
                        val byLang = async { runCatching { api.getStationsByLanguage(query, limit = 250) }.getOrDefault(emptyList()) }
                        val byTag = async { runCatching { api.getStationsByTag(query, limit = 150) }.getOrDefault(emptyList()) }
                        val byName = async { runCatching { api.searchStations(name = query, limit = 100) }.getOrDefault(emptyList()) }
                        byLang.await() + byTag.await() + byName.await()
                    }
                    RadioBrowserApi.pickBestQualityStations(combinedDtos)
                        .map { st ->
                            val norm = LanguageCatalog.normalizeLanguage(st.language)
                            if (norm == "Multilingual") st.copy(language = canonical) else st.copy(language = norm)
                        }
                }
                if (attempt.isSuccess && attempt.getOrDefault(emptyList()).isNotEmpty()) {
                    rbResults = attempt.getOrDefault(emptyList())
                    break
                } else {
                    lastError = attempt.exceptionOrNull()
                }
            }

            val gardenResults = gardenDeferred.await()
            val merged = mergeRadioBrowserAndGarden(rbResults, gardenResults)
                .filter { LanguageCatalog.normalizeLanguage(it.language).equals(canonical, ignoreCase = true) || rbResults.isEmpty() }

            if (merged.isNotEmpty() || lastError == null) {
                Result.success(merged)
            } else {
                Result.failure(lastError)
            }
        }
    }

    fun getLanguageSummaries(stations: List<RadioStation>): List<LanguageSummary> {
        return LanguageCatalog.buildLanguageSummaries(stations)
    }

    fun getDistrictSummaries(stations: List<RadioStation>): List<DistrictSummary> {
        return stations.distinctBy { it.id }
            .groupBy { it.district }
            .map { (district, list) ->
                DistrictSummary(
                    district = district,
                    stationCount = list.size,
                    bands = list.map { it.band }.distinct().sortedBy { it.ordinal },
                    languages = list.map { LanguageCatalog.normalizeLanguage(it.language) }.distinct().sorted(),
                    accentColorHex = list.first().accentColorHex
                )
            }
            .sortedByDescending { it.stationCount }
    }

    fun getAvailableLanguages(stations: List<RadioStation>): List<String> {
        val counts = stations
            .distinctBy { it.id }
            .groupingBy { LanguageCatalog.normalizeLanguage(it.language) }
            .eachCount()
        val sorted = counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
        return if ("Malayalam" in sorted) {
            listOf("Malayalam") + sorted.filterNot { it == "Malayalam" }
        } else {
            sorted
        }
    }

    fun filterFavourites(
        favourites: List<FavouriteStationItem>,
        query: String,
        bandFilter: RadioBand?
    ): List<FavouriteStationItem> {
        val q = query.trim().lowercase()
        return favourites.filter { item ->
            val station = item.station
            val matchesBand = bandFilter == null || station.band == bandFilter
            val matchesQuery = q.isEmpty() ||
                station.name.lowercase().contains(q) ||
                station.frequency.lowercase().contains(q) ||
                station.district.lowercase().contains(q) ||
                station.language.lowercase().contains(q) ||
                station.band.label.lowercase().contains(q) ||
                station.genre.lowercase().contains(q)
            matchesBand && matchesQuery
        }
    }

    private fun mergeCatalog(
        custom: List<RadioStation>,
        liveCached: List<RadioStation>,
        curated: List<RadioStation>
    ): List<RadioStation> {
        val seenUrls = HashSet<String>()
        val seenNames = HashSet<String>()
        val result = ArrayList<RadioStation>(custom.size + liveCached.size + curated.size)

        fun tryAdd(station: RadioStation, allowDuplicateName: Boolean = false) {
            val normUrl = station.streamUrl.lowercase().trimEnd('/')
            val normName = station.name.lowercase().replace(Regex("[^a-z0-9]"), "")
            if (normUrl !in seenUrls && (allowDuplicateName || normName !in seenNames)) {
                seenUrls.add(normUrl)
                seenNames.add(normName)
                result.add(station)
            }
        }

        custom.forEach { tryAdd(it, allowDuplicateName = true) }
        liveCached.forEach { tryAdd(it) }
        curated.forEach { tryAdd(it) }
        return result
    }

    companion object {
        private val FREQ_NUMBER_REGEX = Regex("(\\d+(?:\\.\\d+)?)")

        fun parseNumericFrequency(station: RadioStation): Double? {
            if (station.frequency.contains("kbps", ignoreCase = true)) return null
            val value = FREQ_NUMBER_REGEX.find(station.frequency)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
                ?: return null
            return when (station.band) {
                RadioBand.FM -> value.takeIf { it in 87.5..108.0 }
                RadioBand.AM -> value.takeIf { it in 530.0..1710.0 }
                RadioBand.SW -> value.takeIf { it in 2.3..26.1 }
            }
        }

        fun findClosestStationForFrequency(
            stations: List<RadioStation>,
            band: RadioBand,
            targetFrequency: Double
        ): FrequencyMatchResult {
            val lockTolerance = when (band) {
                RadioBand.FM -> 0.15
                RadioBand.AM -> 10.0
                RadioBand.SW -> 0.05
            }
            val weakTolerance = when (band) {
                RadioBand.FM -> 0.6
                RadioBand.AM -> 36.0
                RadioBand.SW -> 0.25
            }

            val candidates = stations
                .filter { it.band == band }
                .mapNotNull { st -> parseNumericFrequency(st)?.let { st to abs(it - targetFrequency) } }
                .sortedBy { it.second }

            val best = candidates.firstOrNull()
                ?: return FrequencyMatchResult(station = null, signalStrength = 0, isLocked = false)

            val (station, diff) = best
            return when {
                diff <= lockTolerance -> FrequencyMatchResult(
                    station = station,
                    signalStrength = if (diff <= lockTolerance * 0.4) 5 else 4,
                    isLocked = true
                )
                diff <= weakTolerance -> FrequencyMatchResult(
                    station = station,
                    signalStrength = if (diff <= weakTolerance * 0.5) 3 else 2,
                    isLocked = false
                )
                else -> FrequencyMatchResult(
                    station = null,
                    signalStrength = if (diff <= weakTolerance * 2) 1 else 0,
                    isLocked = false
                )
            }
        }

        fun seekAdjacentFrequency(
            stations: List<RadioStation>,
            band: RadioBand,
            currentFrequency: Double,
            forward: Boolean
        ): Pair<Double, RadioStation>? {
            val tunedList = stations
                .filter { it.band == band }
                .mapNotNull { st -> parseNumericFrequency(st)?.let { it to st } }
                .sortedBy { it.first }
                .distinctBy { it.first }

            if (tunedList.isEmpty()) return null

            val epsilon = when (band) {
                RadioBand.FM -> 0.05
                RadioBand.AM -> 4.0
                RadioBand.SW -> 0.002
            }

            return if (forward) {
                tunedList.firstOrNull { it.first > currentFrequency + epsilon } ?: tunedList.first()
            } else {
                tunedList.lastOrNull { it.first < currentFrequency - epsilon } ?: tunedList.last()
            }
        }
    }
}
