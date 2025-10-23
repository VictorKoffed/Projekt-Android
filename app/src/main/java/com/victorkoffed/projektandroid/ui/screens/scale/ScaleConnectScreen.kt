package com.victorkoffed.projektandroid.ui.screens.scale

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons // <-- NY IMPORT
import androidx.compose.material.icons.filled.ArrowBack // <-- NY IMPORT
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
                title = { Text("Anslut till våg") },
                // --- NY NAVIGATION ICON ---
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Tillbaka")
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
                else -> {
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
                        error = error,
                        onToggleScan = { if (isScanning) vm.stopScan() else requestPermissions() },
                        onDeviceClick = { vm.connect(it) }
                    )
                }
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

        Text("Vikt", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "%.1f g".format(measurement.weightGrams),
            fontSize = 72.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Button(onClick = onTare) {
            Text("Nollställ (Tare)")
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onDisconnect) {
            Text("Koppla från")
        }
    }
}

// ScanningView, DeviceList, och DeviceCard behöver inga ändringar
@Composable
private fun ScanningView(
    devices: List<DiscoveredDevice>,
    isScanning: Boolean,
    connectionState: BleConnectionState,
    error: String?,
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
            connectionState = connectionState,
            onToggleScan = onToggleScan
        )

        error?.let {
            Text(
                text = "Fel: $it",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Divider()
        DeviceList(devices, isScanning, onDeviceClick)
    }
}

@Composable
private fun ScanControls(isScanning: Boolean, connectionState: BleConnectionState, onToggleScan: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = onToggleScan, enabled = connectionState == BleConnectionState.Disconnected) {
            when {
                connectionState is BleConnectionState.Connecting -> {
                    CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Ansluter...")
                }
                isScanning -> {
                    CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Stoppa skanning")
                }
                else -> Text("Starta skanning")
            }
        }
    }
}

@Composable
private fun DeviceList(devices: List<DiscoveredDevice>, isScanning: Boolean, onDeviceClick: (DiscoveredDevice) -> Unit) {
    if (isScanning && devices.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Letar efter enheter...")
        }
    } else if (!isScanning && devices.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Inga enheter hittades.")
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
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = device.name ?: "(Namnlös enhet)",
                style = MaterialTheme.typography.titleMedium
            )
            Text(text = "Adress: ${device.address}", style = MaterialTheme.typography.bodySmall)
            Text(text = "Signalstyrka: ${device.rssi} dBm", style = MaterialTheme.typography.bodySmall)
        }
    }
}