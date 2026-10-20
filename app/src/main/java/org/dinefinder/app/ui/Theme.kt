package org.dinefinder.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val DineGreen = Color(0xFF245B50)
val DineMint = Color(0xFFDEEBE3)
val DinePaper = Color(0xFFF7F8F3)
val DineInk = Color(0xFF1E2A25)
val DineMuted = Color(0xFF768079)
val DineRed = Color(0xFFAD3C31)

private val DineColors = lightColorScheme(
    primary = DineGreen,
    onPrimary = Color.White,
    primaryContainer = DineMint,
    onPrimaryContainer = DineGreen,
    secondary = DineGreen,
    onSecondary = Color.White,
    secondaryContainer = DineMint,
    onSecondaryContainer = DineGreen,
    background = DinePaper,
    onBackground = DineInk,
    surface = Color.White,
    onSurface = DineInk,
    surfaceVariant = Color(0xFFEEF1EA),
    onSurfaceVariant = DineMuted,
    outline = Color(0xFFCACFC8),
    outlineVariant = Color(0xFFE5E8E1),
    error = DineRed,
    onError = Color.White,
)

private val DineTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.7).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.5).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
)

@Composable
fun DineFinderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DineColors,
        typography = DineTypography,
        content = content,
    )
}
