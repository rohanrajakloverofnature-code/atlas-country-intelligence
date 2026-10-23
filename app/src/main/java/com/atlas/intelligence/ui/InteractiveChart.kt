package com.atlas.intelligence.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atlas.intelligence.data.Observation
import kotlin.math.abs
import kotlin.math.max
import java.util.Locale

/** Comparable definitions and units are checked by the caller. Never interpolates data. */
data class ChartSeries(val label: String, val points: List<Observation>, val color: Color)

/** Hit testing uses the same actual-calendar-year scale as the drawn points. */
fun chartHitYear(years: List<Int>, x: Float, left: Float, right: Float): Int? {
    if (years.isEmpty() || right <= left) return null
    val target = years.first() + ((x.coerceIn(left, right) - left) / (right - left)) * (years.last() - years.first())
    return years.minByOrNull { abs(it - target) }
}

@Composable
fun InteractiveChart(
    series: List<ChartSeries>, modifier: Modifier, unit: String,
    axes: Boolean = true, showLabels: Boolean = false,
    instruction: String = "Touch or drag horizontally to inspect"
) {
    val prepared = remember(series) { series.map { it.copy(points = it.points.filter { p -> p.value.isFinite() }.distinctBy { p -> p.year }.sortedBy { p -> p.year }) } }
    val years = remember(prepared) { prepared.flatMap { it.points }.map { it.year }.distinct().sorted() }
    val byYear = remember(prepared) { prepared.map { it.points.associateBy { p -> p.year } } }
    val values = remember(prepared) { prepared.flatMap { it.points }.map { it.value } }
    val rawMin = remember(values) { values.minOrNull() ?: 0.0 }
    val rawMax = remember(values) { values.maxOrNull() ?: 1.0 }
    val pad = remember(rawMin, rawMax) { if (rawMin == rawMax) max(abs(rawMin) * .05, 1.0) else (rawMax - rawMin) * .08 }
    val minimum = rawMin - pad
    val span = (rawMax + pad - minimum).coerceAtLeast(1e-12)
    var selectedYear by remember(prepared) { mutableStateOf<Int?>(null) }
    val selectionText = selectedYear?.let { year ->
        "$year · " + prepared.indices.joinToString(if (axes) "\n" else " · ") { i ->
            val p = byYear[i][year]
            "${prepared[i].label}: ${p?.let { "${formatExact(it.value)} $unit" } ?: "No observation"}"
        }
    } ?: if (years.isEmpty()) "No sourced observations" else instruction
    Column(modifier) {
        Spacer(Modifier.fillMaxWidth().weight(1f)
            .semantics { contentDescription = "Interactive observation chart. $selectionText. Unit: $unit" }
            .pointerInput(years, showLabels) {
                detectTapGestures { pos -> selectedYear = chartHitYear(years, pos.x, if (showLabels) 62.dp.toPx() else 6.dp.toPx(), size.width - 8.dp.toPx()) }
            }
            .pointerInput(years, showLabels) {
                // Wait for horizontal slop; vertical gestures remain with the enclosing list.
                detectHorizontalDragGestures(onHorizontalDrag = { change, _ ->
                    selectedYear = chartHitYear(years, change.position.x, if (showLabels) 62.dp.toPx() else 6.dp.toPx(), size.width - 8.dp.toPx())
                })
            }
            .drawWithCache {
                val left = if (showLabels) 62.dp.toPx() else 6.dp.toPx()
                val right = max(left + 1f, size.width - 8.dp.toPx())
                val top = 8.dp.toPx()
                val bottom = max(top + 1f, size.height - if (showLabels) 24.dp.toPx() else 6.dp.toPx())
                val first = years.firstOrNull() ?: 0
                val last = years.lastOrNull() ?: first
                val yearSpan = max(last - first, 1).toFloat()
                fun point(p: Observation) = Offset(if (first == last) (left + right) / 2 else left + (p.year - first) / yearSpan * (right - left), bottom - ((p.value - minimum) / span).toFloat() * (bottom - top))
                // These allocations occur only on data/size changes, never on pointer movement.
                val paths = prepared.map { s -> Path().apply { s.points.forEachIndexed { i, p -> val xy = point(p); if (i == 0 || p.year - s.points[i - 1].year > 1) moveTo(xy.x, xy.y) else lineTo(xy.x, xy.y) } } }
                val positions = prepared.map { s -> s.points.associate { it.year to point(it) } }
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(173, 165, 187); textSize = 10.sp.toPx() }
                onDrawBehind {
                    if (years.isNotEmpty()) {
                        if (axes) for (f in listOf(0f, .5f, 1f)) drawLine(Color(0x29342C45), Offset(left, top + (bottom - top) * f), Offset(right, top + (bottom - top) * f), 1.dp.toPx())
                        prepared.forEachIndexed { i, s ->
                            drawPath(paths[i], s.color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                            if (axes || s.points.size == 1) positions[i].values.forEach { drawCircle(s.color, 2.5.dp.toPx(), it) }
                        }
                        selectedYear?.let { year ->
                            val x = if (first == last) (left + right) / 2 else left + (year - first) / yearSpan * (right - left)
                            drawLine(Color.White.copy(.75f), Offset(x, top), Offset(x, bottom), 1.dp.toPx())
                            prepared.indices.forEach { i -> positions[i][year]?.let { xy -> drawCircle(Color.White, 5.dp.toPx(), xy); drawCircle(prepared[i].color, 3.dp.toPx(), xy) } }
                        }
                        if (showLabels) drawContext.canvas.nativeCanvas.apply {
                            drawText(formatAxis(rawMax + pad), 0f, top + paint.textSize, paint)
                            drawText(formatAxis(minimum), 0f, bottom, paint)
                            drawText(first.toString(), left, size.height - 4.dp.toPx(), paint)
                            if (last != first) drawText(last.toString(), right - paint.measureText(last.toString()), size.height - 4.dp.toPx(), paint)
                        }
                    }
                }
            })
        Text(selectionText, color = if (selectedYear == null) Color(0xFFADA5BB) else Color.White,
            fontSize = 11.sp, maxLines = if (axes) max(3, prepared.size) else 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp))
    }
}

private fun formatExact(value: Double): String = value.toBigDecimal().stripTrailingZeros().toPlainString()
private fun formatAxis(value: Double): String {
    val n = abs(value)
    return when {
        n >= 1e12 -> String.format(Locale.US, "%.1fT", value / 1e12)
        n >= 1e9 -> String.format(Locale.US, "%.1fB", value / 1e9)
        n >= 1e6 -> String.format(Locale.US, "%.1fM", value / 1e6)
        n >= 1e3 -> String.format(Locale.US, "%.1fK", value / 1e3)
        else -> String.format(Locale.US, "%.2f", value)
    }
}
