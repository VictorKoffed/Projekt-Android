package com.victorkoffed.projektandroid.ui.screens.brew

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victorkoffed.projektandroid.data.db.BrewSample
import com.victorkoffed.projektandroid.domain.model.ScaleMeasurement
import com.victorkoffed.projektandroid.ui.theme.ProjektAndroidTheme
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.ceil // För att avrunda uppåt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveBrewScreen(
    samples: List<BrewSample>,
    currentMeasurement: ScaleMeasurement,
    currentTimeMillis: Long,
    isRecording: Boolean,
    isPaused: Boolean,
    onStartClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onStopAndSaveClick: () -> Unit,
    onTareClick: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Brew") },
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
                currentWeightGrams = currentMeasurement.weightGrams,
                isRecording = isRecording,
                isPaused = isPaused
            )
            Spacer(Modifier.height(16.dp))
            BrewGraph(
                samples = samples,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp)
            )
            Spacer(Modifier.height(16.dp))
            BrewControls(
                isRecording = isRecording,
                isPaused = isPaused,
                onStartClick = onStartClick,
                onPauseClick = onPauseClick,
                onResumeClick = onResumeClick,
                onTareClick = onTareClick
            )
        }
    }
}

@Composable
fun StatusDisplay(
    currentTimeMillis: Long,
    currentWeightGrams: Float,
    isRecording: Boolean,
    isPaused: Boolean
) {
    val minutes = (currentTimeMillis / 1000 / 60).toInt()
    val seconds = (currentTimeMillis / 1000 % 60).toInt()
    val timeString = remember(minutes, seconds) { String.format("%02d:%02d", minutes, seconds) }
    val weightString = remember(currentWeightGrams) { "%.1f g".format(currentWeightGrams) }

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
            Text(text = weightString, fontSize = 36.sp, fontWeight = FontWeight.Light)
            if (isPaused) {
                Spacer(Modifier.height(4.dp))
                Text("Pausad", fontSize = 14.sp)
            }
        }
    }
}

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
            textSize = 12.sp.value * density.density
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
    val gridLinePaint = remember { // Paint för rutnätet
        Stroke(
            width = 1f, // Tunna linjer
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f) // Streckade linjer
        )
    }
    val gridLineColor = Color.LightGray // Ljusgrå färg för rutnätet

    Canvas(modifier = modifier.padding(start = 32.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)) {
        val axisPadding = 0f
        val xLabelPadding = 24.dp.toPx()
        val yLabelPadding = 24.dp.toPx()

        val graphWidth = size.width - yLabelPadding - axisPadding
        val graphHeight = size.height - xLabelPadding - axisPadding

        // Skalning (med marginaler)
        val maxTime = max(60000f, samples.maxOfOrNull { it.timeMillis }?.toFloat() ?: 1f) * 1.05f
        // Avrunda maxMass uppåt till närmsta 10-tal för snyggare rutnät
        val actualMaxMass = samples.maxOfOrNull { it.massGrams }?.toFloat() ?: 1f
        val maxMass = max(100f, ceil(actualMaxMass / 10f) * 10f) * 1.1f


        // Axlar start/slut punkter
        val xAxisY = size.height - xLabelPadding
        val yAxisX = yLabelPadding

        // --- RITA RUTNÄT ---
        // Horisontella linjer (Vikt, var 10:e gram)
        val massGridInterval = 10f
        var currentMassGrid = massGridInterval
        while (currentMassGrid < maxMass / 1.1f) { // Rita upp till faktiska max
            val y = xAxisY - (currentMassGrid / maxMass) * graphHeight
            drawLine(
                color = gridLineColor,
                start = Offset(yAxisX, y),
                end = Offset(size.width, y), // Rita över hela bredden
                strokeWidth = gridLinePaint.width,
                pathEffect = gridLinePaint.pathEffect
            )
            currentMassGrid += massGridInterval
        }
        // Vertikala linjer (Tid, var 30:e sekund) - kan läggas till om önskvärt
        val timeGridInterval = 30000f
        var currentTimeGrid = timeGridInterval
        while (currentTimeGrid < maxTime / 1.05f) {
            val x = yAxisX + (currentTimeGrid / maxTime) * graphWidth
            drawLine(
                color = gridLineColor,
                start = Offset(x, axisPadding), // Starta från toppen
                end = Offset(x, xAxisY),       // Sluta vid x-axeln
                strokeWidth = gridLinePaint.width,
                pathEffect = gridLinePaint.pathEffect
            )
            currentTimeGrid += timeGridInterval
        }
        // --- SLUT RUTNÄT ---


        // Rita axlar (ovanpå rutnätet)
        drawLine(Color.Gray, Offset(yAxisX, axisPadding), Offset(yAxisX, xAxisY)) // Y
        drawLine(Color.Gray, Offset(yAxisX, xAxisY), Offset(size.width, xAxisY)) // X

        // Rita axel-etiketter (Text)
        drawContext.canvas.nativeCanvas.apply {
            // X-axel (Tid) - Etiketter var 30:e sekund
            val timeLabelInterval = 30000f
            var currentTimeLabel = timeLabelInterval
            while (currentTimeLabel <= maxTime / 1.05f) {
                val xPos = yAxisX + (currentTimeLabel / maxTime) * graphWidth
                val timeSec = (currentTimeLabel / 1000).toInt()
                drawText("${timeSec}s", xPos, size.height, textPaint)
                currentTimeLabel += timeLabelInterval
            }
            drawText("Tid", yAxisX + graphWidth / 2, size.height + xLabelPadding / 1.5f, axisLabelPaint)

            // Y-axel (Vikt) - Etiketter var 100:e gram (färre etiketter nu när vi har rutnät)
            val massLabelInterval = 100f
            var currentMassLabel = massLabelInterval
            while (currentMassLabel <= maxMass / 1.1f) {
                val yPos = xAxisY - (currentMassLabel / maxMass) * graphHeight
                drawText("${currentMassLabel.toInt()}g", yLabelPadding / 2 , yPos + textPaint.textSize / 3, textPaint.apply{textAlign = android.graphics.Paint.Align.CENTER})
                currentMassLabel += massLabelInterval
            }
            save(); rotate(-90f)
            drawText("Vikt", -size.height / 2, yLabelPadding / 2 - axisLabelPaint.descent(), axisLabelPaint)
            restore()
        }

        // Rita graf-linjen (ovanpå rutnät och axlar)
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


@Composable
fun BrewControls(
    isRecording: Boolean,
    isPaused: Boolean,
    onStartClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onTareClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        OutlinedButton(onClick = onTareClick, enabled = !isRecording && !isPaused) {
            Icon(Icons.Default.Refresh, contentDescription = "Nollställ (Tare)")
        }
        Button(
            onClick = {
                when {
                    isPaused -> onResumeClick()
                    isRecording -> onPauseClick()
                    else -> onStartClick()
                }
            },
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                when {
                    isPaused -> Icons.Default.PlayArrow
                    isRecording -> Icons.Default.Pause
                    else -> Icons.Default.PlayArrow
                },
                contentDescription = when {
                    isPaused -> "Återuppta"
                    isRecording -> "Pausa"
                    else -> "Starta"
                },
                modifier = Modifier.size(36.dp)
            )
        }
        OutlinedButton(onClick = { /* TODO */ }) {
            Icon(Icons.Default.Timer, contentDescription = "Timer Funktion?")
        }
    }
}

// Preview (behålls för testning)
@Preview(showBackground = true, heightDp = 600)
@Composable
fun LiveBrewScreenPreview() {
    ProjektAndroidTheme {
        val previewSamples = remember {
            listOf(
                BrewSample(brewId = 1, timeMillis = 0, massGrams = 0.0),
                BrewSample(brewId = 1, timeMillis = 30000, massGrams = 50.0),
                BrewSample(brewId = 1, timeMillis = 60000, massGrams = 110.0),
                BrewSample(brewId = 1, timeMillis = 120000, massGrams = 250.0),
                BrewSample(brewId = 1, timeMillis = 150000, massGrams = 350.0),
                BrewSample(brewId = 1, timeMillis = 180000, massGrams = 420.0)
            )
        }
        var isRec by remember { mutableStateOf(false) }
        var isPaused by remember { mutableStateOf(false) }
        var time by remember { mutableStateOf(158000L) }
        val currentWeight = ScaleMeasurement(previewSamples.lastOrNull()?.massGrams?.toFloat() ?: 0f)

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
            onStartClick = { isRec = true; isPaused = false },
            onPauseClick = { isPaused = true },
            onResumeClick = { isPaused = false },
            onStopAndSaveClick = { isRec = false; isPaused = false },
            onTareClick = {},
            onNavigateBack = {}
        )
    }
}

