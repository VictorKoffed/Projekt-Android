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
import androidx.compose.material.icons.filled.Menu
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

/**
 * Huvudskärm för att visa, lägga till, redigera och ta bort kvarnar.
 * Använder Compose State för att hantera vilka dialoger som visas.
 * @param onMenuClick Callback för att öppna navigationslådan (hamburgermenyn).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrinderScreen(
    vm: GrinderViewModel,
    onMenuClick: () -> Unit
) {
    // Hämta kvarnar som en State från ViewModel
    val grinders by vm.allGrinders.collectAsState()

    // State-variabler som styr vilka dialogrutor som är synliga
    var showAddDialog by remember { mutableStateOf(false) }
    // Används för att skicka det Grinder-objekt som ska redigeras till dialogen
    var grinderToEdit by remember { mutableStateOf<Grinder?>(null) }
    // Används för att skicka det Grinder-objekt som ska bekräftas för borttagning
    var grinderToDelete by remember { mutableStateOf<Grinder?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Grinders") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            )
        }
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
                            onClick = { grinderToEdit = grinder },
                            onDeleteClick = { grinderToDelete = grinder }
                        )
                    }
                }
            }
        }

        // --- Dialoghantering ---

        // Lägg till dialog
        if (showAddDialog) {
            AddGrinderDialog(
                onDismiss = { },
                onAddGrinder = { name, notes ->
                    vm.addGrinder(name, notes)
                }
            )
        }

        // Redigera dialog (visas endast om grinderToEdit är satt)
        grinderToEdit?.let { currentGrinder ->
            EditGrinderDialog(
                grinder = currentGrinder,
                onDismiss = { },
                onSaveGrinder = { updatedGrinder ->
                    vm.updateGrinder(updatedGrinder)
                }
            )
        }

        // Bekräftelse för borttagning (visas endast om grinderToDelete är satt)
        grinderToDelete?.let { currentGrinder ->
            DeleteGrinderConfirmationDialog(
                grinderName = currentGrinder.name,
                onConfirm = {
                    vm.deleteGrinder(currentGrinder)
                },
                onDismiss = { }
            )
        }
    }
}

/**
 * Komponent för att visa en enskild kvarn i listan.
 * Klick på kortet startar redigering, klick på ikon startar borttagning.
 */
@Composable
fun GrinderCard(
    grinder: Grinder,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Text(grinder.name, style = MaterialTheme.typography.titleMedium)
                // Visa anteckningar endast om de finns
                grinder.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            // Delete-ikon knapp
            IconButton(onClick = onDeleteClick, modifier = Modifier.padding(end = 8.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete grinder",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Dialogruta för att lägga till en ny kvarn.
 */
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
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name *") }, singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes (e.g. burrs)") })
            }
        },
        confirmButton = {
            Button(
                // Kontrollerar att namnet inte är tomt före tillägg
                onClick = { if (name.isNotBlank()) onAddGrinder(name, notes.takeIf { it.isNotBlank() }) },
                enabled = name.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Dialogruta för att redigera en befintlig kvarn.
 * Använder Grinder-objektet för att förfylla fälten.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditGrinderDialog(
    grinder: Grinder,
    onDismiss: () -> Unit,
    onSaveGrinder: (updatedGrinder: Grinder) -> Unit
) {
    // Förfyll fälten med befintliga värden
    var name by remember { mutableStateOf(grinder.name) }
    var notes by remember { mutableStateOf(grinder.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Grinder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name *") }, singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        // Skapa en kopia av det ursprungliga objektet med de nya värdena.
                        // Detta är viktigt eftersom det behåller det ursprungliga ID:t.
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

/**
 * Dialogruta för att bekräfta borttagning av en kvarn.
 */
@Composable
fun DeleteGrinderConfirmationDialog(
    grinderName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete grinder?") },
        // Varningstext om att kopplingen till befintliga bryggningar försvinner
        text = { Text("Are you sure you want to delete '$grinderName'? Brews that used this grinder will lose the connection.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                // Använd error-färg för att markera en destruktiv handling
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}