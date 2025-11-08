package com.victorkoffed.projektandroid.ui.screens.brew.composable

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.withSave
import com.victorkoffed.projektandroid.data.db.BrewSample
import com.victorkoffed.projektandroid.domain.model.ScaleMeasurement
import com.victorkoffed.projektandroid.ui.viewmodel.brew.TargetWeightState
import kotlin.math.ceil
import kotlin.math.max

// --- NYA FÄRGER ---
private val TargetHitGreen = Color(0xFF388E3C) // En mörkare, mättad grön
private val TargetHitContentColor = Color.White // Vit text för den mörka bakgrunden

@SuppressLint("DefaultLocale")
@Composable
fun StatusDisplay(
    currentTimeMillis: Long,
    currentMeasurement: ScaleMeasurement,
    doseGrams: Double,
    isRecording: Boolean,
    isPaused: Boolean,
    isRecordingWhileDisconnected: Boolean,
    showRatio: Boolean,
    showFlow: Boolean,
    countdown: Int?,
    targetWeightMessage: String?,
    targetWeightState: TargetWeightState
) {
    val timeString = remember(currentTimeMillis) {
        val minutes = (currentTimeMillis / 1000 / 60).toInt()
        val seconds = (currentTimeMillis / 1000 % 60).toInt()
        String.format("%02d:%02d", minutes, seconds)
    }
    val weightString =
        remember(currentMeasurement.weightGrams) { "%.1f".format(currentMeasurement.weightGrams) }
    val flowString =
        remember(currentMeasurement.flowRateGramsPerSecond) { "%.1f g/s".format(currentMeasurement.flowRateGramsPerSecond) }

    val ratioString = remember(currentMeasurement.weightGrams, doseGrams) {
        if (doseGrams > 0.0) {
            val ratio = currentMeasurement.weightGrams / doseGrams
            "1:%.1f".format(ratio)
        } else {
            "1:---"
        }
    }

    // --- UPPDATERAD FÄRGLOGIK ---
    // Bestäm container- och content-färgerna (textfärg) TILLSAMMANS
    val (containerColor, contentColor) = when {
        countdown != null -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f) to MaterialTheme.colorScheme.onTertiaryContainer
        isRecordingWhileDisconnected -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f) to MaterialTheme.colorScheme.onErrorContainer
        isPaused -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) to MaterialTheme.colorScheme.onSurface

        // Ny logik för target state
        targetWeightState == TargetWeightState.HIT -> TargetHitGreen to TargetHitContentColor // Mörkgrön bakgrund, vit text
        targetWeightState == TargetWeightState.OVER_HARD -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f) to MaterialTheme.colorScheme.onErrorContainer

        isRecording -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) to MaterialTheme.colorScheme.onSurface
    }
    // --- SLUT UPPDATERAD FÄRGLOGIK ---

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor // Sätter standard-textfärgen för ALLT i kortet
        )
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 16.dp, horizontal = 8.dp)
                .fillMaxWidth()
                .defaultMinSize(minHeight = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (countdown != null) {
                // Denna text ärver nu 'onTertiaryContainer' från 'contentColor'
                Text(
                    text = "Starting in...",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = countdown.toString(),
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                // --- TOP ROW: TIME & WEIGHT ---
                // All text här ärver 'contentColor' (t.ex. vit på grön bakgrund)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Time
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Time", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = timeString,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Light
                        )
                    }

                    // Divider
                    HorizontalDivider(
                        color = contentColor.copy(alpha = 0.3f),
                        modifier = Modifier
                            .height(48.dp)
                            .width(1.dp)
                    )

                    // Weight
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Weight (g)", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = weightString,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Light
                        )
                    }
                }

                if (showRatio || showFlow) {
                    Spacer(Modifier.height(16.dp))
                }

                // --- BOTTOM ROW: RATIO & FLOW ---
                // All text här ärver 'contentColor'
                Row(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showRatio) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Ratio", style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = ratioString,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Light
                            )
                        }
                    }

                    if (showFlow) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Flow", style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = flowString,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Light
                            )
                        }
                    }
                }

                // --- STATUS (Target, Paused, Reconnecting) ---
                when {
                    // Target-meddelande (ärver färg, t.ex. vit på grön)
                    targetWeightMessage != null && !isPaused && !isRecordingWhileDisconnected && isRecording -> {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = targetWeightMessage,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                            // Färg ärvs från Card's 'contentColor'
                        )
                    }

                    // Paus/Frånkopplings-meddelande
                    isPaused || isRecordingWhileDisconnected -> {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isRecordingWhileDisconnected) {
                                // Ikon och text ärver 'onErrorContainer' från 'contentColor'
                                Icon(
                                    Icons.AutoMirrored.Filled.BluetoothSearching,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.size(4.dp))
                                Text(
                                    text = if (isPaused) "Paused - Reconnecting..." else "Recording (Data Paused) - Reconnecting...",
                                    fontSize = 14.sp
                                )
                            } else {
                                // Ärver standard 'contentColor' (t.ex. 'onSurface')
                                Text("Paused", fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ... (Resten av filen, LiveBrewGraph och BrewControls, är oförändrad) ...
@Composable
fun LiveBrewGraph(
    samples: List<BrewSample>,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val graphLineColor = MaterialTheme.colorScheme.tertiary
    val textColor = MaterialTheme.colorScheme.onBackground.toArgb()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridLineColor = Color.LightGray

    val textPaint = remember(textColor) {
        android.graphics.Paint().apply {
            color = textColor
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 10.sp.value * density.density
        }
    }
    val axisLabelPaint = remember(textColor) {
        android.graphics.Paint().apply {
            color = textColor
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 14.sp.value * density.density
            isFakeBoldText = true
        }
    }
    val gridLinePaint = remember {
        Stroke(
            width = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
        )
    }

    val yLabelPadding = 32.dp
    val weightTitleOffsetDp = 0.dp

    val timeTitleOffsetDp = 15.dp

    val yLabelPaddingPx = with(LocalDensity.current) { yLabelPadding.toPx() }
    val weightTitleOffsetPx = with(LocalDensity.current) { weightTitleOffsetDp.toPx() }
    val timeTitleOffsetPx = with(LocalDensity.current) { timeTitleOffsetDp.toPx() }

    Canvas(
        modifier = modifier.padding(
            start = 32.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 32.dp + timeTitleOffsetDp
        )
    ) {
        val axisPadding = 0f
        val xLabelPadding = 24.dp.toPx()
        val graphWidth = size.width - yLabelPaddingPx - axisPadding
        val graphHeight = size.height - xLabelPadding - axisPadding
        if (graphWidth <= 0 || graphHeight <= 0) return@Canvas

        val maxTime = max(60000f, samples.maxOfOrNull { it.timeMillis }?.toFloat() ?: 1f) * 1.05f
        val actualMaxMass = samples.maxOfOrNull { it.massGrams }?.toFloat() ?: 1f
        val maxMass = max(50f, ceil(actualMaxMass / 50f) * 50f) * 1.1f

        val xAxisY = size.height - xLabelPadding

        drawContext.canvas.nativeCanvas.apply {
            val massGridInterval = 50f
            var currentMassGrid = massGridInterval
            while (currentMassGrid < maxMass / 1.1f) {
                val y = xAxisY - (currentMassGrid / maxMass) * graphHeight
                drawLine(
                    color = gridLineColor,
                    start = Offset(yLabelPaddingPx, y),
                    end = Offset(size.width, y),
                    strokeWidth = gridLinePaint.width,
                    pathEffect = gridLinePaint.pathEffect
                )
                drawText("${currentMassGrid.toInt()}g", yLabelPaddingPx / 2, y + textPaint.textSize / 3, textPaint)
                currentMassGrid += massGridInterval
            }

            val timeGridInterval = 30000f
            var currentTimeGrid = timeGridInterval
            while (currentTimeGrid < maxTime / 1.05f) {
                val x = yLabelPaddingPx + (currentTimeGrid / maxTime) * graphWidth
                drawLine(
                    color = gridLineColor,
                    start = Offset(x, axisPadding),
                    end = Offset(x, xAxisY),
                    strokeWidth = gridLinePaint.width,
                    pathEffect = gridLinePaint.pathEffect
                )
                val timeSec = (currentTimeGrid / 1000).toInt()
                drawText("${timeSec}s", x, size.height, textPaint)
                currentTimeGrid += timeGridInterval
            }

            drawText(
                "Time",
                yLabelPaddingPx + graphWidth / 2,
                size.height + axisLabelPaint.textSize / 2 + timeTitleOffsetPx,
                axisLabelPaint
            )

            withSave {
                rotate(-90f)
                drawText(
                    "Weight (g)",
                    -(axisPadding + graphHeight / 2),
                    weightTitleOffsetPx - axisLabelPaint.descent(),
                    axisLabelPaint
                )
            }
        }

        drawLine(axisColor, Offset(yLabelPaddingPx, axisPadding), Offset(yLabelPaddingPx, xAxisY))
        drawLine(axisColor, Offset(yLabelPaddingPx, xAxisY), Offset(size.width, xAxisY))

        if (samples.size > 1) {
            val path = Path()
            samples.forEachIndexed { index, sample ->
                val x = yLabelPaddingPx + (sample.timeMillis.toFloat() / maxTime) * graphWidth
                val mass = sample.massGrams.toFloat()
                val y = xAxisY - (mass / maxMass) * graphHeight
                val clampedX = x.coerceIn(yLabelPaddingPx, size.width)
                val clampedY = y.coerceIn(axisPadding, xAxisY)
                if (index == 0) {
                    path.moveTo(clampedX, clampedY)
                } else {
                    path.lineTo(clampedX, clampedY)
                }
            }
            drawPath(path = path, color = graphLineColor, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

@Composable
fun BrewControls(
    isRecording: Boolean,
    isPaused: Boolean,
    isRecordingWhileDisconnected: Boolean,
    isConnected: Boolean,
    countdown: Int?,
    onStartClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onTareClick: () -> Unit,
    onResetClick: () -> Unit
) {
    val isBusy = countdown != null
    val enableReset = (isRecording || isPaused) && !isBusy

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onResetClick,
            enabled = enableReset
        ) {
            Icon(
                imageVector = Icons.Default.Replay,
                contentDescription = "Reset recording",
                tint = if (enableReset) LocalContentColor.current else Color.Gray
            )
        }
        Button(
            onClick = {
                when {
                    isPaused -> onResumeClick()
                    isRecording -> onPauseClick()
                    else -> onStartClick()
                }
            },
            modifier = Modifier.size(72.dp),
            contentPadding = PaddingValues(0.dp),
            enabled = !isBusy && (isConnected || isRecording)
        ) {
            Icon(
                imageVector = when {
                    isBusy -> Icons.Default.Timer
                    isPaused -> Icons.Default.PlayArrow
                    isRecordingWhileDisconnected -> Icons.Default.Pause
                    isRecording -> Icons.Default.Pause
                    else -> Icons.Default.PlayArrow
                },
                contentDescription = when {
                    isBusy -> "Starting..."
                    isPaused -> "Resume"
                    isRecordingWhileDisconnected -> "Pause (Disconnected)"
                    isRecording -> "Pause"
                    else -> "Start"
                },
                modifier = Modifier.size(40.dp)
            )
        }
        OutlinedButton(
            onClick = onTareClick,
            enabled = isConnected && !isBusy && (!isRecording || isPaused),
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text = "T",
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}