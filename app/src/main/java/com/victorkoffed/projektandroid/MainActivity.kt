package com.victorkoffed.projektandroid

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.* // Importera alla filled ikoner
import androidx.compose.material.icons.outlined.* // Importera alla outlined ikoner för ovalda state
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector // <-- NY IMPORT
import androidx.compose.ui.unit.dp
// import androidx.compose.ui.unit.sp // Behövs ej för NavigationBarItem label
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.victorkoffed.projektandroid.domain.model.BleConnectionState // <-- NY IMPORT
import com.victorkoffed.projektandroid.ui.viewmodel.coffee.CoffeeImageViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.scale.ScaleViewModel
import com.victorkoffed.projektandroid.ui.screens.scale.ScaleConnectScreen
import com.victorkoffed.projektandroid.ui.theme.ProjektAndroidTheme
import com.victorkoffed.projektandroid.ui.viewmodel.grinder.GrinderViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.grinder.GrinderViewModelFactory
import com.victorkoffed.projektandroid.ui.screens.grinder.GrinderScreen
import com.victorkoffed.projektandroid.ui.viewmodel.bean.BeanViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.bean.BeanViewModelFactory
import com.victorkoffed.projektandroid.ui.screens.bean.BeanScreen
import com.victorkoffed.projektandroid.ui.viewmodel.method.MethodViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.method.MethodViewModelFactory
import com.victorkoffed.projektandroid.ui.screens.method.MethodScreen
import com.victorkoffed.projektandroid.data.repository.BookooScaleRepositoryImpl
import com.victorkoffed.projektandroid.ui.viewmodel.scale.ScaleViewModelFactory
import com.victorkoffed.projektandroid.ui.screens.brew.LiveBrewScreen
import com.victorkoffed.projektandroid.ui.screens.brew.BrewScreen
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewViewModelFactory
import com.victorkoffed.projektandroid.ui.screens.home.HomeScreen
import com.victorkoffed.projektandroid.ui.viewmodel.home.HomeViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.home.HomeViewModelFactory
import com.victorkoffed.projektandroid.ui.screens.brew.BrewDetailScreen
import kotlinx.coroutines.launch
// --- NYA IMPORTER ---
import com.victorkoffed.projektandroid.data.db.Bean
import com.victorkoffed.projektandroid.data.db.Method
// --- SLUT ---


// --- NY DATA CLASS för navigationsalternativ ---
data class NavItem(
    val label: String,
    val screenRoute: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)
// --- SLUT ---

class MainActivity : ComponentActivity() {

    // --- ViewModels ---
    private lateinit var scaleVm: ScaleViewModel
    private lateinit var grinderVm: GrinderViewModel
    private lateinit var beanVm: BeanViewModel
    private lateinit var methodVm: MethodViewModel
    private lateinit var brewVm: BrewViewModel
    private lateinit var homeVm: HomeViewModel
    private val coffeeImageVm: CoffeeImageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hämta repositories och skapa ViewModels
        val app = application as CoffeeJournalApplication
        val coffeeRepository = app.coffeeRepository
        val scaleRepository = BookooScaleRepositoryImpl(this)

        // Instansiera ViewModels via Factories
        val scaleViewModelFactory = ScaleViewModelFactory(app, scaleRepository, coffeeRepository)
        scaleVm = ViewModelProvider(this, scaleViewModelFactory)[ScaleViewModel::class.java]
        val grinderViewModelFactory = GrinderViewModelFactory(coffeeRepository)
        grinderVm = ViewModelProvider(this, grinderViewModelFactory)[GrinderViewModel::class.java]
        val beanViewModelFactory = BeanViewModelFactory(coffeeRepository)
        beanVm = ViewModelProvider(this, beanViewModelFactory)[BeanViewModel::class.java]
        val methodViewModelFactory = MethodViewModelFactory(coffeeRepository)
        methodVm = ViewModelProvider(this, methodViewModelFactory)[MethodViewModel::class.java]
        val brewViewModelFactory = BrewViewModelFactory(coffeeRepository)
        brewVm = ViewModelProvider(this, brewViewModelFactory)[BrewViewModel::class.java]
        val homeViewModelFactory = HomeViewModelFactory(coffeeRepository)
        homeVm = ViewModelProvider(this, homeViewModelFactory)[HomeViewModel::class.java]

        setContent {
            ProjektAndroidTheme {
                // State för navigering och dataöverföring
                var currentScreen by remember { mutableStateOf("home") }
                var lastBrewId by remember { mutableStateOf<Long?>(null) }
                var selectedBrewId by remember { mutableStateOf<Long?>(null) }

                // --- NYTT: Hämta listor för att kontrollera om setup är möjlig ---
                val availableBeans by brewVm.availableBeans.collectAsState()
                val availableMethods by brewVm.availableMethods.collectAsState()
                // --- SLUT ---

                // Funktion för att byta skärm
                val navigateToScreen: (String) -> Unit = { screenName ->
                    currentScreen = screenName
                }

                // --- Definiera navigationsalternativen ---
                val navItems = listOf(
                    NavItem("Home", "home", Icons.Filled.Home, Icons.Outlined.Home),
                    NavItem("Bean", "bean", Icons.Filled.Coffee, Icons.Outlined.Coffee),
                    NavItem("Method", "method", Icons.Filled.Science, Icons.Outlined.Science),
                    NavItem("Grinder", "grinder", Icons.Filled.Settings, Icons.Outlined.Settings)
                )
                // --- SLUT ---

                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "screenSwitch",
                            // Lägg till padding för bottom bar här om skärmen KAN ha nav bar
                            modifier = Modifier.fillMaxSize().padding(bottom = if (navItems.any { it.screenRoute == currentScreen }) 80.dp else 0.dp) // Cirka höjden på NavigationBar
                        ) { screen ->
                            when (screen) {
                                "home" -> HomeScreen(
                                    homeVm = homeVm,
                                    coffeeImageVm = coffeeImageVm,
                                    scaleVm = scaleVm,
                                    navigateToScreen = navigateToScreen,
                                    onNavigateToBrewSetup = {
                                        lastBrewId = null; brewVm.clearBrewResults()
                                        currentScreen = "brew_setup"
                                    },
                                    onBrewClick = { brewId ->
                                        selectedBrewId = brewId
                                        currentScreen = "brew_detail"
                                    },
                                    // --- NYA PARAMETRAR ---
                                    availableBeans = availableBeans,
                                    availableMethods = availableMethods
                                )
                                "scale" -> ScaleConnectScreen(
                                    vm = scaleVm,
                                    onNavigateBack = { navigateToScreen("home") } // Gå till home
                                )
                                "grinder" -> GrinderScreen(grinderVm)
                                "bean" -> BeanScreen(beanVm)
                                "method" -> MethodScreen(methodVm)
                                "brew_setup" -> BrewScreen(
                                    vm = brewVm,
                                    completedBrewId = lastBrewId,
                                    onStartBrewClick = { setupState ->
                                        lastBrewId = null
                                        brewVm.clearBrewResults()
                                        currentScreen = "live_brew"
                                    },
                                    onClearResult = {
                                        lastBrewId = null
                                    },
                                    onNavigateBack = { navigateToScreen("home") }
                                )
                                "live_brew" -> {
                                    val samples by scaleVm.recordedSamplesFlow.collectAsState()
                                    val time by scaleVm.recordingTimeMillis.collectAsState()
                                    val isRecording by scaleVm.isRecording.collectAsState()
                                    val isPaused by scaleVm.isPaused.collectAsState()
                                    val currentMeasurement by scaleVm.measurement.collectAsState()
                                    val weightAtPause by scaleVm.weightAtPause.collectAsState()
                                    // --- NY RAD: Hämta connection state ---
                                    val scaleConnectionState by scaleVm.connectionState.collectAsState()

                                    LiveBrewScreen(
                                        samples = samples,
                                        currentMeasurement = currentMeasurement,
                                        currentTimeMillis = time,
                                        isRecording = isRecording,
                                        isPaused = isPaused,
                                        weightAtPause = weightAtPause,
                                        // --- NY PARAMETER: Skicka med state ---
                                        connectionState = scaleConnectionState,
                                        onStartClick = { scaleVm.startRecording() },
                                        onPauseClick = { scaleVm.pauseRecording() },
                                        onResumeClick = { scaleVm.resumeRecording() },
                                        onStopAndSaveClick = {
                                            lifecycleScope.launch {
                                                val currentSetup = brewVm.getCurrentSetup()
                                                Log.d("MainActivity", "Saving brew with setup: $currentSetup")
                                                val savedBrewId = scaleVm.stopRecordingAndSave(currentSetup)
                                                lastBrewId = savedBrewId
                                                currentScreen = "brew_setup"
                                            }
                                        },
                                        onTareClick = { scaleVm.tareScale() },
                                        onNavigateBack = { currentScreen = "brew_setup" },
                                        onResetRecording = { scaleVm.stopRecording() } // Anropa funktionen vi gjorde public
                                    )
                                }
                                "brew_detail" -> {
                                    if (selectedBrewId != null) {
                                        BrewDetailScreen(
                                            brewId = selectedBrewId!!,
                                            onNavigateBack = {
                                                selectedBrewId = null
                                                currentScreen = "home"
                                            }
                                        )
                                    } else {
                                        Text("Error: Brew ID missing")
                                        LaunchedEffect(Unit) { currentScreen = "home" }
                                    }
                                }
                            }
                        }

                        // --- UPPDATERAD NAVIGATIONSRAD ---
                        // Visas endast på skärmar som finns i navItems
                        if (navItems.any { it.screenRoute == currentScreen }) {
                            NavigationBar(
                                modifier = Modifier.align(Alignment.BottomCenter)
                                // .navigationBarsPadding() // Kan behövas beroende på system-UI
                            ) {
                                navItems.forEach { item ->
                                    val isSelected = currentScreen == item.screenRoute
                                    NavigationBarItem(
                                        icon = {
                                            Icon(
                                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = item.label
                                            )
                                        },
                                        label = { Text(item.label) },
                                        selected = isSelected,
                                        onClick = { navigateToScreen(item.screenRoute) },
                                    )
                                }
                            }
                        }
                        // --- SLUT PÅ UPPDATERING ---
                    }
                }
            }
        }
    }
}