package work.lockedinlabs.tracker.ui.weight

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import work.lockedinlabs.tracker.LockedInApp
import work.lockedinlabs.tracker.data.WeightEntry
import work.lockedinlabs.tracker.data.WeightRepository
import work.lockedinlabs.tracker.domain.label
import work.lockedinlabs.tracker.domain.weightText
import work.lockedinlabs.tracker.domain.normalizeDecimal
import java.time.LocalDate

enum class WeightRange(val label: String, val days: Long?) {
    MONTH("1M", 30), QUARTER("3M", 90), HALF("6M", 182), YEAR("1Y", 365), ALL("All", null)
}

sealed interface WeightEvent {
    data class Saved(val entry: WeightEntry) : WeightEvent
    data class Deleted(val entry: WeightEntry) : WeightEvent
}

class WeightViewModel(private val weights: WeightRepository) : ViewModel() {
    /** Newest first. */
    var entries by mutableStateOf<List<WeightEntry>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set
    var range by mutableStateOf(WeightRange.QUARTER)
        private set

    /** Today's weight as typed; pre-filled when you've already weighed in today. */
    var input by mutableStateOf("")
        private set

    private val _events = Channel<WeightEvent>(Channel.BUFFERED)
    val events: Flow<WeightEvent> = _events.receiveAsFlow()

    val latest: WeightEntry? by derivedStateOf { entries.firstOrNull() }
    val todayEntry: WeightEntry? by derivedStateOf { entries.firstOrNull { it.date == LocalDate.now() } }

    /** Weigh-ins inside the selected range, oldest first (for the chart). */
    val window: List<WeightEntry> by derivedStateOf {
        val from = range.days?.let { LocalDate.now().minusDays(it).toEpochDay() } ?: Long.MIN_VALUE
        entries.filter { it.epochDay >= from }.reversed()
    }

    val chartStart: LocalDate by derivedStateOf {
        range.days?.let { LocalDate.now().minusDays(it) }
            ?: window.firstOrNull()?.date?.let { minOf(it, LocalDate.now().minusDays(7)) }
            ?: LocalDate.now().minusDays(7)
    }

    /** Change across the selected range (null until there are two weigh-ins in it). */
    val change: Double? by derivedStateOf {
        window.takeIf { it.size >= 2 }?.let { it.last().lbs - it.first().lbs }
    }

    /** Home tile caption: change since the previous weigh-in. */
    val tileNote: String? by derivedStateOf {
        val (last, prev) = entries.getOrNull(0) to entries.getOrNull(1)
        when {
            last == null -> null
            prev == null -> last.date.label()
            else -> (last.lbs - prev.lbs).let { d -> (if (d >= 0) "+" else "−") + kotlin.math.abs(d).weightText() + " lb since last" }
        }
    }

    val canSave by derivedStateOf {
        val w = input.toDoubleOrNull()
        w != null && w in 40.0..1000.0 && w != todayEntry?.lbs
    }

    init {
        viewModelScope.launch {
            weights.seedFromProfile()
            weights.observeAll().collect { list ->
                val hadToday = todayEntry
                entries = list
                // Keep the field in step with today's saved weigh-in unless you're mid-edit.
                if (!loaded || input.isEmpty() || input == hadToday?.lbs?.weightText()) {
                    input = todayEntry?.lbs?.weightText().orEmpty()
                }
                loaded = true
            }
        }
    }

    fun onInput(typed: String) {
        val v = normalizeDecimal(typed)
        if (v.isEmpty() || WEIGHT_INPUT.matches(v)) input = v
    }
    fun selectRange(r: WeightRange) { range = r }

    fun save() {
        if (!canSave) return
        val lbs = input.toDouble()
        viewModelScope.launch {
            weights.record(LocalDate.now(), lbs)
            _events.send(WeightEvent.Saved(WeightEntry(LocalDate.now().toEpochDay(), lbs)))
        }
    }

    fun delete(entry: WeightEntry) {
        viewModelScope.launch {
            weights.delete(entry)
            _events.send(WeightEvent.Deleted(entry))
        }
    }

    fun restore(entry: WeightEntry) { viewModelScope.launch { weights.restore(entry) } }

    companion object {
        private val WEIGHT_INPUT = Regex("""\d{1,3}(\.\d?)?""")

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LockedInApp
                WeightViewModel(app.weightRepository)
            }
        }
    }
}
