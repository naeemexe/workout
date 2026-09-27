package work.lockedinlabs.tracker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import work.lockedinlabs.tracker.data.Profile
import work.lockedinlabs.tracker.domain.ExerciseSeries
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.saveable.rememberSaveable
import work.lockedinlabs.tracker.domain.StrengthPoint
import work.lockedinlabs.tracker.domain.Suggestion
import work.lockedinlabs.tracker.domain.headline
import work.lockedinlabs.tracker.domain.label
import work.lockedinlabs.tracker.ui.profile.ProfileAvatar
import work.lockedinlabs.tracker.ui.theme.Gain
import work.lockedinlabs.tracker.ui.theme.LabCard
import work.lockedinlabs.tracker.ui.theme.Loss
import java.time.LocalDate
import java.time.YearMonth
import work.lockedinlabs.tracker.ui.log.MonthGrid
import work.lockedinlabs.tracker.ui.log.MonthHeader
import work.lockedinlabs.tracker.ui.log.tagColors
import kotlin.math.abs

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onLogClick: () -> Unit,
    onStepUpClick: (exercise: String) -> Unit,
    profile: Profile?,
    onProfileClick: () -> Unit,
    weightLbs: Double?,
    weightNote: String?,
    onOpenWeight: () -> Unit,
    calendarTags: Map<Long, Int?>,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val header = @Composable { Header(profile, onProfileClick) }
    when {
        state.loading -> Box(modifier.fillMaxSize())
        !state.hasData -> EmptyHome(onLogClick, header, modifier)
        else -> HomeContent(state, viewModel::setRange, onStepUpClick, header, weightLbs, weightNote, onOpenWeight, calendarTags, onDayClick, modifier)
    }
}

/** Brand on the left, profile avatar on the right. */
@Composable
private fun Header(profile: Profile?, onProfileClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Brand()
        Spacer(Modifier.weight(1f))
        ProfileAvatar(profile, 40.dp, Modifier.clickable(onClick = onProfileClick))
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onRange: (ChartRange) -> Unit,
    onStepUpClick: (String) -> Unit,
    header: @Composable () -> Unit,
    bodyweightLbs: Double?,
    weightNote: String?,
    onOpenWeight: () -> Unit,
    calendarTags: Map<Long, Int?>,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier,
) {
    val today = LocalDate.now()
    var scrub by remember { mutableStateOf<Pair<StrengthPoint, Boolean>?>(null) }
    var focused by rememberSaveable { mutableStateOf<String?>(null) }
    // Simple = just the Strength Index; Detailed adds each exercise's line and the legend.
    var detailed by rememberSaveable { mutableStateOf(false) }
    val shownExercises = if (detailed) state.exercises else emptyList()
    val lineColors = remember(state.exercises) { state.exercises.mapIndexed { i, e -> e.name to ExercisePalette[i % ExercisePalette.size] }.toMap() }
    val trendColor = if (state.trendPerWeek >= 0) Gain else Loss

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        header()
        // A little breathing room so the Details pill doesn't crowd the avatar.
        Spacer(Modifier.height(16.dp))
        // "Details" sits on the same text line as "Strength Index" (baselines aligned).
        Row {
            Text("Strength Index", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).alignByBaseline())
            if (state.exercises.isNotEmpty()) {
                ChartModeToggle(detailed, Modifier.alignByBaseline()) { detailed = it }
                Spacer(Modifier.width(4.dp))
            }
        }

        val shown = scrub
        Text(
            "%.1f".format(shown?.first?.value ?: state.current),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
        )
        if (shown != null) {
            val (point, projected) = shown
            Text(
                (if (projected) "Projected · " else "") + point.day.label(today),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val up = state.change >= 0
            Text(
                "${if (up) "+" else "−"}%.1f (%.1f%%)  ".format(abs(state.change), abs(state.changePct)) + state.range.caption,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (up) Gain else Loss,
            )
        }

        Spacer(Modifier.height(20.dp))
        StrengthChart(
            points = state.points,
            projection = state.projection,
            start = state.chartStart,
            end = state.chartEnd,
            exercises = shownExercises,
            exerciseColors = lineColors,
            focused = focused,
            color = trendColor,
            onScrub = { p, projected -> scrub = p?.let { it to projected } },
            modifier = Modifier.fillMaxWidth().height(220.dp),
        )
        if (shownExercises.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            ExerciseLegend(state.exercises, lineColors, focused) { name -> focused = name.takeUnless { it == focused } }
        }
        Spacer(Modifier.height(12.dp))
        RangeSelector(state.range, trendColor, onRange)

        Spacer(Modifier.height(20.dp))
        HighlightTiles(state, bodyweightLbs, weightNote, onOpenWeight)
        Spacer(Modifier.height(12.dp))
        MuscleMapCard(state.muscles)
        if (state.readyToStepUp.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            StepUpCard(state.readyToStepUp, onStepUpClick)
        }
        Spacer(Modifier.height(12.dp))
        CalendarCard(calendarTags, onDayClick)
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * One small pill that turns the per-exercise lines on and off. Three colored dots hint at "more lines";
 * it fills in when they're showing.
 */
@Composable
private fun ChartModeToggle(detailed: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (detailed) primary.copy(alpha = 0.15f) else Color.Transparent)
            .border(1.dp, if (detailed) primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            .toggleable(value = detailed, role = Role.Switch, onValueChange = onChange)
            .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            ExercisePalette.take(3).forEach { c ->
                Box(Modifier.size(5.dp).clip(CircleShape).background(if (detailed) c else c.copy(alpha = 0.45f)))
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            "Details",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (detailed) FontWeight.SemiBold else FontWeight.Medium,
            color = if (detailed) primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Faint colors for per-exercise lines behind the main index line. */
private val ExercisePalette = listOf(
    Color(0xFF4DA3FF), Color(0xFFFFC53D), Color(0xFFFF7A45), Color(0xFFB37FEB),
    Color(0xFF36CFC9), Color(0xFFF759AB), Color(0xFF95DE64), Color(0xFFFFA940),
)

/** "● Squat +43%" per exercise in the chart window; tap one to highlight its line. */
@Composable
private fun ExerciseLegend(exercises: List<ExerciseSeries>, colors: Map<String, Color>, focused: String?, onToggle: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(exercises, key = { it.name }) { e ->
            val change = e.points.last().value / e.points.first().value * 100 - 100
            val isFocused = e.name == focused
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isFocused) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                    .clickable { onToggle(e.name) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(colors[e.name] ?: Color.Gray))
                Spacer(Modifier.width(6.dp))
                Text(
                    e.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (focused == null || isFocused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "%+.0f%%".format(change),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (change >= 0) Gain else Loss,
                )
            }
        }
    }
}

/** This month's calendar: trained days tinted in their plan day's color. Tap a day to open it on the Log tab. */
@Composable
private fun CalendarCard(tags: Map<Long, Int?>, onDayClick: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val ym = YearMonth.parse(month)
    val trained = tags.keys.count { YearMonth.from(LocalDate.ofEpochDay(it)) == ym }
    LabCard {
        Column(Modifier.padding(top = 8.dp, bottom = 12.dp)) {
            MonthHeader(
                ym,
                { month = it.toString() },
                Modifier.padding(start = 16.dp, end = 4.dp),
                trailing = when (trained) { 0 -> "No workouts yet"; 1 -> "1 day trained"; else -> "$trained days trained" },
            )
            MonthGrid(ym, today, picked = null, tagColor = tagColors(tags), onPick = onDayClick)
        }
    }
}

/** The app's name (Locked In); the company, Locked In Labs, is credited in Help & support and the Plan tab. */
@Composable
private fun Brand() {
    Text(
        "LOCKED IN",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 3.sp,
    )
}

@Composable
private fun RangeSelector(selected: ChartRange, color: Color, onSelect: (ChartRange) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        ChartRange.entries.forEach { r ->
            val isSelected = r == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) color.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { onSelect(r) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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

/** "Next session": exercises you're ready to step up on. Tap one to log it. */
@Composable
private fun StepUpCard(stepUps: List<Suggestion>, onExerciseClick: (String) -> Unit) {
    LabCard {
        Column(Modifier.padding(vertical = 8.dp)) {
            SectionTitle("Next session")
            stepUps.forEach { s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onExerciseClick(s.exercise) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(s.exercise, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(s.headline(), style = MaterialTheme.typography.bodySmall, color = Gain)
                    }
                    Text(
                        "STEP UP",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Gain)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun EmptyHome(onLogClick: () -> Unit, header: @Composable () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        header()
        EmptyHomeBody(onLogClick, Modifier.weight(1f))
    }
}

@Composable
private fun EmptyHomeBody(onLogClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Your strength graph starts\nwith your first set.", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Log what you lift. We'll chart your progress and tell you when it's time to go heavier.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onLogClick) { Text("Log your first workout") }
    }
}
