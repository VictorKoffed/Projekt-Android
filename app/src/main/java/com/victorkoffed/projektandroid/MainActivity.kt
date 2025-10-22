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
import androidx.compose.ui.unit.sp // Importera sp för textstorlek
import androidx.lifecycle.ViewModelProvider
import com.victorkoffed.projektandroid.ui.viewmodel.coffee.CoffeeImageViewModel // Antaget att denna finns kvar
import com.victorkoffed.projektandroid.ui.viewmodel.scale.ScaleViewModel
import com.victorkoffed.projektandroid.ui.screens.coffee.CoffeeImageScreen // Antaget att denna finns kvar
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

class MainActivity : ComponentActivity() {

    // Befintlig ViewModel (om du behåller den)
    private val coffeeVm: CoffeeImageViewModel by viewModels()

    // ViewModels som kräver Factory
    private lateinit var scaleVm: ScaleViewModel
    private lateinit var grinderVm: GrinderViewModel
    private lateinit var beanVm: BeanViewModel
    private lateinit var methodVm: MethodViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hämta repositories från Application-klassen
        val app = application as CoffeeJournalApplication
        val coffeeRepository = app.coffeeRepository
        val scaleRepository = BookooScaleRepositoryImpl(this)

        // Skapa ViewModels med Factories
        val scaleViewModelFactory = ScaleViewModelFactory(app, scaleRepository, coffeeRepository)
        scaleVm = ViewModelProvider(this, scaleViewModelFactory)[ScaleViewModel::class.java]

        val grinderViewModelFactory = GrinderViewModelFactory(coffeeRepository)
        grinderVm = ViewModelProvider(this, grinderViewModelFactory)[GrinderViewModel::class.java]

        val beanViewModelFactory = BeanViewModelFactory(coffeeRepository)
        beanVm = ViewModelProvider(this, beanViewModelFactory)[BeanViewModel::class.java]

        val methodViewModelFactory = MethodViewModelFactory(coffeeRepository)
        methodVm = ViewModelProvider(this, methodViewModelFactory)[MethodViewModel::class.java]

        setContent {
            ProjektAndroidTheme {
                var currentScreen by remember { mutableStateOf("grinder") }

                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 80.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            AnimatedContent(
                                targetState = currentScreen,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "screenSwitch"
                            ) { screen ->
                                when (screen) {
                                    "coffee" -> CoffeeImageScreen(coffeeVm)
                                    "scale" -> ScaleConnectScreen(scaleVm)
                                    "grinder" -> GrinderScreen(grinderVm)
                                    "bean" -> BeanScreen(beanVm)
                                    "method" -> MethodScreen(methodVm)
                                    "live_brew" -> {
                                        // Hämta ALLA states från ScaleViewModel
                                        val samples by scaleVm.recordedSamplesFlow.collectAsState()
                                        val time by scaleVm.recordingTimeMillis.collectAsState()
                                        val isRecording by scaleVm.isRecording.collectAsState()
                                        val isPaused by scaleVm.isPaused.collectAsState()
                                        val currentMeasurement by scaleVm.measurement.collectAsState()
                                        val weightAtPause by scaleVm.weightAtPause.collectAsState() // <-- HÄMTA PAUSAD VIKT

                                        LiveBrewScreen(
                                            samples = samples,
                                            currentMeasurement = currentMeasurement,
                                            currentTimeMillis = time,
                                            isRecording = isRecording,
                                            isPaused = isPaused,
                                            weightAtPause = weightAtPause, // <-- SKICKA IN PAUSAD VIKT
                                            onStartClick = { scaleVm.startRecording() },
                                            onPauseClick = { scaleVm.pauseRecording() },
                                            onResumeClick = { scaleVm.resumeRecording() },
                                            onStopAndSaveClick = {
                                                scaleVm.stopRecordingAndSave(beanIdToUse = 1L, doseGramsToUse = 20.0) // Placeholder
                                                currentScreen = "grinder" // Gå tillbaka
                                            },
                                            onTareClick = { scaleVm.tareScale() },
                                            onNavigateBack = { currentScreen = "grinder" } // Gå tillbaka
                                        )
                                    }
                                }
                            }
                        }
                        // Navigationsknappar (Oförändrade)
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NavigationButton(text = "☕ Coffee", isSelected = currentScreen == "coffee", onClick = { currentScreen = "coffee" }, modifier = Modifier.weight(1f))
                            NavigationButton(text = "⚖️ Scale", isSelected = currentScreen == "scale", onClick = { currentScreen = "scale" }, modifier = Modifier.weight(1f))
                            NavigationButton(text = "⚙️ Grinder", isSelected = currentScreen == "grinder", onClick = { currentScreen = "grinder" }, modifier = Modifier.weight(1f))
                            NavigationButton(text = "🫘 Bean", isSelected = currentScreen == "bean", onClick = { currentScreen = "bean" }, modifier = Modifier.weight(1f))
                            NavigationButton(text = "🧪 Method", isSelected = currentScreen == "method", onClick = { currentScreen = "method" }, modifier = Modifier.weight(1f))
                            NavigationButton(text = "📈 Brew", isSelected = currentScreen == "live_brew", onClick = { currentScreen = "live_brew" }, modifier = Modifier.weight(1f))
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
        Text(text, fontSize = 11.sp)
    }
}

