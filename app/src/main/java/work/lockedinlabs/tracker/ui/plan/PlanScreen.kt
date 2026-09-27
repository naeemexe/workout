package work.lockedinlabs.tracker.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import work.lockedinlabs.tracker.ui.theme.DayColors
import work.lockedinlabs.tracker.ui.theme.dayColor
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import work.lockedinlabs.tracker.domain.Rotation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import work.lockedinlabs.tracker.data.PlanDay
import work.lockedinlabs.tracker.domain.ExerciseCatalog
import work.lockedinlabs.tracker.ui.theme.LabCard
import work.lockedinlabs.tracker.ui.more.BackHeader

/** Plan tab: your plans (tap one to edit it), the active one marked, and "Create custom plan". */
@Composable
fun PlansScreen(viewModel: PlanViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        BackHeader("Session plans", onBack)
        Text(
            "Your active plan automatically sets each day's session and exercises, so logging a workout is faster.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(16.dp))
        if (!viewModel.plansLoaded) return@Column
        viewModel.allPlans.forEach { (plan, days) ->
            val cycle = Rotation.cycle(days, plan.weekly)
            LabCard(Modifier.clickable { viewModel.openPlan(plan.id) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                plan.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (plan.isActive) {
                                Spacer(Modifier.width(8.dp))
                                ActivePill()
                            }
                        }
                        if (cycle.isEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text("Tap to set it up", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        OutlinedButton(
            onClick = viewModel::createPlan,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Create session plan", style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ActivePill() {
    Text(
        "Active",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Editor for one plan: its name, whether it's the active plan, and its 7 days. */
@Composable
fun PlanScreen(viewModel: PlanViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val plan = viewModel.editingPlan
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var showWeeklyInfo by rememberSaveable { mutableStateOf(false) }
    if (showWeeklyInfo) {
        AlertDialog(
            onDismissRequest = { showWeeklyInfo = false },
            title = { Text("Week-based rotation") },
            text = {
                Text(
                    "On: your session plan follows the week and starts over at the beginning of each week.\n\n" +
                        "Off: your sessions repeat in order, whatever the day of the week.",
                )
            },
            confirmButton = { TextButton(onClick = { showWeeklyInfo = false }) { Text("Got it") } },
        )
    }
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
            Text("Session plan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete plan", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!viewModel.loaded || plan == null) return@Column

        OutlinedTextField(
            value = viewModel.planName,
            onValueChange = viewModel::onPlanName,
            label = { Text("Plan name") },
            placeholder = { Text("e.g. Push Pull Legs") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        LabCard {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Active plan", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (plan.isActive) "Used on the Log tab" else "Switch to start this plan from its first day",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Only turning a plan on here; switching to another plan turns this one off.
                Switch(checked = plan.isActive, onCheckedChange = { if (it) viewModel.activate(plan.id) })
            }
        }

        Spacer(Modifier.height(10.dp))
        LabCard {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Week-based rotation", style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { showWeeklyInfo = true }) {
                    Icon(Icons.Outlined.Info, contentDescription = "What is week-based rotation?", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.weight(1f))
                Switch(checked = plan.weekly, onCheckedChange = viewModel::setWeekly)
            }
        }
        Spacer(Modifier.height(16.dp))
        viewModel.days.forEachIndexed { i, day ->
            DayCard(
                day = day,
                nameError = viewModel.nameError(i),
                expanded = viewModel.expanded == i,
                knownExercises = viewModel.knownExercises,
                onToggle = { viewModel.toggleExpanded(i) },
                onRename = { viewModel.rename(i, it) },
                onRest = { viewModel.setRest(i, it) },
                onAdd = { viewModel.addExercise(i, it) },
                onRemove = { j -> viewModel.removeExercise(i, j) },
                onMove = { from, to -> viewModel.moveExercise(i, from, to) },
                onSets = { j, n -> viewModel.setSets(i, j, n) },
                onColor = { viewModel.setColor(i, it) },
                weekly = plan.weekly,
            )
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmDelete && plan != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${plan.displayName}?") },
            text = {
                Text(
                    if (plan.isActive) "This is your active plan. The Log tab will have no plan days until you pick another plan. Your logged workouts stay."
                    else "Your logged workouts stay.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.deletePlan(plan.id); onBack() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DayCard(
    day: PlanDay,
    nameError: String?,
    expanded: Boolean,
    knownExercises: List<String>,
    onToggle: () -> Unit,
    onRename: (String) -> Unit,
    onRest: (Boolean) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onSets: (exercise: Int, sets: Int) -> Unit,
    onColor: (Int) -> Unit,
    weekly: Boolean,
) {
    // The day's color tag (falls back to the theme color until it has one).
    val primary = dayColor(day.color)?.takeUnless { day.isRest } ?: MaterialTheme.colorScheme.primary
    LabCard {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (day.isActive) primary.copy(alpha = 0.15f) else Color.Transparent)
                    .border(1.dp, if (day.isActive) primary else MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${day.dayIndex + 1}",
                    fontWeight = FontWeight.Bold,
                    color = if (day.isActive) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (day.isActive) day.displayName else "Day ${day.dayIndex + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (day.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    when {
                        day.isRest -> "Rest day"
                        day.exercises.isNotEmpty() -> "${day.exercises.size} exercise" + if (day.exercises.size == 1) "" else "s"
                        day.isActive -> "No exercises yet"
                        day.exercises.isNotEmpty() -> "Needs a name to join the rotation"
                        weekly -> "Rest day · blank days rest in a week-based rotation"
                        else -> "Tap to set up · skipped until named"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (expanded) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = day.name,
                    onValueChange = onRename,
                    label = { Text("Name") },
                    placeholder = { Text(if (day.isRest) "Rest" else "e.g. Upper Body") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Rest day", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = day.isRest, onCheckedChange = onRest)
                }
                if (!day.isRest) {
                    ColorPicker(day.color, onColor)
                    Spacer(Modifier.height(8.dp))
                    ExerciseList(day.exercises, day.sets, onMove, onRemove, onSets)
                    AddExerciseField(day, knownExercises, onAdd)
                }
            }
        }
    }
}

/** Color tag for a day: tints its number here and its dates on the Log tab's calendar. */
@Composable
private fun ColorPicker(selected: Int?, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Color", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayColors.forEachIndexed { i, c ->
                val isSelected = i == selected
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(c)
                        .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(i) }),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** "3 sets" beside an exercise; tap to pick how many sets you plan for it. */
@Composable
private fun SetCountPicker(count: Int, enabled: Boolean, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, enabled = enabled, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text("$count ${if (count == 1) "set" else "sets"}", style = MaterialTheme.typography.labelLarge)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (1..8).forEach { n ->
                DropdownMenuItem(
                    text = { Text("$n ${if (n == 1) "set" else "sets"}", fontWeight = if (n == count) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { onPick(n); open = false },
                    trailingIcon = if (n == count) {
                        { Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                )
            }
        }
    }
}

private val ROW_HEIGHT = 52.dp

/** The day's exercises in order. Press and hold a row, then drag it up or down to reorder. */
@Composable
private fun ExerciseList(
    exercises: List<String>,
    sets: List<Int>,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (Int) -> Unit,
    onSets: (exercise: Int, sets: Int) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val rowPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val last = exercises.lastIndex
    /** Where the dragged row would land if dropped now. */
    fun target(): Int? = dragging?.let { (it + (offset / rowPx).roundToInt()).coerceIn(0, last) }

    Column {
        exercises.forEachIndexed { j, name ->
            val isDragged = j == dragging
            // Rows between the dragged row's start and target slide over to make room.
            val shift = run {
                val d = dragging ?: return@run 0f
                val t = target() ?: return@run 0f
                when {
                    j == d -> 0f
                    d < t && j in (d + 1)..t -> -rowPx
                    d > t && j in t until d -> rowPx
                    else -> 0f
                }
            }
            // Slide while dragging; snap on drop, when the list itself is reordered.
            val animatedShift by animateFloatAsState(shift, if (dragging == null) snap() else spring(), label = "reorder")
            val position = if (isDragged) target()!! else j + (shift / rowPx).roundToInt()
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(ROW_HEIGHT)
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragged) offset else animatedShift }
                    .shadow(if (isDragged) 6.dp else 0.dp, RoundedCornerShape(12.dp))
                    .background(
                        if (isDragged) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                        RoundedCornerShape(12.dp),
                    )
                    .pointerInput(j, exercises.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragging = j
                                offset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                offset += amount.y
                            },
                            onDragEnd = {
                                target()?.let { onMove(j, it) }
                                dragging = null
                                offset = 0f
                            },
                            onDragCancel = {
                                dragging = null
                                offset = 0f
                            },
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${position + 1}.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp),
                )
                // Name (+ "custom" tag) takes the free space, so the icons line up on the right.
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    // Long names end in "…" so the "custom" tag stays right after the name.
                    Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (ExerciseCatalog.find(name) == null) {
                        // Not a built-in exercise: fine to plan, but it won't count on the muscle map.
                        Text(
                            "custom",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                SetCountPicker(sets.getOrElse(j) { PlanDay.DEFAULT_SETS }, enabled = dragging == null) { onSets(j, it) }
                IconButton(onClick = { onRemove(j) }, enabled = dragging == null) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Remove $name", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Drag handle: a visual cue that the row can be held and moved.
                Icon(
                    Icons.Filled.Menu,
                    contentDescription = "Hold and drag to reorder $name",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (exercises.size > 1) 0.7f else 0f),
                    modifier = Modifier.padding(end = 4.dp).size(20.dp),
                )
            }
        }
        if (exercises.size > 1) {
            Text(
                "Hold an exercise and drag to reorder",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun AddExerciseField(day: PlanDay, knownExercises: List<String>, onAdd: (String) -> Unit) {
    var text by rememberSaveable(day.dayIndex) { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val submit = {
        if (text.isNotBlank()) {
            onAdd(text)
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
                // Cancel: clear what's typed and put the keyboard away.
                if (text.isNotEmpty()) {
                    IconButton(onClick = { text = ""; focus.clearFocus() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = submit, enabled = text.isNotBlank()) { Icon(Icons.Filled.Add, contentDescription = "Add exercise") }
            }
        },
        // "Done" adds and keeps the keyboard up so you can enter the whole day in one go.
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    // Your exercises, then built-in catalog ones, only once you start typing.
    val picks = if (text.isBlank()) emptyList() else {
        val q = text.trim()
        val mine = knownExercises.filter { it.contains(q, ignoreCase = true) }
        (mine + ExerciseCatalog.search(q).map { it.name })
            .distinctBy { ExerciseCatalog.key(it) }
            .filter { k -> day.exercises.none { ExerciseCatalog.key(it) == ExerciseCatalog.key(k) } }
            .take(10)
    }
    // A name that isn't anywhere yet: offer to create it (it's added to your exercises too).
    val q = text.trim()
    val isNew = q.isNotEmpty() && ExerciseCatalog.find(q) == null &&
        (knownExercises + day.exercises).none { ExerciseCatalog.key(it) == ExerciseCatalog.key(q) }
    if (picks.isNotEmpty() || isNew) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 4.dp)) {
            items(picks) { name -> SuggestionChip(onClick = { onAdd(name); text = "" }, label = { Text(name) }) }
            if (isNew) {
                item(key = "create") {
                    SuggestionChip(
                        onClick = { onAdd(q); text = "" },
                        label = { Text("Create “$q”") },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        colors = SuggestionChipDefaults.suggestionChipColors(labelColor = MaterialTheme.colorScheme.primary, iconContentColor = MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}
