package com.snehashis.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ShellCraftDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = OnElectricCyan,
    primaryContainer = ElectricCyanContainer,
    onPrimaryContainer = Color(0xFFA5F3FC),
    secondary = PhosphorEmerald,
    onSecondary = Color(0xFF002116),
    secondaryContainer = PhosphorEmeraldContainer,
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = AmberAlert,
    onTertiary = Color(0xFF261900),
    tertiaryContainer = AmberAlertContainer,
    onTertiaryContainer = Color(0xFFFDE68A),
    error = CrimsonDanger,
    onError = Color(0xFF3B080A),
    errorContainer = CrimsonDangerContainer,
    onErrorContainer = Color(0xFFFECACA),
    background = CarbonBg,
    onBackground = SlateTextPrimary,
    surface = CarbonSurface,
    onSurface = SlateTextPrimary,
    surfaceVariant = CarbonSurfaceVariant,
    onSurfaceVariant = SlateTextSecondary,
    surfaceContainerLowest = CarbonBg,
    surfaceContainerLow = CarbonSurface,
    surfaceContainer = CarbonSurfaceVariant,
    surfaceContainerHigh = CarbonSurfaceElevated,
    outline = CarbonBorder,
    outlineVariant = Color(0xFF1E293B)
)

private val ShellCraftLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = Color(0xFF075985),
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = Color(0xFF065F46),
    tertiary = LightTertiary,
    onTertiary = Color.White,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = Color(0xFF92400E),
    error = LightError,
    onError = Color.White,
    errorContainer = LightErrorContainer,
    onErrorContainer = Color(0xFF991B1B),
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerHigh = Color(0xFFE2E8F0),
    outline = LightBorder,
    outlineVariant = Color(0xFFE2E8F0)
)

val ShellCraftShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun ShellCraftTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ShellCraftDarkColorScheme else ShellCraftLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ShellCraftTypography,
        shapes = ShellCraftShapes,
        content = content
    )
}
