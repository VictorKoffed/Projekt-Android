package com.victorkoffed.projektandroid.ui.screens.brew

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.* // Inkluderar Spacer härifrån
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
// IMPORTERA SPECIFIKT för tydlighet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api // VIKTIGT
import androidx.compose.material3.FilterChip // VIKTIGT
import androidx.compose.material3.FilterChipDefaults // VIKTIGT
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme // VIKTIGT
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
// import androidx.compose.material3.Spacer // Behövs inte separat om layout.* finns
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
// Slut på specifika importer

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victorkoffed.projektandroid.data.db.BrewSample
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.domain.model.ScaleMeasurement
import com.victorkoffed.projektandroid.ui.theme.ProjektAndroidTheme
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class) // Se till att denna finns
@Composable
fun LiveBrewScreen(
    samples: List<BrewSample>,
    currentMeasurement: ScaleMeasurement,
    currentTimeMillis: Long,
    isRecording: Boolean,
    isPaused: Boolean,
    weightAtPause: Float?,
    connectionState: BleConnectionState,
    onStartClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onStopAndSaveClick: () -> Unit,
    onTareClick: () -> Unit,
    onNavigateBack: () -> Unit,
    onResetRecording: () -> Unit,
    navigateTo: (String) -> Unit
) {
    var showFlowInfo by remember { mutableStateOf(true) } // Denna styr nu bara textvisningen
    var showDisconnectedAlert by remember { mutableStateOf(false) }
    var alertMessage by remember { mutableStateOf("The connection to the scale was lost during recording. Recording has been stopped.") }

    LaunchedEffect(connectionState, isRecording) {
        if (isRecording && (connectionState is BleConnectionState.Disconnected || connectionState is BleConnectionState.Error)) {
            if (connectionState is BleConnectionState.Error) {
                alertMessage = "Error connecting to the scale: ${connectionState.message}. Recording has been stopped."
            } else {
                alertMessage = "The connection to the scale was lost during recording. Recording has been stopped.."
            }
            onResetRecording()
            showDisconnectedAlert = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Brew") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = onStopAndSaveClick,
                        enabled = isRecording || isPaused
                    ) {
                        Text("Done")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StatusDisplay(
                currentTimeMillis = currentTimeMillis,
                currentMeasurement = if (isPaused) ScaleMeasurement(weightAtPause ?: 0f, 0f) else currentMeasurement,
                isRecording = isRecording,
                isPaused = isPaused,
                showFlow = showFlowInfo // Skicka med för textvisning
            )
            Spacer(Modifier.height(16.dp)) // <-- Använder korrekt Spacer från layout.*
            BrewGraph( // <-- Använder den förenklade BrewGraph nedan
                samples = samples,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp)
            )
            Spacer(Modifier.height(8.dp)) // <-- Använder korrekt Spacer

            FilterChip(
                selected = showFlowInfo,
                onClick = { showFlowInfo = !showFlowInfo },
                label = { Text("Show Flow") },
                leadingIcon = {
                    Icon(
                        imageVector = if (showFlowInfo) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = if (showFlowInfo) "Visible" else "Hidden"
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                )
            )

            Spacer(Modifier.height(16.dp)) // <-- Använder korrekt Spacer

            BrewControls(
                isRecording = isRecording,
                isPaused = isPaused,
                isConnected = connectionState is BleConnectionState.Connected,
                onStartClick = onStartClick,
                onPauseClick = onPauseClick,
                onResumeClick = onResumeClick,
                onTareClick = onTareClick,
                onResetClick = onResetRecording
            )
        }

        if (showDisconnectedAlert) {
            AlertDialog(
                onDismissRequest = {
                    showDisconnectedAlert = false
                    navigateTo("scale")
                },
                title = { Text("Connection Broken") },
                text = { Text(alertMessage) },
                confirmButton = {
                    TextButton(onClick = {
                        showDisconnectedAlert = false
                        navigateTo("scale")
                    }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

// --- StatusDisplay (Oförändrad) ---
@Composable
fun StatusDisplay(
    currentTimeMillis: Long,
    currentMeasurement: ScaleMeasurement,
    isRecording: Boolean,
    isPaused: Boolean,
    showFlow: Boolean // Används för textvisning
) {
    val minutes = (currentTimeMillis / 1000 / 60).toInt()
    val seconds = (currentTimeMillis / 1000 % 60).toInt()
    val timeString = remember(minutes, seconds) { String.format("%02d:%02d", minutes, seconds) }

    val weightString = remember(currentMeasurement.weightGrams) { "%.1f g".format(currentMeasurement.weightGrams) }
    val flowString = remember(currentMeasurement.flowRateGramsPerSecond) { "%.1f g/s".format(currentMeasurement.flowRateGramsPerSecond) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isPaused -> Color.Gray
                isRecording -> MaterialTheme.colorScheme.tertiaryContainer
                else -> Color.Gray
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = timeString, fontSize = 48.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(8.dp)) // <-- Använder korrekt Spacer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (showFlow) Arrangement.SpaceEvenly else Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Weight", style = MaterialTheme.typography.labelMedium)
                    Text(text = weightString, fontSize = 36.sp, fontWeight = FontWeight.Light)
                }
                if (showFlow) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Flow", style = MaterialTheme.typography.labelMedium)
                        Text(text = flowString, fontSize = 36.sp, fontWeight = FontWeight.Light)
                    }
                }
            }
            if (isPaused) {
                Spacer(Modifier.height(4.dp)) // <-- Använder korrekt Spacer
                Text("Paused", fontSize = 14.sp)
            }
        }
    }
}


// --- FÖRENKLAD BrewGraph (visar BARA vikt) ---
@Composable
fun BrewGraph(
    samples: List<BrewSample>,
    modifier: Modifier = Modifier
    // INGA ANDRA PARAMETRAR HÄR!
) {
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
        Stroke(
            width = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
        )
    }
    val gridLineColor = Color.LightGray

    Canvas(modifier = modifier.padding(start = 32.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)) {
        val axisPadding = 0f
        val xLabelPadding = 24.dp.toPx()
        val yLabelPadding = 24.dp.toPx()

        val graphWidth = size.width - yLabelPadding - axisPadding
        val graphHeight = size.height - xLabelPadding - axisPadding

        if (graphWidth <= 0 || graphHeight <= 0) return@Canvas

        // Skalning (endast tid och massa)
        val maxTime = max(60000f, samples.maxOfOrNull { it.timeMillis }?.toFloat() ?: 1f) * 1.05f
        val actualMaxMass = samples
            .maxOfOrNull { it.massGrams ?: 0.0 }
            ?.toFloat() ?: 1f
        val maxMass = max(50f, ceil(actualMaxMass / 50f) * 50f) * 1.1f

        val xAxisY = size.height - xLabelPadding
        val yAxisX = yLabelPadding

        // Rita rutnät och etiketter (endast vikt och tid)
        drawContext.canvas.nativeCanvas.apply {
            // Vikt (Y-axel)
            val massGridInterval = 50f
            var currentMassGrid = massGridInterval
            while (currentMassGrid < maxMass / 1.1f) {
                val y = xAxisY - (currentMassGrid / maxMass) * graphHeight
                drawLine(
                    color = gridLineColor,
                    start = Offset(yAxisX, y),
                    end = Offset(size.width, y),
                    strokeWidth = gridLinePaint.width,
                    pathEffect = gridLinePaint.pathEffect
                )
                drawText("${currentMassGrid.toInt()}g", yLabelPadding / 2, y + textPaint.textSize / 3, textPaint.apply { textAlign = android.graphics.Paint.Align.CENTER })
                currentMassGrid += massGridInterval
            }

            // Tid (X-axel)
            val timeGridInterval = 30000f
            var currentTimeGrid = timeGridInterval
            while (currentTimeGrid < maxTime / 1.05f) {
                val x = yAxisX + (currentTimeGrid / maxTime) * graphWidth
                drawLine(
                    color = gridLineColor,
                    start = Offset(x, axisPadding),
                    end = Offset(x, xAxisY),
                    strokeWidth = gridLinePaint.width,
                    pathEffect = gridLinePaint.pathEffect
                )
                val timeSec = (currentTimeGrid / 1000).toInt()
                drawText("${timeSec}s", x, size.height, textPaint)
                currentTimeGrid += timeGridInterval
            }

            // Axeltitlar (endast vikt och tid)
            drawText("Time", yAxisX + graphWidth / 2, size.height + xLabelPadding / 1.5f, axisLabelPaint)
            save(); rotate(-90f)
            drawText("Weight", -size.height / 2, yLabelPadding / 2 - axisLabelPaint.descent(), axisLabelPaint)
            restore()
        }

        // Rita axlar (endast vänster Y och X)
        drawLine(Color.Gray, Offset(yAxisX, axisPadding), Offset(yAxisX, xAxisY)) // Y
        drawLine(Color.Gray, Offset(yAxisX, xAxisY), Offset(size.width, xAxisY)) // X

        // Rita BARA vikt-linjen
        if (samples.size > 1) {
            val path = Path()
            samples.forEachIndexed { index, sample ->
                val x = yAxisX + (sample.timeMillis.toFloat() / maxTime) * graphWidth
                val mass = (sample.massGrams ?: 0.0).toFloat()
                val y = xAxisY - (mass / maxMass) * graphHeight
                val clampedX = x.coerceIn(yAxisX, size.width)
                val clampedY = y.coerceIn(axisPadding, xAxisY)
                if (index == 0) {
                    path.moveTo(clampedX, clampedY)
                } else {
                    path.lineTo(clampedX, clampedY)
                }
            }
            drawPath(path = path, color = Color.Black, style = Stroke(width = 2.dp.toPx()))
        }
    }
}
// --- SLUT PÅ FÖRENKLAD BrewGraph ---


/* BrewControls (Oförändrad) */
@Composable
fun BrewControls(
    isRecording: Boolean,
    isPaused: Boolean,
    isConnected: Boolean,
    onStartClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onTareClick: () -> Unit,
    onResetClick: () -> Unit
) {
    // ... (koden är densamma som tidigare) ...
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onResetClick,
            enabled = (isRecording || isPaused)
        ) {
            Icon(
                imageVector = Icons.Default.Replay,
                contentDescription = "Reset recording"
            )
        }
        Button(
            onClick = {
                when {
                    isPaused -> onResumeClick()
                    isRecording -> onPauseClick()
                    else -> onStartClick()
                }
            },
            modifier = Modifier.size(72.dp),
            contentPadding = PaddingValues(0.dp),
            enabled = isConnected
        ) {
            Icon(
                imageVector = when {
                    isPaused -> Icons.Default.PlayArrow
                    isRecording -> Icons.Default.Pause
                    else -> Icons.Default.PlayArrow
                },
                contentDescription = when {
                    isPaused -> "Resume"
                    isRecording -> "Pause"
                    else -> "Start"
                },
                modifier = Modifier.size(40.dp)
            )
        }
        OutlinedButton(
            onClick = onTareClick,
            enabled = isConnected && !isRecording && !isPaused,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text = "T",
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

// Preview (Oförändrad förutom null-säker läsning av Double?)
@Preview(showBackground = true, heightDp = 600)
@Composable
fun LiveBrewScreenPreview() {
    // ... (koden är densamma som tidigare) ...
    ProjektAndroidTheme {
        val previewSamples = remember {
            listOf(
                BrewSample(brewId = 1, timeMillis = 0, massGrams = 0.0, flowRateGramsPerSecond = 0.0),
                BrewSample(brewId = 1, timeMillis = 30000, massGrams = 50.0, flowRateGramsPerSecond = 2.5),
                BrewSample(brewId = 1, timeMillis = 60000, massGrams = 110.0, flowRateGramsPerSecond = 3.0),
                BrewSample(brewId = 1, timeMillis = 120000, massGrams = 250.0, flowRateGramsPerSecond = 2.8),
                BrewSample(brewId = 1, timeMillis = 150000, massGrams = 350.0, flowRateGramsPerSecond = 2.0),
                BrewSample(brewId = 1, timeMillis = 180000, massGrams = 420.0, flowRateGramsPerSecond = 1.5)
            )
        }
        var isRec by remember { mutableStateOf(false) }
        var isPaused by remember { mutableStateOf(false) }
        var time by remember { mutableStateOf(0L) } // Starta på 0 i preview
        var connectionState by remember { mutableStateOf<BleConnectionState>(BleConnectionState.Connected("Preview Scale")) }

        val nextSample = remember(isRec, isPaused, time) { previewSamples.find { it.timeMillis >= time } }
        val lastSample = remember { previewSamples.last() }

        val currentWeight = ScaleMeasurement(
            weightGrams = if (isRec || isPaused)
                ((nextSample?.massGrams ?: lastSample.massGrams ?: 0.0).toFloat())
            else 0f,
            flowRateGramsPerSecond = if (isRec || isPaused)
                ((nextSample?.flowRateGramsPerSecond ?: lastSample.flowRateGramsPerSecond ?: 0.0).toFloat())
            else 0f
        )
        val weightAtPausePreview = remember(isPaused, currentWeight) { if (isPaused) currentWeight.weightGrams else null }

        LiveBrewScreen(
            samples = previewSamples.filter { it.timeMillis <= time }, // Visa bara samples upp till aktuell tid
            currentMeasurement = currentWeight,
            currentTimeMillis = time,
            isRecording = isRec,
            isPaused = isPaused,
            weightAtPause = weightAtPausePreview,
            connectionState = connectionState,
            onStartClick = { isRec = true; isPaused = false; time = 0L; connectionState = BleConnectionState.Connected("Preview Scale") }, // Nollställ tid vid start
            onPauseClick = { isPaused = true },
            onResumeClick = { isPaused = false },
            onStopAndSaveClick = { isRec = false; isPaused = false },
            onTareClick = {},
            onNavigateBack = { Log.d("Preview", "Navigate Back to Scale") },
            onResetRecording = { isRec = false; isPaused = false; time = 0L; connectionState = BleConnectionState.Connected("Preview Scale") },
            navigateTo = { screen -> Log.d("Preview", "Navigate to $screen") }
        )
    }
}
