package com.snehashis.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.snehashis.domain.model.ThemeMode

private val ShellCraftDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = OnNeonCyan,
    primaryContainer = NeonCyanContainer,
    onPrimaryContainer = OnNeonCyanContainer,
    secondary = NeonEmerald,
    onSecondary = OnNeonEmerald,
    secondaryContainer = NeonEmeraldContainer,
    onSecondaryContainer = OnNeonEmeraldContainer,
    tertiary = NeonAmber,
    onTertiary = Color(0xFF281600),
    tertiaryContainer = NeonAmberContainer,
    onTertiaryContainer = OnNeonAmberContainer,
    error = NeonCoral,
    onError = Color(0xFF3E0010),
    errorContainer = NeonCoralContainer,
    onErrorContainer = OnNeonCoralContainer,
    background = MidnightBg,
    onBackground = MidnightTextPrimary,
    surface = MidnightSurface,
    onSurface = MidnightTextPrimary,
    surfaceVariant = MidnightSurfaceVariant,
    onSurfaceVariant = MidnightTextSecondary,
    surfaceContainerLowest = MidnightBg,
    surfaceContainerLow = MidnightSurface,
    surfaceContainer = MidnightSurfaceVariant,
    surfaceContainerHigh = MidnightSurfaceElevated,
    outline = MidnightBorder,
    outlineVariant = MidnightOutlineVariant
)

private val ShellCraftLightColorScheme = lightColorScheme(
    primary = DaylightBlue,
    onPrimary = Color.White,
    primaryContainer = DaylightBlueContainer,
    onPrimaryContainer = OnDaylightBlueContainer,
    secondary = DaylightTeal,
    onSecondary = Color.White,
    secondaryContainer = DaylightTealContainer,
    onSecondaryContainer = OnDaylightTealContainer,
    tertiary = DaylightAmber,
    onTertiary = Color.White,
    tertiaryContainer = DaylightAmberContainer,
    onTertiaryContainer = OnDaylightAmberContainer,
    error = DaylightCrimson,
    onError = Color.White,
    errorContainer = DaylightCrimsonContainer,
    onErrorContainer = OnDaylightCrimsonContainer,
    background = DaylightBg,
    onBackground = DaylightTextPrimary,
    surface = DaylightSurface,
    onSurface = DaylightTextPrimary,
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = DaylightTextSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = DaylightSurfaceLow,
    surfaceContainer = DaylightSurfaceVariant,
    surfaceContainerHigh = DaylightSurfaceHigh,
    outline = DaylightBorder,
    outlineVariant = DaylightOutlineVariant
)

@Immutable
data class ShellCraftExpressiveAccents(
    val violet: Color = NeonViolet,
    val violetContainer: Color = NeonVioletContainer,
    val onVioletContainer: Color = OnNeonVioletContainer,
    val isDark: Boolean = true
)

val LocalExpressiveAccents = staticCompositionLocalOf {
    ShellCraftExpressiveAccents()
}

val LocalDisableHeavyAnimations = staticCompositionLocalOf { false }

val ShellCraftShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun ShellCraftTheme(
    themeMode: ThemeMode = ThemeMode.NEON_DARK,
    disableHeavyAnimations: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.NEON_DARK -> true
        ThemeMode.DAYLIGHT_LIGHT -> false
    }
    val colorScheme = if (useDark) ShellCraftDarkColorScheme else ShellCraftLightColorScheme
    val accents = if (useDark) {
        ShellCraftExpressiveAccents(
            violet = NeonViolet,
            violetContainer = NeonVioletContainer,
            onVioletContainer = OnNeonVioletContainer,
            isDark = true
        )
    } else {
        ShellCraftExpressiveAccents(
            violet = DaylightViolet,
            violetContainer = DaylightVioletContainer,
            onVioletContainer = OnDaylightVioletContainer,
            isDark = false
        )
    }
    CompositionLocalProvider(
        LocalDisableHeavyAnimations provides disableHeavyAnimations,
        LocalExpressiveAccents provides accents
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ShellCraftTypography,
            shapes = ShellCraftShapes,
            content = content
        )
    }
}

@Composable
fun ShellCraftTheme(
    darkTheme: Boolean,
    disableHeavyAnimations: Boolean = false,
    content: @Composable () -> Unit
) {
    ShellCraftTheme(
        themeMode = if (darkTheme) ThemeMode.NEON_DARK else ThemeMode.DAYLIGHT_LIGHT,
        disableHeavyAnimations = disableHeavyAnimations,
        content = content
    )
}
