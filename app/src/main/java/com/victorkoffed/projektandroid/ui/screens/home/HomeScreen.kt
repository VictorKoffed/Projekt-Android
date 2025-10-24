package com.victorkoffed.projektandroid.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape // <-- NY IMPORT
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
// import androidx.compose.ui.draw.shadow // Behövs ej
import androidx.compose.ui.graphics.Color // <-- NY IMPORT
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.victorkoffed.projektandroid.R
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.data.db.Bean
import com.victorkoffed.projektandroid.data.db.Method
import com.victorkoffed.projektandroid.ui.viewmodel.coffee.CoffeeImageViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.home.HomeViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.home.RecentBrewItem
import com.victorkoffed.projektandroid.ui.viewmodel.scale.ScaleViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// Färgdefinitionen antas vara tillgänglig i MainActivity eller på annat ställe.
private val MockupColor = Color(0xFFDCC7AA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeVm: HomeViewModel,
    coffeeImageVm: CoffeeImageViewModel,
    scaleVm: ScaleViewModel,
    navigateToScreen: (String) -> Unit,
    onNavigateToBrewSetup: () -> Unit,
    onBrewClick: (Long) -> Unit,
    availableBeans: List<Bean>,
    availableMethods: List<Method>
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

    // --- NYTT: Hämta connection state - MED INITIALVÄRDE ---
    val scaleConnectionState by scaleVm.connectionState.collectAsState(
        initial = scaleVm.connectionState.replayCache.lastOrNull() ?: BleConnectionState.Disconnected
    )
    // --- SLUT ÄNDRING ---

    // --- NYTT STATE FÖR VARNINGSDIALOG ---
    var showSetupWarningDialog by remember { mutableStateOf(false) }
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
        // --- ÄNDRING: Ljusgrå bakgrundsfärg för Scaffold ---
        containerColor = Color(0xFFF0F0F0), // Ljusgrå bakgrund
        topBar = {
            TopAppBar(
                title = { Text("Home") },
                navigationIcon = {
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.Menu, contentDescription = "Meny")
                    }
                },
                actions = {
                    // --- HELA DENNA DEL ÄR ÄNDRAD ---
                    val isBrewSetupEnabled = availableBeans.isNotEmpty() && availableMethods.isNotEmpty()
                    val buttonColor = MockupColor // Färgen från mockupen
                    val iconColor = Color.Black // Svart ikonfärg för kontrast

                    // Använd Surface för att få en klickbar yta med färg och form
                    Surface(
                        modifier = Modifier
                            .padding(end = 8.dp) // Lite marginal från kanten
                            .size(40.dp) // Ungefärlig storlek för knappen
                            .clip(CircleShape) // Gör den rund
                            .clickable(
                                onClick = {
                                    if (isBrewSetupEnabled) {
                                        onNavigateToBrewSetup()
                                    } else {
                                        showSetupWarningDialog = true
                                    }
                                }
                            ),
                        color = buttonColor, // Sätt bakgrundsfärgen
                        shape = CircleShape // Definiera formen igen (bra för tydlighet)
                    ) {
                        // Box för att centrera ikonen
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New brew",
                                tint = iconColor // Sätt ikonfärgen
                            )
                        }
                    }
                    // --- SLUT PÅ ÄNDRING ---
                },
                // --- ÄNDRING: Vit bakgrundsfärg för TopAppBar ---
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface // Vit bakgrund
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            // --- ÄNDRING: Justerat avstånd ---
            verticalArrangement = Arrangement.spacedBy(12.dp),
            // --- SLUT ÄNDRING ---
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
                    timeSinceLastCoffee = timeSinceLastCoffee ?: "∞",
                    scaleConnectionState = scaleConnectionState, // <-- Skicka med state
                    onReloadImage = { coffeeImageVm.loadRandomCoffeeImage() },
                    onScaleCardClick = { navigateToScreen("scale") } // <-- Skicka med klick-hanterare
                )
            }
            item {
                Text(
                    "Last brews",
                    style = MaterialTheme.typography.headlineSmall,
                    // --- ÄNDRING: Lade till padding för att matcha rutnätet ---
                    modifier = Modifier.padding(top = 8.dp) // Lite extra luft ovanför "Last brews"
                )
            }
            if (recentBrews.isEmpty()) {
                // --- UPPDATERAT OBJEKT HÄR ---
                item {
                    NoBrewsTextWithIcon(
                        modifier = Modifier.padding(vertical = 16.dp),
                        onNavigateToBrewSetup = onNavigateToBrewSetup
                    )
                }
                // --- SLUT UPPDATERAT OBJEKT ---
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

    // --- NY DIALOGRUTA ---
    if (showSetupWarningDialog) {
        AlertDialog(
            onDismissRequest = { showSetupWarningDialog = false },
            title = { Text("Cannot start brew") },
            text = { Text("You must first add at least one bean (under 'Bean') and one brewing method (under 'Method') before you can start a new brew") },
            confirmButton = {
                TextButton(onClick = { showSetupWarningDialog = false }) {
                    Text("Understood")
                }
            }
        )
    }
    // --- SLUT PÅ NY DIALOG ---
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

    // --- ÄNDRING: Ökat avstånd från 8.dp till 12.dp ---
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // --- Första raden ---
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // --- SLUT ÄNDRING ---
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
                        else -> Text("Image", color = Color.Gray) // Fallback
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
        // --- ÄNDRING: Ökat avstånd från 8.dp till 12.dp ---
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // --- SLUT ÄNDRING ---
            // Kort 3: Antal bryggningar
            InfoCard(title = totalBrews.toString(), subtitle = "Brews", modifier = Modifier.weight(1f).height(otherRowHeight))
            // Kort 4: Tid sedan senaste kaffet (Flyttad HIT)
            InfoCard(title = timeSinceLastCoffee, subtitle = "Since last coffee", modifier = Modifier.weight(1f).height(otherRowHeight))
        }
        // --- Tredje raden ---
        // --- ÄNDRING: Ökat avstånd från 8.dp till 12.dp ---
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // --- SLUT ÄNDRING ---
            // Kort 5: Antal bönor
            InfoCard(title = uniqueBeans.toString(), subtitle = "Beans explored", modifier = Modifier.weight(1f).height(otherRowHeight))
            // Kort 6: Tillgänglig vikt
            InfoCard(title = "%.0f g".format(availableWeight), subtitle = "Beans available", modifier = Modifier.weight(1f).height(otherRowHeight))
        }
    }
}

// --- Composable för Vågstatus-kortet ---
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
    val titleColor: Color // <-- NY VARIABEL FÖR TITELFÄRG

    when (connectionState) {
        is BleConnectionState.Connected -> {
            icon = { Icon(Icons.Default.BluetoothConnected, contentDescription = "Connected") }
            // Försök visa enhetsnamnet, annars "Ansluten"
            title = connectionState.deviceName.takeIf { it.isNotEmpty() } ?: "Connected"
            subtitle = "Tap to disconnect"
            iconColor = MaterialTheme.colorScheme.primary
            titleColor = MockupColor // <-- ANVÄND MOCKUPFÄRG HÄR
        }
        is BleConnectionState.Connecting -> {
            icon = { CircularProgressIndicator(modifier = Modifier.size(24.dp)) } // Visa spinner
            title = "Connecting..."
            subtitle = "Please wait"
            iconColor = LocalContentColor.current.copy(alpha = 0.6f)
            titleColor = LocalContentColor.current // <-- Standardfärg
        }
        is BleConnectionState.Error -> {
            icon = { Icon(Icons.Default.BluetoothDisabled, contentDescription = "Fel", tint = MaterialTheme.colorScheme.error) }
            title = "Connection Error"
            subtitle = connectionState.message // Visa felmeddelandet
            iconColor = MaterialTheme.colorScheme.error
            titleColor = MaterialTheme.colorScheme.error // <-- Felfärg
        }
        BleConnectionState.Disconnected -> { // Explicit hantera Disconnected
            icon = { Icon(Icons.Default.BluetoothDisabled, contentDescription = "Frånkopplad") }
            title = "Scale disconnected"
            subtitle = "Tap to connect"
            iconColor = LocalContentColor.current.copy(alpha = 0.6f)
            titleColor = LocalContentColor.current // <-- Standardfärg
        }
    }

    Card(
        modifier = modifier.clickable(onClick = onClick), // Gör kortet klickbart
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp), // Standardskugga
        shape = RoundedCornerShape(12.dp), // Matchar InfoCard
        // --- ÄNDRING: Explicit vit bakgrund för kortet ---
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface // Vit bakgrund
        )
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
            // --- ÄNDRAD RAD: Lade till color = titleColor ---
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = titleColor) // Medium för att få plats med längre namn
            // --- SLUT ÄNDRING ---
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray, textAlign = TextAlign.Center)
        }
    }
}


// InfoCard
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    content: @Composable (ColumnScope.() -> Unit)? = null
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(12.dp), // Ökad hörnradie för att matcha mockup
        // --- ÄNDRING: Explicit vit bakgrund för kortet ---
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface // Vit bakgrund
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (content != null) { content() }
            else if (title != null) {
                // --- ÄNDRAD RAD: Lade till color = MockupColor ---
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MockupColor) // Centrera text
                // --- SLUT ÄNDRING ---
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.Gray, textAlign = TextAlign.Center) } // Centrera text
            }
        }
    }
}

// --- NY/KORRIGERAD COMPOSABLE: Använder Column för att tvinga radbrytning ---
@Composable
fun NoBrewsTextWithIcon(
    modifier: Modifier = Modifier,
    onNavigateToBrewSetup: () -> Unit // Behövs om användaren klickar på ikonen
) {
    // Använder Column för att stapla raderna vertikalt
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onNavigateToBrewSetup) // Gör hela ytan klickbar
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally // Centrera texten
    ) {
        // Rad 1: "Inga bryggningar sparade än, tryck på [ikon]"
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "No brews saved yet, tap ",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray) // Använder Color.Gray för hint-text
            )
            // Liten cirkel med plus-ikonen för att imitera knappen
            Surface(
                modifier = Modifier
                    .size(24.dp) // Gör den lagom stor i texten
                    .clip(CircleShape),
                color = MockupColor // Använd MockupColor
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null, // Inget behov av contentDescription för inbäddad ikon
                        tint = Color.Black, // Svart plus
                        modifier = Modifier.size(16.dp) // Mindre ikon
                    )
                }
            }
        }

        // Rad 2: "för att skapa en."
        Text(
            "to create one.",
            style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray),
            modifier = Modifier.padding(top = 2.dp) // Litet utrymme mellan raderna
        )
    }
}
// --- SLUT NY COMPOSABLE ---


// RecentBrewCard
@Composable
fun RecentBrewCard(
    brewItem: RecentBrewItem,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp), // Mindre skugga
        shape = RoundedCornerShape(12.dp), // Ökad hörnradie
        // --- ÄNDRING: Explicit vit bakgrund för kortet ---
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface // Vit bakgrund
        )
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${dateFormat.format(brewItem.brew.startedAt)} ${timeFormat.format(brewItem.brew.startedAt)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
                Text(brewItem.beanName ?: "Unknown bean", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(12.dp))

            // --- HELA DETTA BLOCK ÄR ERSATT ---
            // Box för att hålla bilden eller placeholdern
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (brewItem.brew.imageUri != null) {
                    // Om bild-URI finns, ladda den med Coil
                    AsyncImage(
                        model = brewItem.brew.imageUri, // Ladda bilden från URI:n
                        contentDescription = "Brew photo",
                        contentScale = ContentScale.Crop, // Fyll utrymmet
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Annars, visa placeholder-bilden
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher_foreground), // Använd mipmap (kaffebönan)
                        contentDescription = "BryggImage",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize() // Fyll boxen
                    )
                }
            }
            // --- SLUT PÅ ERSÄTTNING ---
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