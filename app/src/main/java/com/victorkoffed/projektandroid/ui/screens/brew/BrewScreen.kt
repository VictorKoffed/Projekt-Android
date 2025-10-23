package com.victorkoffed.projektandroid.ui.screens.brew

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
// Importera ALLT från db-paketet för enkelhetens skull
import com.victorkoffed.projektandroid.data.db.*
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewSetupState
// Importera grafen från sin plats (om den ligger i brew-mappen)
import com.victorkoffed.projektandroid.ui.screens.brew.BrewGraph

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrewScreen(
    vm: BrewViewModel,
    completedBrewId: Long?, // ID för den bryggning vars resultat ska visas
    onStartBrewClick: (setup: BrewSetupState) -> Unit,
    onClearResult: () -> Unit // Callback för att nollställa completedBrewId
) {
    // Hämta listor och states från ViewModel
    val availableBeans by vm.availableBeans.collectAsState()
    val availableGrinders by vm.availableGrinders.collectAsState()
    val availableMethods by vm.availableMethods.collectAsState()
    val setupState = vm.brewSetupState
    val metrics by vm.completedBrewMetrics.collectAsState() // Resultat (ratio, etc.)
    val samples by vm.completedBrewSamples.collectAsState() // Resultat (grafdata)

    val scrollState = rememberScrollState()

    // Ladda resultat när completedBrewId ändras (och inte är null)
    LaunchedEffect(completedBrewId) {
        vm.loadBrewResults(completedBrewId)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Bryggning") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- "BEFORE BREW" SEKTION ---
            // Visas bara om vi INTE tittar på ett resultat
            if (metrics == null) {
                Text("Innan bryggning", style = MaterialTheme.typography.headlineSmall)

                DropdownSelector(
                    label = "Böna *",
                    options = availableBeans,
                    selectedOption = setupState.selectedBean,
                    onOptionSelected = { vm.selectBean(it) },
                    optionToString = { it?.name ?: "Välj böna..." }
                )
                OutlinedTextField(
                    value = setupState.doseGrams,
                    onValueChange = { vm.onDoseChange(it) },
                    label = { Text("Dos (g) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), // Korrekt typ
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                DropdownSelector(
                    label = "Kvarn",
                    options = availableGrinders,
                    selectedOption = setupState.selectedGrinder,
                    onOptionSelected = { vm.selectGrinder(it) },
                    optionToString = { it?.name ?: "Välj kvarn..." }
                )
                OutlinedTextField(
                    value = setupState.grindSetting,
                    onValueChange = { vm.onGrindSettingChange(it) },
                    label = { Text("Malningsgrad") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = setupState.grindSpeedRpm,
                    onValueChange = { vm.onGrindSpeedChange(it) },
                    label = { Text("Malningshastighet (RPM)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                DropdownSelector(
                    label = "Metod *",
                    options = availableMethods,
                    selectedOption = setupState.selectedMethod,
                    onOptionSelected = { vm.selectMethod(it) },
                    optionToString = { it?.name ?: "Välj metod..." }
                )
                OutlinedTextField(
                    value = setupState.brewTempCelsius,
                    onValueChange = { vm.onBrewTempChange(it) },
                    label = { Text("Vattentemperatur (°C)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), // Korrekt typ
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                // Knapp för att starta inspelning
                Button(
                    onClick = {
                        onStartBrewClick(vm.getCurrentSetup())
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    enabled = vm.isSetupValid()
                ) {
                    Text("Starta bryggning")
                }
            }

            // --- "AFTER BREW" SEKTION (VISAS BARA OM RESULTAT FINNS) ---
            if (metrics != null && samples.isNotEmpty()) {
                Text("Efter bryggning", style = MaterialTheme.typography.headlineSmall)

                // Visa Ratio och Vattenmängd
                ResultMetrics(metrics = metrics!!) // !! är ok här pga if-satsen

                Spacer(Modifier.height(16.dp))

                // Visa den sparade grafen
                Text("Bryggförlopp", style = MaterialTheme.typography.titleMedium)
                // Återanvänd BrewGraph från LiveBrewScreen (se till att den är public/internal)
                BrewGraph(
                    samples = samples,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(vertical = 8.dp)
                )

                Spacer(Modifier.height(16.dp))

                // Knapp för att rensa resultat och börja ny setup
                OutlinedButton(
                    onClick = {
                        vm.clearBrewResults() // Rensa ViewModel state
                        onClearResult() // Rensa ID i MainActivity
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Ny bryggning")
                }
            }
        }
    }
}

// Composable för att visa resultatmätvärden (Ratio, Vatten, Dos)
@Composable
fun ResultMetrics(metrics: BrewMetrics) { // Använder BrewMetrics korrekt
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Ratio", style = MaterialTheme.typography.labelMedium)
                Text(
                    // Använder metrics.ratio korrekt
                    text = metrics.ratio?.let { "1:%.1f".format(it) } ?: "-",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Vatten", style = MaterialTheme.typography.labelMedium)
                Text(
                    // Använder metrics.waterUsedGrams korrekt
                    text = "%.1f g".format(metrics.waterUsedGrams),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Dos", style = MaterialTheme.typography.labelMedium)
                Text(
                    // Använder metrics.doseGrams korrekt
                    text = "%.1f g".format(metrics.doseGrams),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


// DropdownSelector (Den korrekta definitionen)
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

