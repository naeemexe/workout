package work.lockedinlabs.tracker.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import work.lockedinlabs.tracker.ui.theme.dayColor

private val headerFmt = DateTimeFormatter.ofPattern("MMM d, yyyy")
private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy")

/**
 * Month calendar for picking the Log date. Days you trained get a soft circle in their plan day's color;
 * the picked day is a solid circle, today is outlined, and future days are off.
 *
 * @param tagColor color for a logged date (null = not logged).
 */
@Composable
fun CalendarDialog(
    current: LocalDate,
    tagColor: (LocalDate) -> Color?,
    onPicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now()
    var picked by rememberSaveable { mutableStateOf(current.toEpochDay()) }
    var month by rememberSaveable { mutableStateOf(YearMonth.from(current).toString()) }
    val ym = YearMonth.parse(month)
    val pickedDate = LocalDate.ofEpochDay(picked)

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(top = 20.dp, bottom = 8.dp)) {
                Text(
                    "Select date",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Text(
                    pickedDate.format(headerFmt),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                MonthHeader(ym, { month = it.toString() }, Modifier.padding(start = 24.dp, end = 12.dp, top = 8.dp))

                MonthGrid(ym, today, pickedDate, tagColor, onPick = { picked = it.toEpochDay() })

                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onPicked(pickedDate) }) { Text("OK") }
                }
            }
        }
    }
}

/**
 * Maps [LogViewModel.loggedDayTags] to colors: the plan day's color, grey for a custom session,
 * the theme color when there's no plan day. Null = nothing logged that day.
 */
@Composable
fun tagColors(tags: Map<Long, Int?>): (LocalDate) -> Color? {
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val plain = MaterialTheme.colorScheme.primary
    return { d ->
        val day = d.toEpochDay()
        if (day !in tags) null
        else when (val t = tags[day]) {
            null -> plain
            LogViewModel.CUSTOM_TAG -> neutral
            else -> dayColor(t)
        }
    }
}

/** Month header with arrows (can't go past this month). */
@Composable
fun MonthHeader(ym: YearMonth, onMonth: (YearMonth) -> Unit, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(ym.format(monthFmt), style = MaterialTheme.typography.titleSmall)
            trailing?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        IconButton(onClick = { onMonth(ym.minusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        IconButton(onClick = { onMonth(ym.plusMonths(1)) }, enabled = ym < YearMonth.now()) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

/** A month of days; trained days tinted with their color, [picked] solid, today outlined, future disabled. */
@Composable
fun MonthGrid(
    ym: YearMonth,
    today: LocalDate,
    picked: LocalDate?,
    tagColor: (LocalDate) -> Color?,
    onPick: (LocalDate) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    // Weeks start on Sunday, like the system picker.
    val lead = ym.atDay(1).dayOfWeek.value % 7
    val cells = List(lead) { null } + (1..ym.lengthOfMonth()).map { ym.atDay(it) }
    Column(Modifier.padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
                .forEach { d ->
                    Text(
                        d.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                    )
                }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            val future = date > today
                            val isPicked = date == picked
                            val tag = tagColor(date)
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isPicked -> tag ?: primary
                                            tag != null -> tag.copy(alpha = 0.28f)
                                            else -> Color.Transparent
                                        },
                                    )
                                    .then(if (date == today && !isPicked) Modifier.border(1.dp, primary, CircleShape) else Modifier)
                                    .clickable(enabled = !future) { onPick(date) }
                                    .semantics { contentDescription = date.toString() + if (tag != null) ", trained" else "" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${date.dayOfMonth}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isPicked || tag != null) FontWeight.SemiBold else FontWeight.Normal,
                                    color = when {
                                        isPicked -> Color.Black.copy(alpha = 0.85f)
                                        future -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
