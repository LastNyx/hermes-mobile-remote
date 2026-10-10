package io.github.nideta231.hermesremote.data

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps the process, and with it [LiveLink]'s socket, alive while a turn the phone cares about is
 * running, so "finished" and "needs your approval" can be posted with the app in the background.
 * The notifications themselves come from [LiveLink]; this service only holds the process up and
 * stops as soon as nothing is running.
 *
 * Foreground service rather than WorkManager: turns finish in seconds to minutes, and the socket
 * must stay open the whole time (WorkManager would close it between runs).
 */
class WatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifier.ensureChannels(this)
        startForegroundCompat()
        if (!started) {
            started = true
            scope.launch {
                // Give a just-sent prompt a moment to register as running, then hold until idle.
                withTimeoutOrNull(START_GRACE_MS) { LiveLink.busy.first { it.isNotEmpty() } }
                withTimeoutOrNull(MAX_WATCH_MS) { LiveLink.busy.first { it.isEmpty() } }
                delay(1_000) // let the "finished" notification post before the process may go
                stopNow()
            }
        }
        // A restart arrives without the socket; nothing to resume.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun stopNow() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundCompat() {
        val n = Notifier.watchNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, Notifier.WATCH_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(Notifier.WATCH_ID, n)
        }
    }

    companion object {
        private const val START_GRACE_MS = 15_000L
        private const val MAX_WATCH_MS = 6 * 60 * 60 * 1000L

        /** Call when the app goes to the background with a turn running (Android 12+: while still allowed). */
        fun start(context: Context) {
            val i = Intent(context, WatchService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
                else context.startService(i)
            }
        }
    }
}
