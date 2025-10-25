package com.victorkoffed.projektandroid.ui.screens.brew

// Import ALL from the db package for simplicity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewSetupState
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewViewModel

// Import the graph from its location (if it is in the brew folder)
// import com.victorkoffed.projektandroid.ui.screens.brew.BrewGraph // Används ej direkt här längre

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrewScreen(
    vm: BrewViewModel,
    completedBrewId: Long?, // ID för den bryggning vars resultat ska visas (behålls ifall du vill lägga till historik här senare)
    scaleConnectionState: BleConnectionState,
    onStartBrewClick: (setup: BrewSetupState) -> Unit,
    onSaveWithoutGraph: () -> Unit,
    onNavigateToScale: () -> Unit,
    onClearResult: () -> Unit, // Behålls om completedBrewId används
    onNavigateBack: () -> Unit
) {
    // Hämta listor och states från ViewModel
    val availableBeans by vm.availableBeans.collectAsState()
    val availableGrinders by vm.availableGrinders.collectAsState()
    val availableMethods by vm.availableMethods.collectAsState()
    val setupState = vm.brewSetupState
    // metrics och samples behövs inte längre här
    // val metrics by vm.completedBrewMetrics.collectAsState()
    // val samples by vm.completedBrewSamples.collectAsState()
    val hasPreviousBrews by vm.hasPreviousBrews.collectAsState()

    val scrollState = rememberScrollState()
    // REMOVED: val buttonColor = Color(0xFFDCC7AA) // Färgen från mockupen

    // --- NEW STATE FOR DIALOG ---
    var showConnectionAlert by remember { mutableStateOf(false) }
    // --- END NEW ---

    // Clears old results if we navigate here with an old ID
    // (kan tas bort om du inte vill visa någon form av resultat här alls)
    LaunchedEffect(completedBrewId) {
        if (completedBrewId != null) {
            vm.clearBrewResults() // Nollställ viewmodel state
            onClearResult() // Be MainActivity nollställa ID:t
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Brew") }, // Change title to something more appropriate
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Home")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- ONLY "BEFORE BREW" SECTION REMAINS ---
            Row( // Använd en Row för att placera titel och knapp
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween, // Distributes the space
                verticalAlignment = Alignment.CenterVertically // Centers vertically
            ) {
                // Remove the title "Before Brew", it is not needed when it is the only thing displayed
                // Text("Before Brew", style = MaterialTheme.typography.headlineSmall)

                // Keep the "Load latest" button, it is useful
                TextButton(
                    onClick = { vm.loadLatestBrewSettings() },
                    enabled = hasPreviousBrews // Aktivera bara om det finns bryggningar
                ) {
                    Icon(
                        Icons.Default.History, // Clock icon
                        contentDescription = null, // Descriptive text not necessary here
                        modifier = Modifier.size(ButtonDefaults.IconSize) // Standard icon size
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing)) // Standard spacing
                    Text("Load latest settings") // Clearer button text
                }
            }
            Spacer(Modifier.height(8.dp))

            // ... (DropdownSelectors och OutlinedTextFields för setup är oförändrade) ...
            DropdownSelector(
                label = "Bean *",
                options = availableBeans,
                selectedOption = setupState.selectedBean,
                onOptionSelected = { vm.selectBean(it) },
                optionToString = { it?.name ?: "Select bean..." }
            )
            OutlinedTextField(
                value = setupState.doseGrams,
                onValueChange = { vm.onDoseChange(it) },
                label = { Text("Dose (g) *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            DropdownSelector(
                label = "Grinder",
                options = availableGrinders,
                selectedOption = setupState.selectedGrinder,
                onOptionSelected = { vm.selectGrinder(it) },
                optionToString = { it?.name ?: "Select grinder..." }
            )
            OutlinedTextField(
                value = setupState.grindSetting,
                onValueChange = { vm.onGrindSettingChange(it) },
                label = { Text("Grind Setting") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = setupState.grindSpeedRpm,
                onValueChange = { vm.onGrindSpeedChange(it) },
                label = { Text("Grind Speed (RPM)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            DropdownSelector(
                label = "Method *",
                options = availableMethods,
                selectedOption = setupState.selectedMethod,
                onOptionSelected = { vm.selectMethod(it) },
                optionToString = { it?.name ?: "Select method..." }
            )
            OutlinedTextField(
                value = setupState.brewTempCelsius,
                onValueChange = { vm.onBrewTempChange(it) },
                label = { Text("Brew Temperature (°C)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            // --- UPDATED START BREW BUTTON ---
            Button(
                onClick = {
                    // Check connection status here
                    if (scaleConnectionState is BleConnectionState.Connected) {
                        onStartBrewClick(vm.getCurrentSetup())
                    } else {
                        // Show the dialog if the scale is not connected
                        showConnectionAlert = true
                    }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                enabled = vm.isSetupValid(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary // FIX: Use Theme Color
                )
            ) {
                Text("Start Live Brew") // Clearer button text
            }


            // --- ALERT DIALOG (Unchanged) ---
            if (showConnectionAlert) {
                AlertDialog(
                    onDismissRequest = { showConnectionAlert = false },
                    title = { Text("Scale Not Connected") },
                    text = { Text("The scale is not connected. How do you want to proceed?") },
                    confirmButton = {
                        TextButton(onClick = {
                            showConnectionAlert = false
                            onNavigateToScale()
                        }) {
                            Text("Connect Scale")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConnectionAlert = false }) {
                            Text("Cancel")
                        }
                        TextButton(onClick = {
                            showConnectionAlert = false
                            onSaveWithoutGraph()
                        }) {
                            Text("Save without Graph")
                        }
                    }
                )
            }
            // --- END ALERT DIALOG ---
        } // End of Column
    } // End of Scaffold
} // End of BrewScreen

// --- The ResultMetrics FUNCTION IS REMOVED ---

// DropdownSelector (Unchanged)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownSelector(
    label: String,
    options: List<T>,
    selectedOption: T?,
    onOptionSelected: (T?) -> Unit,
    optionToString: (T?) -> String
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = optionToString(selectedOption),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth() // menuAnchor() is important
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionToString(option)) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}