package com.victorkoffed.projektandroid

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
// import androidx.activity.viewModels // Behövs ej om CoffeeVm tas bort
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
// import com.victorkoffed.projektandroid.ui.viewmodel.coffee.CoffeeImageViewModel // Ta bort om ej används
import com.victorkoffed.projektandroid.ui.viewmodel.scale.ScaleViewModel
// import com.victorkoffed.projektandroid.ui.screens.coffee.CoffeeImageScreen // Ta bort om ej används
import com.victorkoffed.projektandroid.ui.screens.scale.ScaleConnectScreen
import com.victorkoffed.projektandroid.ui.theme.ProjektAndroidTheme
import com.victorkoffed.projektandroid.ui.viewmodel.grinder.GrinderViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.grinder.GrinderViewModelFactory
import com.victorkoffed.projektandroid.ui.screens.grinder.GrinderScreen
import com.victorkoffed.projektandroid.ui.viewmodel.bean.BeanViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.bean.BeanViewModelFactory
import com.victorkoffed.projektandroid.ui.screens.bean.BeanScreen
import com.victorkoffed.projektandroid.ui.viewmodel.method.MethodViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.method.MethodViewModelFactory
import com.victorkoffed.projektandroid.ui.screens.method.MethodScreen
import com.victorkoffed.projektandroid.data.repository.BookooScaleRepositoryImpl
import com.victorkoffed.projektandroid.ui.viewmodel.scale.ScaleViewModelFactory
import com.victorkoffed.projektandroid.ui.screens.brew.LiveBrewScreen
import com.victorkoffed.projektandroid.ui.screens.brew.BrewScreen
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewViewModelFactory
import com.victorkoffed.projektandroid.ui.screens.home.HomeScreen
import com.victorkoffed.projektandroid.ui.viewmodel.home.HomeViewModel
import com.victorkoffed.projektandroid.ui.viewmodel.home.HomeViewModelFactory
import com.victorkoffed.projektandroid.ui.screens.brew.BrewDetailScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // ... (ViewModels som tidigare) ...
    private lateinit var scaleVm: ScaleViewModel
    private lateinit var grinderVm: GrinderViewModel
    private lateinit var beanVm: BeanViewModel
    private lateinit var methodVm: MethodViewModel
    private lateinit var brewVm: BrewViewModel
    private lateinit var homeVm: HomeViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ... (Skapa ViewModels som tidigare) ...
        val app = application as CoffeeJournalApplication
        val coffeeRepository = app.coffeeRepository
        val scaleRepository = BookooScaleRepositoryImpl(this)
        val scaleViewModelFactory = ScaleViewModelFactory(app, scaleRepository, coffeeRepository)
        scaleVm = ViewModelProvider(this, scaleViewModelFactory)[ScaleViewModel::class.java]
        val grinderViewModelFactory = GrinderViewModelFactory(coffeeRepository)
        grinderVm = ViewModelProvider(this, grinderViewModelFactory)[GrinderViewModel::class.java]
        val beanViewModelFactory = BeanViewModelFactory(coffeeRepository)
        beanVm = ViewModelProvider(this, beanViewModelFactory)[BeanViewModel::class.java]
        val methodViewModelFactory = MethodViewModelFactory(coffeeRepository)
        methodVm = ViewModelProvider(this, methodViewModelFactory)[MethodViewModel::class.java]
        val brewViewModelFactory = BrewViewModelFactory(coffeeRepository)
        brewVm = ViewModelProvider(this, brewViewModelFactory)[BrewViewModel::class.java]
        val homeViewModelFactory = HomeViewModelFactory(coffeeRepository)
        homeVm = ViewModelProvider(this, homeViewModelFactory)[HomeViewModel::class.java]

        setContent {
            ProjektAndroidTheme {
                var currentScreen by remember { mutableStateOf("home") }
                var lastBrewId by remember { mutableStateOf<Long?>(null) }
                var selectedBrewId by remember { mutableStateOf<Long?>(null) }

                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "screenSwitch",
                            modifier = Modifier.fillMaxSize()
                        ) { screen ->
                            when (screen) {
                                "home" -> HomeScreen(
                                    vm = homeVm,
                                    onNavigateToBrewSetup = {
                                        lastBrewId = null; brewVm.clearBrewResults()
                                        currentScreen = "brew_setup"
                                    },
                                    onBrewClick = { brewId ->
                                        selectedBrewId = brewId
                                        currentScreen = "brew_detail"
                                    }
                                )
                                "scale" -> ScaleConnectScreen(scaleVm)
                                "grinder" -> GrinderScreen(grinderVm)
                                "bean" -> BeanScreen(beanVm)
                                "method" -> MethodScreen(methodVm)
                                "brew_setup" -> BrewScreen(
                                    vm = brewVm,
                                    completedBrewId = lastBrewId,
                                    onStartBrewClick = { setupState ->
                                        lastBrewId = null
                                        brewVm.clearBrewResults()
                                        currentScreen = "live_brew"
                                    },
                                    onClearResult = {
                                        lastBrewId = null
                                    }
                                )
                                "live_brew" -> {
                                    val samples by scaleVm.recordedSamplesFlow.collectAsState()
                                    val time by scaleVm.recordingTimeMillis.collectAsState()
                                    val isRecording by scaleVm.isRecording.collectAsState()
                                    val isPaused by scaleVm.isPaused.collectAsState()
                                    val currentMeasurement by scaleVm.measurement.collectAsState()
                                    val weightAtPause by scaleVm.weightAtPause.collectAsState()

                                    LiveBrewScreen(
                                        samples = samples,
                                        currentMeasurement = currentMeasurement,
                                        currentTimeMillis = time,
                                        isRecording = isRecording,
                                        isPaused = isPaused,
                                        weightAtPause = weightAtPause,
                                        onStartClick = { scaleVm.startRecording() },
                                        onPauseClick = { scaleVm.pauseRecording() },
                                        onResumeClick = { scaleVm.resumeRecording() },
                                        onStopAndSaveClick = {
                                            lifecycleScope.launch {
                                                val currentSetup = brewVm.getCurrentSetup()
                                                Log.d("MainActivity", "Saving brew with setup: $currentSetup")

                                                val savedBrewId = scaleVm.stopRecordingAndSave(currentSetup)

                                                lastBrewId = savedBrewId
                                                currentScreen = "brew_setup"
                                            }
                                        },
                                        onTareClick = { scaleVm.tareScale() },
                                        onNavigateBack = { currentScreen = "brew_setup" }
                                    )
                                }
                                "brew_detail" -> {
                                    // Skapa en lokal kopia av ID:t FÖRST
                                    val idToShow = selectedBrewId
                                    if (idToShow != null) { // Kolla mot den lokala kopian
                                        BrewDetailScreen(
                                            brewId = idToShow, // Använd den lokala kopian
                                            onNavigateBack = {
                                                // Gör state-uppdateringarna så enkla som möjligt
                                                selectedBrewId = null
                                                currentScreen = "home"
                                            }
                                        )
                                    } else {
                                        // Fallback
                                        Text("Error: Brew ID missing")
                                        LaunchedEffect(Unit) { currentScreen = "home" }
                                    }
                                }
                            }
                        }
                        // Navigationsrad
                        if (currentScreen != "live_brew" && currentScreen != "brew_detail") {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .height(IntrinsicSize.Min)
                                    .navigationBarsPadding(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                NavigationButton(text = "🏠 Home", isSelected = currentScreen == "home", onClick = { currentScreen = "home" }, modifier = Modifier.weight(1f))
                                NavigationButton(text = "⚖️ Scale", isSelected = currentScreen == "scale", onClick = { currentScreen = "scale" }, modifier = Modifier.weight(1f))
                                NavigationButton(text = "➕ Brew", isSelected = currentScreen == "brew_setup", onClick = { currentScreen = "brew_setup" }, modifier = Modifier.weight(1f))
                                NavigationButton(text = "⚙️ Grinder", isSelected = currentScreen == "grinder", onClick = { currentScreen = "grinder" }, modifier = Modifier.weight(1f))
                                NavigationButton(text = "🫘 Bean", isSelected = currentScreen == "bean", onClick = { currentScreen = "bean" }, modifier = Modifier.weight(1f))
                                NavigationButton(text = "🧪 Method", isSelected = currentScreen == "method", onClick = { currentScreen = "method" }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// NavigationButton Composable (Oförändrad)
@Composable
private fun NavigationButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Text(text, fontSize = 11.sp, maxLines = 1)
    }
}

// BrewDetailPlaceholderScreen kan tas bort

