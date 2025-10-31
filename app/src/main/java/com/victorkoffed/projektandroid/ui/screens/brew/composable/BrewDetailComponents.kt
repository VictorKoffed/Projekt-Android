package com.victorkoffed.projektandroid.ui.screens.brew.composable

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.victorkoffed.projektandroid.data.db.BrewMetrics
import com.victorkoffed.projektandroid.data.db.Grinder
import com.victorkoffed.projektandroid.data.db.Method
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewDetailState
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewDetailViewModel
import java.text.SimpleDateFormat
import java.util.Locale

// --- DetailRow ---
/**
 * Enkel återanvändbar rad för att visa en etikett och ett värde i detaljvyer.
 * @param label Etiketten som visas till vänster.
 * @param value Värdet som visas till höger om etiketten.
 */
@Composable
fun DetailRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(100.dp)
        )
        Text(value)
    }
}

// ---------- Brew Image Section ----------
/**
 * Composable som hanterar visning av bryggningsbilden eller en placeholder.
 * Inkluderar knappar för att ta bort bild (i redigeringsläge) och navigera till kamera/fullskärm.
 *
 * @param imageUri URI:n till bilden som ska visas (kan vara null).
 * @param isEditing Om redigeringsläget är aktivt.
 * @param onNavigateToCamera Callback för att navigera till kameraskärmen.
 * @param onNavigateToImageFullscreen Callback för att navigera till helskärmsvyn för bilden.
 * @param onDeleteImage Callback för att ta bort den aktuella bilden (sätta URI till null).
 */
@Composable
fun BrewImageSection(
    imageUri: String?,
    isEditing: Boolean,
    onNavigateToCamera: () -> Unit,
    onNavigateToImageFullscreen: (String) -> Unit,
    onDeleteImage: () -> Unit // Callback för att ta bort bilden
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)) // Avrundade hörn för hela sektionen
    ) {
        if (imageUri != null) {
            // Visa bilden om URI finns
            AsyncImage(
                model = imageUri,
                contentDescription = "Brew photo",
                contentScale = ContentScale.Crop, // Beskär bilden för att fylla ytan
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp) // Fast höjd för bilden
                    .clickable(enabled = !isEditing) { // Klickbar endast i visningsläge
                        onNavigateToImageFullscreen(imageUri)
                    }
            )

            // Visa "Ta bort"-knapp över bilden i redigeringsläge
            if (isEditing) {
                IconButton(
                    onClick = onDeleteImage, // Anropa callback för att ta bort
                    modifier = Modifier
                        .align(Alignment.TopEnd) // Placera uppe till höger
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape), // Halvtransparent bakgrund
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White) // Vit ikon
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = "Delete Picture")
                }
            }
        } else {
            // Visa en klickbar placeholder om ingen bild finns
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp) // Lägre höjd för placeholder
                    .clickable(onClick = onNavigateToCamera), // Navigera till kameran vid klick
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = "Add Picture Icon",
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Add Picture")
                    }
                }
            }
        }
    }
}


// ---------- Summary Card & rows ----------
/**
 * Visar en sammanfattning av bryggningsdetaljerna i ett Card.
 * @param state Det aktuella tillståndet för bryggningsdetaljerna.
 */
@SuppressLint("DefaultLocale")
@Composable
fun BrewSummaryCard(state: BrewDetailState) {
    // Formatterare för datum och tid
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    // Beräkna total tid från samples
    val totalTimeMillis = state.samples.lastOrNull()?.timeMillis ?: 0L
    val minutes = (totalTimeMillis / 1000 / 60).toInt()
    val seconds = (totalTimeMillis / 1000 % 60).toInt()
    val timeString = remember(minutes, seconds) {
        String.format("%02d:%02d", minutes, seconds)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Details", style = MaterialTheme.typography.titleLarge)
            // Använder DetailRow för varje rad
            DetailRow("Bean:", state.bean?.name ?: "-")
            DetailRow("Roaster:", state.bean?.roaster ?: "-")
            // Visa arkivstatus om bönan är arkiverad
            if (state.bean?.isArchived == true) {
                DetailRow("Bean Status:", "Archived")
            }
            DetailRow("Date:", state.brew?.startedAt?.let { dateFormat.format(it) } ?: "-")
            DetailRow("Total time:", if (totalTimeMillis > 0) timeString else "-")
            DetailRow("Dose:", state.brew?.doseGrams?.let { "%.1f g".format(it) } ?: "-")
            DetailRow("Method:", state.method?.name ?: "-")
            DetailRow("Grinder:", state.grinder?.name ?: "-")
            DetailRow("Grind set:", state.brew?.grindSetting ?: "-")
            DetailRow("Grind speed:", state.brew?.grindSpeedRpm?.let { "%.0f RPM".format(it) } ?: "-")
            DetailRow("Temp:", state.brew?.brewTempCelsius?.let { "%.1f °C".format(it) } ?: "-")
        }
    }
}

// ---------- Edit card ----------
/**
 * Visar ett Card med redigerbara fält för bryggningsdetaljer.
 * Använder states och callbacks från BrewDetailViewModel.
 * @param viewModel ViewModel som håller redigerings-states och hanterar ändringar.
 * @param availableGrinders Lista över tillgängliga kvarnar för dropdown.
 * @param availableMethods Lista över tillgängliga metoder för dropdown.
 */
@Composable
fun BrewEditCard(
    viewModel: BrewDetailViewModel,
    availableGrinders: List<Grinder>,
    availableMethods: List<Method>
) {
    // Hämta aktuella redigeringsvärden från ViewModel
    val grinder = viewModel.editSelectedGrinder
    val method = viewModel.editSelectedMethod

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Edit Details", style = MaterialTheme.typography.titleLarge)

            // Dropdown för kvarn
            EditDropdownSelector(
                label = "Grinder",
                options = availableGrinders,
                selectedOption = grinder,
                onOptionSelected = { viewModel.onEditGrinderSelected(it) },
                optionToString = { it?.name ?: "Select grinder..." }
            )
            // Textfält för malningsinställning
            OutlinedTextField(
                value = viewModel.editGrindSetting,
                onValueChange = { viewModel.onEditGrindSettingChanged(it) },
                label = { Text("Grind Setting") },
                modifier = Modifier.fillMaxWidth(),
                colors = defaultTextFieldColors()
            )
            // Textfält för malningshastighet (endast siffror)
            OutlinedTextField(
                value = viewModel.editGrindSpeedRpm,
                onValueChange = { viewModel.onEditGrindSpeedRpmChanged(it) },
                label = { Text("Grind Speed (RPM)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = defaultTextFieldColors()
            )
            // Dropdown för metod
            EditDropdownSelector(
                label = "Method",
                options = availableMethods,
                selectedOption = method,
                onOptionSelected = { viewModel.onEditMethodSelected(it) },
                optionToString = { it?.name ?: "Select method..." }
            )
            // Textfält för temperatur (decimaltal)
            OutlinedTextField(
                value = viewModel.editBrewTempCelsius,
                onValueChange = { viewModel.onEditBrewTempChanged(it) },
                label = { Text("Water Temperature (°C)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                colors = defaultTextFieldColors()
            )
        }
    }
}

// ---------- Reusable UI Components ----------

/**
 * Visar de beräknade nyckeltalen (Ratio, Water, Dose) i ett Card.
 * @param metrics Objektet som innehåller de beräknade värdena.
 */
@SuppressLint("DefaultLocale")
@Composable
fun BrewMetricsCard(metrics: BrewMetrics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround // Fördela jämnt
        ) {
            // Kolumn för Ratio
            MetricItem(
                label = "Ratio",
                // Formatera som 1:X.X eller visa "-" om null
                value = metrics.ratio?.let { "1:%.1f".format(it) } ?: "-"
            )
            // Kolumn för Water
            MetricItem(
                label = "Water",
                value = "%.1f g".format(metrics.waterUsedGrams)
            )
            // Kolumn för Dose
            MetricItem(
                label = "Dose",
                value = "%.1f g".format(metrics.doseGrams)
            )
        }
    }
}

/**
 * En liten Composable för att visa en enskild metrik (etikett och värde).
 * Används inuti `BrewMetricsCard`.
 * @param label Etiketten för metriken (t.ex. "Ratio").
 * @param value Det formaterade värdet för metriken (t.ex. "1:16.5").
 */
@Composable
private fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}


// ---------- Brew Notes Section ----------
/**
 * Composable som hanterar visning och redigering av anteckningar för en bryggning.
 * Växlar mellan ett snabbredigeringsläge (med spara-knapp) och ett fullt redigeringsläge.
 *
 * @param isEditing Anger om det fulla redigeringsläget är aktivt.
 * @param quickEditNotesValue Textvärdet för snabbredigeringsfältet.
 * @param fullEditNotesValue Textvärdet för det fulla redigeringsfältet.
 * @param hasUnsavedQuickNotes Anger om det finns osparade ändringar i snabbredigeringsfältet.
 * @param onQuickEditNotesChanged Callback när texten i snabbredigeringsfältet ändras.
 * @param onFullEditNotesChanged Callback när texten i det fulla redigeringsfältet ändras.
 * @param onSaveQuickEditNotes Callback när spara-knappen för snabbredigering klickas.
 */
@Composable
fun BrewNotesSection(
    isEditing: Boolean,
    quickEditNotesValue: String,
    fullEditNotesValue: String,
    hasUnsavedQuickNotes: Boolean,
    onQuickEditNotesChanged: (String) -> Unit,
    onFullEditNotesChanged: (String) -> Unit,
    onSaveQuickEditNotes: () -> Unit
) {
    Column {
        Text("Notes", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (isEditing) {
            // Fullständig redigering i edit mode
            OutlinedTextField(
                value = fullEditNotesValue,
                onValueChange = onFullEditNotesChanged,
                label = { Text("Notes (Full Edit Mode)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
                colors = defaultTextFieldColors()
            )
        } else {
            // Snabbredigering i visningsläge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Textfält för snabbredigering
                OutlinedTextField(
                    value = quickEditNotesValue,
                    onValueChange = onQuickEditNotesChanged,
                    label = { Text("Notes") },
                    enabled = true, // Alltid editerbart i detta läge
                    readOnly = false,
                    modifier = Modifier.weight(1f).heightIn(min = 100.dp),
                    colors = defaultTextFieldColors()
                )
                // Spara-knapp för snabbredigering
                IconButton(
                    onClick = onSaveQuickEditNotes,
                    enabled = hasUnsavedQuickNotes,
                    modifier = Modifier.align(Alignment.Top).offset(y = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = "Save notes",
                        // Grå ikon om inga ändringar finns
                        tint = if (hasUnsavedQuickNotes) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }
        }
    }
}


// --- Dropdown ---
/**
 * Återanvändbar dropdown-komponent för redigeringslägen.
 * Använder `ExposedDropdownMenuBox` för Material 3-utseende.
 * @param T Typen av objekt i listan (t.ex. Grinder, Method).
 * @param label Etikett som visas ovanför fältet.
 * @param options Listan med valbara objekt.
 * @param selectedOption Det för närvarande valda objektet.
 * @param onOptionSelected Callback som anropas när ett nytt alternativ väljs.
 * @param optionToString Funktion för att konvertera ett objekt av typ T till en String för visning.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> EditDropdownSelector(
    label: String,
    options: List<T>,
    selectedOption: T?,
    onOptionSelected: (T?) -> Unit,
    optionToString: (T?) -> String
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = optionToString(selectedOption), // Visa texten för det valda objektet
            onValueChange = {}, // Ingen direkt ändring, endast via dropdown
            readOnly = true,
            label = { Text(label) },
            // Ikon som indikerar om menyn är öppen eller stängd
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .exposedDropdownSize(true)
                .menuAnchor(
                    ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = true
                )
                .fillMaxWidth(), // Koppla textfältet till menyn
            colors = defaultTextFieldColors()
        )
        // Själva dropdown-menyn
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false } // Stäng menyn om man klickar utanför
        ) {
            // Lägg till ett alternativ för att inte välja något ("No selection")
            DropdownMenuItem(
                text = { Text("No selection") },
                onClick = {
                    onOptionSelected(null) // Skicka null till callback
                    expanded = false // Stäng menyn
                }
            )
            // Skapa ett menyalternativ för varje objekt i listan
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionToString(option)) }, // Visa texten för alternativet
                    onClick = {
                        onOptionSelected(option) // Skicka det valda objektet
                        expanded = false // Stäng menyn
                    }
                )
            }
        }
    }
}

/**
 * Hjälpfunktion för att standardisera färgerna på OutlinedTextField i redigeringslägen.
 * Använder temats färger.
 */
@Composable
private fun defaultTextFieldColors(): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline
    )