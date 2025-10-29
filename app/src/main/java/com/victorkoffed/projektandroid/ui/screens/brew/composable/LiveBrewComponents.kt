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

/**
 * Visar aktuell tid, vikt, flöde och status (inspelning/paus/nedräkning) från vågen.
 *
 * @param currentTimeMillis Tiden i millisekunder för den pågående bryggningen.
 * @param currentMeasurement Den senaste mätningen (vikt och flöde).
 * @param isRecording Anger om inspelning pågår.
 * @param isPaused Anger om inspelningen är pausad (manuellt eller p.g.a. frånkoppling).
 * @param isPausedDueToDisconnect Anger om pausen beror på frånkoppling.
 * @param showFlow Anger om flödeshastigheten ska visas.
 * @param countdown Nedräkningsvärdet (om start håller på).
 */
@SuppressLint("DefaultLocale")
@Composable
fun StatusDisplay(
    currentTimeMillis: Long,
    currentMeasurement: ScaleMeasurement,
    isRecording: Boolean,
    isPaused: Boolean,
    isPausedDueToDisconnect: Boolean,
    showFlow: Boolean,
    countdown: Int?
) {
    // Formatera tiden till MM:SS
    val timeString = remember(currentTimeMillis) {
        val minutes = (currentTimeMillis / 1000 / 60).toInt()
        val seconds = (currentTimeMillis / 1000 % 60).toInt()
        String.format("%02d:%02d", minutes, seconds)
    }
    // Formatera mätvärden
    val weightString = remember(currentMeasurement.weightGrams) { "%.1f g".format(currentMeasurement.weightGrams) }
    val flowString = remember(currentMeasurement.flowRateGramsPerSecond) { "%.1f g/s".format(currentMeasurement.flowRateGramsPerSecond) }

    // Bestäm bakgrundsfärg baserat på status
    val containerColor = when {
        countdown != null -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
        isPausedDueToDisconnect -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f) // Röd/orange vid disconnect-paus
        isPaused -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) // Grå vid manuell paus
        isRecording -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) // Standard grå/dämpad
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
                // Nedräkningsvy
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
                // Normal mätvy
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
                // Visa statusmeddelande vid paus
                if (isPaused) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isPausedDueToDisconnect) {
                            // Meddelande när paus beror på bortkoppling
                            Icon(Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            // Använder error color för varningstext
                            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onErrorContainer) {
                                Text("Paused - Reconnecting...", fontSize = 14.sp)
                            }
                        } else {
                            // Meddelande vid manuell paus
                            Text("Paused", fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Composable för att rita ut realtidsgrafen i Live Brew-läge.
 * Denna visar endast vikt över tid (följer LiveBrewScreen original).
 *
 * @param samples De insamlade BrewSample-punkterna.
 * @param modifier Modifier för layout.
 */
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

    // Paint-objekt för textetiketter (används i nativeCanvas)
    val textPaint = remember(textColor) {
        android.graphics.Paint().apply {
            color = textColor
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 10.sp.value * density.density
        }
    }
    // Paint-objekt för axeltitlar
    val axisLabelPaint = remember(textColor) {
        android.graphics.Paint().apply {
            color = textColor
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 14.sp.value * density.density
            isFakeBoldText = true
        }
    }
    // PathEffect för att rita prickade linjer
    val gridLinePaint = remember {
        Stroke(
            width = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
        )
    }

    Canvas(modifier = modifier.padding(start = 32.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)) {
        val axisPadding = 0f
        val xLabelPadding = 24.dp.toPx()
        val yLabelPadding = 24.dp.toPx()

        // Grafens rityta
        val graphWidth = size.width - yLabelPadding - axisPadding
        val graphHeight = size.height - xLabelPadding - axisPadding

        if (graphWidth <= 0 || graphHeight <= 0) return@Canvas

        // Skalning för tid och vikt
        val maxTime = max(60000f, samples.maxOfOrNull { it.timeMillis }?.toFloat() ?: 1f) * 1.05f
        val actualMaxMass = samples.maxOfOrNull { it.massGrams }?.toFloat() ?: 1f
        val maxMass = max(50f, ceil(actualMaxMass / 50f) * 50f) * 1.1f

        val xAxisY = size.height - xLabelPadding
        val yAxisX = yLabelPadding

        drawContext.canvas.nativeCanvas.apply {
            // Rita rutnät och etiketter för Vikt (Y-axel)
            val massGridInterval = 50f
            var currentMassGrid = massGridInterval
            while (currentMassGrid < maxMass / 1.1f) {
                val y = xAxisY - (currentMassGrid / maxMass) * graphHeight
                drawLine(
                    color = gridLineColor,
                    start = Offset(yAxisX, y),
                    end = Offset(size.width, y),
                    strokeWidth = gridLinePaint.width,
                    pathEffect = gridLinePaint.pathEffect
                )
                drawText("${currentMassGrid.toInt()}g", yLabelPadding / 2, y + textPaint.textSize / 3, textPaint)
                currentMassGrid += massGridInterval
            }

            // Rita rutnät och etiketter för Tid (X-axel)
            val timeGridInterval = 30000f // 30 sekunder
            var currentTimeGrid = timeGridInterval
            while (currentTimeGrid < maxTime / 1.05f) {
                val x = yAxisX + (currentTimeGrid / maxTime) * graphWidth
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

            // Rita axeltitlar
            drawText("Time", yAxisX + graphWidth / 2, size.height + axisLabelPaint.textSize / 2, axisLabelPaint)
            withSave {
                rotate(-90f)
                drawText(
                    "Weight (g)",
                    -(axisPadding + graphHeight / 2),
                    yLabelPadding / 2 - axisLabelPaint.descent(),
                    axisLabelPaint
                )
            }
        }

        // Rita axellinjer
        drawLine(axisColor, Offset(yAxisX, axisPadding), Offset(yAxisX, xAxisY))
        drawLine(axisColor, Offset(yAxisX, xAxisY), Offset(size.width, xAxisY))

        // Rita viktkurvan
        if (samples.size > 1) {
            val path = Path()
            samples.forEachIndexed { index, sample ->
                val x = yAxisX + (sample.timeMillis.toFloat() / maxTime) * graphWidth
                val mass = sample.massGrams.toFloat()
                val y = xAxisY - (mass / maxMass) * graphHeight
                val clampedX = x.coerceIn(yAxisX, size.width)
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

/**
 * Visar och hanterar kontrollknapparna (Start/Paus/Återuppta, Tara, Återställ) för live-bryggningen.
 *
 * @param isRecording Anger om inspelning pågår.
 * @param isPaused Anger om inspelningen är pausad.
 * @param isPausedDueToDisconnect Anger om pausen beror på frånkoppling.
 * @param isConnected Anger om vågen är ansluten.
 * @param countdown Nedräkningsvärdet (om start håller på).
 * @param onStartClick Callback för att starta inspelningen.
 * @param onPauseClick Callback för att manuellt pausa inspelningen.
 * @param onResumeClick Callback för att återuppta inspelningen.
 * @param onTareClick Callback för att tarera vågen.
 * @param onResetClick Callback för att återställa inspelningen.
 */
@Composable
fun BrewControls(
    isRecording: Boolean,
    isPaused: Boolean,
    isPausedDueToDisconnect: Boolean,
    isConnected: Boolean,
    countdown: Int?,
    onStartClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onTareClick: () -> Unit,
    onResetClick: () -> Unit
) {
    val isBusy = countdown != null
    // Återställningsknappen ska vara inaktiv om paus beror på disconnect (för att tvinga fram Resume)
    val enableReset = (isRecording || isPaused) && !isBusy && !isPausedDueToDisconnect

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Återställ (Replay) knapp
        IconButton(
            onClick = onResetClick,
            enabled = enableReset
        ) {
            Icon(
                imageVector = Icons.Default.Replay,
                contentDescription = "Reset recording",
                // Gråa ut om inaktiv
                tint = if (enableReset) LocalContentColor.current else Color.Gray
            )
        }

        // Huvudknapp (Start/Paus/Återuppta)
        Button(
            onClick = {
                when {
                    isPaused -> onResumeClick() // Om pausad, återuppta
                    isRecording -> onPauseClick() // Om inspelning pågår, pausa manuellt
                    else -> onStartClick() // Annars, starta ny inspelning
                }
            },
            modifier = Modifier.size(72.dp),
            contentPadding = PaddingValues(0.dp),
            enabled = !isBusy && (isConnected || isPausedDueToDisconnect) // Aktivera om inte upptagen OCH antingen ansluten ELLER pausad p.g.a. disconnect
        ) {
            Icon(
                imageVector = when {
                    isBusy -> Icons.Default.Timer // Nedräkning pågår
                    isPaused -> Icons.Default.PlayArrow // Visa Play (för att återuppta)
                    isRecording -> Icons.Default.Pause // Visa Pause
                    else -> Icons.Default.PlayArrow // Visa Play (för att starta)
                },
                contentDescription = when {
                    isBusy -> "Starting..."
                    isPausedDueToDisconnect -> "Resume when connected"
                    isPaused -> "Resume"
                    isRecording -> "Pause"
                    else -> "Start"
                },
                modifier = Modifier.size(40.dp)
            )
        }

        // Tara-knapp (T)
        OutlinedButton(
            onClick = onTareClick,
            // Endast aktiv om: Ansluten OCH Inte upptagen OCH (Inte inspelning ELLER Pausad)
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