package com.victorkoffed.projektandroid

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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

// Dataklass som definierar varje objekt i bottennavigeringen.
data class NavItem(
    val label: String,
    val screenRoute: String,
    val selectedIcon: ImageVector, // Ikon när objektet är valt.
    val unselectedIcon: ImageVector // Ikon när objektet inte är valt.
)

/**
 * Anpassad Snackbar-komponent som använder appens MaterialTheme färger.
 * Detta ger ett konsekvent utseende.
 */
@Composable
fun ThemedSnackbar(data: SnackbarData) {
    Snackbar(
        snackbarData = data,
        // Använder primärfärgen (DCC7AA) som bakgrund
        containerColor = MaterialTheme.colorScheme.primary,
        // Använder onPrimary (Svart/Vit beroende på tema) som textfärg
        contentColor = MaterialTheme.colorScheme.onPrimary,
        actionColor = MaterialTheme.colorScheme.onPrimary
    )
}

/**
 * Huvudaktiviteten i applikationen.
 * Hanterar Compose-navigeringen och instansierar alla ViewModels via deras fabriker.
 */
class MainActivity : ComponentActivity() {

    // Instanser av ViewModels. Används för att injicera till Compose-funktioner via "viewModel()" i NavHost.
    private lateinit var scaleVm: ScaleViewModel
    private lateinit var grinderVm: GrinderViewModel
    private lateinit var beanVm: BeanViewModel
    private lateinit var methodVm: MethodViewModel
    private lateinit var brewVm: BrewViewModel
    private lateinit var homeVm: HomeViewModel
    // ViewModels som initialiseras via delegation och är tillgängliga för hela aktiviteten.
    private val coffeeImageVm: CoffeeImageViewModel by viewModels()

    // Referens till applikationsinstansen för att komma åt repositories.
    private lateinit var app: CoffeeJournalApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hämta repositories och ViewModels från Application-instansen.
        app = application as CoffeeJournalApplication
        val coffeeRepository = app.coffeeRepository
        val scaleRepository = BookooScaleRepositoryImpl(this) // Specifik implementering för Bookoo Scale

        // Instansiera ViewModels med korrekta beroenden.
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
            // 1. STATE FÖR MÖRKT LÄGE (Använd DataStore/SharedPreferences för permanent lagring)
            val systemTheme = isSystemInDarkTheme()
            // Initialt tillstånd: spegla systemets, men kan ändras manuellt
            var isDarkModeManual by remember { mutableStateOf(systemTheme) }

            // 2. TILLÄMPA TEMAT MED DET MANUELLA TILLSTÅNDET
            ProjektAndroidTheme(darkTheme = isDarkModeManual) {

                // Konfigurerar Compose-navigering
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // Definierar objekten i bottenmenyn.
                val navItems = listOf(
                    NavItem("Home", Screen.Home.route, Icons.Filled.Home, Icons.Outlined.Home),
                    NavItem("Bean", Screen.BeanList.route, Icons.Filled.Coffee, Icons.Outlined.Coffee),
                    NavItem("Method", Screen.MethodList.route, Icons.Filled.Science, Icons.Outlined.Science),
                    NavItem("Grinder", Screen.GrinderList.route, Icons.Filled.Settings, Icons.Outlined.Settings)
                )

                // Enkel lista över rutter som ska visa bottenmenyn.
                val bottomBarRoutes = navItems.map { it.screenRoute }

                // Observerar state för tillgängliga bönor, metoder och våganslutning.
                val availableBeans by brewVm.availableBeans.collectAsState()
                val availableMethods by brewVm.availableMethods.collectAsState()
                val scaleConnectionState by scaleVm.connectionState.collectAsState(
                    initial = scaleVm.connectionState.replayCache.lastOrNull() ?: BleConnectionState.Disconnected
                )

                // State och scope för att visa SnackBar med meddelanden.
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                // Drawer State
                val drawerState = rememberDrawerState(DrawerValue.Closed) // NEW

                // 3. IMPLEMENTERA MODAL NAVIGATION DRAWER
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    // Aktivera svepgest endast på huvudskärmarna
                    gesturesEnabled = bottomBarRoutes.contains(currentRoute),
                    drawerContent = {
                        ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                            // Rubrik
                            Text(
                                "Settings",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(16.dp)
                            )
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp), DividerDefaults.Thickness, MaterialTheme.colorScheme.outline)

                            // Dark Mode Toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isDarkModeManual = !isDarkModeManual }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Dark Mode (Manual)")
                                Switch(
                                    checked = isDarkModeManual,
                                    onCheckedChange = { isDarkModeManual = it }
                                )
                            }
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp), DividerDefaults.Thickness, MaterialTheme.colorScheme.outline)
                        }
                    }
                ) { // SLUT ModalNavigationDrawer

                    // Scaffold sätter upp den grundläggande layoutstrukturen (TopBar, BottomBar, Content).
                    Scaffold(
                        bottomBar = {
                            // Visar NavigationBar om den aktuella rutten är en av huvudmenyskärmarna.
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
                                                // Navigerar till vald skärm.
                                                navController.navigate(item.screenRoute) {
                                                    // Förhindrar att navigeringsstacken byggs upp för stora
                                                    popUpTo(navController.graph.startDestinationId)
                                                    launchSingleTop = true
                                                }
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                                // FIX 1: Använd tematisk kontrastfärg för vald text. (Blir svart i ljust, vit i mörkt)
                                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                                // FIX 2: Använd tematisk kontrastfärg för icke-valda ikoner.
                                                unselectedIconColor = MaterialTheme.colorScheme.onSurface,
                                                // FIX 3: Använd tematisk kontrastfärg för icke-valda text.
                                                unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                                                indicatorColor = Color.Transparent
                                            )
                                        )
                                    }
                                }
                            }
                        },
                        // Visar meddelanden över innehållet (t.ex. vid fel eller bekräftelse).
                        snackbarHost = {
                            SnackbarHost(
                                snackbarHostState,
                                snackbar = { snackbarData ->
                                    ThemedSnackbar(snackbarData) // Använder den tematiska SnackBar-komponenten.
                                }
                            )
                        }
                    ) { innerPadding ->
                        // NavHost hanterar bytet av Compose-skärmar baserat på aktuell rutt.
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Home.route,
                            modifier = Modifier.padding(innerPadding) // Applicerar padding från Scaffold (inklusive BottomBar).
                        ) {

                            // --- Home ---
                            composable(Screen.Home.route) {
                                HomeScreen(
                                    homeVm = homeVm,
                                    coffeeImageVm = coffeeImageVm,
                                    scaleVm = scaleVm,
                                    snackbarHostState = snackbarHostState, // Skickar med state för att visa SnackBar
                                    navigateToScreen = { screenName -> navController.navigate(screenName) },
                                    onNavigateToBrewSetup = {
                                        brewVm.clearBrewResults()
                                        navController.navigate(Screen.BrewSetup.route)
                                    },
                                    onBrewClick = { brewId ->
                                        navController.navigate(Screen.BrewDetail.createRoute(brewId))
                                    },
                                    availableBeans = availableBeans,
                                    availableMethods = availableMethods,
                                    // NYTT: Callback för att öppna navigeringslådan
                                    onMenuClick = {
                                        scope.launch { drawerState.open() }
                                    }
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

                            // --- Flöde för ny bryggning (Setup) ---
                            composable(Screen.BrewSetup.route) {
                                BrewScreen(
                                    vm = brewVm,
                                    completedBrewId = null,
                                    scaleConnectionState = scaleConnectionState,
                                    onStartBrewClick = {
                                        brewVm.clearBrewResults()
                                        navController.navigate(Screen.LiveBrew.route)
                                    },
                                    onSaveWithoutGraph = {
                                        // Använd aktivitetens scope för att spara.
                                        lifecycleScope.launch {
                                            val newBrewId = brewVm.saveBrewWithoutSamples()
                                            if (newBrewId != null) {
                                                // Navigera till detaljskärm och rensa setup-skärmen från stacken.
                                                navController.navigate(Screen.BrewDetail.createRoute(newBrewId)) {
                                                    popUpTo(Screen.BrewSetup.route) { inclusive = true }
                                                }
                                            } else {
                                                Log.e("MainActivity", "Kunde inte spara bryggning utan graf.")
                                                // Visa SnackBar vid fel.
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Could not save brew. Check inputs.")
                                                }
                                            }
                                        }
                                    },
                                    onNavigateToScale = { navController.navigate(Screen.ScaleConnect.route) },
                                    onClearResult = { /* No-op */ },
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            // --- Flöde för ny bryggning (Live) ---
                            composable(Screen.LiveBrew.route) {
                                // Samlar in all nödvändig state för vågen.
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
                                        // Använd aktivitetens scope för att stoppa och spara.
                                        lifecycleScope.launch {
                                            val currentSetup = brewVm.getCurrentSetup()
                                            val savedBrewId = scaleVm.stopRecordingAndSave(currentSetup)
                                            if (savedBrewId != null) {
                                                // Navigera till detaljskärm och rensa LiveBrew/BrewSetup från stacken.
                                                navController.navigate(Screen.BrewDetail.createRoute(savedBrewId)) {
                                                    popUpTo(Screen.BrewSetup.route) { inclusive = true }
                                                }
                                            } else {
                                                Log.w("MainActivity", "Save cancelled or failed, returning to setup.")
                                                // Visar felmeddelande från ScaleVM om inspelningen misslyckades.
                                                val errorMsg = scaleVm.error.value
                                                if(errorMsg != null) {
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar(errorMsg)
                                                        scaleVm.clearError() // Nollställ felet efter visning.
                                                    }
                                                }
                                                navController.popBackStack() // Gå tillbaka till BrewSetup.
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
                                        // Navigerar till helskärmsvy. URI:n måste URL-kodas för att skickas som argument.
                                        onNavigateToImageFullscreen = { uri ->
                                            val encodedUri = Uri.encode(uri)
                                            navController.navigate(Screen.ImageFullscreen.createRoute(encodedUri))
                                        },
                                        // Använder 'viewModel' med en unik nyckel och fabrik, vilket säkerställer
                                        // att BrewDetailViewModel är scope:ad till denna specifika route.
                                        viewModel = viewModel(
                                            key = "brewDetail_$brewId",
                                            factory = BrewDetailViewModelFactory(app.coffeeRepository, brewId)
                                        ),
                                        // Skickar med backStackEntry för att kunna ta emot resultat (t.ex. bild-URI från CameraScreen).
                                        navBackStackEntry = backStackEntry
                                    )
                                } else {
                                    Log.e("MainActivity", "Brew ID saknas vid navigering till BrewDetail.")
                                    LaunchedEffect(Unit) { navController.popBackStack() }
                                }
                            }

                            composable(
                                route = Screen.BeanDetail.route,
                                arguments = listOf(navArgument("beanId") { type = NavType.LongType })
                            ) { backStackEntry ->
                                val beanId = backStackEntry.arguments?.getLong("beanId")
                                if (beanId != null) {
                                    // BeanViewModel tillhandahålls redan på Activity-nivå.
                                    BeanDetailScreen(
                                        beanId = beanId,
                                        onNavigateBack = { navController.popBackStack() },
                                        onBrewClick = { brewId ->
                                            navController.navigate(Screen.BrewDetail.createRoute(brewId))
                                        }
                                    )
                                } else {
                                    Log.e("MainActivity", "Bean ID saknas vid navigering till BeanDetail.")
                                    LaunchedEffect(Unit) { navController.popBackStack() }
                                }
                            }

                            // --- Kamera ---
                            composable(Screen.Camera.route) {
                                CameraScreen(
                                    onImageCaptured = { uri ->
                                        // Sparar den fångade URI:n i SavedStateHandle för föregående skärm (BrewDetail)
                                        navController.previousBackStackEntry
                                            ?.savedStateHandle
                                            ?.set("captured_image_uri", uri.toString())
                                        navController.popBackStack()
                                    },
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            // --- Helskärmsvy för bild ---
                            composable(
                                route = Screen.ImageFullscreen.route,
                                arguments = listOf(navArgument("uri") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val encodedUri = backStackEntry.arguments?.getString("uri")
                                val uri = encodedUri?.let { Uri.decode(it) } // Avkodar URI:n

                                if (uri != null) {
                                    FullscreenImageScreen(
                                        uri = uri,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                } else {
                                    Log.e("MainActivity", "URI-argument för FullscreenImageScreen saknas eller är ogiltigt.")
                                    LaunchedEffect(Unit) { navController.popBackStack() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}