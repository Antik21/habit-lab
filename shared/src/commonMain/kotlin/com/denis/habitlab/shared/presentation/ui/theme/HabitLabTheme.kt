package com.denis.habitlab.shared.presentation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme

object HabitLabColorTokens {
    val Brand = Color(0xFF2563EB)
    val BrandContainer = Color(0xFFDCE9FF)
    val OnBrandContainer = Color(0xFF0A285C)
    val Accent = Color(0xFF006B5E)
    val AccentContainer = Color(0xFF9EF2DF)
    val OnAccentContainer = Color(0xFF00201A)
    val LightSurface = Color(0xFFFAF9FF)
    val DarkSurface = Color(0xFF121318)
    val LightSurfaceVariant = Color(0xFFE1E2EC)
    val DarkSurfaceVariant = Color(0xFF44474F)
    val LightOutline = Color(0xFF74777F)
    val DarkOutline = Color(0xFF8E9099)
    val Error = Color(0xFFBA1A1A)
}

object HabitLabSpacing {
    val ExtraSmall = 4.dp
    val Small = 8.dp
    val Medium = 16.dp
    val Large = 24.dp
    val ExtraLarge = 32.dp
}

val HabitLabTypography = androidx.compose.material3.Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
)

private val LightColorScheme = lightColorScheme(
    primary = HabitLabColorTokens.Brand,
    onPrimary = Color.White,
    primaryContainer = HabitLabColorTokens.BrandContainer,
    onPrimaryContainer = HabitLabColorTokens.OnBrandContainer,
    secondary = HabitLabColorTokens.Accent,
    onSecondary = Color.White,
    secondaryContainer = HabitLabColorTokens.AccentContainer,
    onSecondaryContainer = HabitLabColorTokens.OnAccentContainer,
    surface = HabitLabColorTokens.LightSurface,
    surfaceVariant = HabitLabColorTokens.LightSurfaceVariant,
    outline = HabitLabColorTokens.LightOutline,
    error = HabitLabColorTokens.Error,
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB3C5FF),
    onPrimary = Color(0xFF002E6A),
    primaryContainer = Color(0xFF004494),
    onPrimaryContainer = Color(0xFFDCE9FF),
    secondary = Color(0xFF82D7C4),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF005045),
    onSecondaryContainer = Color(0xFF9EF2DF),
    surface = HabitLabColorTokens.DarkSurface,
    surfaceVariant = HabitLabColorTokens.DarkSurfaceVariant,
    outline = HabitLabColorTokens.DarkOutline,
    error = Color(0xFFFFB4AB),
)

@Composable
fun HabitLabTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = HabitLabTypography,
        content = content,
    )
}
