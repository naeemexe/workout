package work.lockedinlabs.tracker.ui.log

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Countdown between sets. Tracks an end time on the monotonic clock, so it stays accurate across
 * tab switches and screen locks while the app is alive.
 */
class RestTimer(
    private val scope: CoroutineScope,
    private val alarm: RestAlarm,
    private val onFinished: () -> Unit,
) {
    private var endsAt by mutableLongStateOf(0L)
    var totalSeconds by mutableIntStateOf(0)
        private set
    var remainingMs by mutableLongStateOf(0L)
        private set
    private var job: Job? = null

    val running: Boolean get() = endsAt > 0
    val remainingSeconds: Int get() = ((remainingMs + 999) / 1000).toInt()
    val fraction: Float get() = if (totalSeconds == 0) 0f else (remainingMs / (totalSeconds * 1000f)).coerceIn(0f, 1f)

    fun start(seconds: Int) {
        totalSeconds = seconds
        endsAt = now() + seconds * 1000L
        job?.cancel()
        job = scope.launch {
            while (true) {
                val left = endsAt - now()
                remainingMs = left.coerceAtLeast(0)
                if (left <= 0) break
                delay(minOf(200L, left))
            }
            endsAt = 0
            alarm.fire()
            onFinished()
        }
    }

    fun add(seconds: Int) {
        if (!running) return
        endsAt += seconds * 1000L
        totalSeconds += seconds
        remainingMs = endsAt - now()
    }

    fun skip() {
        job?.cancel()
        endsAt = 0
        remainingMs = 0
    }

    private fun now() = SystemClock.elapsedRealtime()
}

/** Buzz + beep when rest is over. */
class RestAlarm(context: Context) {
    private val app = context.applicationContext
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") app.getSystemService(Vibrator::class.java)

    fun fire() {
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 500), -1))
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 500)
            Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 800)
        }
    }
}
