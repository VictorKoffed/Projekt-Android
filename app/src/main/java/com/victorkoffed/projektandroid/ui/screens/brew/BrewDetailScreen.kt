// app/src/main/java/com/victorkoffed/projektandroid/ui/screens/brew/BrewDetailScreen.kt
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
import androidx.compose.ui.graphics.toArgb
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
import com.victorkoffed.projektandroid.ui.screens.bean.ArchiveConfirmationDialog // Importera arkiveringsdialogen
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
    navBackStackEntry: NavBackStackEntry // Används för bild-URI och arkiveringsprompt
) {
    // State från ViewModel
    val state by viewModel.brewDetailState.collectAsState()
    // Deducerat state för att hantera UI-beteenden
    val isEditing by remember { derivedStateOf { viewModel.isEditing } }
    val showArchivePromptOnEntry by viewModel.showArchivePromptOnEntry.collectAsState() // Nytt state för prompt vid start
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

    // NYTT: Hämta bönan för prompten (behövs för namnet i dialogen)
    val beanForPrompt = state.bean

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
                    // Avbryt-knapp i redigeringsläge, annars tillbaka-knapp
                    IconButton(onClick = { if (isEditing) viewModel.cancelEditing() else onNavigateBack() }) {
                        Icon(
                            if (isEditing) Icons.Default.Cancel else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isEditing) "Cancel editing" else "Back"
                        )
                    }
                },
                actions = {
                    if (isEditing) {
                        // Spara-knapp i redigeringsläge
                        IconButton(
                            onClick = { viewModel.saveChanges() },
                            enabled = state.brew != null // Aktivera när data har laddats
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Save changes", tint = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        // Redigera- och ta bort-knappar i visningsläge
                        IconButton(onClick = { viewModel.startEditing() }, enabled = state.brew != null) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { showDeleteConfirmDialog = true }, enabled = state.brew != null) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Brew", tint = MaterialTheme.colorScheme.error) // Använd Error-färg
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface // Matcha ytfärgen
                )
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
                            .padding(horizontal = 16.dp) // Använd horisontell padding här
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Lägg till padding överst i kolumnen
                        Spacer(modifier = Modifier.height(16.dp))

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
                                        .clickable { onNavigateToCamera() }, // Navigera till kameran vid klick
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), // Mjukare färg
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text("Add Picture")
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
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Text("No ratio/water data available.", modifier = Modifier.padding(16.dp))
                        }

                        Text("Brew Progress", style = MaterialTheme.typography.titleMedium)

                        // Filterchips för att styra vilka linjer som visas i grafen
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = showWeightLine,
                                onClick = { showWeightLine = !showWeightLine },
                                label = { Text("Weight") },
                                leadingIcon = { if (showWeightLine) Icon(Icons.Default.Check, "Visible") else Icon(Icons.Default.Close, "Hidden") },
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
                                leadingIcon = { if (showFlowLine) Icon(Icons.Default.Check, "Visible") else Icon(Icons.Default.Close, "Hidden") },
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
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Text("No graph data saved for this brew.", modifier = Modifier.padding(16.dp))
                            }
                        }

                        Text("Notes", style = MaterialTheme.typography.titleMedium)

                        // Hantering av anteckningar
                        if (isEditing) {
                            // Fullständig redigering i edit mode
                            OutlinedTextField(
                                value = viewModel.editNotes,
                                onValueChange = { viewModel.onEditNotesChanged(it) },
                                label = { Text("Notes (Full Edit Mode)") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 100.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    // ... (resten av färgerna)
                                )
                            )
                        } else {
                            // Snabbredigering i visningsläge
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
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                        // ... (resten av färgerna)
                                    )
                                )
                                IconButton(
                                    onClick = { viewModel.saveQuickEditNotes() },
                                    enabled = hasUnsavedNotes, // Aktiv endast vid ändring
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
                        // Lägg till padding i botten
                        Spacer(modifier = Modifier.height(16.dp))

                    } // Slut Column

                    // Dialogruta för att bekräfta radering
                    if (showDeleteConfirmDialog) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirmDialog = false },
                            title = { Text("Delete brew?") },
                            text = {
                                Text("Are you sure you want to delete the brew for '${state.bean?.name ?: "this bean"}'? This action cannot be undone.")
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        viewModel.deleteCurrentBrew {
                                            showDeleteConfirmDialog = false
                                            onNavigateBack() // Gå tillbaka efter radering
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) // Error-färg för destruktiv handling
                                ) { Text("Delete") }
                            },
                            dismissButton = { TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancel") } }
                        )
                    }

                    // NYTT: Dialogruta för att bekräfta arkivering vid skärmstart
                    if (showArchivePromptOnEntry != null && beanForPrompt != null && showArchivePromptOnEntry == beanForPrompt.id) {
                        ArchiveConfirmationDialog(
                            beanName = beanForPrompt.name,
                            onConfirm = { viewModel.archiveBeanFromPrompt(showArchivePromptOnEntry!!) },
                            onDismiss = { viewModel.dismissArchivePromptOnEntry() }
                        )
                    }

                } else {
                    // Visas om bryggningen inte kunde laddas eller raderades
                    Box(Modifier.fillMaxSize().padding(paddingValues), Alignment.Center) {
                        Text(state.error ?: "Brew data unavailable.")
                    }
                }
            }
        } // Slut when
    } // Slut Scaffold
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
    val totalTimeMillis = state.samples.lastOrNull()?.timeMillis ?: 0L
    val minutes = (totalTimeMillis / 1000 / 60).toInt()
    val seconds = (totalTimeMillis / 1000 % 60).toInt()
    val timeString = remember(minutes, seconds) {
        String.format("%02d:%02d", minutes, seconds)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Details", style = MaterialTheme.typography.titleLarge)
            DetailRow("Bean:", state.bean?.name ?: "-")
            DetailRow("Roaster:", state.bean?.roaster ?: "-")
            // NYTT: Visa arkivstatus för bönan
            if (state.bean?.isArchived == true) {
                DetailRow("Bean Status:", "Archived")
            }
            DetailRow("Date:", state.brew?.startedAt?.let { dateFormat.format(it) } ?: "-")
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
    val grinder = viewModel.editSelectedGrinder
    val method = viewModel.editSelectedMethod

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Edit Details", style = MaterialTheme.typography.titleLarge)

            // Dropdown för att välja kvarn
            EditDropdownSelector(
                label = "Grinder",
                options = availableGrinders,
                selectedOption = grinder,
                onOptionSelected = { viewModel.onEditGrinderSelected(it) },
                optionToString = { it?.name ?: "Select grinder..." }
            )
            // Textfält för inställningar
            OutlinedTextField(
                value = viewModel.editGrindSetting,
                onValueChange = { viewModel.onEditGrindSettingChanged(it) },
                label = { Text("Grind Setting") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    // ... (resten av färgerna)
                )
            )
            OutlinedTextField(
                value = viewModel.editGrindSpeedRpm,
                onValueChange = { viewModel.onEditGrindSpeedRpmChanged(it) },
                label = { Text("Grind Speed (RPM)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    // ... (resten av färgerna)
                )
            )
            // Dropdown för att välja bryggmetod
            EditDropdownSelector(
                label = "Method",
                options = availableMethods,
                selectedOption = method,
                onOptionSelected = { viewModel.onEditMethodSelected(it) },
                optionToString = { it?.name ?: "Select method..." }
            )
            OutlinedTextField(
                value = viewModel.editBrewTempCelsius,
                onValueChange = { viewModel.onEditBrewTempChanged(it) },
                label = { Text("Water Temperature (°C)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    // ... (resten av färgerna)
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Ratio", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = metrics.ratio?.let { "1:%.1f".format(it) } ?: "-",
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Water", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = "%.1f g".format(metrics.waterUsedGrams),
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Dose", style = MaterialTheme.typography.labelMedium)
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
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb() // Använd tema-färg
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant // Färg för axellinjer

    // PathEffect för att rita prickade rutnätslinjer
    val gridLinePaint = remember {
        Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f))
    }
    // Paint-objekt för att rita numeriska etiketter på axlarna
    val numericLabelPaint = remember(textColor) { // Uppdatera om textColor ändras (tema)
        android.graphics.Paint().apply {
            color = textColor
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 10.sp.value * density.density
        }
    }
    val numericLabelPaintRight = remember(flowColor) { // Uppdatera om flowColor ändras
        android.graphics.Paint().apply {
            color = flowColor.toArgb() // Använd tema-färg
            textAlign = android.graphics.Paint.Align.LEFT
            textSize = 10.sp.value * density.density
        }
    }
    // Paint-objekt för axeltitlar
    val axisTitlePaint = remember(textColor) { // Uppdatera om textColor ändras
        android.graphics.Paint().apply {
            color = textColor
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 14.sp.value * density.density
            isFakeBoldText = true
        }
    }
    val axisTitlePaintFlow = remember(flowColor) { // Uppdatera om flowColor ändras
        android.graphics.Paint().apply {
            color = flowColor.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 14.sp.value * density.density
            isFakeBoldText = true
        }
    }

    // Kontrollera om det finns Flow-data att visa
    val hasFlowData = remember(samples) {
        samples.any { it.flowRateGramsPerSecond != null && it.flowRateGramsPerSecond > 0 }
    }

    Canvas(modifier = modifier.padding(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 32.dp)) {
        val xLabelPadding = 32.dp.toPx()
        val yLabelPaddingLeft = 32.dp.toPx()
        val yLabelPaddingRight = if (hasFlowData) 32.dp.toPx() else 0.dp.toPx() // Ingen höger-padding om ingen flödesaxel

        // Grafens rityta
        val graphStartX = yLabelPaddingLeft
        val graphEndX = size.width - yLabelPaddingRight
        val graphWidth = graphEndX - graphStartX
        val graphTopY = 0f
        val graphBottomY = size.height - xLabelPadding
        val graphHeight = graphBottomY - graphTopY


        if (graphWidth <= 0 || graphHeight <= 0) return@Canvas

        // Bestämma maximala värden för skalning (med lite marginal)
        val maxTime = max(60000f, samples.maxOfOrNull { it.timeMillis }?.toFloat() ?: 1f) * 1.05f
        val actualMaxMass = samples.maxOfOrNull { it.massGrams }?.toFloat() ?: 1f
        val maxMass = max(50f, ceil(actualMaxMass / 50f) * 50f) * 1.1f

        val maxFlowRaw = samples.maxOfOrNull { it.flowRateGramsPerSecond?.toFloat() ?: 0f } ?: 1f
        val roundedMaxFlow = max(5f, ceil(maxFlowRaw / 5f) * 5f)
        val maxFlow = roundedMaxFlow * 1.1f

        drawContext.canvas.nativeCanvas.apply {
            // Rita ut de horisontella rutnätslinjerna (Mass-axeln) och dess etiketter
            val massGridInterval = 50f
            var currentMassGrid = massGridInterval
            while (currentMassGrid < maxMass / 1.1f) {
                val y = graphBottomY - (currentMassGrid / maxMass) * graphHeight
                drawLine(gridLineColor, Offset(graphStartX, y), Offset(graphEndX, y),
                    strokeWidth = gridLinePaint.width, pathEffect = gridLinePaint.pathEffect)
                drawText("${currentMassGrid.toInt()}g", yLabelPaddingLeft / 2, y + numericLabelPaint.textSize / 3, numericLabelPaint)
                currentMassGrid += massGridInterval
            }

            // Rita etiketter för Flow-axeln (om data finns)
            if (hasFlowData && showFlowLine) { // Visa endast om linjen är aktiv
                val flowGridInterval = max(1f, ceil(roundedMaxFlow / 3f)) // Intervall baserat på max
                var currentFlowGrid = flowGridInterval
                while (currentFlowGrid < maxFlow / 1.1f) {
                    val y = graphBottomY - (currentFlowGrid / maxFlow) * graphHeight
                    // Rita flödes-etikett på höger sida
                    drawText(String.format("%.1f g/s", currentFlowGrid), size.width - yLabelPaddingRight / 2, y + numericLabelPaintRight.textSize / 3, numericLabelPaintRight)
                    currentFlowGrid += flowGridInterval
                }
            }

            // Rita de vertikala rutnätslinjerna (Tid-axeln) och dess etiketter
            val timeGridInterval = 30000f // 30 sekunder
            var currentTimeGrid = timeGridInterval
            while (currentTimeGrid < maxTime / 1.05f) {
                val x = graphStartX + (currentTimeGrid / maxTime) * graphWidth
                drawLine(gridLineColor, Offset(x, graphTopY), Offset(x, graphBottomY),
                    strokeWidth = gridLinePaint.width, pathEffect = gridLinePaint.pathEffect)
                val timeSec = (currentTimeGrid / 1000).toInt()
                // Rita tids-etikett under rutnätslinjen
                drawText("${timeSec}s", x, size.height, numericLabelPaint) // Rita på botten av Canvas
                currentTimeGrid += timeGridInterval
            }
        }

        // Rita axellinjer
        drawLine(axisColor, Offset(graphStartX, graphTopY), Offset(graphStartX, graphBottomY)) // Mass/Vikt-axel (Vänster Y)
        drawLine(axisColor, Offset(graphStartX, graphBottomY), Offset(graphEndX, graphBottomY)) // Tid-axel (Botten X)
        if (hasFlowData && showFlowLine) { // Rita höger Y-axel endast om flödeslinjen visas
            drawLine(axisColor, Offset(graphEndX, graphTopY), Offset(graphEndX, graphBottomY)) // Flöde-axel (Höger Y)
        }

        // Rita kurvorna för Mass och Flow
        if (samples.size > 1) {
            val massPath = Path()
            val flowPath = Path()
            var flowPathStarted = false // För att hantera luckor i flödesdata

            samples.forEachIndexed { index, s ->
                val x = graphStartX + (s.timeMillis.toFloat() / maxTime) * graphWidth
                val yMass = graphBottomY - (s.massGrams.toFloat() / maxMass) * graphHeight
                // Clamp-värden för att hålla linjerna inom grafområdet
                val cx = x.coerceIn(graphStartX, graphEndX)
                val cyMass = yMass.coerceIn(graphTopY, graphBottomY)

                // Bygg viktkurvan
                if (showWeightLine) {
                    if (index == 0) massPath.moveTo(cx, cyMass) else massPath.lineTo(cx, cyMass)
                }

                // Bygg flödeskurvan
                if (showFlowLine && hasFlowData && s.flowRateGramsPerSecond != null) {
                    val yFlow = graphBottomY - (s.flowRateGramsPerSecond.toFloat() / maxFlow) * graphHeight
                    val cyFlow = yFlow.coerceIn(graphTopY, graphBottomY)

                    if (!flowPathStarted) {
                        flowPath.moveTo(cx, cyFlow)
                        flowPathStarted = true
                    } else {
                        flowPath.lineTo(cx, cyFlow)
                    }
                } else {
                    // Återställ om punkten är null eller utanför, skapar en lucka i linjen
                    flowPathStarted = false
                }
            }
            // Rita de byggda kurvorna
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
            withSave { // Spara canvas state för rotation
                rotate(-90f) // Rotera -90 grader för Y-axlarnas titlar
                // Vikt-axelns titel (vänster)
                drawText(
                    "Weight (g)",
                    -(graphTopY + graphHeight / 2), // Centrera vertikalt efter rotation
                    yLabelPaddingLeft / 2 - axisTitlePaint.descent(), // Positionera horisontellt (blir vertikalt)
                    axisTitlePaint
                )
                // Flöde-axelns titel (höger, endast om relevant)
                if (hasFlowData && showFlowLine) {
                    drawText(
                        "Flow (g/s)",
                        -(graphTopY + graphHeight / 2), // Centrera vertikalt
                        size.width - yLabelPaddingRight / 2 - axisTitlePaintFlow.descent(), // Positionera på höger sida
                        axisTitlePaintFlow
                    )
                }
            } // Restore canvas state
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
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                // ... (resten av färgerna)
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
    val containerColor = Color.Black // Använd svart bakgrund
    val contentColor = Color.White // Vit färg för text/ikoner

    Scaffold(
        containerColor = containerColor,
        topBar = {
            TopAppBar(
                title = { Text("Brew Photo", color = contentColor) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = contentColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor)
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