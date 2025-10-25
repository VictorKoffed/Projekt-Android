package com.victorkoffed.projektandroid.ui.screens.brew

import android.Manifest
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * En helskärms-Composable som hanterar kamerarättigheter och visar kameravyn.
 *
 * @param onImageCaptured Callback som anropas med URI:n till den tagna bilden.
 * @param onNavigateBack Callback för att stänga kameravyn.
 */
@Composable
fun CameraScreen(
    onImageCaptured: (Uri) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    // State för att spåra om vi har nödvändig kamerarättighet
    var hasCamPermission by remember { mutableStateOf(false) }

    // Launcher för att fråga om kamerarättighet
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                hasCamPermission = true
            } else {
                // Om användaren nekar, avbryt och gå tillbaka.
                onNavigateBack()
            }
        }
    )

    // Körs när composable visas första gången
    LaunchedEffect(key1 = true) {
        // Kontrollera om rättigheten redan finns
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            hasCamPermission = true
        } else {
            // Fråga om rättighet om den saknas
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Villkorlig rendering baserat på rättighetsstatus
    if (hasCamPermission) {
        CameraCaptureScreen(
            onImageCaptured = onImageCaptured,
            onNavigateBack = onNavigateBack
        )
    } else {
        // En enkel placeholder under tiden vi väntar på användarens svar
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Waiting for camera permission...", color = Color.White)
        }
    }
}

/**
 * Själva kameravyn som visas när rättigheter har beviljats.
 * Denna composable hanterar CameraX's livscykelbindning och UI-interaktioner.
 */
@Composable
private fun CameraCaptureScreen(
    onImageCaptured: (Uri) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // State för att växla mellan främre och bakre kamera
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }

    // Skapa och kom ihåg ImageCapture use case (för att ta bilden)
    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraSelector = remember(lensFacing) { CameraSelector.Builder().requireLensFacing(lensFacing).build() }

    // Kom ihåg PreviewView (Android View) som ska visa kameraströmmen
    val previewView = remember { PreviewView(context) }
    // State för att hålla referensen till CameraControl, nödvändigt för fokus och zoom
    var cameraControl: CameraControl? by remember { mutableStateOf(null) }

    // Binda CameraX use cases till Composables livscykel
    // Körs varje gång 'lensFacing' ändras
    LaunchedEffect(lensFacing) {
        val cameraProvider = context.getCameraProvider() // Hämta ProcessCameraProvider asynkront
        val preview = Preview.Builder().build().also {
            // Koppla Preview use case till PreviewView's SurfaceProvider
            it.surfaceProvider = previewView.surfaceProvider
        }

        try {
            // Avbinda alla tidigare use cases innan ombindning
            cameraProvider.unbindAll()
            // Bind use cases till lifecycleOwner
            val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
            // Uppdatera CameraControl-referensen
            cameraControl = camera.cameraControl
        } catch (e: Exception) {
            Log.e("CameraScreen", "Use case binding failed", e)
        }
    }

    // Box som täcker hela skärmen (kameravyn + kontroller)
    Box(modifier = Modifier.fillMaxSize()) {
        // Integrerar Android View (PreviewView) i Compose
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                // Implementerar "Tap-to-focus" med pointerInput
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        cameraControl?.let {
                            // Konvertera skärmkoordinater till en MeteringPoint
                            val meteringPoint = previewView.meteringPointFactory
                                .createPoint(offset.x, offset.y)
                            val action = FocusMeteringAction.Builder(meteringPoint).build()
                            // Utför fokus och mätning (exponering) vid tryck
                            it.startFocusAndMetering(action)
                        }
                    }
                }
        )

        // Kontrollpanelen längst ner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                // Halvgenomskinlig bakgrund för bättre kontrast
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Knapp för att byta mellan främre/bakre kamera
                IconButton(onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                }) {
                    Icon(Icons.Default.Cameraswitch, contentDescription = "Switch Camera", tint = Color.White)
                }

                // Avtryckare: Större och central
                IconButton(
                    onClick = {
                        takePhoto(
                            context = context,
                            imageCapture = imageCapture,
                            executor = ContextCompat.getMainExecutor(context),
                            onImageCaptured = onImageCaptured,
                            onError = { Log.e("CameraScreen", "Image capture error", it) }
                        )
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color.White, CircleShape)
                        .border(2.dp, Color.White, CircleShape)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = "Take Picture", tint = Color.Black)
                }

                // Knapp för att navigera tillbaka
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }
        }
    }
}

/**
 * Hjälpfunktion för att ta en bild med ImageCapture use case och spara den i en fil.
 */
private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    executor: Executor,
    onImageCaptured: (Uri) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    // Skapa en unik fil för bilden i appens cache-katalog.
    // CacheDir används för att undvika att behöva externa lagringsrättigheter (WRITE_EXTERNAL_STORAGE).
    val photoFile = File(
        context.cacheDir,
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis()) + ".jpg"
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    // Utför själva bildtagningen asynkront
    imageCapture.takePicture(outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            // Använd den returnerade URI:n om den finns, annars URI:n från den skapade filen.
            val savedUri = outputFileResults.savedUri ?: Uri.fromFile(photoFile)
            onImageCaptured(savedUri)
        }

        override fun onError(exception: ImageCaptureException) {
            onError(exception)
        }
    })
}

/**
 * En Coroutine-vänlig extension function för att hämta ProcessCameraProvider asynkront.
 * Använder 'suspendCoroutine' för att konvertera det Future-baserade CameraX-API:et
 * till ett coroutine-vänligt API.
 */
private suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
    cameraProviderFuture.addListener({
        continuation.resume(cameraProviderFuture.get())
    }, ContextCompat.getMainExecutor(this))
}