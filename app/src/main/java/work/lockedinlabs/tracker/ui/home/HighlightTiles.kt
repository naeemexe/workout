package work.lockedinlabs.tracker.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import work.lockedinlabs.tracker.R
import work.lockedinlabs.tracker.domain.weightText
import work.lockedinlabs.tracker.ui.theme.LabCard

private val Flame = Color(0xFFFF7A45)

/** Weight · Streak, as two equal tiles. */
@Composable
fun HighlightTiles(state: HomeUiState, bodyweightLbs: Double?, weightNote: String?, onOpenWeight: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        WeightTile(bodyweightLbs, weightNote, onOpenWeight, Modifier.weight(1f).fillMaxHeight())
        StreakTile(state.streakDays, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun Tile(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    LabCard(modifier) { Column(Modifier.padding(12.dp), content = content) }
}

@Composable
private fun TileLabel(text: String) =
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)

@Composable
private fun StreakTile(days: Int, modifier: Modifier) {
    Tile(modifier) {
        TileLabel("Streak")
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.avatar_flame),
                contentDescription = null,
                tint = if (days > 0) Flame else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text("$days", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Text(
            when (days) { 0 -> "Train to start one"; 1 -> "day"; else -> "days" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Latest weigh-in; tap for the weight page. */
@Composable
private fun WeightTile(bodyweightLbs: Double?, note: String?, onOpenWeight: () -> Unit, modifier: Modifier) {
    Tile(modifier.clickable(onClick = onOpenWeight)) {
        TileLabel("Weight")
        Spacer(Modifier.height(6.dp))
        if (bodyweightLbs == null) {
            Hint("Tap to log your weight")
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(bodyweightLbs.weightText(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(3.dp))
                Text("lb", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            }
            Hint(note ?: "Tap to track")
        }
    }
}

@Composable
private fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
