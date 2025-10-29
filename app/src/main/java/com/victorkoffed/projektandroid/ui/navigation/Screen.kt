package com.victorkoffed.projektandroid.ui.navigation

import androidx.navigation.NavType
import androidx.navigation.navArgument

/**
 * En förseglad klass som definierar alla navigeringsvägar (routes) i applikationen.
 * Detta säkerställer att alla vägar hanteras på ett centralt och säkert sätt.
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object BeanList : Screen("bean_list")
    object GrinderList : Screen("grinder_list")
    object MethodList : Screen("method_list")
    object ScaleConnect : Screen("scale_connect")
    object BrewSetup : Screen("brew_setup")
    object LiveBrew : Screen("live_brew")
    object Camera : Screen("camera")

    /**
     * Navigeringsväg för att visa en bild i fullskärm. Kräver en URI som argument.
     * URI:n måste vanligtvis vara URL-kodad.
     */
    object ImageFullscreen : Screen("image_fullscreen/{uri}") {
        /** Bygger den fullständiga rutten med den specifika URI:n. */
        fun createRoute(uri: String) = "image_fullscreen/$uri"
    }

    /**
     * Navigeringsväg för att visa detaljer för en bryggning. Kräver ID:t för bryggningen.
     * NYTT: Lägger till ett valfritt argument för att signalera arkiveringsprompt.
     */
    object BrewDetail : Screen("brew_detail/{brewId}?beanIdToArchivePrompt={beanIdToArchivePrompt}") {
        // Argumentdefinitioner
        val arguments = listOf(
            navArgument("brewId") { type = NavType.LongType },
            // Valfritt argument med standardvärde -1 (eller annat ogiltigt ID)
            navArgument("beanIdToArchivePrompt") {
                type = NavType.LongType
                defaultValue = -1L
            }
        )

        /** Bygger den fullständiga rutten med det specifika bryggnings-ID:t och eventuellt bön-ID för arkivering. */
        fun createRoute(brewId: Long, beanIdToArchivePrompt: Long? = null): String {
            val baseRoute = "brew_detail/$brewId"
            // Lägg till query parameter endast om beanIdToArchivePrompt har ett giltigt värde
            return if (beanIdToArchivePrompt != null && beanIdToArchivePrompt > 0) {
                "$baseRoute?beanIdToArchivePrompt=$beanIdToArchivePrompt"
            } else {
                baseRoute
            }
        }
    }


    /**
     * Navigeringsväg för att visa detaljer för en böna. Kräver ID:t för bönan.
     */
    object BeanDetail : Screen("bean_detail/{beanId}") {
        /** Bygger den fullständiga rutten med det specifika bön-ID:t. */
        fun createRoute(beanId: Long) = "bean_detail/$beanId"
    }
}