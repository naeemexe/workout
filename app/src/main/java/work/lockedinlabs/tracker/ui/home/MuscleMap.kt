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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import work.lockedinlabs.tracker.domain.Muscle
import work.lockedinlabs.tracker.domain.MuscleState
import work.lockedinlabs.tracker.domain.MuscleStats
import work.lockedinlabs.tracker.ui.theme.LabCard
import kotlin.math.atan2
import kotlin.math.hypot
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
            val fig = Figure(this)
            fun color(m: Muscle) = lerp(inactive, active, (muscles[m]?.intensity ?: 0.0).toFloat())
            fig.silhouette(body)
            if (front) fig.frontMuscles(::color) else fig.backMuscles(::color)
        }
        Text(if (front) "Front" else "Back", style = MaterialTheme.typography.labelSmall, color = labelColor)
    }
}

private const val W = 100f
private const val H = 210f

/**
 * A simple stylized figure in a 100 × 210 design grid, scaled to the canvas. Shapes are drawn once for the
 * figure's left side and mirrored.
 */
private class Figure(private val scope: DrawScope) {
    private val s = scope.size.height / H
    private val ox = (scope.size.width - W * s) / 2

    private fun p(x: Float, y: Float) = Offset(ox + x * s, y * s)

    /** Runs [draw] for the shape and its mirror image. */
    private inline fun both(draw: (mirror: Boolean) -> Unit) { draw(false); draw(true) }
    private fun mx(x: Float, mirror: Boolean) = if (mirror) W - x else x

    private fun oval(cx: Float, cy: Float, w: Float, h: Float, c: Color) =
        scope.drawOval(c, topLeft = p(cx - w / 2, cy - h / 2), size = Size(w * s, h * s))

    private fun rect(l: Float, t: Float, r: Float, b: Float, radius: Float, c: Color) =
        scope.drawRoundRect(c, topLeft = p(l, t), size = Size((r - l) * s, (b - t) * s), cornerRadius = CornerRadius(radius * s))

    /** A capsule from (x1,y1) to (x2,y2) of the given width — used for limbs and long muscles. */
    private fun limb(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, c: Color) {
        val a = p(x1, y1)
        val b = p(x2, y2)
        val center = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
        val len = hypot(b.x - a.x, b.y - a.y)
        val w = width * s
        val deg = Math.toDegrees(atan2(-(b.x - a.x), b.y - a.y).toDouble()).toFloat()
        scope.rotate(deg, center) {
            drawRoundRect(c, topLeft = Offset(center.x - w / 2, center.y - (len + w) / 2), size = Size(w, len + w), cornerRadius = CornerRadius(w / 2))
        }
    }

    private fun polygon(c: Color, vararg pts: Pair<Float, Float>) {
        val path = Path().apply {
            pts.forEachIndexed { i, (x, y) -> p(x, y).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
            close()
        }
        scope.drawPath(path, c)
    }

    fun silhouette(c: Color) {
        oval(50f, 13f, 15f, 19f, c) // head
        rect(45f, 19f, 55f, 31f, 3f, c) // neck
        rect(29f, 29f, 71f, 60f, 11f, c) // shoulders + chest
        polygon(c, 32f to 52f, 68f to 52f, 64f to 96f, 36f to 96f) // waist
        rect(34f, 88f, 66f, 112f, 9f, c) // hips
        both { m ->
            limb(mx(27f, m), 35f, mx(21f, m), 70f, 11f, c) // upper arm
            limb(mx(21f, m), 70f, mx(16f, m), 104f, 9f, c) // forearm
            oval(mx(15f, m), 111f, 8f, 10f, c) // hand
            limb(mx(41f, m), 104f, mx(40f, m), 152f, 15f, c) // thigh
            limb(mx(40f, m), 152f, mx(40f, m), 197f, 10f, c) // shin
            oval(mx(40f, m), 203f, 12f, 6f, c) // foot
        }
    }

    fun frontMuscles(color: (Muscle) -> Color) {
        both { m ->
            oval(mx(41f, m), 44f, 17f, 13f, color(Muscle.CHEST))
            oval(mx(29f, m), 36f, 13f, 12f, color(Muscle.SHOULDERS))
            limb(mx(26f, m), 42f, mx(22f, m), 63f, 7.5f, color(Muscle.BICEPS))
            limb(mx(21f, m), 75f, mx(17f, m), 99f, 6.5f, color(Muscle.FOREARMS))
            limb(mx(37f, m), 60f, mx(39f, m), 86f, 5f, color(Muscle.OBLIQUES))
            limb(mx(41f, m), 111f, mx(41f, m), 145f, 11.5f, color(Muscle.QUADS))
            limb(mx(40f, m), 161f, mx(40f, m), 186f, 6.5f, color(Muscle.CALVES))
            // abs: 3 rows of blocks either side of the midline
            for (row in 0 until 3) {
                val top = 55f + row * 9f
                if (!m) rect(43.5f, top, 49.2f, top + 7.5f, 2f, color(Muscle.ABS))
                else rect(50.8f, top, 56.5f, top + 7.5f, 2f, color(Muscle.ABS))
            }
        }
    }

    fun backMuscles(color: (Muscle) -> Color) {
        polygon(color(Muscle.TRAPS), 50f to 22f, 63f to 33f, 50f to 48f, 37f to 33f)
        rect(45f, 78f, 55f, 94f, 3f, color(Muscle.LOWER_BACK))
        both { m ->
            oval(mx(29f, m), 36f, 13f, 12f, color(Muscle.SHOULDERS))
            oval(mx(43f, m), 49f, 10f, 12f, color(Muscle.UPPER_BACK))
            limb(mx(37.5f, m), 52f, mx(42f, m), 76f, 8.5f, color(Muscle.LATS))
            limb(mx(26f, m), 42f, mx(22f, m), 63f, 7.5f, color(Muscle.TRICEPS))
            limb(mx(21f, m), 75f, mx(17f, m), 99f, 6.5f, color(Muscle.FOREARMS))
            oval(mx(43.5f, m), 103f, 14f, 14f, color(Muscle.GLUTES))
            limb(mx(41f, m), 116f, mx(41f, m), 146f, 10.5f, color(Muscle.HAMSTRINGS))
            limb(mx(40f, m), 158f, mx(40f, m), 180f, 8.5f, color(Muscle.CALVES))
        }
    }
}
