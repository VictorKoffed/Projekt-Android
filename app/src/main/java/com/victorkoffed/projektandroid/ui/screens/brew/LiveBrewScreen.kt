package com.victorkoffed.projektandroid.ui.screens.brew

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.domain.model.ScaleMeasurement
import com.victorkoffed.projektandroid.ui.screens.brew.composable.BrewControls
import com.victorkoffed.projektandroid.ui.screens.brew.composable.LiveBrewGraph
import com.victorkoffed.projektandroid.ui.screens.brew.composable.StatusDisplay
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.scale.ScaleViewModel
import kotlinx.coroutines.launch


/**
 * Huvudskärmen för att hantera live-bryggning med realtidsdata från vågen.
 * Orkestrerar layouten genom att använda utbrutna Composables för statusdisplay, graf och kontroller.
 *
 * @param onNavigateBack Callback för att navigera tillbaka.
 * @param onNavigateToDetail Callback för att navigera till detaljskärmen efter sparande.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveBrewScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (brewId: Long, beanIdToArchivePrompt: Long?) -> Unit,
    scaleVm: ScaleViewModel, // MOTTAGARE: Ta emot scaleVm
    brewVm: BrewViewModel = hiltViewModel()
) {
    // Hämta alla nödvändiga states från ScaleViewModel lokalt
    val samples by scaleVm.recordedSamplesFlow.collectAsState()
    val time by scaleVm.recordingTimeMillis.collectAsState()
    val isRecording by scaleVm.isRecording.collectAsState()
    val isPaused by scaleVm.isPaused.collectAsState()
    val isPausedDueToDisconnect by scaleVm.isPausedDueToDisconnect.collectAsState()
    val currentMeasurement by scaleVm.measurement.collectAsState()
    val weightAtPause by scaleVm.weightAtPause.collectAsState()
    val countdown by scaleVm.countdown.collectAsState()
    val connectionState by scaleVm.connectionState.collectAsState(
        initial = scaleVm.connectionState.replayCache.lastOrNull() ?: BleConnectionState.Disconnected
    )

    val scope = rememberCoroutineScope()

    var showFlowInfo by remember { mutableStateOf(true) }
    var showDisconnectedAlert by remember { mutableStateOf(false) }
    var alertMessage by remember { mutableStateOf("The connection to the scale was lost.") }
    var alertTitle by remember { mutableStateOf("Connection Lost") }

    // Logik för att stoppa och spara, nu flyttad hit
    val onStopAndSaveClick: () -> Unit = {
        scope.launch {
            // 1. Hämta setup-datan från BrewViewModel
            val currentSetup = brewVm.getCurrentSetup()

            // 2. Stoppa inspelningen på vågen (detta nollställer *inte* samples/time i VM)
            scaleVm.stopRecording()

            // 3. Hämta de slutgiltiga inspelade värdena
            val finalSamples = scaleVm.recordedSamplesFlow.value
            val finalTime = scaleVm.recordingTimeMillis.value

            // 4. Hämta vågens namn (om ansluten) för anteckningar
            val scaleName = (connectionState as? BleConnectionState.Connected)?.deviceName

            // 5. Anropa den *nya* spara-funktionen på BrewViewModel
            val saveResult = brewVm.saveLiveBrew(
                setupState = currentSetup,
                finalSamples = finalSamples,
                finalTimeMillis = finalTime,
                scaleDeviceName = scaleName
            )

            if (saveResult.brewId != null) {
                // SUCCESS: Använd nav-callback
                onNavigateToDetail(saveResult.brewId, saveResult.beanIdReachedZero)
            } else {
                // FAILURE FIX: Återgå till Brew Setup om sparandet misslyckas.
                Log.w("LiveBrewScreen", "Save cancelled or failed, returning to setup.")
                onNavigateBack()
            }
        }
    }

    // Logik: Hantera dialog vid oväntad frånkoppling under inspelning/paus
    LaunchedEffect(connectionState, isRecording, isPaused, isPausedDueToDisconnect) {
        if ((connectionState is BleConnectionState.Disconnected || connectionState is BleConnectionState.Error) &&
            (isRecording || isPaused) &&
            !isPausedDueToDisconnect && // Se till att den inte redan är pausad pga disconnect
            !isPaused // Se till att den inte redan är manuellt pausad
        ) {
            alertTitle = if (connectionState is BleConnectionState.Error) "Connection Error" else "Connection Lost"
            alertMessage = if (connectionState is BleConnectionState.Error) {
                (connectionState as BleConnectionState.Error).message + " Recording paused."
            } else {
                "The connection to the scale was lost. Recording paused."
            }
            showDisconnectedAlert = true
        } else if (connectionState is BleConnectionState.Connected && isPausedDueToDisconnect) {
            showDisconnectedAlert = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Brew") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = onStopAndSaveClick, // Använd den lokalt definierade logiken
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
                currentTimeMillis = time,
                currentMeasurement = if (isPaused) ScaleMeasurement(weightAtPause ?: 0f, 0f) else currentMeasurement,
                isRecording = isRecording,
                isPaused = isPaused,
                isPausedDueToDisconnect = isPausedDueToDisconnect,
                showFlow = showFlowInfo,
                countdown = countdown
            )
            Spacer(Modifier.height(16.dp))
            LiveBrewGraph(
                samples = samples,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp)
            )
            Spacer(Modifier.height(8.dp))
            // Filterchip för Flow (behålls här)
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
                isPausedDueToDisconnect = isPausedDueToDisconnect,
                isConnected = connectionState is BleConnectionState.Connected,
                countdown = countdown,
                onStartClick = { scaleVm.startRecording() },
                onPauseClick = { scaleVm.pauseRecording() },
                onResumeClick = { scaleVm.resumeRecording() },
                onTareClick = { scaleVm.tareScale() },
                onResetClick = { scaleVm.stopRecording() } // Skickar vidare anropet
            )
        }

        // Dialog vid anslutningsfel under pågående inspelning/paus
        if (showDisconnectedAlert) {
            AlertDialog(
                onDismissRequest = { /* Låt den vara kvar */ },
                title = { Text(alertTitle) },
                text = { Text(alertMessage) },
                confirmButton = {
                    TextButton(onClick = {
                        // FIX: Stänger dialogen
                    }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        // Stäng dialogen och spara
                        onStopAndSaveClick()
                        // FIX: Måste stänga dialogen
                    }) {
                        Text("Stop & Save As Is")
                    }
                }
            )
        }
    }
}