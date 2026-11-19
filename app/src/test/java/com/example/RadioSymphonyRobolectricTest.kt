package com.example

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.RadioDatabase
import com.example.data.local.RadioPreferences
import com.example.data.model.CuratedStations
import com.example.data.model.EqualizerCatalog
import com.example.data.model.EqualizerSettings
import com.example.data.model.FavouriteSortOrder
import com.example.data.model.LanguageCatalog
import com.example.data.model.LanguageRegionGroup
import com.example.data.model.RadioBand
import com.example.data.remote.RadioBrowserApi
import com.example.data.remote.RadioBrowserStationDto
import com.example.data.remote.RadioGardenApi
import com.example.data.remote.RadioGardenChannelItem
import com.example.data.remote.RadioGardenContentSection
import com.example.data.remote.RadioGardenHit
import com.example.data.remote.RadioGardenHitSource
import com.example.data.remote.RadioGardenHitsWrapper
import com.example.data.remote.RadioGardenPageRef
import com.example.data.remote.RadioGardenPlaceChannelsData
import com.example.data.remote.RadioGardenPlaceChannelsResponse
import com.example.data.remote.RadioGardenRef
import com.example.data.remote.RadioGardenSearchResponse
import com.example.data.repository.RadioRepository
import com.example.playback.RadioPlaybackContracts
import com.example.util.BatteryOptimizationHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RadioSymphonyRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: RadioDatabase
    private lateinit var repository: RadioRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, RadioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RadioRepository(database.radioDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `favourites persist AM FM and SW Malayalam stations and toggle cleanly`() = runTest {
        val fmStation = CuratedStations.stations.first { it.band == RadioBand.FM }
        val amStation = CuratedStations.stations.first { it.band == RadioBand.AM }
        val swStation = CuratedStations.stations.first { it.band == RadioBand.SW }

        assertTrue(repository.toggleFavourite(fmStation, nowMs = 1_000L))
        assertTrue(repository.toggleFavourite(amStation, nowMs = 2_000L))
        assertTrue(repository.toggleFavourite(swStation, nowMs = 3_000L))

        val ids = repository.favouriteIds.first()
        assertEquals(setOf(fmStation.id, amStation.id, swStation.id), ids)

        assertFalse(repository.toggleFavourite(amStation))
        val remainingIds = repository.favouriteIds.first()
        assertEquals(setOf(fmStation.id, swStation.id), remainingIds)
    }

    @Test
    fun `favourites sort alphabetically and by recently played`() = runTest {
        val stationA = CuratedStations.stations.first { it.id == "ml_air_ananthapuri_fm" } // Akashvani Ananthapuri FM
        val stationC = CuratedStations.stations.first { it.id == "ml_club_fm_943" } // Club FM 94.3
        val stationR = CuratedStations.stations.first { it.id == "ml_radio_mango_919" } // Radio Mango 91.9 FM

        repository.addToFavourites(stationR, nowMs = 1_000L)
        repository.addToFavourites(stationC, nowMs = 2_000L)
        repository.addToFavourites(stationA, nowMs = 3_000L)

        // Play Radio Mango most recently
        repository.recordStationPlayed(stationR, nowMs = 10_000L)

        val alphabetical = repository.observeFavourites(FavouriteSortOrder.ALPHABETICAL).first()
        assertEquals(listOf(stationA.id, stationC.id, stationR.id), alphabetical.map { it.station.id })

        val recentlyPlayed = repository.observeFavourites(FavouriteSortOrder.RECENTLY_PLAYED).first()
        assertEquals(stationR.id, recentlyPlayed.first().station.id)
    }

    @Test
    fun `favourites search and band filter match offline Malayalam records`() = runTest {
        val fmStation = CuratedStations.stations.first { it.id == "ml_radio_mattoli_904" } // Wayanad FM
        val swStation = CuratedStations.stations.first { it.id == "ml_air_malayalam_sw" } // SW

        repository.addToFavourites(fmStation, nowMs = 1_000L)
        repository.addToFavourites(swStation, nowMs = 2_000L)

        val allFavs = repository.observeFavourites(FavouriteSortOrder.ALPHABETICAL).first()

        val wayanadMatches = repository.filterFavourites(allFavs, query = "Wayanad", bandFilter = null)
        assertEquals(1, wayanadMatches.size)
        assertEquals(fmStation.id, wayanadMatches.first().station.id)

        val swOnly = repository.filterFavourites(allFavs, query = "", bandFilter = RadioBand.SW)
        assertEquals(1, swOnly.size)
        assertEquals(swStation.id, swOnly.first().station.id)
    }

    @Test
    fun `last played station is persisted and restored`() = runTest {
        val station = CuratedStations.stations.first { it.id == "ml_club_fm_943" }
        repository.recordStationPlayed(station, nowMs = 50_000L)

        val restored = repository.getLastPlayedStationOnce()
        assertNotNull(restored)
        assertEquals(station.id, restored?.id)
        assertEquals(station.frequency, restored?.frequency)
        assertEquals(station.district, restored?.district)
        assertEquals(station.language, restored?.language)
    }

    @Test
    fun `resolveForServicePlayback restores stream URI after Media3 IPC strips localConfiguration`() {
        val station = CuratedStations.stations.first { it.id == "ml_radio_mango_919" }
        val sentFromController = RadioPlaybackContracts.toMediaItem(station)

        // Media3 MediaItem.toBundle() strips localConfiguration (the stream URI) across Binder IPC
        val receivedOverIpc = MediaItem.fromBundle(sentFromController.toBundle())
        assertEquals(null, receivedOverIpc.localConfiguration)

        val resolvedInService = RadioPlaybackContracts.resolveForServicePlayback(receivedOverIpc)
        assertNotNull(resolvedInService.localConfiguration)
        assertEquals(station.streamUrl, resolvedInService.localConfiguration?.uri?.toString())
    }

    @Test
    fun `radio browser DTO maps Malayalam station to Kerala district and FM band`() {
        val dto = RadioBrowserStationDto(
            stationUuid = "test-club-fm",
            name = "Club FM 94.3 Kochi",
            urlResolved = "https://eu10.fastcast4u.com/clubfmuae",
            country = "India",
            state = "Kerala",
            language = "malayalam",
            tags = "malayalam,hits,kerala",
            bitrate = 128,
            codec = "MP3",
            lastCheckOk = 1
        )
        val mapped = RadioBrowserApi.mapDtoToStation(dto)
        assertNotNull(mapped)
        assertEquals("Kochi", mapped?.district)
        assertEquals("Malayalam", mapped?.language)
        assertEquals(RadioBand.FM, mapped?.band)
        assertEquals("94.3 MHz", mapped?.frequency)
    }

    @Test
    fun `manual tuner locks onto matching FM frequency and seeks adjacent stations`() {
        val lockMatch = RadioRepository.findClosestStationForFrequency(
            stations = CuratedStations.stations,
            band = RadioBand.FM,
            targetFrequency = 91.9
        )
        assertTrue(lockMatch.isLocked)
        assertEquals("ml_radio_mango_919", lockMatch.station?.id)
        assertTrue(lockMatch.signalStrength >= 4)

        val seekNext = RadioRepository.seekAdjacentFrequency(
            stations = CuratedStations.stations,
            band = RadioBand.FM,
            currentFrequency = 91.9,
            forward = true
        )
        assertNotNull(seekNext)
        assertTrue((seekNext?.first ?: 0.0) > 91.9)
    }

    @Test
    fun `custom manual station persists in Room and syncs with favourites`() = runTest {
        val saved = repository.saveCustomStation(
            name = "My Thrissur Custom FM",
            frequency = "95.5 MHz",
            band = RadioBand.FM,
            district = "Thrissur",
            language = "Malayalam",
            streamUrl = "https://example.com/thrissur-live.mp3",
            alsoFavourite = true,
            nowMs = 99_000L
        )

        val customList = repository.customStations.first()
        assertEquals(1, customList.size)
        assertEquals(saved.id, customList.first().id)

        val favIds = repository.favouriteIds.first()
        assertTrue(saved.id in favIds)

        repository.deleteCustomStation(saved.id)
        assertTrue(repository.customStations.first().isEmpty())
        assertFalse(saved.id in repository.favouriteIds.first())
    }

    @Test
    fun `pickBestQualityStations selects highest bitrate AAC stream and assigns fallback URL`() {
        val lowQualityMp3 = RadioBrowserStationDto(
            stationUuid = "club-low",
            name = "Club FM 94.3 Kochi",
            urlResolved = "https://stream.example.com/clubfm-48k.mp3",
            country = "India",
            state = "Kerala",
            language = "malayalam",
            tags = "malayalam,hits",
            bitrate = 48,
            codec = "MP3",
            lastCheckOk = 1
        )
        val highQualityAac = RadioBrowserStationDto(
            stationUuid = "club-high",
            name = "Club FM 94.3 Kochi",
            urlResolved = "https://stream.example.com/clubfm-192k.aac",
            country = "India",
            state = "Kerala",
            language = "malayalam",
            tags = "malayalam,hits",
            bitrate = 192,
            codec = "AAC",
            lastCheckOk = 1
        )

        val picked = RadioBrowserApi.pickBestQualityStations(
            dtos = listOf(lowQualityMp3, highQualityAac),
            forceMalayalamIfBlank = true
        )

        assertEquals(1, picked.size)
        val best = picked.first()
        assertEquals("https://stream.example.com/clubfm-192k.aac", best.streamUrl)
        assertEquals("https://stream.example.com/clubfm-48k.mp3", best.fallbackStreamUrl)
        assertTrue(best.isHighQuality)
        assertTrue(best.qualityBadge.contains("192"))
    }

    @Test
    fun `radio garden parses search hits and place channels into playable live stations`() {
        val searchResponse = RadioGardenSearchResponse(
            hits = RadioGardenHitsWrapper(
                hits = listOf(
                    RadioGardenHit(
                        id = "hit-1",
                        source = RadioGardenHitSource(
                            type = "channel",
                            title = "Club FM 94.3",
                            subtitle = "Kochi, India",
                            page = RadioGardenPageRef(
                                url = "/listen/club-fm-94-3/AbC123Xy",
                                title = "Club FM 94.3",
                                place = RadioGardenRef(id = "kch1", title = "Kochi"),
                                country = RadioGardenRef(id = "in", title = "India")
                            )
                        )
                    ),
                    RadioGardenHit(
                        id = "hit-2",
                        source = RadioGardenHitSource(
                            type = "place",
                            title = "Kochi",
                            subtitle = "India",
                            page = RadioGardenPageRef(
                                url = "/visit/kochi/kchPlace9",
                                title = "Kochi",
                                subtitle = "India"
                            )
                        )
                    )
                )
            )
        )

        val (stations, places) = RadioGardenApi.parseSearchHits(searchResponse)
        assertEquals(1, stations.size)
        assertEquals("rg_AbC123Xy", stations.first().id)
        assertEquals("https://radio.garden/api/ara/content/listen/AbC123Xy/channel.mp3", stations.first().streamUrl)
        assertEquals("Kochi", stations.first().district)
        assertEquals("Malayalam", stations.first().language)
        assertEquals("94.3 MHz", stations.first().frequency)
        assertTrue(stations.first().isRadioGarden)
        assertEquals("GARDEN LIVE", stations.first().qualityBadge)

        assertEquals(1, places.size)
        assertEquals("kchPlace9", places.first().placeId)

        val placeChannelsResponse = RadioGardenPlaceChannelsResponse(
            data = RadioGardenPlaceChannelsData(
                title = "Kozhikode",
                subtitle = "India",
                content = listOf(
                    RadioGardenContentSection(
                        items = listOf(
                            RadioGardenChannelItem(
                                href = "/listen/akashvani-kozhikode/KzK7788",
                                title = "Akashvani Kozhikode 103.6 FM"
                            )
                        )
                    )
                )
            )
        )

        val cityStations = RadioGardenApi.parsePlaceChannels(placeChannelsResponse)
        assertEquals(1, cityStations.size)
        assertEquals("rg_KzK7788", cityStations.first().id)
        assertEquals("Kozhikode", cityStations.first().district)
        assertEquals("103.6 MHz", cityStations.first().frequency)
        assertEquals("https://radio.garden/api/ara/content/listen/KzK7788/channel.mp3", cityStations.first().streamUrl)
    }

    @Test
    fun `language catalog normalizes messy names and organizes languages by region`() {
        assertEquals("Malayalam", LanguageCatalog.normalizeLanguage("malayalam, english"))
        assertEquals("Spanish", LanguageCatalog.normalizeLanguage("castilian"))
        assertEquals("Hindi", LanguageCatalog.normalizeLanguage("  bollywood hindi "))
        assertEquals("English", LanguageCatalog.normalizeLanguage("British English"))
        assertEquals("Tamil", LanguageCatalog.normalizeLanguage("TAMIL"))

        val summaries = LanguageCatalog.buildLanguageSummaries(CuratedStations.stations)
        val malayalam = summaries.first { it.language == "Malayalam" }
        assertEquals("മലയാളം", malayalam.nativeName)
        assertEquals(LanguageRegionGroup.SOUTH_INDIAN, malayalam.regionGroup)
        assertTrue(malayalam.stationCount >= 20)

        val tamil = summaries.first { it.language == "Tamil" }
        assertEquals(LanguageRegionGroup.SOUTH_INDIAN, tamil.regionGroup)
        assertTrue(tamil.stationCount >= 2)

        val hindi = summaries.first { it.language == "Hindi" }
        assertEquals(LanguageRegionGroup.INDIAN, hindi.regionGroup)
        assertTrue(hindi.stationCount >= 2)

        val english = summaries.first { it.language == "English" }
        assertEquals(LanguageRegionGroup.INTERNATIONAL, english.regionGroup)
        assertTrue(english.stationCount >= 2)
    }

    @Test
    fun `battery compatibility helper returns manufacturer guide`() {
        val info = BatteryOptimizationHelper.getCompatibilityInfo(context)
        assertTrue(info.recommendationTitle.isNotBlank())
        assertTrue(info.steps.isNotEmpty())
    }

    @Test
    fun `equalizer catalog presets and normalization clamp values and interpolate curves`() {
        assertEquals(10, EqualizerCatalog.bands.size)
        assertTrue(EqualizerCatalog.presets.size >= 10)
        assertTrue(EqualizerCatalog.presets.all { it.bandGainsDb.size == EqualizerCatalog.BAND_COUNT })

        val outOfBounds = EqualizerSettings(
            enabled = true,
            presetId = EqualizerCatalog.PRESET_CUSTOM,
            bandGainsDb = listOf(-30f, 25f, 4f), // short & out of bounds
            preampDb = 18f,
            bassBoostStrength = 2500,
            virtualizerStrength = -50,
            loudnessGainMb = 5000,
            limiterEnabled = true
        ).normalized()

        assertEquals(EqualizerCatalog.BAND_COUNT, outOfBounds.bandGainsDb.size)
        assertEquals(-12f, outOfBounds.bandGainsDb[0], 0.001f)
        assertEquals(12f, outOfBounds.bandGainsDb[1], 0.001f)
        assertEquals(4f, outOfBounds.bandGainsDb[2], 0.001f)
        assertEquals(0f, outOfBounds.bandGainsDb[9], 0.001f)
        assertEquals(6f, outOfBounds.preampDb, 0.001f)
        assertEquals(1000, outOfBounds.bassBoostStrength)
        assertEquals(0, outOfBounds.virtualizerStrength)
        assertEquals(1000, outOfBounds.loudnessGainMb)

        // Linear interpolation between 1kHz (index 5 = 2.0 dB) and 2kHz (index 6 = 6.0 dB) at 1500Hz -> 4.0 dB
        val gains = listOf(0f, 0f, 0f, 0f, 0f, 2f, 6f, 0f, 0f, 0f)
        assertEquals(4f, EqualizerCatalog.interpolateGainForFrequency(1500, gains), 0.01f)
        assertEquals(0f, EqualizerCatalog.interpolateGainForFrequency(10, gains), 0.01f)
        assertEquals(0f, EqualizerCatalog.interpolateGainForFrequency(24000, gains), 0.01f)
    }

    @Test
    fun `equalizer preferences persist and restore custom 10-band settings`() = runTest {
        val prefs = RadioPreferences(context)
        val custom = EqualizerSettings(
            enabled = true,
            presetId = EqualizerCatalog.PRESET_CUSTOM,
            bandGainsDb = listOf(4.5f, 3.0f, 1.5f, 0f, -1.0f, 1.0f, 2.5f, 3.5f, 4.0f, 2.0f),
            preampDb = 1.5f,
            bassBoostStrength = 420,
            virtualizerStrength = 310,
            loudnessGainMb = 250,
            limiterEnabled = true
        )

        prefs.saveEqualizerSettings(custom)
        val restored = prefs.equalizerSettings.first()

        assertTrue(restored.enabled)
        assertEquals(EqualizerCatalog.PRESET_CUSTOM, restored.presetId)
        assertEquals(custom.bandGainsDb, restored.bandGainsDb)
        assertEquals(1.5f, restored.preampDb, 0.01f)
        assertEquals(420, restored.bassBoostStrength)
        assertEquals(310, restored.virtualizerStrength)
        assertEquals(250, restored.loudnessGainMb)
        assertTrue(restored.limiterEnabled)
    }
}
