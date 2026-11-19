package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RadioBand
import com.example.data.model.RadioStation
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalRadioColors
import com.example.ui.theme.SpaceGroteskFontFamily
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BandBadge(
    band: RadioBand,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    val (fg, bg) = when (band) {
        RadioBand.FM -> radioColors.fmBadge to radioColors.fmBadgeContainer
        RadioBand.AM -> radioColors.amBadge to radioColors.amBadgeContainer
        RadioBand.SW -> radioColors.swBadge to radioColors.swBadgeContainer
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = bg
    ) {
        Text(
            text = band.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold
            ),
            color = fg,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun StationLogoEmblem(
    station: RadioStation,
    size: Dp,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = Color(station.accentColorHex)
    val infiniteTransition = rememberInfiniteTransition(label = "station_emblem")
    val spinDegrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dial_rotation"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val isLarge = size >= 120.dp
    val cornerRadius = if (isLarge) CircleShape else RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .size(size)
            .clip(cornerRadius)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.28f),
                        Color(0xFF141A26),
                        accent.copy(alpha = 0.14f)
                    )
                )
            )
            .border(
                width = if (isPlaying) 1.75.dp else 1.dp,
                color = if (isPlaying) accent.copy(alpha = 0.85f) else accent.copy(alpha = 0.35f),
                shape = cornerRadius
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = this.size.minDimension / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)

            // Concentric acoustic tuning rings
            drawCircle(
                color = accent.copy(alpha = if (isPlaying) pulseAlpha * 0.45f else 0.14f),
                radius = radius * 0.84f,
                center = center,
                style = Stroke(width = if (isLarge) 2.5f else 1.5f)
            )
            drawCircle(
                color = accent.copy(alpha = 0.22f),
                radius = radius * 0.64f,
                center = center,
                style = Stroke(width = if (isLarge) 2f else 1.2f)
            )

            // Rotating broadcast tick marks when active
            rotate(degrees = if (isPlaying) spinDegrees else 0f, pivot = center) {
                val ticks = if (isLarge) 24 else 12
                for (i in 0 until ticks) {
                    val angleRad = Math.toRadians((i * (360.0 / ticks)))
                    val outer = radius * 0.92f
                    val inner = radius * if (i % 3 == 0) 0.78f else 0.84f
                    val start = Offset(
                        center.x + (cos(angleRad) * inner).toFloat(),
                        center.y + (sin(angleRad) * inner).toFloat()
                    )
                    val end = Offset(
                        center.x + (cos(angleRad) * outer).toFloat(),
                        center.y + (sin(angleRad) * outer).toFloat()
                    )
                    drawLine(
                        color = accent.copy(alpha = if (i % 3 == 0) 0.75f else 0.32f),
                        start = start,
                        end = end,
                        strokeWidth = if (isLarge) 3f else 1.8f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = station.logoCode.take(3),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = when {
                        size >= 160.dp -> 42.sp
                        size >= 100.dp -> 28.sp
                        size >= 52.dp -> 17.sp
                        else -> 14.sp
                    }
                ),
                color = Color.White
            )
            if (isLarge) {
                Text(
                    text = station.band.label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    ),
                    color = accent
                )
            }
        }
    }
}

@Composable
fun LiveEqualizerBars(
    isActive: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    barWidth: Dp = 3.dp,
    maxHeight: Dp = 16.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(360, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq3"
    )

    val heights = if (isActive) listOf(bar1, bar2, bar3) else listOf(0.3f, 0.55f, 0.35f)
    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(maxHeight * fraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

@Composable
fun AnalogTuningScale(
    station: RadioStation,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = Color(station.accentColorHex)
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val infiniteTransition = rememberInfiniteTransition(label = "tuner_needle")
    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "needle_glow"
    )

    val needleFraction = ((station.id.hashCode() and 0x7FFFFFFF) % 60 + 20) / 100f

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
    ) {
        val totalTicks = 40
        val step = size.width / totalTicks
        val centerY = size.height * 0.55f

        for (i in 0..totalTicks) {
            val x = i * step
            val isMajor = i % 5 == 0
            val halfLen = if (isMajor) 12.dp.toPx() else 6.dp.toPx()
            drawLine(
                color = if (isMajor) outlineColor.copy(alpha = 0.9f) else outlineColor.copy(alpha = 0.45f),
                start = Offset(x, centerY - halfLen),
                end = Offset(x, centerY + halfLen * 0.6f),
                strokeWidth = if (isMajor) 2.5f else 1.5f,
                cap = StrokeCap.Round
            )
        }

        val needleX = size.width * needleFraction
        drawLine(
            color = accent.copy(alpha = if (isPlaying) shimmer else 0.8f),
            start = Offset(needleX, 2.dp.toPx()),
            end = Offset(needleX, size.height - 2.dp.toPx()),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = accent,
            radius = 5.dp.toPx(),
            center = Offset(needleX, centerY)
        )
    }
}
