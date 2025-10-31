package com.victorkoffed.projektandroid.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.ui.screens.home.composable.InfoGrid
import com.victorkoffed.projektandroid.ui.screens.home.composable.NoBrewsTextWithIcon
import com.victorkoffed.projektandroid.ui.screens.home.composable.RecentBrewCard
import com.victorkoffed.projektandroid.ui.screens.home.composable.formatTimeSince
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.coffee.CoffeeImageViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.home.HomeViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.scale.ScaleViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


/**
 * Huvudskärmen för appen. Visar statistik, senaste bryggningar och anslutningsstatus.
 *
 * @param snackbarHostState Globalt state för att visa felmeddelanden.
 * @param onNavigateToBrewSetup Callback för att starta en ny bryggning.
 * @param onBrewClick Callback för att visa bryggdetaljer.
 * @param onMenuClick Callback för att öppna navigationslådan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    snackbarHostState: SnackbarHostState,
    onNavigateToBrewSetup: () -> Unit,
    onBrewClick: (Long) -> Unit,
    onMenuClick: () -> Unit,
    // Hämta ViewModels lokalt med hiltViewModel
    homeVm: HomeViewModel = hiltViewModel(),
    coffeeImageVm: CoffeeImageViewModel = hiltViewModel(),
    scaleVm: ScaleViewModel = hiltViewModel(),
    brewVm: BrewViewModel = hiltViewModel() // Behövs för validering
) {
    // --- Data från ViewModels (State Collection) ---
    val recentBrews by homeVm.recentBrews.collectAsState()
    val beansExplored by homeVm.beansExploredCount.collectAsState()
    val availableWeight by homeVm.totalAvailableBeanWeight.collectAsState()
    val lastBrewTime by homeVm.lastBrewTime.collectAsState()
    val totalBrews by homeVm.totalBrewCount.collectAsState()

    val imageUrl by coffeeImageVm.imageUrl
    val imageLoading by coffeeImageVm.loading
    val imageError by coffeeImageVm.error

    var timeSinceLastCoffee by remember { mutableStateOf<String?>("...") }

    val scaleConnectionState by scaleVm.connectionState.collectAsState(
        initial = scaleVm.connectionState.replayCache.lastOrNull() ?: BleConnectionState.Disconnected
    )
    val rememberedScaleAddress by scaleVm.rememberedScaleAddress.collectAsState()

    // Hämta states från brewVm för validering
    val availableBeans by brewVm.availableBeans.collectAsState()
    val availableMethods by brewVm.availableMethods.collectAsState()

    var showSetupWarningDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Logik: En bryggning får endast startas om minst en böna och en metod finns.
    val isBrewSetupEnabled = availableBeans.isNotEmpty() && availableMethods.isNotEmpty()

    // Definierar den villkorliga åtgärden för att starta bryggning
    val startBrewAction = {
        if (isBrewSetupEnabled) {
            brewVm.clearBrewResults() // Nollställ brewVm innan navigering
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
        while (true) {
            // Använd den utbrutna hjälpfunktionen
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
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Home") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    // Knapp för att starta ny bryggning
                    Surface(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable(onClick = startBrewAction),
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New brew",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
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
                // ANROP TILL UTBRUTEN KOMPONENT: Statistikrutnätet
                InfoGrid(
                    totalBrews = totalBrews,
                    beansExplored = beansExplored,
                    availableWeight = availableWeight,
                    imageUrl = imageUrl,
                    imageLoading = imageLoading,
                    imageError = imageError,
                    timeSinceLastCoffee = timeSinceLastCoffee ?: "∞",
                    scaleConnectionState = scaleConnectionState,
                    rememberedScaleAddress = rememberedScaleAddress,
                    onReloadImage = { coffeeImageVm.loadRandomCoffeeImage() },
                    onRetryScaleConnect = { scaleVm.retryConnection() }
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
                    // ANROP TILL UTBRUTEN KOMPONENT: Placeholder
                    NoBrewsTextWithIcon(
                        modifier = Modifier.padding(vertical = 16.dp),
                        onStartBrewClick = startBrewAction
                    )
                }
            }
            else {
                // Lista över de senaste bryggningarna
                items(recentBrews) { brewItem ->
                    // ANROP TILL UTBRUTEN KOMPONENT: Kort för senaste bryggning
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
            onDismissRequest = { },
            title = { Text("Cannot start brew") },
            text = { Text("You must first add at least one bean (under 'Bean') and one brewing method (under 'Method') before you can start a new brew") },
            confirmButton = {
                TextButton(onClick = { }) {
                    Text("Understood")
                }
            }
        )
    }
}