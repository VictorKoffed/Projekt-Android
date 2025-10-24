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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.victorkoffed.projektandroid.domain.model.BleConnectionState // <-- KONTROLLERA DENNA IMPORT
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
// --- NYA IMPORTER ---
import com.victorkoffed.projektandroid.ui.screens.bean.BeanDetailScreen
// --- SLUT NYA IMPORTER ---
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
import com.victorkoffed.projektandroid.data.db.Bean
import com.victorkoffed.projektandroid.data.db.Method
// --- NYA IMPORTER FÖR KAMERA ---
import androidx.lifecycle.viewmodel.compose.viewModel
import com.victorkoffed.projektandroid.ui.screens.brew.CameraScreen
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewDetailViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewDetailViewModelFactory
// --- SLUT NYA IMPORTER ---


// --- ÅTERSTÄLLD DATA CLASS för navigationsalternativ ---
data class NavItem(
    val label: String,
    val screenRoute: String,
    val selectedIcon: ImageVector, // Filled ikon
    val unselectedIcon: ImageVector // Outlined ikon
)
// --- SLUT ÅTERSTÄLLD ---

val MockupColor = Color(0xFFDCC7AA)

class MainActivity : ComponentActivity() {

    // --- View Model definitioner ---
    private lateinit var scaleVm: ScaleViewModel
    private lateinit var grinderVm: GrinderViewModel
    private lateinit var beanVm: BeanViewModel
    private lateinit var methodVm: MethodViewModel
    private lateinit var brewVm: BrewViewModel
    private lateinit var homeVm: HomeViewModel
    private val coffeeImageVm: CoffeeImageViewModel by viewModels()

    // --- NYTT: Håll i appen för att skapa factories ---
    private lateinit var app: CoffeeJournalApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- Hämta repositories och skapa ViewModels ---
        app = application as CoffeeJournalApplication // <-- SPARA app-instansen
        val coffeeRepository = app.coffeeRepository
        val scaleRepository = BookooScaleRepositoryImpl(this)

        // --- Instansiera ViewModels via Factories ---
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
                // --- State för navigering och dataöverföring ---
                var currentScreen by remember { mutableStateOf("home") }
                var lastBrewId by remember { mutableStateOf<Long?>(null) }
                var selectedBrewId by remember { mutableStateOf<Long?>(null) }
                var selectedBeanId by remember { mutableStateOf<Long?>(null) }

                var navigationOrigin by remember { mutableStateOf("home") }

                // --- NYTT STATE FÖR BILD ---
                var tempCapturedImageUri by remember { mutableStateOf<String?>(null) }
                // --- SLUT NYTT STATE ---

                // --- Hämta listor för att kontrollera om setup är möjlig ---
                val availableBeans by brewVm.availableBeans.collectAsState()
                val availableMethods by brewVm.availableMethods.collectAsState()

                // --- Hämta vågens anslutningsstatus ---
                val scaleConnectionState by scaleVm.connectionState.collectAsState()

                // --- Funktion för att byta skärm ---
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
                                        brewVm.clearBrewResults()
                                        lastBrewId = null
                                        currentScreen = "brew_setup"
                                    },
                                    onBrewClick = { brewId ->
                                        navigationOrigin = "home"
                                        selectedBrewId = brewId
                                        currentScreen = "brew_detail"
                                    },
                                    availableBeans = availableBeans,
                                    availableMethods = availableMethods
                                )
                                "scale" -> ScaleConnectScreen(
                                    vm = scaleVm,
                                    onNavigateBack = { navigateToScreen("home") }
                                )
                                "grinder" -> GrinderScreen(grinderVm)

                                "bean" -> BeanScreen(
                                    vm = beanVm,
                                    onBeanClick = { beanId ->
                                        selectedBeanId = beanId
                                        currentScreen = "bean_detail"
                                    }
                                )

                                "method" -> MethodScreen(methodVm)

                                "brew_setup" -> BrewScreen(
                                    vm = brewVm,
                                    completedBrewId = null,
                                    scaleConnectionState = scaleConnectionState,
                                    onStartBrewClick = { setupState ->
                                        brewVm.clearBrewResults()
                                        currentScreen = "live_brew"
                                    },
                                    onSaveWithoutGraph = {
                                        lifecycleScope.launch {
                                            val newBrewId = brewVm.saveBrewWithoutSamples()
                                            if (newBrewId != null) {
                                                navigationOrigin = "brew_setup" // Sätt ursprung
                                                selectedBrewId = newBrewId
                                                currentScreen = "brew_detail"
                                            } else {
                                                Log.e("MainActivity", "Kunde inte spara bryggning utan graf.")
                                            }
                                        }
                                    },
                                    onNavigateToScale = {
                                        navigateToScreen("scale")
                                    },
                                    onClearResult = { },
                                    onNavigateBack = { navigateToScreen("home") }
                                )

                                "live_brew" -> {
                                    val samples by scaleVm.recordedSamplesFlow.collectAsState()
                                    val time by scaleVm.recordingTimeMillis.collectAsState()
                                    val isRecording by scaleVm.isRecording.collectAsState()
                                    val isPaused by scaleVm.isPaused.collectAsState()
                                    val currentMeasurement by scaleVm.measurement.collectAsState()
                                    val weightAtPause by scaleVm.weightAtPause.collectAsState()
                                    val countdown by scaleVm.countdown.collectAsState()

                                    LiveBrewScreen(
                                        samples = samples,
                                        currentMeasurement = currentMeasurement,
                                        currentTimeMillis = time,
                                        isRecording = isRecording,
                                        isPaused = isPaused,
                                        weightAtPause = weightAtPause,
                                        connectionState = scaleConnectionState,
                                        countdown = countdown,
                                        onStartClick = { scaleVm.startRecording() },
                                        onPauseClick = { scaleVm.pauseRecording() },
                                        onResumeClick = { scaleVm.resumeRecording() },
                                        onStopAndSaveClick = {
                                            lifecycleScope.launch {
                                                val currentSetup = brewVm.getCurrentSetup()
                                                val savedBrewId = scaleVm.stopRecordingAndSave(currentSetup)
                                                if (savedBrewId != null) {
                                                    navigationOrigin = "live_brew" // Sätt ursprung
                                                    selectedBrewId = savedBrewId
                                                    currentScreen = "brew_detail"
                                                } else {
                                                    Log.w("MainActivity", "Save cancelled or failed, returning to setup.")
                                                    currentScreen = "brew_setup"
                                                }
                                            }
                                        },
                                        onTareClick = { scaleVm.tareScale() },
                                        onNavigateBack = { navigateToScreen("scale") },
                                        onResetRecording = { scaleVm.stopRecording() },
                                        navigateTo = navigateToScreen
                                    )
                                }

                                "brew_detail" -> {
                                    if (selectedBrewId != null) {
                                        // --- NYTT: Hämta VM och kör LaunchedEffect ---
                                        val vm: BrewDetailViewModel = viewModel(
                                            key = selectedBrewId.toString(),
                                            factory = BrewDetailViewModelFactory(app.coffeeRepository, selectedBrewId!!)
                                        )

                                        // Hantera en nyss tagen bild
                                        LaunchedEffect(tempCapturedImageUri) {
                                            if (tempCapturedImageUri != null) {
                                                Log.d("MainActivity", "Hanterar ny bild-URI: $tempCapturedImageUri")
                                                vm.updateBrewImageUri(tempCapturedImageUri)
                                                tempCapturedImageUri = null // Nollställ efter hantering
                                            }
                                        }

                                        BrewDetailScreen(
                                            brewId = selectedBrewId!!,
                                            onNavigateBack = {
                                                selectedBrewId = null
                                                currentScreen = navigationOrigin
                                            },
                                            onNavigateToCamera = {
                                                currentScreen = "camera_screen"
                                            }
                                        )
                                        // --- SLUT PÅ NYTT ---
                                    } else {
                                        Text("Error: Brew ID missing")
                                    }
                                }

                                // --- NYTT CASE FÖR KAMERAN ---
                                "camera_screen" -> {
                                    CameraScreen(
                                        onImageCaptured = { uri ->
                                            Log.d("MainActivity", "Bild tagen: $uri")
                                            tempCapturedImageUri = uri.toString() // Spara URI:n
                                            currentScreen = "brew_detail" // Gå tillbaka
                                        },
                                        onNavigateBack = {
                                            currentScreen = "brew_detail" // Gå tillbaka
                                        }
                                    )
                                }
                                // --- SLUT NYTT CASE ---

                                "bean_detail" -> {
                                    if (selectedBeanId != null) {
                                        BeanDetailScreen(
                                            beanId = selectedBeanId!!,
                                            onNavigateBack = {
                                                selectedBeanId = null
                                                currentScreen = "bean"
                                            },
                                            onBrewClick = { brewId ->
                                                navigationOrigin = "bean_detail" // Sätt ursprung
                                                selectedBrewId = brewId
                                                currentScreen = "brew_detail"
                                            }
                                        )
                                    } else {
                                        // Fallback om ID saknas
                                        Text("Error: Bean ID missing")
                                        LaunchedEffect(Unit) { currentScreen = "bean" }
                                    }
                                }
                                // --- SLUT NYTT CASE ---
                            }
                        }

                        // --- NavigationBar ---
                        if (navItems.any { it.screenRoute == currentScreen }) {
                            NavigationBar(
                                modifier = Modifier.align(Alignment.BottomCenter)
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
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MockupColor,
                                            selectedTextColor = Color.Black,
                                            unselectedIconColor = Color.Black,
                                            unselectedTextColor = Color.Black,
                                            indicatorColor = Color.Transparent
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}