package com.nath.hermesremote.data

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Watches runs the app started, by run id, until each reaches a terminal status, and owns the
 * "finished" and "needs approval" notifications.
 *
 * Nothing in the app stops this service when its own stream sees the end: that stream keeps
 * running in the background too, so it would always win the race and no notification would
 * ever fire. Instead the service decides, and only alerts while the app is off screen; in the
 * foreground the chat already shows the result.
 *
 * Foreground service rather than WorkManager: runs finish in seconds, WorkManager's floor is 15 min.
 */
class WatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = HashMap<String, Job>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifier.ensureChannels(this)
        startForegroundCompat()
        val target = intent?.getStringExtra(EXTRA_RUN_ID)?.takeIf { it.isNotBlank() }?.let {
            Target(it, intent.getStringExtra(EXTRA_SESSION_ID), intent.getStringExtra(EXTRA_TITLE))
        }
        synchronized(jobs) {
            if (target != null && jobs[target.runId]?.isActive != true) {
                jobs[target.runId] = scope.launch {
                    try { watch(target) } finally { finished(target.runId) }
                }
            }
            if (jobs.isEmpty()) stopNow()
        }
        // A restart arrives without an intent, so there is nothing to resume.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private data class Target(val runId: String, val sessionId: String?, val title: String?)

    private suspend fun watch(t: Target) {
        val pairing = CredentialStore(this).load() ?: return
        val client = BridgeClient(pairing)
        var approvalShown = false
        var misses = 0
        val deadline = System.currentTimeMillis() + MAX_WATCH_MS
        while (System.currentTimeMillis() < deadline) {
            val run = try {
                client.run(t.runId).also { misses = 0 }
            } catch (e: BridgeException) {
                if (e.httpCode == 404) return // bridge restarted and forgot the run
                null
            } catch (e: Exception) {
                null // between networks; keep trying
            }
            when {
                run == null -> if (++misses > MAX_MISSES) return
                run.terminal -> {
                    Notifier.clearApproval(this, t.runId)
                    if (!appVisible()) {
                        Notifier.postRunFinished(this, t.runId, t.sessionId, t.title, run.status, lastReply(client, t.sessionId))
                    }
                    return
                }
                run.status == STATUS_APPROVAL -> if (!approvalShown) {
                    approvalShown = true
                    // Posted even in the foreground: it is the one event that blocks the agent.
                    Notifier.postApprovalNeeded(this, t.runId, t.sessionId, t.title, run.pendingApproval)
                }
                approvalShown -> {
                    approvalShown = false
                    Notifier.clearApproval(this, t.runId)
                }
            }
            delay(POLL_MS)
        }
    }

    private suspend fun lastReply(client: BridgeClient, sessionId: String?): String? {
        sessionId ?: return null
        return runCatching {
            HistoryMapper.map(client.messages(sessionId))
                .filterIsInstance<ChatItem.Assistant>()
                .lastOrNull { it.text.isNotBlank() }?.text
        }.getOrNull()
    }

    private suspend fun appVisible(): Boolean = withContext(Dispatchers.Main) {
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

    private fun finished(runId: String) {
        synchronized(jobs) {
            jobs.remove(runId)
            if (jobs.isEmpty()) stopNow()
        }
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
        private const val EXTRA_RUN_ID = "run_id"
        private const val EXTRA_SESSION_ID = "session_id"
        private const val EXTRA_TITLE = "title"
        private const val STATUS_APPROVAL = "waiting_for_approval"
        private const val POLL_MS = 3_000L
        private const val MAX_MISSES = 200 // ~10 min unreachable
        private const val MAX_WATCH_MS = 6 * 60 * 60 * 1000L

        /** Call right after starting a run, while the app is on screen (Android 12+ rule). */
        fun start(context: Context, runId: String, sessionId: String?, title: String?) {
            val i = Intent(context, WatchService::class.java)
                .putExtra(EXTRA_RUN_ID, runId)
                .putExtra(EXTRA_SESSION_ID, sessionId)
                .putExtra(EXTRA_TITLE, title)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
                else context.startService(i)
            }
        }
    }
}
