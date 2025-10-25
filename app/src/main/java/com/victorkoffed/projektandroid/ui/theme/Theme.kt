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
    secondary = GraphFlowBlue, // FIX: Används för Flow (blå)
    onSecondary = Black,
    tertiary = GraphWeightBlack, // FIX: Används för vikt (svart)
    onTertiary = Color.White, // FIX: Vit text på svart viktgraf
    tertiaryContainer = CoffeeBrown.copy(alpha = 0.3f), // Används i LiveBrewScreen
    onTertiaryContainer = Black,

    // Bakgrund och Yta (Vitt/Ljusgrått)
    background = BackgroundLightGray, // FIX: Använder den ljusgråa konstanten
    surface = Color.White,
    onSurface = Color.Black,
    onBackground = Color.Black,

    // Övriga färger
    error = Color(0xFFB00020),
    onError = Color.White,
    outline = PlaceholderDarkGray // FIX: Använd den mörka platshållar-färgen för konturer
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