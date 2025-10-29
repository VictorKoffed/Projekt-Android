package com.victorkoffed.projektandroid.ui.navigation

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
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
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.coffee.CoffeeImageViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.grinder.GrinderViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.home.HomeViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.method.MethodViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.scale.ScaleViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Composable som definierar navigationsgrafen för appen.
 * Hanterar alla skärmar och deras argument.
 *
 * @param navController Navigationskontrollern för att hantera byten mellan skärmar.
 * @param homeVm ViewModel för HomeScreen.
 * @param brewVm ViewModel för BrewScreen och LiveBrewScreen.
 * @param scaleVm ViewModel för ScaleConnectScreen och LiveBrewScreen.
 * @param coffeeImageVm ViewModel för att hämta slumpmässiga kaffebilder.
 * @param snackbarHostState State för att visa globala Snackbars.
 * @param coroutineScope Coroutine scope för att starta asynkrona operationer (t.ex. spara bryggning).
 * @param lifecycleScope Lifecycle scope för att starta coroutines knutna till Activity/Fragment lifecycle.
 * @param innerPadding Padding som ska appliceras från Scaffold.
 * @param startDrawerOpen Funktion för att öppna navigeringslådan.
 */
@Composable
fun AppNavigationGraph(
    navController: NavHostController,
    homeVm: HomeViewModel,
    brewVm: BrewViewModel,
    scaleVm: ScaleViewModel,
    coffeeImageVm: CoffeeImageViewModel,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
    lifecycleScope: LifecycleCoroutineScope,
    innerPadding: PaddingValues,
    startDrawerOpen: () -> Unit
) {
    // Collect states needed within the NavHost destinations
    val scaleConnectionState by scaleVm.connectionState.collectAsState(
        initial = scaleVm.connectionState.replayCache.lastOrNull() ?: BleConnectionState.Disconnected
    )
    val availableBeans by brewVm.availableBeans.collectAsState()
    val availableMethods by brewVm.availableMethods.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = Modifier.padding(innerPadding) // Apply padding from Scaffold
    ) {

        // --- Home ---
        composable(Screen.Home.route) {
            HomeScreen(
                homeVm = homeVm,
                coffeeImageVm = coffeeImageVm,
                scaleVm = scaleVm,
                snackbarHostState = snackbarHostState,
                navigateToScreen = { /* No longer used from here */ },
                onNavigateToBrewSetup = {
                    brewVm.clearBrewResults()
                    navController.navigate(Screen.BrewSetup.route)
                },
                onBrewClick = { brewId ->
                    navController.navigate(Screen.BrewDetail.createRoute(brewId))
                },
                availableBeans = availableBeans,
                availableMethods = availableMethods,
                onMenuClick = startDrawerOpen // Use the callback
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
                    brewVm.clearBrewResults() // Ensure results are clear before starting
                    navController.navigate(Screen.LiveBrew.route)
                },
                onSaveWithoutGraph = {
                    lifecycleScope.launch {
                        brewVm.getCurrentSetup() // Get current setup data
                        val newBrewId = brewVm.saveBrewWithoutSamples()
                        if (newBrewId != null) {
                            navController.navigate(Screen.BrewDetail.createRoute(newBrewId)) {
                                // Pop BrewSetup off the stack
                                popUpTo(Screen.BrewSetup.route) { inclusive = true }
                            }
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "Could not save brew. Check inputs.",
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    }
                },
                onNavigateToScale = { navController.navigate(Screen.ScaleConnect.route) },
                onClearResult = { /* No-op, handled differently */ },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // --- Flöde för ny bryggning (Live) ---
        composable(Screen.LiveBrew.route) {
            // Hämta alla nödvändiga states från ScaleViewModel
            val samples by scaleVm.recordedSamplesFlow.collectAsState()
            val time by scaleVm.recordingTimeMillis.collectAsState()
            val isRecording by scaleVm.isRecording.collectAsState()
            val isPaused by scaleVm.isPaused.collectAsState()
            val isPausedDueToDisconnect by scaleVm.isPausedDueToDisconnect.collectAsState()
            val currentMeasurement by scaleVm.measurement.collectAsState()
            val weightAtPause by scaleVm.weightAtPause.collectAsState()
            val countdown by scaleVm.countdown.collectAsState()

            LiveBrewScreen(
                samples = samples,
                currentMeasurement = currentMeasurement,
                currentTimeMillis = time,
                isRecording = isRecording,
                isPaused = isPaused,
                isPausedDueToDisconnect = isPausedDueToDisconnect,
                weightAtPause = weightAtPause,
                connectionState = scaleConnectionState,
                countdown = countdown,
                onStartClick = { scaleVm.startRecording() },
                onPauseClick = { scaleVm.pauseRecording() },
                onResumeClick = { scaleVm.resumeRecording() },
                onStopAndSaveClick = {
                    lifecycleScope.launch {
                        val currentSetup = brewVm.getCurrentSetup()
                        val saveResult = scaleVm.stopRecordingAndSave(currentSetup)

                        if (saveResult.brewId != null) {
                            val route = Screen.BrewDetail.createRoute(
                                brewId = saveResult.brewId,
                                beanIdToArchivePrompt = saveResult.beanIdReachedZero
                            )
                            navController.navigate(route) {
                                // Pop everything up to and including BrewSetup
                                popUpTo(Screen.BrewSetup.route) { inclusive = true }
                                launchSingleTop = true // Avoid multiple instances
                            }
                        } else {
                            Log.w("AppNavigationGraph", "Save cancelled or failed, returning to setup.")
                            // Global error handling via Snackbar in MainActivity takes care of user feedback
                            navController.popBackStack() // Go back to BrewSetup
                        }
                    }
                },
                onTareClick = { scaleVm.tareScale() },
                onNavigateBack = { navController.popBackStack() },
                onResetClick = { scaleVm.stopRecording() }, // FIX: Återställer inspelning/data
                navigateTo = { route -> navController.navigate(route) }
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
                navBackStackEntry = backStackEntry
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
                    }
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