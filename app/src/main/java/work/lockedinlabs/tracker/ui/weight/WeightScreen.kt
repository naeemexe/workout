package work.lockedinlabs.tracker.ui.weight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import work.lockedinlabs.tracker.data.WeightEntry
import work.lockedinlabs.tracker.domain.label
import work.lockedinlabs.tracker.domain.weightText
import work.lockedinlabs.tracker.ui.theme.LabCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val axisDate = DateTimeFormatter.ofPattern("MMM d")

/** Body weight: chart on top, today's weigh-in, then every entry. */
@Composable
fun WeightScreen(viewModel: WeightViewModel, snackbar: SnackbarHostState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                is WeightEvent.Saved -> snackbar.showSnackbar("Logged ${e.entry.lbs.weightText()} lb for today")
                is WeightEvent.Deleted -> {
                    val r = snackbar.showSnackbar("Deleted ${e.entry.date.label()}", "Undo", duration = SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) viewModel.restore(e.entry)
                }
            }
        }
    }
    var scrub by remember { mutableStateOf<WeightEntry?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Weight", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        if (!viewModel.loaded) return@Column

        // Headline: latest weight (or the scrubbed point) and the change over the range.
        Spacer(Modifier.height(8.dp))
        val shown = scrub ?: viewModel.latest
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                shown?.lbs?.weightText() ?: "—",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(4.dp))
            Text("lb", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        }
        Text(
            when {
                scrub != null -> scrub!!.date.label()
                shown == null -> "Log your first weigh-in below"
                else -> viewModel.change?.let { "${signed(it)} lb in ${rangeCaption(viewModel.range)}" } ?: "Last weighed ${shown.date.label().lowercase()}"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (viewModel.window.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            WeightChart(
                points = viewModel.window,
                start = viewModel.chartStart,
                end = LocalDate.now(),
                color = MaterialTheme.colorScheme.primary,
                onScrub = { scrub = it },
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )
        } else if (viewModel.entries.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("No weigh-ins in this range.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (viewModel.entries.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            RangeChips(viewModel.range, viewModel::selectRange)
        }

        // Today's weigh-in.
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = viewModel.input,
                onValueChange = viewModel::onInput,
                label = { Text("Today's weight") },
                suffix = { Text("lb") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); viewModel.save() }),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { focus.clearFocus(); viewModel.save() },
                enabled = viewModel.canSave,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(56.dp).padding(top = 6.dp),
            ) { Text(if (viewModel.todayEntry != null) "Update" else "Save") }
        }

        Spacer(Modifier.height(24.dp))
        Text("History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (viewModel.entries.isEmpty()) {
            Text("Nothing yet. Weigh in at the same time each day.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LabCard {
                val list = viewModel.entries
                list.forEachIndexed { i, e ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    val diff = list.getOrNull(i + 1)?.let { e.lbs - it.lbs }
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(e.date.label(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (diff != null && abs(diff) >= 0.05) {
                            Text(signed(diff), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                        }
                        Text("${e.lbs.weightText()} lb", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { viewModel.delete(e) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete ${e.date.label()}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun signed(v: Double) = (if (v >= 0) "+" else "−") + abs(v).weightText()

private fun rangeCaption(r: WeightRange) = when (r) {
    WeightRange.MONTH -> "1 month"
    WeightRange.QUARTER -> "3 months"
    WeightRange.HALF -> "6 months"
    WeightRange.YEAR -> "1 year"
    WeightRange.ALL -> "all time"
}

@Composable
private fun RangeChips(selected: WeightRange, onSelect: (WeightRange) -> Unit) {
    val color = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        WeightRange.entries.forEach { r ->
            val isSelected = r == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) color.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { onSelect(r) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    r.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Weigh-ins on a [start]..[end] date axis with dots, a soft fill, and touch-to-scrub. [points] oldest first. */
@Composable
private fun WeightChart(
    points: List<WeightEntry>,
    start: LocalDate,
    end: LocalDate,
    color: Color,
    onScrub: (WeightEntry?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val minV = points.minOf { it.lbs }
    val maxV = points.maxOf { it.lbs }
    val pad = (maxV - minV).coerceAtLeast(4.0) * 0.25
    val lo = minV - pad
    val hi = maxV + pad
    val startDay = start.toEpochDay()
    val span = (end.toEpochDay() - startDay).coerceAtLeast(1).toFloat()

    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val scrubColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val holeColor = MaterialTheme.colorScheme.background
    val density = LocalDensity.current
    val gutter = with(density) { 36.dp.toPx() }
    val axisHeight = with(density) { 20.dp.toPx() }

    var scrubIndex by remember(points) { mutableStateOf<Int?>(null) }
    val scrubCallback by rememberUpdatedState(onScrub)
    fun scrubTo(i: Int?) {
        if (i == scrubIndex) return
        scrubIndex = i
        scrubCallback(i?.let { points[it] })
    }
    fun indexAt(x: Float, width: Float): Int {
        val day = startDay + (x / (width - gutter)).coerceIn(0f, 1f) * span
        return points.indices.minBy { abs(points[it].epochDay - day) }
    }

    Canvas(
        modifier
            .semantics { contentDescription = "Weight chart, latest ${points.last().lbs.weightText()} pounds" }
            .pointerInput(points) {
                detectTapGestures(onPress = { o ->
                    scrubTo(indexAt(o.x, size.width.toFloat()))
                    tryAwaitRelease()
                    scrubTo(null)
                })
            }
            .pointerInput(points) {
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
        fun x(e: WeightEntry) = w * (e.epochDay - startDay) / span
        fun y(v: Double) = (h * (1 - (v - lo) / (hi - lo))).toFloat()

        for (frac in listOf(0.2f, 0.5f, 0.8f)) {
            val gy = h * frac
            drawLine(gridColor, Offset(0f, gy), Offset(w, gy), strokeWidth = 1.dp.toPx())
            val layout = measurer.measure((if (hi - lo < 10) "%.1f" else "%.0f").format(hi - (hi - lo) * frac), labelStyle)
            drawText(layout, topLeft = Offset(w + 6.dp.toPx(), gy - layout.size.height / 2f))
        }
        val startLabel = measurer.measure(start.format(axisDate), labelStyle)
        drawText(startLabel, topLeft = Offset(0f, h + 4.dp.toPx()))
        val endLabel = measurer.measure("Today", labelStyle)
        drawText(endLabel, topLeft = Offset(w - endLabel.size.width, h + 4.dp.toPx()))

        val line = Path().apply {
            moveTo(x(points[0]), y(points[0].lbs))
            for (i in 1 until points.size) lineTo(x(points[i]), y(points[i].lbs))
        }
        if (points.size >= 2) {
            val fill = Path().apply {
                addPath(line)
                lineTo(x(points.last()), h)
                lineTo(x(points[0]), h)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), Color.Transparent), endY = h))
            drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        // A dot per weigh-in (only while they're far enough apart to read).
        val dotted = points.size <= 60
        points.forEachIndexed { i, e ->
            if (dotted || i == points.lastIndex) drawCircle(color, radius = 3.5.dp.toPx(), center = Offset(x(e), y(e.lbs)))
        }

        scrubIndex?.let { i ->
            val sx = x(points[i])
            val p = Offset(sx, y(points[i].lbs))
            drawLine(scrubColor, Offset(sx, 0f), Offset(sx, h), strokeWidth = 1.dp.toPx())
            drawCircle(color, radius = 6.dp.toPx(), center = p)
            drawCircle(holeColor, radius = 3.dp.toPx(), center = p)
        }
    }
}
