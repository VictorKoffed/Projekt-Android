package com.victorkoffed.projektandroid.ui.screens.brew

import android.Manifest
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    var hasCamPermission by remember { mutableStateOf(false) }

    // Launcher för att fråga om kamerarättighet
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                hasCamPermission = true
            } else {
                // Användaren nekade. Gå tillbaka.
                onNavigateBack()
            }
        }
    )

    // Körs när composable visas första gången
    LaunchedEffect(key1 = true) {
        // Kontrollera om vi redan har rättighet
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            hasCamPermission = true
        } else {
            // Fråga om rättighet
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Visa kameravyn om vi har rättighet, annars en laddnings- eller felvy
    if (hasCamPermission) {
        CameraCaptureScreen(
            onImageCaptured = onImageCaptured,
            onNavigateBack = onNavigateBack
        )
    } else {
        // Kan visa en spinner eller text här medan vi väntar på svar från permissionLauncher
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Waiting for camera permission...", color = Color.White)
        }
    }
}

/**
 * Själva kameravyn som visas när rättigheter har beviljats.
 */
@Composable
private fun CameraCaptureScreen(
    onImageCaptured: (Uri) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    // val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) } // <-- BORTTAGEN

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraSelector = remember(lensFacing, CameraSelector.Builder().requireLensFacing(lensFacing)::build)

    // --- NYTT: Kom ihåg PreviewView och CameraControl ---
    val previewView = remember { PreviewView(context) }
    var cameraControl: CameraControl? by remember { mutableStateOf(null) }
    // --- SLUT NYTT ---

    // --- NYTT: LaunchedEffect för att binda CameraX ---
    // Denna körs när composable startar och varje gång lensFacing ändras
    LaunchedEffect(lensFacing) {
        val cameraProvider = context.getCameraProvider() // Använd suspend-hjälpfunktionen
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        try {
            // Avbinda allt och bind sedan om
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle( // Spara referensen till kameran
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
            cameraControl = camera.cameraControl // Spara cameraControl för tap-to-focus
        } catch (e: Exception) {
            Log.e("CameraScreen", "Use case binding failed", e)
        }
    }
    // --- SLUT NYTT ---


    // Box som täcker hela skärmen
    Box(modifier = Modifier.fillMaxSize()) {
        // AndroidView som håller CameraX PreviewView
        AndroidView(
            factory = { previewView }, // <-- ANVÄND DEN IHÅGKOMNA PREVIEWVIEW
            modifier = Modifier
                .fillMaxSize()
                // --- NYTT: Lade till tap-to-focus ---
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        cameraControl?.let {
                            val meteringPoint = previewView.meteringPointFactory
                                .createPoint(offset.x, offset.y)
                            val action = FocusMeteringAction.Builder(meteringPoint).build()
                            it.startFocusAndMetering(action)
                        }
                    }
                }
            // --- SLUT NYTT ---
        )

        // --- BORTTAGEN: Knapp för att gå tillbaka (uppe i vänstra hörnet) ---
        // IconButton(
        //     onClick = onNavigateBack,
        //     modifier = Modifier
        //         .align(Alignment.TopStart)
        //         ...
        // ) { ... }
        // --- SLUT BORTTAGEN ---

        // Box för kontrollerna (längst ner)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Knapp för att byta kamera (Oförändrad)
                IconButton(onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                }) {
                    Icon(Icons.Default.Cameraswitch, contentDescription = "Switch Camera", tint = Color.White)
                }

                // Avtryckare (Oförändrad)
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

                // --- ÄNDRAD: Byt ut Spacer mot "Tillbaka"-knappen ---
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                // --- SLUT ÄNDRAD ---
            }
        }
    }
}

/**
 * Hjälpfunktion för att ta en bild och spara den.
 */
private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    executor: Executor,
    onImageCaptured: (Uri) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    // Skapa en unik fil för bilden i appens cache-katalog
    val photoFile = File(
        context.cacheDir, // Använder cacheDir, ingen lagringsrättighet behövs
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis()) + ".jpg"
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            val savedUri = outputFileResults.savedUri ?: Uri.fromFile(photoFile)
            onImageCaptured(savedUri)
        }

        override fun onError(exception: ImageCaptureException) {
            onError(exception)
        }
    })
}

/**
 * En Coroutine-vänlig version av cameraProviderFuture.get()
 */
private suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
    cameraProviderFuture.addListener({
        continuation.resume(cameraProviderFuture.get())
    }, ContextCompat.getMainExecutor(this))
}