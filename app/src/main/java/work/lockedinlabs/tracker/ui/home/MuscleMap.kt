package work.lockedinlabs.tracker.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import work.lockedinlabs.tracker.domain.Muscle
import work.lockedinlabs.tracker.domain.MuscleState
import work.lockedinlabs.tracker.domain.MuscleStats
import work.lockedinlabs.tracker.ui.theme.LabCard
import work.lockedinlabs.tracker.domain.plainNumber
import work.lockedinlabs.tracker.pack.Pack

/** Home card: front + back body with each muscle colored by its sets this week against the weekly target. */
@Composable
fun MuscleMapCard(muscles: Map<Muscle, MuscleState>) {
    val anyTrained = muscles.values.any { it.daysSinceTrained != null }
    LabCard {
        Column(Modifier.padding(16.dp)) {
            Text("Muscle map", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                if (anyTrained) "Sets per muscle this week" else "Log a workout to see the muscles you train.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            MuscleFigures(muscles)
            Spacer(Modifier.height(12.dp))
            Legend()
            val fading = MuscleStats.neglected(muscles)
            if (fading.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Needs attention", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                fading.forEach { (m, s) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(m.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            setsLabel(s.weeklySets),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Front and back figures side by side, each muscle tinted by its intensity (0 = no sets lately, 1 = weekly target met). */
@Composable
fun MuscleFigures(muscles: Map<Muscle, MuscleState>, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = modifier.fillMaxWidth()) {
        BodyFigure(front = true, muscles = muscles, modifier = Modifier.weight(1f))
        BodyFigure(front = false, muscles = muscles, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Legend() {
    val (inactive, active) = muscleColors()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(inactive, active))),
        )
        Spacer(Modifier.width(8.dp))
        Text("${plainNumber(Pack.science.muscles.weeklyTargetSets)}+ sets", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "3 sets", "1.5 sets", "1 set" */
private fun setsLabel(sets: Double): String {
    val n = Math.round(sets * 2) / 2.0
    return "${plainNumber(n)} set" + if (n == 1.0) "" else "s"
}

@Composable
private fun muscleColors(): Pair<Color, Color> =
    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f) to MaterialTheme.colorScheme.primary

@Composable
private fun BodyFigure(front: Boolean, muscles: Map<Muscle, MuscleState>, modifier: Modifier) {
    val body = MaterialTheme.colorScheme.surfaceContainerHighest
    val (inactive, active) = muscleColors()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .fillMaxWidth(0.8f)
                .aspectRatio(W / H)
                .semantics { contentDescription = if (front) "Muscle map, front" else "Muscle map, back" },
        ) {
            val s = size.height / H
            val regions = if (front) frontRegions else backRegions
            withTransform({
                translate(left = (size.width - W * s) / 2)
                scale(s, s, Offset.Zero)
            }) {
                fun side() {
                    drawPath(bodyOutline, body)
                    regions.forEach { r ->
                        drawPath(r.outline, lerp(inactive, active, (muscles[r.muscle]?.intensity ?: 0.0).toFloat()))
                    }
                }
                side()
                scale(-1f, 1f, Offset(W / 2, 0f)) { side() }
            }
        }
        Text(if (front) "Front" else "Back", style = MaterialTheme.typography.labelSmall, color = labelColor)
    }
}

private const val W = 100f
private const val H = 210f

/** One muscle shape, on the figure's left side. The mirror image gives the right side. */
private class Region(val muscle: Muscle, vararg points: Float) {
    val outline = smoothPath(points, closed = true)
}

/**
 * A smooth curve through [points] (x, y pairs) on the 100 × 210 design grid. A point listed twice in a row is
 * a sharp corner. An open curve is closed with a straight line, which is how the body meets the midline.
 */
private fun smoothPath(points: FloatArray, closed: Boolean): Path {
    val n = points.size / 2
    fun index(i: Int) = if (closed) (i + n) % n else i.coerceIn(0, n - 1)
    fun x(i: Int) = points[index(i) * 2]
    fun y(i: Int) = points[index(i) * 2 + 1]
    return Path().apply {
        moveTo(x(0), y(0))
        for (i in 0 until if (closed) n else n - 1) {
            if (x(i) == x(i + 1) && y(i) == y(i + 1)) continue
            cubicTo(
                x(i) + (x(i + 1) - x(i - 1)) / 6, y(i) + (y(i + 1) - y(i - 1)) / 6,
                x(i + 1) - (x(i + 2) - x(i)) / 6, y(i + 1) - (y(i + 2) - y(i)) / 6,
                x(i + 1), y(i + 1),
            )
        }
        close()
    }
}

/** The figure's left half, head to foot and back to the midline. */
private val bodyOutline by lazy {
    smoothPath(
        floatArrayOf(
            50.4f, 4.0f, 46.0f, 4.5f, 42.8f, 7.5f, 41.7f, 12.5f, 42.0f, 18.0f, 43.2f, 22.6f, 44.8f, 26.0f,
            45.0f, 29.0f, 44.4f, 32.0f, 42.8f, 34.8f, 40.0f, 37.0f, 36.0f, 39.2f, 29.5f, 41.0f, 24.5f, 43.0f,
            20.8f, 47.5f, 18.9f, 54.0f, 17.6f, 62.0f, 15.8f, 72.0f, 13.9f, 82.0f, 11.6f, 90.0f, 9.6f, 100.0f,
            8.3f, 109.0f, 7.0f, 115.0f, 6.0f, 121.0f, 7.0f, 127.0f, 9.6f, 130.8f, 12.2f, 129.6f, 13.4f, 125.0f,
            13.3f, 121.4f, 14.8f, 118.6f, 14.9f, 113.0f, 16.8f, 104.0f, 20.0f, 94.0f, 23.0f, 86.0f, 25.8f, 76.0f,
            28.6f, 68.0f, 31.0f, 61.5f, 31.0f, 61.5f, 31.6f, 68.0f, 32.8f, 76.0f, 34.6f, 84.0f, 35.6f, 91.0f,
            34.6f, 99.0f, 32.0f, 107.0f, 30.2f, 116.0f, 29.6f, 127.0f, 30.2f, 140.0f, 31.8f, 152.0f,
            33.4f, 160.0f, 32.9f, 166.0f, 31.9f, 174.0f, 32.6f, 184.0f, 35.4f, 194.0f, 35.8f, 199.0f,
            33.2f, 203.5f, 32.3f, 206.6f, 34.0f, 208.0f, 38.0f, 208.0f, 42.0f, 207.8f, 44.0f, 206.0f,
            42.9f, 200.0f, 43.2f, 192.0f, 46.0f, 181.0f, 47.4f, 172.0f, 46.5f, 162.0f, 47.4f, 150.0f,
            48.4f, 135.0f, 48.9f, 124.0f, 50.4f, 117.0f, 50.4f, 117.0f,
        ),
        closed = false,
    )
}

private val frontRegions by lazy {
    listOf(
        Region(
            Muscle.CHEST,
            49.3f, 45.0f, 49.3f, 45.0f, 42.0f, 43.2f, 34.5f, 44.8f, 31.8f, 49.0f, 32.5f, 56.0f, 37.0f, 62.0f,
            43.5f, 63.6f, 49.3f, 61.5f, 49.3f, 61.5f,
        ),
        Region(
            Muscle.SHOULDERS,
            31.5f, 43.6f, 27.0f, 42.8f, 22.8f, 45.2f, 20.0f, 50.5f, 19.4f, 56.0f, 20.2f, 60.6f, 23.6f, 58.4f,
            27.4f, 53.6f, 30.4f, 48.0f,
        ),
        Region(
            Muscle.BICEPS,
            25.8f, 60.5f, 27.6f, 66.0f, 26.2f, 73.0f, 23.4f, 80.0f, 20.8f, 83.5f, 18.3f, 79.0f, 17.9f, 71.0f,
            19.6f, 64.5f, 22.5f, 61.0f,
        ),
        Region(
            Muscle.FOREARMS,
            20.8f, 87.5f, 19.6f, 93.0f, 16.6f, 102.0f, 14.0f, 110.5f, 12.0f, 112.0f, 10.0f, 110.0f,
            10.6f, 100.0f, 12.8f, 91.5f, 15.8f, 86.5f, 18.5f, 85.5f,
        ),
        Region(
            Muscle.OBLIQUES,
            34.6f, 65.5f, 38.6f, 67.0f, 40.0f, 78.0f, 40.0f, 90.0f, 39.4f, 100.0f, 37.0f, 104.0f, 36.0f, 98.0f,
            37.0f, 90.5f, 36.0f, 83.0f, 34.4f, 74.0f,
        ),
        Region(
            Muscle.ABS,
            42.8f, 65.6f, 45.4f, 65.6f, 48.0f, 65.6f, 49.2f, 67.0f, 49.2f, 69.6f, 49.2f, 72.2f, 48.0f, 73.6f,
            45.4f, 73.6f, 42.8f, 73.6f, 41.6f, 72.2f, 41.6f, 69.6f, 41.6f, 67.0f,
        ),
        Region(
            Muscle.ABS,
            42.8f, 75.1f, 45.4f, 75.1f, 48.0f, 75.1f, 49.2f, 76.5f, 49.2f, 79.1f, 49.2f, 81.7f, 48.0f, 83.1f,
            45.4f, 83.1f, 42.8f, 83.1f, 41.6f, 81.7f, 41.6f, 79.1f, 41.6f, 76.5f,
        ),
        Region(
            Muscle.ABS,
            42.8f, 84.6f, 45.4f, 84.6f, 48.0f, 84.6f, 49.2f, 86.0f, 49.2f, 88.6f, 49.2f, 91.2f, 48.0f, 92.6f,
            45.4f, 92.6f, 42.8f, 92.6f, 41.6f, 91.2f, 41.6f, 88.6f, 41.6f, 86.0f,
        ),
        Region(
            Muscle.ABS,
            42.8f, 94.1f, 45.4f, 94.1f, 48.0f, 94.1f, 49.2f, 95.5f, 49.2f, 100.1f, 49.2f, 104.6f, 43.0f, 106.0f,
            42.9f, 106.0f, 42.8f, 106.0f, 41.6f, 104.6f, 41.6f, 100.1f, 41.6f, 95.5f,
        ),
        Region(
            Muscle.QUADS,
            33.4f, 113.0f, 36.8f, 120.0f, 37.2f, 135.0f, 37.6f, 150.0f, 37.0f, 157.0f, 34.6f, 154.0f,
            32.2f, 143.0f, 31.2f, 128.0f, 31.6f, 118.0f,
        ),
        Region(
            Muscle.QUADS,
            39.6f, 110.0f, 43.0f, 117.0f, 43.6f, 130.0f, 42.6f, 146.0f, 40.2f, 151.5f, 38.8f, 146.0f,
            38.5f, 130.0f, 38.0f, 118.0f,
        ),
        Region(
            Muscle.QUADS,
            45.6f, 128.0f, 47.2f, 139.0f, 46.6f, 150.0f, 45.2f, 158.4f, 41.4f, 158.4f, 39.8f, 155.0f,
            40.6f, 151.8f, 44.0f, 147.0f, 45.0f, 138.0f,
        ),
        Region(
            Muscle.CALVES,
            36.0f, 165.5f, 38.0f, 172.0f, 37.6f, 181.0f, 35.8f, 190.0f, 34.0f, 182.0f, 33.0f, 174.0f,
        ),
        Region(
            Muscle.CALVES,
            44.6f, 166.0f, 46.4f, 172.0f, 45.2f, 182.0f, 43.2f, 190.0f, 41.8f, 181.0f, 42.0f, 171.5f,
        ),
    )
}

private val backRegions by lazy {
    listOf(
        Region(
            Muscle.TRAPS,
            49.4f, 30.0f, 49.4f, 30.0f, 45.6f, 33.2f, 41.5f, 37.4f, 35.0f, 40.6f, 31.0f, 43.2f, 31.0f, 43.2f,
            36.0f, 46.2f, 42.0f, 52.5f, 46.0f, 62.0f, 49.4f, 74.0f, 49.4f, 74.0f,
        ),
        Region(
            Muscle.SHOULDERS,
            31.5f, 43.6f, 27.0f, 42.8f, 22.8f, 45.2f, 20.0f, 50.5f, 19.4f, 56.0f, 20.2f, 60.6f, 23.6f, 58.4f,
            27.4f, 53.6f, 30.4f, 48.0f,
        ),
        Region(
            Muscle.UPPER_BACK,
            32.2f, 46.2f, 36.4f, 48.2f, 41.6f, 54.2f, 44.8f, 62.4f, 42.6f, 65.4f, 36.4f, 62.0f, 32.6f, 57.0f,
            31.4f, 51.0f,
        ),
        Region(
            Muscle.LATS,
            32.6f, 59.6f, 36.4f, 64.0f, 43.6f, 67.4f, 46.4f, 72.6f, 44.6f, 83.0f, 41.0f, 93.0f, 37.6f, 97.0f,
            36.8f, 90.5f, 35.0f, 82.0f, 33.0f, 72.0f,
        ),
        Region(
            Muscle.LOWER_BACK,
            49.3f, 76.5f, 49.3f, 76.5f, 47.6f, 76.0f, 45.8f, 85.0f, 42.8f, 94.0f, 43.4f, 98.4f, 49.3f, 101.4f,
            49.3f, 101.4f,
        ),
        Region(
            Muscle.TRICEPS,
            25.8f, 60.5f, 27.4f, 66.0f, 26.0f, 73.0f, 23.4f, 80.0f, 23.0f, 73.0f, 23.0f, 66.0f, 24.0f, 61.0f,
        ),
        Region(
            Muscle.TRICEPS,
            22.4f, 61.0f, 21.4f, 67.0f, 21.4f, 74.0f, 22.2f, 82.0f, 20.4f, 84.0f, 18.3f, 79.0f, 17.9f, 71.0f,
            19.6f, 64.5f,
        ),
        Region(
            Muscle.FOREARMS,
            20.8f, 87.5f, 19.6f, 93.0f, 16.6f, 102.0f, 14.0f, 110.5f, 12.0f, 112.0f, 10.0f, 110.0f,
            10.6f, 100.0f, 12.8f, 91.5f, 15.8f, 86.5f, 18.5f, 85.5f,
        ),
        Region(
            Muscle.GLUTES,
            33.6f, 105.0f, 40.0f, 100.6f, 49.3f, 103.6f, 49.3f, 103.6f, 49.3f, 117.8f, 49.3f, 117.8f,
            44.0f, 121.8f, 36.0f, 120.8f, 31.6f, 114.0f,
        ),
        Region(
            Muscle.HAMSTRINGS,
            32.4f, 124.0f, 37.0f, 123.6f, 39.2f, 132.0f, 39.0f, 146.0f, 38.0f, 157.0f, 35.2f, 154.0f,
            32.6f, 143.0f, 31.4f, 132.0f,
        ),
        Region(
            Muscle.HAMSTRINGS,
            40.4f, 123.8f, 47.8f, 122.6f, 47.6f, 135.0f, 46.6f, 148.0f, 45.0f, 158.0f, 42.0f, 155.0f,
            40.6f, 146.0f, 40.2f, 132.0f,
        ),
        Region(
            Muscle.CALVES,
            39.2f, 160.6f, 39.6f, 172.0f, 38.8f, 185.0f, 37.2f, 190.0f, 33.8f, 179.0f, 33.0f, 171.0f,
            35.2f, 163.0f,
        ),
        Region(
            Muscle.CALVES,
            40.8f, 160.6f, 44.8f, 162.6f, 47.0f, 171.0f, 45.6f, 181.0f, 41.8f, 190.0f, 40.9f, 175.0f,
        ),
    )
}
