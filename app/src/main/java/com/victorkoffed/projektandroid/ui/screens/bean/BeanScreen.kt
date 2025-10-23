package com.victorkoffed.projektandroid.ui.screens.bean

// --- Core Compose & Foundation ---
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions

// --- Material Design 3 ---
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*

// --- Runtime State & Effects ---
import androidx.compose.runtime.*

// --- UI Helpers ---
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

// --- Dina Databas Entities & Views ---
import com.victorkoffed.projektandroid.data.db.Bean

// --- Din ViewModel ---
import com.victorkoffed.projektandroid.ui.viewmodel.bean.BeanViewModel

// --- Java Util & Text Formatting ---
import java.text.SimpleDateFormat
import java.util.*

// --- NY IMPORT FÖR LOGGNING ---
import android.util.Log

// Återanvändbar date formatter
private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeanScreen(vm: BeanViewModel) {
    // ... (states: beans, showAddDialog, etc. - oförändrat) ...
    val beans by vm.allBeans.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var beanToEdit by remember { mutableStateOf<Bean?>(null) }
    var beanToDelete by remember { mutableStateOf<Bean?>(null) }


    Scaffold(
        topBar = { TopAppBar(title = { Text("Bönor") }) }
    ) { paddingValues ->
        // ... (Column och LazyColumn - oförändrat) ...
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = { showAddDialog = true }) { Text("Lägg till ny böna") }
            Spacer(modifier = Modifier.height(16.dp))

            if (beans.isEmpty()) {
                Text("Inga bönor tillagda än.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(beans) { bean ->
                        BeanCard(
                            bean = bean,
                            onClick = { beanToEdit = bean },
                            onDeleteClick = { beanToDelete = bean }
                        )
                    }
                }
            }
        }


        // Dialog för att lägga till (oförändrat anrop)
        if (showAddDialog) {
            AddEditBeanDialog(
                onDismiss = { showAddDialog = false },
                onSaveBean = { name, roaster, roastDate, initialWeight, remainingWeight, notes ->
                    vm.addBean(name, roaster, roastDate, initialWeight, remainingWeight, notes)
                    showAddDialog = false
                }
            )
        }

        // Dialog för att redigera (oförändrat anrop)
        beanToEdit?.let { currentBean ->
            AddEditBeanDialog(
                beanToEdit = currentBean,
                onDismiss = { beanToEdit = null },
                onSaveBean = { name, roaster, roastDate, initialWeight, remainingWeight, notes ->
                    val updatedBean = currentBean.copy(name = name, roaster = roaster, roastDate = roastDate, initialWeightGrams = initialWeight, remainingWeightGrams = remainingWeight, notes = notes)
                    vm.updateBean(updatedBean)
                    beanToEdit = null
                }
            )
        }

        // Dialog för att ta bort (oförändrat anrop)
        beanToDelete?.let { currentBean ->
            DeleteConfirmationDialog(
                beanName = currentBean.name,
                onConfirm = {
                    vm.deleteBean(currentBean)
                    beanToDelete = null
                },
                onDismiss = { beanToDelete = null }
            )
        }
    }
}

// BeanCard (Oförändrad)
@Composable
fun BeanCard(bean: Bean, onClick: () -> Unit, onDeleteClick: () -> Unit) { /* ... som tidigare ... */ }

// --- AddEditBeanDialog MED LOGGUTSKRIFT ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditBeanDialog(
    beanToEdit: Bean? = null,
    onDismiss: () -> Unit,
    onSaveBean: (name: String, roaster: String?, roastDate: Date?, initialWeight: Double?, remainingWeight: Double, notes: String?) -> Unit
) {
    // ... (alla states: name, roaster, selectedDateMillis, showDatePicker, etc. - oförändrat) ...
    var name by remember { mutableStateOf(beanToEdit?.name ?: "") }
    var roaster by remember { mutableStateOf(beanToEdit?.roaster ?: "") }
    var selectedDateMillis by remember { mutableStateOf(beanToEdit?.roastDate?.time) }
    var showDatePicker by remember { mutableStateOf(false) }
    val formattedDate = remember(selectedDateMillis) {
        selectedDateMillis?.let { dateFormat.format(Date(it)) } ?: ""
    }
    var initialWeightStr by remember { mutableStateOf(beanToEdit?.initialWeightGrams?.toString() ?: "") }
    var remainingWeightStr by remember { mutableStateOf(beanToEdit?.remainingWeightGrams?.toString() ?: "") }
    var notes by remember { mutableStateOf(beanToEdit?.notes ?: "") }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDateMillis ?: System.currentTimeMillis()
    )


    // Visa DatePickerDialog (oförändrat)
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedDateMillis = datePickerState.selectedDateMillis
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Avbryt") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // AlertDialog (oförändrat förutom clickable)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (beanToEdit == null) "Lägg till ny böna" else "Redigera böna") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Namn *") }, singleLine = true)
                OutlinedTextField(value = roaster, onValueChange = { roaster = it }, label = { Text("Rosteri") }, singleLine = true)

                // Klickbart datumfält MED LOGGNING
                OutlinedTextField(
                    value = formattedDate,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Rostdatum (yyyy-mm-dd)") },
                    placeholder = { Text("Tryck för att välja...") },
                    trailingIcon = {
                        Icon(Icons.Default.DateRange, contentDescription = "Välj datum")
                    },
                    modifier = Modifier.clickable {
                        Log.d("BeanScreen", "Datumfält klickat! showDatePicker sätts till true.") // <-- LOGGUTSKRIFT
                        showDatePicker = true
                    }
                )

                OutlinedTextField(
                    value = initialWeightStr, onValueChange = { initialWeightStr = it },
                    label = { Text("Ursprungsvikt (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = remainingWeightStr, onValueChange = { remainingWeightStr = it },
                    label = { Text("Nuvarande vikt (g) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Noteringar") })
            }
        },
        confirmButton = { /* ... (oförändrat) ... */ },
        dismissButton = { /* ... (oförändrat) ... */ }
    )
}
// --- SLUT PÅ NY DIALOG ---

// AddBeanDialog - KAN TAS BORT
// EditBeanDialog - KAN TAS BORT

// DeleteConfirmationDialog (Oförändrad)
@Composable
fun DeleteConfirmationDialog(
    beanName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ta bort böna?") },
        text = { Text("Är du säker på att du vill ta bort '$beanName'? Detta går inte att ångra.") },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Ta bort") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } }
    )
}