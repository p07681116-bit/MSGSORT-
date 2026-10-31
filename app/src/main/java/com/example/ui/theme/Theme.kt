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

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

private val DarkColorScheme = darkColorScheme(
    primary = RoyalIndigo,
    onPrimary = Color.White,
    primaryContainer = DeepIndigo,
    onPrimaryContainer = Color(0xFFEEF2FF),
    secondary = AccentPurple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3B0764),
    onSecondaryContainer = Color(0xFFF3E8FF),
    tertiary = AccentSky,
    onTertiary = Color.White,
    background = DarkNavy,
    onBackground = Color(0xFFF8FAFC),
    surface = NavyLight,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = SlateGray,
    onSurfaceVariant = Color(0xFFE2E8F0),
    outline = SlateMuted
)

private val LightColorScheme = lightColorScheme(
    primary = RoyalIndigo,
    onPrimary = Color.White,
    primaryContainer = ContainerIndigo,
    onPrimaryContainer = DeepIndigo,
    secondary = AccentPurple,
    onSecondary = Color.White,
    secondaryContainer = ContainerPurple,
    onSecondaryContainer = Color(0xFF581C87),
    tertiary = AccentSky,
    onTertiary = Color.White,
    background = BackgroundLight,
    onBackground = DarkNavy,
    surface = SurfaceWhite,
    onSurface = DarkNavy,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = SlateGray,
    outline = BorderLight
)

@Composable
fun MsgSortTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MsgSortTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
