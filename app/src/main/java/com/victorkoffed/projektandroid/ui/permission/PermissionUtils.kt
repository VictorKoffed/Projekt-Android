/**
 * Implementation Note: Conditional Bluetooth permission handling based on the Android SDK version (API 31+)
 * was structured with AI assistance. See README.md.
 */

package com.victorkoffed.projektandroid.ui.permission

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

/**
 * Encapsulates runtime permission requests for Bluetooth connectivity, dynamically adjusting
 * required permission sets based on Android SDK level constraints (Android 12+ fine-grained permissions
 * versus legacy location-based BLE scanning requirements).
 */
@Composable
fun rememberBluetoothPermissionLauncher(onResult: (isGranted: Boolean) -> Unit): () -> Unit {
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val allPermissionsGranted = permissions.values.all { it }
            onResult(allPermissionsGranted)
        }
    )

    return {
        permissionLauncher.launch(permissionsToRequest)
    }
}