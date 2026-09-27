package work.lockedinlabs.tracker.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import work.lockedinlabs.tracker.data.ProgressionRule
import work.lockedinlabs.tracker.domain.ExerciseCatalog
import work.lockedinlabs.tracker.domain.Rule
import work.lockedinlabs.tracker.domain.weightText
import work.lockedinlabs.tracker.ui.theme.LabCard
import work.lockedinlabs.tracker.ui.more.BackHeader

private val INCREMENTS = listOf(Rule.AUTO_INCREMENT, 2.5, 5.0, 10.0)
private val DROPS = listOf(5, 10, 15, 20)

private fun incrementText(lbs: Double) = if (lbs <= 0) "Auto" else "+${lbs.weightText()} lb"

/** "2 × 10 · +5 lb" or "10, 10, 8 · Auto". */
private fun ProgressionRule.summary(): String {
    val t = targets
    val sets = when {
        t.size == 1 -> "1 × ${t[0]}"
        t.distinct().size == 1 -> "${t.size} × ${t[0]}"
        else -> t.joinToString(", ")
    }
    return "$sets · ${incrementText(increment)}"
}

/** More → Progression plans: your rules; the default one covers every exercise not in another. */
@Composable
fun RulesScreen(viewModel: RulesViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        BackHeader("Progression plans", onBack)
        Text(
            "When an exercise is ready to step up or should step down.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        RulesList(viewModel)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RulesList(viewModel: RulesViewModel) {
    viewModel.rules.forEach { rule ->
        LabCard(Modifier.clickable { viewModel.open(rule.id) }) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(rule.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (rule.isDefault) {
                            Spacer(Modifier.width(8.dp))
                            Pill("Default")
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        rule.summary() + " · " + when {
                            rule.isDefault -> "all other exercises"
                            rule.exercises.isEmpty() -> "no exercises yet"
                            rule.exercises.size == 1 -> rule.exercises[0]
                            else -> "${rule.exercises.size} exercises"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    OutlinedButton(onClick = viewModel::create, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Create progression plan", style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun Pill(text: String) {
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

/** Editor for one rule: step-up table, weight jump, step-down, and which exercises follow it. */
@Composable
fun RuleScreen(viewModel: RulesViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val rule = viewModel.draft ?: return
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
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
            Text("Progression plan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (!rule.isDefault) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete progression plan", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        OutlinedTextField(
            value = rule.name,
            onValueChange = viewModel::onName,
            label = { Text("Name") },
            placeholder = { Text("e.g. Heavy compounds") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        LabCard {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Default plan", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (rule.isDefault) "Used for every exercise not in another plan" else "Use this plan for all other exercises",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Only turned on here; making another rule default turns this one off.
                Switch(checked = rule.isDefault, onCheckedChange = { if (it) viewModel.makeDefault() })
            }
        }

        // ---- Step up ----
        Spacer(Modifier.height(24.dp))
        SectionTitle("Step up")
        LabCard {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text("Set", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(56.dp))
                    Text("Reps", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                viewModel.repsInputs.forEachIndexed { i, reps ->
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp))
                        NumberField(reps, { viewModel.onSetReps(i, it) }, Modifier.width(96.dp))
                        Spacer(Modifier.weight(1f))
                        if (viewModel.repsInputs.size > 1) {
                            IconButton(onClick = { viewModel.removeSet(i) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Remove set ${i + 1}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (viewModel.repsInputs.size < RulesViewModel.MAX_SETS) {
                    TextButton(onClick = viewModel::addSet, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add set")
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                // Label and choices on one line; chips drop the "lb" to fit (weights are in lb everywhere).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Then add", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        INCREMENTS.forEach { lbs ->
                            FilterChip(
                                selected = rule.increment == lbs,
                                onClick = { viewModel.onIncrement(lbs) },
                                label = { Text(if (lbs <= 0) "Auto" else "+${lbs.weightText()}") },
                            )
                        }
                    }
                }
                if (rule.increment <= 0) {
                    Text(
                        "Auto: +2.5 lb under 30 lb, otherwise +5 lb",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("and restart at", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    NumberField(viewModel.startInput, viewModel::onStartReps, Modifier.width(96.dp), suffix = "reps")
                }
            }
        }

        // ---- Step down ----
        Spacer(Modifier.height(24.dp))
        SectionTitle("Step down", "Two sessions in a row with a set under the minimum.")
        LabCard {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Minimum", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    NumberField(viewModel.minInput, viewModel::onMinReps, Modifier.width(96.dp), suffix = "reps")
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Drop by", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DROPS.forEach { pct ->
                            FilterChip(selected = rule.dropPct == pct, onClick = { viewModel.onDropPct(pct) }, label = { Text("$pct%") })
                        }
                    }
                }
            }
        }

        // ---- Exercises ----
        Spacer(Modifier.height(24.dp))
        if (rule.isDefault) {
            SectionTitle("Exercises", "Every exercise that isn't in another plan follows this one.")
        } else {
            SectionTitle("Exercises", "These follow this plan. Everything else uses the default.")
            ExercisePicker(viewModel, rule)
        }
        Spacer(Modifier.height(32.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${rule.displayName}?") },
            text = { Text("Its exercises go back to the default plan.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionTitle(title: String, caption: String? = null) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, modifier: Modifier, suffix: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
        suffix = suffix?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        modifier = modifier,
    )
}

/** Assigned exercises as removable chips, plus a field that suggests your exercises and catalog ones. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExercisePicker(viewModel: RulesViewModel, rule: ProgressionRule) {
    var text by rememberSaveable(rule.id) { mutableStateOf("") }
    val focus = LocalFocusManager.current
    if (rule.exercises.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rule.exercises.forEach { name ->
                InputChip(
                    selected = false,
                    onClick = { viewModel.removeExercise(name) },
                    label = { Text(name) },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove $name", modifier = Modifier.size(16.dp)) },
                )
            }
        }
    }
    val submit = {
        if (text.isNotBlank()) {
            viewModel.addExercise(text)
            text = ""
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { if (it.length <= 40) text = it },
        placeholder = { Text("Add exercise") },
        singleLine = true,
        trailingIcon = {
            Row {
                if (text.isNotEmpty()) {
                    IconButton(onClick = { text = ""; focus.clearFocus() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = submit, enabled = text.isNotBlank()) { Icon(Icons.Filled.Add, contentDescription = "Add exercise") }
            }
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    // Your exercises first, then catalog ones — only while typing.
    val picks = if (text.isBlank()) emptyList() else {
        val q = text.trim()
        (viewModel.knownExercises.filter { it.contains(q, ignoreCase = true) } + ExerciseCatalog.search(q).map { it.name })
            .distinctBy { ExerciseCatalog.key(it) }
            .filter { k -> rule.exercises.none { ExerciseCatalog.key(it) == ExerciseCatalog.key(k) } }
            .take(10)
    }
    if (picks.isNotEmpty()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 4.dp)) {
            items(picks) { name ->
                SuggestionChip(onClick = { viewModel.addExercise(name); text = "" }, label = { Text(name) })
            }
        }
    }
    // Adding moves an exercise out of any other rule; say so.
    val moved = picks.firstOrNull()?.let { viewModel.ruleOf(it) }
    if (moved != null) {
        Text(
            "${picks.first()} is in ${moved.displayName}. Adding it here moves it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
