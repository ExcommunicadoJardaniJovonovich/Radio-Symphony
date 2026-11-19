package com.example.data.remote

import com.example.data.model.LanguageCatalog
import com.example.data.model.RadioBand
import com.example.data.model.RadioStation
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import kotlin.math.abs

// --- Radio Garden Search Response DTOs (/api/search?q=...) ---

@JsonClass(generateAdapter = true)
data class RadioGardenSearchResponse(
    @Json(name = "hits") val hits: RadioGardenHitsWrapper? = null
)

@JsonClass(generateAdapter = true)
data class RadioGardenHitsWrapper(
    @Json(name = "hits") val hits: List<RadioGardenHit>? = null
)

@JsonClass(generateAdapter = true)
data class RadioGardenHit(
    @Json(name = "_id") val id: String? = null,
    @Json(name = "_source") val source: RadioGardenHitSource? = null
)

@JsonClass(generateAdapter = true)
data class RadioGardenHitSource(
    @Json(name = "type") val type: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "subtitle") val subtitle: String? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "code") val code: String? = null,
    @Json(name = "page") val page: RadioGardenPageRef? = null
)

@JsonClass(generateAdapter = true)
data class RadioGardenPageRef(
    @Json(name = "url") val url: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "subtitle") val subtitle: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "place") val place: RadioGardenRef? = null,
    @Json(name = "country") val country: RadioGardenRef? = null
)

@JsonClass(generateAdapter = true)
data class RadioGardenRef(
    @Json(name = "id") val id: String? = null,
    @Json(name = "title") val title: String? = null
)

// --- Radio Garden Place Channels Response DTOs (/api/ara/content/page/{placeId}/channels) ---

@JsonClass(generateAdapter = true)
data class RadioGardenPlaceChannelsResponse(
    @Json(name = "data") val data: RadioGardenPlaceChannelsData? = null
)

@JsonClass(generateAdapter = true)
data class RadioGardenPlaceChannelsData(
    @Json(name = "title") val title: String? = null,
    @Json(name = "subtitle") val subtitle: String? = null,
    @Json(name = "content") val content: List<RadioGardenContentSection>? = null
)

@JsonClass(generateAdapter = true)
data class RadioGardenContentSection(
    @Json(name = "items") val items: List<RadioGardenChannelItem>? = null
)

@JsonClass(generateAdapter = true)
data class RadioGardenChannelItem(
    @Json(name = "href") val href: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "page") val page: RadioGardenPageRef? = null
)

data class RadioGardenPlaceMatch(
    val placeId: String,
    val title: String,
    val subtitle: String
)

interface RadioGardenApi {

    @GET("search")
    suspend fun search(
        @Query("q") query: String
    ): RadioGardenSearchResponse

    @GET("ara/content/page/{placeId}/channels")
    suspend fun getPlaceChannels(
        @Path("placeId") placeId: String
    ): RadioGardenPlaceChannelsResponse

    companion object {
        const val BASE_URL = "https://radio.garden/api/"

        private val palette = listOf(
            0xFF34D399L,
            0xFF2DD4BFL,
            0xFF38BDF8L,
            0xFFF5A623L,
            0xFFA78BFAL,
            0xFFFB923CL,
            0xFFE85D5AL,
            0xFFF472B6L
        )

        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Android; RadioSymphony/1.3)")
                        .header("Accept", "application/json")
                        .build()
                    chain.proceed(request)
                }
                .build()
        }

        private val moshi: Moshi by lazy {
            Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
        }

        fun create(): RadioGardenApi {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(httpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(RadioGardenApi::class.java)
        }

        fun buildListenUrl(channelId: String): String {
            return "https://radio.garden/api/ara/content/listen/$channelId/channel.mp3"
        }

        fun extractIdFromPath(path: String?): String? {
            if (path.isNullOrBlank()) return null
            val clean = path.trim().substringBefore("?").trimEnd('/')
            val segment = clean.substringAfterLast('/')
            return segment.takeIf { it.isNotBlank() && it != "listen" && it != "visit" }
        }

        fun parseSearchHits(
            response: RadioGardenSearchResponse,
            forceMalayalamIfKerala: Boolean = true
        ): Pair<List<RadioStation>, List<RadioGardenPlaceMatch>> {
            val stations = mutableListOf<RadioStation>()
            val places = mutableListOf<RadioGardenPlaceMatch>()

            val hits = response.hits?.hits.orEmpty()
            for (hit in hits) {
                val src = hit.source ?: continue
                val type = (src.type ?: src.page?.type).orEmpty().lowercase()
                val pageUrl = src.page?.url ?: src.url.orEmpty()

                when {
                    type == "channel" || pageUrl.contains("/listen/") -> {
                        val channelId = extractIdFromPath(pageUrl) ?: continue
                        val title = (src.page?.title ?: src.title).orEmpty().trim()
                        if (title.isBlank()) continue
                        val placeTitle = src.page?.place?.title
                            ?: src.page?.subtitle
                            ?: src.subtitle
                            ?: ""
                        val countryTitle = src.page?.country?.title.orEmpty()
                        stations.add(
                            mapChannelToStation(
                                channelId = channelId,
                                title = title,
                                placeTitle = placeTitle,
                                countryTitle = countryTitle,
                                forceMalayalamIfKerala = forceMalayalamIfKerala
                            )
                        )
                    }
                    type == "place" || pageUrl.contains("/visit/") -> {
                        val placeId = extractIdFromPath(pageUrl) ?: continue
                        val title = (src.page?.title ?: src.title).orEmpty().trim()
                        if (title.isBlank()) continue
                        val subtitle = (src.page?.subtitle ?: src.subtitle ?: src.page?.country?.title).orEmpty().trim()
                        places.add(RadioGardenPlaceMatch(placeId = placeId, title = title, subtitle = subtitle))
                    }
                }
            }

            return stations.distinctBy { it.id } to places.distinctBy { it.placeId }
        }

        fun parsePlaceChannels(
            response: RadioGardenPlaceChannelsResponse,
            fallbackPlaceTitle: String = "",
            fallbackCountryTitle: String = "",
            forceMalayalamIfKerala: Boolean = true
        ): List<RadioStation> {
            val data = response.data ?: return emptyList()
            val placeTitle = data.title?.takeIf { it.isNotBlank() } ?: fallbackPlaceTitle
            val subtitle = data.subtitle?.takeIf { it.isNotBlank() } ?: fallbackCountryTitle

            val stations = mutableListOf<RadioStation>()
            for (section in data.content.orEmpty()) {
                for (item in section.items.orEmpty()) {
                    val path = item.page?.url ?: item.href
                    val channelId = extractIdFromPath(path) ?: continue
                    val title = (item.page?.title ?: item.title).orEmpty().trim()
                    if (title.isBlank()) continue
                    val itemPlace = item.page?.place?.title?.takeIf { it.isNotBlank() } ?: placeTitle
                    val itemCountry = item.page?.country?.title?.takeIf { it.isNotBlank() } ?: subtitle
                    stations.add(
                        mapChannelToStation(
                            channelId = channelId,
                            title = title,
                            placeTitle = itemPlace,
                            countryTitle = itemCountry,
                            forceMalayalamIfKerala = forceMalayalamIfKerala
                        )
                    )
                }
            }
            return stations.distinctBy { it.id }
        }

        fun mapChannelToStation(
            channelId: String,
            title: String,
            placeTitle: String,
            countryTitle: String,
            forceMalayalamIfKerala: Boolean = true
        ): RadioStation {
            val cleanName = title.trim().replace(Regex("\\s+"), " ")
            val searchable = "$cleanName $placeTitle $countryTitle".lowercase()
            val upper = searchable.uppercase()

            val band = when {
                upper.contains("SHORTWAVE") || Regex("\\bSW\\b").containsMatchIn(upper) -> RadioBand.SW
                Regex("\\bAM\\b").containsMatchIn(upper) ||
                    upper.contains("KHZ") ||
                    upper.contains("MEDIUM WAVE") -> RadioBand.AM
                else -> RadioBand.FM
            }

            val freqRegex = when (band) {
                RadioBand.FM -> Regex("(\\d{2,3}\\.\\d)\\s*(?:MHZ|FM)?", RegexOption.IGNORE_CASE)
                RadioBand.AM -> Regex("(\\d{3,4})\\s*(?:KHZ|AM)?", RegexOption.IGNORE_CASE)
                RadioBand.SW -> Regex("(\\d{1,2}\\.\\d{1,3})\\s*(?:MHZ|SW)", RegexOption.IGNORE_CASE)
            }
            val extractedFreq = freqRegex.find(cleanName)?.groupValues?.getOrNull(1)
            val frequency = if (extractedFreq != null) {
                "$extractedFreq ${band.unit}"
            } else {
                "Live ${band.label}"
            }

            val district = detectDistrict(cleanName, placeTitle, countryTitle)
            val isKeralaOrMalayalam = searchable.contains("malayalam") ||
                searchable.contains("kerala") ||
                district in KERALA_DISTRICTS ||
                searchable.contains("club fm") ||
                searchable.contains("radio mango") ||
                searchable.contains("mazhavil") ||
                searchable.contains("hit 96.7") ||
                searchable.contains("gold 101.3") ||
                searchable.contains("suno")

            val language = LanguageCatalog.normalizeLanguage(
                when {
                    forceMalayalamIfKerala && isKeralaOrMalayalam -> "Malayalam"
                    searchable.contains("malayalam") -> "Malayalam"
                    searchable.contains("tamil") || searchable.contains("chennai") -> "Tamil"
                    searchable.contains("hindi") || searchable.contains("mumbai") || searchable.contains("delhi") -> "Hindi"
                    searchable.contains("kannada") || searchable.contains("bengaluru") || searchable.contains("bangalore") -> "Kannada"
                    searchable.contains("telugu") || searchable.contains("hyderabad") -> "Telugu"
                    searchable.contains("bengali") || searchable.contains("kolkata") -> "Bengali"
                    searchable.contains("marathi") || searchable.contains("pune") -> "Marathi"
                    searchable.contains("punjabi") || searchable.contains("chandigarh") -> "Punjabi"
                    searchable.contains("gujarati") || searchable.contains("ahmedabad") -> "Gujarati"
                    searchable.contains("arabic") || searchable.contains("dubai") || searchable.contains("doha") || searchable.contains("riyadh") || searchable.contains("cairo") -> "Arabic"
                    searchable.contains("japan") || searchable.contains("tokyo") || searchable.contains("osaka") -> "Japanese"
                    searchable.contains("korea") || searchable.contains("seoul") -> "Korean"
                    searchable.contains("spain") || searchable.contains("madrid") || searchable.contains("mexico") || searchable.contains("argentina") -> "Spanish"
                    searchable.contains("france") || searchable.contains("paris") -> "French"
                    searchable.contains("germany") || searchable.contains("berlin") -> "German"
                    searchable.contains("italy") || searchable.contains("rome") || searchable.contains("milan") -> "Italian"
                    searchable.contains("brazil") || searchable.contains("portugal") || searchable.contains("lisbon") -> "Portuguese"
                    searchable.contains("russia") || searchable.contains("moscow") -> "Russian"
                    searchable.contains("turkey") || searchable.contains("istanbul") -> "Turkish"
                    searchable.contains("uk") || searchable.contains("london") || searchable.contains("united states") || searchable.contains("usa") || searchable.contains("canada") || searchable.contains("australia") -> "English"
                    else -> "Multilingual"
                }
            )

            val locationLabel = buildList {
                val p = placeTitle.trim()
                val c = countryTitle.trim()
                if (p.isNotBlank()) add(p)
                if (c.isNotBlank() && !p.contains(c, ignoreCase = true)) add(c)
            }.joinToString(", ").ifBlank { district }

            val words = cleanName.split(" ").filter { it.isNotBlank() && it.first().isLetterOrDigit() }
            val logoCode = when {
                words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase()
                cleanName.length >= 2 -> cleanName.filter { it.isLetterOrDigit() }.take(2).uppercase().ifBlank { "RG" }
                else -> "RG"
            }
            val color = palette[abs(channelId.hashCode()) % palette.size]

            return RadioStation(
                id = "rg_$channelId",
                name = cleanName,
                frequency = frequency,
                band = band,
                district = district,
                language = language,
                genre = "Radio Garden",
                streamUrl = buildListenUrl(channelId),
                accentColorHex = color,
                logoCode = logoCode,
                tagline = "Radio Garden • $locationLabel",
                isLiveDirectory = true
            )
        }

        private val KERALA_DISTRICTS = setOf(
            "Thiruvananthapuram", "Kochi", "Kozhikode", "Thrissur", "Kannur",
            "Kollam", "Kottayam", "Wayanad", "Malappuram", "Pathanamthitta",
            "Idukki", "Alappuzha", "Palakkad", "Kasaragod", "Kerala"
        )

        private fun detectDistrict(name: String, placeTitle: String, countryTitle: String): String {
            val text = "$name $placeTitle $countryTitle".lowercase()
            return when {
                text.contains("thiruvananthapuram") || text.contains("trivandrum") || text.contains("ananthapuri") -> "Thiruvananthapuram"
                text.contains("kochi") || text.contains("cochin") || text.contains("ernakulam") || text.contains("aluva") -> "Kochi"
                text.contains("kozhikode") || text.contains("calicut") -> "Kozhikode"
                text.contains("thrissur") || text.contains("trichur") -> "Thrissur"
                text.contains("kannur") || text.contains("cannanore") -> "Kannur"
                text.contains("kollam") || text.contains("quilon") || text.contains("benziger") -> "Kollam"
                text.contains("kottayam") || text.contains("changanassery") || text.contains("pala") -> "Kottayam"
                text.contains("wayanad") || text.contains("mattoli") || text.contains("kalpetta") -> "Wayanad"
                text.contains("malappuram") || text.contains("manjeri") || text.contains("tirur") -> "Malappuram"
                text.contains("pathanamthitta") || text.contains("thiruvalla") || text.contains("macfast") -> "Pathanamthitta"
                text.contains("idukki") || text.contains("devikulam") || text.contains("munnar") || text.contains("thodupuzha") -> "Idukki"
                text.contains("alappuzha") || text.contains("alleppey") -> "Alappuzha"
                text.contains("palakkad") || text.contains("palghat") -> "Palakkad"
                text.contains("kasaragod") -> "Kasaragod"
                text.contains("dubai") -> "Dubai (UAE)"
                text.contains("abu dhabi") -> "Abu Dhabi (UAE)"
                text.contains("doha") || text.contains("qatar") -> "Doha (Qatar)"
                placeTitle.isNotBlank() -> placeTitle.substringBefore(",").trim()
                countryTitle.isNotBlank() -> countryTitle.trim()
                else -> "Global"
            }
        }
    }
}
