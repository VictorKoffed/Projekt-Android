/*
 * Referensnotering (AI-assistans): Implementeringen av kundanpassad graf-ritning
 * med Compose Canvas (hantering av koordinatsystem, axlar, skalning och linjer)
 * har utvecklats med AI-assistans. Se README.md för AI-verktyg.
 */

package com.victorkoffed.projektandroid.ui.screens.brew.composable

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

private data class GraphDrawingContext(
    val massColor: Color,
    val flowColor: Color,
    val axisColor: Color,
    val gridLineColor: Color,
    val gridLinePaint: Stroke,
    val numericLabelPaint: android.graphics.Paint,
    val numericLabelPaintRight: android.graphics.Paint,
    val axisTitlePaint: android.graphics.Paint,
    val axisTitlePaintFlow: android.graphics.Paint,
    val graphStartX: Float,
    val graphEndX: Float,
    @Suppress("SameParameterValue") val graphTopY: Float,
    val graphBottomY: Float,
    val graphWidth: Float,
    val graphHeight: Float,
    val maxTime: Float,
    val maxMass: Float,
    val maxFlowForScaling: Float,
    val maxFlowForGridLines: Float,
    val yLabelPaddingLeft: Float,
    val yLabelPaddingRight: Float,
    val xLabelPadding: Float,
    val weightTitleOffsetPx: Float,
    val flowTitleOffsetPx: Float,
)
@SuppressLint("DefaultLocale")
@Composable
fun BrewSamplesGraph(
    samples: List<BrewSample>,
    showWeightLine: Boolean,
    showFlowLine: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val massColor = MaterialTheme.colorScheme.tertiary
    val flowColor = MaterialTheme.colorScheme.secondary
    val gridLineColor = Color.LightGray
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val graphLineStrokeWidth = with(LocalDensity.current) { 2.dp.toPx() }
    val weightTitleOffsetDp = 0.dp
    val flowTitleOffsetDp = 30.dp
    val weightTitleOffsetPx = with(LocalDensity.current) { weightTitleOffsetDp.toPx() }
    val flowTitleOffsetPx = with(LocalDensity.current) { flowTitleOffsetDp.toPx() }
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
    val numericLabelPaintRight = remember(flowColor) {
        android.graphics.Paint().apply {
            color = flowColor.toArgb()
            textAlign = android.graphics.Paint.Align.LEFT
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
    val axisTitlePaintFlow = remember(flowColor) {
        android.graphics.Paint().apply {
            color = flowColor.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 14.sp.value * density.density
            isFakeBoldText = true
        }
    }
    val hasFlowData = remember(samples) {
        samples.any { it.flowRateGramsPerSecond != null && it.flowRateGramsPerSecond > 0 }
    }
    Canvas(modifier = modifier.padding(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 32.dp)) {
        val xLabelPadding = 32.dp.toPx()
        val yLabelPaddingLeft = 32.dp.toPx()
        val yLabelPaddingRight = if (hasFlowData && showFlowLine) 32.dp.toPx() else 0.dp.toPx()
        val graphEndX = size.width - yLabelPaddingRight
        val graphTopY = 0f
        val graphBottomY = size.height - xLabelPadding
        val graphWidth = graphEndX - yLabelPaddingLeft
        val graphHeight = graphBottomY - graphTopY
        if (graphWidth <= 0 || graphHeight <= 0) return@Canvas
        val maxTime = max(60000f, samples.maxOfOrNull { it.timeMillis }?.toFloat() ?: 1f) * 1.05f
        val actualMaxMass = samples.maxOfOrNull { it.massGrams }?.toFloat() ?: 1f
        val maxMass = max(50f, ceil(actualMaxMass / 50f) * 50f) * 1.1f
        val visualMaxFlowCap = 25f
        val actualMaxFlow = samples.maxOfOrNull { it.flowRateGramsPerSecond?.toFloat() ?: 0f } ?: 1f
        val maxFlowForScaling = max(5f,
            if (actualMaxFlow > visualMaxFlowCap) visualMaxFlowCap else ceil(actualMaxFlow / 5f) * 5f
        ) * 1.1f
        val maxFlowForGridLines = minOf(actualMaxFlow, visualMaxFlowCap) * 1.1f
        val drawingContext = GraphDrawingContext(
            massColor = massColor, flowColor = flowColor, axisColor = axisColor, gridLineColor = gridLineColor,
            gridLinePaint = gridLinePaint, numericLabelPaint = numericLabelPaint, numericLabelPaintRight = numericLabelPaintRight,
            axisTitlePaint = axisTitlePaint, axisTitlePaintFlow = axisTitlePaintFlow,
            graphStartX = yLabelPaddingLeft, graphEndX = graphEndX, graphTopY = graphTopY, graphBottomY = graphBottomY,
            graphWidth = graphWidth, graphHeight = graphHeight, maxTime = maxTime, maxMass = maxMass,
            maxFlowForScaling = maxFlowForScaling, maxFlowForGridLines = maxFlowForGridLines,
            yLabelPaddingLeft = yLabelPaddingLeft, yLabelPaddingRight = yLabelPaddingRight, xLabelPadding = xLabelPadding,
            weightTitleOffsetPx = weightTitleOffsetPx,
            flowTitleOffsetPx = flowTitleOffsetPx
        )
        drawGridAndLabels(hasFlowData, showFlowLine, drawingContext)
        drawAxes(hasFlowData, showFlowLine, drawingContext)
        drawDataPaths(samples, showWeightLine, showFlowLine, graphLineStrokeWidth, drawingContext)
        drawAxisTitles(hasFlowData, showFlowLine, drawingContext)
    }
}
@SuppressLint("DefaultLocale")
private fun DrawScope.drawGridAndLabels(
    hasFlowData: Boolean,
    showFlowLine: Boolean,
    ctx: GraphDrawingContext
) = with(ctx) {
    drawContext.canvas.nativeCanvas.apply {
        val massGridInterval = 50f
        var currentMassGrid = massGridInterval
        while (currentMassGrid < maxMass / 1.1f) {
            val y = graphBottomY - (currentMassGrid / maxMass) * graphHeight
            drawLine(gridLineColor, Offset(graphStartX, y), Offset(graphEndX, y),
                strokeWidth = gridLinePaint.width, pathEffect = gridLinePaint.pathEffect)
            drawText("${currentMassGrid.toInt()}g", yLabelPaddingLeft / 2, y + numericLabelPaint.textSize / 3, numericLabelPaint)
            currentMassGrid += massGridInterval
        }
        if (hasFlowData && showFlowLine) {
            val flowGridInterval = max(1f, ceil(maxFlowForGridLines / 1.1f / 3f))
            var currentFlowGrid = flowGridInterval
            while (currentFlowGrid < maxFlowForGridLines / 1.1f) {
                val y = graphBottomY - (currentFlowGrid / maxFlowForScaling) * graphHeight
                drawText(String.format("%.1f g/s", currentFlowGrid), size.width - yLabelPaddingRight * 0.75f, y + numericLabelPaintRight.textSize / 3, numericLabelPaintRight)
                currentFlowGrid += flowGridInterval
            }
        }
        val timeGridInterval = 30000f
        var currentTimeGrid = timeGridInterval
        while (currentTimeGrid < maxTime / 1.05f) {
            val x = graphStartX + (currentTimeGrid / maxTime) * graphWidth
            drawLine(gridLineColor, Offset(x, graphTopY), Offset(x, graphBottomY),
                strokeWidth = gridLinePaint.width, pathEffect = gridLinePaint.pathEffect)
            val timeSec = (currentTimeGrid / 1000).toInt()
            drawText("${timeSec}s", x, size.height, numericLabelPaint)
            currentTimeGrid += timeGridInterval
        }
    }
}
private fun DrawScope.drawAxes(
    hasFlowData: Boolean,
    showFlowLine: Boolean,
    ctx: GraphDrawingContext
) = with(ctx) {
    drawLine(axisColor, Offset(graphStartX, graphTopY), Offset(graphStartX, graphBottomY))
    drawLine(axisColor, Offset(graphStartX, graphBottomY), Offset(graphEndX, graphBottomY))
    if (hasFlowData && showFlowLine) {
        drawLine(axisColor, Offset(graphEndX, graphTopY), Offset(graphEndX, graphBottomY))
    }
}
private fun DrawScope.drawDataPaths(
    samples: List<BrewSample>,
    showWeightLine: Boolean,
    showFlowLine: Boolean,
    strokeWidth: Float,
    ctx: GraphDrawingContext
) = with(ctx) {
    if (samples.size > 1) {
        val massPath = Path()
        val flowPath = Path()
        var flowPathStarted = false
        samples.forEachIndexed { index, sample ->
            val x = graphStartX + (sample.timeMillis.toFloat() / maxTime) * graphWidth
            val clampedX = x.coerceIn(graphStartX, graphEndX)
            if (showWeightLine) {
                val yMass = graphBottomY - (sample.massGrams.toFloat() / maxMass) * graphHeight
                val clampedYMass = yMass.coerceIn(graphTopY, graphBottomY)
                if (index == 0) massPath.moveTo(clampedX, clampedYMass) else massPath.lineTo(clampedX, clampedYMass)
            }
            if (showFlowLine && sample.flowRateGramsPerSecond != null) {
                val flowValue = sample.flowRateGramsPerSecond.toFloat()
                val yFlow = graphBottomY - (flowValue / maxFlowForScaling) * graphHeight
                val clampedYFlow = yFlow.coerceIn(graphTopY, graphBottomY)
                if (!flowPathStarted) {
                    flowPath.moveTo(clampedX, clampedYFlow)
                    flowPathStarted = true
                } else {
                    flowPath.lineTo(clampedX, clampedYFlow)
                }
            } else {
                flowPathStarted = false
            }
        }
        if (showWeightLine) {
            drawPath(path = massPath, color = massColor, style = Stroke(width = strokeWidth))
        }
        if (showFlowLine && samples.any { it.flowRateGramsPerSecond != null }) {
            drawPath(path = flowPath, color = flowColor, style = Stroke(width = strokeWidth))
        }
    }
}
private fun DrawScope.drawAxisTitles(
    hasFlowData: Boolean,
    showFlowLine: Boolean,
    ctx: GraphDrawingContext
) = with(ctx) {
    drawContext.canvas.nativeCanvas.apply {
        drawText("Time", graphStartX + graphWidth / 2, size.height - xLabelPadding / 2 + axisTitlePaint.textSize / 3, axisTitlePaint)
        withSave {
            rotate(-90f)
            drawText(
                "Weight (g)",
                -(graphTopY + graphHeight / 2),
                weightTitleOffsetPx - axisTitlePaint.descent(),
                axisTitlePaint
            )
            if (hasFlowData && showFlowLine) {
                val flowAxisTitle = "Flow (g/s)"
                drawText(
                    flowAxisTitle,
                    -(graphTopY + graphHeight / 2),
                    size.width + flowTitleOffsetPx - axisTitlePaintFlow.descent(),
                    axisTitlePaintFlow
                )
            }
        }
    }
}