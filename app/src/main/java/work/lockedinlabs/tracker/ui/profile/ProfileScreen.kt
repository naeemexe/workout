package work.lockedinlabs.tracker.ui.profile

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import android.text.format.DateUtils
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import work.lockedinlabs.tracker.sync.SyncStatus
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.shape.RoundedCornerShape
import work.lockedinlabs.tracker.domain.weightText
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import work.lockedinlabs.tracker.data.Profile
import work.lockedinlabs.tracker.domain.label
import work.lockedinlabs.tracker.ui.theme.LabCard

@Composable
fun ProfileScreen(viewModel: ProfileViewModel, weightLbs: Double?, onOpenWeight: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val p = viewModel.profile

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
            Text("Profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        if (!viewModel.loaded) return@Column

        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ProfileAvatar(p, 96.dp)
            Spacer(Modifier.height(16.dp))
            // Pick one of the built-in avatars; tap the selected one again to go back to initials.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatars.forEachIndexed { i, style ->
                    val selected = p.avatarId == i
                    AvatarIcon(
                        style,
                        48.dp,
                        Modifier
                            .then(if (selected) Modifier.border(2.5.dp, style.color, CircleShape) else Modifier)
                            .clip(CircleShape)
                            .clickable { viewModel.onAvatar(i) },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = p.name,
            onValueChange = viewModel::onName,
            label = { Text("Name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = viewModel.age,
                onValueChange = viewModel::onAge,
                label = { Text("Age") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f),
            )
            // Weight lives in the weight log; this shows the latest and opens it.
            Box(Modifier.weight(1.3f)) {
                OutlinedTextField(
                    value = weightLbs?.weightText().orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Weight") },
                    placeholder = { Text("Log it") },
                    suffix = { Text("lb") },
                    trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Covers the field so a tap opens the weight page instead of focusing it.
                Box(Modifier.matchParentSize().padding(top = 8.dp).clip(RoundedCornerShape(4.dp)).clickable(onClick = onOpenWeight))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = viewModel.heightFt,
                onValueChange = viewModel::onHeightFt,
                label = { Text("Height") },
                suffix = { Text("ft") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = viewModel.heightIn,
                onValueChange = viewModel::onHeightIn,
                label = { Text("Inches") },
                suffix = { Text("in") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(20.dp))
        ChoiceRow("Goal", Profile.GOALS, p.goal, viewModel::onGoal)
        Spacer(Modifier.height(16.dp))
        ChoiceRow("Experience", Profile.EXPERIENCE, p.experience, viewModel::onExperience)

        Spacer(Modifier.height(24.dp))
        LabCard {
            Row(Modifier.padding(vertical = 16.dp)) {
                Stat("${viewModel.workoutDays}", "workout days", Modifier.weight(1f))
                Stat("${viewModel.totalSets}", "sets logged", Modifier.weight(1f))
                Stat("${viewModel.exercisesLogged}", "exercises", Modifier.weight(1f))
            }
            Text(
                "Member since ${viewModel.memberSince.label()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 14.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        AccountCard(viewModel)
        Spacer(Modifier.height(24.dp))
    }
}

/** Google sign-in for cloud backup; the app works fully without it. */
@Composable
private fun AccountCard(viewModel: ProfileViewModel) {
    val context = LocalContext.current
    val status by viewModel.syncStatus.collectAsStateWithLifecycle()
    val account = viewModel.account
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    if (confirmDelete) DeleteAccountDialog(
        deleting = viewModel.deleting,
        onDelete = { eraseLocal -> viewModel.deleteAccount(context, eraseLocal) },
        onDismiss = { confirmDelete = false },
    )
    // Closes once the account is gone (signed out).
    LaunchedEffect(account) { if (account == null) confirmDelete = false }
    // This phone's data was erased too: start over from the welcome screen.
    LaunchedEffect(viewModel.erased) {
        if (viewModel.erased) {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            context.startActivity(Intent.makeRestartActivityTask(launch!!.component))
            Runtime.getRuntime().exit(0)
        }
    }
    LabCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (account == null) Icons.Filled.Lock else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (account == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (account == null) "Back up your data" else account.name ?: "Signed in",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        when {
                            account == null -> "Sign in with Google to back up your workouts."
                            else -> listOfNotNull(account.email, statusText(status)).joinToString(" · ")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status is SyncStatus.Failed && account != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (account == null) {
                Button(
                    onClick = { viewModel.signIn(context) },
                    enabled = !viewModel.signingIn,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (viewModel.signingIn) "Signing in…" else "Continue with Google") }
                viewModel.signInError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::syncNow, enabled = status !is SyncStatus.Syncing) { Text("Sync now") }
                    TextButton(onClick = { viewModel.signOut(context) }) {
                        Text("Sign out", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = { confirmDelete = true }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text("Delete account", color = MaterialTheme.colorScheme.error)
                }
                viewModel.deleteError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** Confirms deleting the account; optionally erases this phone's data too. */
@Composable
private fun DeleteAccountDialog(deleting: Boolean, onDelete: (eraseLocal: Boolean) -> Unit, onDismiss: () -> Unit) {
    var eraseLocal by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text("Delete account?") },
        text = {
            Column {
                Text("Your backup and account will be deleted. This can't be undone.")
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !deleting) { eraseLocal = !eraseLocal },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = eraseLocal, onCheckedChange = { eraseLocal = it }, enabled = !deleting)
                    Text("Also erase this phone's data")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDelete(eraseLocal) }, enabled = !deleting) {
                Text(if (deleting) "Deleting…" else "Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !deleting) { Text("Cancel") } },
    )
}

private fun statusText(status: SyncStatus): String = when (status) {
    SyncStatus.SignedOut -> "Not synced"
    SyncStatus.Syncing -> "Syncing…"
    is SyncStatus.Synced -> "Backed up " + DateUtils.getRelativeTimeSpanString(status.at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
    is SyncStatus.Failed -> status.message
}

@Composable
private fun ChoiceRow(title: String, options: List<String>, selected: String?, onSelect: (String) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(o) }) }
    }
}

@Composable
private fun Stat(value: String, caption: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
