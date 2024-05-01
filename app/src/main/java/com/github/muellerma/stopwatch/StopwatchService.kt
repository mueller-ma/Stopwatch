package com.github.muellerma.stopwatch

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.seconds

class StopwatchService : Service(), CoroutineScope {
    override val coroutineContext = Job()
    private var wakeLock: PowerManager.WakeLock? = null
    private var stopwatchJob: Job? = null
    private var lastSeconds: Long = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand() playFlag = $currentFlag")
        stopwatchJob?.cancel()
        if (currentFlag == PlayFlag.PLAY) {
            play()
        } else if (currentFlag == PlayFlag.PAUSE) {
            pause()
        }
        return START_STICKY
    }

    @SuppressLint("WakelockTimeout")
    private fun play() {
        stopwatchJob = launch {
            (lastSeconds+1..Long.MAX_VALUE).forEach { seconds ->
                Log.d(TAG, "Running for $seconds seconds")
                (application as StopwatchApp).notifyObservers(ServiceStatus.Running(seconds))
                lastSeconds = seconds
                delay(1.seconds)
            }
        }
        wakeLock.safeRelease()
        wakeLock = getSystemService<PowerManager>()!!
            .newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Stopwatch::StopwatchService"
            )
        wakeLock?.acquire()
    }

    private fun pause() {
        Log.d(TAG, "Paused at $lastSeconds seconds")
        (application as StopwatchApp).notifyObservers(ServiceStatus.Paused(lastSeconds))
        wakeLock.safeRelease()
    }

    override fun onCreate() {
        Log.d(TAG, "onCreate")
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService<NotificationManager>()!!
            with(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_MIN
                )
            ) {
                setShowBadge(true)
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
                nm.createNotificationChannel(this)
            }
        }

        startForeground(NOTIFICATION_ID, getNotification(getString(R.string.app_name)))
    }

    private fun getNotification(title: String): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setTicker(title)
            .setSmallIcon(R.drawable.baseline_timer_24)
            .setOngoing(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        super.onDestroy()

        wakeLock.safeRelease()
        stopwatchJob?.cancel()
        lastSeconds = -1
        (application as StopwatchApp).notifyObservers(ServiceStatus.Stopped)
    }

    companion object {
        private val TAG = StopwatchService::class.java.simpleName
        const val NOTIFICATION_ID = 1
        const val NOTIFICATION_CHANNEL_ID = "foreground_service"
        private var currentFlag: PlayFlag = PlayFlag.RESET

        fun changeState(context: Context, flag: PlayFlag) {
            Log.d(TAG, "changeState($flag)")
            val intent = Intent(context, StopwatchService::class.java)
            currentFlag = flag
            if (flag == PlayFlag.RESET) {
                context.stopService(intent)
            } else {
                ContextCompat.startForegroundService(context, intent)
            }
        }
    }
}

enum class PlayFlag {
    PREPAUSE, PAUSE, PLAY, RESET
}

fun PowerManager.WakeLock?.safeRelease() {
    try {
        this?.release()
    } catch (e: RuntimeException) {
        Log.d("WakeLock", "Couldn't release wakelock $this", e)
    }
}