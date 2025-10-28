package com.victorkoffed.projektandroid

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels // Behålls för coffeeImageVm initialt <--- RADERAD/UPPDATERAD
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel // <-- NY IMPORT
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.victorkoffed.projektandroid.data.themePref.ThemePreferenceManager
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.ui.navigation.Screen
import com.victorkoffed.projektandroid.ui.screens.bean.BeanDetailScreen // Se till att denna är uppdaterad utan beanId-parameter
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
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewDetailViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.coffee.CoffeeImageViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.grinder.GrinderViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.home.HomeViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.method.MethodViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.scale.ScaleViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

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
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        actionColor = MaterialTheme.colorScheme.onPrimary
    )
}

/**
 * Huvudaktiviteten i applikationen.
 * Hanterar Compose-navigeringen och instansierar alla ViewModels via Hilt.
 */
@AndroidEntryPoint // <-- NY ANNOTERING
class MainActivity : ComponentActivity() {

    // Injicera ThemePreferenceManager direkt här
    @Inject
    lateinit var themePreferenceManager: ThemePreferenceManager

    // CoffeeImageViewModel kan fortfarande skapas så här eftersom den inte har komplexa beroenden (ännu)
    // private val coffeeImageVm: CoffeeImageViewModel by viewModels() <--- BORTTAGEN


    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_ProjektAndroid)
        super.onCreate(savedInstanceState)

        setContent {

            // Använd den injicerade themePreferenceManager
            ProjektAndroidTheme(themePreferenceManager = themePreferenceManager) {

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

                // Hämta ViewModels med Hilt
                val homeVm: HomeViewModel = hiltViewModel()
                val brewVm: BrewViewModel = hiltViewModel()
                val scaleVm: ScaleViewModel = hiltViewModel()
                val coffeeImageVm: CoffeeImageViewModel = hiltViewModel() // <--- NY HILT VIEWMODEL HÄMTNING

                // Observerar state direkt från Hilt ViewModel
                val isDarkModeManual by homeVm.isDarkMode.collectAsState()
                val availableBeans by brewVm.availableBeans.collectAsState()
                val availableMethods by brewVm.availableMethods.collectAsState()
                val scaleConnectionState by scaleVm.connectionState.collectAsState(
                    initial = scaleVm.connectionState.replayCache.lastOrNull() ?: BleConnectionState.Disconnected
                )

                // State och scope för att visa SnackBar med meddelanden.
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                // Drawer State
                val drawerState = rememberDrawerState(DrawerValue.Closed)

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = bottomBarRoutes.contains(currentRoute),
                    drawerContent = {
                        ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                            Text(
                                "Settings",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(16.dp)
                            )
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp), DividerDefaults.Thickness, MaterialTheme.colorScheme.outline)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { homeVm.toggleDarkMode(!isDarkModeManual) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Dark Mode (Manual)")
                                Switch(
                                    checked = isDarkModeManual,
                                    onCheckedChange = { homeVm.toggleDarkMode(it) }
                                )
                            }
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp), DividerDefaults.Thickness, MaterialTheme.colorScheme.outline)
                            // Lägg till fler inställningar här vid behov
                        }
                    }
                ) {
                    Scaffold(
                        bottomBar = {
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
                                                    popUpTo(navController.graph.startDestinationId)
                                                    launchSingleTop = true
                                                }
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                                unselectedIconColor = MaterialTheme.colorScheme.onSurface,
                                                unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                                                indicatorColor = Color.Transparent
                                            )
                                        )
                                    }
                                }
                            }
                        },
                        snackbarHost = {
                            SnackbarHost(
                                snackbarHostState,
                                snackbar = { snackbarData ->
                                    ThemedSnackbar(snackbarData)
                                }
                            )
                        }
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Home.route,
                            modifier = Modifier.padding(innerPadding)
                        ) {

                            // --- Home ---
                            composable(Screen.Home.route) {
                                HomeScreen(
                                    homeVm = homeVm, // Använd instansen från ovan
                                    coffeeImageVm = coffeeImageVm,
                                    scaleVm = scaleVm, // Använd instansen från ovan
                                    snackbarHostState = snackbarHostState,
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
                                    onMenuClick = {
                                        scope.launch { drawerState.open() }
                                    }
                                )
                            }

                            // --- Huvudmenyns skärmar ---
                            composable(Screen.BeanList.route) {
                                BeanScreen(
                                    vm = hiltViewModel<BeanViewModel>(),
                                    onBeanClick = { beanId ->
                                        navController.navigate(Screen.BeanDetail.createRoute(beanId))
                                    },
                                    onMenuClick = {
                                        scope.launch { drawerState.open() }
                                    }
                                )
                            }
                            composable(Screen.GrinderList.route) {
                                GrinderScreen(
                                    vm = hiltViewModel<GrinderViewModel>(),
                                    onMenuClick = {
                                        scope.launch { drawerState.open() }
                                    }
                                )
                            }
                            composable(Screen.MethodList.route) {
                                MethodScreen(
                                    vm = hiltViewModel<MethodViewModel>(),
                                    onMenuClick = {
                                        scope.launch { drawerState.open() }
                                    }
                                )
                            }

                            // --- Våg ---
                            composable(Screen.ScaleConnect.route) {
                                ScaleConnectScreen(
                                    vm = scaleVm, // Använd instansen från ovan
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            // --- Flöde för ny bryggning (Setup) ---
                            composable(Screen.BrewSetup.route) {
                                BrewScreen(
                                    vm = brewVm, // Använd instansen från ovan
                                    completedBrewId = null,
                                    scaleConnectionState = scaleConnectionState,
                                    onStartBrewClick = {
                                        brewVm.clearBrewResults()
                                        navController.navigate(Screen.LiveBrew.route)
                                    },
                                    onSaveWithoutGraph = {
                                        lifecycleScope.launch {
                                            brewVm.getCurrentSetup()
                                            val newBrewId = brewVm.saveBrewWithoutSamples()
                                            if (newBrewId != null) {
                                                navController.navigate(Screen.BrewDetail.createRoute(newBrewId)) {
                                                    popUpTo(Screen.BrewSetup.route) { inclusive = true }
                                                }
                                            } else {
                                                Log.e("MainActivity", "Kunde inte spara bryggning utan graf.")
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
                                                navController.navigate(Screen.BrewDetail.createRoute(savedBrewId)) {
                                                    popUpTo(Screen.BrewSetup.route) { inclusive = true }
                                                }
                                            } else {
                                                Log.w("MainActivity", "Save cancelled or failed, returning to setup.")
                                                val errorMsg = scaleVm.error.value
                                                if(errorMsg != null) {
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar(errorMsg)
                                                        scaleVm.clearError()
                                                    }
                                                }
                                                navController.popBackStack()
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
                                // Hämta ViewModel med Hilt. SavedStateHandle injiceras automatiskt.
                                // Använder nyckel för att få unika instanser vid snabb navigering
                                val brewDetailViewModel: BrewDetailViewModel = hiltViewModel(key = "brewDetail_${backStackEntry.arguments?.getLong("brewId")}")

                                BrewDetailScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToCamera = { navController.navigate(Screen.Camera.route) },
                                    onNavigateToImageFullscreen = { uri ->
                                        val encodedUri = Uri.encode(uri)
                                        navController.navigate(Screen.ImageFullscreen.createRoute(encodedUri))
                                    },
                                    viewModel = brewDetailViewModel, // Skicka in Hilt-skapade ViewModel
                                    navBackStackEntry = backStackEntry
                                )
                                // Ingen manuell ID-kontroll behövs här, ViewModel hanterar det.
                            }

                            composable(
                                route = Screen.BeanDetail.route,
                                arguments = listOf(navArgument("beanId") { type = NavType.LongType })
                            ) { backStackEntry ->
                                // ID-kontroll behövs fortfarande för att säkerställa att argumentet finns
                                val beanIdArg = backStackEntry.arguments?.getLong("beanId")
                                if (beanIdArg != null && beanIdArg > 0) {
                                    // BeanDetailScreen hämtar nu sin egen ViewModel med hiltViewModel() internt
                                    BeanDetailScreen(
                                        // beanId tas inte emot som parameter längre
                                        onNavigateBack = { navController.popBackStack() },
                                        onBrewClick = { brewId ->
                                            navController.navigate(Screen.BrewDetail.createRoute(brewId))
                                        }
                                        // ViewModel skapas internt via hiltViewModel()
                                    )
                                } else {
                                    Log.e("MainActivity", "Bean ID saknas eller är ogiltigt vid navigering till BeanDetail.")
                                    LaunchedEffect(Unit) { navController.popBackStack() }
                                }
                            }

                            // --- Kamera ---
                            composable(Screen.Camera.route) {
                                CameraScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    navController = navController // Skicka in NavController för ViewModel
                                )
                            }

                            // --- Helskärmsvy för bild ---
                            composable(
                                route = Screen.ImageFullscreen.route,
                                arguments = listOf(navArgument("uri") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val encodedUri = backStackEntry.arguments?.getString("uri")
                                val uri = encodedUri?.let { Uri.decode(it) }

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
                        } // Slut NavHost
                    } // Slut Scaffold Content Scope
                } // Slut Scaffold
            } // Slut ModalNavigationDrawer Content Scope
        } // Slut ProjektAndroidTheme
    } // Slut setContent
} // Slut onCreate
// Slut MainActivity