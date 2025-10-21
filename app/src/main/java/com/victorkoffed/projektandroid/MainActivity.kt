package com.victorkoffed.projektandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.DividerDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.victorkoffed.projektandroid.data.viewmodel.CoffeeImageViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.scale.ScaleViewModel
import com.victorkoffed.projektandroid.ui.screens.coffee.CoffeeImageScreen
import com.victorkoffed.projektandroid.ui.screens.scale.ScaleConnectScreen
import com.victorkoffed.projektandroid.ui.theme.ProjektAndroidTheme

/**
 * Huvudaktivitet som används som testyta för både Coffee- och Scale-funktionerna.
 */
class MainActivity : ComponentActivity() {

    private val coffeeVm: CoffeeImageViewModel by viewModels()
    private val scaleVm: ScaleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProjektAndroidTheme {
                var currentScreen by remember { mutableStateOf("coffee") }

                Surface(color = MaterialTheme.colorScheme.background) {
                    // Huvudlayout – Coffee/Scale vy + navigation längst ner
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Innehåll (växlar mellan vyerna)
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 72.dp), // Lämna plats för knappar
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            HorizontalDivider(
                                Modifier.padding(vertical = 8.dp),
                                DividerDefaults.Thickness,
                                DividerDefaults.color
                            )

                            @OptIn(ExperimentalAnimationApi::class)
                            AnimatedContent(
                                targetState = currentScreen,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "screenSwitch"
                            ) { screen ->
                                when (screen) {
                                    "coffee" -> CoffeeImageScreen(coffeeVm)
                                    "scale" -> ScaleConnectScreen(scaleVm)
                                }
                            }
                        }

                        // Navigationsknappar längst ned
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { currentScreen = "coffee" },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (currentScreen == "coffee")
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Text("☕ Coffee")
                            }
                            Button(
                                onClick = { currentScreen = "scale" },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (currentScreen == "scale")
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Text("⚙️ Scale")
                            }
                        }
                    }
                }
            }
        }
    }
}
