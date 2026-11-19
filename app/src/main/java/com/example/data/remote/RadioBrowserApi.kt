package com.example.data.remote

import com.example.data.model.LanguageCatalog
import com.example.data.model.RadioBand
import com.example.data.model.RadioStation
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@JsonClass(generateAdapter = true)
data class RadioBrowserStationDto(
    @Json(name = "stationuuid") val stationUuid: String = "",
    @Json(name = "name") val name: String = "",
    @Json(name = "url_resolved") val urlResolved: String = "",
    @Json(name = "url") val url: String = "",
    @Json(name = "country") val country: String = "",
    @Json(name = "countrycode") val countryCode: String = "",
    @Json(name = "state") val state: String = "",
    @Json(name = "language") val language: String = "",
    @Json(name = "tags") val tags: String = "",
    @Json(name = "bitrate") val bitrate: Int = 0,
    @Json(name = "codec") val codec: String = "",
    @Json(name = "hls") val hls: Int = 0,
    @Json(name = "clickcount") val clickCount: Int = 0,
    @Json(name = "votes") val votes: Int = 0,
    @Json(name = "lastcheckok") val lastCheckOk: Int = 1
)

interface RadioBrowserApi {

    @GET("json/stations/bylanguage/{language}")
    suspend fun getStationsByLanguage(
        @Path("language") language: String,
        @Query("limit") limit: Int = 500,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("order") order: String = "bitrate",
        @Query("reverse") reverse: Boolean = true
    ): List<RadioBrowserStationDto>

    @GET("json/stations/bytag/{tag}")
    suspend fun getStationsByTag(
        @Path("tag") tag: String,
        @Query("limit") limit: Int = 400,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("order") order: String = "bitrate",
        @Query("reverse") reverse: Boolean = true
    ): List<RadioBrowserStationDto>

    @GET("json/stations/search")
    suspend fun searchStations(
        @Query("name") name: String,
        @Query("limit") limit: Int = 200,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("order") order: String = "bitrate",
        @Query("reverse") reverse: Boolean = true
    ): List<RadioBrowserStationDto>

    @GET("json/stations/search")
    suspend fun searchStationsByLanguageAndCountry(
        @Query("language") language: String,
        @Query("countrycode") countryCode: String? = null,
        @Query("limit") limit: Int = 500,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("order") order: String = "bitrate",
        @Query("reverse") reverse: Boolean = true
    ): List<RadioBrowserStationDto>

    companion object {
        private val MIRRORS = listOf(
            "https://de1.api.radio-browser.info/",
            "https://de2.api.radio-browser.info/",
            "https://at1.api.radio-browser.info/",
            "https://nl1.api.radio-browser.info/"
        )

        private val palette = listOf(
            0xFFF5A623L,
            0xFFE85D5AL,
            0xFF38BDF8L,
            0xFF34D399L,
            0xFFA78BFAL,
            0xFFFB923CL,
            0xFF2DD4BFL,
            0xFFF472B6L
        )

        val sharedHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "RadioSymphonyAndroid/1.2")
                        .build()
                    chain.proceed(request)
                }
                .build()
        }

        private val sharedMoshi: Moshi by lazy {
            Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
        }

        fun create(baseUrl: String = MIRRORS.first()): RadioBrowserApi {
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(sharedHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(sharedMoshi))
                .build()
                .create(RadioBrowserApi::class.java)
        }

        fun createMirrors(): List<RadioBrowserApi> = MIRRORS.map { create(it) }

        /**
         * Scores a stream's perceptual audio quality so duplicate stations always pick
         * the highest-fidelity stream first (e.g., 192 kbps AAC / FLAC / OPUS / HLS HD
         * beats low-bitrate 48 kbps MP3) and keeps the 2nd-best stream as fallback.
         */
        fun computeAudioQualityScore(dto: RadioBrowserStationDto): Double {
            val url = dto.urlResolved.ifBlank { dto.url }.trim().lowercase()
            val codec = dto.codec.trim().uppercase()
            val effectiveBitrate = when {
                dto.bitrate > 0 -> dto.bitrate.toDouble()
                dto.hls == 1 || url.contains(".m3u8") -> 128.0
                codec.contains("AAC") -> 96.0
                else -> 64.0
            }

            // Modern codecs achieve higher clarity per kbps than legacy MP3
            val codecMultiplier = when {
                codec.contains("FLAC") -> 2.0
                codec.contains("OPUS") -> 1.45
                codec.contains("AAC+") || codec.contains("HE-AAC") -> 1.38
                codec.contains("AAC") -> 1.30
                codec.contains("OGG") -> 1.25
                dto.hls == 1 || url.contains(".m3u8") -> 1.22
                else -> 1.0
            }

            val httpsBonus = if (url.startsWith("https://")) 15.0 else 0.0
            val healthBonus = if (dto.lastCheckOk == 1) 50.0 else -500.0
            return (effectiveBitrate * codecMultiplier) + httpsBonus + healthBonus
        }

        /**
         * Groups duplicate stations by normalized name, selects the highest audio quality
         * stream as primary, assigns the next-best distinct URL as automatic fallback,
         * and orders stations by audio quality and popularity.
         */
        fun pickBestQualityStations(
            dtos: List<RadioBrowserStationDto>,
            forceMalayalamIfBlank: Boolean = false
        ): List<RadioStation> {
            val validDtos = dtos
                .filter { it.lastCheckOk != 0 }
                .filter {
                    val u = it.urlResolved.ifBlank { it.url }.trim()
                    it.name.isNotBlank() && (u.startsWith("http://") || u.startsWith("https://"))
                }
                .distinctBy { it.urlResolved.ifBlank { it.url }.trim().lowercase().trimEnd('/') }

            val groupedByName = validDtos.groupBy { dto ->
                dto.name.lowercase()
                    .replace(Regex("\\b(hd|hq|high|low|mobile|stream|live|radio|fm|am)\\b"), "")
                    .replace(Regex("[^a-z0-9]"), "")
                    .ifBlank { dto.name.lowercase().replace(Regex("[^a-z0-9]"), "") }
            }

            val bestStationsWithRank = groupedByName.values.mapNotNull { group ->
                val rankedByQuality = group.sortedWith(
                    compareByDescending<RadioBrowserStationDto> { computeAudioQualityScore(it) }
                        .thenByDescending { it.clickCount + it.votes * 3 }
                )
                val primaryDto = rankedByQuality.firstOrNull() ?: return@mapNotNull null
                val primaryUrl = primaryDto.urlResolved.ifBlank { primaryDto.url }.trim()
                val fallbackUrl = rankedByQuality.drop(1)
                    .map { it.urlResolved.ifBlank { it.url }.trim() }
                    .firstOrNull { !it.equals(primaryUrl, ignoreCase = true) }
                    .orEmpty()

                val mapped = mapDtoToStation(primaryDto, forceMalayalamIfBlank = forceMalayalamIfBlank)
                    ?.copy(fallbackStreamUrl = fallbackUrl)
                    ?: return@mapNotNull null

                val combinedPopularity = group.maxOf { it.clickCount + it.votes * 2 }
                val qualityScore = computeAudioQualityScore(primaryDto)
                Triple(mapped, qualityScore, combinedPopularity)
            }

            return bestStationsWithRank
                .sortedWith(
                    compareByDescending<Triple<RadioStation, Double, Int>> { it.second >= 140.0 }
                        .thenByDescending { it.third }
                        .thenByDescending { it.second }
                )
                .map { it.first }
        }

        /**
         * If a stream URL points to a plain-text .pls or .m3u playlist (not .m3u8 HLS),
         * downloads it and extracts the first direct http/https stream URL.
         */
        suspend fun resolveDirectStreamUrl(url: String): String = withContext(Dispatchers.IO) {
            val lower = url.lowercase().substringBefore("?")
            val isTextPlaylist = (lower.endsWith(".pls") || lower.endsWith(".m3u")) && !lower.endsWith(".m3u8")
            if (!isTextPlaylist) return@withContext url

            runCatching {
                val request = Request.Builder().url(url).build()
                sharedHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use url
                    val body = response.body?.string().orEmpty()
                    val lines = body.lines().map { it.trim() }
                    val extracted = lines.firstNotNullOfOrNull { line ->
                        when {
                            line.startsWith("File", ignoreCase = true) && line.contains("=") ->
                                line.substringAfter("=").trim().takeIf { it.startsWith("http") }
                            line.startsWith("http://") || line.startsWith("https://") -> line
                            else -> null
                        }
                    }
                    extracted ?: url
                }
            }.getOrDefault(url)
        }

        fun mapDtoToStation(dto: RadioBrowserStationDto, forceMalayalamIfBlank: Boolean = false): RadioStation? {
            if (dto.lastCheckOk == 0) return null
            val resolved = dto.urlResolved.ifBlank { dto.url }.trim()
            if (resolved.isBlank() || dto.name.isBlank()) return null
            if (!resolved.startsWith("http://") && !resolved.startsWith("https://")) return null

            val cleanName = dto.name.trim().replace(Regex("\\s+"), " ")
            val searchable = "$cleanName ${dto.tags} ${dto.state} ${dto.country} ${dto.language}".lowercase()
            val upper = searchable.uppercase()

            val band = when {
                upper.contains("SHORTWAVE") || Regex("\\bSW\\b").containsMatchIn(upper) -> RadioBand.SW
                Regex("\\bAM\\b").containsMatchIn(upper) ||
                    upper.contains("KHZ") ||
                    upper.contains("MEDIUM WAVE") ||
                    upper.contains("657") ||
                    upper.contains("1161") ||
                    upper.contains("684") ||
                    upper.contains("630") ||
                    upper.contains("576") -> RadioBand.AM
                else -> RadioBand.FM
            }

            val freqRegex = when (band) {
                RadioBand.FM -> Regex("(\\d{2,3}\\.\\d)\\s*(?:MHZ|FM)?", RegexOption.IGNORE_CASE)
                RadioBand.AM -> Regex("(\\d{3,4})\\s*(?:KHZ|AM)?", RegexOption.IGNORE_CASE)
                RadioBand.SW -> Regex("(\\d{1,2}\\.\\d{1,3})\\s*(?:MHZ|SW)", RegexOption.IGNORE_CASE)
            }
            val extractedFreq = freqRegex.find(cleanName)?.groupValues?.getOrNull(1)
            val frequency = when {
                extractedFreq != null -> "$extractedFreq ${band.unit}"
                dto.bitrate > 0 -> "${dto.bitrate} kbps"
                else -> "Live ${band.label}"
            }

            val district = detectDistrict(cleanName, dto.state, dto.country, dto.tags)

            val rawLang = dto.language.split(",")
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() }
            val language = when {
                searchable.contains("malayalam") || searchable.contains("kerala") -> "Malayalam"
                rawLang != null -> LanguageCatalog.normalizeLanguage(rawLang)
                forceMalayalamIfBlank -> "Malayalam"
                else -> "Multilingual"
            }

            val genreTags = dto.tags.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.equals("malayalam", ignoreCase = true) && !it.equals("india", ignoreCase = true) }
                .take(2)
                .joinToString(" • ") { tag -> tag.replaceFirstChar { it.uppercase() } }
                .ifBlank {
                    when {
                        searchable.contains("news") || searchable.contains("air") || searchable.contains("akashvani") -> "News & Regional"
                        searchable.contains("devotional") || searchable.contains("christian") || searchable.contains("islamic") || searchable.contains("bhakti") -> "Devotional"
                        searchable.contains("old") || searchable.contains("retro") || searchable.contains("classic") -> "Classic Melodies"
                        else -> "Malayalam Hits & Talk"
                    }
                }

            val words = cleanName.split(" ").filter { it.isNotBlank() && it.first().isLetterOrDigit() }
            val logoCode = when {
                words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase()
                cleanName.length >= 2 -> cleanName.filter { it.isLetterOrDigit() }.take(2).uppercase().ifBlank { "ML" }
                else -> "ML"
            }
            val color = palette[abs(dto.stationUuid.ifBlank { cleanName }.hashCode()) % palette.size]

            val codecLabel = when {
                dto.codec.isNotBlank() -> dto.codec.uppercase()
                dto.hls == 1 || resolved.contains(".m3u8", ignoreCase = true) -> "HLS"
                else -> ""
            }
            val qualityInfo = buildList {
                if (codecLabel.isNotBlank()) add(codecLabel)
                if (dto.bitrate > 0) add("${dto.bitrate} kbps")
            }.joinToString(" ")

            val tagline = buildString {
                append(genreTags)
                if (qualityInfo.isNotBlank()) {
                    append(" • ")
                    append(qualityInfo)
                }
            }

            return RadioStation(
                id = "rb_${dto.stationUuid.ifBlank { abs(resolved.hashCode()).toString() }}",
                name = cleanName,
                frequency = frequency,
                band = band,
                district = district,
                language = language,
                genre = genreTags,
                streamUrl = resolved,
                accentColorHex = color,
                logoCode = logoCode,
                tagline = tagline,
                isLiveDirectory = true
            )
        }

        private fun detectDistrict(name: String, state: String, country: String, tags: String): String {
            val text = "$name $state $tags $country".lowercase()
            return when {
                text.contains("thiruvananthapuram") || text.contains("trivandrum") || text.contains("ananthapuri") -> "Thiruvananthapuram"
                text.contains("kochi") || text.contains("cochin") || text.contains("ernakulam") || text.contains("aluva") -> "Kochi"
                text.contains("kozhikode") || text.contains("calicut") -> "Kozhikode"
                text.contains("thrissur") || text.contains("trichur") -> "Thrissur"
                text.contains("kannur") || text.contains("cannanore") -> "Kannur"
                text.contains("kollam") || text.contains("quilon") || text.contains("benziger") -> "Kollam"
                text.contains("kottayam") || text.contains("changanassery") || text.contains("pala") || text.contains("media village") -> "Kottayam"
                text.contains("wayanad") || text.contains("mattoli") || text.contains("kalpetta") -> "Wayanad"
                text.contains("malappuram") || text.contains("manjeri") -> "Malappuram"
                text.contains("pathanamthitta") || text.contains("thiruvalla") || text.contains("macfast") -> "Pathanamthitta"
                text.contains("idukki") || text.contains("devikulam") || text.contains("munnar") || text.contains("thodupuzha") -> "Idukki"
                text.contains("alappuzha") || text.contains("alleppey") -> "Alappuzha"
                text.contains("palakkad") || text.contains("palghat") -> "Palakkad"
                text.contains("kasaragod") -> "Kasaragod"
                text.contains("dubai") || text.contains("abu dhabi") || text.contains("uae") || text.contains("emirates") || text.contains("hit 96.7") || text.contains("gold 101.3") -> "Dubai (UAE)"
                text.contains("qatar") || text.contains("doha") || text.contains("suno") || text.contains("98.6") -> "Doha (Qatar)"
                text.contains("kuwait") || text.contains("oman") || text.contains("bahrain") || text.contains("saudi") -> "Gulf Region"
                text.contains("kerala") -> "Kerala"
                state.isNotBlank() -> state.trim().replaceFirstChar { it.uppercase() }
                country.isNotBlank() -> country.trim().replaceFirstChar { it.uppercase() }
                else -> "Kerala"
            }
        }
    }
}
