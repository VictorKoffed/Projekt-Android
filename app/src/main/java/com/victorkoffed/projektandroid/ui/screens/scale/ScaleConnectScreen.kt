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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.domain.model.DiscoveredDevice
import com.victorkoffed.projektandroid.domain.model.ScaleMeasurement
import com.victorkoffed.projektandroid.ui.permission.rememberBluetoothPermissionLauncher
import com.victorkoffed.projektandroid.ui.viewmodel.scale.ScaleViewModel
import kotlinx.coroutines.flow.firstOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaleConnectScreen(
    vm: ScaleViewModel,
    onNavigateBack: () -> Unit
) {
    val connectionState by vm.connectionState.collectAsState(initial = vm.connectionState.replayCache.lastOrNull() ?: BleConnectionState.Disconnected)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect to Scale") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
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
                    val measurement by vm.measurement.collectAsState(initial = ScaleMeasurement(0f, 0f))
                    // Hämta remember-status direkt från VM
                    val rememberScaleEnabled = vm.isRememberScaleEnabled()

                    ConnectedView(
                        deviceName = state.deviceName,
                        measurement = measurement,
                        // Skicka med värden till ConnectedView
                        rememberScale = rememberScaleEnabled,
                        onRememberScaleChange = { vm.setRememberScaleEnabled(it) },
                        onDisconnect = { vm.disconnect() },
                        onTare = { vm.tareScale() }
                    )
                }
                else -> { // Handles Disconnected, Connecting, Error
                    val devices by vm.devices.collectAsState()
                    val isScanning by vm.isScanning.collectAsState()
                    val error by vm.error.collectAsState()
                    val requestPermissions = rememberBluetoothPermissionLauncher { granted ->
                        if (granted) vm.startScan()
                    }
                    ScanningView(
                        devices = devices,
                        isScanning = isScanning,
                        connectionState = state,
                        error = (state as? BleConnectionState.Error)?.message,
                        onToggleScan = { if (isScanning) vm.stopScan() else requestPermissions() },
                        // Använd explicit namn istället för 'it'
                        onDeviceClick = { device -> // Explicit namn 'device'
                            if (state is BleConnectionState.Disconnected || state is BleConnectionState.Error) {
                                vm.connect(device) // Använd 'device' här
                            }
                        }
                    )
                }
            }
        }
    }
}


// --- ConnectedView (oförändrad från förra svaret) ---
@Composable
private fun ConnectedView(
    deviceName: String,
    measurement: ScaleMeasurement,
    rememberScale: Boolean,
    onRememberScaleChange: (Boolean) -> Unit,
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
        Spacer(Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onRememberScaleChange(!rememberScale) }
        ) {
            Checkbox(
                checked = rememberScale,
                onCheckedChange = onRememberScaleChange
            )
            Text("Remember this scale")
        }
        Spacer(Modifier.height(16.dp))

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


// --- ScanningView definitionen behöver sina parametrar ---
@Composable
private fun ScanningView(
    devices: List<DiscoveredDevice>, // <--- Lades till
    isScanning: Boolean,             // <--- Lades till
    connectionState: BleConnectionState, // <--- Lades till
    error: String?,                  // <--- Lades till
    onToggleScan: () -> Unit,        // <--- Lades till
    onDeviceClick: (DiscoveredDevice) -> Unit // <--- Lades till
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

        error?.let {
            Text(
                text = "Error: $it",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Divider()
        DeviceList(devices, isScanning, connectionState, onDeviceClick) // Skicka vidare
    }
}


// --- ScanControls (oförändrad) ---
@Composable
private fun ScanControls(isScanning: Boolean, connectionState: BleConnectionState, onToggleScan: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onToggleScan,
            enabled = connectionState is BleConnectionState.Disconnected || connectionState is BleConnectionState.Error
        ) {
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
                else -> Text("Start scanning")
            }
        }
    }
}

// --- DeviceList (oförändrad) ---
@Composable
private fun DeviceList(
    devices: List<DiscoveredDevice>,
    isScanning: Boolean,
    connectionState: BleConnectionState,
    onDeviceClick: (DiscoveredDevice) -> Unit
) {
    if (isScanning && devices.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Scanning for devices...")
        }
    } else if (!isScanning && devices.isEmpty() && connectionState !is BleConnectionState.Error) {
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

// --- DeviceCard (oförändrad) ---
@Composable
private fun DeviceCard(device: DiscoveredDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
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