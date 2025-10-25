package com.victorkoffed.projektandroid.ui.navigation

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
     */
    object BrewDetail : Screen("brew_detail/{brewId}") {
        /** Bygger den fullständiga rutten med det specifika bryggnings-ID:t. */
        fun createRoute(brewId: Long) = "brew_detail/$brewId"
    }

    /**
     * Navigeringsväg för att visa detaljer för en böna. Kräver ID:t för bönan.
     */
    object BeanDetail : Screen("bean_detail/{beanId}") {
        /** Bygger den fullständiga rutten med det specifika bön-ID:t. */
        fun createRoute(beanId: Long) = "bean_detail/$beanId"
    }
}