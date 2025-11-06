package com.victorkoffed.projektandroid.ui.screens.bean

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.victorkoffed.projektandroid.ThemedSnackbar
import com.victorkoffed.projektandroid.data.db.Bean
import com.victorkoffed.projektandroid.data.db.Brew
import com.victorkoffed.projektandroid.ui.viewmodel.bean.BeanDetailViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// --- Konstanter ---
@SuppressLint("ConstantLocale")
private val detailDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
@SuppressLint("ConstantLocale")
private val brewItemDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeanDetailScreen(
    onNavigateBack: () -> Unit,
    onBrewClick: (Long) -> Unit,
    snackbarHostState: SnackbarHostState,
    // Hämta ViewModel direkt här med Hilt.
    viewModel: BeanDetailViewModel = hiltViewModel()
) {
    // UI-states från ViewModel
    val state by viewModel.beanDetailState.collectAsState()
    val isEditing by remember { derivedStateOf { viewModel.isEditing } }
    val showArchivePrompt by viewModel.showArchivePromptAfterSave.collectAsState()
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showArchiveWeightWarningDialog by remember { mutableStateOf(false) }

    // Snackbar-hantering
    val scope = rememberCoroutineScope()

    // Effekt för felmeddelanden
    LaunchedEffect(state.error) {
        if (state.error != null) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = state.error!!,
                    duration = SnackbarDuration.Long
                )
            }
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.bean?.name ?: "Loading bean...") },
                navigationIcon = {
                    // Avbryt-knapp i redigeringsläge, annars tillbaka-knapp
                    IconButton(onClick = { if (isEditing) viewModel.cancelEditing() else onNavigateBack() }) {
                        Icon(
                            if (isEditing) Icons.Default.Cancel else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isEditing) "Cancel editing" else "Back"
                        )
                    }
                },
                actions = {
                    if (isEditing) {
                        // Spara-knapp i redigeringsläge
                        IconButton(onClick = { viewModel.saveChanges() }, enabled = state.bean != null) {
                            Icon(Icons.Default.Save, contentDescription = "Save changes", tint = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        // Visa Redigera-knapp
                        IconButton(onClick = { viewModel.startEditing() }, enabled = state.bean != null) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }

                        state.bean?.let { bean ->
                            if (bean.isArchived) {
                                // --- Arkiverad status ---
                                // Av-arkivera-knapp
                                IconButton(onClick = { viewModel.unarchiveBean() }, enabled = !state.isLoading) {
                                    Icon(Icons.Default.Unarchive, contentDescription = "Unarchive Bean", tint = MaterialTheme.colorScheme.primary)
                                }
                                // Permanent Radera-knapp
                                IconButton(onClick = { showDeleteConfirmDialog = true }, enabled = !state.isLoading) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Bean permanently", tint = MaterialTheme.colorScheme.error)
                                }
                            } else {
                                // --- Aktiv status ---
                                // Arkivera-knapp (visar Archive-ikon)
                                IconButton(
                                    onClick = {
                                        if (bean.remainingWeightGrams == 0.0) {
                                            viewModel.archiveBean { /* Stannar kvar på sidan */ }
                                        } else {
                                            // Visa varning om vikt > 0
                                            showArchiveWeightWarningDialog = true
                                        }
                                    },
                                    enabled = !state.isLoading
                                ) {
                                    Icon(Icons.Default.Archive, contentDescription = "Archive Bean", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            )
        },
    ) { paddingValues ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(paddingValues), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.bean != null -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        BeanDetailHeaderCard(bean = state.bean!!)
                    }
                    item {
                        Text(
                            "Brews using this bean",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    if (state.brews.isEmpty()) {
                        item {
                            Text(
                                "No brews recorded for this bean yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        items(state.brews, key = { it.id }) { brew ->
                            BrewItemCard(brew = brew, onClick = { onBrewClick(brew.id) })
                        }
                    }
                }
            }
            else -> { // Fallback om bönan är null och inte laddar
                Box(Modifier.fillMaxSize().padding(paddingValues), Alignment.Center) {
                    Text(state.error ?: "Bean not found or has been deleted.")
                }
            }
        } // Slut when

        // --- Dialogrutor ---
        if (isEditing && state.bean != null) {
            EditBeanDialog(
                // ... (samma som tidigare)
                editName = viewModel.editName,
                editRoaster = viewModel.editRoaster,
                editRoastDateStr = viewModel.editRoastDateStr,
                editInitialWeightStr = viewModel.editInitialWeightStr,
                editRemainingWeightStr = viewModel.editRemainingWeightStr,
                editNotes = viewModel.editNotes,
                onNameChange = { viewModel.editName = it },
                onRoasterChange = { viewModel.editRoaster = it },
                onRoastDateChange = { viewModel.editRoastDateStr = it },
                onInitialWeightChange = { viewModel.editInitialWeightStr = it },
                onRemainingWeightChange = { viewModel.editRemainingWeightStr = it },
                onNotesChange = { viewModel.editNotes = it },
                onDismiss = { viewModel.cancelEditing() },
                onSaveBean = { viewModel.saveChanges() }
            )
        }

        // NYTT: Dialogruta för att bekräfta arkivering efter sparande till noll
        if (showArchivePrompt && state.bean != null && !state.bean!!.isArchived) {
            ArchiveConfirmationDialog(
                beanName = state.bean!!.name,
                onConfirm = { viewModel.confirmAndArchiveBean() }, // Bekräfta arkivering
                onDismiss = { viewModel.dismissArchivePrompt() }  // Avbryt arkivering
            )
        }


        // Varningsdialog vid försök att arkivera med vikt > 0
        if (showArchiveWeightWarningDialog) {
            AlertDialog(
                onDismissRequest = { showArchiveWeightWarningDialog = false },
                title = { Text("Cannot archive bean") },
                text = { Text("The remaining weight for this bean must be zero (0.0 g) before it can be archived. Archiving hides the bean from the main list.") },
                confirmButton = {
                    TextButton(onClick = { showArchiveWeightWarningDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }

        // Borttagningsbekräftelse
        if (showDeleteConfirmDialog && state.bean != null) {
            DeleteConfirmationDialog(
                beanName = state.bean!!.name,
                brewCount = state.brews.size,
                isArchived = state.bean!!.isArchived, // Skicka med arkivstatus
                onConfirm = {
                    // Försök radera via ViewModel (som kollar isArchived)
                    viewModel.deleteBean {
                        onNavigateBack() // Gå tillbaka endast vid lyckad radering
                    }
                    showDeleteConfirmDialog = false
                },
                onDismiss = { showDeleteConfirmDialog = false }
            )
        }
    } // Slut Scaffold
}

@Composable
fun BeanDetailHeaderCard(bean: Bean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Visa arkivstatus
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(bean.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (bean.isArchived) {
                    Text(
                        " (Archived)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            DetailRow("Roaster:", bean.roaster ?: "-")

            val dateStr = bean.roastDate?.let { detailDateFormat.format(it) } ?: "-"
            val ageStr = bean.roastDate?.let {
                val diffMillis = System.currentTimeMillis() - it.time
                val daysOld = TimeUnit.MILLISECONDS.toDays(diffMillis)
                when {
                    daysOld < 0 -> "(Future date)"
                    daysOld == 0L -> "(Roasted today)"
                    daysOld == 1L -> "(1 day old)"
                    else -> "($daysOld days old)"
                }
            } ?: ""
            DetailRow("Roast Date:", "$dateStr $ageStr")

            DetailRow("Remaining Weight:", "%.1f g".format(bean.remainingWeightGrams))
            DetailRow("Initial Weight:", bean.initialWeightGrams?.let { "%.1f g".format(it) } ?: "-")
            DetailRow("Notes:", bean.notes ?: "-")
        }
    }
}

@Composable
fun BrewItemCard(brew: Brew, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = brewItemDateFormat.format(brew.startedAt),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${brew.doseGrams} g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "View brew")
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(100.dp))
        Text(value)
    }
}


@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBeanDialog(
    editName: String, editRoaster: String, editRoastDateStr: String, editInitialWeightStr: String,
    editRemainingWeightStr: String, editNotes: String, onNameChange: (String) -> Unit,
    onRoasterChange: (String) -> Unit, onRoastDateChange: (String) -> Unit,
    onInitialWeightChange: (String) -> Unit, onRemainingWeightChange: (String) -> Unit,
    onNotesChange: (String) -> Unit, onDismiss: () -> Unit, onSaveBean: () -> Unit
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    val (initialYear, initialMonth, initialDay) = try {
        val date = detailDateFormat.parse(editRoastDateStr)!!
        calendar.time = date
        Triple(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
    } catch (_: Exception) {
        calendar.time = Date()
        Triple(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
    }

    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year: Int, month: Int, dayOfMonth: Int ->
                onRoastDateChange("$year-${String.format("%02d", month + 1)}-${String.format("%02d", dayOfMonth)}")
            },
            initialYear, initialMonth, initialDay
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Bean") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = editName, onValueChange = onNameChange, label = { Text("Name *") }, singleLine = true)
                OutlinedTextField(value = editRoaster, onValueChange = onRoasterChange, label = { Text("Roaster") }, singleLine = true)
                OutlinedTextField(
                    value = editRoastDateStr,
                    onValueChange = {},
                    label = { Text("Roast Date (YYYY-MM-DD)") },
                    readOnly = true,
                    modifier = Modifier.clickable { datePickerDialog.show() },
                    trailingIcon = {
                        Icon(Icons.Default.DateRange, "Select date", Modifier.clickable { datePickerDialog.show() })
                    }
                )
                OutlinedTextField(
                    value = editInitialWeightStr, onValueChange = onInitialWeightChange,
                    label = { Text("Initial Weight (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = editRemainingWeightStr, onValueChange = onRemainingWeightChange,
                    label = { Text("Current Weight (g) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(value = editNotes, onValueChange = onNotesChange, label = { Text("Notes") })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveBean()
                    onDismiss()
                },
                enabled = editName.isNotBlank() && (editRemainingWeightStr.toDoubleOrNull() ?: -1.0) >= 0.0
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * NYTT: Dialog för att bekräfta arkivering när vikten når noll.
 */
@Composable
fun ArchiveConfirmationDialog(
    beanName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Archive Bean?") },
        text = { Text("The remaining weight for '$beanName' is now zero. Do you want to archive this bean? Archived beans are hidden from the main list but can still be viewed.") },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Archive") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not Now") } }
    )
}


@Composable
fun DeleteConfirmationDialog(
    beanName: String,
    brewCount: Int,
    isArchived: Boolean, // Tar emot arkivstatus
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isArchived) "Delete bean permanently?" else "Cannot delete active bean") },
        text = {
            if (isArchived) {
                val brewText = if (brewCount == 1) "1 related brew" else "$brewCount related brews"
                Text("Are you sure you want to permanently delete the ARCHIVED bean '$beanName'? This will also permanently delete $brewText. This cannot be undone.")
            } else {
                Text("The bean '$beanName' is still active. You must first empty the bean (set Remaining Weight to 0.0 g) and then archive it before it can be permanently deleted. Archiving hides the bean from the main bean list.")
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = isArchived, // Endast aktiv om bönan är arkiverad
                colors = ButtonDefaults.buttonColors(containerColor = if (isArchived) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}