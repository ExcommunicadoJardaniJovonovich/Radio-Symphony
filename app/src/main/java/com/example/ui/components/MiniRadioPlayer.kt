package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.playback.PlaybackStateSnapshot
import com.example.playback.PlaybackStatus
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalRadioColors

@Composable
fun MiniRadioPlayer(
    playbackState: PlaybackStateSnapshot,
    isFavourite: Boolean,
    equalizerEnabled: Boolean = false,
    onTogglePlayPause: () -> Unit,
    onToggleFavourite: () -> Unit,
    onOpenEqualizer: () -> Unit = {},
    onExpandPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val station = playbackState.currentStation ?: return
    val radioColors = LocalRadioColors.current
    val accent = Color(station.accentColorHex)

    val statusHint = when (playbackState.status) {
        PlaybackStatus.PLAYING -> "LIVE"
        PlaybackStatus.BUFFERING -> "Buffering…"
        PlaybackStatus.RECONNECTING -> "Reconnecting…"
        PlaybackStatus.ERROR -> "Error • Tap to view"
        PlaybackStatus.PAUSED -> "Paused"
        PlaybackStatus.STOPPED -> "Stopped"
        PlaybackStatus.IDLE -> "Ready"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("mini_radio_player"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (playbackState.isPlaying) accent.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onExpandPlayer)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StationLogoEmblem(
                station = station,
                size = 46.dp,
                isPlaying = playbackState.isPlaying
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = station.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (playbackState.isPlaying) {
                        LiveEqualizerBars(
                            isActive = true,
                            color = MaterialTheme.colorScheme.primary,
                            maxHeight = 12.dp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = station.formattedFrequency,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                    Text(
                        text = "• $statusHint",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (playbackState.status == PlaybackStatus.ERROR) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onToggleFavourite,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("mini_player_favourite_button")
            ) {
                Icon(
                    imageVector = if (isFavourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(R.string.action_toggle_favourite),
                    tint = if (isFavourite) radioColors.heartActive else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onTogglePlayPause)
                    .testTag("mini_player_play_pause_button"),
                contentAlignment = Alignment.Center
            ) {
                when {
                    playbackState.isBuffering || playbackState.status == PlaybackStatus.RECONNECTING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    playbackState.isPlaying -> {
                        Icon(
                            imageVector = Icons.Filled.Pause,
                            contentDescription = stringResource(R.string.action_pause),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.action_play),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            IconButton(
                onClick = onOpenEqualizer,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("mini_player_equalizer_button")
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
        }
    }
}
