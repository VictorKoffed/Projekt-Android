package com.victorkoffed.projektandroid.ui.screens.brew.composable // Nytt paket

// Importer som behövs för grafen
import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.withSave
import com.victorkoffed.projektandroid.data.db.BrewSample
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Composable för att rita ut grafen baserat på BrewSample-data i BrewDetailScreen.
 * Visar vikt och/eller flödeshastighet över tid.
 *
 * @param samples Lista med mätpunkter från bryggningen.
 * @param showWeightLine Om viktkurvan ska visas.
 * @param showFlowLine Om flödeskurvan ska visas.
 * @param modifier Modifier för att anpassa layouten.
 */
@SuppressLint("DefaultLocale")
@Composable
fun BrewSamplesGraph(
    samples: List<BrewSample>,
    showWeightLine: Boolean,
    showFlowLine: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // Färger från temat
    val massColor = MaterialTheme.colorScheme.tertiary
    val flowColor = MaterialTheme.colorScheme.secondary
    val gridLineColor = Color.LightGray
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb() // Använd tema-färg
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant // Färg för axellinjer

    // --- Paint-objekt för rendering ---
    val gridLinePaint = remember {
        Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f))
    }
    val numericLabelPaint = remember(textColor) {
        android.graphics.Paint().apply {
            color = textColor
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 10.sp.value * density.density
        }
    }
    // Egen paint för höger axel (flöde) för att kunna ha annan färg och justering
    val numericLabelPaintRight = remember(flowColor) {
        android.graphics.Paint().apply {
            color = flowColor.toArgb() // Använd flödesfärgen
            textAlign = android.graphics.Paint.Align.LEFT // Vänsterjustera för att passa vid höger axel
            textSize = 10.sp.value * density.density
        }
    }
    val axisTitlePaint = remember(textColor) {
        android.graphics.Paint().apply {
            color = textColor
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 14.sp.value * density.density
            isFakeBoldText = true
        }
    }
    // Egen paint för höger axeltitel (flöde)
    val axisTitlePaintFlow = remember(flowColor) {
        android.graphics.Paint().apply {
            color = flowColor.toArgb() // Använd flödesfärgen
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 14.sp.value * density.density
            isFakeBoldText = true
        }
    }
    // --- Slut Paint-objekt ---

    // Kontrollera om det finns flödesdata att visa (positivt värde)
    val hasFlowData = remember(samples) {
        samples.any { it.flowRateGramsPerSecond != null && it.flowRateGramsPerSecond > 0 }
    }

    // --- Canvas-ritning ---
    Canvas(modifier = modifier.padding(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 32.dp)) {
        // --- Beräkna grafens dimensioner och marginaler ---
        val xLabelPadding = 32.dp.toPx() // Utrymme under grafen för tidsaxeln och titel
        val yLabelPaddingLeft = 32.dp.toPx() // Utrymme till vänster för viktaxelns etiketter och titel
        // Utrymme till höger endast om flödesaxeln ska visas
        val yLabelPaddingRight = if (hasFlowData && showFlowLine) 32.dp.toPx() else 0.dp.toPx()

        // Grafens faktiska rityta
        val graphStartX = yLabelPaddingLeft
        val graphEndX = size.width - yLabelPaddingRight
        val graphWidth = graphEndX - graphStartX
        val graphTopY = 0f
        val graphBottomY = size.height - xLabelPadding
        val graphHeight = graphBottomY - graphTopY

        // Avbryt om ytan är för liten
        if (graphWidth <= 0 || graphHeight <= 0) return@Canvas
        // --- Slut Dimensionsberäkning ---

        // --- Beräkna maxvärden för skalning ---
        // Tid: Minst 60 sekunder, annars max uppmätt tid + 5% marginal
        val maxTime = max(60000f, samples.maxOfOrNull { it.timeMillis }?.toFloat() ?: 1f) * 1.05f
        // Vikt: Max uppmätt vikt, avrundat uppåt till närmaste 50g + 10% marginal (minst 50g)
        val actualMaxMass = samples.maxOfOrNull { it.massGrams }?.toFloat() ?: 1f
        val maxMass = max(50f, ceil(actualMaxMass / 50f) * 50f) * 1.1f
        // Flöde: Kapat vid 25 g/s visuellt, men skalar baserat på faktiskt max om det är lägre
        val visualMaxFlowCap = 25f // Visuellt tak för flödesaxeln
        val actualMaxFlow = samples.maxOfOrNull { it.flowRateGramsPerSecond?.toFloat() ?: 0f } ?: 1f
        // Använd taket för skalning om faktiskt max överskrider det, annars avrunda uppåt till närmaste 5
        val maxFlowForScaling = max(5f, // Minsta skala för flöde är 5 g/s
            if (actualMaxFlow > visualMaxFlowCap) visualMaxFlowCap else ceil(actualMaxFlow / 5f) * 5f
        ) * 1.1f // Lägg till marginal
        // Maxvärde att rita rutnätslinjer upp till (antingen taket eller faktiskt max)
        val maxFlowForGridLines = minOf(actualMaxFlow, visualMaxFlowCap) * 1.1f
        // --- Slut Maxvärdesberäkning ---

        // --- Rita Rutnät och Etiketter ---
        drawContext.canvas.nativeCanvas.apply {
            // Horisontella linjer (Viktaxel) och vänstra etiketter
            val massGridInterval = 50f
            var currentMassGrid = massGridInterval
            while (currentMassGrid < maxMass / 1.1f) { // Rita upp till faktiskt max (utan marginal)
                val y = graphBottomY - (currentMassGrid / maxMass) * graphHeight
                // Rita linjen
                drawLine(gridLineColor, Offset(graphStartX, y), Offset(graphEndX, y),
                    strokeWidth = gridLinePaint.width, pathEffect = gridLinePaint.pathEffect)
                // Rita etikett till vänster
                drawText("${currentMassGrid.toInt()}g", yLabelPaddingLeft / 2, y + numericLabelPaint.textSize / 3, numericLabelPaint)
                currentMassGrid += massGridInterval
            }

            // Högra etiketter (Flödesaxel), endast om relevant
            if (hasFlowData && showFlowLine) {
                // Beräkna intervall baserat på maxvärdet för rutnät (kapat)
                val flowGridInterval = max(1f, ceil(maxFlowForGridLines / 1.1f / 3f)) // Sikta på ca 3 linjer
                var currentFlowGrid = flowGridInterval
                while (currentFlowGrid < maxFlowForGridLines / 1.1f) { // Jämför mot max för rutnät
                    // Beräkna Y-position med det fulla skalningsvärdet
                    val y = graphBottomY - (currentFlowGrid / maxFlowForScaling) * graphHeight
                    // Rita etikett till höger
                    drawText(String.format("%.1f g/s", currentFlowGrid), size.width - yLabelPaddingRight * 0.75f, y + numericLabelPaintRight.textSize / 3, numericLabelPaintRight)
                    currentFlowGrid += flowGridInterval
                }
            }

            // Vertikala linjer (Tidsaxel) och nedre etiketter
            val timeGridInterval = 30000f // Var 30:e sekund
            var currentTimeGrid = timeGridInterval
            while (currentTimeGrid < maxTime / 1.05f) { // Rita upp till faktiskt max (utan marginal)
                val x = graphStartX + (currentTimeGrid / maxTime) * graphWidth
                // Rita linjen
                drawLine(gridLineColor, Offset(x, graphTopY), Offset(x, graphBottomY),
                    strokeWidth = gridLinePaint.width, pathEffect = gridLinePaint.pathEffect)
                // Rita etikett nedanför
                val timeSec = (currentTimeGrid / 1000).toInt()
                drawText("${timeSec}s", x, size.height, numericLabelPaint) // Rita längst ner på Canvas
                currentTimeGrid += timeGridInterval
            }
        }
        // --- Slut Rutnät och Etiketter ---

        // --- Rita Axellinjer ---
        drawLine(axisColor, Offset(graphStartX, graphTopY), Offset(graphStartX, graphBottomY)) // Vänster Y (Vikt)
        drawLine(axisColor, Offset(graphStartX, graphBottomY), Offset(graphEndX, graphBottomY)) // Botten X (Tid)
        if (hasFlowData && showFlowLine) { // Höger Y (Flöde), endast om relevant
            drawLine(axisColor, Offset(graphEndX, graphTopY), Offset(graphEndX, graphBottomY))
        }
        // --- Slut Axellinjer ---

        // --- Rita Datakurvor ---
        if (samples.size > 1) { // Behöver minst två punkter för att rita en linje
            val massPath = Path()
            val flowPath = Path()
            var flowPathStarted = false // För att hantera ev. null-värden i flödesdata

            samples.forEachIndexed { index, sample ->
                // Beräkna X-position (tid)
                val x = graphStartX + (sample.timeMillis.toFloat() / maxTime) * graphWidth
                // Beräkna Y-position för vikt
                val yMass = graphBottomY - (sample.massGrams.toFloat() / maxMass) * graphHeight

                // Säkerställ att punkterna är inom grafens gränser (clamp)
                val clampedX = x.coerceIn(graphStartX, graphEndX)
                val clampedYMass = yMass.coerceIn(graphTopY, graphBottomY)

                // Lägg till punkt i viktkurvan om den ska visas
                if (showWeightLine) {
                    if (index == 0) massPath.moveTo(clampedX, clampedYMass) else massPath.lineTo(clampedX, clampedYMass)
                }

                // Lägg till punkt i flödeskurvan om den ska visas och data finns
                if (showFlowLine && hasFlowData && sample.flowRateGramsPerSecond != null) {
                    val flowValue = sample.flowRateGramsPerSecond.toFloat()
                    // Beräkna Y-position för flöde med skalningsfaktorn
                    val yFlow = graphBottomY - (flowValue / maxFlowForScaling) * graphHeight
                    // Klampa Y-koordinaten (inte värdet) till grafens topp/botten
                    val clampedYFlow = yFlow.coerceIn(graphTopY, graphBottomY)

                    if (!flowPathStarted) {
                        flowPath.moveTo(clampedX, clampedYFlow)
                        flowPathStarted = true
                    } else {
                        flowPath.lineTo(clampedX, clampedYFlow)
                    }
                } else {
                    // Om flödesdata saknas eller kurvan inte ska visas, starta om path vid nästa giltiga punkt
                    flowPathStarted = false
                }
            }

            // Rita de färdiga kurvorna
            if (showWeightLine) {
                drawPath(path = massPath, color = massColor, style = Stroke(width = 2.dp.toPx()))
            }
            if (showFlowLine && hasFlowData) {
                drawPath(path = flowPath, color = flowColor, style = Stroke(width = 2.dp.toPx()))
            }
        }
        // --- Slut Datakurvor ---

        // --- Rita Axeltitlar ---
        drawContext.canvas.nativeCanvas.apply {
            // Tidsaxelns titel (centrerad under X-axeln)
            drawText("Time", graphStartX + graphWidth / 2, size.height - xLabelPadding / 2 + axisTitlePaint.textSize / 3, axisTitlePaint)

            // Rotera för Y-axeltitlar
            withSave {
                rotate(-90f) // Rotera -90 grader

                // Viktaxelns titel (centrerad längs vänster Y-axel)
                drawText(
                    "Weight (g)",
                    -(graphTopY + graphHeight / 2), // Centrera vertikalt (efter rotation)
                    yLabelPaddingLeft / 2 - axisTitlePaint.descent(), // Positionera horisontellt (blir vertikalt)
                    axisTitlePaint
                )

                // Flödesaxelns titel (centrerad längs höger Y-axel, om relevant)
                if (hasFlowData && showFlowLine) {
                    val flowAxisTitle = "Flow (g/s)" // Håll titeln generell
                    drawText(
                        flowAxisTitle,
                        -(graphTopY + graphHeight / 2), // Centrera vertikalt
                        size.width - yLabelPaddingRight / 2 - axisTitlePaintFlow.descent(), // Positionera vid höger axel
                        axisTitlePaintFlow // Använd den färgade painten
                    )
                }
            } // Återställ rotation
        }
        // --- Slut Axeltitlar ---
    }
    // --- Slut Canvas-ritning ---
}

// Hjälpfunktioner för att rita specifika delar (kan implementeras för ännu mer uppdelning)
// private fun DrawScope.drawGridLines(...) {}
// private fun DrawScope.drawAxes(...) {}
// private fun DrawScope.drawDataPath(...) {}
// private fun android.graphics.Canvas.drawAxisLabels(...) {}
