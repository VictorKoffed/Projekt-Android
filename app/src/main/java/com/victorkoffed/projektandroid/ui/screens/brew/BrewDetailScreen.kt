package com.victorkoffed.projektandroid.ui.screens.brew

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.withSave
import androidx.navigation.NavBackStackEntry
import coil.compose.AsyncImage
import com.victorkoffed.projektandroid.ThemedSnackbar
import com.victorkoffed.projektandroid.data.db.BrewMetrics
import com.victorkoffed.projektandroid.data.db.BrewSample
import com.victorkoffed.projektandroid.data.db.Grinder
import com.victorkoffed.projektandroid.data.db.Method
import com.victorkoffed.projektandroid.ui.theme.PlaceholderDarkGray
import com.victorkoffed.projektandroid.ui.theme.PlaceholderGray
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewDetailState
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewDetailViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrewDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToImageFullscreen: (String) -> Unit,
    // ViewModel och NavBackStackEntry hanteras här för att fånga returvärden från kameran.
    viewModel: BrewDetailViewModel,
    navBackStackEntry: NavBackStackEntry
) {
    // State från ViewModel
    val state by viewModel.brewDetailState.collectAsState()
    // Deducerat state för att hantera UI-beteenden
    val isEditing by remember { derivedStateOf { viewModel.isEditing } }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Endast nödvändigt i redigeringsläge
    val availableGrinders by viewModel.availableGrinders.collectAsState()
    val availableMethods by viewModel.availableMethods.collectAsState()

    // Lokalt UI-state för grafens synlighet
    var showWeightLine by remember { mutableStateOf(true) }
    var showFlowLine by remember { mutableStateOf(true) }

    // Snackbar state och scope för att visa meddelanden (t.ex. vid fel)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Hantera returvärde (bild-URI) från CameraScreen via SavedStateHandle.
    val savedImageUri by navBackStackEntry.savedStateHandle
        .getLiveData<String>("captured_image_uri")
        .observeAsState()

    LaunchedEffect(savedImageUri) {
        if (savedImageUri != null) {
            viewModel.updateBrewImageUri(savedImageUri)
            // Rensa värdet direkt så att det inte återanvänds om skärmen återskapas
            navBackStackEntry.savedStateHandle.remove<String>("captured_image_uri")
        }
    }

    // Visa felmeddelanden från ViewModel i en Snackbar
    LaunchedEffect(state.error) {
        if (state.error != null) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = state.error!!,
                    duration = SnackbarDuration.Long
                )
            }
            // Nollställ felet i ViewModel efter visning
            viewModel.clearError()
        }
    }

    // Beräkna om snabbspara-knappen för anteckningar ska vara aktiv.
    val savedNotes = state.brew?.notes ?: ""
    val hasUnsavedNotes = viewModel.quickEditNotes.trim() != savedNotes.trim()


    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        // Visa antingen "Edit Brew" eller information om bryggningen
                        if (isEditing) "Edit Brew"
                        else state.brew?.let { "Brew: ${state.bean?.name ?: "Unknown Bean"}" } ?: "Loading..."
                    )
                },
                navigationIcon = {
                    if (isEditing) {
                        // Knapp för att avbryta redigering
                        IconButton(onClick = { viewModel.cancelEditing() }) {
                            Icon(Icons.Default.Cancel, contentDescription = "Cancel editing")
                        }
                    } else {
                        // Knapp för att navigera tillbaka
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (isEditing) {
                        // Spara-knapp i redigeringsläge
                        IconButton(
                            onClick = { viewModel.saveChanges() },
                            // Notera: saveChanges i VM måste implementeras
                            enabled = state.brew != null
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Save changes", tint = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        // Redigera- och ta bort-knappar i visningsläge
                        IconButton(onClick = { viewModel.startEditing() }, enabled = state.brew != null) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { showDeleteConfirmDialog = true }, enabled = state.brew != null) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Brew", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        },
        // Använder en anpassad Snackbar-komponent för tematisk design
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { snackbarData ->
                    ThemedSnackbar(snackbarData)
                }
            )
        }
    ) { paddingValues ->
        when {
            state.isLoading && !isEditing -> {
                // Visa laddningsindikator när data hämtas
                Box(Modifier.fillMaxSize().padding(paddingValues), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                val currentBrew = state.brew
                if (currentBrew != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // --- Bild/placeholder ---
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            if (currentBrew.imageUri != null) {
                                // Visa bilden med Coil
                                AsyncImage(
                                    model = currentBrew.imageUri,
                                    contentDescription = "Brew photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(250.dp)
                                        // Gå till helskärm om vi inte redigerar
                                        .clickable {
                                            if (!isEditing) {
                                                onNavigateToImageFullscreen(currentBrew.imageUri)
                                            }
                                        }
                                )

                                // Ta bort-knapp när vi redigerar
                                if (isEditing) {
                                    IconButton(
                                        onClick = { viewModel.updateBrewImageUri(null) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            // Lägg till bakgrund för att synas bättre mot bilden
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                                    ) {
                                        Icon(Icons.Default.DeleteForever, contentDescription = "Delete Picture")
                                    }
                                }
                            } else {
                                // Visa en klickbar "Add Picture"-placeholder
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .clickable { onNavigateToCamera() },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = PlaceholderGray,
                                        contentColor = PlaceholderDarkGray
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                Icons.Default.PhotoCamera,
                                                contentDescription = "Add Picture",
                                                tint = PlaceholderDarkGray
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text("Add Picture", color = PlaceholderDarkGray)
                                        }
                                    }
                                }
                            }
                        }
                        // --- Slut bild/placeholder ---

                        // Växla mellan sammanfattning och redigeringskort
                        if (isEditing) {
                            BrewEditCard(
                                viewModel = viewModel,
                                availableGrinders = availableGrinders,
                                availableMethods = availableMethods
                            )
                        } else {
                            BrewSummaryCard(state = state)
                        }

                        // Visa metrik-kortet om data finns
                        state.metrics?.let { metrics ->
                            BrewMetricsCard(metrics = metrics)
                        } ?: Card(
                            modifier = Modifier.fillMaxWidth(),
                            // FIX: Använd tematisk ytfärg istället för hårdkodad vit
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Text("No ratio/water data.", modifier = Modifier.padding(16.dp))
                        }

                        Text("Brew Progress", style = MaterialTheme.typography.titleMedium)

                        // Filterchips för att styra vilka linjer som visas i grafen
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = showWeightLine,
                                onClick = { showWeightLine = !showWeightLine },
                                label = { Text("Weight") },
                                leadingIcon = { if (showWeightLine) Icon(Icons.Default.Check, "Visas") else Icon(Icons.Default.Close, "Dold") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onTertiary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onTertiary
                                )
                            )
                            FilterChip(
                                selected = showFlowLine,
                                onClick = { showFlowLine = !showFlowLine },
                                label = { Text("Flow") },
                                leadingIcon = { if (showFlowLine) Icon(Icons.Default.Check, "Visas") else Icon(Icons.Default.Close, "Dold") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondary
                                )
                            )
                        }

                        // Visa grafen om det finns samples
                        if (state.samples.isNotEmpty()) {
                            BrewSamplesGraph(
                                samples = state.samples,
                                showWeightLine = showWeightLine,
                                showFlowLine = showFlowLine,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                            )
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                // FIX: Använd tematisk ytfärg istället för hårdkodad vit
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Text("No graph data saved.", modifier = Modifier.padding(16.dp))
                            }
                        }

                        Text("Notes", style = MaterialTheme.typography.titleMedium)

                        // Hantering av anteckningar: Fullständig redigering i edit mode, annars snabb-redigering.
                        if (isEditing) {
                            // FULL EDIT MODE: Textfältet uppdaterar ViewModelns 'editNotes'
                            OutlinedTextField(
                                value = viewModel.editNotes, // <-- Fält som måste finnas i ViewModel
                                onValueChange = { viewModel.onEditNotesChanged(it) }, // <-- Funktion som måste finnas i ViewModel
                                label = { Text("Notes (Full Edit Mode)") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 100.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    // FIX: Använd tematisk ytfärg istället för hårdkodad vit
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                                    errorContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        } else {
                            // QUICK EDIT MODE: Möjliggör snabb redigering
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = viewModel.quickEditNotes,
                                    onValueChange = { viewModel.onQuickEditNotesChanged(it) },
                                    label = { Text("Notes") },
                                    enabled = true,
                                    readOnly = false,
                                    modifier = Modifier.weight(1f).heightIn(min = 100.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        // FIX: Använd tematisk ytfärg istället för hårdkodad vit
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                        disabledContainerColor = MaterialTheme.colorScheme.surface,
                                        errorContainerColor = MaterialTheme.colorScheme.surface,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                        cursorColor = MaterialTheme.colorScheme.primary,
                                        focusedLabelColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                // Spara-knapp som endast är aktiv vid osparade ändringar
                                IconButton(
                                    onClick = { viewModel.saveQuickEditNotes() },
                                    enabled = hasUnsavedNotes,
                                    modifier = Modifier.align(Alignment.Top).offset(y = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Save,
                                        contentDescription = "Save notes",
                                        tint = if (hasUnsavedNotes) MaterialTheme.colorScheme.primary else Color.Gray
                                    )
                                }
                            }
                        }
                    }

                    // Dialogruta för att bekräfta radering
                    state.brew
                    if (showDeleteConfirmDialog) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirmDialog = false },
                            title = { Text("Delete brew?") },
                            text = {
                                Text("Are you sure you want to delete the brew for '${state.bean?.name ?: "this bean"}'?")
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        // Radera bryggningen och navigera sedan tillbaka
                                        viewModel.deleteCurrentBrew {
                                            showDeleteConfirmDialog = false
                                            onNavigateBack()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) { Text("Delete", color = Color.Black) }
                            },
                            dismissButton = { TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancel") } }
                        )
                    }
                } else {
                    // Detta bör endast visas kortvarigt under laddning
                    Box(Modifier.fillMaxSize().padding(paddingValues), Alignment.Center) {
                        Text("Brew data became unavailable.")
                    }
                }
            }
        }
    }
}


// --- DetailRow ---
@Composable
fun DetailRow(label: String, value: String) {
    // Enkel återanvändbar rad för att visa en etikett och ett värde
    Row(verticalAlignment = Alignment.Top) {
        Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(100.dp))
        Text(value)
    }
}

// ---------- Summary & rows ----------
@SuppressLint("DefaultLocale")
@Composable
fun BrewSummaryCard(state: BrewDetailState) {
    // Visa en sammanfattning av bryggningsdetaljerna
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    // FIX: Använd timeMillis för att hitta total tid
    val totalTimeMillis = state.samples.lastOrNull()?.timeMillis ?: 0L
    val minutes = (totalTimeMillis / 1000 / 60).toInt()
    val seconds = (totalTimeMillis / 1000 % 60).toInt()
    val timeString = remember(minutes, seconds) {
        String.format("%02d:%02d", minutes, seconds)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        // FIX: Använd tematisk ytfärg istället för hårdkodad vit
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Details", style = MaterialTheme.typography.titleLarge)
            // Visar alla detaljer i DetailRow-format
            DetailRow("Bean:", state.bean?.name ?: "-")
            DetailRow("Roaster:", state.bean?.roaster ?: "-")
            DetailRow("Date:", state.brew?.startedAt?.let { dateFormat.format(it) } ?: "-") // FIX: Använd startedAt
            DetailRow("Total time:", if (totalTimeMillis > 0) timeString else "-")
            DetailRow("Dose:", state.brew?.doseGrams?.let { "%.1f g".format(it) } ?: "-")
            DetailRow("Method:", state.method?.name ?: "-")
            DetailRow("Grinder:", state.grinder?.name ?: "-")
            DetailRow("Grind set:", state.brew?.grindSetting ?: "-")
            DetailRow("Grind speed:", state.brew?.grindSpeedRpm?.let { "%.0f RPM".format(it) } ?: "-")
            DetailRow("Temp:", state.brew?.brewTempCelsius?.let { "%.1f °C".format(it) } ?: "-")
        }
    }
}

// ---------- Edit card ----------
@Composable
fun BrewEditCard(
    viewModel: BrewDetailViewModel,
    availableGrinders: List<Grinder>,
    availableMethods: List<Method>
) {
    // Redigeringskort för bryggningsdetaljer. Använder separata `edit*` state-variabler i ViewModel.
    // FIX: Hämta state från ViewModel
    val grinder = viewModel.editSelectedGrinder // <-- Måste skapas i VM
    val method = viewModel.editSelectedMethod   // <-- Måste skapas i VM

    Card(
        modifier = Modifier.fillMaxWidth(),
        // FIX: Använd tematisk ytfärg istället för hårdkodad vit
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Edit Details", style = MaterialTheme.typography.titleLarge)
            // ... (Resten av InfoRows har tagits bort här för enkelhetens skull, men de finns i den version som orsakade felet)

            // Dropdown för att välja kvarn
            EditDropdownSelector(
                label = "Grinder",
                options = availableGrinders,
                selectedOption = grinder, // FIX: Använd det nya VM-fältet
                onOptionSelected = { viewModel.onEditGrinderSelected(it) }, // FIX: Använd den nya VM-funktionen
                optionToString = { it?.name ?: "Select grinder..." }
            )
            // Textfält för inställningar
            OutlinedTextField(
                value = viewModel.editGrindSetting, // <-- Måste skapas i VM
                onValueChange = { viewModel.onEditGrindSettingChanged(it) }, // <-- Måste skapas i VM
                label = { Text("Grind Setting") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    errorContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor = MaterialTheme.colorScheme.primary
                )
            )
            OutlinedTextField(
                value = viewModel.editGrindSpeedRpm, // <-- Måste skapas i VM
                onValueChange = { viewModel.onEditGrindSpeedRpmChanged(it) }, // <-- Måste skapas i VM
                label = { Text("Grind Speed (RPM)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    errorContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor = MaterialTheme.colorScheme.primary
                )
            )
            // Dropdown för att välja bryggmetod
            EditDropdownSelector(
                label = "Method",
                options = availableMethods,
                selectedOption = method, // FIX: Använd det nya VM-fältet
                onOptionSelected = { viewModel.onEditMethodSelected(it) }, // FIX: Använd den nya VM-funktionen
                optionToString = { it?.name ?: "Select method..." }
            )
            OutlinedTextField(
                value = viewModel.editBrewTempCelsius, // <-- Måste skapas i VM
                onValueChange = { viewModel.onEditBrewTempChanged(it) }, // <-- Måste skapas i VM
                label = { Text("Water Temperature (°C)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    errorContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

// ---------- Reusable UI ----------
@Composable
fun BrewMetricsCard(metrics: BrewMetrics) {
    // Visar de beräknade nyckeltalen för bryggningen (förhållande, vatten, dos)
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        // FIX: Använd tematisk ytfärg istället för hårdkodad vit
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Ratio", style = MaterialTheme.typography.labelMedium)
                // FIX: Använd metrics.ratio
                Text(
                    text = metrics.ratio?.let { "1:%.1f".format(it) } ?: "-",
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Water", style = MaterialTheme.typography.labelMedium)
                // FIX: Använd metrics.waterUsedGrams
                Text(
                    text = "%.1f g".format(metrics.waterUsedGrams),
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Dose", style = MaterialTheme.typography.labelMedium)
                // FIX: Använd metrics.doseGrams
                Text(
                    text = "%.1f g".format(metrics.doseGrams),
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// --- Graf ---
@Composable
fun BrewSamplesGraph(
    samples: List<BrewSample>,
    showWeightLine: Boolean,
    showFlowLine: Boolean,
    modifier: Modifier = Modifier
) {
    // Komponent för att rita ut grafen baserat på BrewSample-data
    val density = LocalDensity.current

    val massColor = MaterialTheme.colorScheme.tertiary
    val flowColor = MaterialTheme.colorScheme.secondary
    val gridLineColor = Color.LightGray
    // PathEffect för att rita prickade rutnätslinjer
    val gridLinePaint = remember {
        Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f))
    }
    // Paint-objekt för att rita numeriska etiketter på axlarna (används med Canvas.nativeCanvas)
    val numericLabelPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.DKGRAY
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 10.sp.value * density.density
        }
    }
    val numericLabelPaintRight = remember {
        android.graphics.Paint().apply {
            color = flowColor.hashCode()
            textAlign = android.graphics.Paint.Align.LEFT
            textSize = 10.sp.value * density.density
        }
    }
    // Paint-objekt för axeltitlar
    val axisTitlePaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 14.sp.value * density.density
            isFakeBoldText = true
        }
    }
    val axisTitlePaintFlow = remember {
        android.graphics.Paint().apply {
            color = flowColor.hashCode()
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 14.sp.value * density.density
            isFakeBoldText = true
        }
    }

    // Kontrollera om det finns Flow-data att visa
    val hasFlowData = remember(samples) {
        // FIX: Använd flowRateGramsPerSecond
        samples.any { it.flowRateGramsPerSecond != null && it.flowRateGramsPerSecond > 0 }
    }

    Canvas(modifier = modifier.padding(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 32.dp)) {
        val xLabelPadding = 32.dp.toPx()
        val yLabelPaddingLeft = 32.dp.toPx()
        val yLabelPaddingRight = 32.dp.toPx()

        // Grafens rityta
        val graphStartX = yLabelPaddingLeft
        val graphEndX = size.width - yLabelPaddingRight
        val graphWidth = graphEndX - graphStartX
        val graphHeight = size.height - xLabelPadding

        if (graphWidth <= 0 || graphHeight <= 0) return@Canvas

        // Bestämma maximala värden för skalning (med lite marginal)
        // FIX: Använd timeMillis
        val maxTime = max(60000f, samples.maxOfOrNull { it.timeMillis }?.toFloat() ?: 1f) * 1.05f
        // FIX: Använd massGrams
        val actualMaxMass = samples.maxOfOrNull { it.massGrams }?.toFloat() ?: 1f
        val maxMass = max(50f, ceil(actualMaxMass / 50f) * 50f) * 1.1f

        val maxFlowRaw = samples.maxOfOrNull { it.flowRateGramsPerSecond?.toFloat() ?: 0f } ?: 1f
        // Runda upp maxFlöde till närmaste 5, plus marginal
        val roundedMaxFlow = max(5f, ceil(maxFlowRaw / 5f) * 5f)
        val maxFlow = roundedMaxFlow * 1.1f

        val xAxisY = size.height - xLabelPadding

        drawContext.canvas.nativeCanvas.apply {
            // Rita ut de horisontella rutnätslinjerna (Mass-axeln) och dess etiketter
            val massGridInterval = 50f
            var currentMassGrid = massGridInterval
            while (currentMassGrid < maxMass / 1.1f) {
                val y = xAxisY - (currentMassGrid / maxMass) * graphHeight
                drawLine(gridLineColor, Offset(graphStartX, y), Offset(graphEndX, y),
                    strokeWidth = gridLinePaint.width, pathEffect = gridLinePaint.pathEffect)
                // Rita viktetikett vid sidan av rutnätslinjen
                drawText("${currentMassGrid.toInt()}g", yLabelPaddingLeft / 2, y + numericLabelPaint.textSize / 3, numericLabelPaint)
                currentMassGrid += massGridInterval
            }

            // Rita etiketter för Flow-axeln (om data finns)
            if (hasFlowData) {
                val flowGridInterval = max(1f, ceil(roundedMaxFlow / 3f))
                var currentFlowGrid = flowGridInterval
                while (currentFlowGrid < maxFlow / 1.1f) {
                    val y = xAxisY - (currentFlowGrid / maxFlow) * graphHeight
                    // Rita flödes-etikett
                    drawText(String.format("%.1f g/s", currentFlowGrid), size.width - yLabelPaddingRight / 2, y + numericLabelPaintRight.textSize / 3, numericLabelPaintRight)
                    currentFlowGrid += flowGridInterval
                }
            }

            // Rita de vertikala rutnätslinjerna (Tid-axeln) och dess etiketter
            val timeGridInterval = 30000f // 30 sekunder
            var currentTimeGrid = timeGridInterval
            while (currentTimeGrid < maxTime / 1.05f) {
                val x = graphStartX + (currentTimeGrid / maxTime) * graphWidth
                drawLine(gridLineColor, Offset(x, 0f), Offset(x, xAxisY),
                    strokeWidth = gridLinePaint.width, pathEffect = gridLinePaint.pathEffect)
                val timeSec = (currentTimeGrid / 1000).toInt()
                // Rita tids-etikett under rutnätslinjen
                drawText("${timeSec}s", x, size.height, numericLabelPaint)
                currentTimeGrid += timeGridInterval
            }
        }

        // Rita axellinjer (tjockare linjer)
        drawLine(massColor, Offset(graphStartX, 0f), Offset(graphStartX, xAxisY)) // Mass/Vikt-axel
        drawLine(Color.Gray, Offset(graphStartX, xAxisY), Offset(graphEndX, xAxisY)) // Tid-axel
        if (hasFlowData) {
            drawLine(flowColor, Offset(graphEndX, 0f), Offset(graphEndX, xAxisY)) // Flöde-axel
        }

        // Rita kurvorna för Mass och Flow
        if (samples.size > 1) {
            val massPath = Path()
            val flowPath = Path()
            var flowPathStarted = false
            samples.forEachIndexed { index, s ->
                val x = graphStartX + (s.timeMillis.toFloat() / maxTime) * graphWidth
                // FIX: Använd massGrams
                val yMass = xAxisY - (s.massGrams.toFloat() / maxMass) * graphHeight
                val cx = x.coerceIn(graphStartX, graphEndX)
                val cyMass = yMass.coerceIn(0f, xAxisY)
                // Rita viktkurvan
                if (showWeightLine) {
                    if (index == 0) massPath.moveTo(cx, cyMass) else massPath.lineTo(cx, cyMass)
                }

                // Rita flödeskurvan, men bara om datan är inom det synliga intervallet
                // FIX: Använd flowRateGramsPerSecond
                if (showFlowLine && hasFlowData && s.flowRateGramsPerSecond != null) {
                    val yFlow = xAxisY - (s.flowRateGramsPerSecond.toFloat() / maxFlow) * graphHeight
                    val cyFlow = yFlow.coerceIn(0f, xAxisY)

                    if (!flowPathStarted) {
                        flowPath.moveTo(cx, cyFlow)
                        flowPathStarted = true
                    } else {
                        flowPath.lineTo(cx, cyFlow)
                    }
                } else {
                    // Återställ om punkten är utanför intervallet (skapar en paus i linjen)
                    flowPathStarted = false
                }
            }
            if (showWeightLine) {
                drawPath(path = massPath, color = massColor, style = Stroke(width = 2.dp.toPx()))
            }
            if (showFlowLine && hasFlowData) {
                drawPath(path = flowPath, color = flowColor, style = Stroke(width = 2.dp.toPx()))
            }
        }

        // Rita axeltitlarna
        drawContext.canvas.nativeCanvas.apply {
            // Tid-axelns titel (centrerad nedtill)
            drawText("Time", graphStartX + graphWidth / 2, size.height - xLabelPadding / 2 + axisTitlePaint.textSize / 3, axisTitlePaint)
            withSave {
                rotate(-90f)
                // Vikt-axelns titel (vänster)
                drawText(
                    "Weight (g)",
                    -(0f + graphHeight / 2),
                    yLabelPaddingLeft / 2 - axisTitlePaint.descent(),
                    axisTitlePaint
                )
                if (hasFlowData) {
                    // Flöde-axelns titel (höger)
                    drawText(
                        "Flow (g/s)",
                        -(0f + graphHeight / 2),
                        size.width - yLabelPaddingRight / 2 - axisTitlePaint.descent(),
                        axisTitlePaintFlow
                    )
                }
            }
        }
    }
}


// --- Dropdown ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> EditDropdownSelector(
    label: String,
    options: List<T>,
    selectedOption: T?,
    onOptionSelected: (T?) -> Unit,
    optionToString: (T?) -> String
) {
    // Återanvändbar dropdown-komponent för att välja objekt (kvarnar/metoder)
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
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                // FIX: Använd tematisk ytfärg
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                errorContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // Alternativ för att inte välja något
            DropdownMenuItem(
                text = { Text("No selection") },
                onClick = {
                    onOptionSelected(null)
                    expanded = false
                }
            )
            // Alternativ för de tillgängliga objekten
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

// --- Helskärmsbild ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenImageScreen(
    uri: String,
    onNavigateBack: () -> Unit
) {
    // Skärm för att visa bryggningsbilden i helskärm
    val darkContainerColor = MaterialTheme.colorScheme.tertiary
    val onDarkColor = MaterialTheme.colorScheme.onTertiary

    Scaffold(
        containerColor = darkContainerColor,
        topBar = {
            TopAppBar(
                title = { Text("Brew Photo", color = onDarkColor) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = onDarkColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = darkContainerColor)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = uri,
                contentDescription = "Fullscreen brew photo",
                contentScale = ContentScale.Fit, // Använder Fit för att säkerställa att hela bilden syns
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}