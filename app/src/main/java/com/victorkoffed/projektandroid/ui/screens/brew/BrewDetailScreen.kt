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
                        if (state.samples.isNotEmpty()) {
                            BrewSamplesGraph(
                                samples = state.samples,
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
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Detaljer", style = MaterialTheme.typography.titleLarge)
            DetailRow("Böna:", state.bean?.name ?: "-")
            DetailRow("Rosteri:", state.bean?.roaster ?: "-")
            DetailRow("Datum:", state.brew?.startedAt?.let { dateFormat.format(it) } ?: "-")
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

@Composable
fun BrewSamplesGraph(samples: List<BrewSample>, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val textPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.DKGRAY
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 10.sp.value * density.density
        }
    }
    val axisLabelPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 14.sp.value * density.density
            isFakeBoldText = true
        }
    }
    val gridLinePaint = remember {
        Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f))
    }
    val gridLineColor = Color.LightGray

    Canvas(modifier = modifier.padding(start = 32.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)) {
        val xLabelPadding = 24.dp.toPx()
        val yLabelPadding = 24.dp.toPx()
        val graphWidth = size.width - yLabelPadding
        val graphHeight = size.height - xLabelPadding
        val maxTime = max(60000f, samples.maxOfOrNull { it.timeMillis }?.toFloat() ?: 1f) * 1.05f
        val actualMaxMass = samples.maxOfOrNull { it.massGrams }?.toFloat() ?: 1f
        val maxMass = max(50f, ceil(actualMaxMass / 10f) * 10f) * 1.1f
        val xAxisY = size.height - xLabelPadding
        val yAxisX = yLabelPadding

        // grid + labels
        drawContext.canvas.nativeCanvas.apply {
            val massGridInterval = 10f
            var currentMassGrid = massGridInterval
            while (currentMassGrid < maxMass / 1.1f) {
                val y = xAxisY - (currentMassGrid / maxMass) * graphHeight
                drawLine(gridLineColor, Offset(yAxisX, y), Offset(size.width, y),
                    strokeWidth = gridLinePaint.width, pathEffect = gridLinePaint.pathEffect)
                drawText("${currentMassGrid.toInt()}g", yLabelPadding / 2, y + textPaint.textSize / 3, textPaint)
                currentMassGrid += massGridInterval
            }
            val timeGridInterval = 30000f
            var currentTimeGrid = timeGridInterval
            while (currentTimeGrid < maxTime / 1.05f) {
                val x = yAxisX + (currentTimeGrid / maxTime) * graphWidth
                drawLine(gridLineColor, Offset(x, 0f), Offset(x, xAxisY),
                    strokeWidth = gridLinePaint.width, pathEffect = gridLinePaint.pathEffect)
                val timeSec = (currentTimeGrid / 1000).toInt()
                drawText("${timeSec}s", x, size.height, textPaint)
                currentTimeGrid += timeGridInterval
            }
            drawText("Tid", yAxisX + graphWidth / 2, size.height + xLabelPadding / 1.5f, axisLabelPaint)
            save(); rotate(-90f)
            drawText("Vikt", -size.height / 2, yLabelPadding / 2 - axisLabelPaint.descent(), axisLabelPaint)
            restore()
        }

        // axes
        drawLine(Color.Gray, Offset(yAxisX, 0f), Offset(yAxisX, xAxisY))
        drawLine(Color.Gray, Offset(yAxisX, xAxisY), Offset(size.width, xAxisY))

        // line
        if (samples.size > 1) {
            val path = Path()
            samples.forEachIndexed { index, s ->
                val x = yAxisX + (s.timeMillis.toFloat() / maxTime) * graphWidth
                val y = xAxisY - (s.massGrams.toFloat() / maxMass) * graphHeight
                val cx = x.coerceIn(yAxisX, size.width)
                val cy = y.coerceIn(0f, xAxisY)
                if (index == 0) path.moveTo(cx, cy) else path.lineTo(cx, cy)
            }
            drawPath(path = path, color = Color.Black, style = Stroke(width = 2.dp.toPx()))
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
