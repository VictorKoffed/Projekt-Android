package com.victorkoffed.projektandroid

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.victorkoffed.projektandroid.data.repository.BookooScaleRepositoryImpl
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.ui.navigation.Screen
import com.victorkoffed.projektandroid.ui.screens.bean.BeanDetailScreen
import com.victorkoffed.projektandroid.ui.screens.bean.BeanScreen
import com.victorkoffed.projektandroid.ui.screens.brew.BrewDetailScreen
import com.victorkoffed.projektandroid.ui.screens.brew.BrewScreen
import com.victorkoffed.projektandroid.ui.screens.brew.CameraScreen
import com.victorkoffed.projektandroid.ui.screens.brew.FullscreenImageScreen
import com.victorkoffed.projektandroid.ui.screens.brew.LiveBrewScreen
import com.victorkoffed.projektandroid.ui.screens.grinder.GrinderScreen
import com.victorkoffed.projektandroid.ui.screens.home.HomeScreen
import com.victorkoffed.projektandroid.ui.screens.method.MethodScreen
import com.victorkoffed.projektandroid.ui.screens.scale.ScaleConnectScreen
import com.victorkoffed.projektandroid.ui.theme.ProjektAndroidTheme
import com.victorkoffed.projektandroid.ui.viewmodel.bean.BeanViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.bean.BeanViewModelFactory
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewDetailViewModelFactory
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewViewModelFactory
import com.victorkoffed.projektandroid.ui.viewmodel.coffee.CoffeeImageViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.grinder.GrinderViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.grinder.GrinderViewModelFactory
import com.victorkoffed.projektandroid.ui.viewmodel.home.HomeViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.home.HomeViewModelFactory
import com.victorkoffed.projektandroid.ui.viewmodel.method.MethodViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.method.MethodViewModelFactory
import com.victorkoffed.projektandroid.ui.viewmodel.scale.ScaleViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.scale.ScaleViewModelFactory
import kotlinx.coroutines.launch

// --- ÅTERSTÄLLD DATA CLASS för navigationsalternativ ---
data class NavItem(
    val label: String,
    val screenRoute: String,
    val selectedIcon: ImageVector, // Filled ikon
    val unselectedIcon: ImageVector // Outlined ikon
)
// --- SLUT ÅTERSTÄLLD ---

// REMOVED: val MockupColor = Color(0xFFDCC7AA)

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
                // --- NYTT: NavController och state för bottenmenyn ---
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val navItems = listOf(
                    NavItem("Home", Screen.Home.route, Icons.Filled.Home, Icons.Outlined.Home),
                    NavItem("Bean", Screen.BeanList.route, Icons.Filled.Coffee, Icons.Outlined.Coffee),
                    NavItem("Method", Screen.MethodList.route, Icons.Filled.Science, Icons.Outlined.Science),
                    NavItem("Grinder", Screen.GrinderList.route, Icons.Filled.Settings, Icons.Outlined.Settings)
                )

                // Lista över rutter som ska visa bottenmenyn
                val bottomBarRoutes = navItems.map { it.screenRoute }

                // --- Hämta globala states ---
                val availableBeans by brewVm.availableBeans.collectAsState()
                val availableMethods by brewVm.availableMethods.collectAsState()
                val scaleConnectionState by scaleVm.connectionState.collectAsState(
                    initial = scaleVm.connectionState.replayCache.lastOrNull() ?: BleConnectionState.Disconnected
                )

                // --- NYTT: SnackbarHostState och Scope ---
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                // --- SLUT NYTT ---

                // --- NYTT: Scaffold hanterar nu bottenmenyn ---
                Scaffold(
                    bottomBar = {
                        // Visa bara bottenmenyn om vi är på en av huvudskärmarna
                        if (bottomBarRoutes.contains(currentRoute)) {
                            NavigationBar {
                                navItems.forEach { item ->
                                    val isSelected = currentRoute == item.screenRoute
                                    NavigationBarItem(
                                        icon = {
                                            Icon(
                                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = item.label
                                            )
                                        },
                                        label = { Text(item.label) },
                                        selected = isSelected,
                                        onClick = {
                                            navController.navigate(item.screenRoute) {
                                                // Pop up to the start destination to avoid building a large stack
                                                popUpTo(navController.graph.startDestinationId)
                                                launchSingleTop = true
                                            }
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary, // FIX: Use Theme Color
                                            selectedTextColor = Color.Black,
                                            unselectedIconColor = Color.Black,
                                            unselectedTextColor = Color.Black,
                                            indicatorColor = Color.Transparent
                                        )
                                    )
                                }
                            }
                        }
                    },
                    // --- NYTT: Lade till snackbarHost ---
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                    // --- SLUT NYTT ---
                ) { innerPadding ->
                    // --- NYTT: NavHost ersätter AnimatedContent ---
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route,
                        modifier = Modifier.padding(innerPadding) // Applicera padding från Scaffold
                    ) {

                        // --- Home ---
                        composable(Screen.Home.route) {
                            HomeScreen(
                                homeVm = homeVm,
                                coffeeImageVm = coffeeImageVm,
                                scaleVm = scaleVm,
                                // --- NYTT: Skicka med snackbarHostState ---
                                snackbarHostState = snackbarHostState,
                                // --- SLUT NYTT ---
                                navigateToScreen = { screenName -> navController.navigate(screenName) },
                                onNavigateToBrewSetup = {
                                    brewVm.clearBrewResults()
                                    navController.navigate(Screen.BrewSetup.route)
                                },
                                onBrewClick = { brewId ->
                                    navController.navigate(Screen.BrewDetail.createRoute(brewId))
                                },
                                availableBeans = availableBeans,
                                availableMethods = availableMethods
                            )
                        }

                        // --- Huvudmenyns skärmar ---
                        composable(Screen.BeanList.route) {
                            BeanScreen(
                                vm = beanVm,
                                onBeanClick = { beanId ->
                                    navController.navigate(Screen.BeanDetail.createRoute(beanId))
                                }
                            )
                        }
                        composable(Screen.GrinderList.route) { GrinderScreen(grinderVm) }
                        composable(Screen.MethodList.route) { MethodScreen(methodVm) }

                        // --- Våg ---
                        composable(Screen.ScaleConnect.route) {
                            ScaleConnectScreen(
                                vm = scaleVm,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        // --- Flöde för ny bryggning ---
                        composable(Screen.BrewSetup.route) {
                            BrewScreen(
                                vm = brewVm,
                                completedBrewId = null,
                                scaleConnectionState = scaleConnectionState,
                                onStartBrewClick = { setupState ->
                                    brewVm.clearBrewResults()
                                    navController.navigate(Screen.LiveBrew.route)
                                },
                                onSaveWithoutGraph = {
                                    lifecycleScope.launch {
                                        val newBrewId = brewVm.saveBrewWithoutSamples()
                                        if (newBrewId != null) {
                                            // Navigera till detalj och rensa setup-skärmen från stacken
                                            navController.navigate(Screen.BrewDetail.createRoute(newBrewId)) {
                                                popUpTo(Screen.BrewSetup.route) { inclusive = true }
                                            }
                                        } else {
                                            Log.e("MainActivity", "Kunde inte spara bryggning utan graf.")
                                            // --- NYTT: Visa felmeddelande ---
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Could not save brew. Check inputs.")
                                            }
                                            // --- SLUT NYTT ---
                                        }
                                    }
                                },
                                onNavigateToScale = { navController.navigate(Screen.ScaleConnect.route) },
                                onClearResult = { },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.LiveBrew.route) {
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
                                            // Navigera till detalj och rensa setup-skärmen från stacken
                                            navController.navigate(Screen.BrewDetail.createRoute(savedBrewId)) {
                                                popUpTo(Screen.BrewSetup.route) { inclusive = true }
                                            }
                                        } else {
                                            Log.w("MainActivity", "Save cancelled or failed, returning to setup.")
                                            // --- NYTT: Visa fel om det finns ett (från ScaleVM) ---
                                            val errorMsg = scaleVm.error.value
                                            if(errorMsg != null) {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(errorMsg)
                                                    scaleVm.clearError() // Nollställ felet
                                                }
                                            }
                                            // --- SLUT NYTT ---
                                            navController.popBackStack() // Gå tillbaka till BrewSetup
                                        }
                                    }
                                },
                                onTareClick = { scaleVm.tareScale() },
                                onNavigateBack = { navController.popBackStack() },
                                onResetRecording = { scaleVm.stopRecording() },
                                navigateTo = { route -> navController.navigate(route) }
                            )
                        }

                        // --- Detalj-skärmar (med argument) ---
                        composable(
                            route = Screen.BrewDetail.route,
                            arguments = listOf(navArgument("brewId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val brewId = backStackEntry.arguments?.getLong("brewId")
                            if (brewId != null) {
                                BrewDetailScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToCamera = { navController.navigate(Screen.Camera.route) },
                                    // --- ÄNDRING: Implementera helskärmsnavigering ---
                                    onNavigateToImageFullscreen = { uri ->
                                        // Måste URL-koda URI:n
                                        val encodedUri = Uri.encode(uri)
                                        navController.navigate(Screen.ImageFullscreen.createRoute(encodedUri))
                                    },
                                    // --- SLUT ÄNDRING ---

                                    // NYTT: Hämta VM scoped till denna route
                                    viewModel = viewModel(
                                        key = "brewDetail_$brewId", // Unik nyckel för denna bryggning
                                        factory = BrewDetailViewModelFactory(app.coffeeRepository, brewId)
                                    ),

                                    // NYTT: Skicka med backStackEntry för att ta emot resultat
                                    navBackStackEntry = backStackEntry
                                )
                            } else {
                                // Detta ska inte hända om navigeringen görs rätt
                                Text("Error: Brew ID saknas.")
                                LaunchedEffect(Unit) { navController.popBackStack() }
                            }
                        }

                        composable(
                            route = Screen.BeanDetail.route,
                            arguments = listOf(navArgument("beanId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val beanId = backStackEntry.arguments?.getLong("beanId")
                            if (beanId != null) {
                                BeanDetailScreen(
                                    beanId = beanId,
                                    onNavigateBack = { navController.popBackStack() },
                                    onBrewClick = { brewId ->
                                        navController.navigate(Screen.BrewDetail.createRoute(brewId))
                                    }
                                )
                            } else {
                                // Detta ska inte hända
                                Text("Error: Bean ID saknas.")
                                LaunchedEffect(Unit) { navController.popBackStack() }
                            }
                        }

                        // --- Kamera ---
                        composable(Screen.Camera.route) {
                            CameraScreen(
                                onImageCaptured = { uri ->
                                    // Skicka tillbaka URI:n till föregående skärm (BrewDetail)
                                    navController.previousBackStackEntry
                                        ?.savedStateHandle
                                        ?.set("captured_image_uri", uri.toString())
                                    navController.popBackStack()
                                },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        // --- NYTT: Helskärmsvy för bild ---
                        composable(
                            route = Screen.ImageFullscreen.route,
                            arguments = listOf(navArgument("uri") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val encodedUri = backStackEntry.arguments?.getString("uri")
                            val uri = encodedUri?.let { Uri.decode(it) } // Avkoda URI:n

                            if (uri != null) {
                                FullscreenImageScreen(
                                    uri = uri,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            } else {
                                Log.e("MainActivity", "Error: URI argument for FullscreenImageScreen missing or invalid.")
                                LaunchedEffect(Unit) { navController.popBackStack() }
                            }
                        }
                        // --- SLUT NYTT ---
                    } // Slut på NavHost
                } // Slut på Scaffold
            }
        }
    }
}