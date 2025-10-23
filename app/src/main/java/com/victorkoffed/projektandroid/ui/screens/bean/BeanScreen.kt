package com.victorkoffed.projektandroid.ui.screens.bean

// --- Core Compose & Foundation ---
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions // <--- VIKTIG IMPORT

// --- Material Design 3 ---
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*

// --- Runtime State & Effects ---
import androidx.compose.runtime.*

// --- UI Helpers ---
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType // <--- VIKTIG IMPORT
import androidx.compose.ui.unit.dp

// --- Dina Databas Entities & Views ---
import com.victorkoffed.projektandroid.data.db.Bean

// --- Din ViewModel ---
import com.victorkoffed.projektandroid.ui.viewmodel.bean.BeanViewModel

// --- Java Util & Text Formatting ---
import java.text.SimpleDateFormat
import java.util.*

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
        topBar = { TopAppBar(title = { Text("Bönor") }) }
    ) { paddingValues ->
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

// BeanCard
@Composable
fun BeanCard(bean: Bean, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(bean.name, style = MaterialTheme.typography.titleMedium)
                bean.roaster?.let { Text("Rosteri: $it", style = MaterialTheme.typography.bodyMedium) }
                bean.roastDate?.let { Text("Rostdatum: ${dateFormat.format(it)}", style = MaterialTheme.typography.bodySmall) }
                Text("Kvar: %.1f g".format(bean.remainingWeightGrams), style = MaterialTheme.typography.bodyMedium)
                bean.initialWeightGrams?.let { Text("Ursprung: %.1f g".format(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
                bean.notes?.let { Text("Noteringar: $it", style = MaterialTheme.typography.bodySmall) }
            }
            IconButton(onClick = onDeleteClick, modifier = Modifier.padding(end = 8.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Ta bort böna", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}


// AddBeanDialog
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBeanDialog(
    onDismiss: () -> Unit,
    onAddBean: (name: String, roaster: String?, roastDateStr: String?, initialWeightStr: String?, remainingWeight: Double, notes: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var roaster by remember { mutableStateOf("") }
    var roastDateStr by remember { mutableStateOf("") }
    var initialWeightStr by remember { mutableStateOf("") }
    var remainingWeightStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lägg till ny böna") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Namn *") }, singleLine = true)
                OutlinedTextField(value = roaster, onValueChange = { roaster = it }, label = { Text("Rosteri") }, singleLine = true)
                OutlinedTextField(
                    value = roastDateStr, onValueChange = { roastDateStr = it },
                    label = { Text("Rostdatum (yyyy-mm-dd)") },
                    placeholder = { Text("T.ex. 2025-10-23") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number) // Använder Number här
                )
                OutlinedTextField(
                    value = initialWeightStr, onValueChange = { initialWeightStr = it },
                    label = { Text("Ursprungsvikt (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal) // Använder Decimal här
                )
                OutlinedTextField(
                    value = remainingWeightStr, onValueChange = { remainingWeightStr = it },
                    label = { Text("Nuvarande vikt (g) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal) // Använder Decimal här
                )
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Noteringar") })
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
            ) { Text("Lägg till") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } }
    )
}

// EditBeanDialog
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Redigera böna") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Namn *") }, singleLine = true)
                OutlinedTextField(value = roaster, onValueChange = { roaster = it }, label = { Text("Rosteri") }, singleLine = true)
                OutlinedTextField(
                    value = roastDateStr, onValueChange = { roastDateStr = it },
                    label = { Text("Rostdatum (yyyy-mm-dd)") },
                    placeholder = { Text("T.ex. 2025-10-23") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
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
        confirmButton = {
            Button(
                onClick = {
                    val remainingWeight = remainingWeightStr.toDoubleOrNull()
                    if (name.isNotBlank() && remainingWeight != null && remainingWeight >= 0) {
                        onSaveBean(name, roaster.takeIf { it.isNotBlank() }, roastDateStr.takeIf { it.isNotBlank() }, initialWeightStr.takeIf { it.isNotBlank() }, remainingWeight, notes.takeIf { it.isNotBlank() })
                    }
                },
                enabled = name.isNotBlank() && (remainingWeightStr.toDoubleOrNull() ?: -1.0) >= 0.0
            ) { Text("Spara") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } }
    )
}

// DeleteConfirmationDialog
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

