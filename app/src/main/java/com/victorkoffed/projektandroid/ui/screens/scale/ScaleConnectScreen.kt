package com.victorkoffed.projektandroid.ui.screens.scale

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.domain.model.DiscoveredDevice
import com.victorkoffed.projektandroid.domain.model.ScaleMeasurement
import com.victorkoffed.projektandroid.ui.permission.rememberBluetoothPermissionLauncher
import com.victorkoffed.projektandroid.ui.viewmodel.scale.ScaleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaleConnectScreen(
    vm: ScaleViewModel,
    onNavigateBack: () -> Unit // <-- NY PARAMETER
) {
    val connectionState by vm.connectionState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect to Scale") },
                // --- NY NAVIGATION ICON ---
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
                // --- SLUT ---
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = connectionState,
            modifier = Modifier.padding(padding),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "connectionStateAnimation"
        ) { state ->
            when (state) {
                is BleConnectionState.Connected -> {
                    val measurement by vm.measurement.collectAsState(initial = ScaleMeasurement(0f, 0f)) // Lägg till initialValue
                    ConnectedView(
                        deviceName = state.deviceName,
                        measurement = measurement,
                        onDisconnect = { vm.disconnect() },
                        onTare = { vm.tareScale() }
                    )
                }
                // --- ÄNDRING: Hanterar nu Disconnected, Connecting och Error här ---
                else -> { // Handles Disconnected, Connecting, Error
                    val devices by vm.devices.collectAsState()
                    val isScanning by vm.isScanning.collectAsState()
                    val error by vm.error.collectAsState() // Detta state används inte längre direkt för att visa fel, men kan vara bra för loggning
                    val requestPermissions = rememberBluetoothPermissionLauncher { granted ->
                        if (granted) vm.startScan()
                    }
                    ScanningView(
                        devices = devices,
                        isScanning = isScanning,
                        connectionState = state, // Skicka med hela state (Disconnected, Connecting eller Error)
                        // Om state är Error, använd dess meddelande, annars null
                        error = (state as? BleConnectionState.Error)?.message,
                        onToggleScan = { if (isScanning) vm.stopScan() else requestPermissions() },
                        onDeviceClick = {
                            // --- NYTT: Tillåt bara connect om state är Disconnected eller Error ---
                            if (state is BleConnectionState.Disconnected || state is BleConnectionState.Error) {
                                vm.connect(it)
                            }
                        }
                    )
                }
                // --- SLUT ÄNDRING ---
            }
        }
    }
}


@Composable
private fun ConnectedView(
    deviceName: String,
    measurement: ScaleMeasurement,
    onDisconnect: () -> Unit,
    onTare: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Ansluten till:", style = MaterialTheme.typography.titleMedium)
        Text(deviceName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))

        Text("Weight", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "%.1f g".format(measurement.weightGrams),
            fontSize = 72.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Button(onClick = onTare) {
            Text("Tare")
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onDisconnect) {
            Text("Disconnect")
        }
    }
}

// ScanningView, DeviceList, och DeviceCard behöver inga ändringar
@Composable
private fun ScanningView(
    devices: List<DiscoveredDevice>,
    isScanning: Boolean,
    connectionState: BleConnectionState, // Tar nu emot hela state (Disconnected, Connecting, Error)
    error: String?, // Felmeddelande (kan vara null)
    onToggleScan: () -> Unit,
    onDeviceClick: (DiscoveredDevice) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScanControls(
            isScanning = isScanning,
            connectionState = connectionState, // Skicka med hela state
            onToggleScan = onToggleScan
        )

        // Visa felmeddelande om det finns (från connectionState.Error)
        error?.let {
            Text(
                text = "Error: $it",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Divider()
        // --- ÄNDRING: Skicka med connectionState till DeviceList ---
        DeviceList(devices, isScanning, connectionState, onDeviceClick)
        // --- SLUT ÄNDRING ---
    }
}

@Composable
private fun ScanControls(isScanning: Boolean, connectionState: BleConnectionState, onToggleScan: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // --- ÄNDRING: Uppdaterat enabled-villkor ---
        Button(
            onClick = onToggleScan,
            // Aktivera om state är Disconnected ELLER Error
            enabled = connectionState is BleConnectionState.Disconnected || connectionState is BleConnectionState.Error
        ) {
            // --- SLUT ÄNDRING ---
            when {
                connectionState is BleConnectionState.Connecting -> {
                    CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Ansluter...")
                }
                isScanning -> {
                    CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Stop scanning")
                }
                // Visa "Starta skanning" för både Disconnected och Error
                else -> Text("Start scanning")
            }
        }
    }
}

// --- ÄNDRING: Lade till connectionState som parameter ---
@Composable
private fun DeviceList(
    devices: List<DiscoveredDevice>,
    isScanning: Boolean,
    connectionState: BleConnectionState, // <-- NY PARAMETER
    onDeviceClick: (DiscoveredDevice) -> Unit
) {
// --- SLUT ÄNDRING ---
    if (isScanning && devices.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Scanning for devices...")
        }
        // --- ÄNDRING: Använder den nya parametern här ---
    } else if (!isScanning && devices.isEmpty() && connectionState !is BleConnectionState.Error) { // Visa bara om inget fel visas
        // --- SLUT ÄNDRING ---
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No devices found.")
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items = devices, key = { it.address }) { device ->
                DeviceCard(device = device, onClick = { onDeviceClick(device) })
            }
        }
    }
}

@Composable
private fun DeviceCard(device: DiscoveredDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick) // Klickbarheten är kvar
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = device.name ?: "(Nameless device)",
                style = MaterialTheme.typography.titleMedium
            )
            Text(text = "Adress: ${device.address}", style = MaterialTheme.typography.bodySmall)
            Text(text = "Signalstyrka: ${device.rssi} dBm", style = MaterialTheme.typography.bodySmall)
        }
    }
}