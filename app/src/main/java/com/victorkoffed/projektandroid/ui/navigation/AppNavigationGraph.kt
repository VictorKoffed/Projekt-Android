package com.victorkoffed.projektandroid.ui.navigation

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
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
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewSetupViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.brew.LiveBrewViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.grinder.GrinderViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.method.MethodViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.scale.ScaleViewModel
import kotlinx.coroutines.launch

private const val BREW_DETAIL_FLOW_ROUTE = "brew_detail_flow/{brewId}?beanIdToArchivePrompt={beanIdToArchivePrompt}"
private const val CAMERA_URI_KEY = "captured_image_uri"

/**
 * Defines the root navigation graph for the application.
 * Manages route declarations, parameter passing, and ViewModel lifecycles (screen-level vs nested flow-level).
 */
@Composable
fun AppNavigationGraph(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    innerPadding: PaddingValues,
    startDrawerOpen: () -> Unit,
    scaleVm: ScaleViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = Modifier.padding(innerPadding)
    ) {

        composable(Screen.Home.route) {
            HomeScreen(
                snackbarHostState = snackbarHostState,
                onNavigateToBrewSetup = {
                    navController.navigate(Screen.BrewSetup.route)
                },
                onBrewClick = { brewId ->
                    navController.navigate(Screen.BrewDetail.createRoute(brewId))
                },
                onMenuClick = startDrawerOpen,
                scaleVm = scaleVm,
                brewVm = hiltViewModel<BrewSetupViewModel>()
            )
        }

        composable(Screen.BeanList.route) {
            BeanScreen(
                vm = hiltViewModel<BeanViewModel>(),
                onBeanClick = { beanId ->
                    navController.navigate(Screen.BeanDetail.createRoute(beanId))
                },
                onMenuClick = startDrawerOpen
            )
        }

        composable(Screen.GrinderList.route) {
            GrinderScreen(
                vm = hiltViewModel<GrinderViewModel>(),
                onMenuClick = startDrawerOpen
            )
        }

        composable(Screen.MethodList.route) {
            MethodScreen(
                vm = hiltViewModel<MethodViewModel>(),
                onMenuClick = startDrawerOpen
            )
        }

        composable(Screen.ScaleConnect.route) {
            ScaleConnectScreen(
                snackbarHostState = snackbarHostState,
                onNavigateBack = { navController.popBackStack() },
                vm = scaleVm
            )
        }

        composable(Screen.BrewSetup.route) {
            val scope = rememberCoroutineScope()
            val brewSetupVm: BrewSetupViewModel = hiltViewModel()

            BrewScreen(
                onStartBrewClick = { setupState ->
                    /*
                     * Jetpack Navigation strictly requires non-null string representations (even if empty)
                     * for optional arguments mapped to NavType.StringType to prevent routing crashes.
                     */
                    val route = Screen.LiveBrew.createRoute(
                        beanId = setupState.selectedBean!!.id,
                        doseGrams = setupState.doseGrams.value,
                        methodId = setupState.selectedMethod!!.id,
                        grinderId = setupState.selectedGrinder?.id,
                        grindSetting = setupState.grindSetting,
                        grindSpeedRpm = setupState.grindSpeedRpm.value,
                        brewTempCelsius = setupState.brewTempCelsius.value
                    )

                    navController.navigate(route)
                },
                onSaveWithoutGraph = { newBrewId ->
                    if (newBrewId != null) {
                        navController.navigate(Screen.BrewDetail.createRoute(newBrewId)) {
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
                onNavigateBack = { navController.popBackStack() },
                vm = brewSetupVm,
                scaleVm = scaleVm
            )
        }

        composable(
            route = Screen.LiveBrew.route,
            arguments = Screen.LiveBrew.arguments
        ) {
            val liveBrewVm: LiveBrewViewModel = hiltViewModel()

            LiveBrewScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { brewId, beanIdToArchivePrompt ->
                    val route = Screen.BrewDetail.createRoute(
                        brewId = brewId,
                        beanIdToArchivePrompt = beanIdToArchivePrompt
                    )
                    navController.navigate(route) {
                        popUpTo(Screen.BrewSetup.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                scaleVm = scaleVm,
                vm = liveBrewVm
            )
        }

        composable(
            route = Screen.BeanDetail.route,
            arguments = listOf(navArgument("beanId") { type = NavType.LongType })
        ) { backStackEntry ->
            val beanIdArg = backStackEntry.arguments?.getLong("beanId")
            if (beanIdArg != null && beanIdArg > 0) {
                BeanDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onBrewClick = { brewId ->
                        navController.navigate(Screen.BrewDetail.createRoute(brewId))
                    },
                    snackbarHostState = snackbarHostState
                )
            } else {
                Log.e("AppNavigationGraph", "Bean ID missing or invalid for BeanDetail.")
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }

        /*
         * Scopes the BrewDetailViewModel to a nested graph to share state between the
         * BrewDetailScreen and CameraScreen. This allows the camera to seamlessly return
         * the captured image URI back to the detail screen via the SavedStateHandle.
         */
        navigation(
            route = BREW_DETAIL_FLOW_ROUTE,
            startDestination = Screen.BrewDetail.route,
            arguments = Screen.BrewDetail.arguments
        ) {

            composable(
                route = Screen.BrewDetail.route,
                arguments = Screen.BrewDetail.arguments
            ) { backStackEntry ->

                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(BREW_DETAIL_FLOW_ROUTE)
                }
                val brewDetailViewModel: BrewDetailViewModel = hiltViewModel(parentEntry)

                val imageUri = backStackEntry.savedStateHandle.get<String>(CAMERA_URI_KEY)
                LaunchedEffect(imageUri) {
                    if (imageUri != null) {
                        Log.d("AppNavigationGraph", "Mottog URI från kameran: $imageUri")
                        brewDetailViewModel.updateBrewImageUri(imageUri)
                        backStackEntry.savedStateHandle.remove<String>(CAMERA_URI_KEY)
                    }
                }

                BrewDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCamera = {
                        navController.navigate(Screen.Camera.route)
                    },
                    onNavigateToImageFullscreen = { uri ->
                        val encodedUri = Uri.encode(uri)
                        navController.navigate(Screen.ImageFullscreen.createRoute(encodedUri))
                    },
                    viewModel = brewDetailViewModel,
                    snackbarHostState = snackbarHostState
                )
            }

            composable(Screen.Camera.route) {
                CameraScreen(
                    onNavigateBack = { navController.popBackStack() },
                    navController = navController
                )
            }
        }

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
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }
    }
}