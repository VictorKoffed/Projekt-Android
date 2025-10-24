package com.victorkoffed.projektandroid.ui.screens.method

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
import androidx.compose.ui.unit.dp
import com.victorkoffed.projektandroid.data.db.Method
import com.victorkoffed.projektandroid.ui.viewmodel.method.MethodViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MethodScreen(vm: MethodViewModel) {
    val methods by vm.allMethods.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var methodToEdit by remember { mutableStateOf<Method?>(null) }
    var methodToDelete by remember { mutableStateOf<Method?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Brew Methods") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = { showAddDialog = true }) {
                Text("Add new method")
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (methods.isEmpty()) {
                Text("No methods added yet.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(methods) { method ->
                        MethodCard(
                            method = method,
                            onClick = { methodToEdit = method },
                            onDeleteClick = { methodToDelete = method }
                        )
                    }
                }
            }
        }

        // Dialogs
        if (showAddDialog) {
            AddMethodDialog(
                onDismiss = { showAddDialog = false },
                onAddMethod = { name ->
                    vm.addMethod(name)
                    showAddDialog = false
                }
            )
        }

        methodToEdit?.let { currentMethod ->
            EditMethodDialog(
                method = currentMethod,
                onDismiss = { methodToEdit = null },
                onSaveMethod = { updatedMethod ->
                    vm.updateMethod(updatedMethod)
                    methodToEdit = null
                }
            )
        }

        methodToDelete?.let { currentMethod ->
            DeleteMethodConfirmationDialog(
                methodName = currentMethod.name,
                onConfirm = {
                    vm.deleteMethod(currentMethod)
                    methodToDelete = null
                },
                onDismiss = { methodToDelete = null }
            )
        }
    }
}

@Composable
fun MethodCard(
    method: Method,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface) // Ändring 1: Vit bakgrund
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically // Center vertically
        ) {
            Text(method.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onDeleteClick, modifier = Modifier.padding(end = 8.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete method",
                    tint = MaterialTheme.colorScheme.primary // Ändring 2: DCC7AA färg
                )
            }
        }
    }
}

// Dialog for Adding
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMethodDialog(
    onDismiss: () -> Unit,
    onAddMethod: (name: String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add new method") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Method Name *") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onAddMethod(name) },
                enabled = name.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// Dialog for Editing
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMethodDialog(
    method: Method,
    onDismiss: () -> Unit,
    onSaveMethod: (updatedMethod: Method) -> Unit
) {
    var name by remember { mutableStateOf(method.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Method") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Metodnamn *") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSaveMethod(method.copy(name = name))
                    }
                },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// Dialog for Deleting
@Composable
fun DeleteMethodConfirmationDialog(
    methodName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete method?") },
        text = { Text("ÄAre you sure you want to delete '$methodName'? Brews that used this method will lose the connection.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                // Behåller error-färg här för att indikera en permanent, destruktiv åtgärd (trots önskemål om tema-färg, är rött standard UX för Delete)
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Ta bort") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}