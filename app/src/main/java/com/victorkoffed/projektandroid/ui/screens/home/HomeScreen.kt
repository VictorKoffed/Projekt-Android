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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.ui.screens.home.composable.InfoGrid
import com.victorkoffed.projektandroid.ui.screens.home.composable.NoBrewsTextWithIcon
import com.victorkoffed.projektandroid.ui.screens.home.composable.RecentBrewCard
import com.victorkoffed.projektandroid.ui.screens.home.composable.formatTimeSince
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewSetupViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.coffee.CoffeeImageViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.home.HomeViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.scale.ScaleViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    snackbarHostState: SnackbarHostState,
    onNavigateToBrewSetup: () -> Unit,
    onBrewClick: (Long) -> Unit,
    onMenuClick: () -> Unit,
    scaleVm: ScaleViewModel,
    homeVm: HomeViewModel = hiltViewModel(),
    coffeeImageVm: CoffeeImageViewModel = hiltViewModel(),
    brewVm: BrewSetupViewModel = hiltViewModel() // Uppdaterad till BrewSetupViewModel
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
            brewVm.clearForm() // Byt namn från clearBrewResults
            onNavigateToBrewSetup()
        } else {
            showSetupWarningDialog = true
        }
    }

    // --- Launched Effects (Sid-effekter) ---

    LaunchedEffect(Unit) {
        if (imageUrl == null) {
            coffeeImageVm.loadRandomCoffeeImage()
        }
    }

    LaunchedEffect(lastBrewTime) {
        while (true) {
            timeSinceLastCoffee = formatTimeSince(lastBrewTime)
            delay(60000)
        }
    }

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
            if (recentBrews.isEmpty()) {
                item {
                    NoBrewsTextWithIcon(
                        modifier = Modifier.padding(vertical = 16.dp),
                        onStartBrewClick = startBrewAction
                    )
                }
            }
            else {
                items(recentBrews) { brewItem ->
                    RecentBrewCard(
                        brewItem = brewItem,
                        onClick = { onBrewClick(brewItem.brew.id) }
                    )
                }
            }
        }
    }

    if (showSetupWarningDialog) {
        AlertDialog(
            onDismissRequest = { showSetupWarningDialog = false }, // Tillåt att stänga
            title = { Text("Cannot start brew") },
            text = { Text("You must first add at least one bean (under 'Bean') and one brewing method (under 'Method') before you can start a new brew") },
            confirmButton = {
                TextButton(onClick = { showSetupWarningDialog = false }) { // Stäng vid klick
                    Text("Understood")
                }
            }
        )
    }
}