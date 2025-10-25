package com.victorkoffed.projektandroid.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Importera de egendefinierade färgkonstanterna
// (Antar att importen av färgkonstanterna CoffeeBrown, Black, GraphFlowBlue,
// GraphWeightBlack, BackgroundLightGray, PlaceholderDarkGray finns i filen)


/**
 * Definierar en ljus färgpalett enligt Material 3-specifikationen,
 * med CoffeeBrown (DCC7AA) som huvudaccent.
 */
private val LightCoffeeColorScheme = lightColorScheme(
    // --- Primärfärger (Huvudaccent och interaktion) ---
    primary = CoffeeBrown, // Huvudaccent, används för viktiga knappar/ikoner
    onPrimary = Black, // Textfärg som är läsbar på primary
    primaryContainer = CoffeeBrown.copy(alpha = 0.5f), // Lättare variant för containers/bakgrunder
    onPrimaryContainer = Black, // Textfärg som är läsbar på primaryContainer

    // --- Sekundärfärger (Grafiska/Tertiära accenter) ---
    secondary = GraphFlowBlue, // Mappas till Flow-grafens färg (blå)
    onSecondary = Color.White, // Säkerställ god kontrast (vit text på blått)

    // --- Tertiärfärger (Grafiska/Tertiära accenter) ---
    tertiary = GraphWeightBlack, // Mappas till Vikt-grafens färg (svart)
    onTertiary = Color.White, // Vit text/ikon på den svarta vikt-färgen

    // Tertiär Container används specifikt för statuskort i LiveBrewScreen (nedräkning/inspelning)
    tertiaryContainer = CoffeeBrown.copy(alpha = 0.3f),
    onTertiaryContainer = Black,

    // --- Bakgrunds- och Ytfärger (Struktur/Layout) ---
    background = BackgroundLightGray, // Ljusgrå bakgrund mellan kort/element
    surface = Color.White, // Vit färg för kort, TopAppBar och andra ytor
    onSurface = Color.Black, // Standard textfärg på ytor (svart)
    onBackground = Color.Black, // Standard textfärg på bakgrunden (svart)

    // --- Övriga färger ---
    error = Color(0xFFB00020), // Standard röd för felmeddelanden
    onError = Color.White,
    outline = PlaceholderDarkGray // Använd mörkgrå för textfältskonturer och avdelare
)


/**
 * Huvudkomponent för att tillämpa temat.
 */
@Composable
fun ProjektAndroidTheme(
    content: @Composable () -> Unit
) {
    // Använd den definierade LightCoffeeColorScheme
    val colorScheme = LightCoffeeColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // Använd den definierade typografin
        content = content
    )
    // Notera: Dark theme stöds ej i denna implementering
}