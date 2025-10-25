package com.victorkoffed.projektandroid.ui.permission

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

/**
 * En anpassad Composable som hanterar logiken för att fråga om Bluetooth-relaterade rättigheter
 * baserat på enhetens Android-version.
 *
 * @param onResult Callback som anropas med `true` om *alla* nödvändiga rättigheter beviljades.
 * @return En launcher-funktion (lambda) som du kan anropa för att starta rättighetsförfrågan.
 */
@Composable
fun rememberBluetoothPermissionLauncher(onResult: (isGranted: Boolean) -> Unit): () -> Unit {
    // Lista över de Bluetooth-rättigheter som krävs, beroende på Android-version.
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12 (API 31) och nyare använder specifika BLUETOOTH_SCAN och BLUETOOTH_CONNECT.
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        // Äldre versioner (före API 31) använder ACCESS_FINE_LOCATION för att tillåta BLE-skanning.
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // Skapa en launcher för att begära flera behörigheter samtidigt.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            // Kontrollera att alla värden i mappen är 'true'.
            val allPermissionsGranted = permissions.values.all { it }
            onResult(allPermissionsGranted)
        }
    )

    // Returnera en funktion som enkelt kan anropas i UI-lagret för att starta förfrågan.
    return {
        permissionLauncher.launch(permissionsToRequest)
    }
}