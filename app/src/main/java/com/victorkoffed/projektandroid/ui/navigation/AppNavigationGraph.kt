package com.victorkoffed.projektandroid.ui.navigation

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
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
import com.victorkoffed.projektandroid.ui.viewmodel.bean.BeanViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewDetailViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.grinder.GrinderViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.method.MethodViewModel
import kotlinx.coroutines.launch

/**
 * Composable som definierar navigationsgrafen för appen.
 * Hanterar alla skärmar och deras argument.
 *
 * @param navController Navigationskontrollern för att hantera byten mellan skärmar.
 * @param snackbarHostState State för att visa globala Snackbars.
 * @param innerPadding Padding som ska appliceras från Scaffold.
 * @param startDrawerOpen Funktion för att öppna navigeringslådan.
 */
@Composable
fun AppNavigationGraph(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    innerPadding: PaddingValues,
    startDrawerOpen: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = Modifier.padding(innerPadding) // Apply padding from Scaffold
    ) {

        // --- Home ---
        composable(Screen.Home.route) {
            HomeScreen(
                snackbarHostState = snackbarHostState,
                onNavigateToBrewSetup = {
                    navController.navigate(Screen.BrewSetup.route)
                },
                onBrewClick = { brewId ->
                    navController.navigate(Screen.BrewDetail.createRoute(brewId))
                },
                onMenuClick = startDrawerOpen // Use the callback
                // homeVm, coffeeImageVm, scaleVm, brewVm hämtas nu lokalt i HomeScreen
            )
        }

        // --- Huvudmenyns skärmar ---
        composable(Screen.BeanList.route) {
            BeanScreen(
                vm = hiltViewModel<BeanViewModel>(),
                onBeanClick = { beanId ->
                    navController.navigate(Screen.BeanDetail.createRoute(beanId))
                },
                onMenuClick = startDrawerOpen // Use the callback
            )
        }
        composable(Screen.GrinderList.route) {
            GrinderScreen(
                vm = hiltViewModel<GrinderViewModel>(),
                onMenuClick = startDrawerOpen // Use the callback
            )
        }
        composable(Screen.MethodList.route) {
            MethodScreen(
                vm = hiltViewModel<MethodViewModel>(),
                onMenuClick = startDrawerOpen // Use the callback
            )
        }

        // --- Våg ---
        composable(Screen.ScaleConnect.route) {
            ScaleConnectScreen(
                snackbarHostState = snackbarHostState, // Skicka vidare globala
                onNavigateBack = { navController.popBackStack() }
                // scaleVm hämtas lokalt
            )
        }

        // --- Flöde för ny bryggning (Setup) ---
        composable(Screen.BrewSetup.route) {
            val scope = rememberCoroutineScope() // Lokalt scope för snackbar
            BrewScreen(
                // vm, scaleConnectionState tas bort. Skärmen hämtar dem själv.
                onStartBrewClick = {
                    navController.navigate(Screen.LiveBrew.route)
                },
                onSaveWithoutGraph = { newBrewId -> // Uppdaterad callback
                    if (newBrewId != null) {
                        navController.navigate(Screen.BrewDetail.createRoute(newBrewId)) {
                            // Pop BrewSetup off the stack
                            popUpTo(Screen.BrewSetup.route) { inclusive = true }
                        }
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Could not save brew. Check inputs.",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                },
                onNavigateToScale = { navController.navigate(Screen.ScaleConnect.route) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // --- Flöde för ny bryggning (Live) ---
        composable(Screen.LiveBrew.route) {
            // Alla states tas bort. Skärmen hämtar dem själv.
            LiveBrewScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { brewId, beanIdToArchivePrompt ->
                    val route = Screen.BrewDetail.createRoute(
                        brewId = brewId,
                        beanIdToArchivePrompt = beanIdToArchivePrompt
                    )
                    navController.navigate(route) {
                        // Pop everything up to and including BrewSetup
                        popUpTo(Screen.BrewSetup.route) { inclusive = true }
                        launchSingleTop = true // Avoid multiple instances
                    }
                }
                // scaleVm och brewVm hämtas nu lokalt i LiveBrewScreen
            )
        }


        // --- Detalj-skärmar (med argument) ---
        composable(
            route = Screen.BrewDetail.route,
            arguments = Screen.BrewDetail.arguments
        ) { backStackEntry ->
            // Use HiltViewModel with a key to scope it to this specific brewId instance
            val brewDetailViewModel: BrewDetailViewModel = hiltViewModel(key = "brewDetail_${backStackEntry.arguments?.getLong("brewId")}")

            BrewDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCamera = { navController.navigate(Screen.Camera.route) },
                onNavigateToImageFullscreen = { uri ->
                    val encodedUri = Uri.encode(uri)
                    navController.navigate(Screen.ImageFullscreen.createRoute(encodedUri))
                },
                viewModel = brewDetailViewModel,
                snackbarHostState = snackbarHostState // Skicka vidare globala
            )
        }


        composable(
            route = Screen.BeanDetail.route,
            arguments = listOf(navArgument("beanId") { type = NavType.LongType })
        ) { backStackEntry ->
            val beanIdArg = backStackEntry.arguments?.getLong("beanId")
            if (beanIdArg != null && beanIdArg > 0) {
                // BeanDetailScreen gets its own ViewModel via hiltViewModel() internally
                BeanDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onBrewClick = { brewId ->
                        navController.navigate(Screen.BrewDetail.createRoute(brewId))
                    },
                    snackbarHostState = snackbarHostState // Skicka vidare globala
                )
            } else {
                Log.e("AppNavigationGraph", "Bean ID missing or invalid for BeanDetail.")
                // Use LaunchedEffect to pop back safely after composition
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }

        // --- Kamera ---
        composable(Screen.Camera.route) {
            // CameraScreen uses hiltViewModel() internally
            CameraScreen(
                onNavigateBack = { navController.popBackStack() },
                navController = navController
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
                Log.e("AppNavigationGraph", "URI argument missing or invalid for FullscreenImageScreen.")
                // Use LaunchedEffect to pop back safely after composition
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }
    } // End NavHost
}