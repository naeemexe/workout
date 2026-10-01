package work.lockedinlabs.tracker.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.lockedinlabs.tracker.pack.Level
import work.lockedinlabs.tracker.ui.plan.PresetDays
import work.lockedinlabs.tracker.ui.plan.PresetPlanCard
import work.lockedinlabs.tracker.ui.plan.forLevel

/** First launch: welcome, about you, pick a plan, how it works. */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel) {
    BackHandler(enabled = viewModel.step != OnboardingStep.WELCOME, onBack = viewModel::back)
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        AnimatedContent(
            targetState = viewModel.step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "onboarding",
        ) { step ->
            when (step) {
                OnboardingStep.WELCOME -> Welcome(viewModel)
                OnboardingStep.ABOUT -> AboutYou(viewModel)
                OnboardingStep.PLAN -> PickPlan(viewModel)
                OnboardingStep.TOUR -> Tour(viewModel)
            }
        }
    }
}

@Composable
private fun Welcome(viewModel: OnboardingViewModel) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Spacer(Modifier.weight(1f))
        Text(
            "LOCKED IN",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Train with a plan.\nWatch your strength grow.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(28.dp))
        Feature(Icons.Filled.Add, "Log sets in seconds")
        Feature(Icons.Filled.KeyboardArrowUp, "Know when to add weight")
        Feature(Icons.Filled.Person, "See every muscle you train")
        Spacer(Modifier.weight(1f))
        viewModel.restoreError?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
        }
        PrimaryButton("Get started", onClick = viewModel::next)
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = { viewModel.restore(context) },
            enabled = !viewModel.restoring,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (viewModel.restoring) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Restoring")
            } else {
                Text("Restore my backup")
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Header for the steps after the welcome: back arrow and progress dots. */
@Composable
private fun StepHeader(viewModel: OnboardingViewModel) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = viewModel::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        Spacer(Modifier.weight(1f))
        val steps = OnboardingStep.entries.drop(1)
        steps.forEach { s ->
            val on = s.ordinal <= viewModel.step.ordinal
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = if (s == viewModel.step) 20.dp else 8.dp, height = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            )
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(48.dp))
    }
}

/** A step: header, scrolling content, and a button pinned to the bottom. */
@Composable
private fun StepLayout(
    viewModel: OnboardingViewModel,
    title: String,
    subtitle: String?,
    button: String,
    onButton: () -> Unit,
    secondary: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        StepHeader(viewModel)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            subtitle?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(20.dp))
            content()
            Spacer(Modifier.height(16.dp))
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            PrimaryButton(button, onClick = onButton)
            secondary?.invoke()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutYou(viewModel: OnboardingViewModel) {
    val focus = LocalFocusManager.current
    StepLayout(viewModel, "About you", null, "Continue", onButton = { focus.clearFocus(); viewModel.next() }) {
        OutlinedTextField(
            value = viewModel.name,
            onValueChange = viewModel::onName,
            label = { Text("Your name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        Text("Experience", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        LEVELS.forEach { (level, note) ->
            Choice(level.label, note, selected = viewModel.level == level, onClick = { viewModel.onLevel(level) })
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("Main goal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OnboardingViewModel.GOALS.forEach { g ->
                val selected = viewModel.goal == g
                SuggestionChip(
                    onClick = { viewModel.onGoal(g) },
                    label = { Text(g) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        labelColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    ),
                    border = BorderStroke(
                        if (selected) 1.5.dp else 1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    ),
                )
            }
        }
    }
}

private val LEVELS = listOf(
    Level.BEGINNER to "Under 1 year",
    Level.INTERMEDIATE to "1 to 3 years",
    Level.ADVANCED to "3+ years",
)

/** A selectable card with a title and a note; selected = green outline and check. */
@Composable
private fun Choice(title: String, note: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun PickPlan(viewModel: OnboardingViewModel) {
    val chosen = viewModel.plan
    StepLayout(
        viewModel,
        title = "Pick a plan",
        subtitle = "You can change it any time in More.",
        button = if (chosen == null) "Skip" else "Use this plan",
        onButton = viewModel::next,
        secondary = if (chosen != null) {
            { TextButton(onClick = { viewModel.onPlan(null); viewModel.next() }, modifier = Modifier.fillMaxWidth()) { Text("Skip") } }
        } else null,
    ) {
        viewModel.presets.forLevel(viewModel.level).forEach { preset ->
            val selected = preset == chosen
            PresetPlanCard(
                preset,
                recommended = viewModel.level != null && preset.levels.first() == viewModel.level,
                onClick = { viewModel.onPlan(if (selected) null else preset) },
                modifier = if (selected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)) else Modifier,
            )
            if (selected) {
                Spacer(Modifier.height(10.dp))
                PresetDays(preset)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun Tour(viewModel: OnboardingViewModel) {
    StepLayout(viewModel, "How it works", null, "Start training", onButton = viewModel::finish) {
        TourStep(1, "Log", "Enter weight and reps, then tap Save.")
        TourStep(2, "Step up", "Hit your reps and you'll be told to add weight.")
        TourStep(3, "Track", "Home shows your strength and muscles.")
        TourStep(4, "Back up", "Sign in with Google in Profile.")
    }
}

@Composable
private fun TourStep(n: Int, title: String, text: String) {
    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text("$n", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
