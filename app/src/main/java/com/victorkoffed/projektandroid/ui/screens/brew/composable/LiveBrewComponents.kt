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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import kotlin.math.ceil
import kotlin.math.max

@SuppressLint("DefaultLocale")
@Composable
fun StatusDisplay(
    currentTimeMillis: Long,
    currentMeasurement: ScaleMeasurement,
    isRecording: Boolean,
    isPaused: Boolean,
    isRecordingWhileDisconnected: Boolean,
    showFlow: Boolean,
    countdown: Int?
) {
    val timeString = remember(currentTimeMillis) {
        val minutes = (currentTimeMillis / 1000 / 60).toInt()
        val seconds = (currentTimeMillis / 1000 % 60).toInt()
        String.format("%02d:%02d", minutes, seconds)
    }
    val weightString =
        remember(currentMeasurement.weightGrams) { "%.1f g".format(currentMeasurement.weightGrams) }
    val flowString =
        remember(currentMeasurement.flowRateGramsPerSecond) { "%.1f g/s".format(currentMeasurement.flowRateGramsPerSecond) }

    val containerColor = when {
        countdown != null -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
        isRecordingWhileDisconnected -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
        isPaused -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        isRecording -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 16.dp)
                .fillMaxWidth()
                .defaultMinSize(minHeight = 180.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (countdown != null) {
                Text(
                    text = "Starting in...",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = countdown.toString(),
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            } else {
                Text(text = timeString, fontSize = 48.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (showFlow) Arrangement.SpaceEvenly else Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Weight", style = MaterialTheme.typography.labelMedium)
                        Text(text = weightString, fontSize = 36.sp, fontWeight = FontWeight.Light)
                    }
                    if (showFlow) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Flow", style = MaterialTheme.typography.labelMedium)
                            Text(text = flowString, fontSize = 36.sp, fontWeight = FontWeight.Light)
                        }
                    }
                }

                if (isPaused || isRecordingWhileDisconnected) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isRecordingWhileDisconnected) {
                            Icon(
                                Icons.AutoMirrored.Filled.BluetoothSearching,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.size(4.dp))
                            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onErrorContainer) {
                                if (isPaused) {
                                    Text("Paused - Reconnecting...", fontSize = 14.sp)
                                } else {
                                    Text("Recording (Data Paused) - Reconnecting...", fontSize = 14.sp)
                                }
                            }
                        } else {
                            Text("Paused", fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

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

    // ⬇️ Ny offset för X-axelns titel ("Time")
    val timeTitleOffsetDp = 15.dp

    val yLabelPaddingPx = with(LocalDensity.current) { yLabelPadding.toPx() }
    val weightTitleOffsetPx = with(LocalDensity.current) { weightTitleOffsetDp.toPx() }
    val timeTitleOffsetPx = with(LocalDensity.current) { timeTitleOffsetDp.toPx() }

    // Öka bottom-padding så att den nedflyttade titeln inte klipps
    Canvas(
        modifier = modifier.padding(
            start = 32.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 32.dp + timeTitleOffsetDp
        )
    ) {
        val axisPadding = 0f
        val xLabelPadding = 24.dp.toPx() // utrymme för siffrorna (tick labels)
        val graphWidth = size.width - yLabelPaddingPx - axisPadding
        val graphHeight = size.height - xLabelPadding - axisPadding
        if (graphWidth <= 0 || graphHeight <= 0) return@Canvas

        val maxTime = max(60000f, samples.maxOfOrNull { it.timeMillis }?.toFloat() ?: 1f) * 1.05f
        val actualMaxMass = samples.maxOfOrNull { it.massGrams }?.toFloat() ?: 1f
        val maxMass = max(50f, ceil(actualMaxMass / 50f) * 50f) * 1.1f

        val xAxisY = size.height - xLabelPadding

        drawContext.canvas.nativeCanvas.apply {
            // Horisontella gridlinjer + Y-etiketter (massa)
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

            // Vertikala gridlinjer + X-etiketter (sekunder)
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
                // ⬅️ Siffrorna ligger kvar på samma baslinje (oförändrat)
                drawText("${timeSec}s", x, size.height, textPaint)
                currentTimeGrid += timeGridInterval
            }

            // ⬇️ Flytta bara titeln "Time" nedåt med timeTitleOffsetPx
            drawText(
                "Time",
                yLabelPaddingPx + graphWidth / 2,
                size.height + axisLabelPaint.textSize / 2 + timeTitleOffsetPx,
                axisLabelPaint
            )

            // Y-titel (roterad)
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

        // Axlar
        drawLine(axisColor, Offset(yLabelPaddingPx, axisPadding), Offset(yLabelPaddingPx, xAxisY))
        drawLine(axisColor, Offset(yLabelPaddingPx, xAxisY), Offset(size.width, xAxisY))

        // Kurvan
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
