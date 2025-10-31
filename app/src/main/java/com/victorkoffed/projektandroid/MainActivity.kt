package com.victorkoffed.projektandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
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
import androidx.compose.material3.SnackbarDuration
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.victorkoffed.projektandroid.data.themePref.ThemePreferenceManager
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.ui.navigation.AppNavigationGraph
import com.victorkoffed.projektandroid.ui.navigation.Screen
import com.victorkoffed.projektandroid.ui.theme.ProjektAndroidTheme
import com.victorkoffed.projektandroid.ui.viewmodel.home.HomeViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.scale.ScaleViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NavItem(
    val label: String,
    val screenRoute: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themePreferenceManager: ThemePreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_ProjektAndroid) // Behåll splash screen temat
        super.onCreate(savedInstanceState)

        setContent {
            // Använd den injicerade themePreferenceManager
            ProjektAndroidTheme(themePreferenceManager = themePreferenceManager) {

                // NavController och globala states
                val navController = rememberNavController()
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                val drawerState = rememberDrawerState(DrawerValue.Closed)

                // Hämta ViewModels som behövs globalt eller skickas till AppNavigationGraph
                val homeVm: HomeViewModel = hiltViewModel()
                val scaleVm: ScaleViewModel = hiltViewModel()
                // brewVm och coffeeImageVm tas bort - de hämtas nu i sina egna skärmar

                // Observera states som behövs i MainActivity (för Drawer eller global Snackbar)
                val isDarkModeManual by homeVm.isDarkMode.collectAsState()
                val scaleConnectionState by scaleVm.connectionState.collectAsState(
                    initial = scaleVm.connectionState.replayCache.lastOrNull() ?: BleConnectionState.Disconnected
                )
                val scaleError by scaleVm.error.collectAsState()

                // --- Global Snackbar-logik ---
                LaunchedEffect(scaleConnectionState, scaleError) {
                    val errorMessage: String? = when {
                        scaleConnectionState is BleConnectionState.Error -> (scaleConnectionState as BleConnectionState.Error).message
                        scaleError != null -> scaleError
                        else -> null
                    }

                    if (errorMessage != null) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = errorMessage,
                                duration = SnackbarDuration.Long
                            )
                        }
                        if (scaleError != null && errorMessage == scaleError) {
                            scaleVm.clearError()
                        }
                    }
                }
                // --- Slut Global Snackbar-logik ---

                // --- Definition av Bottom Nav Items ---
                val navItems = remember { // remember för att undvika rekreation
                    listOf(
                        NavItem("Home", Screen.Home.route, Icons.Filled.Home, Icons.Outlined.Home),
                        NavItem("Bean", Screen.BeanList.route, Icons.Filled.Coffee, Icons.Outlined.Coffee),
                        NavItem("Method", Screen.MethodList.route, Icons.Filled.Science, Icons.Outlined.Science),
                        NavItem("Grinder", Screen.GrinderList.route, Icons.Filled.Settings, Icons.Outlined.Settings)
                    )
                }
                val bottomBarRoutes = remember { navItems.map { it.screenRoute }.toSet() }
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val showBottomBar = currentRoute in bottomBarRoutes
                // --- Slut Definition av Bottom Nav Items ---

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = showBottomBar, // Aktivera svepgesten endast när bottenmenyn visas
                    drawerContent = {
                        // Innehållet i Drawer (kan också brytas ut till en egen Composable)
                        AppDrawerContent(
                            isDarkMode = isDarkModeManual,
                            onToggleDarkMode = { homeVm.toggleDarkMode(it) },
                            onNavigateToScale = {
                                scope.launch { drawerState.close() }
                                navController.navigate(Screen.ScaleConnect.route)
                            },
                            // Lägg till fler callbacks för andra menyalternativ här
                        )
                    }
                ) {
                    Scaffold(
                        bottomBar = {
                            if (showBottomBar) {
                                AppBottomNavigationBar(
                                    navItems = navItems,
                                    currentRoute = currentRoute,
                                    onNavigate = { route ->
                                        navController.navigate(route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true // Spara state för skärmar i bottenmenyn
                                            }
                                            launchSingleTop = true
                                            restoreState = true // Återställ state när man navigerar tillbaka
                                        }
                                    }
                                )
                            }
                        },
                        snackbarHost = {
                            SnackbarHost(
                                snackbarHostState,
                                snackbar = { snackbarData -> ThemedSnackbar(snackbarData) } // <-- Använder den lokala funktionen
                            )
                        }
                    ) { innerPadding ->
                        AppNavigationGraph(
                            navController = navController,
                            snackbarHostState = snackbarHostState, // Skicka vidare den globala
                            innerPadding = innerPadding,
                            startDrawerOpen = { // Skicka en lambda för att öppna lådan
                                scope.launch { drawerState.open() }
                            }
                        )
                    } // Slut Scaffold Content Scope
                } // Slut ModalNavigationDrawer Content Scope
            } // Slut ProjektAndroidTheme
        } // Slut setContent
    } // Slut onCreate
} // Slut MainActivity

/**
 * Composable för innehållet i navigeringslådan (Drawer).
 */
@Composable
fun AppDrawerContent(
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    onNavigateToScale: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(modifier = modifier.width(300.dp)) {
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
                .clickable { onToggleDarkMode(!isDarkMode) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Dark Mode")
            Switch(
                checked = isDarkMode,
                onCheckedChange = onToggleDarkMode
            )
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), DividerDefaults.Thickness, MaterialTheme.colorScheme.outline)
        // Connect to Scale Item
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToScale) // Använd callback
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Bluetooth,
                contentDescription = null, // Dekorativ ikon
                modifier = Modifier.padding(end = 16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Connect to Scale")
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), DividerDefaults.Thickness, MaterialTheme.colorScheme.outline)
        // Lägg till fler inställningar här vid behov
    }
}

/**
 * Composable för bottennavigeringsfältet.
 */
@Composable
fun AppBottomNavigationBar(
    navItems: List<NavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(modifier = modifier) {
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
                onClick = { onNavigate(item.screenRoute) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary, // Gör texten primärfärgad också
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, // Lite dovare färg för ovalda
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = Color.Transparent // Ingen "piller"-indikator
                )
            )
        }
    }
}