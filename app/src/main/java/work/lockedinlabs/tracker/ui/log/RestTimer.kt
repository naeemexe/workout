package work.lockedinlabs.tracker.ui.log

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
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
import work.lockedinlabs.tracker.R

/**
 * Countdown for a rest or a hold. Tracks an end time on the monotonic clock. [RestService] keeps the app
 * running while it counts, so it ends on time with the app in the background or the screen off.
 */
class RestTimer(
    private val scope: CoroutineScope,
    private val alarm: RestSounds,
    private val now: () -> Long = SystemClock::elapsedRealtime,
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
    /** Whole seconds counted so far. */
    val elapsedSeconds: Int get() = ((totalSeconds * 1000L - remainingMs) / 1000).toInt().coerceAtLeast(0)

    /**
     * @param quiet no start sound (right after an end tone, which it would play over).
     * @param tone play the end tone and buzz; without it, [onEnd] must start the next timer.
     * @param onEnd runs at zero, not after [skip].
     */
    fun start(seconds: Int, quiet: Boolean = false, tone: Boolean = true, onEnd: () -> Unit = {}) {
        totalSeconds = seconds
        endsAt = now() + seconds * 1000L
        remainingMs = seconds * 1000L
        job?.cancel()
        alarm.started(quiet)
        job = scope.launch {
            while (true) {
                val left = endsAt - now()
                remainingMs = left.coerceAtLeast(0)
                if (left <= 0) break
                delay(minOf(200L, left))
            }
            endsAt = 0
            if (tone) alarm.fire()
            onEnd()
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
        if (running) alarm.skipped()
        endsAt = 0
        remainingMs = 0
    }

}

interface RestSounds {
    fun started(quiet: Boolean)
    fun skipped()
    fun fire()
}

/**
 * Soft sound when rest starts; buzz + tone when it's over. Both are notification sounds, so they
 * follow notification volume and silent mode (the buzz still comes through on silent).
 */
class RestAlarm(context: Context) : RestSounds {
    private val app = context.applicationContext
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") app.getSystemService(Vibrator::class.java)

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val sounds = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attributes).build()
    private val startSound = sounds.load(app, R.raw.rest_start, 1)
    private val endSound = sounds.load(app, R.raw.rest_end, 1)

    private val audio = app.getSystemService(AudioManager::class.java)
    private val duck = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()
    private val main = Handler(Looper.getMainLooper())
    private val endDuck = Runnable { audio.abandonAudioFocusRequest(duck) }
    private val stopService = Runnable { RestService.stop(app) }

    /** Plays over any music as it is: you've just tapped Save, so this one can stay quiet. */
    override fun started(quiet: Boolean) {
        main.removeCallbacks(stopService)
        RestService.start(app)
        if (!quiet) sounds.play(startSound, 1f, 1f, 1, 0, 1f)
    }

    override fun skipped() {
        main.removeCallbacks(stopService)
        RestService.stop(app)
    }

    override fun fire() {
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 500), -1))
        // Dips music for the tone. Refused when the app is in the background; the tone plays anyway.
        if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            audio.requestAudioFocus(duck)
            main.removeCallbacks(endDuck)
            main.postDelayed(endDuck, END_SOUND_MS)
        }
        sounds.play(endSound, 1f, 1f, 1, 0, 1f)
        // Outlasts the tone and buzz, or the phone could sleep partway through them.
        main.removeCallbacks(stopService)
        main.postDelayed(stopService, FINISH_MS)
    }

    private companion object {
        const val END_SOUND_MS = 1300L
        const val FINISH_MS = 1600L
    }
}
