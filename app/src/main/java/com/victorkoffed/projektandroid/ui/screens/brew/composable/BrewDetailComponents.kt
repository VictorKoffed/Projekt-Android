/**
 * Implementation Note: The reusable generic Compose component [EditDropdownSelector]
 * (utilizing ExposedDropdownMenuBox with generic types) was implemented with AI assistance. See README.md.
 */

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

@Composable
fun BrewImageSection(
    imageUri: String?,
    isEditing: Boolean,
    onNavigateToCamera: () -> Unit,
    onNavigateToImageFullscreen: (String) -> Unit,
    onDeleteImage: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
    ) {
        if (imageUri != null) {
            AsyncImage(
                model = imageUri,
                contentDescription = "Brew photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .clickable(enabled = !isEditing) {
                        onNavigateToImageFullscreen(imageUri)
                    }
            )

            if (isEditing) {
                IconButton(
                    onClick = onDeleteImage,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = "Delete Picture")
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clickable(onClick = onNavigateToCamera),
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

@SuppressLint("DefaultLocale")
@Composable
fun BrewSummaryCard(state: BrewDetailState) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
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
            DetailRow("Bean:", state.bean?.name ?: "-")
            DetailRow("Roaster:", state.bean?.roaster ?: "-")
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

@Composable
fun BrewEditCard(
    viewModel: BrewDetailViewModel,
    availableGrinders: List<Grinder>,
    availableMethods: List<Method>
) {
    val grinder = viewModel.editSelectedGrinder
    val method = viewModel.editSelectedMethod

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Edit Details", style = MaterialTheme.typography.titleLarge)

            EditDropdownSelector(
                label = "Grinder",
                options = availableGrinders,
                selectedOption = grinder,
                onOptionSelected = { viewModel.onEditGrinderSelected(it) },
                optionToString = { it?.name ?: "Select grinder..." }
            )
            OutlinedTextField(
                value = viewModel.editGrindSetting,
                onValueChange = { viewModel.onEditGrindSettingChanged(it) },
                label = { Text("Grind Setting") },
                modifier = Modifier.fillMaxWidth(),
                colors = defaultTextFieldColors()
            )
            OutlinedTextField(
                value = viewModel.editGrindSpeedRpm,
                onValueChange = { viewModel.onEditGrindSpeedRpmChanged(it) },
                label = { Text("Grind Speed (RPM)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = defaultTextFieldColors()
            )
            EditDropdownSelector(
                label = "Method",
                options = availableMethods,
                selectedOption = method,
                onOptionSelected = { viewModel.onEditMethodSelected(it) },
                optionToString = { it?.name ?: "Select method..." }
            )
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
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            MetricItem(
                label = "Ratio",
                value = metrics.ratio?.let { "1:%.1f".format(it) } ?: "-"
            )
            MetricItem(
                label = "Water",
                value = "%.1f g".format(metrics.waterUsedGrams)
            )
            MetricItem(
                label = "Dose",
                value = "%.1f g".format(metrics.doseGrams)
            )
        }
    }
}

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = quickEditNotesValue,
                    onValueChange = onQuickEditNotesChanged,
                    label = { Text("Notes") },
                    enabled = true,
                    readOnly = false,
                    modifier = Modifier.weight(1f).heightIn(min = 100.dp),
                    colors = defaultTextFieldColors()
                )
                IconButton(
                    onClick = onSaveQuickEditNotes,
                    enabled = hasUnsavedQuickNotes,
                    modifier = Modifier.align(Alignment.Top).offset(y = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = "Save notes",
                        tint = if (hasUnsavedQuickNotes) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }
        }
    }
}

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
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = optionToString(selectedOption),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .exposedDropdownSize(true)
                .menuAnchor(
                    ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = true
                )
                .fillMaxWidth(),
            colors = defaultTextFieldColors()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("No selection") },
                onClick = {
                    onOptionSelected(null)
                    expanded = false
                }
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionToString(option)) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun defaultTextFieldColors(): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline
    )