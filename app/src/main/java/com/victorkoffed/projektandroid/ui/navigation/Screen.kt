package com.victorkoffed.projektandroid.ui.navigation

import androidx.navigation.NavType
import androidx.navigation.navArgument

/**
 * Defines the centralized routing structure and argument contracts for the application's navigation graph.
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object BeanList : Screen("bean_list")
    object GrinderList : Screen("grinder_list")
    object MethodList : Screen("method_list")
    object ScaleConnect : Screen("scale_connect")
    object BrewSetup : Screen("brew_setup")
    object Camera : Screen("camera")

    object ImageFullscreen : Screen("image_fullscreen/{uri}") {
        fun createRoute(uri: String) = "image_fullscreen/$uri"
    }

    object LiveBrew : Screen(
        route = "live_brew/{beanId}/{doseGrams}/{methodId}?" +
                "grinderId={grinderId}&" +
                "grindSetting={grindSetting}&" +
                "grindSpeedRpm={grindSpeedRpm}&" +
                "brewTempCelsius={brewTempCelsius}"
    ) {
        val arguments = listOf(
            navArgument("beanId") { type = NavType.LongType },
            navArgument("doseGrams") { type = NavType.StringType },
            navArgument("methodId") { type = NavType.LongType },
            navArgument("grinderId") { type = NavType.LongType; defaultValue = -1L },
            navArgument("grindSetting") { type = NavType.StringType; defaultValue = "null" },
            navArgument("grindSpeedRpm") { type = NavType.StringType; defaultValue = "null" },
            navArgument("brewTempCelsius") { type = NavType.StringType; defaultValue = "null" }
        )

        fun createRoute(
            beanId: Long,
            doseGrams: String,
            methodId: Long,
            grinderId: Long?,
            grindSetting: String?,
            grindSpeedRpm: String?,
            brewTempCelsius: String?
        ): String {
            val baseRoute = "live_brew/$beanId/$doseGrams/$methodId"
            /*
             * Jetpack Navigation arguments cannot be explicitly typed as nullable primitives;
             * therefore, "null" is utilized as a literal fallback placeholder for optional parameters
             * to safely satisfy string serialization constraints.
             */
            return "$baseRoute?" +
                    "grinderId=${grinderId ?: -1L}&" +
                    "grindSetting=${grindSetting ?: "null"}&" +
                    "grindSpeedRpm=${grindSpeedRpm ?: "null"}&" +
                    "brewTempCelsius=${brewTempCelsius ?: "null"}"
        }
    }

    object BrewDetail : Screen("brew_detail/{brewId}?beanIdToArchivePrompt={beanIdToArchivePrompt}") {
        val arguments = listOf(
            navArgument("brewId") { type = NavType.LongType },
            navArgument("beanIdToArchivePrompt") {
                type = NavType.LongType
                defaultValue = -1L
            }
        )

        fun createRoute(brewId: Long, beanIdToArchivePrompt: Long? = null): String {
            val baseRoute = "brew_detail/$brewId"
            return if (beanIdToArchivePrompt != null && beanIdToArchivePrompt > 0) {
                "$baseRoute?beanIdToArchivePrompt=$beanIdToArchivePrompt"
            } else {
                baseRoute
            }
        }
    }

    object BeanDetail : Screen("bean_detail/{beanId}") {
        fun createRoute(beanId: Long) = "bean_detail/$beanId"
    }
}