package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.data.model.AppThemeMode

@Immutable
data class RadioExtendedColors(
    val fmBadge: Color,
    val fmBadgeContainer: Color,
    val amBadge: Color,
    val amBadgeContainer: Color,
    val swBadge: Color,
    val swBadgeContainer: Color,
    val livePulse: Color,
    val heartActive: Color,
    val successGreen: Color
)

val LocalRadioColors = staticCompositionLocalOf {
    RadioExtendedColors(
        fmBadge = BroadcastAmber,
        fmBadgeContainer = BroadcastAmberContainer,
        amBadge = MediumwaveEmerald,
        amBadgeContainer = MediumwaveEmeraldContainer,
        swBadge = ShortwaveCyan,
        swBadgeContainer = ShortwaveCyanContainer,
        livePulse = SignalCoral,
        heartActive = SignalCoral,
        successGreen = MediumwaveEmerald
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = BroadcastAmber,
    onPrimary = BroadcastAmberDark,
    primaryContainer = BroadcastAmberContainer,
    onPrimaryContainer = WarmGoldText,
    secondary = SignalCoral,
    onSecondary = Color.White,
    secondaryContainer = SignalCoralContainer,
    onSecondaryContainer = SignalCoralLight,
    tertiary = ShortwaveCyan,
    onTertiary = Color(0xFF002436),
    tertiaryContainer = ShortwaveCyanContainer,
    onTertiaryContainer = Color(0xFFBBEAFF),
    background = ObsidianBg,
    onBackground = WarmIvory,
    surface = StudioSurface,
    onSurface = WarmIvory,
    surfaceVariant = ConsoleContainer,
    onSurfaceVariant = MutedSilver,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = ConsoleContainer,
    surfaceContainerHigh = ConsoleContainerHigh,
    surfaceContainerHighest = Color(0xFF2D384F),
    outline = ConsoleOutline,
    outlineVariant = Color(0xFF232C3F),
    error = SignalCoral,
    onError = Color.White,
    errorContainer = SignalCoralContainer,
    onErrorContainer = SignalCoralLight
)

private val LightColorScheme = lightColorScheme(
    primary = DeepAmberPrimary,
    onPrimary = Color.White,
    primaryContainer = DeepAmberContainer,
    onPrimaryContainer = Color(0xFF3E2200),
    secondary = DeepCoralSecondary,
    onSecondary = Color.White,
    secondaryContainer = DeepCoralContainer,
    onSecondaryContainer = Color(0xFF410004),
    tertiary = DeepShortwaveTertiary,
    onTertiary = Color.White,
    tertiaryContainer = DeepShortwaveContainer,
    onTertiaryContainer = Color(0xFF001E30),
    background = ParchmentBg,
    onBackground = DeepCharcoalText,
    surface = ParchmentSurface,
    onSurface = DeepCharcoalText,
    surfaceVariant = ParchmentContainer,
    onSurfaceVariant = SlateSecondaryText,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = ParchmentSurface,
    surfaceContainer = ParchmentContainer,
    surfaceContainerHigh = ParchmentContainerHigh,
    surfaceContainerHighest = Color(0xFFD6CCB9),
    outline = ParchmentOutline,
    outlineVariant = Color(0xFFE0D7C8),
    error = DeepCoralSecondary,
    onError = Color.White,
    errorContainer = DeepCoralContainer,
    onErrorContainer = Color(0xFF410004)
)

@Composable
fun RadioSymphonyTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val extendedColors = if (isDark) {
        RadioExtendedColors(
            fmBadge = BroadcastAmber,
            fmBadgeContainer = BroadcastAmberContainer,
            amBadge = MediumwaveEmerald,
            amBadgeContainer = MediumwaveEmeraldContainer,
            swBadge = ShortwaveCyan,
            swBadgeContainer = ShortwaveCyanContainer,
            livePulse = SignalCoral,
            heartActive = SignalCoral,
            successGreen = MediumwaveEmerald
        )
    } else {
        RadioExtendedColors(
            fmBadge = DeepAmberPrimary,
            fmBadgeContainer = DeepAmberContainer,
            amBadge = Color(0xFF047857),
            amBadgeContainer = Color(0xFFD1FAE5),
            swBadge = DeepShortwaveTertiary,
            swBadgeContainer = DeepShortwaveContainer,
            livePulse = DeepCoralSecondary,
            heartActive = DeepCoralSecondary,
            successGreen = Color(0xFF059669)
        )
    }

    CompositionLocalProvider(LocalRadioColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = RadioTypography,
            content = content
        )
    }
}
