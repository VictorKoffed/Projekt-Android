package com.victorkoffed.projektandroid.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.victorkoffed.projektandroid.R
import com.victorkoffed.projektandroid.data.db.Bean
import com.victorkoffed.projektandroid.data.db.Method
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.ui.navigation.Screen
import com.victorkoffed.projektandroid.ui.viewmodel.coffee.CoffeeImageViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.home.HomeViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.home.RecentBrewItem
import com.victorkoffed.projektandroid.ui.viewmodel.scale.ScaleViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeVm: HomeViewModel,
    coffeeImageVm: CoffeeImageViewModel,
    scaleVm: ScaleViewModel,
    snackbarHostState: SnackbarHostState, // State för Snackbar (för felhantering)
    navigateToScreen: (String) -> Unit, // Generell navigations-callback
    onNavigateToBrewSetup: () -> Unit, // Callback för att starta ny bryggning
    onBrewClick: (Long) -> Unit, // Callback för att visa bryggdetaljer
    availableBeans: List<Bean>, // Data för att kontrollera förutsättningar
    availableMethods: List<Method> // Data för att kontrollera förutsättningar
) {
    // --- Data från ViewModels (State Collection) ---
    val recentBrews by homeVm.recentBrews.collectAsState()
    val totalBrews by homeVm.totalBrewCount.collectAsState()
    val uniqueBeans by homeVm.uniqueBeanCount.collectAsState()
    val availableWeight by homeVm.totalAvailableBeanWeight.collectAsState()
    val lastBrewTime by homeVm.lastBrewTime.collectAsState()

    // States för slumpmässig bild
    val imageUrl by coffeeImageVm.imageUrl
    val imageLoading by coffeeImageVm.loading
    val imageError by coffeeImageVm.error

    // Lokalt state för den formaterade "tid sedan"-strängen
    var timeSinceLastCoffee by remember { mutableStateOf<String?>("...") }

    // Hämta connection state från ScaleViewModel. Använder replayCache för att undvika null initialvärde.
    val scaleConnectionState by scaleVm.connectionState.collectAsState(
        initial = scaleVm.connectionState.replayCache.lastOrNull() ?: BleConnectionState.Disconnected
    )

    // State för att visa varning om setup saknas (kaffeböna/metod)
    var showSetupWarningDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Logik: En bryggning får endast startas om minst en böna och en metod finns.
    val isBrewSetupEnabled = availableBeans.isNotEmpty() && availableMethods.isNotEmpty()

    // Definierar den villkorliga åtgärden för att starta bryggning
    val startBrewAction = {
        if (isBrewSetupEnabled) {
            onNavigateToBrewSetup()
        } else {
            showSetupWarningDialog = true // Visa varning om setup saknas
        }
    }

    // --- Launched Effects (Sid-effekter) ---

    // 1. Ladda slumpmässig bild vid första laddning
    LaunchedEffect(Unit) {
        if (imageUrl == null) {
            coffeeImageVm.loadRandomCoffeeImage()
        }
    }

    // 2. Uppdatera "tid sedan" strängen varje minut
    LaunchedEffect(lastBrewTime) {
        // En loop som körs kontinuerligt så länge composable är aktiv
        while (true) {
            timeSinceLastCoffee = formatTimeSince(lastBrewTime)
            delay(60000) // Vänta en minut (60 sekunder)
        }
    }

    // 3. Visa Snackbar vid bildfel
    LaunchedEffect(imageError) {
        if (imageError != null) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "Image Error: $imageError"
                )
            }
            // Nollställ felet i ViewModel efter visning för att undvika upprepning
            coffeeImageVm.clearError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Home") },
                navigationIcon = {
                    IconButton(onClick = { /* TODO: Implement Menu */ }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    // Knapp för att starta ny bryggning
                    val buttonColor = MaterialTheme.colorScheme.primary
                    val iconColor = MaterialTheme.colorScheme.onPrimary

                    Surface(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(38.dp)
                            .clip(CircleShape)
                            // Klicket utlöser den villkorliga startBrewAction
                            .clickable(onClick = startBrewAction),
                        color = buttonColor,
                        shape = CircleShape
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New brew",
                                tint = iconColor,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                },
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
                // Rutnät för statistik och statuskort
                InfoGrid(
                    totalBrews = totalBrews,
                    uniqueBeans = uniqueBeans,
                    availableWeight = availableWeight,
                    imageUrl = imageUrl,
                    imageLoading = imageLoading,
                    imageError = imageError,
                    timeSinceLastCoffee = timeSinceLastCoffee ?: "∞",
                    scaleConnectionState = scaleConnectionState,
                    onReloadImage = { coffeeImageVm.loadRandomCoffeeImage() },
                    // Navigering till våganslutningsskärmen
                    onScaleCardClick = { navigateToScreen(Screen.ScaleConnect.route) }
                )
            }
            item {
                Text(
                    "Last brews",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            // Villkorlig rendering: Visa listan eller en placeholder
            if (recentBrews.isEmpty()) {
                item {
                    NoBrewsTextWithIcon(
                        modifier = Modifier.padding(vertical = 16.dp),
                        onStartBrewClick = startBrewAction // Använd den villkorliga åtgärden
                    )
                }
            }
            else {
                // Lista över de senaste bryggningarna
                items(recentBrews) { brewItem ->
                    RecentBrewCard(
                        brewItem = brewItem,
                        onClick = { onBrewClick(brewItem.brew.id) }
                    )
                }
            }
        }
    }

    // Dialogruta som visas om användaren försöker starta en bryggning utan nödvändig setup
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
}

// --- InfoGrid och InfoCards ---

/**
 * Rutnät för infokorten som visar nyckelstatistik och status.
 */
@Composable
fun InfoGrid(
    totalBrews: Int,
    uniqueBeans: Int,
    availableWeight: Double,
    imageUrl: String?,
    imageLoading: Boolean,
    imageError: String?,
    timeSinceLastCoffee: String,
    scaleConnectionState: BleConnectionState,
    onReloadImage: () -> Unit,
    onScaleCardClick: () -> Unit
) {
    val firstRowHeight = 160.dp
    val otherRowHeight = 100.dp

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Första raden: Bild och vågens status
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Kort 1: Slumpmässig bild
            InfoCard(modifier = Modifier.weight(1f).height(firstRowHeight)) {
                Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    when {
                        imageLoading -> CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        imageError != null -> {
                            // Visa felikon och en knapp för att ladda om
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Warning, "Error loading image", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                IconButton(onClick = onReloadImage, modifier= Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Refresh, "Reload")
                                }
                            }
                        }
                        imageUrl != null -> {
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = "Random coffee",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clickable { onReloadImage() }
                            )
                        }
                        else -> Text("Image", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Kort 2: Vågens status (klickbar)
            ScaleStatusCard(
                connectionState = scaleConnectionState,
                onClick = onScaleCardClick,
                modifier = Modifier.weight(1f).height(firstRowHeight)
            )
        }

        // Andra och tredje raden: Statistik
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard(title = totalBrews.toString(), subtitle = "Brews", modifier = Modifier.weight(1f).height(otherRowHeight))
            InfoCard(title = timeSinceLastCoffee, subtitle = "Since last coffee", modifier = Modifier.weight(1f).height(otherRowHeight))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard(title = uniqueBeans.toString(), subtitle = "Beans explored", modifier = Modifier.weight(1f).height(otherRowHeight))
            // Använd %.0f g för att formatera vikt utan decimaler (inte specificerat, men vanligare för totalvikt)
            InfoCard(title = "%.0f g".format(availableWeight), subtitle = "Beans available", modifier = Modifier.weight(1f).height(otherRowHeight))
        }
    }
}

/**
 * Visar vågens anslutningsstatus med dynamiska ikoner och färger.
 */
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
            titleColor = MaterialTheme.colorScheme.primary
        }
        is BleConnectionState.Connecting -> {
            icon = { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
            title = "Connecting..."
            subtitle = "Please wait"
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant
            titleColor = MaterialTheme.colorScheme.onSurface
        }
        is BleConnectionState.Error -> {
            icon = { Icon(Icons.Default.BluetoothDisabled, contentDescription = "Error", tint = MaterialTheme.colorScheme.error) }
            title = "Connection Error"
            subtitle = connectionState.message
            iconColor = MaterialTheme.colorScheme.error
            titleColor = MaterialTheme.colorScheme.error
        }
        BleConnectionState.Disconnected -> {
            icon = { Icon(Icons.Default.BluetoothDisabled, contentDescription = "Disconnected") }
            title = "Scale disconnected"
            subtitle = "Tap to connect"
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant
            titleColor = MaterialTheme.colorScheme.onSurface
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
                // Tvingar ikonen att använda den dynamiskt valda färgen
                CompositionLocalProvider(LocalContentColor provides iconColor) {
                    icon()
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = titleColor)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}


/**
 * Generisk baskomponent för informationskort.
 */
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
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
            }
        }
    }
}

/**
 * Placeholder-text som visas när inga bryggningar finns sparade.
 */
@Composable
fun NoBrewsTextWithIcon(
    modifier: Modifier = Modifier,
    onStartBrewClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Gör hela ytan klickbar för att starta bryggning
            .clickable(onClick = onStartBrewClick)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "No brews saved yet, tap ",
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            )
            Surface(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        Text(
            "to create one.",
            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}


/**
 * Kortkomponent för att visa en nyligen genomförd bryggning.
 */
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
                Text("${dateFormat.format(brewItem.brew.startedAt)} ${timeFormat.format(brewItem.brew.startedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(brewItem.beanName ?: "Unknown bean", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(12.dp))

            // Bild eller standardikon
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
                    // Visar en standardikon om ingen bild finns
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                        contentDescription = "Brew Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Hjälpfunktion för att formatera tiden som gått sedan senaste bryggningen.
 */
private fun formatTimeSince(lastBrewTime: Date?): String? {
    if (lastBrewTime == null) return null

    val now = System.currentTimeMillis()
    val diffMillis = now - lastBrewTime.time

    val diffSeconds = TimeUnit.MILLISECONDS.toSeconds(diffMillis)
    val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
    val diffHours = TimeUnit.MILLISECONDS.toHours(diffMillis)
    val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)

    // Returnerar den största relevanta tidsenheten
    return when {
        diffSeconds < 60 -> "< 1 min"
        diffMinutes < 60 -> "$diffMinutes min"
        diffHours < 24 -> "$diffHours h"
        diffDays < 7 -> "$diffDays d"
        else -> {
            val weeks = diffDays / 7
            if (weeks >= 52) {
                val years = weeks / 52
                "$years y"
            } else {
                "$weeks w"
            }
        }
    }
}