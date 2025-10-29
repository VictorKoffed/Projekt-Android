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
import androidx.compose.material.icons.filled.BluetoothSearching // NY IKON
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
import androidx.compose.material3.LocalContentColor // Importen fanns redan här
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
    isPausedDueToDisconnect: Boolean, // NYTT STATE
    weightAtPause: Float?,
    connectionState: BleConnectionState,
    countdown: Int?,
    onStartClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onStopAndSaveClick: () -> Unit,
    onTareClick: () -> Unit,
    onNavigateBack: () -> Unit,
    // onResetRecording tas bort härifrån, anropas direkt från ViewModel vid disconnect nu
    navigateTo: (String) -> Unit
) {
    var showFlowInfo by remember { mutableStateOf(true) }
    var showDisconnectedAlert by remember { mutableStateOf(false) }
    var alertMessage by remember { mutableStateOf("The connection to the scale was lost.") }
    var alertTitle by remember { mutableStateOf("Connection Lost") }
    // NYTT: State för att visa återanslutningsmeddelande
    var showReconnectingMessage by remember(connectionState, isPausedDueToDisconnect) {
        mutableStateOf(isPausedDueToDisconnect && connectionState !is BleConnectionState.Connected)
    }


    // Övervakar anslutningsstatus
    LaunchedEffect(connectionState, isRecording, isPaused, isPausedDueToDisconnect) {
        // Om vi tappar anslutningen MEDAN inspelning/paus pågår (och det INTE är en manuell paus)
        if ((connectionState is BleConnectionState.Disconnected || connectionState is BleConnectionState.Error) &&
            (isRecording || isPaused) && // Inspelning eller paus pågick
            !isPausedDueToDisconnect && // Och pausen berodde *inte* redan på disconnect (för att undvika loop)
            !isPaused // Säkerställ att den inte var *manuellt* pausad just nu
        ) {
            // Sätt titel och meddelande
            alertTitle = if (connectionState is BleConnectionState.Error) "Connection Error" else "Connection Lost"
            alertMessage = if (connectionState is BleConnectionState.Error) {
                connectionState.message + " Recording paused." // Lägg till pausinfo
            } else {
                "The connection to the scale was lost. Recording paused." // Lägg till pausinfo
            }
            // Visa dialogen som informerar om att inspelningen är pausad
            showDisconnectedAlert = true
            showReconnectingMessage = true // Visa återanslutningsmeddelande
            // ViewModel hanterar nu själva pausen via handleConnectionStateChange

        } else if (connectionState is BleConnectionState.Connected && isPausedDueToDisconnect) {
            // Om vi återansluter MEDAN vi är pausade pga disconnect
            showDisconnectedAlert = false // Dölj dialogen
            showReconnectingMessage = false // Dölj återanslutningsmeddelandet
            // Visa ett meddelande i StatusDisplay eller liknande att man kan återuppta? (Hanteras nu i StatusDisplay)

        } else if (connectionState !is BleConnectionState.Connected && !isPausedDueToDisconnect) {
            // Om vi är frånkopplade men INTE pausade pga disconnect (t.ex. vid skärmstart, eller manuell paus)
            // Dölj återanslutningsmeddelandet om det mot förmodan var synligt
            showReconnectingMessage = false
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
                        // Aktivera om inspelning/paus/nedräkning pågår (oavsett orsak till paus)
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
            StatusDisplay(
                currentTimeMillis = currentTimeMillis,
                currentMeasurement = if (isPaused) ScaleMeasurement(weightAtPause ?: 0f, 0f) else currentMeasurement,
                isRecording = isRecording,
                isPaused = isPaused,
                isPausedDueToDisconnect = isPausedDueToDisconnect, // Skicka med nya state
                showFlow = showFlowInfo,
                countdown = countdown
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
            BrewControls(
                isRecording = isRecording,
                isPaused = isPaused,
                isPausedDueToDisconnect = isPausedDueToDisconnect, // Skicka med nya state
                isConnected = connectionState is BleConnectionState.Connected,
                countdown = countdown,
                onStartClick = onStartClick,
                onPauseClick = onPauseClick, // För manuell paus
                onResumeClick = onResumeClick,
                onTareClick = onTareClick,
                onResetClick = { /* ViewModel hanterar reset vid disconnect internt */ } // Lambda för manuell reset (inaktiv vid disconnect)
            )
        }

        // Dialog vid anslutningsfel under pågående inspelning/paus
        if (showDisconnectedAlert) {
            AlertDialog(
                onDismissRequest = {
                    showDisconnectedAlert = false
                    // Navigera inte automatiskt här, låt användaren stanna kvar
                },
                title = { Text(alertTitle) },
                text = { Text(alertMessage) },
                confirmButton = {
                    TextButton(onClick = {
                        showDisconnectedAlert = false
                        // Navigera inte, låt användaren försöka återansluta eller spara
                    }) {
                        Text("OK")
                    }
                },
                // NYTT: Knapp för att avbryta och spara direkt från dialogen
                dismissButton = {
                    TextButton(onClick = {
                        showDisconnectedAlert = false
                        onStopAndSaveClick() // Anropa spara-funktionen
                    }) {
                        Text("Stop & Save As Is")
                    }
                }
            )
        }

    }
}

// --- StatusDisplay (UPPDATERAD) ---
@SuppressLint("DefaultLocale")
@Composable
fun StatusDisplay(
    currentTimeMillis: Long,
    currentMeasurement: ScaleMeasurement,
    isRecording: Boolean,
    isPaused: Boolean,
    isPausedDueToDisconnect: Boolean, // NYTT STATE
    showFlow: Boolean,
    countdown: Int?
) {
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
                countdown != null -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                isPausedDueToDisconnect -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f) // Röd vid disconnect-paus
                isPaused -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) // Grå vid manuell paus
                isRecording -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 16.dp)
                .fillMaxWidth()
                .defaultMinSize(minHeight = 180.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (countdown != null) {
                // Nedräkningsvy (behålls som tidigare)
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
                // Normal mätvy
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
                    if (showFlow) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Flow", style = MaterialTheme.typography.labelMedium)
                            Text(text = flowString, fontSize = 36.sp, fontWeight = FontWeight.Light)
                        }
                    }
                }
                // NYTT: Visa statusmeddelande vid paus
                if (isPaused) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isPausedDueToDisconnect) {
                            Icon(Icons.Default.BluetoothSearching, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Paused - Reconnecting...", fontSize = 14.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                        } else {
                            Text("Paused", fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}


// --- BrewGraph (Inga ändringar behövs här) ---
@Composable
fun BrewGraph(
    samples: List<BrewSample>,
    modifier: Modifier = Modifier
) {
    // ... (samma kod som tidigare) ...
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
// --- SLUT BrewGraph ---


// --- BrewControls (UPPDATERAD) ---
@Composable
fun BrewControls(
    isRecording: Boolean,
    isPaused: Boolean,
    isPausedDueToDisconnect: Boolean, // NYTT STATE
    isConnected: Boolean,
    countdown: Int?,
    onStartClick: () -> Unit,
    onPauseClick: () -> Unit, // Manuell paus
    onResumeClick: () -> Unit,
    onTareClick: () -> Unit,
    onResetClick: () -> Unit // Behövs fortfarande för manuell reset
) {
    val isBusy = countdown != null
    // Återställningsknappen ska vara inaktiv om paus beror på disconnect
    val enableReset = (isRecording || isPaused) && !isBusy && !isPausedDueToDisconnect

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Återställ (Replay) knapp - Inaktiv om pausad pga disconnect
        IconButton(
            onClick = onResetClick,
            enabled = enableReset
        ) {
            Icon(
                imageVector = Icons.Default.Replay,
                contentDescription = "Reset recording",
                // Använd LocalContentColor för standardfärg när den är aktiv
                tint = if (enableReset) LocalContentColor.current else Color.Gray // Gråa ut om inaktiv
            )
        }

        // Huvudknapp (Start/Paus/Återuppta)
        Button(
            onClick = {
                when {
                    // Om pausad (oavsett orsak), försök återuppta
                    isPaused -> onResumeClick()
                    // Om inspelning pågår, pausa manuellt
                    isRecording -> onPauseClick()
                    // Annars, starta ny inspelning
                    else -> onStartClick()
                }
            },
            modifier = Modifier.size(72.dp),
            contentPadding = PaddingValues(0.dp),
            // Logik för att aktivera knappen:
            // - Inte under nedräkning
            // - Antingen:
            //   - Ansluten (för start/manuell paus/manuell resume)
            //   - ELLER Pausad pga disconnect (för att tillåta försök att återuppta när anslutningen återkommer)
            enabled = !isBusy && (isConnected || isPausedDueToDisconnect)
        ) {
            Icon(
                imageVector = when {
                    isBusy -> Icons.Default.Timer
                    // Visa Play om pausad (oavsett orsak)
                    isPaused -> Icons.Default.PlayArrow
                    isRecording -> Icons.Default.Pause
                    else -> Icons.Default.PlayArrow
                },
                contentDescription = when {
                    isBusy -> "Starting..."
                    // Ändra text om pausad pga disconnect
                    isPausedDueToDisconnect -> "Resume when connected"
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
            // Logik för att aktivera Tara:
            // - Måste vara ansluten
            // - Inte under nedräkning
            // - Inte under aktiv inspelning (såvida den inte är pausad, oavsett orsak)
            enabled = isConnected && !isBusy && (!isRecording || isPaused),
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
    val context = LocalContext.current
    val themeManager = remember { ThemePreferenceManager(context) }

    ProjektAndroidTheme(themePreferenceManager = themeManager) {
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
        var isPausedDc by remember { mutableStateOf(false) } // NYTT preview state
        var time by remember { mutableLongStateOf(0L) }
        var connectionState by remember { mutableStateOf<BleConnectionState>(BleConnectionState.Connected("Preview Scale", "00:11:22:33:FF:EE")) }
        var countdown by remember { mutableStateOf<Int?>(null) }

        val scope = rememberCoroutineScope()

        LaunchedEffect(isRec, isPaused) {
            while (isRec && !isPaused) {
                delay(100)
                time += 100
            }
        }

        val currentWeight = remember(time, isRec, isPaused) {
            // ... (samma logik som tidigare) ...
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

        // Simulera knapptryckningar
        val pauseAction = { isPaused = true; isPausedDc = false } // Manuell paus
        val pauseDcAction = { isPaused = true; isPausedDc = true } // Simulera disconnect-paus
        val resumeAction = { isPaused = false; isPausedDc = false }
        val stopSaveAction = { isRec = false; isPaused = false; isPausedDc = false; countdown = null }
        val resetAction = { isRec = false; isPaused = false; isPausedDc = false; time = 0L; countdown = null }

        LiveBrewScreen(
            samples = previewSamples.filter { it.timeMillis <= time },
            currentMeasurement = currentWeight,
            currentTimeMillis = time,
            isRecording = isRec,
            isPaused = isPaused,
            isPausedDueToDisconnect = isPausedDc, // Skicka med nya state
            weightAtPause = weightAtPausePreview,
            connectionState = connectionState,
            countdown = countdown,
            // FIX 2: Definiera lambda direkt i anropet för onStartClick
            onStartClick = {
                scope.launch {
                    countdown = 3; delay(1000)
                    countdown = 2; delay(1000)
                    countdown = 1; delay(1000)
                    countdown = null
                    isRec = true
                    isPaused = false
                    isPausedDc = false
                    time = 0L
                }
            },
            onPauseClick = pauseAction,
            onResumeClick = resumeAction,
            onStopAndSaveClick = stopSaveAction,
            onTareClick = { time = 0L },
            onNavigateBack = { Log.d("Preview", "Navigate Back") },
            // onResetRecording = resetAction, // Tas bort från anropet
            navigateTo = { screen -> Log.d("Preview", "Navigate to $screen") }
        )
    }
}