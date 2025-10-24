package com.victorkoffed.projektandroid.ui.navigation

// En förseglad klass för att definiera alla våra navigeringsvägar (routes)
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object BeanList : Screen("bean_list")
    object GrinderList : Screen("grinder_list")
    object MethodList : Screen("method_list")
    object ScaleConnect : Screen("scale_connect")
    object BrewSetup : Screen("brew_setup")
    object LiveBrew : Screen("live_brew")
    object Camera : Screen("camera")
    object ImageFullscreen : Screen("image_fullscreen") // TODO: Implementera denna skärm

    // Rutt med ett obligatoriskt 'brewId' argument
    object BrewDetail : Screen("brew_detail/{brewId}") {
        // Hjälpfunktion för att bygga den fullständiga rutten
        fun createRoute(brewId: Long) = "brew_detail/$brewId"
    }

    // Rutt med ett obligatoriskt 'beanId' argument
    object BeanDetail : Screen("bean_detail/{beanId}") {
        fun createRoute(beanId: Long) = "bean_detail/$beanId"
    }
}