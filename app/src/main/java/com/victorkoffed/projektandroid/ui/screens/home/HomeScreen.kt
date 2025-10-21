package com.victorkoffed.projektandroid.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee // Exempel, byt mot passande ikoner
import androidx.compose.material.icons.filled.MonitorWeight // Exempel, byt mot passande ikoner
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Start-/hemskärmen med huvudmeny och plats för en graf (placeholder nu).
 * Knapp-callbacks styrs av en högre nivå via lambdas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenCoffee: () -> Unit,
    onOpenScale: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Coffee Journal") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp), // Lite mer luft
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GraphPlaceholderCard()
            MainMenu(
                onOpenCoffee = onOpenCoffee,
                onOpenScale = onOpenScale
            )
        }
    }
}

@Composable
private fun GraphPlaceholderCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Graph placeholder",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MainMenu(onOpenCoffee: () -> Unit, onOpenScale: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MenuButton(
            modifier = Modifier.weight(1f),
            text = "Coffee",
            icon = Icons.Filled.Coffee,
            onClick = onOpenCoffee
        )
        MenuButton(
            modifier = Modifier.weight(1f),
            text = "Scale",
            icon = Icons.Filled.MonitorWeight,
            onClick = onOpenScale
        )
    }
}

@Composable
private fun MenuButton(
    modifier: Modifier = Modifier,
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(16.dp) // Mjukare hörn
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = icon, contentDescription = null)
            Spacer(Modifier.height(4.dp))
            Text(text)
        }
    }
}
