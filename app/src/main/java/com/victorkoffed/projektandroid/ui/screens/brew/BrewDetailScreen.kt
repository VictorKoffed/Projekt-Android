package com.victorkoffed.projektandroid.ui.screens.brew

// Core
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
// Material 3
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check // <-- NY IMPORT
import androidx.compose.material.icons.filled.Close // <-- NY IMPORT
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
// UI helpers
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
// Data
import com.victorkoffed.projektandroid.CoffeeJournalApplication
import com.victorkoffed.projektandroid.data.db.*
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewDetailState
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewDetailViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewDetailViewModelFactory
// Util
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrewDetailScreen(
    brewId: Long,
    onNavigateBack: () -> Unit
) {
    val application = LocalContext.current.applicationContext as CoffeeJournalApplication
    val repository = application.coffeeRepository

    val viewModel: BrewDetailViewModel = viewModel(
        key = brewId.toString(),
        factory = BrewDetailViewModelFactory(repository, brewId)
    )

    val state by viewModel.brewDetailState.collectAsState()
    val isEditing by remember { derivedStateOf { viewModel.isEditing } }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Only needed while editing
    val availableGrinders by viewModel.availableGrinders.collectAsState()
    val availableMethods by viewModel.availableMethods.collectAsState()

    // --- NYTT STATE FÖR TOGGLES ---
    var showWeightLine by remember { mutableStateOf(true) }
    var showFlowLine by remember { mutableStateOf(true) }
    // --- SLUT PÅ NYTT STATE ---

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditing) "Redigera bryggning"
                        else state.brew?.let { "Bryggning: ${state.bean?.name ?: "Okänd Böna"}" } ?: "Laddar..."
                    )
                },
                navigationIcon = {
                    if (isEditing) {
                        IconButton(onClick = { viewModel.cancelEditing() }) {
                            Icon(Icons.Default.Cancel, contentDescription = "Avbryt redigering")
                        }
                    } else {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Tillbaka")
                        }
                    }
                },
                actions = {
                    if (isEditing) {
                        IconButton(onClick = { viewModel.saveChanges() }) {
                            Icon(Icons.Default.Save, contentDescription = "Spara ändringar")
                        }
                    } else {
                        IconButton(onClick = { viewModel.startEditing() }, enabled = state.brew != null) {
                            Icon(Icons.Default.Edit, contentDescription = "Redigera")
                        }
                        IconButton(onClick = { showDeleteConfirmDialog = true }, enabled = state.brew != null) {
                            Icon(Icons.Default.Delete, contentDescription = "Ta bort", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            state.isLoading && !isEditing -> {
                Box(Modifier.fillMaxSize().padding(paddingValues), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                Box(Modifier.fillMaxSize().padding(paddingValues).padding(16.dp), Alignment.Center) {
                    Text("Fel: ${state.error}", color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {
                // Make brew non-null within this block
                state.brew?.let { currentBrew ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (isEditing) {
                            BrewEditCard(
                                viewModel = viewModel,
                                availableGrinders = availableGrinders,
                                availableMethods = availableMethods
                            )
                        } else {
                            BrewSummaryCard(state = state)
                        }

                        state.metrics?.let { metrics ->
                            BrewMetricsCard(metrics = metrics)
                        } ?: Card(Modifier.fillMaxWidth()) {
                            Text("Ingen ratio/vatten-data.", modifier = Modifier.padding(16.dp))
                        }

                        Text("Bryggförlopp", style = MaterialTheme.typography.titleMedium)

                        // --- NY KOD (TOGGLES) ---
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = showWeightLine,
                                onClick = { showWeightLine = !showWeightLine },
                                label = { Text("Vikt") },
                                leadingIcon = {
                                    if (showWeightLine) Icon(Icons.Default.Check, "Visas") else Icon(Icons.Default.Close, "Dold")
                                }
                            )
                            FilterChip(
                                selected = showFlowLine,
                                onClick = { showFlowLine = !showFlowLine },
                                label = { Text("Flöde") },
                                leadingIcon = {
                                    if (showFlowLine) Icon(Icons.Default.Check, "Visas") else Icon(Icons.Default.Close, "Dold")
                                }
                            )
                        }
                        // --- SLUT PÅ NY KOD ---

                        if (state.samples.isNotEmpty()) {
                            BrewSamplesGraph(
                                samples = state.samples,
                                // --- NYA PARAMETRAR ---
                                showWeightLine = showWeightLine,
                                showFlowLine = showFlowLine,
                                // --- SLUT ---
                                modifier = Modifier.fillMaxWidth().height(300.dp)
                            )
                        } else {
                            Card(Modifier.fillMaxWidth()) {
                                Text("Ingen grafdata sparad.", modifier = Modifier.padding(16.dp))
                            }
                        }

                        Text("Noteringar", style = MaterialTheme.typography.titleMedium)
                        if (isEditing) {
                            OutlinedTextField(
                                value = viewModel.editNotes,
                                onValueChange = { viewModel.onEditNotesChanged(it) },
                                label = { Text("Noteringar") },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp)
                            )
                        } else {
                            currentBrew.notes?.let { Text(it) } ?: Text("-")
                        }
                    }
                }
            }
        }

        // Delete confirm dialog (must pass all params)
        val brewToDelete = state.brew
        if (showDeleteConfirmDialog && brewToDelete != null) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text("Ta bort bryggning?") },
                text = {
                    Text("Är du säker på att du vill ta bort bryggningen för '${state.bean?.name ?: "denna böna"}'?")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteCurrentBrew {
                                showDeleteConfirmDialog = false
                                onNavigateBack()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Ta bort") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Avbryt") }
                }
            )
        }
    }
}

// ---------- Summary & rows ----------
@Composable
fun BrewSummaryCard(state: BrewDetailState) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    // --- Beräkna total tid ---
    val totalTimeMillis = state.samples.lastOrNull()?.timeMillis ?: 0L
    val minutes = (totalTimeMillis / 1000 / 60).toInt()
    val seconds = (totalTimeMillis / 1000 % 60).toInt()
    // Formatera till "MM:SS"
    val timeString = remember(minutes, seconds) {
        String.format("%02d:%02d", minutes, seconds)
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Detaljer", style = MaterialTheme.typography.titleLarge)
            DetailRow("Böna:", state.bean?.name ?: "-")
            DetailRow("Rosteri:", state.bean?.roaster ?: "-")
            DetailRow("Datum:", state.brew?.startedAt?.let { dateFormat.format(it) } ?: "-")
            DetailRow("Total tid:", if (totalTimeMillis > 0) timeString else "-")
            DetailRow("Dos:", state.brew?.doseGrams?.let { "%.1f g".format(it) } ?: "-")
            DetailRow("Metod:", state.method?.name ?: "-")
            DetailRow("Kvarn:", state.grinder?.name ?: "-")
            DetailRow("Maln.grad:", state.brew?.grindSetting ?: "-")
            DetailRow("Temp:", state.brew?.brewTempCelsius?.let { "%.1f °C".format(it) } ?: "-")
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(100.dp))
        Text(value)
    }
}

// ---------- Edit card ----------
@Composable
fun BrewEditCard(
    viewModel: BrewDetailViewModel,
    availableGrinders: List<Grinder>,
    availableMethods: List<Method>
) {
    val state = viewModel.brewDetailState.value
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Redigera detaljer", style = MaterialTheme.typography.titleLarge)

            DetailRow("Böna:", state.bean?.name ?: "-")
            DetailRow("Datum:", state.brew?.startedAt?.let { dateFormat.format(it) } ?: "-")
            DetailRow("Dos:", state.brew?.doseGrams?.let { "%.1f g".format(it) } ?: "-")

            Divider(Modifier.padding(vertical = 8.dp))

            EditDropdownSelector(
                label = "Kvarn",
                options = availableGrinders,
                selectedOption = viewModel.editSelectedGrinder,
                onOptionSelected = { viewModel.onEditGrinderSelected(it) },
                optionToString = { it?.name ?: "Välj kvarn..." }
            )
            OutlinedTextField(
                value = viewModel.editGrindSetting,
                onValueChange = { viewModel.onEditGrindSettingChanged(it) },
                label = { Text("Malningsgrad") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = viewModel.editGrindSpeedRpm,
                onValueChange = { viewModel.onEditGrindSpeedRpmChanged(it) },
                label = { Text("Malningshastighet (RPM)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            EditDropdownSelector(
                label = "Metod",
                options = availableMethods,
                selectedOption = viewModel.editSelectedMethod,
                onOptionSelected = { viewModel.onEditMethodSelected(it) },
                optionToString = { it?.name ?: "Välj metod..." }
            )
            OutlinedTextField(
                value = viewModel.editBrewTempCelsius,
                onValueChange = { viewModel.onEditBrewTempChanged(it) },
                label = { Text("Vattentemperatur (°C)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ---------- Reusable UI ----------
@Composable
fun BrewMetricsCard(metrics: BrewMetrics) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
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
                Text("Vatten", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = "%.1f g".format(metrics.waterUsedGrams),
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Dos", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = "%.1f g".format(metrics.doseGrams),
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * UPPDATERAD GRAF:
 * Visar nu både Massa (Vänster Y-axel, Svart) och Flöde (Höger Y-axel, Blå).
 * Tar emot showWeightLine och showFlowLine för att växla synlighet.
 * Försöker minska röran genom att inte rita linjesegment för noll-flöde.
 */
@Composable
fun BrewSamplesGraph(
    samples: List<BrewSample>,
    showWeightLine: Boolean, // <-- NY PARAMETER
    showFlowLine: Boolean,   // <-- NY PARAMETER
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // --- Färger och Penslar (inga ändringar här) ---
    val massColor = Color.Black
    val flowColor = Color(0xFF007BFF) // En tydlig blå färg
    val gridLineColor = Color.LightGray
    val gridLinePaint = remember {
        Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f))
    }
    val textPaintLeft = remember { /* ... som tidigare ... */
        android.graphics.Paint().apply {
            color = android.graphics.Color.DKGRAY
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 10.sp.value * density.density
        }
    }
    val textPaintRight = remember { /* ... som tidigare ... */
        android.graphics.Paint().apply {
            color = flowColor.hashCode() // Använd flödets färg
            textAlign = android.graphics.Paint.Align.LEFT // Justera vänster för höger axel
            textSize = 10.sp.value * density.density
        }
    }
    val axisLabelPaint = remember { /* ... som tidigare ... */
        android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 14.sp.value * density.density
            isFakeBoldText = true
        }
    }
    val axisLabelPaintFlow = remember { /* ... som tidigare ... */
        android.graphics.Paint().apply {
            color = flowColor.hashCode()
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 14.sp.value * density.density
            isFakeBoldText = true
        }
    }

    // --- Kontrollera om vi har flödesdata (inga ändringar här) ---
    val hasFlowData = remember(samples) {
        samples.any { it.flowRateGramsPerSecond != null && it.flowRateGramsPerSecond!! > 0 }
    }

    Canvas(modifier = modifier.padding(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 32.dp)) {
        // --- Utrymme för axlar (inga ändringar här) ---
        val xLabelPadding = 24.dp.toPx()
        val yLabelPaddingLeft = 24.dp.toPx()
        val yLabelPaddingRight = 24.dp.toPx()
        val graphWidth = size.width - yLabelPaddingLeft - yLabelPaddingRight
        val graphHeight = size.height - xLabelPadding
        val yAxisLeftX = yLabelPaddingLeft
        val yAxisRightX = size.width - yLabelPaddingRight
        val xAxisY = size.height - xLabelPadding

        // --- Skalning (inga ändringar här) ---
        val maxTime = max(60000f, samples.maxOfOrNull { it.timeMillis }?.toFloat() ?: 1f) * 1.1f
        val actualMaxMass = samples.maxOfOrNull { it.massGrams }?.toFloat() ?: 1f
        val maxMass = max(50f, ceil(actualMaxMass / 50f) * 50f) * 1.1f
        val actualMaxFlow = samples.maxOfOrNull { it.flowRateGramsPerSecond ?: 0.0 }?.toFloat() ?: 1f
        val maxFlow = max(5f, ceil(actualMaxFlow / 2f) * 2f) * 1.1f

        // --- Rutnät och Etiketter (inga ändringar här) ---
        drawContext.canvas.nativeCanvas.apply {
            // 1. Vänster Y-axel (Vikt)
            val massGridInterval = 50f
            var currentMassGrid = massGridInterval
            while (currentMassGrid < maxMass / 1.1f) {
                val y = xAxisY - (currentMassGrid / maxMass) * graphHeight
                drawLine(gridLineColor, Offset(yAxisLeftX, y), Offset(yAxisRightX, y),
                    strokeWidth = gridLinePaint.width, pathEffect = gridLinePaint.pathEffect)
                drawText("${currentMassGrid.toInt()}g", yLabelPaddingLeft / 2, y + textPaintLeft.textSize / 3, textPaintLeft)
                currentMassGrid += massGridInterval
            }

            // 3. X-axel (Tid)
            val timeGridInterval = 30000f
            var currentTimeGrid = timeGridInterval
            while (currentTimeGrid < maxTime / 1.05f) {
                val x = yAxisLeftX + (currentTimeGrid / maxTime) * graphWidth
                drawLine(gridLineColor, Offset(x, 0f), Offset(x, xAxisY),
                    strokeWidth = gridLinePaint.width, pathEffect = gridLinePaint.pathEffect)
                val timeSec = (currentTimeGrid / 1000).toInt()
                drawText("${timeSec}s", x, size.height, textPaintLeft)
                currentTimeGrid += timeGridInterval
            }
            // 4. Axeltitlar
            drawText("Tid", yAxisLeftX + graphWidth / 2, size.height + xLabelPadding / 1.5f, axisLabelPaint)
            save(); rotate(-90f)
            drawText("Vikt (g)", -size.height / 2, yLabelPaddingLeft / 2 - axisLabelPaint.descent(), axisLabelPaint)
            if (hasFlowData) {
                drawText("Flöde (g/s)", -size.height / 2, yAxisRightX + yLabelPaddingRight / 1.5f, axisLabelPaintFlow)
            }
            restore()
        }

        // --- Axlar (inga ändringar här) ---
        drawLine(massColor, Offset(yAxisLeftX, 0f), Offset(yAxisLeftX, xAxisY)) // Vänster Y
        drawLine(Color.Gray, Offset(yAxisLeftX, xAxisY), Offset(yAxisRightX, xAxisY)) // X
        if (hasFlowData) {
            drawLine(flowColor, Offset(yAxisRightX, 0f), Offset(yAxisRightX, xAxisY)) // Höger Y
        }

        // --- Linjer ---
        if (samples.size > 1) {
            val massPath = Path()
            val flowPath = Path()
            var flowPathStarted = false // Flagga för att hantera null-värden i början

            samples.forEachIndexed { index, s ->
                val x = yAxisLeftX + (s.timeMillis.toFloat() / maxTime) * graphWidth
                val yMass = xAxisY - (s.massGrams.toFloat() / maxMass) * graphHeight
                val cx = x.coerceIn(yAxisLeftX, yAxisRightX)
                val cyMass = yMass.coerceIn(0f, xAxisY)

                // Alltid lägg till punkt för massPath om den ska visas
                if (showWeightLine) {
                    if (index == 0) massPath.moveTo(cx, cyMass) else massPath.lineTo(cx, cyMass)
                }

                // --- JUSTERAD LOGIK FÖR FLÖDE ---
                // Lägg bara till punkt för flowPath om flödet ska visas, finns, OCH är positivt
                if (showFlowLine && hasFlowData && s.flowRateGramsPerSecond != null && s.flowRateGramsPerSecond > 0.0) {
                    val yFlow = xAxisY - (s.flowRateGramsPerSecond.toFloat() / maxFlow) * graphHeight
                    val cyFlow = yFlow.coerceIn(0f, xAxisY)
                    if (!flowPathStarted) {
                        flowPath.moveTo(cx, cyFlow) // Starta en ny linje här
                        flowPathStarted = true
                    } else {
                        flowPath.lineTo(cx, cyFlow) // Fortsätt nuvarande linje
                    }
                } else {
                    // Om flödet är null eller 0, återställ flaggan.
                    // Nästa gång vi får ett positivt flöde kommer det starta en ny linje ('moveTo').
                    flowPathStarted = false
                }
                // --- SLUT PÅ JUSTERING ---
            }
            // Rita Vikt-linjen (om vald)
            if (showWeightLine) {
                drawPath(path = massPath, color = massColor, style = Stroke(width = 2.dp.toPx()))
            }
            // Rita Flöde-linjen (om vald och data finns)
            if (showFlowLine && hasFlowData) {
                drawPath(path = flowPath, color = flowColor, style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
}


/** Renamed to avoid overload ambiguity with a similarly named dropdown elsewhere. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> EditDropdownSelector(
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
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Inget val") },
                onClick = {
                    onOptionSelected(null)
                    expanded = false
                }
            )
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