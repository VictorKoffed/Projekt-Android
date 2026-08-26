package com.victorkoffed.projektandroid.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.victorkoffed.projektandroid.data.themePref.ThemePreferenceManager


private val LightCoffeeColorScheme = lightColorScheme(
    primary = CoffeeBrown,
    onPrimary = Black,
    primaryContainer = CoffeeBrown.copy(alpha = 0.5f),
    onPrimaryContainer = Black,

    secondary = GraphFlowBlue,
    onSecondary = Color.White,

    tertiary = GraphWeightBlack,
    onTertiary = Color.White,

    tertiaryContainer = CoffeeBrown.copy(alpha = 0.3f),
    onTertiaryContainer = Black,

    background = AppBackgroundGray,
    surface = Color.White,
    onSurface = Color.Black,
    onBackground = Color.Black,

    error = Color(0xFFB00020),
    onError = Color.White,
    outline = PlaceholderDarkGray
)

private val DarkCoffeeColorScheme = darkColorScheme(
    primary = CoffeeBrown,
    onPrimary = Color.Black,
    primaryContainer = CoffeeBrown.copy(alpha = 0.3f),
    onPrimaryContainer = Color.White,

    secondary = GraphFlowBlue,
    onSecondary = Color.White,
    tertiary = Color.White,
    onTertiary = Color.Black,

    tertiaryContainer = CoffeeDark,
    onTertiaryContainer = Color.White,

    background = Color.Black,
    surface = CoffeeDark,
    onSurface = Color.White,
    onBackground = Color.White,

    error = Color(0xFFCF6679),
    onError = Color.Black,
    outline = PlaceholderDarkGray
)


/**
 * Core theme provider responsible for applying Material 3 color schemes
 * driven by user preferences rather than system-wide dark mode overrides.
 */
@Composable
fun ProjektAndroidTheme(
    themePreferenceManager: ThemePreferenceManager,
    content: @Composable () -> Unit
) {
    val manualDarkMode by themePreferenceManager.isDarkMode.collectAsState()

    val darkTheme = manualDarkMode

    val colorScheme = when {
        darkTheme -> DarkCoffeeColorScheme
        else -> LightCoffeeColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}