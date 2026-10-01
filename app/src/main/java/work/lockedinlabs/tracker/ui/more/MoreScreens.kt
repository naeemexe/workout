package work.lockedinlabs.tracker.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.lockedinlabs.tracker.domain.Muscle
import work.lockedinlabs.tracker.domain.MuscleState
import work.lockedinlabs.tracker.ui.home.MuscleFigures
import work.lockedinlabs.tracker.ui.theme.LabCard

/** More tab: session plans, progression plans, and the exercise library. */
@Composable
fun MoreScreen(
    sessionSummary: String,
    progressionSummary: String,
    exerciseSummary: String,
    onSessionPlans: () -> Unit,
    onProgressionPlans: () -> Unit,
    onExercises: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("More", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        MenuCard(Icons.Filled.DateRange, "Session plans", sessionSummary, onSessionPlans)
        Spacer(Modifier.height(10.dp))
        MenuCard(Icons.Filled.Star, "Progression plans", progressionSummary, onProgressionPlans)
        Spacer(Modifier.height(10.dp))
        MenuCard(Icons.AutoMirrored.Filled.List, "Exercises", exerciseSummary, onExercises)
        Spacer(Modifier.height(32.dp))
        Text(
            "LOCKED IN LABS · lockedinlabs.work",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MenuCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    LabCard(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = primary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Header row with a back arrow, shared by the More sub-screens. */
@Composable
fun BackHeader(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        trailing()
    }
}

private fun muscleLine(primary: Set<Muscle>, secondary: Set<Muscle>): String =
    buildString {
        append(primary.joinToString(", ") { it.label })
        if (secondary.isNotEmpty()) append(" · also " + secondary.joinToString(", ") { it.label.lowercase() })
    }

/** Search box on top, then every exercise as a card: yours first, then the built-in ones. */
@Composable
fun ExercisesScreen(viewModel: ExercisesViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    var naming by rememberSaveable { mutableStateOf(false) }
    if (naming) {
        CreateExerciseDialog(
            errorFor = viewModel::nameError,
            onCreate = { viewModel.create(it); naming = false },
            onDismiss = { naming = false },
        )
    }
    LazyColumn(modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            BackHeader("Exercises", onBack)
            OutlinedTextField(
                value = viewModel.query,
                onValueChange = viewModel::onQuery,
                placeholder = { Text("Search or add an exercise") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (viewModel.query.isNotEmpty()) {
                    { IconButton(onClick = { viewModel.onQuery(""); focus.clearFocus() }) { Icon(Icons.Filled.Close, contentDescription = "Clear") } }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            // Always-there way to add your own; typing a new name in the search also offers it below.
            if (viewModel.query.isBlank()) {
                OutlinedButton(
                    onClick = { naming = true },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Create exercise", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        if (viewModel.canCreate) {
            item(key = "create") {
                val name = viewModel.query.trim()
                LabCard(Modifier.clickable { focus.clearFocus(); viewModel.create(name) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Create “$name”", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            Text("A custom exercise. Pick its muscles next.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        val list = viewModel.filtered
        if (list.isEmpty() && !viewModel.canCreate) {
            item { Text("No exercises match.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(list, key = { (if (it.isCustom) "c:" else "b:") + it.name.lowercase() }) { e ->
            LabCard(Modifier.clickable { focus.clearFocus(); viewModel.open(e.name) }) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(e.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                            if (e.isCustom) {
                                Spacer(Modifier.width(8.dp))
                                Tag("Custom")
                            }
                        }
                        Text(
                            if (e.primary.isEmpty()) "Tap to set its muscles" else muscleLine(e.primary, e.secondary),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (e.primary.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CreateExerciseDialog(errorFor: (String) -> String?, onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val error = errorFor(name)
    val ok = name.isNotBlank() && error == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New exercise") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 40) name = it },
                label = { Text("Name") },
                placeholder = { Text("e.g. Cable Kickback") },
                singleLine = true,
                isError = error != null,
                supportingText = { Text(error ?: "You'll pick its muscles next") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (ok) onCreate(name) }),
            )
        },
        confirmButton = { TextButton(onClick = { onCreate(name) }, enabled = ok) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Tag(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/**
 * One exercise and the muscles it works, on the body figure. Built-in exercises are read-only; for your own,
 * tap a muscle to cycle it: main → assisting → off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailScreen(viewModel: ExercisesViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val e = viewModel.selectedEntry ?: return
    // Main muscles full color, assisting ones half.
    val map = Muscle.entries.associateWith { m ->
        when (m) {
            in e.primary -> MuscleState(1.0, 0)
            in e.secondary -> MuscleState(0.5, 0)
            else -> MuscleState(0.0, null)
        }
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        BackHeader(e.name, onBack)
        if (e.isCustom) {
            Text(
                "Custom exercise",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        LabCard {
            Column(Modifier.padding(16.dp)) {
                MuscleFigures(map)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendDot(1f, "Main")
                    LegendDot(0.5f, "Assisting")
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        if (!e.isCustom) {
            InfoRow("Main", e.primary.joinToString(", ") { it.label })
            if (e.secondary.isNotEmpty()) InfoRow("Assisting", e.secondary.joinToString(", ") { it.label })
        } else {
            Text("Muscles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Tap once for main, twice for assisting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Muscle.entries.forEach { m ->
                    val state = when (m) { in e.primary -> 2; in e.secondary -> 1; else -> 0 }
                    MuscleChip(m.label, state) { viewModel.cycleMuscle(m) }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LegendDot(alpha: Float, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 0 = off, 1 = assisting (half tint), 2 = main (full). */
@Composable
private fun MuscleChip(label: String, state: Int, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val bg = when (state) { 2 -> primary; 1 -> primary.copy(alpha = 0.25f); else -> Color.Transparent }
    val fg = when (state) { 2 -> MaterialTheme.colorScheme.onPrimary; 1 -> primary; else -> MaterialTheme.colorScheme.onSurface }
    Text(
        label + when (state) { 1 -> " · assist"; else -> "" },
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (state > 0) FontWeight.SemiBold else FontWeight.Normal,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, if (state > 0) primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = label + when (state) { 2 -> ", main"; 1 -> ", assisting"; else -> "" }
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
