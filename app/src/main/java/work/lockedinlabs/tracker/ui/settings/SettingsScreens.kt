package work.lockedinlabs.tracker.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import work.lockedinlabs.tracker.BuildConfig
import work.lockedinlabs.tracker.data.ThemeMode
import work.lockedinlabs.tracker.ui.theme.LabCard

/** Where "Contact support" and the website link go. */
private const val SUPPORT_EMAIL = "support@lockedinlabs.work"
private const val WEBSITE = "https://lockedinlabs.work"

@Composable
private fun SubScreen(title: String, onBack: () -> Unit, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        content()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SubScreen("Settings", onBack, modifier) {
        SectionLabel("Appearance")
        LabCard {
            Column(Modifier.selectableGroup().padding(vertical = 4.dp)) {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = mode == themeMode, onClick = { onThemeMode(mode) }, role = Role.RadioButton)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                            if (mode == ThemeMode.SYSTEM) {
                                Text(
                                    "Match your phone's light / dark setting",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        RadioButton(selected = mode == themeMode, onClick = null)
                    }
                }
            }
        }
    }
}

@Composable
fun SupportScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    SubScreen("Help & support", onBack, modifier) {
        SectionLabel("How it works")
        LabCard {
            Faq(
                "What is the Strength Index?",
                "It starts at 100. Each exercise is scored by your estimated one-rep max compared with the first time " +
                    "you logged it, and the index averages them. After 2 days without training it slowly drifts down, " +
                    "so the line tells you when to get back in.",
            )
            FaqDivider()
            Faq(
                "When does it tell me to step up?",
                "When your first sets hit the reps in the exercise's progression plan. " +
                    "Set this up in More → Progression plans.",
            )
            FaqDivider()
            Faq(
                "How does my session plan rotate?",
                "It moves to the next session only after you train. Miss a day and it waits for you. Planned rest days " +
                    "pass on their own. Choosing Rest or Custom on the Log tab doesn't move it.",
            )
            FaqDivider()
            Faq(
                "Where is my data?",
                "On this phone. Sign in with Google in Profile to back it up and restore it on a new phone.",
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Contact")
        LabCard {
            LinkRow("Email support", SUPPORT_EMAIL) {
                context.startActivity(
                    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL"))
                        .putExtra(Intent.EXTRA_SUBJECT, "Locked In ${BuildConfig.VERSION_NAME} support"),
                )
            }
            FaqDivider()
            LinkRow("Website", WEBSITE.removePrefix("https://")) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WEBSITE)))
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Locked In ${BuildConfig.VERSION_NAME} · Locked In Labs",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun Faq(question: String, answer: String) {
    var open by rememberSaveable(question) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { open = !open }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(question, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (open) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (open) {
            Spacer(Modifier.height(6.dp))
            Text(answer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LinkRow(title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun FaqDivider() = HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
