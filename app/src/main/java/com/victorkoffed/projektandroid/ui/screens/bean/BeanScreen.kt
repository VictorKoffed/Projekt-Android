package com.victorkoffed.projektandroid.ui.screens.bean

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.victorkoffed.projektandroid.data.db.Bean
import com.victorkoffed.projektandroid.ui.viewmodel.bean.BeanViewModel
import java.text.SimpleDateFormat
import java.util.*

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
            Button(onClick = { showAddDialog = true }) {
                Text("Lägg till ny böna")
            }
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
                onAddBean = { name, roaster, roastDate, initialWeight, remainingWeight, notes ->
                    vm.addBean(name, roaster, roastDate, initialWeight, remainingWeight, notes)
                    showAddDialog = false
                }
            )
        }

        // Dialog för att redigera
        beanToEdit?.let { currentBean ->
            EditBeanDialog(
                bean = currentBean,
                onDismiss = { beanToEdit = null },
                onSaveBean = { updatedBean ->
                    vm.updateBean(updatedBean)
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

@Composable
fun BeanCard(
    bean: Bean,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Text(bean.name, style = MaterialTheme.typography.titleMedium)
                bean.roaster?.let { Text("Rosteri: $it", style = MaterialTheme.typography.bodyMedium) }
                bean.roastDate?.let { Text("Rostdatum: ${dateFormat.format(it)}", style = MaterialTheme.typography.bodySmall) }
                Text(
                    "Kvarvarande vikt: %.1f g".format(bean.remainingWeightGrams),
                    style = MaterialTheme.typography.bodyMedium
                )
                bean.initialWeightGrams?.let { Text("Ursprungsvikt: %.1f g".format(it), style = MaterialTheme.typography.bodySmall) }
                bean.notes?.let { Text("Noteringar: $it", style = MaterialTheme.typography.bodySmall) }
            }
            IconButton(onClick = onDeleteClick, modifier = Modifier.padding(end = 8.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Ta bort böna",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBeanDialog(
    onDismiss: () -> Unit,
    onAddBean: (name: String, roaster: String?, roastDate: Date?, initialWeight: Double?, remainingWeight: Double, notes: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var roaster by remember { mutableStateOf("") }
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
                    value = remainingWeightStr,
                    onValueChange = { remainingWeightStr = it },
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
                        onAddBean(name, roaster.takeIf { it.isNotBlank() }, null, null, remainingWeight, notes.takeIf { it.isNotBlank() })
                    }
                },
                enabled = name.isNotBlank() && (remainingWeightStr.toDoubleOrNull() ?: -1.0) >= 0.0
            ) { Text("Lägg till") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBeanDialog(
    bean: Bean,
    onDismiss: () -> Unit,
    onSaveBean: (updatedBean: Bean) -> Unit
) {
    var name by remember { mutableStateOf(bean.name) }
    var roaster by remember { mutableStateOf(bean.roaster ?: "") }
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
                    value = remainingWeightStr,
                    onValueChange = { remainingWeightStr = it },
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
                        val updatedBean = bean.copy(
                            name = name,
                            roaster = roaster.takeIf { it.isNotBlank() },
                            remainingWeightGrams = remainingWeight,
                            notes = notes.takeIf { it.isNotBlank() }
                        )
                        onSaveBean(updatedBean)
                    }
                },
                enabled = name.isNotBlank() && (remainingWeightStr.toDoubleOrNull() ?: -1.0) >= 0.0
            ) { Text("Spara") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } }
    )
}

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
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Ta bort")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Avbryt")
            }
        }
    )
}
