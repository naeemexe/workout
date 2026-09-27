package work.lockedinlabs.tracker.domain

import work.lockedinlabs.tracker.data.ExerciseLog
import work.lockedinlabs.tracker.data.SetEntry
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * "50", "52.5", "52.25": up to 2 decimals, no trailing zeros, always with a "." (never the phone's decimal comma),
 * so it reads back exactly when it pre-fills an input.
 */
fun Double.weightText(): String = plainNumber(this)

/** Locale-independent number text with up to 2 decimals and no trailing zeros. */
fun plainNumber(value: Double): String =
    BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/** Typed weight with a decimal comma ("52,5") accepted as "52.5". */
fun normalizeDecimal(typed: String): String = typed.replace(',', '.')

/** 90 → "1:30". */
fun clock(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

fun Double.lb(): String = if (this <= 0.0) "bodyweight" else "${weightText()} lb"

fun ExerciseLog.setText(): String = sets.summary()

/** "50 lb · 3 × 10", "50 lb · 10, 10, 8", or "50×10, 55×8 lb" when weights differ. */
fun List<SetEntry>.summary(): String {
    if (isEmpty()) return ""
    val weight = map { it.weightLbs }.distinct().singleOrNull()
    val reps = map { it.reps }
    return when {
        weight == null -> joinToString(", ") { "${it.weightLbs.weightText()}×${it.reps}" } + " lb"
        reps.distinct().size == 1 -> "${weight.lb()} · $size × ${reps[0]}"
        else -> "${weight.lb()} · ${reps.joinToString(", ")}"
    }
}

private val thisYearFmt = DateTimeFormatter.ofPattern("EEE, MMM d")
private val otherYearFmt = DateTimeFormatter.ofPattern("MMM d, yyyy")

fun LocalDate.label(today: LocalDate = LocalDate.now()): String = when (this) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    today.plusDays(1) -> "Tomorrow"
    else -> format(if (year == today.year) thisYearFmt else otherYearFmt)
}
