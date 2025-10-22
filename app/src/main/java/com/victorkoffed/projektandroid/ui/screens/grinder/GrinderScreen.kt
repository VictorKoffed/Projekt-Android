package com.victorkoffed.projektandroid.ui.screens.grinder

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
import com.victorkoffed.projektandroid.data.db.Grinder
import com.victorkoffed.projektandroid.ui.viewmodel.grinder.GrinderViewModel

/**
 * En skärm för att visa, lägga till och ta bort kvarnar (Grinders).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrinderScreen(vm: GrinderViewModel) {
    // Hämta listan på kvarnar från ViewModel
    val grinders by vm.allGrinders.collectAsState()

    // State för textfälten
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Hantera kvarnar", style = MaterialTheme.typography.headlineSmall)

        // Formulär för att lägga till ny kvarn
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Kvarn-namn (t.ex. DF83)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Anteckningar (t.ex. SSP HU-malskivor)") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    vm.addGrinder(name, notes)
                    // Rensa fälten
                    name = ""
                    notes = ""
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Lägg till")
            }
        }

        HorizontalDivider()

        // Lista över befintliga kvarnar
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (grinders.isEmpty()) {
                item {
                    Text(
                        "Inga kvarnar tillagda.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items(grinders) { grinder ->
                    GrinderCard(
                        grinder = grinder,
                        onDelete = { vm.deleteGrinder(grinder) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GrinderCard(grinder: Grinder, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(grinder.name, style = MaterialTheme.typography.titleMedium)
                if (grinder.notes != null) {
                    Text(grinder.notes, style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Ta bort kvarn",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
