package com.example.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.MainActivity
import com.example.R
import com.example.RadioSymphonyApp
import com.example.data.model.EqualizerSettings
import com.example.data.model.RadioStation
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class RadioPlaybackService : MediaSessionService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var sessionPlayer: Player
    private var mediaSession: MediaSession? = null

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var reconnectJob: Job? = null

    private var activeStation: RadioStation? = null
    private var triedFallbackForStationId: String? = null
    private var shouldResumeOnNetworkRestore: Boolean = false
    private var userExplicitlyPausedOrStopped: Boolean = false

    private val audioEffectsEngine = RadioAudioEffectsEngine()
    private var currentEqualizerSettings: EqualizerSettings = EqualizerSettings()

    private val stopSessionCommand = SessionCommand(RadioPlaybackContracts.CUSTOM_COMMAND_STOP, Bundle.EMPTY)
    private val prevFavSessionCommand = SessionCommand(RadioPlaybackContracts.CUSTOM_COMMAND_PREV_FAV, Bundle.EMPTY)
    private val nextFavSessionCommand = SessionCommand(RadioPlaybackContracts.CUSTOM_COMMAND_NEXT_FAV, Bundle.EMPTY)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        configureNotificationProvider()
        initializePlayerAndSession()
        observeEqualizerPreferences()
        registerNetworkMonitor()
    }

    private fun observeEqualizerPreferences() {
        val app = application as? RadioSymphonyApp ?: return
        serviceScope.launch {
            app.preferences.equalizerSettings.collect { settings ->
                currentEqualizerSettings = settings
                if (::exoPlayer.isInitialized && exoPlayer.audioSessionId > 0) {
                    audioEffectsEngine.attachToSession(exoPlayer.audioSessionId, settings)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(RadioPlaybackContracts.NOTIFICATION_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    RadioPlaybackContracts.NOTIFICATION_CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.notification_channel_description)
                    setShowBadge(false)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun configureNotificationProvider() {
        val provider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(RadioPlaybackContracts.NOTIFICATION_CHANNEL_ID)
            .setChannelName(R.string.notification_channel_name)
            .build()
        provider.setSmallIcon(R.drawable.ic_radio_notification)
        setMediaNotificationProvider(provider)
    }

    private fun initializePlayerAndSession() {
        val okHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("RadioSymphony/1.0 (Android; Media3 ExoPlayer)")

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 45_000,
                /* bufferForPlaybackMs = */ 2_000,
                /* bufferForPlaybackAfterRebufferMs = */ 4_000
            )
            .build()

        // Force ExoPlayer to always pick the highest-bitrate audio variant on adaptive HLS streams
        val trackSelector = DefaultTrackSelector(this).apply {
            setParameters(
                buildUponParameters()
                    .setForceHighestSupportedBitrate(true)
                    .setMaxAudioBitrate(Int.MAX_VALUE)
                    .setMaxAudioChannelCount(Int.MAX_VALUE)
            )
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.CONTENT_TYPE_MUSIC)
            .build()

        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        exoPlayer.addListener(playerListener)

        // Wrap ExoPlayer so Bluetooth headsets, car audio steering controls, and lock-screen
        // Next/Previous buttons navigate Favourite stations, and Stop releases resources cleanly.
        sessionPlayer = object : ForwardingPlayer(exoPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands()
                    .buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(Player.COMMAND_STOP)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    Player.COMMAND_STOP -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun hasNextMediaItem(): Boolean = true
            override fun hasPreviousMediaItem(): Boolean = true

            override fun seekToNext() {
                switchAdjacentStation(forward = true)
            }

            override fun seekToNextMediaItem() {
                switchAdjacentStation(forward = true)
            }

            override fun seekToPrevious() {
                switchAdjacentStation(forward = false)
            }

            override fun seekToPreviousMediaItem() {
                switchAdjacentStation(forward = false)
            }

            override fun setMediaItem(mediaItem: MediaItem) {
                userExplicitlyPausedOrStopped = false
                shouldResumeOnNetworkRestore = false
                reconnectJob?.cancel()
                val resolved = RadioPlaybackContracts.resolveForServicePlayback(mediaItem)
                RadioPlaybackContracts.fromMediaItem(resolved)?.let {
                    activeStation = it
                    triedFallbackForStationId = null
                }
                super.setMediaItem(resolved)
            }

            override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
                userExplicitlyPausedOrStopped = false
                shouldResumeOnNetworkRestore = false
                reconnectJob?.cancel()
                val resolved = mediaItems.map { RadioPlaybackContracts.resolveForServicePlayback(it) }.toMutableList()
                resolved.firstOrNull()?.let { first ->
                    RadioPlaybackContracts.fromMediaItem(first)?.let {
                        activeStation = it
                        triedFallbackForStationId = null
                    }
                }
                super.setMediaItems(resolved, resetPosition)
            }

            override fun setMediaItems(
                mediaItems: MutableList<MediaItem>,
                startIndex: Int,
                startPositionMs: Long
            ) {
                userExplicitlyPausedOrStopped = false
                shouldResumeOnNetworkRestore = false
                reconnectJob?.cancel()
                val resolved = mediaItems.map { RadioPlaybackContracts.resolveForServicePlayback(it) }.toMutableList()
                resolved.getOrNull(startIndex.coerceAtLeast(0))?.let { item ->
                    RadioPlaybackContracts.fromMediaItem(item)?.let {
                        activeStation = it
                        triedFallbackForStationId = null
                    }
                }
                super.setMediaItems(resolved, startIndex, startPositionMs)
            }

            override fun play() {
                userExplicitlyPausedOrStopped = false
                if (exoPlayer.playbackState == Player.STATE_IDLE && exoPlayer.currentMediaItem != null) {
                    exoPlayer.prepare()
                }
                super.play()
            }

            override fun pause() {
                userExplicitlyPausedOrStopped = true
                shouldResumeOnNetworkRestore = false
                reconnectJob?.cancel()
                super.pause()
            }

            override fun stop() {
                performCleanStop()
            }
        }

        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(RadioPlaybackContracts.EXTRA_OPEN_FULL_PLAYER, true)
        }
        val sessionPendingIntent = PendingIntent.getActivity(
            this,
            1001,
            sessionActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopButton = CommandButton.Builder(CommandButton.ICON_STOP)
            .setDisplayName(getString(R.string.action_stop))
            .setCustomIconResId(R.drawable.ic_stop_playback)
            .setSessionCommand(stopSessionCommand)
            .setSlots(CommandButton.SLOT_OVERFLOW, CommandButton.SLOT_BACK_SECONDARY)
            .build()

        val prevFavButton = CommandButton.Builder(CommandButton.ICON_PREVIOUS)
            .setDisplayName(getString(R.string.action_previous_favourite))
            .setCustomIconResId(R.drawable.ic_skip_previous_fav)
            .setSessionCommand(prevFavSessionCommand)
            .setSlots(CommandButton.SLOT_BACK)
            .build()

        val nextFavButton = CommandButton.Builder(CommandButton.ICON_NEXT)
            .setDisplayName(getString(R.string.action_next_favourite))
            .setCustomIconResId(R.drawable.ic_skip_next_fav)
            .setSessionCommand(nextFavSessionCommand)
            .setSlots(CommandButton.SLOT_FORWARD)
            .build()

        mediaSession = MediaSession.Builder(this, sessionPlayer)
            .setId("radio_symphony_session")
            .setSessionActivity(sessionPendingIntent)
            .setMediaButtonPreferences(listOf(prevFavButton, nextFavButton, stopButton))
            .setCallback(RadioSessionCallback(listOf(prevFavButton, nextFavButton, stopButton)))
            .build()
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val station = RadioPlaybackContracts.fromMediaItem(mediaItem)
            if (station != null) {
                activeStation = station
                triedFallbackForStationId = null
                userExplicitlyPausedOrStopped = false
                val app = application as? RadioSymphonyApp
                serviceScope.launch {
                    app?.repository?.recordStationPlayed(station)
                }
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            if (audioSessionId > 0) {
                audioEffectsEngine.attachToSession(audioSessionId, currentEqualizerSettings)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                if (exoPlayer.audioSessionId > 0) {
                    audioEffectsEngine.attachToSession(exoPlayer.audioSessionId, currentEqualizerSettings)
                }
                if (exoPlayer.playWhenReady) {
                    shouldResumeOnNetworkRestore = false
                    reconnectJob?.cancel()
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "ExoPlayer stream error: ${error.errorCodeName}", error)
            val station = activeStation ?: RadioPlaybackContracts.fromMediaItem(exoPlayer.currentMediaItem)
            if (station != null &&
                station.fallbackStreamUrl.isNotBlank() &&
                triedFallbackForStationId != station.id
            ) {
                // Attempt fallback stream seamlessly before surfacing a playback error
                triedFallbackForStationId = station.id
                exoPlayer.setMediaItem(RadioPlaybackContracts.toMediaItem(station, useFallbackUrl = true))
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
                return
            }

            if (!userExplicitlyPausedOrStopped) {
                scheduleReconnectIfEnabled()
            }
        }
    }

    private fun scheduleReconnectIfEnabled() {
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            val app = application as? RadioSymphonyApp ?: return@launch
            val autoReconnect = app.preferences.autoReconnectEnabled.first()
            if (!autoReconnect) return@launch

            shouldResumeOnNetworkRestore = true
            if (isNetworkConnected()) {
                delay(3_000)
                if (!userExplicitlyPausedOrStopped && activeStation != null) {
                    val station = activeStation!!
                    exoPlayer.setMediaItem(RadioPlaybackContracts.toMediaItem(station, useFallbackUrl = false))
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                }
            }
        }
    }

    private fun registerNetworkMonitor() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        connectivityManager = cm
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                serviceScope.launch {
                    val app = application as? RadioSymphonyApp ?: return@launch
                    val autoReconnect = app.preferences.autoReconnectEnabled.first()
                    if (autoReconnect && shouldResumeOnNetworkRestore && !userExplicitlyPausedOrStopped) {
                        val station = activeStation ?: return@launch
                        delay(800)
                        if (!userExplicitlyPausedOrStopped) {
                            shouldResumeOnNetworkRestore = false
                            exoPlayer.setMediaItem(RadioPlaybackContracts.toMediaItem(station))
                            exoPlayer.prepare()
                            exoPlayer.playWhenReady = true
                        }
                    }
                }
            }
        }
        networkCallback = callback
        runCatching { cm.registerDefaultNetworkCallback(callback) }
    }

    private fun isNetworkConnected(): Boolean {
        val cm = connectivityManager ?: return true
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun switchAdjacentStation(forward: Boolean) {
        serviceScope.launch {
            val app = application as? RadioSymphonyApp ?: return@launch
            val favourites = withContext(Dispatchers.IO) {
                app.repository.getAllFavouriteStationsOnce()
            }
            val pool = if (favourites.size >= 2) favourites else app.repository.curatedStations
            if (pool.isEmpty()) return@launch

            val currentId = activeStation?.id ?: exoPlayer.currentMediaItem?.mediaId
            val currentIndex = pool.indexOfFirst { it.id == currentId }
            val nextIndex = when {
                currentIndex == -1 -> 0
                forward -> (currentIndex + 1) % pool.size
                else -> (currentIndex - 1 + pool.size) % pool.size
            }
            val nextStation = pool[nextIndex]
            activeStation = nextStation
            triedFallbackForStationId = null
            userExplicitlyPausedOrStopped = false
            shouldResumeOnNetworkRestore = false

            exoPlayer.setMediaItem(RadioPlaybackContracts.toMediaItem(nextStation))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    private fun performCleanStop() {
        userExplicitlyPausedOrStopped = true
        shouldResumeOnNetworkRestore = false
        reconnectJob?.cancel()
        audioEffectsEngine.release()
        exoPlayer.playWhenReady = false
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private inner class RadioSessionCallback(
        private val customButtons: List<CommandButton>
    ) : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(stopSessionCommand)
                .add(prevFavSessionCommand)
                .add(nextFavSessionCommand)
                .build()

            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                .buildUpon()
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_STOP)
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .setMediaButtonPreferences(customButtons)
                .build()
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolvedItems = mediaItems
                .map { RadioPlaybackContracts.resolveForServicePlayback(it) }
                .toMutableList()
            return Futures.immediateFuture(resolvedItems)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                RadioPlaybackContracts.CUSTOM_COMMAND_STOP -> performCleanStop()
                RadioPlaybackContracts.CUSTOM_COMMAND_PREV_FAV -> switchAdjacentStation(forward = false)
                RadioPlaybackContracts.CUSTOM_COMMAND_NEXT_FAV -> switchAdjacentStation(forward = true)
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch {
                val app = application as? RadioSymphonyApp
                val restoredStation = withContext(Dispatchers.IO) {
                    app?.repository?.getLastPlayedStationOnce()
                } ?: app?.repository?.curatedStations?.firstOrNull()

                if (restoredStation != null) {
                    activeStation = restoredStation
                    val item = RadioPlaybackContracts.toMediaItem(restoredStation)
                    future.set(MediaSession.MediaItemsWithStartPosition(listOf(item), 0, C.TIME_UNSET))
                } else {
                    future.set(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, C.TIME_UNSET))
                }
            }
            return future
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || (!player.playWhenReady && player.playbackState != Player.STATE_BUFFERING)) {
            performCleanStop()
        }
    }

    override fun onDestroy() {
        reconnectJob?.cancel()
        networkCallback?.let { cb ->
            runCatching { connectivityManager?.unregisterNetworkCallback(cb) }
        }
        networkCallback = null
        audioEffectsEngine.release()
        serviceScope.cancel()
        mediaSession?.run {
            exoPlayer.removeListener(playerListener)
            exoPlayer.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RadioPlaybackService"
    }
}
