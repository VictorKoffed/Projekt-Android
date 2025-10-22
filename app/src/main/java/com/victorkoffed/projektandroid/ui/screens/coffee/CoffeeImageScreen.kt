package com.victorkoffed.projektandroid.ui.screens.coffee


import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.victorkoffed.projektandroid.ui.viewmodel.coffee.CoffeeImageViewModel

/**
 * Compose-skärm som visar en knapp för att hämta bild och renderar resultatet.
 * All logik hämtas från CoffeeImageViewModel.
 */
@Composable
fun CoffeeImageScreen(vm: CoffeeImageViewModel) {
    val imageUrl = vm.imageUrl.value
    val loading = vm.loading.value
    val error = vm.error.value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Random Coffee Image", style = MaterialTheme.typography.titleLarge)

        // Knapp för att hämta ny bild
        Button(onClick = { vm.loadRandomCoffeeImage() }, enabled = !loading) {
            Text(if (loading) "Loading..." else "Getting picture")
        }

        // Felmeddelande (om något gått fel)
        if (error != null) {
            Text("Error: $error", color = MaterialTheme.colorScheme.error)
        }

        // Visar hämtad bild (om någon finns)
        AsyncImage(
            model = imageUrl,
            contentDescription = "Coffee",
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            contentScale = ContentScale.Crop
        )
    }
}
