package work.lockedinlabs.tracker.ui.theme

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Dark background, also used for the launch window. */
val Ink = Color(0xFF0B0E0D)

/** Color tags for plan days (stored by index, so only append). Readable in both light and dark themes. */
val DayColors = listOf(
    Color(0xFF2EC4B6), Color(0xFF4DA3FF), Color(0xFFB37FEB), Color(0xFFFF7A45),
    Color(0xFFFFC53D), Color(0xFFF759AB), Color(0xFF95DE64), Color(0xFFFF5C5C),
)

fun dayColor(index: Int?): Color? = index?.let { DayColors[it.mod(DayColors.size)] }

private val DarkGain = Color(0xFF2EE59D)
private val DarkLoss = Color(0xFFFF5C5C)
// Deeper shades so green/red stay readable on white.
private val LightGain = Color(0xFF0E9F6E)
private val LightLoss = Color(0xFFD93636)

private data class TrendColors(val gain: Color, val loss: Color)

private val LocalTrendColors = staticCompositionLocalOf { TrendColors(DarkGain, DarkLoss) }

/** "Up" color (green) for the current theme. */
val Gain: Color
    @Composable @ReadOnlyComposable get() = LocalTrendColors.current.gain

/** "Down" color (red) for the current theme. */
val Loss: Color
    @Composable @ReadOnlyComposable get() = LocalTrendColors.current.loss

private val DarkScheme = darkColorScheme(
    primary = DarkGain,
    onPrimary = Color(0xFF00210F),
    primaryContainer = Color(0xFF0F3D2A),
    onPrimaryContainer = DarkGain,
    secondaryContainer = Color(0xFF1E2B27),
    onSecondaryContainer = Color(0xFFCFE9DD),
    background = Ink,
    onBackground = Color(0xFFE8EDEB),
    surface = Ink,
    onSurface = Color(0xFFE8EDEB),
    surfaceVariant = Color(0xFF1C2322),
    onSurfaceVariant = Color(0xFF9AA5A1),
    surfaceContainerLowest = Color(0xFF080B0A),
    surfaceContainerLow = Color(0xFF111615),
    surfaceContainer = Color(0xFF151B1A),
    surfaceContainerHigh = Color(0xFF1A2120),
    surfaceContainerHighest = Color(0xFF202827),
    outline = Color(0xFF3A4442),
    outlineVariant = Color(0xFF263030),
    error = DarkLoss,
    onError = Color(0xFF3B0000),
)

private val LightScheme = lightColorScheme(
    primary = LightGain,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF5E3),
    onPrimaryContainer = Color(0xFF00391F),
    secondaryContainer = Color(0xFFDDEBE5),
    onSecondaryContainer = Color(0xFF14302A),
    background = Color(0xFFF4F7F5),
    onBackground = Color(0xFF111614),
    surface = Color(0xFFF4F7F5),
    onSurface = Color(0xFF111614),
    surfaceVariant = Color(0xFFE3E9E6),
    onSurfaceVariant = Color(0xFF5A6661),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEEF3F0),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEAF0ED),
    surfaceContainerHighest = Color(0xFFE2E9E5),
    outline = Color(0xFFB5C0BB),
    outlineVariant = Color(0xFFDCE3E0),
    error = LightLoss,
    onError = Color.White,
)

@Composable
fun LockedInTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalTrendColors provides if (dark) TrendColors(DarkGain, DarkLoss) else TrendColors(LightGain, LightLoss),
    ) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
    }
}

@Composable
fun LabCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        content = content,
    )
}
