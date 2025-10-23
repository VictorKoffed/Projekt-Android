package com.victorkoffed.projektandroid.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable // NY IMPORT
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// Importera R manuellt om det behövs
import com.victorkoffed.projektandroid.R
import com.victorkoffed.projektandroid.ui.viewmodel.home.HomeViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.home.RecentBrewItem
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onNavigateToBrewSetup: () -> Unit,
    onBrewClick: (Long) -> Unit
    // TODO: Callback för meny
) {
    val recentBrews by vm.recentBrews.collectAsState()
    val totalBrews by vm.totalBrewCount.collectAsState()
    val uniqueBeans by vm.uniqueBeanCount.collectAsState()
    val availableWeight by vm.totalAvailableBeanWeight.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Home") },
                navigationIcon = {
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.Menu, contentDescription = "Meny")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToBrewSetup) {
                        Icon(Icons.Default.Add, contentDescription = "Ny bryggning")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
        ) {
            item {
                // KORRIGERING: Skicka med datan till InfoGrid
                InfoGrid(
                    totalBrews = totalBrews,
                    uniqueBeans = uniqueBeans,
                    availableWeight = availableWeight
                    // TODO: Lägg till data för placeholders
                )
            }
            item {
                Text(
                    "Last brews",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (recentBrews.isEmpty()) {
                item { Text("Inga bryggningar sparade än.", modifier = Modifier.padding(vertical = 16.dp)) }
            } else {
                items(recentBrews) { brewItem ->
                    RecentBrewCard(
                        brewItem = brewItem,
                        onClick = { onBrewClick(brewItem.brew.id) }
                    )
                }
            }
        }
    }
}

// Rutnät för infokorten - KORRIGERING: Tar nu emot parametrar
@Composable
fun InfoGrid(
    totalBrews: Int,
    uniqueBeans: Int,
    availableWeight: Double
    // TODO: Fler parametrar för placeholders
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoCard(modifier = Modifier.weight(1f).height(80.dp)) { // Kort 1: Bild placeholder
                Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Text("Bild", color = Color.Gray) // TODO: Ersätt
                }
            }
            // Kort 2: Tid placeholder
            InfoCard(title = "1h", subtitle = "Time without coffee", modifier = Modifier.weight(1f).height(80.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Kort 3: Antal bryggningar (Använder nu parametern)
            InfoCard(title = totalBrews.toString(), subtitle = "Brews", modifier = Modifier.weight(1f).height(80.dp))
            // Kort 4: Våg-status placeholder
            InfoCard(title = "bookoo themis mini", subtitle = "Connected", isConnected = true, modifier = Modifier.weight(1f).height(80.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Kort 5: Antal bönor (Använder nu parametern)
            InfoCard(title = uniqueBeans.toString(), subtitle = "Beans explored", modifier = Modifier.weight(1f).height(80.dp))
            // Kort 6: Tillgänglig vikt (Använder nu parametern)
            InfoCard(title = "%.0f g".format(availableWeight), subtitle = "Beans available", modifier = Modifier.weight(1f).height(80.dp))
        }
    }
}

// InfoCard (Oförändrad)
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    isConnected: Boolean? = null,
    content: @Composable (ColumnScope.() -> Unit)? = null
) {
    Card(modifier = modifier, elevation = CardDefaults.cardElevation(2.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (content != null) { content() }
            else if (title != null) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                isConnected?.let { /* ... som tidigare ... */ }
            }
        }
    }
}


// RecentBrewCard (Oförändrad)
@Composable
fun RecentBrewCard(
    brewItem: RecentBrewItem,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${dateFormat.format(brewItem.brew.startedAt)} ${timeFormat.format(brewItem.brew.startedAt)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
                Text(brewItem.beanName ?: "Okänd böna", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(12.dp))
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground), // Bättre placeholder?
                contentDescription = "Bryggbild",
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}

