package com.victorkoffed.projektandroid.ui.screens.grinder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.victorkoffed.projektandroid.data.db.Grinder
import com.victorkoffed.projektandroid.ui.viewmodel.grinder.GrinderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrinderScreen(vm: GrinderViewModel) {
    val grinders by vm.allGrinders.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var grinderToEdit by remember { mutableStateOf<Grinder?>(null) } // State för redigering
    var grinderToDelete by remember { mutableStateOf<Grinder?>(null) } // State för borttagning

    Scaffold(
        topBar = { TopAppBar(title = { Text("Grinders") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = { showAddDialog = true }) {
                Text("Add new grinder")
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (grinders.isEmpty()) {
                Text("No grinders added yet.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(grinders) { grinder ->
                        GrinderCard(
                            grinder = grinder,
                            onClick = { grinderToEdit = grinder }, // Öppna redigering
                            onDeleteClick = { grinderToDelete = grinder } // Visa bekräftelse
                        )
                    }
                }
            }
        }

        // Dialog för att lägga till (oförändrad)
        if (showAddDialog) {
            AddGrinderDialog(
                onDismiss = { showAddDialog = false },
                onAddGrinder = { name, notes ->
                    vm.addGrinder(name, notes)
                    showAddDialog = false
                }
            )
        }

        // Dialog för att redigera
        grinderToEdit?.let { currentGrinder ->
            EditGrinderDialog(
                grinder = currentGrinder,
                onDismiss = { grinderToEdit = null },
                onSaveGrinder = { updatedGrinder ->
                    vm.updateGrinder(updatedGrinder)
                    grinderToEdit = null
                }
            )
        }

        // Dialog för att bekräfta borttagning
        grinderToDelete?.let { currentGrinder ->
            DeleteGrinderConfirmationDialog(
                grinderName = currentGrinder.name,
                onConfirm = {
                    vm.deleteGrinder(currentGrinder)
                    grinderToDelete = null
                },
                onDismiss = { grinderToDelete = null }
            )
        }
    }
}

@Composable
fun GrinderCard(
    grinder: Grinder,
    onClick: () -> Unit, // Callback för klick
    onDeleteClick: () -> Unit // Callback för delete-ikon
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick), // Gör kortet klickbart
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface) // Ändring 1: Vit bakgrund
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Text(grinder.name, style = MaterialTheme.typography.titleMedium)
                grinder.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            // Delete-ikon knapp
            IconButton(onClick = onDeleteClick, modifier = Modifier.padding(end = 8.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete grinder",
                    tint = MaterialTheme.colorScheme.primary // Ändring 2: DCC7AA färg
                )
            }
        }
    }
}

// Dialog för att lägga till (oförändrad)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGrinderDialog(
    onDismiss: () -> Unit,
    onAddGrinder: (name: String, notes: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add new grinder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Namn *") }, singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes (e.g. burrs)") })
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onAddGrinder(name, notes.takeIf { it.isNotBlank() }) },
                enabled = name.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// NY DIALOG: För att redigera kvarn
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditGrinderDialog(
    grinder: Grinder, // Tar emot kvarnen som ska redigeras
    onDismiss: () -> Unit,
    onSaveGrinder: (updatedGrinder: Grinder) -> Unit
) {
    // Förfyll fälten
    var name by remember { mutableStateOf(grinder.name) }
    var notes by remember { mutableStateOf(grinder.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Grinder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Namn *") }, singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Noteringar") })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        // Skapa kopia med uppdaterad data, behåll ID
                        val updatedGrinder = grinder.copy(
                            name = name,
                            notes = notes.takeIf { it.isNotBlank() }
                        )
                        onSaveGrinder(updatedGrinder)
                    }
                },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// NY DIALOG: För att bekräfta borttagning av kvarn
@Composable
fun DeleteGrinderConfirmationDialog(
    grinderName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete grinder?") },
        text = { Text("Are you sure you want to delete '$grinderName'? Brews that used this grinder will lose the connection.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Ta bort") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}