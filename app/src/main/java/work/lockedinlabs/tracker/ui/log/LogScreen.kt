package work.lockedinlabs.tracker.ui.log

import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import work.lockedinlabs.tracker.data.SettingsStore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import work.lockedinlabs.tracker.domain.clock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusManager
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.unit.dp
import work.lockedinlabs.tracker.data.PlanDay
import work.lockedinlabs.tracker.domain.label
import work.lockedinlabs.tracker.domain.setText
import work.lockedinlabs.tracker.ui.theme.LabCard
import work.lockedinlabs.tracker.ui.theme.Gain
import work.lockedinlabs.tracker.domain.headline
import androidx.compose.material.icons.filled.KeyboardArrowUp

private val LABEL_WIDTH = 88.dp
/** Session and Exercise chips share one width, so the selected ones line up; long names end in "…". */
private val CHIP_WIDTH = 132.dp

/** Space before the selected chip: a sliver of the chip before it shows there, so you can tell the row scrolls left. */
private val PEEK = 20.dp

/**
 * Session and Exercise rows: the selected chip always sits at the same spot, [PEEK] from the row's start, so the two
 * line up whichever one is picked. The end padding lets the last chips scroll there too.
 */
@Composable
private fun ChipRow(state: LazyListState, selected: Int, modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    var viewport by remember { mutableIntStateOf(0) }
    val endPadding = with(LocalDensity.current) { (viewport.toDp() - CHIP_WIDTH - PEEK).coerceAtLeast(0.dp) }
    // Coming back to the page jumps straight into place; only a new pick scrolls there.
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(selected, viewport > 0) {
        if (selected < 0 || viewport == 0) return@LaunchedEffect
        if (placed) state.animateScrollToItem(selected) else state.scrollToItem(selected)
        placed = true
    }
    LazyRow(
        modifier.onSizeChanged { viewport = it.width }.fadingEdges(state, start = PEEK),
        state = state,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(start = PEEK, end = endPadding, top = 4.dp, bottom = 4.dp),
        content = content,
    )
}

@Composable
fun LogScreen(viewModel: LogViewModel, snackbar: SnackbarHostState, onOpenPlans: () -> Unit, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    var pickingDate by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                is LogEvent.Saved -> snackbar.showSnackbar(
                    if (e.isPr) "New PR on ${e.log.exercise}! ${e.log.setText()}"
                    else "Logged ${e.log.exercise} · ${e.log.setText()}",
                )
                is LogEvent.SetSaved -> snackbar.showSnackbar("Set ${e.set} saved", duration = SnackbarDuration.Short)
                is LogEvent.Deleted -> {
                    val r = snackbar.showSnackbar("Deleted ${e.log.exercise}", "Undo", duration = SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) viewModel.restore(e.log)
                }
            }
        }
    }

    val rest = viewModel.rest
    // Keep the screen awake while resting so the countdown stays visible.
    val view = LocalView.current
    DisposableEffect(rest.running) {
        view.keepScreenOn = rest.running
        onDispose { view.keepScreenOn = false }
    }

    Box(modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Log workout", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            AssistChip(
                onClick = { pickingDate = true },
                label = { Text(viewModel.date.label()) },
                leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = "Change date", modifier = Modifier.size(18.dp)) },
            )
        }
        Spacer(Modifier.height(12.dp))
        PlanDayPicker(viewModel, onOpenPlans)
        Spacer(Modifier.height(16.dp))

        // Rest day: nothing to log, just confirm it. Otherwise: exercise → sets → weight/reps → Save / Rest.
        if (viewModel.planDay?.isRest == true) RestDayCard(viewModel.restLogged, viewModel::saveRestDay)
        else ExerciseLogger(viewModel, focus)

        Spacer(Modifier.height(24.dp))
        DayEntries(viewModel)
        Spacer(Modifier.height(if (rest.running) 120.dp else 24.dp))
    }
    if (rest.running) {
        RestPanel(
            rest = rest,
            note = viewModel.restNote,
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
        )
    }
    }

    if (pickingDate) {
        CalendarDialog(
            current = viewModel.date,
            tagColor = tagColors(viewModel.loggedDayTags),
            onPicked = { viewModel.onDateChange(it); pickingDate = false },
            onDismiss = { pickingDate = false },
        )
    }
}

/** The logging steps for a workout day: exercise, sets, weight and reps, then Save and the rest timer. */
@Composable
private fun ColumnScope.ExerciseLogger(viewModel: LogViewModel, focus: FocusManager) {
    if (viewModel.planDay?.isCustom == true) {
        val error = viewModel.customNameError(viewModel.customName)
        OutlinedTextField(
            value = viewModel.customName,
            onValueChange = viewModel::onCustomName,
            label = { Text("Session name") },
            placeholder = { Text("e.g. Arms") },
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
    }
        // Kept outside the search/row switch so the row doesn't jump back to the start after "+ Other".
        val chipsState = rememberLazyListState(initialFirstVisibleItemIndex = viewModel.dayChips.indexOfFirst { it.selected }.coerceAtLeast(0))
        if (viewModel.searching) {
            // "+ Other": search your history and the catalog, or type a custom name.
            val searchFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { searchFocus.requestFocus() }
            OutlinedTextField(
                value = viewModel.exercise,
                onValueChange = viewModel::onExerciseChange,
                placeholder = { Text("Search exercises") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = viewModel::closeSearch) { Icon(Icons.Filled.Close, contentDescription = "Close search") }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    viewModel.chipOptions.firstOrNull()?.let { viewModel.pickFromSearch(it) }
                    focus.clearFocus()
                }),
                modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
            )
            val resultsState = rememberLazyListState()
            LazyRow(Modifier.fadingEdges(resultsState), state = resultsState, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(viewModel.chipOptions, key = { (if (it.custom) "use:" else "") + it.name }) { chip ->
                    ExerciseChipView(chip, onClick = { viewModel.pickFromSearch(chip); focus.clearFocus() })
                }
            }
        } else {
            // "Exercise" label, then the day's exercises; anything else lives behind "+ Other".
            Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Exercise", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(LABEL_WIDTH))
            // The selected exercise lines up under the selected session (also one just added at the end).
            ChipRow(chipsState, viewModel.dayChips.indexOfFirst { it.selected }, Modifier.weight(1f)) {
                items(viewModel.dayChips, key = { it.name }) { chip ->
                    ExerciseChipView(chip, fixedWidth = true, onClick = { viewModel.selectExercise(chip.name) })
                }
                item(key = "+other") {
                    AssistChip(
                        onClick = viewModel::openSearch,
                        label = { Text(if (viewModel.dayChips.isEmpty()) "Add exercise" else "Other") },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
            }
            viewModel.stepUp?.let { s ->
                Row(Modifier.padding(start = LABEL_WIDTH, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = Gain, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(s.headline(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Gain)
                }
            }
            if (viewModel.isCustomExercise) {
                Text(
                    "Custom exercise · set its muscles in More → Exercises",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = LABEL_WIDTH, top = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SetSelector(viewModel.sets.size, viewModel.selectedSet, viewModel.savedThrough, viewModel::selectSet, viewModel::addSet)
        Spacer(Modifier.height(16.dp))
        val current = viewModel.current
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ValueField("Weight", current.weight, viewModel::onWeightChange, KeyboardType.Decimal, suffix = "lb", modifier = Modifier.weight(1f))
            ValueField(
                "Reps", current.reps, viewModel::onRepsChange, KeyboardType.Number,
                // Hint with the previous set's reps so repeating them is one glance away.
                placeholder = viewModel.sets.getOrNull(viewModel.selectedSet - 1)?.reps,
                imeAction = ImeAction.Done, onDone = { focus.clearFocus() },
                modifier = Modifier.weight(1f),
            )
        }
        if (viewModel.sets.size > 1 || !current.isBlank) {
            TextButton(onClick = viewModel::removeSelectedSet, modifier = Modifier.align(Alignment.End)) {
                Text("Remove set ${viewModel.selectedSet + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
        } else {
            Spacer(Modifier.height(20.dp))
        }

        Button(
            onClick = { focus.clearFocus(); viewModel.save() },
            enabled = viewModel.canSave,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text(viewModel.saveLabel, style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Done with a set? Start resting; at zero the next set is selected.
            FilledTonalButton(
                onClick = { focus.clearFocus(); viewModel.startRest() },
                enabled = !viewModel.rest.running,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Rest ${clock(viewModel.defaultRestSeconds)}", style = MaterialTheme.typography.titleSmall)
            }
            RestTimeMenu(viewModel.defaultRestSeconds, viewModel::setDefaultRest)
        }
}

/** Shown instead of the logger on a rest day. Today's rest is saved automatically; earlier dates need a tap. */
@Composable
private fun RestDayCard(logged: Boolean, onSave: () -> Unit) {
    LabCard {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Rest day", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onSave,
        enabled = !logged,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        if (logged) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(if (logged) "Rest day logged" else "Save rest day", style = MaterialTheme.typography.titleMedium)
    }
}

/** ⋮ next to Rest: pick the default rest time. */
@Composable
private fun RestTimeMenu(selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Default rest time")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                "Default rest",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SettingsStore.REST_CHOICES.forEach { sec ->
                DropdownMenuItem(
                    text = { Text(clock(sec), fontWeight = if (sec == selected) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { onSelect(sec); open = false },
                    trailingIcon = if (sec == selected) {
                        { Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                )
            }
        }
    }
}

/** Countdown pinned to the bottom of the Log page while resting. */
@Composable
private fun RestPanel(rest: RestTimer, note: String, modifier: Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (note.isEmpty()) "Resting" else "Resting · $note", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(clock(rest.remainingSeconds), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = { rest.add(30) }) { Text("+30s") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = rest::skip) { Text("Skip") }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { rest.fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
            )
        }
    }
}

/**
 * Today's session: the rotation's pick is pre-selected; tap another workout, Rest or Custom to change it.
 * With no plan yet, the row offers "+ Add plan" (to the Plan tab) instead.
 */
@Composable
private fun PlanDayPicker(viewModel: LogViewModel, onOpenPlans: () -> Unit) {
    val selected = viewModel.planDay
    val custom = selected?.takeIf { it.dayIndex == PlanDay.CUSTOM_CHOICE }
    // Row order: workout days, then Rest, then Custom; the selected one is kept in place by ChipRow.
    val selectedIndex = when {
        selected == null -> 0
        selected.isCustom -> viewModel.workoutDays.size + 1
        selected.isRest -> viewModel.workoutDays.size
        else -> viewModel.workoutDays.indexOfFirst { it.dayIndex == selected.dayIndex }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    // "Session" label, matching "Exercise" and "Sets" below it.
    Row(verticalAlignment = Alignment.CenterVertically) {
    Text("Session", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(LABEL_WIDTH))
    if (viewModel.cycle.isEmpty()) {
        AssistChip(
            onClick = onOpenPlans,
            label = { Text("Add plan") },
            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.padding(start = PEEK),
        )
        return@Row
    }
    ChipRow(listState, selectedIndex, Modifier.weight(1f)) {
        items(viewModel.workoutDays, key = { it.dayIndex }) { day ->
            DayChip(day.displayName, day.dayIndex == selected?.dayIndex) { viewModel.choosePlanDay(day) }
        }
        item(key = "rest") { DayChip("Rest", selected?.isRest == true) { viewModel.choosePlanDay(PlanDay.RestChoice) } }
        item(key = "custom") {
            DayChip("Custom", custom != null) { viewModel.chooseCustom() }
        }
    }
    }
}

/** Selected look shared by Session chips, Exercise chips and set bubbles: light green fill with a green outline. */
@Composable
private fun selectedFill(selected: Boolean) = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent

@Composable
private fun selectedBorder(selected: Boolean, faded: Boolean = false) = BorderStroke(
    if (selected) 1.5.dp else 1.dp,
    when {
        selected -> MaterialTheme.colorScheme.primary
        faded -> MaterialTheme.colorScheme.outlineVariant
        else -> MaterialTheme.colorScheme.outline
    },
)

@Composable
private fun DayChip(label: String, selected: Boolean, onClick: () -> Unit) {
    SuggestionChip(
        onClick = onClick,
        label = { ChipLabel(label, fixedWidth = true) },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = selectedFill(selected),
            labelColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        ),
        border = selectedBorder(selected),
        modifier = Modifier.width(CHIP_WIDTH).semantics { if (selected) stateDescription = "Selected" },
    )
}

/** Chip text: one line; at a fixed width it's centered and long names end in "…". */
@Composable
private fun ChipLabel(text: String, fixedWidth: Boolean) {
    Text(
        text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = if (fixedWidth) TextAlign.Center else null,
        modifier = if (fixedWidth) Modifier.fillMaxWidth() else Modifier,
    )
}

@Composable
private fun ExerciseChipView(chip: ExerciseChip, onClick: () -> Unit, fixedWidth: Boolean = false) {
    SuggestionChip(
        onClick = onClick,
        label = { ChipLabel(if (chip.custom) "Create “${chip.name}”" else chip.name, fixedWidth) },
        icon = if (chip.done) {
            { Icon(Icons.Filled.Check, contentDescription = "Done", modifier = Modifier.size(18.dp)) }
        } else null,
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = selectedFill(chip.selected),
            labelColor = when {
                chip.selected -> MaterialTheme.colorScheme.onPrimaryContainer
                chip.done -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
            iconContentColor = MaterialTheme.colorScheme.primary,
        ),
        border = selectedBorder(chip.selected),
        modifier = if (fixedWidth) Modifier.width(CHIP_WIDTH) else Modifier,
    )
}

/**
 * "Sets  1 2 3 +": one bubble per set (as many as planned), + for another. Saved sets are bold; the next one is open;
 * later ones are faded and wait until the one before is saved.
 */
@Composable
private fun SetSelector(count: Int, selected: Int, savedThrough: Int, onSelect: (Int) -> Unit, onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Sets", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(LABEL_WIDTH))
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(count) { i ->
                val saved = i <= savedThrough
                val open = i <= savedThrough + 1
                val isSelected = i == selected
                val border = selectedBorder(isSelected, faded = !open)
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(selectedFill(isSelected))
                        .border(border.width, border.brush, CircleShape)
                        .clickable(enabled = open) { onSelect(i) }
                        .semantics {
                            contentDescription = "Set ${i + 1}" + when {
                                isSelected -> ", selected"
                                saved -> ", saved"
                                open -> ", next"
                                else -> ", locked"
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${i + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSelected || saved) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                            open -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        },
                    )
                }
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add set", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ValueField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    suffix: String? = null,
    placeholder: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
            placeholder = placeholder?.takeIf { it.isNotEmpty() }?.let {
                { Text(it, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            },
            suffix = suffix?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            label = { Text(label) },
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = modifier,
        )
}

@Composable
private fun DayEntries(viewModel: LogViewModel) {
    val entries = viewModel.entriesForDate
    val day = viewModel.date.label()
    Text(
        if (day == "Today" || day == "Yesterday") "Logged ${day.lowercase()}" else "Logged on $day",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    if (entries.isEmpty()) {
        Text(
            if (viewModel.restLogged) "Rest day. Recovery counts too." else "Nothing yet. Your first set is waiting.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LabCard {
        entries.forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.exercise, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(e.setText(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { viewModel.delete(e) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete ${e.exercise}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Softly fades a sideways-scrolling row's edges where there's more to scroll, instead of a hard cut. */
private fun Modifier.fadingEdges(state: LazyListState, width: Dp = 36.dp, start: Dp = width): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = width.toPx().coerceAtMost(size.width / 3)
        if (state.canScrollBackward) {
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = start.toPx().coerceAtMost(size.width / 3)),
                blendMode = BlendMode.DstIn,
            )
        }
        if (state.canScrollForward) {
            drawRect(
                Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - fade, endX = size.width),
                blendMode = BlendMode.DstIn,
            )
        }
    }
