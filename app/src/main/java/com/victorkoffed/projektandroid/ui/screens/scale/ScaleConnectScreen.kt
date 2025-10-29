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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victorkoffed.projektandroid.ThemedSnackbar
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.domain.model.DiscoveredDevice
import com.victorkoffed.projektandroid.domain.model.ScaleMeasurement
import com.victorkoffed.projektandroid.ui.permission.rememberBluetoothPermissionLauncher
import com.victorkoffed.projektandroid.ui.viewmodel.scale.ScaleViewModel

/**
 * Huvudskärm för att hantera anslutning till Bluetooth-vågen.
 * UI-läget växlar baserat på 'connectionState' (Ansluten, Scanning, Frånkopplad, Fel).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaleConnectScreen(
    vm: ScaleViewModel,
    onNavigateBack: () -> Unit
) {
    // Hämta aktuell anslutningsstatus (med fallback till senaste värde)
    val connectionState by vm.connectionState.collectAsState(initial = vm.connectionState.replayCache.lastOrNull() ?: BleConnectionState.Disconnected)
    // Hämta felmeddelanden från ViewModel (för skanning/sparande)
    val error by vm.error.collectAsState()
    // --- Hämta Remember & Auto-Connect State ---
    val rememberScaleEnabled by vm.rememberScaleEnabled.collectAsState()
    val autoConnectEnabled by vm.autoConnectEnabled.collectAsState()
    val rememberedAddress by vm.rememberedScaleAddress.collectAsState()


    // --- Snackbar state för felmeddelanden ---
    val snackbarHostState = remember { SnackbarHostState() }
    // OBS: Ingen 'rememberCoroutineScope()' behövs längre då LaunchedEffect är suspenderande.

    // Effekt för att visa (skanning/sparande) felmeddelanden i en Snackbar
    LaunchedEffect(error) {
        // Skapa ett lokalt, stabilt värde. Returnera om null (förhindrar krasch).
        val msg = error ?: return@LaunchedEffect

        snackbarHostState.showSnackbar(
            message = msg,
            duration = SnackbarDuration.Long
        )
        // Nollställ felet i ViewModel så det inte visas igen
        vm.clearError()
    }

    // Effekt för att visa anslutningsfel i en Snackbar
    LaunchedEffect(connectionState) {
        (connectionState as? BleConnectionState.Error)?.let {
            snackbarHostState.showSnackbar(
                message = it.message,
                duration = SnackbarDuration.Long
            )
        }
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
        // Använder en anpassad Snackbar-komponent för tematisk design
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { snackbarData ->
                    ThemedSnackbar(snackbarData)
                }
            )
        }
    ) { padding ->
        // Använder AnimatedContent för en smidig övergång mellan anslutna/frånkopplade vyer
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
                else -> { // Hanterar Disconnected, Connecting och Error
                    val devices by vm.devices.collectAsState()
                    val isScanning by vm.isScanning.collectAsState()

                    // Launcher för att begära Bluetooth- och platstillstånd vid start av scanning
                    val requestPermissions = rememberBluetoothPermissionLauncher { granted ->
                        if (granted) vm.startScan() // Starta scanning om tillstånd ges
                    }

                    ScanningView(
                        devices = devices,
                        isScanning = isScanning,
                        connectionState = state,
                        rememberedAddress = rememberedAddress,
                        onToggleScan = { if (isScanning) vm.stopScan() else requestPermissions() },
                        onDeviceClick = { device ->
                            // Tillåt anslutning endast om vågen är frånkopplad eller i fel-läge
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


// --- ConnectedView ---
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

        // Kontroll för "Kom ihåg våg"
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
        // NY KONTROLL för "Auto-connect"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // Gör endast klickbar om "Remember" är på
            modifier = Modifier.clickable(enabled = rememberScale) {
                if (rememberScale) { // Dubbelkolla att remember är på innan ändring
                    onAutoConnectChange(!autoConnect)
                }
            }
        ) {
            Checkbox(
                checked = autoConnect,
                onCheckedChange = onAutoConnectChange,
                enabled = rememberScale // Checkbox är bara aktiv om "Remember" är på
            )
            Text(
                text ="Auto-connect when available",
                // Gråa ut texten om "Remember" är av
                color = if (rememberScale) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        Spacer(Modifier.height(16.dp))


        // Visning av aktuell vikt
        Text("Weight", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "%.1f g".format(measurement.weightGrams),
            fontSize = 72.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        // Tara- och Frånkopplingsknappar
        Button(onClick = onTare) {
            Text("Tare")
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onDisconnect) {
            Text("Disconnect")
        }
    }
}


// --- ScanningView ---
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

        // Visa en rad för att glömma vågen om en adress finns sparad
        if (rememberedAddress != null) {
            ForgetScaleRow(
                address = rememberedAddress,
                onClick = onForgetScaleClick
            )
        }

        // Separator mellan kontroller och lista
        HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

        // Visar listan över hittade enheter
        DeviceList(devices, isScanning, connectionState, onDeviceClick)
    }
}


// --- ScanControls ---
@Composable
private fun ScanControls(isScanning: Boolean, connectionState: BleConnectionState, onToggleScan: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onToggleScan,
            // Tillåt scanning endast om vågen är frånkopplad eller i fel-läge
            enabled = connectionState is BleConnectionState.Disconnected || connectionState is BleConnectionState.Error
        ) {
            when {
                // Specialläge: Visar "Ansluter..." medan anslutningsförsöket pågår
                connectionState is BleConnectionState.Connecting -> {
                    CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Connecting...")
                }
                // Visar "Stoppa scanning" om aktiv
                isScanning -> {
                    CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Stop scanning")
                }
                // Standardläge: Starta scanning
                else -> Text("Start scanning")
            }
        }
    }
}

// --- DeviceList ---
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
    } else if (!isScanning && devices.isEmpty() && connectionState is BleConnectionState.Disconnected) { // Visa endast om Disconnected
        // Visa om scanningen avslutades utan resultat (och inget fel finns)
        Box(modifier = Modifier.fillMaxSize().padding(top = 16.dp), contentAlignment = Alignment.TopCenter) {
            Text("No devices found. Tap 'Start scanning' to search again.") // Tydligare text
        }
    } else if (connectionState is BleConnectionState.Error && devices.isEmpty()) { // Visa fel om inga enheter hittades OCH fel uppstod
        Box(modifier = Modifier.fillMaxSize().padding(top = 16.dp), contentAlignment = Alignment.TopCenter) {
            // Meddelandet här är nu det översatta från connectionState
            Text(connectionState.message)
        }
    }
    else {
        // Lista över hittade enheter
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items = devices, key = { it.address }) { device ->
                DeviceCard(device = device, onClick = { onDeviceClick(device) })
            }
        }
    }
}


// --- DeviceCard ---
@Composable
private fun DeviceCard(device: DiscoveredDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick), // Gör kortet klickbart för att initiera anslutning
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = device.name ?: "(Unknown Device)", // Fallback om enheten saknar namn
                style = MaterialTheme.typography.titleMedium
            )
            Text(text = "Address: ${device.address}", style = MaterialTheme.typography.bodySmall)
            Text(text = "Signal strength: ${device.rssi} dBm", style = MaterialTheme.typography.bodySmall)
        }
    }
}

// Composable för att visa den sparade vågen och "Glöm"-knappen
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