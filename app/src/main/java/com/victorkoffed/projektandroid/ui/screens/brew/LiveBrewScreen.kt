// app/src/main/java/com/victorkoffed/projektandroid/ui/screens/brew/LiveBrewScreen.kt
package com.victorkoffed.projektandroid.ui.screens.brew

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.withSave
import com.victorkoffed.projektandroid.data.db.BrewSample
import com.victorkoffed.projektandroid.data.themePref.ThemePreferenceManager
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.domain.model.ScaleMeasurement
import com.victorkoffed.projektandroid.ui.navigation.Screen
import com.victorkoffed.projektandroid.ui.theme.ProjektAndroidTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    connectionState: BleConnectionState,
    countdown: Int?, // Aktuell nedräkning (null om inte aktiv)
    onStartClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onStopAndSaveClick: () -> Unit,
    onTareClick: () -> Unit,
    onNavigateBack: () -> Unit,
    onResetRecording: () -> Unit,
    navigateTo: (String) -> Unit
) {
    // Styr visningen av flödesdata i StatusDisplay
    var showFlowInfo by remember { mutableStateOf(true) }
    var showDisconnectedAlert by remember { mutableStateOf(false) }
    var alertMessage by remember { mutableStateOf("The connection to the scale was lost.") }
    // UPPDATERAD: State för dialogens titel
    var alertTitle by remember { mutableStateOf("Connection Lost") }


    // Övervakar anslutningsstatus och reagerar på avbrott/fel
    LaunchedEffect(connectionState) {
        if (connectionState is BleConnectionState.Disconnected || connectionState is BleConnectionState.Error) {

            // UPPDATERAD: Sätt titel och meddelande baserat på state
            if (connectionState is BleConnectionState.Error) {
                alertTitle = "Connection Error"
                // Meddelandet kommer nu färdigöversatt från ViewModel
                alertMessage = connectionState.message
            } else {
                alertTitle = "Connection Lost"
                alertMessage = "The connection to the scale was lost."
            }

            // Om vi spelade in, pausa/återställ inspelningen och informera användaren
            if (isRecording || isPaused || countdown != null) {
                alertMessage += " Recording has been stopped."
                onResetRecording() // Återställ inspelning/nedräkning i VM
            }
            showDisconnectedAlert = true // Visa alltid dialogen vid fel/frånkoppling
        } else {
            // Dölj dialogen om anslutningen återupprättas eller är stabil
            showDisconnectedAlert = false
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
                        // Aktivera om inspelning/paus/nedräkning pågår, så att användaren kan avbryta och spara
                        enabled = isRecording || isPaused || countdown != null
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
            // Visar tid, vikt, flöde eller nedräkning
            StatusDisplay(
                currentTimeMillis = currentTimeMillis,
                // Visa vikt vid paus om 'weightAtPause' finns, annars nuvarande mätning
                currentMeasurement = if (isPaused) ScaleMeasurement(weightAtPause ?: 0f, 0f) else currentMeasurement,
                isRecording = isRecording,
                isPaused = isPaused,
                showFlow = showFlowInfo,
                countdown = countdown
            )
            Spacer(Modifier.height(16.dp))
            // Grafen visar endast viktlinjen i Live Brew-läge för att förenkla UI
            BrewGraph(
                samples = samples,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp)
            )
            Spacer(Modifier.height(8.dp))

            // FilterChip för att växla Flow-visning i StatusDisplay
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

            Spacer(Modifier.height(16.dp))

            // Kontrollknappar (Start/Paus/Återuppta/Nollställ/Tara)
            BrewControls(
                isRecording = isRecording,
                isPaused = isPaused,
                isConnected = connectionState is BleConnectionState.Connected,
                countdown = countdown,
                onStartClick = onStartClick,
                onPauseClick = onPauseClick,
                onResumeClick = onResumeClick,
                onTareClick = onTareClick,
                onResetClick = onResetRecording
            )
        }

        // Dialog vid anslutningsfel
        if (showDisconnectedAlert) {
            AlertDialog(
                onDismissRequest = {
                    showDisconnectedAlert = false
                    // Navigera bara om vi faktiskt är frånkopplade (inte vid Error)
                    if (connectionState is BleConnectionState.Disconnected) {
                        navigateTo(Screen.ScaleConnect.route)
                    }
                },
                // UPPDATERAD: Använd dynamisk titel
                title = { Text(alertTitle) },
                text = { Text(alertMessage) },
                confirmButton = {
                    TextButton(onClick = {
                        showDisconnectedAlert = false
                        // Navigera bara om vi faktiskt är frånkopplade
                        if (connectionState is BleConnectionState.Disconnected) {
                            navigateTo(Screen.ScaleConnect.route) // Navigera till anslutningsskärmen
                        }
                    }) {
                        Text("OK")
                    }
                }
            )
        }

    }
}

// --- StatusDisplay  ---
@SuppressLint("DefaultLocale")
@Composable
fun StatusDisplay(
    currentTimeMillis: Long,
    currentMeasurement: ScaleMeasurement,
    isRecording: Boolean,
    isPaused: Boolean,
    showFlow: Boolean,
    countdown: Int?
) {
    // Formatering av tid, vikt och flöde
    val timeString = remember(currentTimeMillis) {
        val minutes = (currentTimeMillis / 1000 / 60).toInt()
        val seconds = (currentTimeMillis / 1000 % 60).toInt()
        String.format("%02d:%02d", minutes, seconds)
    }

    val weightString = remember(currentMeasurement.weightGrams) { "%.1f g".format(currentMeasurement.weightGrams) }
    val flowString = remember(currentMeasurement.flowRateGramsPerSecond) { "%.1f g/s".format(currentMeasurement.flowRateGramsPerSecond) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                // Sätt färg baserat på status: Nedräkning, Paus, Inspelning, eller inaktiv
                countdown != null -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                isPaused -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                isRecording -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 16.dp)
                .fillMaxWidth()
                // Fast minsta höjd för att förhindra hopp när innehållet byts
                .defaultMinSize(minHeight = 180.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Visa nedräkning om 'countdown' har ett värde
            if (countdown != null) {
                Text(
                    text = "Starting in...",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = countdown.toString(),
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            } else {
                // Visa normala mätvärden
                Text(text = timeString, fontSize = 48.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (showFlow) Arrangement.SpaceEvenly else Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Weight", style = MaterialTheme.typography.labelMedium)
                        Text(text = weightString, fontSize = 36.sp, fontWeight = FontWeight.Light)
                    }
                    // Visa flödesdata endast om showFlow är sann
                    if (showFlow) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Flow", style = MaterialTheme.typography.labelMedium)
                            Text(text = flowString, fontSize = 36.sp, fontWeight = FontWeight.Light)
                        }
                    }
                }
                if (isPaused) {
                    Spacer(Modifier.height(4.dp))
                    Text("Paused", fontSize = 14.sp)
                }
            }
        }
    }
}


// --- FÖRENKLAD BrewGraph (visar BARA vikt) ---
@Composable
fun BrewGraph(
    samples: List<BrewSample>,
    modifier: Modifier = Modifier
) {
    // Denna graf är avsiktligt förenklad jämfört med BrewDetailScreen.
    // Den visar endast VILTLINJEN för att minska komplexiteten i realtid.
    val density = LocalDensity.current

    val graphLineColor = MaterialTheme.colorScheme.tertiary
    val textColor = MaterialTheme.colorScheme.onBackground.toArgb()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridLineColor = Color.LightGray

    // Paint-objekt för textetiketter (används i nativeCanvas)
    val textPaint = remember(textColor) { // Reagera på temabyte
        android.graphics.Paint().apply {
            color = textColor
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 10.sp.value * density.density
        }
    }
    // Paint-objekt för axeltitlar
    val axisLabelPaint = remember(textColor) { // Reagera på temabyte
        android.graphics.Paint().apply {
            color = textColor
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 14.sp.value * density.density
            isFakeBoldText = true
        }
    }
    // PathEffect för att rita prickade linjer
    val gridLinePaint = remember {
        Stroke(
            width = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
        )
    }

    Canvas(modifier = modifier.padding(start = 32.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)) {
        val axisPadding = 0f
        val xLabelPadding = 24.dp.toPx()
        val yLabelPadding = 24.dp.toPx()

        // Grafens rityta
        val graphWidth = size.width - yLabelPadding - axisPadding
        val graphHeight = size.height - xLabelPadding - axisPadding

        if (graphWidth <= 0 || graphHeight <= 0) return@Canvas

        // Skalning för tid och vikt
        // maxTime rundas upp till närmaste 60 sekunder (1 min), med en marginal på 5%
        val maxTime = max(60000f, samples.maxOfOrNull { it.timeMillis }?.toFloat() ?: 1f) * 1.05f
        // maxMass rundas upp till närmaste 50g, med en marginal på 10%
        val actualMaxMass = samples
            .maxOfOrNull { it.massGrams }
            ?.toFloat() ?: 1f
        val maxMass = max(50f, ceil(actualMaxMass / 50f) * 50f) * 1.1f

        val xAxisY = size.height - xLabelPadding
        val yAxisX = yLabelPadding

        drawContext.canvas.nativeCanvas.apply {
            // Rita rutnät och etiketter för Vikt (Y-axel)
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
                // Rita viktetikett vid sidan av rutnätslinjen
                drawText("${currentMassGrid.toInt()}g", yLabelPadding / 2, y + textPaint.textSize / 3, textPaint)
                currentMassGrid += massGridInterval
            }

            // Rita rutnät och etiketter för Tid (X-axel)
            val timeGridInterval = 30000f // 30 sekunder
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
                // Rita tids-etikett under rutnätslinjen
                val timeSec = (currentTimeGrid / 1000).toInt()
                drawText("${timeSec}s", x, size.height, textPaint)
                currentTimeGrid += timeGridInterval
            }

            // Rita axeltitlar
            drawText("Time", yAxisX + graphWidth / 2, size.height + axisLabelPaint.textSize / 2, axisLabelPaint) // Justerad Y-pos
            withSave {
                rotate(-90f)
                drawText(
                    "Weight (g)", // Lade till (g)
                    -(axisPadding + graphHeight / 2), // Centrera vertikalt
                    yLabelPadding / 2 - axisLabelPaint.descent(), // Positionera horisontellt
                    axisLabelPaint
                )
            }
        }


        // Rita axellinjer (vänster Y och botten X)
        drawLine(axisColor, Offset(yAxisX, axisPadding), Offset(yAxisX, xAxisY))
        drawLine(axisColor, Offset(yAxisX, xAxisY), Offset(size.width, xAxisY))

        // Rita endast viktkurvan
        if (samples.size > 1) {
            val path = Path()
            samples.forEachIndexed { index, sample ->
                val x = yAxisX + (sample.timeMillis.toFloat() / maxTime) * graphWidth
                val mass = sample.massGrams.toFloat()
                val y = xAxisY - (mass / maxMass) * graphHeight
                val clampedX = x.coerceIn(yAxisX, size.width)
                val clampedY = y.coerceIn(axisPadding, xAxisY)
                if (index == 0) {
                    path.moveTo(clampedX, clampedY)
                } else {
                    path.lineTo(clampedX, clampedY)
                }
            }
            drawPath(path = path, color = graphLineColor, style = Stroke(width = 2.dp.toPx()))
        }
    }
}
// --- SLUT FÖRENKLAD BrewGraph ---


// --- BrewControls ---
@Composable
fun BrewControls(
    isRecording: Boolean,
    isPaused: Boolean,
    isConnected: Boolean,
    countdown: Int?,
    onStartClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onTareClick: () -> Unit,
    onResetClick: () -> Unit
) {
    // Kontrollera om UI är upptaget med en nedräkning
    val isBusy = countdown != null

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Återställ (Replay) knapp
        IconButton(
            onClick = onResetClick,
            // Aktivera endast om inspelning eller paus pågår, och inte under nedräkning
            enabled = (isRecording || isPaused) && !isBusy
        ) {
            Icon(
                imageVector = Icons.Default.Replay,
                contentDescription = "Reset recording"
            )
        }
        // Huvudknapp (Start/Paus/Återuppta)
        Button(
            onClick = {
                when {
                    isPaused -> onResumeClick()
                    isRecording -> onPauseClick()
                    else -> onStartClick() // Startar sekvensen (som kan inkludera nedräkning)
                }
            },
            modifier = Modifier.size(72.dp),
            contentPadding = PaddingValues(0.dp),
            // Endast aktiv om vågen är ansluten och inte under nedräkning
            enabled = isConnected && !isBusy
        ) {
            Icon(
                imageVector = when {
                    isBusy -> Icons.Default.Timer // Timerikon vid nedräkning
                    isPaused -> Icons.Default.PlayArrow
                    isRecording -> Icons.Default.Pause
                    else -> Icons.Default.PlayArrow
                },
                contentDescription = when {
                    isBusy -> "Starting..."
                    isPaused -> "Resume"
                    isRecording -> "Pause"
                    else -> "Start"
                },
                modifier = Modifier.size(40.dp)
            )
        }
        // Tara-knapp (T)
        OutlinedButton(
            onClick = onTareClick,
            // Endast aktiv när vågen är ansluten och inte under nedräkning
            // TILLÅT TARA ÄVEN UNDER PAUS
            enabled = isConnected && !isBusy && (!isRecording || isPaused) ,
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

// --- Preview (UPPDATERAD) ---
@Preview(showBackground = true, heightDp = 600)
@Composable
fun LiveBrewScreenPreview() {
    // Hämta en tillfällig Context för att kunna skapa ThemePreferenceManager
    val context = LocalContext.current
    // Skapa en dummy-instans av ThemePreferenceManager för Preview
    val themeManager = remember { ThemePreferenceManager(context) }

    ProjektAndroidTheme(themePreferenceManager = themeManager) {
        // Skapar simulerade sample-data för grafen
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
        // Lokalt state för preview-simulering
        var isRec by remember { mutableStateOf(false) }
        var isPaused by remember { mutableStateOf(false) }
        var time by remember { mutableLongStateOf(0L) }
        // UPPDATERAD: Lade till dummy deviceAddress
        var connectionState by remember { mutableStateOf<BleConnectionState>(BleConnectionState.Connected("Preview Scale", "00:11:22:33:FF:EE")) }
        var countdown by remember { mutableStateOf<Int?>(null) } // Hanterar nedräkningsstate i preview

        val scope = rememberCoroutineScope()

        // Logik för att simulera mätningar under inspelning/paus
        LaunchedEffect(isRec, isPaused) {
            while (isRec && !isPaused) {
                delay(100) // Uppdatera tiden var 100ms
                time += 100
            }
        }


        val currentWeight = remember(time, isRec, isPaused) {
            if (!isRec && !isPaused) {
                ScaleMeasurement(0f, 0f) // Visa noll om inte inspelning/paus
            } else {
                // Hitta närmaste sample baserat på simulerad tid
                val currentSample = previewSamples.lastOrNull { it.timeMillis <= time }
                    ?: previewSamples.first() // Fallback till första

                ScaleMeasurement(
                    weightGrams = currentSample.massGrams.toFloat(),
                    flowRateGramsPerSecond = currentSample.flowRateGramsPerSecond?.toFloat() ?: 0f
                )
            }
        }
        val weightAtPausePreview = remember(isPaused, currentWeight) { if (isPaused) currentWeight.weightGrams else null }

        LiveBrewScreen(
            samples = previewSamples.filter { it.timeMillis <= time },
            currentMeasurement = currentWeight,
            currentTimeMillis = time,
            isRecording = isRec,
            isPaused = isPaused,
            weightAtPause = weightAtPausePreview,
            connectionState = connectionState,
            countdown = countdown,
            onStartClick = {
                // Simulera nedräkning i Preview
                scope.launch {
                    countdown = 3
                    delay(1000)
                    countdown = 2
                    delay(1000)
                    countdown = 1
                    delay(1000)
                    countdown = null
                    isRec = true
                    isPaused = false
                    time = 0L // Nollställ tiden vid start
                }
            },
            onPauseClick = { isPaused = true },
            onResumeClick = { isPaused = false },
            onStopAndSaveClick = { isRec = false; isPaused = false; countdown = null },
            onTareClick = { time = 0L /* Simulera tare genom att nollställa tiden? Eller bara logga? */ },
            onNavigateBack = { Log.d("Preview", "Navigate Back") }, // Ändrad loggtext
            onResetRecording = { isRec = false; isPaused = false; time = 0L; countdown = null },
            navigateTo = { screen -> Log.d("Preview", "Navigate to $screen") }
        )
    }
}