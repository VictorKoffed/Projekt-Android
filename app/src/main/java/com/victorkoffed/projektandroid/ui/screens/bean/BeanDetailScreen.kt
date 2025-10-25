// app/src/main/java/com/victorkoffed/projektandroid/ui/screens/bean/BeanDetailScreen.kt
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
// import androidx.lifecycle.viewmodel.compose.viewModel // Redan inkluderad via hiltViewModel
import androidx.hilt.navigation.compose.hiltViewModel // Ny import för Hilt
import com.victorkoffed.projektandroid.CoffeeJournalApplication // Behåll om ThemedSnackbar behöver den, annars kan den tas bort
import com.victorkoffed.projektandroid.ThemedSnackbar
import com.victorkoffed.projektandroid.data.db.Bean
import com.victorkoffed.projektandroid.data.db.Brew
import com.victorkoffed.projektandroid.ui.viewmodel.bean.BeanDetailViewModel
// import com.victorkoffed.projektandroid.ui.viewmodel.bean.BeanDetailViewModelFactory // TAS BORT
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
    // beanId: Long, // <-- PARAMETER BORTTAGEN
    onNavigateBack: () -> Unit,
    onBrewClick: (Long) -> Unit,
    // Hämta ViewModel direkt här med Hilt.
    viewModel: BeanDetailViewModel = hiltViewModel()
) {
    // UI-states från ViewModel
    val state by viewModel.beanDetailState.collectAsState()
    val isEditing by remember { derivedStateOf { viewModel.isEditing } }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Snackbar-hantering
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.startEditing() }, enabled = state.bean != null) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { showDeleteConfirmDialog = true }, enabled = state.bean != null) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { snackbarData ->
                    ThemedSnackbar(snackbarData)
                }
            )
        }
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
                        items(state.brews) { brew ->
                            BrewItemCard(brew = brew, onClick = { onBrewClick(brew.id) })
                        }
                    }
                }
            }
            else -> { // Fallback om bönan är null och inte laddar (t.ex. vid fel ID)
                Box(Modifier.fillMaxSize().padding(paddingValues), Alignment.Center) {
                    Text(state.error ?: "Bean not found.")
                }
            }
        } // Slut when

        // --- Dialogrutor ---
        if (isEditing && state.bean != null) {
            EditBeanDialog(
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

        if (showDeleteConfirmDialog && state.bean != null) {
            DeleteConfirmationDialog(
                beanName = state.bean!!.name,
                brewCount = state.brews.size,
                onConfirm = {
                    viewModel.deleteBean {
                        showDeleteConfirmDialog = false
                        onNavigateBack()
                    }
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
            Text(bean.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

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
                onClick = onSaveBean,
                enabled = editName.isNotBlank() && (editRemainingWeightStr.toDoubleOrNull() ?: -1.0) >= 0.0
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun DeleteConfirmationDialog(
    beanName: String,
    brewCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete bean?") },
        text = {
            val brewText = if (brewCount == 1) "1 related brew" else "$brewCount related brews"
            Text("Are you sure you want to delete '$beanName'? This will also permanently delete $brewText. This cannot be undone.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}