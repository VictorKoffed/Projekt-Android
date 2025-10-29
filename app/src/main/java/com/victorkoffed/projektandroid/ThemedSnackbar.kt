package com.victorkoffed.projektandroid

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.runtime.Composable

/**
 * En anpassad Composable för att visa Snackbar-meddelanden
 * med tematiska färger, som ersätter standardutseendet.
 *
 * @param data Dataobjektet som innehåller informationen för Snackbar.
 */
@Composable
fun ThemedSnackbar(data: SnackbarData) {
    Snackbar(
        snackbarData = data,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        actionColor = MaterialTheme.colorScheme.onPrimary
    )
}