package com.victorkoffed.projektandroid.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth // <-- NY IMPORT
import androidx.compose.material.icons.filled.BluetoothConnected // <-- NY IMPORT
import androidx.compose.material.icons.filled.BluetoothDisabled // <-- NY IMPORT
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign // <-- NY IMPORT
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.victorkoffed.projektandroid.R
import com.victorkoffed.projektandroid.domain.model.BleConnectionState // <-- NY IMPORT
// --- NYA IMPORTER ---
import com.victorkoffed.projektandroid.data.db.Bean
import com.victorkoffed.projektandroid.data.db.Method
// --- SLUT ---
import com.victorkoffed.projektandroid.ui.viewmodel.coffee.CoffeeImageViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.home.HomeViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.home.RecentBrewItem
import com.victorkoffed.projektandroid.ui.viewmodel.scale.ScaleViewModel // <-- NY IMPORT
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    // --- UPPDATERADE PARAMETRAR ---
    homeVm: HomeViewModel,
    coffeeImageVm: CoffeeImageViewModel,
    scaleVm: ScaleViewModel, // Ny parameter
    navigateToScreen: (String) -> Unit, // Ny parameter
    // --- SLUT ---
    onNavigateToBrewSetup: () -> Unit,
    onBrewClick: (Long) -> Unit,
    // --- NYA PARAMETRAR FÖR ATT INAKTIVERA KNAPP ---
    availableBeans: List<Bean>,
    availableMethods: List<Method>
    // --- SLUT ---
) {
    // Hämta states från ViewModels
    val recentBrews by homeVm.recentBrews.collectAsState()
    val totalBrews by homeVm.totalBrewCount.collectAsState()
    val uniqueBeans by homeVm.uniqueBeanCount.collectAsState()
    val availableWeight by homeVm.totalAvailableBeanWeight.collectAsState()
    val lastBrewTime by homeVm.lastBrewTime.collectAsState()

    // States för slumpmässig bild
    val imageUrl by coffeeImageVm.imageUrl
    val imageLoading by coffeeImageVm.loading
    val imageError by coffeeImageVm.error

    // State för den formaterade "tid sedan"-strängen
    var timeSinceLastCoffee by remember { mutableStateOf<String?>("...") }

    // --- NYTT: Hämta connection state ---
    val scaleConnectionState by scaleVm.connectionState.collectAsState()
    // --- SLUT ---

    // Effekt för att hämta slumpmässig bild
    LaunchedEffect(Unit) {
        if (imageUrl == null) {
            coffeeImageVm.loadRandomCoffeeImage()
        }
    }

    // Effekt för att uppdatera "tid sedan"-strängen
    LaunchedEffect(lastBrewTime) {
        while (true) {
            timeSinceLastCoffee = formatTimeSince(lastBrewTime)
            delay(60000) // Vänta en minut
        }
    }

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
                    // --- UPPDATERAD KNAPP ---
                    val isBrewSetupEnabled = availableBeans.isNotEmpty() && availableMethods.isNotEmpty()

                    IconButton(
                        onClick = onNavigateToBrewSetup,
                        enabled = isBrewSetupEnabled // <-- ÄNDRINGEN ÄR HÄR
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Ny bryggning")
                    }
                    // --- SLUT PÅ UPPDATERING ---
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
                // Skicka med all data till InfoGrid
                InfoGrid(
                    totalBrews = totalBrews,
                    uniqueBeans = uniqueBeans,
                    availableWeight = availableWeight,
                    imageUrl = imageUrl,
                    imageLoading = imageLoading,
                    imageError = imageError,
                    timeSinceLastCoffee = timeSinceLastCoffee ?: "Ingen bryggning",
                    scaleConnectionState = scaleConnectionState, // <-- Skicka med state
                    onReloadImage = { coffeeImageVm.loadRandomCoffeeImage() },
                    onScaleCardClick = { navigateToScreen("scale") } // <-- Skicka med klick-hanterare
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

// Rutnät för infokorten - Uppdaterad med flyttat kort och connection state
@Composable
fun InfoGrid(
    totalBrews: Int,
    uniqueBeans: Int,
    availableWeight: Double,
    imageUrl: String?,
    imageLoading: Boolean,
    imageError: String?,
    timeSinceLastCoffee: String,
    scaleConnectionState: BleConnectionState, // <-- Ny parameter
    onReloadImage: () -> Unit,
    onScaleCardClick: () -> Unit // <-- Ny parameter
) {
    // Justerade höjder
    val firstRowHeight = 160.dp
    val otherRowHeight = 100.dp

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // --- Första raden ---
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Kort 1: Slumpmässig bild (som tidigare)
            InfoCard(modifier = Modifier.weight(1f).height(firstRowHeight)) {
                Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    when {
                        imageLoading -> CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        imageError != null -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Error", color = Color.Gray, fontSize = 12.sp)
                                IconButton(onClick = onReloadImage, modifier= Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Refresh, "Reload")
                                }
                            }
                        }
                        imageUrl != null -> {
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = "Random coffee",
                                contentScale = ContentScale.Crop, // Fyller kortet
                                modifier = Modifier.fillMaxSize()
                                    .clickable { onReloadImage() } // Klicka för ny bild
                            )
                        }
                        else -> Text("Bild", color = Color.Gray) // Fallback
                    }
                }
            }

            // --- KORT 2: NU ÄR DET VÅGENS STATUS ---
            ScaleStatusCard( // Bryter ut till egen Composable för tydlighet
                connectionState = scaleConnectionState,
                onClick = onScaleCardClick, // Skicka vidare klicket
                modifier = Modifier.weight(1f).height(firstRowHeight) // Använd firstRowHeight
            )
            // --- SLUT PÅ FLYTTAT KORT ---
        }

        // --- Andra raden ---
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Kort 3: Antal bryggningar
            InfoCard(title = totalBrews.toString(), subtitle = "Brews", modifier = Modifier.weight(1f).height(otherRowHeight))
            // Kort 4: Tid sedan senaste kaffet (Flyttad HIT)
            InfoCard(title = timeSinceLastCoffee, subtitle = "Since last coffee", modifier = Modifier.weight(1f).height(otherRowHeight))
        }
        // --- Tredje raden ---
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Kort 5: Antal bönor
            InfoCard(title = uniqueBeans.toString(), subtitle = "Beans explored", modifier = Modifier.weight(1f).height(otherRowHeight))
            // Kort 6: Tillgänglig vikt
            InfoCard(title = "%.0f g".format(availableWeight), subtitle = "Beans available", modifier = Modifier.weight(1f).height(otherRowHeight))
        }
    }
}

// --- NY Composable för Vågstatus-kortet ---
@Composable
fun ScaleStatusCard(
    connectionState: BleConnectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Bestäm ikon och text baserat på state
    val icon: @Composable () -> Unit
    val title: String
    val subtitle: String
    val iconColor: Color

    when (connectionState) {
        is BleConnectionState.Connected -> {
            icon = { Icon(Icons.Default.BluetoothConnected, contentDescription = "Ansluten") }
            // Försök visa enhetsnamnet, annars "Ansluten"
            title = connectionState.deviceName.takeIf { it.isNotEmpty() } ?: "Ansluten"
            subtitle = "Tryck för att koppla från"
            iconColor = MaterialTheme.colorScheme.primary
        }
        is BleConnectionState.Connecting -> {
            icon = { CircularProgressIndicator(modifier = Modifier.size(24.dp)) } // Visa spinner
            title = "Ansluter..."
            subtitle = "Var god vänta"
            iconColor = LocalContentColor.current.copy(alpha = 0.6f)
        }
        is BleConnectionState.Disconnected -> {
            icon = { Icon(Icons.Default.BluetoothDisabled, contentDescription = "Frånkopplad") }
            title = "Våg frånkopplad"
            subtitle = "Tryck för att ansluta"
            iconColor = LocalContentColor.current.copy(alpha = 0.6f)
        }
        is BleConnectionState.Error -> {
            icon = { Icon(Icons.Default.BluetoothDisabled, contentDescription = "Fel", tint = MaterialTheme.colorScheme.error) }
            title = "Anslutningsfel"
            subtitle = connectionState.message // Visa felmeddelandet
            iconColor = MaterialTheme.colorScheme.error
        }
    }

    // Återanvänd InfoCard men gör den klickbar och lägg till ikon
    Card(
        modifier = modifier.clickable(onClick = onClick), // Gör kortet klickbart
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Lägg till ikonen ovanför texten
            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) { // Box för att hantera storlek
                CompositionLocalProvider(LocalContentColor provides iconColor) {
                    icon()
                }
            }
            Spacer(Modifier.height(4.dp)) // Lite luft
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) // Medium för att få plats med längre namn
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray, textAlign = TextAlign.Center)
        }
    }
}


// InfoCard (Tidigare isConnected borttagen, text centrerad)
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
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
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) // Centrera text
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.Gray, textAlign = TextAlign.Center) } // Centrera text
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
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)), // Behåller 64dp
                contentScale = ContentScale.Crop
            )
        }
    }
}

// formatTimeSince (Oförändrad)
private fun formatTimeSince(lastBrewTime: Date?): String? {
    if (lastBrewTime == null) return null

    val now = System.currentTimeMillis()
    val diffMillis = now - lastBrewTime.time

    val diffSeconds = TimeUnit.MILLISECONDS.toSeconds(diffMillis)
    val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
    val diffHours = TimeUnit.MILLISECONDS.toHours(diffMillis)
    val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)

    return when {
        diffSeconds < 60 -> "< 1 min"
        diffMinutes < 60 -> "$diffMinutes min"
        diffHours < 24 -> "$diffHours h"
        diffDays < 7 -> "$diffDays d"
        else -> {
            val weeks = diffDays / 7
            // Ungefärlig beräkning för år
            if (weeks >= 52) {
                val years = weeks / 52
                "$years år"
            } else {
                "$weeks v"
            }
        }
    }
}