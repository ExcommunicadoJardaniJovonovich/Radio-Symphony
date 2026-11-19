package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.playback.PlaybackStateSnapshot
import com.example.playback.PlaybackStatus
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalRadioColors

@Composable
fun FullScreenRadioPlayer(
    playbackState: PlaybackStateSnapshot,
    isFavourite: Boolean,
    favouritesCount: Int,
    equalizerEnabled: Boolean,
    onCollapse: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onToggleFavourite: () -> Unit,
    onPreviousFavourite: () -> Unit,
    onNextFavourite: () -> Unit,
    onSetSleepTimer: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onCollapse)

    val station = playbackState.currentStation ?: return
    val radioColors = LocalRadioColors.current
    val accent = Color(station.accentColorHex)
    val bgColor = MaterialTheme.colorScheme.background
    var sleepMenuExpanded by remember { mutableStateOf(false) }

    val (statusLabel, statusDotColor) = when (playbackState.status) {
        PlaybackStatus.PLAYING -> stringResource(R.string.status_live) to radioColors.livePulse
        PlaybackStatus.BUFFERING -> stringResource(R.string.status_buffering) to MaterialTheme.colorScheme.primary
        PlaybackStatus.RECONNECTING -> stringResource(R.string.status_reconnecting) to MaterialTheme.colorScheme.primary
        PlaybackStatus.PAUSED -> stringResource(R.string.status_paused) to MaterialTheme.colorScheme.onSurfaceVariant
        PlaybackStatus.STOPPED -> stringResource(R.string.status_stopped) to MaterialTheme.colorScheme.onSurfaceVariant
        PlaybackStatus.ERROR -> "Connection Issue" to MaterialTheme.colorScheme.error
        PlaybackStatus.IDLE -> stringResource(R.string.status_ready) to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.24f),
                            accent.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.26f),
                        radius = size.width * 0.85f
                    )
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("full_screen_radio_player"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("full_player_collapse_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.action_collapse_player),
                        modifier = Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "NOW TUNED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            letterSpacing = 1.8.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Radio Symphony",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenEqualizer,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("full_player_equalizer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = "Open Studio Equalizer",
                            tint = if (equalizerEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { sleepMenuExpanded = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("full_player_sleep_timer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bedtime,
                                contentDescription = "Sleep timer",
                                tint = if (playbackState.sleepTimerRemainingSeconds != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        DropdownMenu(
                            expanded = sleepMenuExpanded,
                            onDismissRequest = { sleepMenuExpanded = false }
                        ) {
                            listOf(15, 30, 45, 60).forEach { mins ->
                                DropdownMenuItem(
                                    text = { Text("Sleep in $mins min") },
                                    onClick = {
                                        sleepMenuExpanded = false
                                        onSetSleepTimer(mins)
                                    }
                                )
                            }
                            if (playbackState.sleepTimerRemainingSeconds != null) {
                                DropdownMenuItem(
                                    text = { Text("Turn off sleep timer") },
                                    onClick = {
                                        sleepMenuExpanded = false
                                        onSetSleepTimer(null)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Streaming Status Pill
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, statusDotColor.copy(alpha = 0.45f)),
                modifier = Modifier.testTag("full_player_status_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (playbackState.isBuffering || playbackState.status == PlaybackStatus.RECONNECTING) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(14.dp)
                                .testTag("full_player_buffering_indicator"),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(statusDotColor)
                        )
                    }
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    playbackState.sleepTimerRemainingSeconds?.let { sec ->
                        val m = sec / 60
                        val s = sec % 60
                        Text(
                            text = "• ${m}m ${s.toString().padStart(2, '0')}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Large Station Logo Emblem
            StationLogoEmblem(
                station = station,
                size = 200.dp,
                isPlaying = playbackState.isPlaying
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Analog tuning scale + frequency readout
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BandBadge(band = station.band)
                Text(
                    text = station.frequency,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            AnalogTuningScale(
                station = station,
                isPlaying = playbackState.isPlaying
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Station Name & Tagline
            Text(
                text = station.name,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (station.tagline.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = station.tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // District & Language Metadata Pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = "District",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = station.district,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Language,
                            contentDescription = "Language",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = station.language,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Live Decoded Audio Quality Telemetry Pill (tapping opens Studio Equalizer)
            val qualityText = playbackState.liveAudioQuality ?: "${station.qualityBadge} • Best Stream Selected"
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = radioColors.successGreen.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, radioColors.successGreen.copy(alpha = 0.35f)),
                modifier = Modifier
                    .clickable(onClick = onOpenEqualizer)
                    .testTag("full_player_audio_quality_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = "Audio quality and equalizer",
                        modifier = Modifier.size(15.dp),
                        tint = radioColors.successGreen
                    )
                    Text(
                        text = if (equalizerEnabled) "$qualityText • EQ ON" else "$qualityText • EQ",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = radioColors.successGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Playback Error Banner (when applicable)
            if (playbackState.errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("full_player_error_banner"),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = "Playback error",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = playbackState.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = onRetry,
                            modifier = Modifier.testTag("full_player_retry_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.action_retry),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // Primary Transport Controls: Prev Favourite, Play/Pause, Next Favourite
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FilledTonalIconButton(
                    onClick = onPreviousFavourite,
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("full_player_prev_favourite_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = stringResource(R.string.action_previous_favourite),
                        modifier = Modifier.size(30.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onTogglePlayPause)
                        .testTag("full_player_play_pause_button"),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        playbackState.isBuffering || playbackState.status == PlaybackStatus.RECONNECTING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(34.dp),
                                strokeWidth = 3.5.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        playbackState.isPlaying -> {
                            Icon(
                                imageVector = Icons.Filled.Pause,
                                contentDescription = stringResource(R.string.action_pause),
                                modifier = Modifier.size(38.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.action_play),
                                modifier = Modifier.size(38.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                FilledTonalIconButton(
                    onClick = onNextFavourite,
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("full_player_next_favourite_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = stringResource(R.string.action_next_favourite),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (favouritesCount > 0) {
                    "Skip cycles through your $favouritesCount favourite station${if (favouritesCount == 1) "" else "s"}"
                } else {
                    "Tap ♥ to save favourites — Skip cycles stations"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Secondary Controls: Stop + Favourite Heart
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("full_player_stop_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = stringResource(R.string.action_stop),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_stop))
                }

                Button(
                    onClick = onToggleFavourite,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("full_player_favourite_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFavourite) {
                            radioColors.heartActive
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        contentColor = if (isFavourite) {
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                ) {
                    Icon(
                        imageVector = if (isFavourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(R.string.action_toggle_favourite),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isFavourite) "Favourited" else "Favourite")
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Background playback assurance caption
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Headphones,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Background audio active: continues when locked, minimized, or controlled via Bluetooth & car audio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
