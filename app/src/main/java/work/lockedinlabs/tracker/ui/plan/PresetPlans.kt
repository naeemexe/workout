package work.lockedinlabs.tracker.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import work.lockedinlabs.tracker.pack.Level
import work.lockedinlabs.tracker.pack.PresetPlan
import work.lockedinlabs.tracker.ui.more.BackHeader
import work.lockedinlabs.tracker.ui.theme.LabCard
import work.lockedinlabs.tracker.ui.theme.dayColor

/** Ready-made plans, the ones that suit [level] first. */
fun List<PresetPlan>.forLevel(level: Level?): List<PresetPlan> =
    sortedBy { p -> if (level == null) 0 else p.levels.indexOf(level).takeIf { it >= 0 } ?: Int.MAX_VALUE }

/** A ready-made plan in a list: name, who it's for, how often, and its rotation. */
@Composable
fun PresetPlanCard(preset: PresetPlan, recommended: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LabCard(modifier.clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        preset.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (recommended) {
                        Spacer(Modifier.width(8.dp))
                        PresetPill("For you")
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    preset.levels.joinToString(", ") { it.label } + " · " + preset.schedule,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Rotation(preset)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "● Push  ● Pull  ● Legs  Rest", each workout with the color tag it'll get. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Rotation(preset: PresetPlan) {
    var color = 0
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        preset.days.forEach { d ->
            if (d.isRest) {
                Text("Rest", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val c = color++
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(dayColor(c)!!))
                    Spacer(Modifier.width(4.dp))
                    Text(d.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PresetPill(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Every day of a ready-made plan with its exercises and sets. */
@Composable
fun PresetDays(preset: PresetPlan, modifier: Modifier = Modifier) {
    var color = 0
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        preset.days.forEachIndexed { i, d ->
            LabCard {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Day ${i + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(52.dp),
                        )
                        if (!d.isRest) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(dayColor(color++)!!))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            if (d.isRest) "Rest" else d.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (d.isRest) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    d.exercises.forEach { e ->
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.padding(start = 52.dp)) {
                            Text(e.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                "${e.sets} sets",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** More → Session plans → a ready-made plan: what's in it, and a button to use it. */
@Composable
fun PresetPlanScreen(preset: PresetPlan, onUse: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            BackHeader(preset.name, onBack)
            Text(
                preset.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                preset.levels.joinToString(", ") { it.label } + " · " + preset.schedule,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
            PresetDays(preset)
            Spacer(Modifier.height(16.dp))
        }
        Button(
            onClick = onUse,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
        ) {
            Text("Use this plan", style = MaterialTheme.typography.titleMedium)
        }
    }
}
