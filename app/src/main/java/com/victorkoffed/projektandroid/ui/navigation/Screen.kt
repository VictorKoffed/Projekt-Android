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
     * Navigeringsväg för live-bryggning. Kräver setup-data som argument.
     * Obligatoriska: beanId, doseGrams, methodId
     * Valfria: grinderId, grindSetting, grindSpeedRpm, brewTempCelsius, targetRatio
     */
    object LiveBrew : Screen(
        route = "live_brew/{beanId}/{doseGrams}/{methodId}?" +
                "grinderId={grinderId}&" +
                "grindSetting={grindSetting}&" +
                "grindSpeedRpm={grindSpeedRpm}&" +
                "brewTempCelsius={brewTempCelsius}&" +
                "targetRatio={targetRatio}" // Argumentet måste finnas i rutten
    ) {
        val arguments = listOf(
            // Obligatoriska
            navArgument("beanId") { type = NavType.LongType },
            navArgument("doseGrams") { type = NavType.StringType }, // Skickas som sträng
            navArgument("methodId") { type = NavType.LongType },
            // Valfria (med standardvärden)
            navArgument("grinderId") { type = NavType.LongType; defaultValue = -1L },
            navArgument("grindSetting") { type = NavType.StringType; defaultValue = "null" }, // Skickas som "null"
            navArgument("grindSpeedRpm") { type = NavType.StringType; defaultValue = "null" },
            navArgument("brewTempCelsius") { type = NavType.StringType; defaultValue = "null" },

            // --- FIX FÖR KRASCH ---
            navArgument("targetRatio") {
                type = NavType.StringType
                nullable = true       // 1. Tala om att argumentet FÅR vara null
                defaultValue = null     // 2. Standardsvärdet ÄR null
            }
            // --- SLUT PÅ FIX ---
        )

        /** Bygger den fullständiga rutten med all setup-data. */
        fun createRoute(
            beanId: Long,
            doseGrams: String,
            methodId: Long,
            grinderId: Long?,
            grindSetting: String?,
            grindSpeedRpm: String?,
            brewTempCelsius: String?,
            targetRatio: String?
        ): String {
            val baseRoute = "live_brew/$beanId/$doseGrams/$methodId"
            // Lägg till valfria parametrar.
            // Vi använder "null" som sträng-placeholder för de gamla null-värdena.
            return "$baseRoute?" +
                    "grinderId=${grinderId ?: -1L}&" +
                    "grindSetting=${grindSetting ?: "null"}&" +
                    "grindSpeedRpm=${grindSpeedRpm ?: "null"}&" +
                    "brewTempCelsius=${brewTempCelsius ?: "null"}&" +
                    // --- FIX FÖR KRASCH ---
                    // 3. Skicka en TOM STRÄNG ("") istället för strängen "null".
                    // En tom sträng är en giltig StringType, till skillnad från ett null-objekt.
                    "targetRatio=${targetRatio ?: ""}"
            // --- SLUT PÅ FIX ---
        }
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