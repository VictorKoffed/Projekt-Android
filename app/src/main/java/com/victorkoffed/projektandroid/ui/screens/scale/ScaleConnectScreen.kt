package com.victorkoffed.projektandroid.ui.screens.scale

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

/**
 * Screen presenting Bluetooth Low Energy (BLE) peripheral discovery and connection management.
 * Dynamically switches UI contracts between scanning lists and active scale telemetry views
 * based on the underlying connection state machine.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaleConnectScreen(
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    vm: ScaleViewModel
) {
    val connectionState by vm.connectionState.collectAsState(initial = vm.connectionState.replayCache.lastOrNull() ?: BleConnectionState.Disconnected)
    val error by vm.error.collectAsState()
    val rememberScaleEnabled by vm.rememberScaleEnabled.collectAsState()
    val autoConnectEnabled by vm.autoConnectEnabled.collectAsState()
    val rememberedAddress by vm.rememberedScaleAddress.collectAsState()

    LaunchedEffect(error) {
        val msg = error ?: return@LaunchedEffect

        snackbarHostState.showSnackbar(
            message = msg,
            duration = SnackbarDuration.Long
        )
        vm.clearError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect to Scale") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
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

                    ConnectedView(
                        deviceName = state.deviceName,
                        measurement = measurement,
                        rememberScale = rememberScaleEnabled,
                        autoConnect = autoConnectEnabled,
                        onRememberScaleChange = vm::setRememberScaleEnabled,
                        onAutoConnectChange = vm::setAutoConnectEnabled,
                        onDisconnect = { vm.disconnect() },
                        onTare = { vm.tareScale() }
                    )
                }
                else -> {
                    val devices by vm.devices.collectAsState()
                    val isScanning by vm.isScanning.collectAsState()

                    val requestPermissions = rememberBluetoothPermissionLauncher { granted ->
                        if (granted) vm.startScan()
                    }

                    ScanningView(
                        devices = devices,
                        isScanning = isScanning,
                        connectionState = state,
                        rememberedAddress = rememberedAddress,
                        onToggleScan = { if (isScanning) vm.stopScan() else requestPermissions() },
                        onDeviceClick = { device ->
                            if (state is BleConnectionState.Disconnected || state is BleConnectionState.Error) {
                                vm.connect(device)
                            }
                        },
                        onForgetScaleClick = { vm.forgetRememberedScale() }
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
    rememberScale: Boolean,
    autoConnect: Boolean,
    onRememberScaleChange: (Boolean) -> Unit,
    onAutoConnectChange: (Boolean) -> Unit,
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
        Text("Connected to:", style = MaterialTheme.typography.titleMedium)
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(enabled = rememberScale) {
                if (rememberScale) {
                    onAutoConnectChange(!autoConnect)
                }
            }
        ) {
            Checkbox(
                checked = autoConnect,
                onCheckedChange = onAutoConnectChange,
                enabled = rememberScale
            )
            Text(
                text ="Auto-connect when available",
                color = if (rememberScale) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
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

@Composable
private fun ScanningView(
    devices: List<DiscoveredDevice>,
    isScanning: Boolean,
    connectionState: BleConnectionState,
    rememberedAddress: String?,
    onToggleScan: () -> Unit,
    onDeviceClick: (DiscoveredDevice) -> Unit,
    onForgetScaleClick: () -> Unit
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

        if (rememberedAddress != null) {
            ForgetScaleRow(
                address = rememberedAddress,
                onClick = onForgetScaleClick
            )
        }

        HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

        DeviceList(devices, isScanning, connectionState, onDeviceClick)
    }
}

@Composable
private fun ScanControls(isScanning: Boolean, connectionState: BleConnectionState, onToggleScan: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
                    Text("Connecting...")
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

@Composable
private fun DeviceList(
    devices: List<DiscoveredDevice>,
    isScanning: Boolean,
    connectionState: BleConnectionState,
    onDeviceClick: (DiscoveredDevice) -> Unit
) {
    if (isScanning && devices.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(top = 16.dp), contentAlignment = Alignment.TopCenter) {
            Text("Scanning for devices...")
        }
    } else if (!isScanning && devices.isEmpty() && connectionState is BleConnectionState.Disconnected) {
        Box(modifier = Modifier.fillMaxSize().padding(top = 16.dp), contentAlignment = Alignment.TopCenter) {
            Text("No devices found. Tap 'Start scanning' to search again.")
        }
    } else if (connectionState is BleConnectionState.Error && devices.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(top = 16.dp), contentAlignment = Alignment.TopCenter) {
            Text(connectionState.message)
        }
    }
    else {
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
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = device.name ?: "(Unknown Device)",
                style = MaterialTheme.typography.titleMedium
            )
            Text(text = "Address: ${device.address}", style = MaterialTheme.typography.bodySmall)
            Text(text = "Signal strength: ${device.rssi} dBm", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ForgetScaleRow(
    address: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Remembered scale:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = address,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
        TextButton(onClick = onClick) {
            Text("Forget")
        }
    }
}