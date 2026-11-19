package com.example.data.model

import kotlin.math.abs

enum class LanguageRegionGroup(val label: String) {
    SOUTH_INDIAN("South Indian Languages"),
    INDIAN("Indian Languages"),
    INTERNATIONAL("International Languages"),
    OTHER("Other Languages")
}

data class LanguageDirectoryEntry(
    val name: String,
    val nativeName: String,
    val regionGroup: LanguageRegionGroup,
    val accentColorHex: Long,
    val apiQuery: String = name.lowercase()
)

data class LanguageSummary(
    val language: String,
    val nativeName: String,
    val regionGroup: LanguageRegionGroup,
    val stationCount: Int,
    val bands: List<RadioBand>,
    val districts: List<String>,
    val accentColorHex: Long
)

object LanguageCatalog {

    val entries: List<LanguageDirectoryEntry> = listOf(
        // South Indian
        LanguageDirectoryEntry("Malayalam", "മലയാളം", LanguageRegionGroup.SOUTH_INDIAN, 0xFFF5A623),
        LanguageDirectoryEntry("Tamil", "தமிழ்", LanguageRegionGroup.SOUTH_INDIAN, 0xFFE85D5A),
        LanguageDirectoryEntry("Kannada", "ಕನ್ನಡ", LanguageRegionGroup.SOUTH_INDIAN, 0xFF34D399),
        LanguageDirectoryEntry("Telugu", "తెలుగు", LanguageRegionGroup.SOUTH_INDIAN, 0xFF38BDF8),
        LanguageDirectoryEntry("Tulu", "ತುಳು", LanguageRegionGroup.SOUTH_INDIAN, 0xFF2DD4BF),
        LanguageDirectoryEntry("Konkani", "कोंकणी", LanguageRegionGroup.SOUTH_INDIAN, 0xFFA78BFA),

        // Other Indian Languages
        LanguageDirectoryEntry("Hindi", "हिन्दी", LanguageRegionGroup.INDIAN, 0xFFFB923C),
        LanguageDirectoryEntry("Bengali", "বাংলা", LanguageRegionGroup.INDIAN, 0xFFF472B6),
        LanguageDirectoryEntry("Marathi", "मराठी", LanguageRegionGroup.INDIAN, 0xFF22C55E),
        LanguageDirectoryEntry("Punjabi", "ਪੰਜਾਬੀ", LanguageRegionGroup.INDIAN, 0xFFFBBF24),
        LanguageDirectoryEntry("Gujarati", "ગુજરાતી", LanguageRegionGroup.INDIAN, 0xFF60A5FA),
        LanguageDirectoryEntry("Urdu", "اردو", LanguageRegionGroup.INDIAN, 0xFF14B8A6),
        LanguageDirectoryEntry("Odia", "ଓଡ଼ିଆ", LanguageRegionGroup.INDIAN, 0xFFC084FC),
        LanguageDirectoryEntry("Assamese", "অসমীয়া", LanguageRegionGroup.INDIAN, 0xFF10B981),
        LanguageDirectoryEntry("Sanskrit", "संस्कृतम्", LanguageRegionGroup.INDIAN, 0xFFF59E0B),

        // International Languages
        LanguageDirectoryEntry("English", "English", LanguageRegionGroup.INTERNATIONAL, 0xFF38BDF8),
        LanguageDirectoryEntry("Arabic", "العربية", LanguageRegionGroup.INTERNATIONAL, 0xFF34D399),
        LanguageDirectoryEntry("Spanish", "Español", LanguageRegionGroup.INTERNATIONAL, 0xFFE85D5A),
        LanguageDirectoryEntry("French", "Français", LanguageRegionGroup.INTERNATIONAL, 0xFF818CF8),
        LanguageDirectoryEntry("German", "Deutsch", LanguageRegionGroup.INTERNATIONAL, 0xFFF5A623),
        LanguageDirectoryEntry("Portuguese", "Português", LanguageRegionGroup.INTERNATIONAL, 0xFF22C55E),
        LanguageDirectoryEntry("Italian", "Italiano", LanguageRegionGroup.INTERNATIONAL, 0xFF2DD4BF),
        LanguageDirectoryEntry("Japanese", "日本語", LanguageRegionGroup.INTERNATIONAL, 0xFFF472B6),
        LanguageDirectoryEntry("Korean", "한국어", LanguageRegionGroup.INTERNATIONAL, 0xFFA78BFA),
        LanguageDirectoryEntry("Chinese", "中文", LanguageRegionGroup.INTERNATIONAL, 0xFFFB7185),
        LanguageDirectoryEntry("Russian", "Русский", LanguageRegionGroup.INTERNATIONAL, 0xFF60A5FA),
        LanguageDirectoryEntry("Turkish", "Türkçe", LanguageRegionGroup.INTERNATIONAL, 0xFFFB923C),
        LanguageDirectoryEntry("Indonesian", "Bahasa Indonesia", LanguageRegionGroup.INTERNATIONAL, 0xFF10B981),
        LanguageDirectoryEntry("Persian", "فارسی", LanguageRegionGroup.INTERNATIONAL, 0xFFC084FC)
    )

    private val entryByLowerName: Map<String, LanguageDirectoryEntry> =
        entries.associateBy { it.name.lowercase() }

    private val fallbackPalette = listOf(
        0xFFF5A623L, 0xFF38BDF8L, 0xFF34D399L, 0xFFA78BFAL,
        0xFFE85D5AL, 0xFFFB923CL, 0xFF2DD4BFL, 0xFFF472B6L
    )

    /**
     * Normalizes noisy directory language strings (e.g. "malayalam, english",
     * "british english", "castilian", "Arabic / Malayalam") into a clean canonical language name.
     */
    fun normalizeLanguage(raw: String?): String {
        val cleaned = raw?.trim().orEmpty()
        if (cleaned.isBlank()) return "Multilingual"

        val lower = cleaned.lowercase()
        return when {
            lower.contains("malayalam") -> "Malayalam"
            lower.contains("tamil") -> "Tamil"
            lower.contains("kannada") -> "Kannada"
            lower.contains("telugu") -> "Telugu"
            lower.contains("tulu") -> "Tulu"
            lower.contains("konkani") -> "Konkani"
            lower.contains("hindi") || lower.contains("bollywood") -> "Hindi"
            lower.contains("bengali") || lower.contains("bangla") -> "Bengali"
            lower.contains("marathi") -> "Marathi"
            lower.contains("punjabi") -> "Punjabi"
            lower.contains("gujarati") -> "Gujarati"
            lower.contains("urdu") -> "Urdu"
            lower.contains("odia") || lower.contains("oriya") -> "Odia"
            lower.contains("assamese") -> "Assamese"
            lower.contains("sanskrit") -> "Sanskrit"
            lower.contains("arabic") -> "Arabic"
            lower.contains("english") -> "English"
            lower.contains("spanish") || lower.contains("español") || lower.contains("castilian") -> "Spanish"
            lower.contains("french") || lower.contains("français") -> "French"
            lower.contains("german") || lower.contains("deutsch") -> "German"
            lower.contains("portuguese") || lower.contains("português") -> "Portuguese"
            lower.contains("italian") || lower.contains("italiano") -> "Italian"
            lower.contains("japanese") || lower.contains("nihongo") -> "Japanese"
            lower.contains("korean") -> "Korean"
            lower.contains("chinese") || lower.contains("mandarin") || lower.contains("cantonese") -> "Chinese"
            lower.contains("russian") -> "Russian"
            lower.contains("turkish") -> "Turkish"
            lower.contains("indonesian") || lower.contains("bahasa") -> "Indonesian"
            lower.contains("persian") || lower.contains("farsi") -> "Persian"
            lower == "global" || lower == "international" || lower == "world" || lower == "unknown" -> "Multilingual"
            else -> {
                val primary = cleaned.split(",", "/", "|", ";")
                    .map { it.trim() }
                    .firstOrNull { it.isNotBlank() }
                    ?: "Multilingual"
                primary.replaceFirstChar { it.uppercase() }
            }
        }
    }

    fun findEntry(language: String): LanguageDirectoryEntry? {
        val canonical = normalizeLanguage(language)
        return entryByLowerName[canonical.lowercase()]
    }

    fun buildLanguageSummaries(stations: List<RadioStation>): List<LanguageSummary> {
        val groupedByLang = stations
            .distinctBy { it.id }
            .groupBy { normalizeLanguage(it.language) }

        val knownSummaries = entries.map { entry ->
            val list = groupedByLang[entry.name].orEmpty()
            LanguageSummary(
                language = entry.name,
                nativeName = entry.nativeName,
                regionGroup = entry.regionGroup,
                stationCount = list.size,
                bands = list.map { it.band }.distinct().sortedBy { it.ordinal },
                districts = list.map { it.district }.distinct().take(3),
                accentColorHex = entry.accentColorHex
            )
        }

        val knownNames = entries.map { it.name }.toSet()
        val extraSummaries = groupedByLang
            .filterKeys { it !in knownNames }
            .map { (lang, list) ->
                LanguageSummary(
                    language = lang,
                    nativeName = lang,
                    regionGroup = LanguageRegionGroup.OTHER,
                    stationCount = list.size,
                    bands = list.map { it.band }.distinct().sortedBy { it.ordinal },
                    districts = list.map { it.district }.distinct().take(3),
                    accentColorHex = fallbackPalette[abs(lang.hashCode()) % fallbackPalette.size]
                )
            }
            .sortedByDescending { it.stationCount }

        return knownSummaries + extraSummaries
    }
}
