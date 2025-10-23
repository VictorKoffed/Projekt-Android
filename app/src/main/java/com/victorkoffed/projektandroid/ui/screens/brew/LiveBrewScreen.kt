package com.victorkoffed.projektandroid.ui.screens.brew

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape // <-- NY IMPORT (om du vill ha rund knapp)
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect // För streckade linjer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas // För text på Canvas
import androidx.compose.ui.platform.LocalDensity // För textstorlek
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victorkoffed.projektandroid.data.db.BrewSample
import com.victorkoffed.projektandroid.domain.model.ScaleMeasurement
import com.victorkoffed.projektandroid.ui.theme.ProjektAndroidTheme
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveBrewScreen(
    samples: List<BrewSample>,
    currentMeasurement: ScaleMeasurement,
    currentTimeMillis: Long,
    isRecording: Boolean,
    isPaused: Boolean,
    weightAtPause: Float?,
    onStartClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onStopAndSaveClick: () -> Unit,
    onTareClick: () -> Unit,
    onNavigateBack: () -> Unit,
    onResetRecording: () -> Unit
) {
    var showFlowInfo by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Brew") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Tillbaka")
                    }
                },
                actions = {
                    TextButton(
                        onClick = onStopAndSaveClick,
                        enabled = isRecording || isPaused
                    ) {
                        Text("Klar")
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
                showFlow = showFlowInfo
            )
            Spacer(Modifier.height(16.dp))
            BrewGraph(
                samples = samples,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp)
            )
            Spacer(Modifier.height(8.dp))
            FilterChip(
                selected = showFlowInfo,
                onClick = { showFlowInfo = !showFlowInfo },
                label = { Text("Visa Flöde") },
                leadingIcon = {
                    if (showFlowInfo) Icon(Icons.Default.Check, "Visas") else Icon(Icons.Default.Close, "Dold")
                }
            )
            Spacer(Modifier.height(16.dp)) // Mer space före kontrollerna

            BrewControls(
                isRecording = isRecording,
                isPaused = isPaused,
                onStartClick = onStartClick,
                onPauseClick = onPauseClick,
                onResumeClick = onResumeClick,
                onTareClick = onTareClick,
                onResetClick = onResetRecording
            )
        }
    }
}

// StatusDisplay (Oförändrad)
@Composable
fun StatusDisplay(
    currentTimeMillis: Long,
    currentMeasurement: ScaleMeasurement,
    isRecording: Boolean,
    isPaused: Boolean,
    showFlow: Boolean
) {
    // Tid
    val minutes = (currentTimeMillis / 1000 / 60).toInt()
    val seconds = (currentTimeMillis / 1000 % 60).toInt()
    val timeString = remember(minutes, seconds) { String.format("%02d:%02d", minutes, seconds) }

    // Vikt och Flöde
    val weightString = remember(currentMeasurement.weightGrams) { "%.1f g".format(currentMeasurement.weightGrams) }
    val flowString = remember(currentMeasurement.flowRateGramsPerSecond) { "%.1f g/s".format(currentMeasurement.flowRateGramsPerSecond) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isPaused -> Color.Gray
                isRecording -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = timeString, fontSize = 48.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (showFlow) Arrangement.SpaceEvenly else Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Vikt", style = MaterialTheme.typography.labelMedium)
                    Text(text = weightString, fontSize = 36.sp, fontWeight = FontWeight.Light)
                }
                if (showFlow) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Flöde", style = MaterialTheme.typography.labelMedium)
                        Text(text = flowString, fontSize = 36.sp, fontWeight = FontWeight.Light)
                    }
                }
            }
            if (isPaused) {
                Spacer(Modifier.height(4.dp))
                Text("Pausad", fontSize = 14.sp)
            }
        }
    }
}

// BrewGraph (Oförändrad, visar bara vikt)
@Composable
fun BrewGraph(
    samples: List<BrewSample>,
    modifier: Modifier = Modifier
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

        // Skalning
        val maxTime = max(60000f, samples.maxOfOrNull { it.timeMillis }?.toFloat() ?: 1f) * 1.05f
        val actualMaxMass = samples.maxOfOrNull { it.massGrams }?.toFloat() ?: 1f
        val maxMass = max(50f, ceil(actualMaxMass / 50f) * 50f) * 1.1f

        // Axlar start/slut
        val xAxisY = size.height - xLabelPadding
        val yAxisX = yLabelPadding

        // Rita rutnät och etiketter
        drawContext.canvas.nativeCanvas.apply {
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

            drawText("Tid", yAxisX + graphWidth / 2, size.height + xLabelPadding / 1.5f, axisLabelPaint)
            save(); rotate(-90f)
            drawText("Vikt", -size.height / 2, yLabelPadding / 2 - axisLabelPaint.descent(), axisLabelPaint)
            restore()
        }

        // Rita axlar
        drawLine(Color.Gray, Offset(yAxisX, axisPadding), Offset(yAxisX, xAxisY)) // Y
        drawLine(Color.Gray, Offset(yAxisX, xAxisY), Offset(size.width, xAxisY)) // X

        // Rita graf-linjen
        if (samples.size > 1) {
            val path = Path()
            samples.forEachIndexed { index, sample ->
                val x = yAxisX + (sample.timeMillis.toFloat() / maxTime) * graphWidth
                val y = xAxisY - (sample.massGrams.toFloat() / maxMass) * graphHeight
                val clampedX = x.coerceIn(yAxisX, size.width)
                val clampedY = y.coerceIn(axisPadding, xAxisY)
                if (index == 0) path.moveTo(clampedX, clampedY) else path.lineTo(clampedX, clampedY)
            }
            drawPath(path = path, color = Color.Black, style = Stroke(width = 2.dp.toPx()))
        }
    }
}


// --- UPPDATERAD BrewControls med "T"-knapp ---
@Composable
fun BrewControls(
    isRecording: Boolean,
    isPaused: Boolean,
    onStartClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onTareClick: () -> Unit,
    onResetClick: () -> Unit // Ny parameter för återställning
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly, // Fördelar knapparna jämnt
        verticalAlignment = Alignment.CenterVertically // Centrerar vertikalt
    ) {
        // Knapp 1: Återställ (Reset) - IconButton
        IconButton(
            onClick = onResetClick,
            // Inaktivera om inspelning *inte* pågår (för att undvika återställning av misstag)
            enabled = isRecording || isPaused
        ) {
            Icon(
                imageVector = Icons.Default.Replay, // Rund pil ikon
                contentDescription = "Återställ inspelning"
            )
        }

        // Knapp 2: Play/Pause/Resume (Större knapp i mitten) - Button
        Button(
            onClick = {
                when {
                    isPaused -> onResumeClick()
                    isRecording -> onPauseClick()
                    else -> onStartClick()
                }
            },
            modifier = Modifier.size(72.dp), // Gör den lite större
            // Centrera innehållet perfekt
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                imageVector = when {
                    isPaused -> Icons.Default.PlayArrow
                    isRecording -> Icons.Default.Pause
                    else -> Icons.Default.PlayArrow
                },
                contentDescription = when {
                    isPaused -> "Återuppta"
                    isRecording -> "Pausa"
                    else -> "Starta"
                },
                modifier = Modifier.size(40.dp) // Större ikon
            )
        }

        // --- KNAPP 3: Tare (Nu en OutlinedButton med text "T") ---
        OutlinedButton(
            onClick = onTareClick,
            enabled = !isRecording && !isPaused,
            // Gör den fyrkantig och lika stor som IconButton ungefär
            modifier = Modifier.size(48.dp), // Standardstorlek för IconButton är 48dp
            shape = CircleShape, // Gör den rund om du vill, annars ta bort för rektangel
            contentPadding = PaddingValues(0.dp) // Ta bort padding för att centrera texten
        ) {
            Text(
                text = "T",
                style = MaterialTheme.typography.titleLarge // Gör T lite större
            )
        }
        // --- SLUT PÅ ÄNDRING ---
    }
}
// --- SLUT PÅ UPPDATERING ---


// Preview (Oförändrad)
@Preview(showBackground = true, heightDp = 600)
@Composable
fun LiveBrewScreenPreview() {
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
        var time by remember { mutableStateOf(158000L) }

        val currentWeight = ScaleMeasurement(
            weightGrams = previewSamples.lastOrNull()?.massGrams?.toFloat() ?: 0f,
            flowRateGramsPerSecond = previewSamples.lastOrNull()?.flowRateGramsPerSecond?.toFloat() ?: 0f
        )
        val weightAtPausePreview = remember(isPaused, currentWeight) { if (isPaused) currentWeight.weightGrams else null }

        LaunchedEffect(isRec, isPaused) {
            while(isRec && !isPaused) {
                delay(100)
                time += 100
            }
        }

        LiveBrewScreen(
            samples = previewSamples,
            currentMeasurement = currentWeight,
            currentTimeMillis = time,
            isRecording = isRec,
            isPaused = isPaused,
            weightAtPause = weightAtPausePreview,
            onStartClick = { isRec = true; isPaused = false },
            onPauseClick = { isPaused = true },
            onResumeClick = { isPaused = false },
            onStopAndSaveClick = { isRec = false; isPaused = false },
            onTareClick = {},
            onNavigateBack = {},
            onResetRecording = { isRec = false; isPaused = false; time = 0L }
        )
    }
}