package com.example.data.model

enum class RadioBand(
    val label: String,
    val fullName: String,
    val unit: String
) {
    FM("FM", "Frequency Modulation", "MHz"),
    AM("AM", "Amplitude Modulation", "kHz"),
    SW("SW", "Shortwave World Band", "MHz");

    companion object {
        fun fromString(value: String?): RadioBand {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: FM
        }
    }
}

enum class FavouriteSortOrder(val label: String) {
    RECENTLY_PLAYED("Recently Played"),
    ALPHABETICAL("Alphabetical (A–Z)")
}

enum class AppThemeMode(val label: String) {
    DARK("Studio Dark"),
    LIGHT("Warm Light"),
    SYSTEM("System Default");

    companion object {
        fun fromString(value: String?): AppThemeMode {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DARK
        }
    }
}

data class RadioStation(
    val id: String,
    val name: String,
    val frequency: String,
    val band: RadioBand,
    val district: String,
    val language: String,
    val genre: String,
    val streamUrl: String,
    val fallbackStreamUrl: String = "",
    val accentColorHex: Long = 0xFFF5A623,
    val logoCode: String = "RS",
    val tagline: String = "",
    val isLiveDirectory: Boolean = false
) {
    val formattedFrequency: String
        get() = "$frequency ${band.label}"

    val fullBandLabel: String
        get() = "${band.label} • $frequency"

    val isRadioGarden: Boolean
        get() = id.startsWith("rg_") || streamUrl.contains("radio.garden", ignoreCase = true)

    val qualityBadge: String
        get() {
            if (isRadioGarden) return "GARDEN LIVE"
            val bitrateMatch = Regex("(\\d{2,4})\\s*kbps", RegexOption.IGNORE_CASE)
                .find("$tagline $frequency")
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
            val upper = "$tagline $streamUrl".uppercase()
            val codec = when {
                upper.contains("FLAC") -> "FLAC"
                upper.contains("OPUS") -> "OPUS"
                upper.contains("AAC") -> "AAC"
                upper.contains("M3U8") || upper.contains("HLS") -> "HLS"
                upper.contains("OGG") -> "OGG"
                upper.contains("MP3") -> "MP3"
                else -> ""
            }
            return when {
                bitrateMatch != null && bitrateMatch >= 192 -> "HI-FI ${bitrateMatch}k"
                bitrateMatch != null && bitrateMatch >= 128 -> "HD ${bitrateMatch}k"
                bitrateMatch != null && bitrateMatch > 0 -> "${bitrateMatch}k${if (codec.isNotEmpty()) " $codec" else ""}"
                codec == "HLS" -> "HLS HD"
                codec.isNotEmpty() -> "$codec HD"
                else -> "HQ"
            }
        }

    val isHighQuality: Boolean
        get() {
            if (isRadioGarden) return true
            val bitrate = Regex("(\\d{2,4})\\s*kbps", RegexOption.IGNORE_CASE)
                .find("$tagline $frequency")
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
            val upper = "$tagline $streamUrl".uppercase()
            return (bitrate != null && bitrate >= 96) ||
                upper.contains("M3U8") ||
                upper.contains("AAC") ||
                upper.contains("FLAC") ||
                upper.contains("OPUS")
        }
}

data class FavouriteStationItem(
    val station: RadioStation,
    val addedAt: Long,
    val lastPlayedAt: Long
)

data class DistrictSummary(
    val district: String,
    val stationCount: Int,
    val bands: List<RadioBand>,
    val languages: List<String>,
    val accentColorHex: Long
)
