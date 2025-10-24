package com.victorkoffed.projektandroid.ui.screens.bean

// --- Core Compose & Foundation ---
import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions // <--- VIKTIG IMPORT

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType // <--- VIKTIG IMPORT
import androidx.compose.ui.unit.dp

// --- Dina Databas Entities & Views ---
import com.victorkoffed.projektandroid.data.db.Bean

// --- Din ViewModel ---
import com.victorkoffed.projektandroid.ui.viewmodel.bean.BeanViewModel

// --- Java Util & Text Formatting ---
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit // <-- NY IMPORT FÖR ÅLDER

// Återanvändbar date formatter
private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeanScreen(vm: BeanViewModel) {
    val beans by vm.allBeans.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var beanToEdit by remember { mutableStateOf<Bean?>(null) }
    var beanToDelete by remember { mutableStateOf<Bean?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Beans") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = { showAddDialog = true }) { Text("Add new bean") }
            Spacer(modifier = Modifier.height(16.dp))

            if (beans.isEmpty()) {
                Text("No beans added yet.")
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

        // Dialog för att lägga till
        if (showAddDialog) {
            AddBeanDialog(
                onDismiss = { showAddDialog = false },
                onAddBean = { name, roaster, roastDateStr, initialWeightStr, remainingWeight, notes ->
                    vm.addBean(name, roaster, roastDateStr, initialWeightStr, remainingWeight, notes)
                    showAddDialog = false
                }
            )
        }

        // Dialog för att redigera
        beanToEdit?.let { currentBean ->
            EditBeanDialog(
                bean = currentBean,
                onDismiss = { beanToEdit = null },
                onSaveBean = { name, roaster, roastDateStr, initialWeightStr, remainingWeight, notes ->
                    vm.updateBean(currentBean, name, roaster, roastDateStr, initialWeightStr, remainingWeight, notes)
                    beanToEdit = null
                }
            )
        }

        // Dialog för att ta bort
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

// BeanCard (UPPDATERAD med bön-ålder)
@Composable
fun BeanCard(bean: Bean, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface) // Fix 1: Vit bakgrund
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(bean.name, style = MaterialTheme.typography.titleMedium)
                bean.roaster?.let { Text("Roaster: $it", style = MaterialTheme.typography.bodyMedium) }

                // --- NY LOGIK FÖR DATUM OCH ÅLDER ---
                bean.roastDate?.let { roastDate ->
                    // Beräkna ålder i dagar
                    val diffMillis = System.currentTimeMillis() - roastDate.time
                    val daysOld = TimeUnit.MILLISECONDS.toDays(diffMillis)

                    val dateStr = dateFormat.format(roastDate)
                    val ageStr = when {
                        daysOld < 0 -> "(Future date)" // Om datumet är felaktigt
                        daysOld == 0L -> "(Roasted today)"
                        daysOld == 1L -> "(1 day old)"
                        else -> "($daysOld days old)"
                    }

                    // Visa både datum och ålder
                    Text("Roast Date:: $dateStr $ageStr", style = MaterialTheme.typography.bodySmall)
                }
                // --- SLUT PÅ NY LOGIK ---

                Text("Remaining: %.1f g".format(bean.remainingWeightGrams), style = MaterialTheme.typography.bodyMedium)
                bean.initialWeightGrams?.let { Text("Initial: %.1f g".format(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
                bean.notes?.let { Text("Notes: $it", style = MaterialTheme.typography.bodySmall) }
            }
            IconButton(onClick = onDeleteClick, modifier = Modifier.padding(end = 8.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete bean",
                    tint = MaterialTheme.colorScheme.primary // Fix 2: DCC7AA färg
                )
            }
        }
    }
}


// AddBeanDialog (UPPDATERAD MED KALENDER)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBeanDialog(
    onDismiss: () -> Unit,
    onAddBean: (name: String, roaster: String?, roastDateStr: String?, initialWeightStr: String?, remainingWeight: Double, notes: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var roaster by remember { mutableStateOf("") }
    var roastDateStr by remember { mutableStateOf("") } // Detta state uppdateras nu av kalendern
    var initialWeightStr by remember { mutableStateOf("") }
    var remainingWeightStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    // --- NY KOD FÖR KALENDER STARTAR HÄR ---

    // 1. Hämta context och en kalenderinstans
    val context = LocalContext.current
    val calendar = Calendar.getInstance()

    // 2. Ställ in startdatum för kalendern (dagens datum)
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH)
    val day = calendar.get(Calendar.DAY_OF_MONTH)

    // 3. Skapa en DatePickerDialog
    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, selectedYear: Int, selectedMonth: Int, selectedDayOfMonth: Int ->
                // Formatera datumet och uppdatera ditt state
                // Månader är 0-indexerade, så lägg till 1
                roastDateStr = "$selectedYear-${String.format("%02d", selectedMonth + 1)}-${String.format("%02d", selectedDayOfMonth)}"
            },
            year,
            month,
            day
        )
    }
    // --- NY KOD SLUTAR HÄR ---


    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add new bean") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name *") }, singleLine = true)
                OutlinedTextField(value = roaster, onValueChange = { roaster = it }, label = { Text("Roaster") }, singleLine = true)

                // --- UPPDATERAT DATUMFÄLT ---
                OutlinedTextField(
                    value = roastDateStr,
                    onValueChange = {}, // Låt vara tom, uppdateras av dialogen
                    label = { Text("Roast Date: (yyyy-mm-dd)") },
                    placeholder = { Text("Select date...") },
                    singleLine = true,
                    readOnly = true, // <--- VIKTIGT: Förhindrar tangentbordet
                    modifier = Modifier.clickable(onClick = { // <--- VIKTIGT: Gör hela fältet klickbart
                        datePickerDialog.show()
                    }),
                    trailingIcon = { // <--- Bonus: Lägg till en klickbar ikon
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = "Select dateSelect date",
                            modifier = Modifier.clickable { datePickerDialog.show() }
                        )
                    }
                )
                // --- SLUT PÅ UPPDATERING ---

                OutlinedTextField(
                    value = initialWeightStr, onValueChange = { initialWeightStr = it },
                    label = { Text("Initial Weight (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = remainingWeightStr, onValueChange = { remainingWeightStr = it },
                    label = { Text("Current Weight (g) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val remainingWeight = remainingWeightStr.toDoubleOrNull()
                    if (name.isNotBlank() && remainingWeight != null && remainingWeight >= 0) {
                        onAddBean(name, roaster.takeIf { it.isNotBlank() }, roastDateStr.takeIf { it.isNotBlank() }, initialWeightStr.takeIf { it.isNotBlank() }, remainingWeight, notes.takeIf { it.isNotBlank() })
                    }
                },
                enabled = name.isNotBlank() && (remainingWeightStr.toDoubleOrNull() ?: -1.0) >= 0.0
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// EditBeanDialog (UPPDATERAD MED KALENDER)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBeanDialog(
    bean: Bean,
    onDismiss: () -> Unit,
    onSaveBean: (name: String, roaster: String?, roastDateStr: String?, initialWeightStr: String?, remainingWeight: Double, notes: String?) -> Unit
) {
    var name by remember { mutableStateOf(bean.name) }
    var roaster by remember { mutableStateOf(bean.roaster ?: "") }
    var roastDateStr by remember { mutableStateOf(bean.roastDate?.let { dateFormat.format(it) } ?: "") }
    var initialWeightStr by remember { mutableStateOf(bean.initialWeightGrams?.toString() ?: "") }
    var remainingWeightStr by remember { mutableStateOf(bean.remainingWeightGrams.toString()) }
    var notes by remember { mutableStateOf(bean.notes ?: "") }


    // --- NY KOD FÖR KALENDER STARTAR HÄR ---

    val context = LocalContext.current
    val calendar = Calendar.getInstance()

    // Annorlunda logik här: Försök parsa befintligt datum, annars ta dagens
    val (initialYear, initialMonth, initialDay) = try {
        // Försök parsa det befintliga datumet
        val date = dateFormat.parse(roastDateStr)!!
        calendar.time = date
        Triple(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
    } catch (e: Exception) {
        // Använd dagens datum om strängen är tom eller ogiltig
        calendar.time = Date()
        Triple(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
    }

    // Skapa DatePickerDialog
    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year: Int, month: Int, dayOfMonth: Int ->
                roastDateStr = "$year-${String.format("%02d", month + 1)}-${String.format("%02d", dayOfMonth)}"
            },
            initialYear, // Använd det parsade/dagens datum
            initialMonth,
            initialDay
        )
    }
    // --- NY KOD SLUTAR HÄR ---


    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Bean") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Namn *") }, singleLine = true)
                OutlinedTextField(value = roaster, onValueChange = { roaster = it }, label = { Text("Roaster") }, singleLine = true)

                // --- UPPDATERAT DATUMFÄLT (samma som i Add) ---
                OutlinedTextField(
                    value = roastDateStr,
                    onValueChange = {},
                    label = { Text("Roast Date: (yyyy-mm-dd)") },
                    placeholder = { Text("Select date...") },
                    singleLine = true,
                    readOnly = true,
                    modifier = Modifier.clickable(onClick = {
                        datePickerDialog.show()
                    }),
                    trailingIcon = {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = "Select date",
                            modifier = Modifier.clickable { datePickerDialog.show() }
                        )
                    }
                )
                // --- SLUT PÅ UPPDATERING ---

                OutlinedTextField(
                    value = initialWeightStr, onValueChange = { initialWeightStr = it },
                    label = { Text("Initial Weight (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = remainingWeightStr, onValueChange = { remainingWeightStr = it },
                    label = { Text("Current Weight (g) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val remainingWeight = remainingWeightStr.toDoubleOrNull()
                    if (name.isNotBlank() && remainingWeight != null && remainingWeight >= 0) {
                        onSaveBean(name, roaster.takeIf { it.isNotBlank() }, roastDateStr.takeIf { it.isNotBlank() }, initialWeightStr.takeIf { it.isNotBlank() }, remainingWeight, notes.takeIf { it.isNotBlank() })
                    }
                },
                enabled = name.isNotBlank() && (remainingWeightStr.toDoubleOrNull() ?: -1.0) >= 0.0
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// DeleteConfirmationDialog (Oförändrad)
@Composable
fun DeleteConfirmationDialog(
    beanName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete bean?") },
        text = { Text("Are you sure you want to delete '$beanName'? This cannot be undone.") },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Ta bort") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}