package com.victorkoffed.projektandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.victorkoffed.projektandroid.data.viewmodel.CoffeeImageViewModel
import com.victorkoffed.projektandroid.ui.screens.CoffeeImageScreen
import com.victorkoffed.projektandroid.ui.theme.ProjektAndroidTheme

/**
 * Huvudaktivitet som laddar upp vår Compose-view (CoffeeImageScreen)
 * och kopplar den till CoffeeImageViewModel för datahantering.
 */
class MainActivity : ComponentActivity() {

    // Skapar ViewModel-instansen som delas med Compose
    private val vm: CoffeeImageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProjektAndroidTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    CoffeeImageScreen(vm)
                }
            }
        }
    }
}
