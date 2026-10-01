package work.lockedinlabs.tracker.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode(val label: String) { SYSTEM("Follow system"), LIGHT("Light"), DARK("Dark") }

/** Device-level app preferences (not synced — appearance is a per-phone choice). */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        prefs.getString(THEME, null)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode

    /** Default rest between sets, in seconds. */
    private val _restSeconds = MutableStateFlow(prefs.getInt(REST, 90))
    val restSeconds: StateFlow<Int> = _restSeconds

    fun setRestSeconds(seconds: Int) {
        prefs.edit { putInt(REST, seconds) }
        _restSeconds.value = seconds
    }

    /** The first-launch welcome has been finished (or skipped because there was data already). */
    var onboarded: Boolean
        get() = prefs.getBoolean(ONBOARDED, false)
        set(value) = prefs.edit { putBoolean(ONBOARDED, value) }

    /** Back to first-launch defaults (erasing this phone's data). */
    fun clear() = prefs.edit(commit = true) { clear() }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(THEME, mode.name) }
        _themeMode.value = mode
    }

    companion object {
        private const val THEME = "theme_mode"
        private const val REST = "rest_seconds"
        private const val ONBOARDED = "onboarded"
        val REST_CHOICES = listOf(30, 60, 90, 120, 150, 180, 240, 300)
    }
}
