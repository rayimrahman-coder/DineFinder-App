package com.example.dinefinder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA4D5BA),
    onPrimary = ForestGreenDark,
    primaryContainer = Color(0xFF285540),
    onPrimaryContainer = Color(0xFFC0ECD1),
    secondary = GoldenYellow,
    onSecondary = Ink,
    secondaryContainer = Color(0xFF574326),
    onSecondaryContainer = Color(0xFFFFDCAB),
    tertiary = Color(0xFFA4D2C2),
    onTertiary = Color(0xFF08382B),
    tertiaryContainer = Color(0xFF245042),
    onTertiaryContainer = Color(0xFFBFEEDC),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121916),
    onBackground = Color(0xFFE5ECE7),
    surface = Color(0xFF1A231F),
    onSurface = Color(0xFFE5ECE7),
    surfaceVariant = Color(0xFF29352F),
    onSurfaceVariant = Color(0xFFC3CEC7),
    surfaceTint = Color(0xFFA4D5BA),
    surfaceDim = Color(0xFF121916),
    surfaceBright = Color(0xFF354039),
    surfaceContainerLowest = Color(0xFF0D1411),
    surfaceContainerLow = Color(0xFF18211C),
    surfaceContainer = Color(0xFF1D2721),
    surfaceContainerHigh = Color(0xFF27312B),
    surfaceContainerHighest = Color(0xFF313C35),
    inverseSurface = Color(0xFFE5ECE7),
    inverseOnSurface = Color(0xFF29352F),
    inversePrimary = ForestGreen,
    outline = Color(0xFF8D9B92),
    outlineVariant = Color(0xFF46554D),
    scrim = Color.Black
)

private val LightColorScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = Color.White,
    primaryContainer = SageGreen,
    onPrimaryContainer = ForestGreenDark,
    secondary = GoldenYellow,
    onSecondary = Ink,
    tertiary = Color(0xFF347562),
    background = WarmCream,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    secondaryContainer = SageGreen,
    onSecondaryContainer = ForestGreenDark,
    surfaceVariant = Color(0xFFEEF1EB),
    onSurfaceVariant = MutedInk,
    outline = SoftOutline
)

@Composable
fun DineFinderTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
