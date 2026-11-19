package com.example.playback

import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.example.data.local.RadioPreferences
import com.example.data.model.RadioStation
import com.example.data.repository.RadioRepository
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Application-scoped playback controller that bridges Jetpack Compose UI / ViewModel
 * with [RadioPlaybackService] via Media3 [MediaController].
 *
 * Guarantees:
 * - Only one radio stream plays at a time.
 * - Navigating between screens never restarts playback.
 * - Switching stations cleanly replaces the previous stream.
 * - Last played station is persisted and restored across app launches.
 */
class RadioPlaybackManager(
    private val context: Context,
    private val repository: RadioRepository,
    private val preferences: RadioPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(
        PlaybackStateSnapshot(isNetworkAvailable = checkNetworkConnected())
    )
    val state: StateFlow<PlaybackStateSnapshot> = _state.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var pendingStationToPlay: RadioStation? = null
    private var sleepTimerJob: Job? = null
    private var connectivityCallback: ConnectivityManager.NetworkCallback? = null

    init {
        observeNetworkConnectivity()
        restoreLastPlayedStationIfNeeded()
        connectToService()
    }

    private fun connectToService() {
        val existingController = mediaController
        if (existingController != null && existingController.isConnected) return
        if (controllerFuture != null) return

        val sessionToken = SessionToken(
            context,
            ComponentName(context, RadioPlaybackService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    if (mediaController === controller) {
                        mediaController = null
                    }
                    controllerFuture = null
                    _state.update { current ->
                        if (current.isPlaying || current.isBuffering) {
                            current.copy(
                                status = PlaybackStatus.STOPPED,
                                isPlaying = false,
                                isBuffering = false
                            )
                        } else {
                            current
                        }
                    }
                }
            })
            .buildAsync()

        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }.onSuccess { controller ->
                    mediaController = controller
                    controller.addListener(controllerListener)
                    syncStateFromController(controller)

                    val pending = pendingStationToPlay
                    if (pending != null) {
                        pendingStationToPlay = null
                        startStreamOnController(controller, pending)
                    }
                }.onFailure { error ->
                    controllerFuture = null
                    mediaController = null
                    _state.update {
                        it.copy(
                            status = PlaybackStatus.ERROR,
                            isPlaying = false,
                            isBuffering = false,
                            errorMessage = "Could not connect to playback service: ${error.localizedMessage ?: "Unknown error"}"
                        )
                    }
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private fun restoreLastPlayedStationIfNeeded() {
        scope.launch {
            val shouldRestore = preferences.restoreLastStationEnabled.first()
            if (!shouldRestore) return@launch
            val savedStation = repository.getLastPlayedStationOnce()
            if (savedStation != null && _state.value.currentStation == null) {
                _state.update { current ->
                    if (current.currentStation == null) {
                        current.copy(
                            currentStation = savedStation,
                            status = PlaybackStatus.IDLE
                        )
                    } else {
                        current
                    }
                }
            }
        }
    }

    private fun observeNetworkConnectivity() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _state.update { current ->
                    val recovered = current.status == PlaybackStatus.ERROR && !current.isNetworkAvailable
                    current.copy(
                        isNetworkAvailable = true,
                        errorMessage = if (recovered) null else current.errorMessage
                    )
                }
            }

            override fun onLost(network: Network) {
                val connected = checkNetworkConnected()
                _state.update { current ->
                    val wasStreaming = current.isPlaying || current.isBuffering
                    current.copy(
                        isNetworkAvailable = connected,
                        status = if (!connected && wasStreaming) PlaybackStatus.RECONNECTING else current.status,
                        errorMessage = if (!connected && wasStreaming) {
                            "Network connection lost. Waiting to reconnect…"
                        } else {
                            current.errorMessage
                        }
                    )
                }
            }
        }
        connectivityCallback = callback
        runCatching { cm.registerDefaultNetworkCallback(callback) }
    }

    private fun checkNetworkConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun playStation(station: RadioStation) {
        val current = _state.value
        // Do not restart unnecessarily if the exact same station is already playing or buffering
        if (current.currentStation?.id == station.id &&
            (current.status == PlaybackStatus.PLAYING || current.status == PlaybackStatus.BUFFERING)
        ) {
            return
        }

        if (!checkNetworkConnected()) {
            _state.update {
                it.copy(
                    currentStation = station,
                    status = PlaybackStatus.ERROR,
                    isPlaying = false,
                    isBuffering = false,
                    isNetworkAvailable = false,
                    errorMessage = "No internet connection. Favourites are available offline, but live streaming requires internet access."
                )
            }
            scope.launch { repository.recordStationPlayed(station) }
            return
        }

        _state.update {
            it.copy(
                currentStation = station,
                status = PlaybackStatus.BUFFERING,
                isPlaying = false,
                isBuffering = true,
                errorMessage = null,
                liveAudioQuality = null
            )
        }
        scope.launch { repository.recordStationPlayed(station) }

        val controller = mediaController
        if (controller != null && controller.isConnected) {
            startStreamOnController(controller, station)
        } else {
            pendingStationToPlay = station
            mediaController = null
            controllerFuture = null
            connectToService()
        }
    }

    private fun startStreamOnController(controller: MediaController, station: RadioStation) {
        scope.launch {
            val resolvedUrl = com.example.data.remote.RadioBrowserApi.resolveDirectStreamUrl(station.streamUrl)
            val resolvedStation = if (resolvedUrl != station.streamUrl) {
                station.copy(streamUrl = resolvedUrl)
            } else {
                station
            }
            // Only start if this station is still the active selection
            if (_state.value.currentStation?.id != station.id) return@launch
            if (!controller.isConnected) return@launch

            // Replacing the single MediaItem on ExoPlayer stops the previous stream immediately
            // without calling stop() / stopSelf() on the foreground MediaSessionService.
            controller.setMediaItem(RadioPlaybackContracts.toMediaItem(resolvedStation))
            controller.prepare()
            controller.play()
        }
    }

    fun togglePlayPause() {
        val controller = mediaController
        val station = _state.value.currentStation ?: return

        if (controller == null || !controller.isConnected) {
            playStation(station)
            return
        }

        if (controller.isPlaying || (controller.playbackState == Player.STATE_BUFFERING && controller.playWhenReady)) {
            controller.pause()
            _state.update {
                it.copy(
                    status = PlaybackStatus.PAUSED,
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = null
                )
            }
        } else {
            if (!checkNetworkConnected()) {
                _state.update {
                    it.copy(
                        status = PlaybackStatus.ERROR,
                        isPlaying = false,
                        isBuffering = false,
                        isNetworkAvailable = false,
                        errorMessage = "Internet connection required to resume live radio stream."
                    )
                }
                return
            }

            if (controller.currentMediaItem == null ||
                controller.playbackState == Player.STATE_IDLE ||
                controller.playbackState == Player.STATE_ENDED
            ) {
                startStreamOnController(controller, station)
            } else {
                controller.play()
            }
            _state.update {
                it.copy(
                    status = PlaybackStatus.BUFFERING,
                    isBuffering = true,
                    errorMessage = null
                )
            }
        }
    }

    fun stopPlayback() {
        cancelSleepTimer()
        val controller = mediaController
        if (controller != null && controller.isConnected) {
            controller.sendCustomCommand(
                SessionCommand(RadioPlaybackContracts.CUSTOM_COMMAND_STOP, Bundle.EMPTY),
                Bundle.EMPTY
            )
        }
        _state.update {
            it.copy(
                status = PlaybackStatus.STOPPED,
                isPlaying = false,
                isBuffering = false,
                errorMessage = null,
                liveAudioQuality = null
            )
        }
    }

    fun retryCurrentStation() {
        val station = _state.value.currentStation ?: return
        _state.update { it.copy(errorMessage = null, status = PlaybackStatus.IDLE) }
        playStation(station)
    }

    fun playNextFavourite(favourites: List<RadioStation>, fallbackCatalog: List<RadioStation>) {
        val pool = if (favourites.isNotEmpty()) favourites else fallbackCatalog
        if (pool.isEmpty()) return
        val currentId = _state.value.currentStation?.id
        val index = pool.indexOfFirst { it.id == currentId }
        val target = if (index == -1) pool.first() else pool[(index + 1) % pool.size]
        playStation(target)
    }

    fun playPreviousFavourite(favourites: List<RadioStation>, fallbackCatalog: List<RadioStation>) {
        val pool = if (favourites.isNotEmpty()) favourites else fallbackCatalog
        if (pool.isEmpty()) return
        val currentId = _state.value.currentStation?.id
        val index = pool.indexOfFirst { it.id == currentId }
        val target = if (index == -1) pool.last() else pool[(index - 1 + pool.size) % pool.size]
        playStation(target)
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _state.update { it.copy(sleepTimerRemainingSeconds = null) }
            return
        }
        sleepTimerJob = scope.launch {
            var remaining = minutes * 60
            while (remaining > 0) {
                _state.update { it.copy(sleepTimerRemainingSeconds = remaining) }
                delay(1_000)
                remaining--
            }
            _state.update { it.copy(sleepTimerRemainingSeconds = null) }
            stopPlayback()
        }
    }

    private fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _state.update { it.copy(sleepTimerRemainingSeconds = null) }
    }

    private val controllerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val station = RadioPlaybackContracts.fromMediaItem(mediaItem)
            if (station != null) {
                _state.update {
                    it.copy(
                        currentStation = station,
                        errorMessage = null,
                        liveAudioQuality = null
                    )
                }
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            val quality = extractLiveAudioQuality(tracks)
            if (quality != null) {
                _state.update { it.copy(liveAudioQuality = quality) }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            mediaController?.let { syncStateFromController(it) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            mediaController?.let { syncStateFromController(it) }
        }

        override fun onPlayerError(error: PlaybackException) {
            val connected = checkNetworkConnected()
            val friendlyMessage = when {
                !connected -> "Internet connection lost. Playback will resume when reconnected."
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                    "Stream connection timed out. Tap Retry to reconnect."
                error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                    "Broadcast server temporarily unavailable. Tap Retry or try another station."
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                    "Unable to reach broadcast server. Check your internet connection."
                else -> "Stream interrupted (${error.errorCodeName}). Tap Retry to reconnect."
            }
            _state.update {
                it.copy(
                    status = if (!connected) PlaybackStatus.RECONNECTING else PlaybackStatus.ERROR,
                    isPlaying = false,
                    isBuffering = false,
                    isNetworkAvailable = connected,
                    errorMessage = friendlyMessage
                )
            }
        }
    }

    private fun syncStateFromController(controller: MediaController) {
        val activeFromMediaItem = RadioPlaybackContracts.fromMediaItem(controller.currentMediaItem)
        val playbackState = controller.playbackState
        val isPlaying = controller.isPlaying
        val playWhenReady = controller.playWhenReady

        val mappedStatus = when {
            controller.playerError != null -> PlaybackStatus.ERROR
            playbackState == Player.STATE_BUFFERING -> PlaybackStatus.BUFFERING
            isPlaying -> PlaybackStatus.PLAYING
            playbackState == Player.STATE_READY && !playWhenReady -> PlaybackStatus.PAUSED
            playbackState == Player.STATE_ENDED -> PlaybackStatus.STOPPED
            playbackState == Player.STATE_IDLE && _state.value.status == PlaybackStatus.STOPPED -> PlaybackStatus.STOPPED
            else -> _state.value.status.takeIf { it == PlaybackStatus.RECONNECTING || it == PlaybackStatus.ERROR }
                ?: PlaybackStatus.IDLE
        }

        val liveQuality = extractLiveAudioQuality(controller.currentTracks)

        _state.update { current ->
            current.copy(
                currentStation = activeFromMediaItem ?: current.currentStation,
                status = mappedStatus,
                isPlaying = isPlaying,
                isBuffering = playbackState == Player.STATE_BUFFERING,
                liveAudioQuality = when {
                    mappedStatus == PlaybackStatus.STOPPED -> null
                    liveQuality != null -> liveQuality
                    else -> current.liveAudioQuality
                },
                errorMessage = if (mappedStatus == PlaybackStatus.PLAYING || mappedStatus == PlaybackStatus.BUFFERING) {
                    null
                } else {
                    current.errorMessage
                }
            )
        }
    }

    private fun extractLiveAudioQuality(tracks: Tracks): String? {
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_AUDIO) continue
            for (i in 0 until group.length) {
                if (!group.isTrackSelected(i)) continue
                val format = group.getTrackFormat(i)
                val codec = when (format.sampleMimeType) {
                    MimeTypes.AUDIO_AAC -> "AAC"
                    MimeTypes.AUDIO_MPEG -> "MP3"
                    MimeTypes.AUDIO_OPUS -> "OPUS"
                    MimeTypes.AUDIO_FLAC -> "FLAC"
                    MimeTypes.AUDIO_VORBIS -> "OGG"
                    MimeTypes.AUDIO_WAV -> "WAV"
                    else -> format.codecs?.substringBefore('.')?.uppercase()
                        ?.takeIf { it.isNotBlank() && it.length <= 6 }
                }
                val bitrateKbps = when {
                    format.averageBitrate > 0 -> format.averageBitrate / 1000
                    format.peakBitrate > 0 -> format.peakBitrate / 1000
                    format.bitrate > 0 -> format.bitrate / 1000
                    else -> 0
                }
                val sampleRateKhz = if (format.sampleRate > 0) {
                    val khz = format.sampleRate / 1000.0
                    if (khz % 1.0 == 0.0) "${khz.toInt()} kHz" else String.format(java.util.Locale.US, "%.1f kHz", khz)
                } else null
                val channels = when (format.channelCount) {
                    1 -> "Mono"
                    2 -> "Stereo"
                    in 3..8 -> "${format.channelCount}ch"
                    else -> null
                }

                val parts = buildList {
                    if (!codec.isNullOrBlank()) add(codec)
                    if (bitrateKbps > 0) add("$bitrateKbps kbps")
                    if (sampleRateKhz != null) {
                        add(if (channels != null) "$sampleRateKhz $channels" else sampleRateKhz)
                    } else if (channels != null) {
                        add(channels)
                    }
                }
                if (parts.isNotEmpty()) {
                    return parts.joinToString(" • ")
                }
            }
        }
        return null
    }
}
