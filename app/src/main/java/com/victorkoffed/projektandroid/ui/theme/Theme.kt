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

    //  FÄRG: Bakgrund Ljusgrå (för bakom korten)
    background = AppBackgroundGray,
    //  FÄRG: Ytor (kort, TopAppBar, NavigationBar/Bottom Bar) ska vara Vita
    surface = Color.White,
    onSurface = Color.Black,
    onBackground = Color.Black,

    error = Color(0xFFB00020),
    onError = Color.White,
    outline = PlaceholderDarkGray
)

/**
 * Definierar en mörk färgpalett enligt Material 3-specifikationen.
 */
private val DarkCoffeeColorScheme = darkColorScheme(
    // Använder CoffeeDark (331A15) som bas och CoffeeBrown (DCC7AA) som accent
    primary = CoffeeBrown, // Primär accent
    onPrimary = Color.Black, // Textfärg som är läsbar på primary
    primaryContainer = CoffeeBrown.copy(alpha = 0.3f),
    onPrimaryContainer = Color.White,

    // Sekundär (Flow-graf) och Tertiär (Vikt-graf)
    secondary = GraphFlowBlue,
    onSecondary = Color.White,
    tertiary = Color.White, // Använd vit/ljusgrå för viktlinjen i mörkt läge
    onTertiary = Color.Black,

    tertiaryContainer = CoffeeDark,
    onTertiaryContainer = Color.White,

    // Mörka Ytfärger (Surface och Background)
    background = Color.Black, // Ren svart bakgrund
    surface = CoffeeDark, // Mycket mörkbrun (331A15) för kort och ytor
    onSurface = Color.White,
    onBackground = Color.White,

    // Fel (Error)
    error = Color(0xFFCF6679), // Ljus röd för mörkt läge
    onError = Color.Black,
    outline = PlaceholderDarkGray
)


/**
 * Huvudkomponent för att tillämpa temat.
 * Väljer nu tema baserat på det sparade valet i ThemePreferenceManager.
 */
@Composable
fun ProjektAndroidTheme(
    themePreferenceManager: ThemePreferenceManager,
    content: @Composable () -> Unit
) {
    // Läs det sparade mörkerläge-valet från ThemePreferenceManager som ett Compose State.
    val manualDarkMode by themePreferenceManager.isDarkMode.collectAsState()

    // Använder den manuella inställningen, oavsett systeminställningen.
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