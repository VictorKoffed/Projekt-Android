package com.victorkoffed.projektandroid.ui.screens.brew

// Core
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
// Material 3
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
// UI helpers
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
import androidx.compose.runtime.collectAsState
// Bild
import coil.compose.AsyncImage
// --- NYA IMPORTER ---
import androidx.compose.runtime.livedata.observeAsState
import androidx.navigation.NavBackStackEntry
// --- SLUT NYA IMPORTER ---

// --- NYA IMPORTER FÖR SNACKBAR ---
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
// --- SLUT NYA IMPORTER ---


// Lokala färger för denna skärm
private val Accent = Color(0xFFDCC7AA)
private val CardGray = Color(0xFFF0F0F0)

// Specifikt för "Add Picture"-ytan (grå stil)
private val AddPicBgGray = Color(0xFFE7E7E7)   // ljusgrå bakgrund
private val AddPicFgGray = Color(0xFF606060)   // mörkgrå ikon/text

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrewDetailScreen(
    brewId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToImageFullscreen: (String) -> Unit,

    // --- NYA PARAMETRAR (från NavHost) ---
    viewModel: BrewDetailViewModel,
    navBackStackEntry: NavBackStackEntry
    // --- SLUT NYA PARAMETRAR ---
) {
    // --- VM-initiering är bortflyttad till NavHost ---
    // val application = LocalContext.current.applicationContext as CoffeeJournalApplication
    // val repository = application.coffeeRepository
    // val viewModel: BrewDetailViewModel = viewModel( ... )

    val state by viewModel.brewDetailState.collectAsState()
    val isEditing by remember { derivedStateOf { viewModel.isEditing } }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Only needed while editing
    val availableGrinders by viewModel.availableGrinders.collectAsState()
    val availableMethods by viewModel.availableMethods.collectAsState()

    var showWeightLine by remember { mutableStateOf(true) }
    var showFlowLine by remember { mutableStateOf(true) }

    // --- NYTT: Snackbar state och scope ---
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // --- SLUT NYTT ---

    // --- NYTT: Hantera returvärde från CameraScreen ---
    val savedImageUri by navBackStackEntry.savedStateHandle
        .getLiveData<String>("captured_image_uri")
        .observeAsState()

    LaunchedEffect(savedImageUri) {
        if (savedImageUri != null) {
            viewModel.updateBrewImageUri(savedImageUri)
            // Rensa värdet så det inte återanvänds
            navBackStackEntry.savedStateHandle.remove<String>("captured_image_uri")
        }
    }
    // --- SLUT NYTT ---

    // --- NYTT: LaunchedEffect för att visa fel ---
    LaunchedEffect(state.error) {
        if (state.error != null) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = state.error!!,
                    duration = SnackbarDuration.Long
                )
            }
            // Nollställ felet i ViewModel
            viewModel.clearError()
        }
    }
    // --- SLUT NYTT ---

    Scaffold(
        containerColor = CardGray, // grå bakgrund mellan korten
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditing) "Edit Brew"
                        else state.brew?.let { "Brew: ${state.bean?.name ?: "Unknown Bean"}" } ?: "Loading..."
                    )
                },
                navigationIcon = {
                    if (isEditing) {
                        IconButton(onClick = { viewModel.cancelEditing() }) {
                            Icon(Icons.Default.Cancel, contentDescription = "Cancel editing")
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
                            Icon(Icons.Default.Save, contentDescription = "Save changes", tint = Accent)
                        }
                    } else {
                        IconButton(onClick = { viewModel.startEditing() }, enabled = state.brew != null) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { showDeleteConfirmDialog = true }, enabled = state.brew != null) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Brew", tint = Accent)
                        }
                    }
                }
            )
        },
        // --- NYTT: Lägg till snackbarHost ---
        snackbarHost = { SnackbarHost(snackbarHostState) }
        // --- SLUT NYTT ---
    ) { paddingValues ->
        when {
            state.isLoading && !isEditing -> {
                Box(Modifier.fillMaxSize().padding(paddingValues), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            // --- BORTTAGEN: Denna gren hanteras nu av LaunchedEffect ---
            /*
            state.error != null -> {
                Box(Modifier.fillMaxSize().padding(paddingValues).padding(16.dp), Alignment.Center) {
                    Text("Error: ${state.error}", color = MaterialTheme.colorScheme.error)
                }
            }
            */
            // --- SLUT BORTTAGEN ---
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
                                // Visa bilden
                                AsyncImage(
                                    model = currentBrew.imageUri,
                                    contentDescription = "Brew photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(250.dp)
                                        .clickable {
                                            if (!isEditing) {
                                                onNavigateToImageFullscreen(currentBrew.imageUri!!)
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
                                // GRÅ "Add Picture"-yta (oavsett om vi redigerar eller ej)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .clickable { onNavigateToCamera() },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = AddPicBgGray,
                                        contentColor = AddPicFgGray
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
                                                tint = AddPicFgGray
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text("Add Picture", color = AddPicFgGray)
                                        }
                                    }
                                }
                            }
                        }
                        // --- Slut bild/placeholder ---

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
                        } ?: Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Text("No ratio/water data.", modifier = Modifier.padding(16.dp))
                        }

                        Text("Brew Progress", style = MaterialTheme.typography.titleMedium)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = showWeightLine,
                                onClick = { showWeightLine = !showWeightLine },
                                label = { Text("Vikt") },
                                leadingIcon = {
                                    if (showWeightLine) Icon(Icons.Default.Check, "Visas") else Icon(Icons.Default.Close, "Dold")
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Accent,
                                    selectedLabelColor = Color.Black,
                                    selectedLeadingIconColor = Color.Black
                                )
                            )
                            FilterChip(
                                selected = showFlowLine,
                                onClick = { showFlowLine = !showFlowLine },
                                label = { Text("Flow") },
                                leadingIcon = {
                                    if (showFlowLine) Icon(Icons.Default.Check, "Visas") else Icon(Icons.Default.Close, "Dold")
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Accent,
                                    selectedLabelColor = Color.Black,
                                    selectedLeadingIconColor = Color.Black
                                )
                            )
                        }

                        if (state.samples.isNotEmpty()) {
                            BrewSamplesGraph(
                                samples = state.samples,
                                showWeightLine = showWeightLine,
                                showFlowLine = showFlowLine,
                                modifier = Modifier.fillMaxWidth().height(300.dp)
                            )
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Text("No graph data saved.", modifier = Modifier.padding(16.dp))
                            }
                        }

                        Text("Notes", style = MaterialTheme.typography.titleMedium)
                        if (isEditing) {
                            OutlinedTextField(
                                value = viewModel.editNotes,
                                onValueChange = { viewModel.onEditNotesChanged(it) },
                                label = { Text("Noteringar") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 100.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White,
                                    disabledContainerColor = Color.White,
                                    errorContainerColor = Color.White,
                                    focusedBorderColor = Accent,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    cursorColor = Accent,
                                    focusedLabelColor = Accent
                                )
                            )
                        } else {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Text(
                                    currentBrew.notes ?: "-",
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Denna text visas nu bara om state.error är null men state.brew ändå blev null,
                    // vilket inte borde hända om ViewModelns logik är korrekt.
                    Text("Brew data became unavailable.")
                }
            }
        }

        // Delete confirm dialog
        val brewToDelete = state.brew
        if (showDeleteConfirmDialog && brewToDelete != null) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text("Delete brew?") },
                text = {
                    Text("Are you sure you want to delete the brew for '${state.bean?.name ?: "this bean"}'?")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteCurrentBrew {
                                showDeleteConfirmDialog = false
                                onNavigateBack()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) { Text("Ta bort", color = Color.Black) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}


// --- Resten av Composable-funktionerna (DetailRow, BrewSummaryCard, BrewEditCard, BrewMetricsCard, BrewSamplesGraph, EditDropdownSelector, FullscreenImageScreen) är oförändrade ---


// --- DetailRow ---
@Composable
fun DetailRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(100.dp))
        Text(value)
    }
}

// ---------- Summary & rows ----------
@Composable
fun BrewSummaryCard(state: BrewDetailState) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val totalTimeMillis = state.samples.lastOrNull()?.timeMillis ?: 0L
    val minutes = (totalTimeMillis / 1000 / 60).toInt()
    val seconds = (totalTimeMillis / 1000 % 60).toInt()
    val timeString = remember(minutes, seconds) {
        String.format("%02d:%02d", minutes, seconds)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Details", style = MaterialTheme.typography.titleLarge)
            DetailRow("Bean:", state.bean?.name ?: "-")
            DetailRow("Roaster:", state.bean?.roaster ?: "-")
            DetailRow("Date:", state.brew?.startedAt?.let { dateFormat.format(it) } ?: "-")
            DetailRow("Total time:", if (totalTimeMillis > 0) timeString else "-")
            DetailRow("Dose:", state.brew?.doseGrams?.let { "%.1f g".format(it) } ?: "-")
            DetailRow("Method:", state.method?.name ?: "-")
            DetailRow("Grinder:", state.grinder?.name ?: "-")
            DetailRow("Grind set:", state.brew?.grindSetting ?: "-")
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
    val state = viewModel.brewDetailState.collectAsState().value
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Edit Details", style = MaterialTheme.typography.titleLarge)
            DetailRow("Bean:", state.bean?.name ?: "-")
            DetailRow("Date:", state.brew?.startedAt?.let { dateFormat.format(it) } ?: "-")
            DetailRow("Dose:", state.brew?.doseGrams?.let { "%.1f g".format(it) } ?: "-")
            Divider(Modifier.padding(vertical = 8.dp))
            EditDropdownSelector(
                label = "Grinder",
                options = availableGrinders,
                selectedOption = viewModel.editSelectedGrinder,
                onOptionSelected = { viewModel.onEditGrinderSelected(it) },
                optionToString = { it?.name ?: "Select grinder..." }
            )
            OutlinedTextField(
                value = viewModel.editGrindSetting,
                onValueChange = { viewModel.onEditGrindSettingChanged(it) },
                label = { Text("Grind Setting") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = viewModel.editGrindSpeedRpm,
                onValueChange = { viewModel.onEditGrindSpeedRpmChanged(it) },
                label = { Text("Grind Speed (RPM)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            EditDropdownSelector(
                label = "Metod",
                options = availableMethods,
                selectedOption = viewModel.editSelectedMethod,
                onOptionSelected = { viewModel.onEditMethodSelected(it) },
                optionToString = { it?.name ?: "Select method..." }
            )
            OutlinedTextField(
                value = viewModel.editBrewTempCelsius,
                onValueChange = { viewModel.onEditBrewTempChanged(it) },
                label = { Text("Water Temperature (°C)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ---------- Reusable UI ----------
@Composable
fun BrewMetricsCard(metrics: BrewMetrics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
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
    val density = LocalDensity.current

    val massColor = Color.Black
    val flowColor = Color(0xFF007BFF)
    val gridLineColor = Color.LightGray
    val gridLinePaint = remember {
        Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f))
    }
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

    val hasFlowData = remember(samples) {
        samples.any { it.flowRateGramsPerSecond != null && it.flowRateGramsPerSecond!! > 0 }
    }

    Canvas(modifier = modifier) {
        val titlePaddingBottom = 60.dp.toPx()
        val titlePaddingStart = 60.dp.toPx()
        val titlePaddingEnd = 60.dp.toPx()
        val numericLabelPaddingBottom = 16.dp.toPx()
        val numericLabelPaddingStart = 28.dp.toPx()
        val numericLabelPaddingEnd = 28.dp.toPx()

        val totalPaddingBottom = titlePaddingBottom + numericLabelPaddingBottom
        val totalPaddingStart = titlePaddingStart + numericLabelPaddingStart
        val totalPaddingEnd = titlePaddingEnd + numericLabelPaddingEnd

        val graphStartX = totalPaddingStart
        val graphEndX = size.width - totalPaddingEnd
        val graphStartY = 0f
        val graphEndY = size.height - totalPaddingBottom
        val graphWidth = graphEndX - graphStartX
        val graphHeight = graphEndY - graphStartY

        if (graphWidth <= 0 || graphHeight <= 0) return@Canvas

        val maxTime = max(60000f, samples.maxOfOrNull { it.timeMillis }?.toFloat() ?: 1f) * 1.05f
        val actualMaxMass = samples.maxOfOrNull { it.massGrams }?.toFloat() ?: 1f
        val maxMass = max(50f, ceil(actualMaxMass / 50f) * 50f) * 1.1f
        val roundedMaxFlow = 20f
        val maxFlow = max(5f, roundedMaxFlow * 1.1f)

        drawContext.canvas.nativeCanvas.apply {
            val massGridInterval = 50f
            var currentMassGrid = massGridInterval
            while (currentMassGrid < maxMass / 1.1f) {
                val y = graphEndY - (currentMassGrid / maxMass) * graphHeight
                drawLine(gridLineColor, Offset(graphStartX, y), Offset(graphEndX, y),
                    strokeWidth = gridLinePaint.width, pathEffect = gridLinePaint.pathEffect)
                drawText("${currentMassGrid.toInt()}g", graphStartX - numericLabelPaddingStart / 2, y + numericLabelPaint.textSize / 3, numericLabelPaint)
                currentMassGrid += massGridInterval
            }

            if (hasFlowData) {
                val yMax = graphEndY - (roundedMaxFlow / maxFlow) * graphHeight
                drawText("${roundedMaxFlow.toInt()} g/s", graphEndX + 4.dp.toPx(), yMax + numericLabelPaintRight.textSize / 3, numericLabelPaintRight)
                val halfMaxFlow = roundedMaxFlow / 2f
                if (halfMaxFlow > 2f) {
                    val yHalf = graphEndY - (halfMaxFlow / maxFlow) * graphHeight
                    drawText("${halfMaxFlow.toInt()} g/s", graphEndX + 4.dp.toPx(), yHalf + numericLabelPaintRight.textSize / 3, numericLabelPaintRight)
                }
                if (roundedMaxFlow > 4f) {
                    val yLow = graphEndY - (2f / maxFlow) * graphHeight
                    drawText("2 g/s", graphEndX + 4.dp.toPx(), yLow + numericLabelPaintRight.textSize / 3, numericLabelPaintRight)
                }
            }

            val timeGridInterval = 30000f
            var currentTimeGrid = timeGridInterval
            while (currentTimeGrid < maxTime / 1.05f) {
                val x = graphStartX + (currentTimeGrid / maxTime) * graphWidth
                drawLine(gridLineColor, Offset(x, graphStartY), Offset(x, graphEndY),
                    strokeWidth = gridLinePaint.width, pathEffect = gridLinePaint.pathEffect)
                val timeSec = (currentTimeGrid / 1000).toInt()
                drawText("${timeSec}s", x, graphEndY + numericLabelPaddingBottom / 2 + numericLabelPaint.textSize / 2, numericLabelPaint)
                currentTimeGrid += timeGridInterval
            }
        }

        drawLine(massColor, Offset(graphStartX, graphStartY), Offset(graphStartX, graphEndY))
        drawLine(Color.Gray, Offset(graphStartX, graphEndY), Offset(graphEndX, graphEndY))
        if (hasFlowData) {
            drawLine(flowColor, Offset(graphEndX, graphStartY), Offset(graphEndX, graphEndY))
        }

        if (samples.size > 1) {
            val massPath = Path()
            val flowPath = Path()
            var flowPathStarted = false
            samples.forEachIndexed { index, s ->
                val x = graphStartX + (s.timeMillis.toFloat() / maxTime) * graphWidth
                val yMass = graphEndY - (s.massGrams.toFloat() / maxMass) * graphHeight
                val cx = x.coerceIn(graphStartX, graphEndX)
                val cyMass = yMass.coerceIn(graphStartY, graphEndY)
                if (showWeightLine) {
                    if (index == 0) massPath.moveTo(cx, cyMass) else massPath.lineTo(cx, cyMass)
                }

                if (showFlowLine && hasFlowData && s.flowRateGramsPerSecond != null && s.flowRateGramsPerSecond in 0.0..roundedMaxFlow.toDouble()) {
                    val yFlow = graphEndY - (s.flowRateGramsPerSecond.toFloat() / maxFlow) * graphHeight
                    val cyFlow = yFlow.coerceIn(graphStartY, graphEndY)

                    if (!flowPathStarted) {
                        flowPath.moveTo(cx, cyFlow)
                        flowPathStarted = true
                    } else {
                        flowPath.lineTo(cx, cyFlow)
                    }
                } else {
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

        drawContext.canvas.nativeCanvas.apply {
            drawText("Tid", graphStartX + graphWidth / 2, size.height - 60.dp.toPx() / 2 + axisTitlePaint.textSize / 3, axisTitlePaint)
            save(); rotate(-90f)
            drawText("Weight (g)", -(graphStartY + graphHeight / 2), 60.dp.toPx() / 2 + axisTitlePaint.textSize / 3, axisTitlePaint)
            if (hasFlowData) {
                drawText("Flow (g/s)", -(graphStartY + graphHeight / 2), size.width - 60.dp.toPx() / 2 - axisTitlePaint.descent(), axisTitlePaintFlow)
            }
            restore()
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
                text = { Text("No selection") },
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

// --- Helskärmsbild ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenImageScreen(
    uri: String,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("Brew Photo", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
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
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}