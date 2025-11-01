package com.victorkoffed.projektandroid.ui.screens.brew

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
import androidx.compose.material3.ExposedDropdownMenuAnchorType
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.scale.ScaleViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrewScreen(
    onStartBrewClick: () -> Unit,
    onSaveWithoutGraph: (newBrewId: Long?) -> Unit,
    onNavigateToScale: () -> Unit,
    onNavigateBack: () -> Unit,
    vm: BrewViewModel, // <-- INGEN default hiltViewModel() HÄR
    scaleVm: ScaleViewModel
) {
    val availableBeans by vm.availableBeans.collectAsState()
    val availableGrinders by vm.availableGrinders.collectAsState()
    val availableMethods by vm.availableMethods.collectAsState()

    // KORRIGERING: Använd 'val setupState by vm.brewSetupState.collectAsState()'
    // Detta hämtar det oföränderliga BrewSetupState-objektet
    val setupState by vm.brewSetupState.collectAsState()

    val hasPreviousBrews by vm.hasPreviousBrews.collectAsState()
    val scaleConnectionState by scaleVm.connectionState.collectAsState(
        initial = scaleVm.connectionState.replayCache.lastOrNull() ?: BleConnectionState.Disconnected
    )

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var showConnectionAlert by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.clearBrewResults()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Brew") },
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
                .padding(paddingValues),
        ) {
            // --- Scrollbart Innehåll (Formuläret) ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Kontrollrad med funktionen "Ladda senaste inställningar"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { vm.loadLatestBrewSettings() },
                        enabled = hasPreviousBrews
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Load latest settings")
                    }
                }
                Spacer(Modifier.height(8.dp))

                // --- Inställningsformulär ---

                // Dropdown för val av kaffeböna (obligatorisk, markerad med *)
                DropdownSelector(
                    label = "Bean *",
                    options = availableBeans,
                    selectedOption = setupState.selectedBean,
                    onOptionSelected = { vm.selectBean(it) },
                    optionToString = { it?.name ?: "Select bean..." }
                )

                // Textfält för dos (obligatorisk)
                OutlinedTextField(
                    // Använd setupState.doseGrams.value
                    value = setupState.doseGrams.value,
                    onValueChange = { vm.onDoseChange(it) },
                    label = { Text("Dose (g) *") },
                    isError = setupState.doseGrams.error != null,
                    supportingText = {
                        // Använd setupState.doseGrams.error
                        if (setupState.doseGrams.error != null) {
                            Text(text = setupState.doseGrams.error!!)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // Dropdown för kvarn
                DropdownSelector(
                    label = "Grinder",
                    options = availableGrinders,
                    selectedOption = setupState.selectedGrinder,
                    onOptionSelected = { vm.selectGrinder(it) },
                    optionToString = { it?.name ?: "Select grinder..." }
                )

                // Textfält för malningsinställning
                OutlinedTextField(
                    value = setupState.grindSetting,
                    onValueChange = { vm.onGrindSettingChange(it) },
                    label = { Text("Grind Setting") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Textfält för kvarnhastighet
                OutlinedTextField(
                    // Använd setupState.grindSpeedRpm.value
                    value = setupState.grindSpeedRpm.value,
                    onValueChange = { vm.onGrindSpeedChange(it) },
                    label = { Text("Grind Speed (RPM)") },
                    isError = setupState.grindSpeedRpm.error != null,
                    supportingText = {
                        // Använd setupState.grindSpeedRpm.error
                        if (setupState.grindSpeedRpm.error != null) {
                            Text(text = setupState.grindSpeedRpm.error!!)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // Dropdown för bryggmetod (obligatorisk)
                DropdownSelector(
                    label = "Method *",
                    options = availableMethods,
                    selectedOption = setupState.selectedMethod,
                    onOptionSelected = { vm.selectMethod(it) },
                    optionToString = { it?.name ?: "Select method..." }
                )

                // Textfält för vattentemperatur
                OutlinedTextField(
                    // Använd setupState.brewTempCelsius.value
                    value = setupState.brewTempCelsius.value,
                    onValueChange = { vm.onBrewTempChange(it) },
                    label = { Text("Brew Temperature (°C)") },
                    isError = setupState.brewTempCelsius.error != null,
                    supportingText = {
                        // Använd setupState.brewTempCelsius.error
                        if (setupState.brewTempCelsius.error != null) {
                            Text(text = setupState.brewTempCelsius.error!!)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                // Extra padding i slutet av scrollbart innehåll
                Spacer(Modifier.height(16.dp))
            }

            // --- Fixerad Knapp (Alltid synlig) ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        if (scaleConnectionState is BleConnectionState.Connected) {
                            vm.clearBrewResults()
                            onStartBrewClick()
                        } else {
                            showConnectionAlert = true
                        }
                    },
                    enabled = vm.isSetupValid(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Start Live Brew")
                }
            }
        }
    }

    // --- Alert Dialog vid frånkopplad våg ---
    if (showConnectionAlert) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Scale Not Connected") },
            text = { Text("The scale is not connected. How do you want to proceed?") },
            confirmButton = {
                // Alternativ 1: Navigera till anslutningsskärmen
                TextButton(onClick = {
                    onNavigateToScale()
                }) {
                    Text("Connect Scale")
                }
            },
            dismissButton = {
                // Alternativ 2: Avbryt
                TextButton(onClick = { }) {
                    Text("Cancel")
                }
                // Alternativ 3: Fortsätt och spara utan realtidsdata
                TextButton(onClick = {
                    scope.launch {
                        val newBrewId = vm.saveBrewWithoutSamples()
                        onSaveWithoutGraph(newBrewId)
                    }
                }) {
                    Text("Save without Graph")
                }
            }
        )
    }
}

// --- DropdownSelector (Återanvändbar komponent) ---
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
            modifier = Modifier
                .exposedDropdownSize(true)
                .menuAnchor(
                    ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = true
                )
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // Lägg till "None" för valfria fält
            if (label != "Bean *" && label != "Method *") {
                DropdownMenuItem(
                    text = { Text("None") },
                    onClick = {
                        onOptionSelected(null)
                        expanded = false
                    }
                )
            }
            // Itererar över tillgängliga objekt för att skapa menyval
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