package com.example.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.data.model.CuratedStations
import com.example.data.model.RadioBand
import com.example.data.model.RadioStation

enum class PlaybackStatus {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    RECONNECTING,
    STOPPED,
    ERROR
}

data class PlaybackStateSnapshot(
    val currentStation: RadioStation? = null,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val errorMessage: String? = null,
    val isNetworkAvailable: Boolean = true,
    val sleepTimerRemainingSeconds: Int? = null,
    val liveAudioQuality: String? = null
)

object RadioPlaybackContracts {
    const val NOTIFICATION_CHANNEL_ID = "radio_symphony_playback_channel"
    const val EXTRA_OPEN_FULL_PLAYER = "com.example.radiosymphony.EXTRA_OPEN_FULL_PLAYER"

    const val CUSTOM_COMMAND_STOP = "com.example.radiosymphony.COMMAND_STOP"
    const val CUSTOM_COMMAND_PREV_FAV = "com.example.radiosymphony.COMMAND_PREV_FAV"
    const val CUSTOM_COMMAND_NEXT_FAV = "com.example.radiosymphony.COMMAND_NEXT_FAV"

    private const val KEY_FREQUENCY = "station_frequency"
    private const val KEY_BAND = "station_band"
    private const val KEY_DISTRICT = "station_district"
    private const val KEY_LANGUAGE = "station_language"
    private const val KEY_GENRE = "station_genre"
    private const val KEY_FALLBACK_URL = "station_fallback_url"
    private const val KEY_ACCENT_COLOR = "station_accent_color"
    private const val KEY_LOGO_CODE = "station_logo_code"
    private const val KEY_TAGLINE = "station_tagline"
    private const val KEY_STREAM_URL = "station_stream_url"
    private const val KEY_ACTIVE_URL = "station_active_url"

    fun toMediaItem(station: RadioStation, useFallbackUrl: Boolean = false): MediaItem {
        val targetUrl = if (useFallbackUrl && station.fallbackStreamUrl.isNotBlank()) {
            station.fallbackStreamUrl
        } else {
            station.streamUrl
        }
        val targetUri = Uri.parse(targetUrl)

        val extras = Bundle().apply {
            putString(KEY_FREQUENCY, station.frequency)
            putString(KEY_BAND, station.band.name)
            putString(KEY_DISTRICT, station.district)
            putString(KEY_LANGUAGE, station.language)
            putString(KEY_GENRE, station.genre)
            putString(KEY_FALLBACK_URL, station.fallbackStreamUrl)
            putLong(KEY_ACCENT_COLOR, station.accentColorHex)
            putString(KEY_LOGO_CODE, station.logoCode)
            putString(KEY_TAGLINE, station.tagline)
            putString(KEY_STREAM_URL, station.streamUrl)
            putString(KEY_ACTIVE_URL, targetUrl)
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(station.name)
            .setArtist("${station.formattedFrequency} • ${station.district}")
            .setSubtitle("${station.language} • ${station.genre}")
            .setAlbumTitle("Radio Symphony")
            .setStation(station.name)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setExtras(extras)
            .build()

        // Include RequestMetadata.mediaUri because Media3 intentionally strips
        // MediaItem.localConfiguration when sending MediaItems over MediaController IPC.
        val requestMetadata = MediaItem.RequestMetadata.Builder()
            .setMediaUri(targetUri)
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setMediaId(station.id)
            .setUri(targetUri)
            .setMediaMetadata(metadata)
            .setRequestMetadata(requestMetadata)
            .build()
    }

    /**
     * Restores the stream URI onto a [MediaItem] inside [RadioPlaybackService]
     * after Media3 IPC strips [MediaItem.localConfiguration].
     */
    fun resolveForServicePlayback(item: MediaItem): MediaItem {
        if (item.localConfiguration?.uri != null) {
            return item
        }

        val resolvedUri: Uri? = item.requestMetadata.mediaUri
            ?: item.mediaMetadata.extras?.getString(KEY_ACTIVE_URL)
                ?.takeIf { it.isNotBlank() }
                ?.let { Uri.parse(it) }
            ?: item.mediaMetadata.extras?.getString(KEY_STREAM_URL)
                ?.takeIf { it.isNotBlank() }
                ?.let { Uri.parse(it) }
            ?: CuratedStations.findById(item.mediaId)?.streamUrl
                ?.takeIf { it.isNotBlank() }
                ?.let { Uri.parse(it) }

        return if (resolvedUri != null) {
            item.buildUpon()
                .setUri(resolvedUri)
                .build()
        } else {
            item
        }
    }

    fun fromMediaItem(mediaItem: MediaItem?): RadioStation? {
        if (mediaItem == null) return null
        val metadata = mediaItem.mediaMetadata
        val extras = metadata.extras
        val id = mediaItem.mediaId.ifBlank { return null }
        val name = metadata.title?.toString()?.ifBlank { "Radio Station" } ?: "Radio Station"
        val streamUrl = extras?.getString(KEY_STREAM_URL)
            ?: mediaItem.localConfiguration?.uri?.toString()
            ?: mediaItem.requestMetadata.mediaUri?.toString()
            ?: CuratedStations.findById(id)?.streamUrl
            ?: ""

        return RadioStation(
            id = id,
            name = name,
            frequency = extras?.getString(KEY_FREQUENCY) ?: "Live",
            band = RadioBand.fromString(extras?.getString(KEY_BAND)),
            district = extras?.getString(KEY_DISTRICT) ?: "Broadcast",
            language = extras?.getString(KEY_LANGUAGE) ?: "Multilingual",
            genre = extras?.getString(KEY_GENRE) ?: "Live Radio",
            streamUrl = streamUrl,
            fallbackStreamUrl = extras?.getString(KEY_FALLBACK_URL) ?: "",
            accentColorHex = extras?.getLong(KEY_ACCENT_COLOR, 0xFFF5A623L) ?: 0xFFF5A623L,
            logoCode = extras?.getString(KEY_LOGO_CODE) ?: name.take(2).uppercase(),
            tagline = extras?.getString(KEY_TAGLINE) ?: ""
        )
    }
}
