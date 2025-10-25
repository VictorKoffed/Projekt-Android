package com.victorkoffed.projektandroid.ui.screens.brew

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
// import androidx.compose.ui.text.font.FontWeight // Behövs inte längre
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
// Importera ALLT från db-paketet för enkelhetens skull
import com.victorkoffed.projektandroid.domain.model.BleConnectionState // <-- KONTROLLERA DENNA IMPORT
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewSetupState
// Importera grafen från sin plats (om den ligger i brew-mappen)
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
    val buttonColor = Color(0xFFDCC7AA) // Färgen från mockupen

    // --- NYTT STATE FÖR DIALOG ---
    var showConnectionAlert by remember { mutableStateOf(false) }
    // --- SLUT NYTT ---

    // Rensar gamla resultat om vi navigerar hit med ett gammalt ID
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
                title = { Text("New Brew") }, // Byt titel till något mer passande
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
            // --- ENDAST "BEFORE BREW" SEKTION KVAR ---
            Row( // Använd en Row för att placera titel och knapp
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween, // Fördelar utrymmet
                verticalAlignment = Alignment.CenterVertically // Centrerar vertikalt
            ) {
                // Ta bort titeln "Before Brew", den behövs inte när det är det enda som visas
                // Text("Before Brew", style = MaterialTheme.typography.headlineSmall)

                // Behåll "Load latest"-knappen, den är användbar
                TextButton(
                    onClick = { vm.loadLatestBrewSettings() },
                    enabled = hasPreviousBrews // Aktivera bara om det finns bryggningar
                ) {
                    Icon(
                        Icons.Default.History, // Klock-ikon
                        contentDescription = null, // Beskrivande text inte nödvändig här
                        modifier = Modifier.size(ButtonDefaults.IconSize) // Standard ikonstorlek
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing)) // Standard avstånd
                    Text("Load latest settings") // Tydligare text
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

            // --- UPPDATERAD START BREW-KNAPP ---
            Button(
                onClick = {
                    // Kontrollera anslutningsstatus här
                    if (scaleConnectionState is BleConnectionState.Connected) {
                        onStartBrewClick(vm.getCurrentSetup())
                    } else {
                        // Visa dialogrutan om vågen inte är ansluten
                        showConnectionAlert = true
                    }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                enabled = vm.isSetupValid(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor
                )
            ) {
                Text("Start Live Brew") // Tydligare knapptext
            }


            // --- ALERT DIALOG (Oförändrad) ---
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
            // --- SLUT ALERT DIALOG ---
        } // Slut på Column
    } // Slut på Scaffold
} // Slut på BrewScreen

// --- ResultMetrics FUNKTIONEN ÄR BORTTAGEN ---

// DropdownSelector (Oförändrad)
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
            modifier = Modifier.menuAnchor().fillMaxWidth() // menuAnchor() är viktigt
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