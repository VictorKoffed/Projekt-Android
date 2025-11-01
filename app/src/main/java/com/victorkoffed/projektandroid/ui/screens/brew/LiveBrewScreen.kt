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
import com.victorkoffed.projektandroid.ui.viewmodel.brew.LiveBrewViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.scale.ScaleViewModel
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveBrewScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (brewId: Long, beanIdToArchivePrompt: Long?) -> Unit,
    scaleVm: ScaleViewModel,
    vm: LiveBrewViewModel
) {
    // === Hämta sessions-state från LiveBrewViewModel ===
    val samples by vm.recordedSamplesFlow.collectAsState()
    val time by vm.recordingTimeMillis.collectAsState()
    val isRecording by vm.isRecording.collectAsState()
    val isPaused by vm.isPaused.collectAsState()
    val isRecordingWhileDisconnected by vm.isRecordingWhileDisconnected.collectAsState()
    val weightAtPause by vm.weightAtPause.collectAsState()
    val countdown by vm.countdown.collectAsState()
    val error by vm.error.collectAsState()

    // === Hämta globalt anslutnings/data-state från ScaleViewModel ===
    val liveMeasurement by scaleVm.measurement.collectAsState()
    val connectionState by scaleVm.connectionState.collectAsState(
        initial = scaleVm.connectionState.replayCache.lastOrNull() ?: BleConnectionState.Disconnected
    )

    val scope = rememberCoroutineScope()

    var showFlowInfo by remember { mutableStateOf(true) }
    var showDisconnectedAlert by remember { mutableStateOf(false) }
    var alertMessage by remember { mutableStateOf("The connection to the scale was lost.") }
    var alertTitle by remember { mutableStateOf("Connection Lost") }

    // Visa fel från ViewModel (t.ex. om setup-data saknas)
    LaunchedEffect(error) {
        if (error != null) {
            alertTitle = "Error"
            alertMessage = error!!
            showDisconnectedAlert = true // Återanvänder dialogen för fel
            vm.clearError()
        }
    }


    // Logik för att stoppa och spara
    val onStopAndSaveClick: () -> Unit = {
        scope.launch {
            Log.d("LiveBrewScreen_DEBUG", "onStopAndSaveClick: Klickade 'Done'. Försöker spara.")

            // Hämta data från vm
            val finalSamples = vm.recordedSamplesFlow.value
            val finalTime = vm.recordingTimeMillis.value

            // Stoppa inspelningen
            vm.stopRecording()

            Log.d("LiveBrewScreen_DEBUG", "onStopAndSaveClick: Insamlat - Samples: ${finalSamples.size}, Tid: ${finalTime}ms.")

            val scaleName = (connectionState as? BleConnectionState.Connected)?.deviceName
            val saveResult = vm.saveLiveBrew(
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

    // Logik: Hantera dialog vid oväntad frånkoppling
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
            showDisconnectedAlert = false
        }
    }

    // Bestäm vad som faktiskt ska visas
    val displayMeasurement = remember(liveMeasurement, weightAtPause, isPaused, isRecordingWhileDisconnected, isRecording) {
        val lastKnownWeight = weightAtPause ?: 0f

        when {
            // 1. Om vi är pausade eller frånkopplade, visa den frysta vikten.
            isRecordingWhileDisconnected || isPaused -> {
                ScaleMeasurement(lastKnownWeight, 0f)
            }
            // 2. Om vi precis återupptagit inspelningen (isRecording=true) OCH
            //    den nya live-vikten (0g) är LÄGRE än den pausade (200g),
            //    fortsätt visa den pausade vikten (200g) tills vågen rapporterar ett högre värde.
            isRecording && liveMeasurement.weightGrams < lastKnownWeight -> {
                ScaleMeasurement(lastKnownWeight, liveMeasurement.flowRateGramsPerSecond)
            }
            // 3. Annars (t.ex. live-vikten är > 200g), visa den nya live-vikten.
            else -> {
                liveMeasurement
            }
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
                        enabled = (isRecording || isPaused || countdown != null) && error == null
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
                currentMeasurement = displayMeasurement,
                isRecording = isRecording,
                isPaused = isPaused,
                isRecordingWhileDisconnected = isRecordingWhileDisconnected,
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
                isRecordingWhileDisconnected = isRecordingWhileDisconnected,
                isConnected = connectionState is BleConnectionState.Connected,
                countdown = countdown,
                onStartClick = { vm.startRecording() },
                onPauseClick = { vm.pauseRecording() },
                onResumeClick = { vm.resumeRecording() },
                onTareClick = { vm.tareScale() },
                onResetClick = { vm.stopRecording() }
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
                        // Om felet var pga setup-data, navigera tillbaka
                        if (error != null && error!!.contains("setup")) {
                            onNavigateBack()
                        }
                    }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    // Visa bara "Stop & Save" om det inte är ett setup-fel
                    if (error == null) {
                        TextButton(onClick = {
                            showDisconnectedAlert = false
                            onStopAndSaveClick()
                        }) {
                            Text("Stop & Save As Is")
                        }
                    }
                }
            )
        }
    }
}