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
import androidx.compose.material.icons.filled.Warning
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
// --- NY IMPORT FÖR Screen ---
import com.victorkoffed.projektandroid.ui.navigation.Screen
// --- SLUT NY IMPORT ---
import com.victorkoffed.projektandroid.ui.viewmodel.coffee.CoffeeImageViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.home.HomeViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.home.RecentBrewItem
import com.victorkoffed.projektandroid.ui.viewmodel.scale.ScaleViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// --- NYA IMPORTER FÖR SNACKBAR ---
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.launch
// --- SLUT NYA IMPORTER ---


// Färgdefinitionen antas vara tillgänglig i MainActivity eller på annat ställe.
private val MockupColor = Color(0xFFDCC7AA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeVm: HomeViewModel,
    coffeeImageVm: CoffeeImageViewModel,
    scaleVm: ScaleViewModel,
    // --- NY PARAMETER ---
    snackbarHostState: SnackbarHostState,
    // --- SLUT NY PARAMETER ---
    navigateToScreen: (String) -> Unit, // Denna tar emot en Rutt-sträng
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
    val imageError by coffeeImageVm.error // Fel från VM

    // State för den formaterade "tid sedan"-strängen
    var timeSinceLastCoffee by remember { mutableStateOf<String?>("...") }

    // --- Hämta connection state - MED INITIALVÄRDE ---
    val scaleConnectionState by scaleVm.connectionState.collectAsState(
        initial = scaleVm.connectionState.replayCache.lastOrNull() ?: BleConnectionState.Disconnected
    )
    // --- SLUT ÄNDRING ---

    // --- STATE FÖR VARNINGSDIALOG ---
    var showSetupWarningDialog by remember { mutableStateOf(false) }
    // --- SLUT ---

    // --- NYTT: Scope för Snackbar ---
    val scope = rememberCoroutineScope()
    // --- SLUT NYTT ---

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

    // --- NYTT: Effekt för att visa Snackbar vid bildfel ---
    LaunchedEffect(imageError) {
        if (imageError != null) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "Image Error: $imageError"
                )
            }
            // Nollställ felet i ViewModel så det inte visas igen
            coffeeImageVm.clearError()
        }
    }
    // --- SLUT NYTT ---

    Scaffold(
        // --- Ljusgrå bakgrundsfärg för Scaffold ---
        containerColor = Color(0xFFF0F0F0), // Ljusgrå bakgrund
        topBar = {
            TopAppBar(
                title = { Text("Home") },
                navigationIcon = {
                    IconButton(onClick = { /* TODO: Implement Menu */ }) {
                        Icon(Icons.Default.Menu, contentDescription = "Meny")
                    }
                },
                actions = {
                    // --- Logik för "Lägg till bryggning"-knappen ---
                    val isBrewSetupEnabled = availableBeans.isNotEmpty() && availableMethods.isNotEmpty()
                    val buttonColor = MockupColor // Färgen från mockupen
                    val iconColor = Color.Black // Svart ikonfärg för kontrast

                    Surface(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(
                                onClick = {
                                    if (isBrewSetupEnabled) {
                                        onNavigateToBrewSetup() // Denna navigerar nu med NavController
                                    } else {
                                        showSetupWarningDialog = true
                                    }
                                }
                            ),
                        color = buttonColor,
                        shape = CircleShape
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New brew",
                                tint = iconColor
                            )
                        }
                    }
                    // --- SLUT PÅ "Lägg till bryggning" ---
                },
                // --- Vit bakgrundsfärg för TopAppBar ---
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    // --- ÄNDRING: Skicka med felet ---
                    imageError = imageError,
                    // --- SLUT ÄNDRING ---
                    timeSinceLastCoffee = timeSinceLastCoffee ?: "∞",
                    scaleConnectionState = scaleConnectionState,
                    onReloadImage = { coffeeImageVm.loadRandomCoffeeImage() },
                    // --- KORRIGERAD NAVIGERING HÄR ---
                    onScaleCardClick = { navigateToScreen(Screen.ScaleConnect.route) } // Använd korrekt rutt
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
                item {
                    NoBrewsTextWithIcon(
                        modifier = Modifier.padding(vertical = 16.dp),
                        onNavigateToBrewSetup = onNavigateToBrewSetup
                    )
                }
            } else {
                items(recentBrews) { brewItem ->
                    RecentBrewCard(
                        brewItem = brewItem,
                        onClick = { onBrewClick(brewItem.brew.id) } // Denna navigerar nu via NavController
                    )
                }
            }
        }
    }

    // --- Dialogruta för setup-varning ---
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
    // --- SLUT PÅ DIALOG ---
}

// Rutnät för infokorten
@Composable
fun InfoGrid(
    totalBrews: Int,
    uniqueBeans: Int,
    availableWeight: Double,
    imageUrl: String?,
    imageLoading: Boolean,
    imageError: String?, // <-- Ta emot felet
    timeSinceLastCoffee: String,
    scaleConnectionState: BleConnectionState,
    onReloadImage: () -> Unit,
    onScaleCardClick: () -> Unit // Denna kör navigateToScreen med korrekt rutt
) {
    val firstRowHeight = 160.dp
    val otherRowHeight = 100.dp

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // --- Första raden ---
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Kort 1: Slumpmässig bild
            InfoCard(modifier = Modifier.weight(1f).height(firstRowHeight)) {
                Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    when {
                        imageLoading -> CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        // --- ÄNDRING: Ta bort Text-visningen av felet ---
                        imageError != null -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                // Behåll bara ikonen och knappen
                                Icon(Icons.Default.Warning, "Error loading image", tint = Color.Gray)
                                IconButton(onClick = onReloadImage, modifier= Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Refresh, "Reload")
                                }
                            }
                        }
                        // --- SLUT ÄNDRING ---
                        imageUrl != null -> {
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = "Random coffee",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clickable { onReloadImage() }
                            )
                        }
                        else -> Text("Image", color = Color.Gray)
                    }
                }
            }

            // Kort 2: Vågens status
            ScaleStatusCard(
                connectionState = scaleConnectionState,
                onClick = onScaleCardClick, // Denna skickas vidare från InfoGrid-anropet
                modifier = Modifier.weight(1f).height(firstRowHeight)
            )
        }

        // --- Andra raden ---
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard(title = totalBrews.toString(), subtitle = "Brews", modifier = Modifier.weight(1f).height(otherRowHeight))
            InfoCard(title = timeSinceLastCoffee, subtitle = "Since last coffee", modifier = Modifier.weight(1f).height(otherRowHeight))
        }
        // --- Tredje raden ---
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard(title = uniqueBeans.toString(), subtitle = "Beans explored", modifier = Modifier.weight(1f).height(otherRowHeight))
            InfoCard(title = "%.0f g".format(availableWeight), subtitle = "Beans available", modifier = Modifier.weight(1f).height(otherRowHeight))
        }
    }
}

// Composable för Vågstatus-kortet (Oförändrad)
@Composable
fun ScaleStatusCard(
    connectionState: BleConnectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon: @Composable () -> Unit
    val title: String
    val subtitle: String
    val iconColor: Color
    val titleColor: Color

    when (connectionState) {
        is BleConnectionState.Connected -> {
            icon = { Icon(Icons.Default.BluetoothConnected, contentDescription = "Connected") }
            title = connectionState.deviceName.takeIf { it.isNotEmpty() } ?: "Connected"
            subtitle = "Tap to disconnect"
            iconColor = MaterialTheme.colorScheme.primary
            titleColor = MockupColor
        }
        is BleConnectionState.Connecting -> {
            icon = { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
            title = "Connecting..."
            subtitle = "Please wait"
            iconColor = LocalContentColor.current.copy(alpha = 0.6f)
            titleColor = LocalContentColor.current
        }
        is BleConnectionState.Error -> {
            icon = { Icon(Icons.Default.BluetoothDisabled, contentDescription = "Fel", tint = MaterialTheme.colorScheme.error) }
            title = "Connection Error"
            subtitle = connectionState.message
            iconColor = MaterialTheme.colorScheme.error
            titleColor = MaterialTheme.colorScheme.error
        }
        BleConnectionState.Disconnected -> {
            icon = { Icon(Icons.Default.BluetoothDisabled, contentDescription = "Frånkopplad") }
            title = "Scale disconnected"
            subtitle = "Tap to connect"
            iconColor = LocalContentColor.current.copy(alpha = 0.6f)
            titleColor = LocalContentColor.current
        }
    }

    Card(
        modifier = modifier.clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalContentColor provides iconColor) {
                    icon()
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = titleColor)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray, textAlign = TextAlign.Center)
        }
    }
}


// InfoCard (Oförändrad)
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (content != null) { content() }
            else if (title != null) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MockupColor)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.Gray, textAlign = TextAlign.Center) }
            }
        }
    }
}

// NoBrewsTextWithIcon (Oförändrad)
@Composable
fun NoBrewsTextWithIcon(
    modifier: Modifier = Modifier,
    onNavigateToBrewSetup: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onNavigateToBrewSetup)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "No brews saved yet, tap ",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray)
            )
            Surface(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape),
                color = MockupColor
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Text(
            "to create one.",
            style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray),
            modifier = Modifier.padding(top = 2.dp)
        )
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${dateFormat.format(brewItem.brew.startedAt)} ${timeFormat.format(brewItem.brew.startedAt)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
                Text(brewItem.beanName ?: "Unknown bean", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (brewItem.brew.imageUri != null) {
                    AsyncImage(
                        model = brewItem.brew.imageUri,
                        contentDescription = "Brew photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                        contentDescription = "BryggImage",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
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
            if (weeks >= 52) {
                val years = weeks / 52
                "$years år"
            } else {
                "$weeks v"
            }
        }
    }
}