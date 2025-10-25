package com.victorkoffed.projektandroid.ui.screens.bean

// --- NYA IMPORTER FÖR SNACKBAR ---
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
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.victorkoffed.projektandroid.CoffeeJournalApplication
import com.victorkoffed.projektandroid.data.db.Bean
import com.victorkoffed.projektandroid.data.db.Brew
import com.victorkoffed.projektandroid.ui.viewmodel.bean.BeanDetailViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.bean.BeanDetailViewModelFactory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// --- SLUT NYA IMPORTER ---


// Formatterare
@SuppressLint("ConstantLocale")
private val detailDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
@SuppressLint("ConstantLocale")
private val brewItemDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeanDetailScreen(
    beanId: Long,
    onNavigateBack: () -> Unit,
    onBrewClick: (Long) -> Unit // För att navigera till BrewDetailScreen
) {
    val application = LocalContext.current.applicationContext as CoffeeJournalApplication
    val repository = application.coffeeRepository

    val viewModel: BeanDetailViewModel = viewModel(
        key = beanId.toString(),
        factory = BeanDetailViewModelFactory(repository, beanId)
    )

    val state by viewModel.beanDetailState.collectAsState()
    val isEditing by remember { derivedStateOf { viewModel.isEditing } }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // --- NYTT: Snackbar state och scope ---
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // --- SLUT NYTT ---

    // --- NYTT: LaunchedEffect för att visa fel ---
    LaunchedEffect(state.error) {
        if (state.error != null) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = state.error!!,
                    duration = SnackbarDuration.Long // Visa felet lite längre
                )
            }
            // Nollställ felet i ViewModel
            viewModel.clearError()
        }
    }
    // --- SLUT NYTT ---

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
        // --- NYTT: Lägg till snackbarHost ---
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { snackbarData ->
                    Snackbar(snackbarData = snackbarData, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, actionColor = MaterialTheme.colorScheme.onPrimary)
                }
            )
        }
        // --- SLUT NYTT ---
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
                    // Objekt 1: Bönans detaljkort
                    item {
                        BeanDetailHeaderCard(bean = state.bean!!)
                    }

                    // Objekt 2: Titel för brygg-listan
                    item {
                        Text(
                            "Brews using this bean",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    // Objekt 3...N: Bryggningarna
                    if (state.brews.isEmpty()) {
                        item {
                            Text(
                                "No brews recorded for this bean yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                    } else {
                        items(state.brews) { brew ->
                            BrewItemCard(brew = brew, onClick = { onBrewClick(brew.id) })
                        }
                    }
                }
            }
        }

        // --- Dialogrutor (Flyttade från BeanScreen) ---

        // Dialog för att redigera
        if (isEditing && state.bean != null) {
            // Återanvänd EditBeanDialog, men skicka in states från BeanDetailViewModel
            EditBeanDialog(
                // Skicka med nuvarande redigerings-states
                editName = viewModel.editName,
                editRoaster = viewModel.editRoaster,
                editRoastDateStr = viewModel.editRoastDateStr,
                editInitialWeightStr = viewModel.editInitialWeightStr,
                editRemainingWeightStr = viewModel.editRemainingWeightStr,
                editNotes = viewModel.editNotes,
                // Skicka med funktioner för att uppdatera dem
                onNameChange = { viewModel.editName = it },
                onRoasterChange = { viewModel.editRoaster = it },
                onRoastDateChange = { viewModel.editRoastDateStr = it },
                onInitialWeightChange = { viewModel.editInitialWeightStr = it },
                onRemainingWeightChange = { viewModel.editRemainingWeightStr = it },
                onNotesChange = { viewModel.editNotes = it },
                // Spara/Avbryt-callbacks
                onDismiss = { viewModel.cancelEditing() },
                onSaveBean = { viewModel.saveChanges() }
            )
        }

        // Dialog för att ta bort (UPPDATERAD)
        if (showDeleteConfirmDialog && state.bean != null) {
            DeleteConfirmationDialog(
                beanName = state.bean!!.name,
                brewCount = state.brews.size, // SKICKAR MED ANTALET BRYGGNINGAR
                onConfirm = {
                    viewModel.deleteBean {
                        showDeleteConfirmDialog = false
                        onNavigateBack() // Navigera tillbaka efter lyckad borttagning
                    }
                },
                onDismiss = { showDeleteConfirmDialog = false }
            )
        }
    }
}

// --- Resten av Composable-funktionerna (BeanDetailHeaderCard, BrewItemCard, DetailRow, EditBeanDialog) är oförändrade ---

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
        colors = CardDefaults.cardColors(containerColor = Color.White) // <-- VITA KORT
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
                    color = Color.Gray
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "View brew")
        }
    }
}

// Enkel rad-komponent (kopierad från BrewDetailScreen)
@Composable
fun DetailRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(100.dp))
        Text(value)
    }
}


// --- Dialogrutor (Flyttade från BeanScreen.kt och Modifierade) ---

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBeanDialog(
    // States
    editName: String,
    editRoaster: String,
    editRoastDateStr: String,
    editInitialWeightStr: String,
    editRemainingWeightStr: String,
    editNotes: String,
    // Callbacks
    onNameChange: (String) -> Unit,
    onRoasterChange: (String) -> Unit,
    onRoastDateChange: (String) -> Unit,
    onInitialWeightChange: (String) -> Unit,
    onRemainingWeightChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,

    onDismiss: () -> Unit,
    onSaveBean: () -> Unit
) {
    // Logik för kalender (flyttad hit)
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

// DeleteConfirmationDialog (UPPDATERAD)
@Composable
fun DeleteConfirmationDialog(
    beanName: String,
    brewCount: Int, // NY PARAMETER
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
