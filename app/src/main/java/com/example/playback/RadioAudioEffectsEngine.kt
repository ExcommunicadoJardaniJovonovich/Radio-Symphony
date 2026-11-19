package com.example.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.data.model.EqualizerCatalog
import com.example.data.model.EqualizerSettings
import kotlin.math.roundToInt

/**
 * Hardware-accelerated DSP Equalizer & Audio Enhancement Engine attached directly to
 * ExoPlayer's active audio session ID.
 *
 * - On Android 9+ (API 28+): Uses [DynamicsProcessing] with a true 10-band Post-EQ,
 *   preamp input gain, and a studio soft-knee anti-clipping Limiter.
 * - On Android 7-8 (API 24-27) or OEM fallback: Uses Android [Equalizer] with smooth
 *   frequency interpolation across hardware bands.
 * - Supplements EQ with native [BassBoost], [Virtualizer] (3D Surround), and [LoudnessEnhancer].
 */
class RadioAudioEffectsEngine {

    private var currentAudioSessionId: Int = 0
    private var lastAppliedSettings: EqualizerSettings = EqualizerSettings()

    private var dynamicsProcessing: DynamicsProcessing? = null
    private var legacyEqualizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    val isUsing10BandStudioDsp: Boolean
        get() = dynamicsProcessing != null

    fun attachToSession(audioSessionId: Int, settings: EqualizerSettings) {
        if (audioSessionId <= 0) return
        lastAppliedSettings = settings.normalized()

        if (currentAudioSessionId == audioSessionId && hasAnyEffectAttached()) {
            applySettings(lastAppliedSettings)
            return
        }

        release()
        currentAudioSessionId = audioSessionId

        var initialized10Band = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            initialized10Band = initDynamicsProcessing(audioSessionId)
        }
        if (!initialized10Band) {
            initLegacyEqualizer(audioSessionId)
        }

        initSupplementaryEffects(audioSessionId)
        applySettings(lastAppliedSettings)
    }

    fun applySettings(settings: EqualizerSettings) {
        val safe = settings.normalized()
        lastAppliedSettings = safe

        if (currentAudioSessionId <= 0) return

        val isEnabled = safe.enabled

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && dynamicsProcessing != null) {
            applyDynamicsProcessing(safe, isEnabled)
        } else {
            applyLegacyEqualizer(safe, isEnabled)
        }

        runCatching {
            bassBoost?.let { bb ->
                val active = isEnabled && safe.bassBoostStrength > 0
                bb.enabled = active
                if (active && bb.strengthSupported) {
                    bb.setStrength(safe.bassBoostStrength.toShort())
                }
            }
        }

        runCatching {
            virtualizer?.let { virt ->
                val active = isEnabled && safe.virtualizerStrength > 0
                virt.enabled = active
                if (active && virt.strengthSupported) {
                    virt.setStrength(safe.virtualizerStrength.toShort())
                }
            }
        }

        runCatching {
            loudnessEnhancer?.let { le ->
                val active = isEnabled && safe.loudnessGainMb > 0
                le.enabled = active
                if (active) {
                    le.setTargetGain(safe.loudnessGainMb)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun initDynamicsProcessing(audioSessionId: Int): Boolean {
        return runCatching {
            val config = DynamicsProcessing.Config.Builder(
                /* variant = */ DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                /* channelCount = */ 2,
                /* preEqInUse = */ false,
                /* preEqBandCount = */ 0,
                /* mbcInUse = */ false,
                /* mbcBandCount = */ 0,
                /* postEqInUse = */ true,
                /* postEqBandCount = */ EqualizerCatalog.BAND_COUNT,
                /* limiterInUse = */ true
            ).build()

            val dp = DynamicsProcessing(0, audioSessionId, config)
            val postEq = DynamicsProcessing.Eq(true, true, EqualizerCatalog.BAND_COUNT)
            EqualizerCatalog.bands.forEach { spec ->
                postEq.setBand(
                    spec.index,
                    DynamicsProcessing.EqBand(true, spec.cutoffFreqHz, 0f)
                )
            }
            dp.setPostEqAllChannelsTo(postEq)

            val limiter = DynamicsProcessing.Limiter(
                /* inUse = */ true,
                /* enabled = */ true,
                /* linkGroup = */ 0,
                /* attackTime = */ 1.0f,
                /* releaseTime = */ 60.0f,
                /* ratio = */ 10.0f,
                /* threshold = */ -1.0f,
                /* postGain = */ 0.0f
            )
            dp.setLimiterAllChannelsTo(limiter)
            dynamicsProcessing = dp
            true
        }.onFailure { err ->
            Log.w(TAG, "DynamicsProcessing unavailable on this device, falling back to hardware Equalizer", err)
            dynamicsProcessing = null
        }.getOrDefault(false)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun applyDynamicsProcessing(settings: EqualizerSettings, isEnabled: Boolean) {
        runCatching {
            val dp = dynamicsProcessing ?: return
            dp.enabled = isEnabled
            if (!isEnabled) return

            // Auto-headroom prevents digital clipping when bands are boosted heavily
            val peakBoost = settings.bandGainsDb.maxOrNull()?.coerceAtLeast(0f) ?: 0f
            val autoHeadroom = if (settings.limiterEnabled && peakBoost > 4f) {
                -(peakBoost - 4f) * 0.35f
            } else {
                0f
            }
            dp.setInputGainAllChannelsTo((settings.preampDb + autoHeadroom).coerceIn(-12f, 12f))

            val postEq = DynamicsProcessing.Eq(true, true, EqualizerCatalog.BAND_COUNT)
            EqualizerCatalog.bands.forEach { spec ->
                val gain = settings.bandGainsDb.getOrElse(spec.index) { 0f }
                postEq.setBand(
                    spec.index,
                    DynamicsProcessing.EqBand(true, spec.cutoffFreqHz, gain)
                )
            }
            dp.setPostEqAllChannelsTo(postEq)

            val limiter = DynamicsProcessing.Limiter(
                /* inUse = */ true,
                /* enabled = */ settings.limiterEnabled,
                /* linkGroup = */ 0,
                /* attackTime = */ 1.0f,
                /* releaseTime = */ 60.0f,
                /* ratio = */ 10.0f,
                /* threshold = */ -1.0f,
                /* postGain = */ 0.0f
            )
            dp.setLimiterAllChannelsTo(limiter)
        }.onFailure { err ->
            Log.w(TAG, "Failed updating DynamicsProcessing settings", err)
        }
    }

    private fun initLegacyEqualizer(audioSessionId: Int) {
        runCatching {
            legacyEqualizer = Equalizer(0, audioSessionId)
        }.onFailure { err ->
            Log.w(TAG, "Hardware Equalizer unavailable on session $audioSessionId", err)
            legacyEqualizer = null
        }
    }

    private fun applyLegacyEqualizer(settings: EqualizerSettings, isEnabled: Boolean) {
        runCatching {
            val eq = legacyEqualizer ?: return
            eq.enabled = isEnabled
            if (!isEnabled) return

            val bandCount = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val minMb = range.getOrNull(0)?.toInt() ?: -1500
            val maxMb = range.getOrNull(1)?.toInt() ?: 1500

            for (bandIdx in 0 until bandCount) {
                val centerFreqHz = (eq.getCenterFreq(bandIdx.toShort()) / 1000).coerceAtLeast(20)
                val targetDb = EqualizerCatalog.interpolateGainForFrequency(centerFreqHz, settings.bandGainsDb) +
                    (settings.preampDb * 0.5f)
                val levelMb = (targetDb * 100f).roundToInt().coerceIn(minMb, maxMb)
                eq.setBandLevel(bandIdx.toShort(), levelMb.toShort())
            }
        }.onFailure { err ->
            Log.w(TAG, "Failed updating legacy Equalizer settings", err)
        }
    }

    private fun initSupplementaryEffects(audioSessionId: Int) {
        runCatching {
            bassBoost = BassBoost(0, audioSessionId)
        }.onFailure { bassBoost = null }

        runCatching {
            @Suppress("DEPRECATION")
            virtualizer = Virtualizer(0, audioSessionId)
        }.onFailure { virtualizer = null }

        runCatching {
            loudnessEnhancer = LoudnessEnhancer(audioSessionId)
        }.onFailure { loudnessEnhancer = null }
    }

    private fun hasAnyEffectAttached(): Boolean {
        return dynamicsProcessing != null ||
            legacyEqualizer != null ||
            bassBoost != null ||
            virtualizer != null ||
            loudnessEnhancer != null
    }

    fun release() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dynamicsProcessing?.release()
            }
        }
        dynamicsProcessing = null

        runCatching { legacyEqualizer?.release() }
        legacyEqualizer = null

        runCatching { bassBoost?.release() }
        bassBoost = null

        runCatching { virtualizer?.release() }
        virtualizer = null

        runCatching { loudnessEnhancer?.release() }
        loudnessEnhancer = null

        currentAudioSessionId = 0
    }

    companion object {
        private const val TAG = "RadioAudioEffectsEngine"
    }
}
