package com.victorkoffed.projektandroid.ui.theme

// --- TILLAGDA IMPORTER ---
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
// --- SLUT TILLAGDA IMPORTER ---

// NYTT: Definiera en Lätt färgskala med DCC7AA som primärfärg.
private val LightCoffeeColorScheme = lightColorScheme(
    // Primärfärg (DCC7AA) och textfärg på primärfärg (Svart)
    primary = CoffeeBrown,
    onPrimary = Black,
    primaryContainer = CoffeeBrown.copy(alpha = 0.5f), // Lättare variant för containers
    onPrimaryContainer = Black,

    // Sekundär/Tertiär färg (Används ofta för FABs, knappar, mm.)
    secondary = CoffeeBrown,
    onSecondary = Black,
    tertiary = CoffeeBrown,
    onTertiary = Black,
    tertiaryContainer = CoffeeBrown.copy(alpha = 0.3f), // Används i LiveBrewScreen
    onTertiaryContainer = Black,

    // Bakgrund och Yta (Vitt/Ljusgrått)
    background = Color(0xFFF0F0F0), // Används i HomeScreen Scaffold
    surface = Color.White,
    onSurface = Color.Black,
    onBackground = Color.Black,

    // Övriga färger
    error = Color(0xFFB00020),
    onError = Color.White,
    outline = CoffeeDark // Använd en mörk färg för konturer
)


@Composable
fun ProjektAndroidTheme(
    content: @Composable () -> Unit
) {
    // Använd den definierade LightCoffeeColorScheme
    val colorScheme = LightCoffeeColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}