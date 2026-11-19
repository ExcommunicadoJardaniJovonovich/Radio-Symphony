package com.example.data.model

data class EqualizerBandSpec(
    val index: Int,
    val centerFreqHz: Int,
    val cutoffFreqHz: Float,
    val shortLabel: String
)

data class EqualizerPreset(
    val id: String,
    val name: String,
    val description: String,
    val bandGainsDb: List<Float>,
    val bassBoostStrength: Int = 0,
    val virtualizerStrength: Int = 0,
    val loudnessGainMb: Int = 0
)

data class EqualizerSettings(
    val enabled: Boolean = false,
    val presetId: String = EqualizerCatalog.PRESET_HIFI_STUDIO,
    val bandGainsDb: List<Float> = EqualizerCatalog.findPreset(EqualizerCatalog.PRESET_HIFI_STUDIO).bandGainsDb,
    val preampDb: Float = 0f,
    val bassBoostStrength: Int = 180,
    val virtualizerStrength: Int = 150,
    val loudnessGainMb: Int = 0,
    val limiterEnabled: Boolean = true
) {
    fun normalized(): EqualizerSettings {
        val safeBands = List(EqualizerCatalog.BAND_COUNT) { idx ->
            bandGainsDb.getOrElse(idx) { 0f }.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        }
        return copy(
            bandGainsDb = safeBands,
            preampDb = preampDb.coerceIn(MIN_PREAMP_DB, MAX_PREAMP_DB),
            bassBoostStrength = bassBoostStrength.coerceIn(0, MAX_EFFECT_STRENGTH),
            virtualizerStrength = virtualizerStrength.coerceIn(0, MAX_EFFECT_STRENGTH),
            loudnessGainMb = loudnessGainMb.coerceIn(0, MAX_LOUDNESS_MB)
        )
    }

    companion object {
        const val MIN_GAIN_DB = -12f
        const val MAX_GAIN_DB = 12f
        const val MIN_PREAMP_DB = -6f
        const val MAX_PREAMP_DB = 6f
        const val MAX_EFFECT_STRENGTH = 1000
        const val MAX_LOUDNESS_MB = 1000 // +10 dB
    }
}

object EqualizerCatalog {
    const val BAND_COUNT = 10
    const val PRESET_FLAT = "flat"
    const val PRESET_HIFI_STUDIO = "hifi_studio"
    const val PRESET_CUSTOM = "custom"

    val bands: List<EqualizerBandSpec> = listOf(
        EqualizerBandSpec(0, 31, 44f, "31Hz"),
        EqualizerBandSpec(1, 62, 88f, "62Hz"),
        EqualizerBandSpec(2, 125, 177f, "125Hz"),
        EqualizerBandSpec(3, 250, 355f, "250Hz"),
        EqualizerBandSpec(4, 500, 710f, "500Hz"),
        EqualizerBandSpec(5, 1000, 1420f, "1kHz"),
        EqualizerBandSpec(6, 2000, 2840f, "2kHz"),
        EqualizerBandSpec(7, 4000, 5680f, "4kHz"),
        EqualizerBandSpec(8, 8000, 11360f, "8kHz"),
        EqualizerBandSpec(9, 16000, 20000f, "16kHz")
    )

    val presets: List<EqualizerPreset> = listOf(
        EqualizerPreset(
            id = PRESET_HIFI_STUDIO,
            name = "Hi-Fi Studio",
            description = "Balanced audiophile warmth & crisp treble detail",
            bandGainsDb = listOf(2.5f, 2.0f, 1.0f, 0f, -0.5f, 0f, 1.5f, 2.5f, 3.0f, 2.5f),
            bassBoostStrength = 180,
            virtualizerStrength = 150
        ),
        EqualizerPreset(
            id = "malayalam_melodies",
            name = "Malayalam Melodies",
            description = "Lush vocals, silky strings & gentle low-end",
            bandGainsDb = listOf(1.5f, 2.5f, 1.5f, 1.0f, 2.0f, 2.5f, 1.5f, 2.0f, 2.5f, 1.5f),
            bassBoostStrength = 200,
            virtualizerStrength = 220
        ),
        EqualizerPreset(
            id = "fm_broadcast_warmth",
            name = "FM Broadcast HD",
            description = "Restores depth & airiness to live radio streams",
            bandGainsDb = listOf(3.0f, 2.5f, 1.5f, 0.5f, 0f, 1.0f, 2.0f, 3.5f, 4.0f, 3.0f),
            bassBoostStrength = 220,
            virtualizerStrength = 200,
            loudnessGainMb = 150
        ),
        EqualizerPreset(
            id = "deep_bass",
            name = "Deep Bass",
            description = "Punchy sub-bass & kick without muddying vocals",
            bandGainsDb = listOf(6.0f, 5.5f, 4.0f, 1.5f, 0f, -0.5f, 0.5f, 1.5f, 2.0f, 1.5f),
            bassBoostStrength = 600,
            virtualizerStrength = 120
        ),
        EqualizerPreset(
            id = "vocal_news_talk",
            name = "Vocal & News",
            description = "Crystal-clear RJ voice & AM/SW speech clarity",
            bandGainsDb = listOf(-3.0f, -2.0f, -0.5f, 1.5f, 3.5f, 4.5f, 3.5f, 2.0f, 0.5f, -1.0f),
            bassBoostStrength = 0,
            virtualizerStrength = 0,
            loudnessGainMb = 250
        ),
        EqualizerPreset(
            id = "classical_carnatic",
            name = "Classical & Carnatic",
            description = "Acoustic transparency for strings, flute & percussion",
            bandGainsDb = listOf(2.0f, 1.5f, 0.5f, 1.0f, 1.5f, 1.0f, 2.0f, 3.0f, 3.5f, 3.0f),
            bassBoostStrength = 100,
            virtualizerStrength = 320
        ),
        EqualizerPreset(
            id = "car_audio",
            name = "Car Audio",
            description = "Compensates for cabin road noise & door speakers",
            bandGainsDb = listOf(4.5f, 3.5f, 1.5f, -1.0f, 0.5f, 2.0f, 2.5f, 3.0f, 3.5f, 2.0f),
            bassBoostStrength = 380,
            virtualizerStrength = 260,
            loudnessGainMb = 200
        ),
        EqualizerPreset(
            id = "headphones_3d",
            name = "Headphones 3D",
            description = "Wide stereo soundstage & sparkling highs",
            bandGainsDb = listOf(2.0f, 1.5f, 0.5f, 0f, 0.5f, 1.5f, 2.5f, 3.0f, 3.5f, 2.5f),
            bassBoostStrength = 220,
            virtualizerStrength = 520
        ),
        EqualizerPreset(
            id = "pop_film_hits",
            name = "Pop & Film Hits",
            description = "Energetic studio punch for upbeat tracks",
            bandGainsDb = listOf(3.5f, 3.0f, 1.5f, 0f, -1.0f, 0.5f, 2.0f, 3.0f, 3.5f, 2.5f),
            bassBoostStrength = 350,
            virtualizerStrength = 250
        ),
        EqualizerPreset(
            id = "late_night",
            name = "Late Night",
            description = "Smooth low-volume listening with softened treble",
            bandGainsDb = listOf(2.5f, 2.0f, 1.0f, 0.5f, 1.0f, 0.5f, -0.5f, -1.5f, -2.5f, -3.5f),
            bassBoostStrength = 140,
            virtualizerStrength = 100
        ),
        EqualizerPreset(
            id = PRESET_FLAT,
            name = "Flat Reference",
            description = "Pure untouched broadcast audio response",
            bandGainsDb = List(BAND_COUNT) { 0f },
            bassBoostStrength = 0,
            virtualizerStrength = 0,
            loudnessGainMb = 0
        )
    )

    fun findPreset(id: String): EqualizerPreset {
        return presets.firstOrNull { it.id == id } ?: presets.first()
    }

    fun presetDisplayName(presetId: String): String {
        if (presetId == PRESET_CUSTOM) return "Custom"
        return presets.firstOrNull { it.id == presetId }?.name ?: "Custom"
    }

    /**
     * Interpolates a target frequency's gain (in dB) from our 10-band ISO gain array
     * so devices falling back to 5-band Android AudioFX Equalizer preserve the curve smoothly.
     */
    fun interpolateGainForFrequency(freqHz: Int, bandGainsDb: List<Float>): Float {
        if (bandGainsDb.size < BAND_COUNT) return 0f
        if (freqHz <= bands.first().centerFreqHz) return bandGainsDb.first()
        if (freqHz >= bands.last().centerFreqHz) return bandGainsDb.last()

        for (i in 0 until bands.lastIndex) {
            val lowFreq = bands[i].centerFreqHz.toFloat()
            val highFreq = bands[i + 1].centerFreqHz.toFloat()
            if (freqHz in lowFreq.toInt()..highFreq.toInt()) {
                val ratio = ((freqHz - lowFreq) / (highFreq - lowFreq)).coerceIn(0f, 1f)
                return bandGainsDb[i] + (bandGainsDb[i + 1] - bandGainsDb[i]) * ratio
            }
        }
        return 0f
    }
}
