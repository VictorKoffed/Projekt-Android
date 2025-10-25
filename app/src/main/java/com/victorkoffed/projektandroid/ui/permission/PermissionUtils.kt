package com.victorkoffed.projektandroid.ui.permission

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

/**
 * En anpassad Composable som hanterar logiken för att fråga om Bluetooth-rättigheter
 * på ett återanvändbart sätt.
 *
 * @param onResult Callback som anropas med `true` om alla rättigheter beviljades, annars `false`.
 * @return En launcher-funktion som du kan anropa för att starta rättighetsförfrågan.
 */
@Composable
fun rememberBluetoothPermissionLauncher(onResult: (isGranted: Boolean) -> Unit): () -> Unit {
    // Lista över de Bluetooth-rättigheter som krävs, beroende på Android-version
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        // För äldre versioner krävs ACCESS_FINE_LOCATION för BLE-skanning
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // Skapa en launcher som hanterar resultatet av rättighetsförfrågan
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            // Kontrollera om alla begärda rättigheter beviljades
            val allPermissionsGranted = permissions.values.all { it }
            onResult(allPermissionsGranted)
        }
    )

    // Returnera en enkel lambda-funktion som anropar launchen med rättigheterna
    return {
        permissionLauncher.launch(permissionsToRequest)
    }
}