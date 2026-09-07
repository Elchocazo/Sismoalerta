package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AlertRedVivid,
    onPrimary = Color.White,
    secondary = WarningAmber,
    onSecondary = Color.Black,
    tertiary = EmergencyTertiary,
    background = TacticalDarkBg,
    surface = TacticalSurfaceDark,
    onBackground = Color.White,
    onSurface = Color.White
)

private val HighContrastColorScheme = darkColorScheme(
    primary = HighContrastRed,
    onPrimary = HighContrastWhite,
    secondary = HighContrastYellow,
    onSecondary = Color.Black,
    tertiary = HighContrastGreen,
    background = HighContrastBg,
    surface = HighContrastSurface,
    onBackground = HighContrastWhite,
    onSurface = HighContrastWhite
)

private val LightColorScheme = lightColorScheme(
    primary = EmergencyPrimary,
    onPrimary = Color.White,
    secondary = EmergencySecondary,
    onSecondary = Color.Black,
    tertiary = EmergencyTertiary,
    background = TacticalSurfaceLight,
    surface = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F)
)

@Composable
fun SismoAlertaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isHighContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        isHighContrast -> HighContrastColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
