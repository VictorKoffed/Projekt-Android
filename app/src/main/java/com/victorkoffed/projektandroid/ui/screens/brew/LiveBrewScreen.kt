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
    scaleVm: ScaleViewModel, // MOTTAGARE: Tar emot scaleVm
    brewVm: BrewViewModel // <-- INGEN default hiltViewModel() HÄR
) {
    // === Hämta sessions-state från BrewViewModel ===
    val samples by brewVm.recordedSamplesFlow.collectAsState()
    val time by brewVm.recordingTimeMillis.collectAsState()
    val isRecording by brewVm.isRecording.collectAsState()
    val isPaused by brewVm.isPaused.collectAsState()
    val isRecordingWhileDisconnected by brewVm.isRecordingWhileDisconnected.collectAsState()
    val weightAtPause by brewVm.weightAtPause.collectAsState()
    val countdown by brewVm.countdown.collectAsState()

    // === Hämta globalt anslutnings/data-state från ScaleViewModel ===
    val currentMeasurement by scaleVm.measurement.collectAsState()
    val connectionState by scaleVm.connectionState.collectAsState(
        initial = scaleVm.connectionState.replayCache.lastOrNull() ?: BleConnectionState.Disconnected
    )

    val scope = rememberCoroutineScope()

    var showFlowInfo by remember { mutableStateOf(true) }
    var showDisconnectedAlert by remember { mutableStateOf(false) }
    var alertMessage by remember { mutableStateOf("The connection to the scale was lost.") }
    var alertTitle by remember { mutableStateOf("Connection Lost") }

    // Logik för att stoppa och spara
    val onStopAndSaveClick: () -> Unit = {
        scope.launch {
            Log.d("LiveBrewScreen_DEBUG", "onStopAndSaveClick: Klickade 'Done'. Försöker spara.")
            val currentSetup = brewVm.getCurrentSetup()
            // Hämta data från brewVm
            val finalSamples = brewVm.recordedSamplesFlow.value
            val finalTime = brewVm.recordingTimeMillis.value

            // Stoppa inspelningen (via brewVm)
            brewVm.stopRecording()

            Log.d("LiveBrewScreen_DEBUG", "onStopAndSaveClick: Setup BeanId: ${currentSetup.selectedBean?.id}, Dose: ${currentSetup.doseGrams.value}")
            Log.d("LiveBrewScreen_DEBUG", "onStopAndSaveClick: Insamlat - Samples: ${finalSamples.size}, Tid: ${finalTime}ms. Första t_ms: ${finalSamples.firstOrNull()?.timeMillis ?: -1L}")

            val scaleName = (connectionState as? BleConnectionState.Connected)?.deviceName
            val saveResult = brewVm.saveLiveBrew(
                setupState = currentSetup,
                finalSamples = finalSamples,
                finalTimeMillis = finalTime,
                scaleDeviceName = scaleName
            )

            Log.d("LiveBrewScreen_DEBUG", "onStopAndSaveClick: Resultat mottaget. BrewId: ${saveResult.brewId}, BeanIdReachedZero: ${saveResult.beanIdReachedZero}")

            if (saveResult.brewId != null) {
                Log.d("LiveBrewScreen_DEBUG", "onStopAndSaveClick: Spar LYCKADES. Navigerar till detaljvy.")
                onNavigateToDetail(saveResult.brewId, saveResult.beanIdReachedZero)
            } else {
                Log.w("LiveBrewScreen_DEBUG", "onStopAndSaveClick: Spar MISSLYCKADES. Återgår till setup.")
                onNavigateBack()
            }
        }
    }

    // Logik: Hantera dialog vid oväntad frånkoppling under inspelning/paus
    // Denna logik är densamma, men den använder nu states från brewVm och scaleVm
    LaunchedEffect(connectionState, isRecording, isPaused, isRecordingWhileDisconnected) {
        if ((connectionState is BleConnectionState.Disconnected || connectionState is BleConnectionState.Error) &&
            (isRecording) &&
            !isPaused &&
            isRecordingWhileDisconnected
        ) {
            alertTitle = if (connectionState is BleConnectionState.Error) "Connection Error" else "Connection Lost"
            alertMessage = if (connectionState is BleConnectionState.Error) {
                (connectionState as BleConnectionState.Error).message + " Recording continues..."
            } else {
                "The connection to the scale was lost. Recording continues..."
            }
            showDisconnectedAlert = true
        } else if (connectionState is BleConnectionState.Connected && !isRecordingWhileDisconnected) {
            // Dölj dialogen om vi återansluter
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
                        onClick = onStopAndSaveClick,
                        enabled = isRecording || isPaused || countdown != null // Använder brewVm states
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
                currentTimeMillis = time, // från brewVm
                // Använder currentMeasurement från scaleVm
                currentMeasurement = if (isRecordingWhileDisconnected || isPaused) ScaleMeasurement(weightAtPause ?: 0f, 0f) else currentMeasurement,
                isRecording = isRecording, // från brewVm
                isPaused = isPaused, // från brewVm
                isRecordingWhileDisconnected = isRecordingWhileDisconnected, // från brewVm
                showFlow = showFlowInfo,
                countdown = countdown // från brewVm
            )
            Spacer(Modifier.height(16.dp))
            LiveBrewGraph(
                samples = samples, // från brewVm
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
                isRecording = isRecording, // från brewVm
                isPaused = isPaused, // från brewVm
                isRecordingWhileDisconnected = isRecordingWhileDisconnected, // från brewVm
                isConnected = connectionState is BleConnectionState.Connected, // från scaleVm
                countdown = countdown, // från brewVm
                onStartClick = { brewVm.startRecording() },
                onPauseClick = { brewVm.pauseRecording() },
                onResumeClick = { brewVm.resumeRecording() },
                onTareClick = { brewVm.tareScale() }, // Anropar brewVm
                onResetClick = { brewVm.stopRecording() }
            )
        }

        if (showDisconnectedAlert) {
            AlertDialog(
                onDismissRequest = { /* Låt den vara kvar */ },
                title = { Text(alertTitle) },
                text = { Text(alertMessage) },
                confirmButton = {
                    TextButton(onClick = {
                        showDisconnectedAlert = false
                    }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDisconnectedAlert = false
                        onStopAndSaveClick()
                    }) {
                        Text("Stop & Save As Is")
                    }
                }
            )
        }
    }
}