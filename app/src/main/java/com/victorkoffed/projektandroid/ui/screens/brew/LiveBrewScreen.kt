package com.victorkoffed.projektandroid.ui.screens.brew

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.victorkoffed.projektandroid.data.db.BrewSample
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.domain.model.ScaleMeasurement
import com.victorkoffed.projektandroid.ui.screens.brew.composable.BrewControls
import com.victorkoffed.projektandroid.ui.screens.brew.composable.LiveBrewGraph
import com.victorkoffed.projektandroid.ui.screens.brew.composable.StatusDisplay


/**
 * Huvudskärmen för att hantera live-bryggning med realtidsdata från vågen.
 * Orkestrerar layouten genom att använda utbrutna Composables för statusdisplay, graf och kontroller.
 *
 * @param samples De insamlade BrewSample-punkterna.
 * @param currentMeasurement Den senaste mätningen (vikt och flöde).
 * @param currentTimeMillis Tiden i millisekunder för den pågående bryggningen.
 * @param isRecording Anger om inspelning pågår.
 * @param isPaused Anger om inspelningen är pausad.
 * @param isPausedDueToDisconnect Anger om pausen beror på frånkoppling.
 * @param weightAtPause Vikten vid paus (används för att visa statisk vikt).
 * @param connectionState Aktuell Bluetooth-anslutningsstatus.
 * @param countdown Nedräkningsvärdet (om start håller på).
 * @param onStartClick Callback för att starta inspelningen.
 * @param onPauseClick Callback för att manuellt pausa inspelningen.
 * @param onResumeClick Callback för att återuppta inspelningen.
 * @param onStopAndSaveClick Callback för att stoppa och spara bryggningen.
 * @param onTareClick Callback för att tarera vågen.
 * @param onNavigateBack Callback för att navigera tillbaka.
 * @param onResetClick Callback för att manuellt återställa inspelningen. <--- NYTT HÄR
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveBrewScreen(
    samples: List<BrewSample>,
    currentMeasurement: ScaleMeasurement,
    currentTimeMillis: Long,
    isRecording: Boolean,
    isPaused: Boolean,
    isPausedDueToDisconnect: Boolean,
    weightAtPause: Float?,
    connectionState: BleConnectionState,
    countdown: Int?,
    onStartClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onStopAndSaveClick: () -> Unit,
    onTareClick: () -> Unit,
    onNavigateBack: () -> Unit,
    onResetClick: () -> Unit,
    navigateTo: (String) -> Unit
) {
    var showFlowInfo by remember { mutableStateOf(true) }
    var showDisconnectedAlert by remember { mutableStateOf(false) }
    var alertMessage by remember { mutableStateOf("The connection to the scale was lost.") }
    var alertTitle by remember { mutableStateOf("Connection Lost") }

    // Logik: Hantera dialog vid oväntad frånkoppling under inspelning/paus
    LaunchedEffect(connectionState, isRecording, isPaused, isPausedDueToDisconnect) {
        if ((connectionState is BleConnectionState.Disconnected || connectionState is BleConnectionState.Error) &&
            (isRecording || isPaused) &&
            !isPausedDueToDisconnect &&
            !isPaused
        ) {
            alertTitle = if (connectionState is BleConnectionState.Error) "Connection Error" else "Connection Lost"
            alertMessage = if (connectionState is BleConnectionState.Error) {
                connectionState.message + " Recording paused."
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
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = onStopAndSaveClick,
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
                onStartClick = onStartClick,
                onPauseClick = onPauseClick,
                onResumeClick = onResumeClick,
                onTareClick = onTareClick,
                onResetClick = onResetClick
            )
        }

        // Dialog vid anslutningsfel under pågående inspelning/paus
        if (showDisconnectedAlert) {
            AlertDialog(
                onDismissRequest = { showDisconnectedAlert = false },
                title = { Text(alertTitle) },
                text = { Text(alertMessage) },
                confirmButton = {
                    TextButton(onClick = { showDisconnectedAlert = false }) {
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