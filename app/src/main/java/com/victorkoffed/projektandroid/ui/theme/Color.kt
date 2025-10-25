package com.victorkoffed.projektandroid.ui.theme

import androidx.compose.ui.graphics.Color

// --- HUVUDFÄRGER ---

// Primär accentfärg (Baserad på DCC7AA från din ursprungliga design)
val CoffeeBrown = Color(0xFFDCC7AA)

// Mörkbrun/kontrasterande färg (Kan användas som Primary/Secondary i ett mörkt schema)
val CoffeeDark = Color(0xFF331A15)

// Ren svart (Behålls som konstant, även om Color.Black ofta används)
val Black = Color(0xFF000000)


// --- GRAFFÄRGER (SPECIFIKA KONSTANTER) ---

// Sekundär färg: Används för flödesgrafen i LiveBrewScreen/BrewDetailScreen
// Denna motsvarar ofta MaterialTheme.colorScheme.secondary
val GraphFlowBlue = Color(0xFF007BFF)

// Tertiär färg: Används för viktgrafen i LiveBrewScreen/BrewDetailScreen
// Denna motsvarar ofta MaterialTheme.colorScheme.tertiary
val GraphWeightBlack = Color(0xFF000000)


// --- PLATSHÅLLARFÄRGER (SPECIFIKA FÖR UTVALDA KOMPONENTER) ---

// Ljusgrå bakgrund. Denna färg är i praktiken ersatt av MaterialTheme.colorScheme.background/surfaceVariant
val BackgroundLightGray = Color(0xFFF0F0F0)

// Grå bakgrund för "Lägg till bild"-platshållaren
val PlaceholderGray = Color(0xFFE7E7E7)

// Mörkgrå färg för ikoner/text inuti bildplatshållaren (för kontrast)
val PlaceholderDarkGray = Color(0xFF606060)