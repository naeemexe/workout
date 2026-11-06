package work.lockedinlabs.tracker.ui.log

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import work.lockedinlabs.tracker.MainActivity
import work.lockedinlabs.tracker.R

/**
 * Runs only during a rest or a hold. Without it Android freezes the app in the background and the phone can
 * sleep with the screen off, and the timer's end comes late. The countdown stays in [RestTimer].
 */
class RestService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lockedin:rest")
            .apply { acquire(WAKE_LOCK_LIMIT_MS) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification())
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val channel = NotificationChannel(CHANNEL, "Rest timer", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.avatar_dumbbell)
            .setContentTitle("Timer running")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "rest_timer"
        private const val NOTIFICATION_ID = 1
        /** Past the longest cardio timer. */
        private const val WAKE_LOCK_LIMIT_MS = 4 * 60 * 60 * 1000L

        /** The timer runs either way, just without this help if Android refuses. */
        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, RestService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RestService::class.java))
        }
    }
}
