package work.lockedinlabs.tracker.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import work.lockedinlabs.tracker.domain.ExerciseSeries
import work.lockedinlabs.tracker.domain.StrengthPoint
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val axisDate = DateTimeFormatter.ofPattern("MMM d")

/**
 * Stock-ticker style chart on a fixed time axis [start]..[end]: solid history (drawn only where you have data),
 * dashed projection, a "today" marker, and touch-to-scrub. `projection[0]` is today's point.
 */
@Composable
fun StrengthChart(
    points: List<StrengthPoint>,
    projection: List<StrengthPoint>,
    start: LocalDate,
    end: LocalDate,
    exercises: List<ExerciseSeries>,
    exerciseColors: Map<String, Color>,
    focused: String?,
    color: Color,
    onScrub: (point: StrengthPoint?, projected: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return
    val all = remember(points, projection) { points + projection.drop(1) }
    // Scale fits the index and every exercise line.
    val minV = minOf(all.minOf { it.value }, exercises.minOfOrNull { e -> e.points.minOf { it.value } } ?: Double.MAX_VALUE)
    val maxV = maxOf(all.maxOf { it.value }, exercises.maxOfOrNull { e -> e.points.maxOf { it.value } } ?: -Double.MAX_VALUE)
    val pad = (maxV - minV).coerceAtLeast(4.0) * 0.2
    val lo = minV - pad
    val hi = maxV + pad
    val startDay = start.toEpochDay()
    val span = (end.toEpochDay() - startDay).coerceAtLeast(1).toFloat()

    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val scrubColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val todayColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val holeColor = MaterialTheme.colorScheme.background
    val density = LocalDensity.current
    val gutter = with(density) { 36.dp.toPx() }
    val axisHeight = with(density) { 20.dp.toPx() }

    var scrubIndex by remember(all) { mutableStateOf<Int?>(null) }
    val scrubCallback by rememberUpdatedState(onScrub)
    fun scrubTo(i: Int?) {
        if (i == scrubIndex) return
        scrubIndex = i
        scrubCallback(i?.let { all[it] }, i != null && i >= points.size)
    }
    /** Nearest point (by date) to a touch at [x]. */
    fun indexAt(x: Float, width: Float): Int {
        val day = startDay + (x / (width - gutter)).coerceIn(0f, 1f) * span
        return all.indices.minBy { abs(all[it].day.toEpochDay() - day) }
    }

    val today = points.last()
    Canvas(
        modifier
            .semantics { contentDescription = "Strength index chart, currently ${"%.1f".format(today.value)}" }
            .pointerInput(all) {
                detectTapGestures(onPress = { o ->
                    scrubTo(indexAt(o.x, size.width.toFloat()))
                    tryAwaitRelease()
                    scrubTo(null)
                })
            }
            .pointerInput(all) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> scrubTo(indexAt(o.x, size.width.toFloat())) },
                    onDragEnd = { scrubTo(null) },
                    onDragCancel = { scrubTo(null) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        scrubTo(indexAt(change.position.x, size.width.toFloat()))
                    },
                )
            },
    ) {
        val w = size.width - gutter
        val h = size.height - axisHeight
        fun x(p: StrengthPoint) = w * (p.day.toEpochDay() - startDay) / span
        fun y(v: Double) = (h * (1 - (v - lo) / (hi - lo))).toFloat()

        // Value grid on the right, like a trading chart.
        for (frac in listOf(0.2f, 0.5f, 0.8f)) {
            val gy = h * frac
            drawLine(gridColor, Offset(0f, gy), Offset(w, gy), strokeWidth = 1.dp.toPx())
            val layout = measurer.measure("%.0f".format(hi - (hi - lo) * frac), labelStyle)
            drawText(layout, topLeft = Offset(w + 6.dp.toPx(), gy - layout.size.height / 2f))
        }

        // Today divider: history to the left, projection to the right.
        val todayX = x(today)
        drawLine(
            todayColor, Offset(todayX, 0f), Offset(todayX, h), strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
        )

        // Date axis: window start, today, window end.
        val startLabel = measurer.measure(start.format(axisDate), labelStyle)
        drawText(startLabel, topLeft = Offset(0f, h + 4.dp.toPx()))
        val todayLabel = measurer.measure("Today", labelStyle)
        drawText(todayLabel, topLeft = Offset(todayX - todayLabel.size.width / 2f, h + 4.dp.toPx()))
        val endLabel = measurer.measure(end.format(axisDate), labelStyle)
        drawText(endLabel, topLeft = Offset(w - endLabel.size.width, h + 4.dp.toPx()))

        // Each exercise's own line, faint in the background (the focused one stronger).
        for (e in exercises) {
            val c = exerciseColors[e.name] ?: continue
            val isFocused = e.name == focused
            val path = Path().apply {
                moveTo(x(e.points[0]), y(e.points[0].value))
                for (i in 1 until e.points.size) lineTo(x(e.points[i]), y(e.points[i].value))
            }
            drawPath(
                path,
                c.copy(alpha = if (isFocused) 0.9f else if (focused == null) 0.35f else 0.15f),
                style = Stroke(if (isFocused) 2.dp.toPx() else 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        // History: only where there's data (nothing is drawn before your first log).
        val line = Path().apply {
            moveTo(x(points[0]), y(points[0].value))
            for (i in 1 until points.size) lineTo(x(points[i]), y(points[i].value))
        }
        if (points.size >= 2) {
            val fill = Path().apply {
                addPath(line)
                lineTo(x(points.last()), h)
                lineTo(x(points[0]), h)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.30f), Color.Transparent), endY = h))
        }
        drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        if (projection.size >= 2) {
            val proj = Path().apply {
                moveTo(x(projection[0]), y(projection[0].value))
                for (j in 1 until projection.size) lineTo(x(projection[j]), y(projection[j].value))
            }
            drawPath(
                proj,
                color.copy(alpha = 0.6f),
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                ),
            )
        }

        // "You are here" dot.
        val here = Offset(todayX, y(today.value))
        drawCircle(color.copy(alpha = 0.25f), radius = 9.dp.toPx(), center = here)
        drawCircle(color, radius = 4.dp.toPx(), center = here)

        scrubIndex?.let { i ->
            val sx = x(all[i])
            val p = Offset(sx, y(all[i].value))
            drawLine(scrubColor, Offset(sx, 0f), Offset(sx, h), strokeWidth = 1.dp.toPx())
            drawCircle(color, radius = 6.dp.toPx(), center = p)
            drawCircle(holeColor, radius = 3.dp.toPx(), center = p)
        }
    }
}
