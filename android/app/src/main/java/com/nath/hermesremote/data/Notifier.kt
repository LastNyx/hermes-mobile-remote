package com.nath.hermesremote.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nath.hermesremote.MainActivity
import com.nath.hermesremote.R

/**
 * The app's notifications. Only events worth interrupting someone for: a run that needs a
 * decision, and a run that ended while the app was closed. Tool progress stays silent.
 *
 * Channels use the system defaults (sound, vibration, heads-up for approvals) so the user's own
 * Android notification settings apply; tapping opens the session the notification is about.
 */
object Notifier {
    private const val CHANNEL_APPROVALS = "approvals"
    private const val CHANNEL_RESULTS = "results"
    private const val CHANNEL_WATCH = "watching"

    const val WATCH_ID = 1
    const val EXTRA_OPEN_SESSION = "open_session"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(listOf(
            NotificationChannel(CHANNEL_APPROVALS, "Approval requests", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Hermes is paused until you allow or deny a command"
            },
            NotificationChannel(CHANNEL_RESULTS, "Finished tasks", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A task you started from this device finished, failed or was stopped"
            },
            // Required by Android for a foreground service; silent and collapsed.
            NotificationChannel(CHANNEL_WATCH, "Task in progress", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Shown while Hermes Remote waits for a task to finish"
                setShowBadge(false)
            },
        ))
    }

    fun watchNotification(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_WATCH)
            .setContentTitle("Hermes is working")
            .setContentText("You'll be notified when it finishes")
            .setSmallIcon(R.drawable.ic_stat_hermes)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp(context, null, 0))
            .build()

    fun postRunFinished(context: Context, runId: String, sessionId: String?, title: String?, status: String, reply: String?) {
        val where = title?.takeIf { it.isNotBlank() } ?: "Hermes"
        val (heading, fallback) = when (status) {
            "completed" -> where to "Finished."
            "failed" -> "Failed: $where" to "The task ended with an error. Tap to see what happened."
            "cancelled", "interrupted" -> "Stopped: $where" to "The task was stopped before it finished."
            else -> where to "Task ended ($status)."
        }
        val body = reply?.let(::plain)?.takeIf { it.isNotBlank() }?.take(600) ?: fallback
        post(context, CHANNEL_RESULTS, resultId(runId), heading, body, runId, sessionId,
            NotificationCompat.CATEGORY_MESSAGE)
    }

    fun postApprovalNeeded(context: Context, runId: String, sessionId: String?, title: String?, request: ApprovalRequest?) {
        val body = listOfNotNull(request?.description, request?.command?.let { "$ $it" })
            .joinToString("\n").ifBlank { "Hermes is waiting for your decision." }
        post(context, CHANNEL_APPROVALS, approvalId(runId),
            "Approval needed${title?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}",
            body.take(600), runId, sessionId, NotificationCompat.CATEGORY_REMINDER)
    }

    fun clearApproval(context: Context, runId: String) {
        NotificationManagerCompat.from(context).cancel(approvalId(runId))
    }

    /** Drop alerts for a session the user just opened; they have seen it. */
    fun clearSession(context: Context, sessionId: String) {
        val nm = NotificationManagerCompat.from(context)
        sessions.filterValues { it == sessionId }.keys.toList().forEach { runId ->
            nm.cancel(resultId(runId)); nm.cancel(approvalId(runId)); sessions.remove(runId)
        }
    }

    /** runId → sessionId of what is currently posted, so opening a chat can clear its alerts. */
    private val sessions = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun post(context: Context, channel: String, id: Int, title: String, body: String,
                     runId: String, sessionId: String?, category: String) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        if (sessionId != null) sessions[runId] = sessionId
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, channel)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_stat_hermes)
            .setCategory(category)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, sessionId, id))
            .build()
        runCatching { nm.notify(id, n) } // SecurityException if permission was revoked mid-flight
    }

    private fun openApp(context: Context, sessionId: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (sessionId != null) putExtra(EXTRA_OPEN_SESSION, sessionId) }
        return PendingIntent.getActivity(context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Markdown in a notification shows as raw symbols; strip the common marks. */
    internal fun plain(md: String): String = md
        .replace(Regex("```[\\s\\S]*?```"), "[code]")
        .replace(Regex("`([^`]*)`"), "$1")
        .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        .replace(Regex("(?m)^#{1,6}\\s*"), "")
        .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    private fun resultId(runId: String) = 0x10000 + (runId.hashCode() and 0xffff)
    private fun approvalId(runId: String) = 0x20000 + (runId.hashCode() and 0xffff)
}
