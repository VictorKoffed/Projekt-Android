package com.victorkoffed.projektandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.victorkoffed.projektandroid.data.viewmodel.CoffeeImageViewModel // Antaget att denna finns kvar
import com.victorkoffed.projektandroid.ui.viewmodel.scale.ScaleViewModel
import com.victorkoffed.projektandroid.ui.screens.coffee.CoffeeImageScreen // Antaget att denna finns kvar
import com.victorkoffed.projektandroid.ui.screens.scale.ScaleConnectScreen
import com.victorkoffed.projektandroid.ui.theme.ProjektAndroidTheme
import com.victorkoffed.projektandroid.ui.viewmodel.grinder.GrinderViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.grinder.GrinderViewModelFactory
import com.victorkoffed.projektandroid.ui.screens.grinder.GrinderScreen

class MainActivity : ComponentActivity() {

    // Befintliga ViewModels
    private val coffeeVm: CoffeeImageViewModel by viewModels() // Om du behåller denna
    private val scaleVm: ScaleViewModel by viewModels()

    // Ny ViewModel för Grinder (kräver Factory)
    private lateinit var grinderVm: GrinderViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hämta repository från Application-klassen
        val app = application as CoffeeJournalApplication
        val coffeeRepository = app.coffeeRepository

        // Skapa GrinderViewModel med Factory
        val grinderViewModelFactory = GrinderViewModelFactory(coffeeRepository)
        grinderVm = ViewModelProvider(this, grinderViewModelFactory)[GrinderViewModel::class.java]

        setContent {
            ProjektAndroidTheme {
                // Behåll state för vilken skärm som visas
                var currentScreen by remember { mutableStateOf("grinder") } // Starta på grinder nu

                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.fillMaxSize()) {

                        // Huvudinnehåll (växlar mellan skärmarna)
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 80.dp), // Lämna plats för knappar
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Divider eller annan top-layout om du vill
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))

                            AnimatedContent(
                                targetState = currentScreen,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "screenSwitch"
                            ) { screen ->
                                when (screen) {
                                    "coffee" -> CoffeeImageScreen(coffeeVm) // Om du behåller denna
                                    "scale" -> ScaleConnectScreen(scaleVm)
                                    "grinder" -> GrinderScreen(grinderVm) // Ny skärm
                                }
                            }
                        }

                        // Navigationsknappar längst ned
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp) // Lite mer padding
                                .height(IntrinsicSize.Min), // För att knapparna ska få samma höjd
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Knapp för Coffee (om du behåller den)
                            NavigationButton(
                                text = "☕ Coffee",
                                isSelected = currentScreen == "coffee",
                                onClick = { currentScreen = "coffee" },
                                modifier = Modifier.weight(1f)
                            )

                            // Knapp för Scale
                            NavigationButton(
                                text = "⚖️ Scale", // Bytte ikon
                                isSelected = currentScreen == "scale",
                                onClick = { currentScreen = "scale" },
                                modifier = Modifier.weight(1f)
                            )

                            // NY Knapp för Grinder
                            NavigationButton(
                                text = "⚙️ Grinder",
                                isSelected = currentScreen == "grinder",
                                onClick = { currentScreen = "grinder" },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** En återanvändbar Composable för nav-knapparna */
@Composable
private fun NavigationButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(), // Fyller höjden av Row
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Text(text)
    }
}
