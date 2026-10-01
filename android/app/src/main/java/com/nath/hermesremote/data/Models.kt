package com.nath.hermesremote.data

import org.json.JSONArray
import org.json.JSONObject

data class Pairing(
    val url: String,
    val device: String,
    val token: String,
    /** Other addresses from the pairing code, so the first connect can fall back. */
    val alternates: List<String> = emptyList(),
    /** SHA-256 of the bridge's TLS certificate (pairing code v2). Required for the LAN. */
    val pin: String? = null,
)

data class SessionSummary(
    val id: String,
    val title: String?,
    val preview: String?,
    val source: String?,
    val messageCount: Int,
    val lastActive: Double?,
    val model: String?,
    val pinned: Boolean = false,
) {
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() } ?: preview?.lineSequence()?.firstOrNull()?.take(80)
            ?.takeIf { it.isNotBlank() } ?: "Untitled session"
}

/** One poll of a session that another surface (desktop, messaging platforms) is driving. */
data class SessionSync(
    val messages: List<JSONObject>,
    val cursor: Int,
    val changed: Boolean,
    val active: Boolean,
    val model: String?,
)

data class ModelOption(val provider: String, val providerName: String, val id: String, val label: String, val current: Boolean)

data class ModelCatalog(
    val currentModel: String?,
    val currentProvider: String?,
    val options: List<ModelOption>,
) {
    val selected: ModelOption?
        get() = options.firstOrNull { it.id == currentModel && it.provider == currentProvider }
            ?: options.firstOrNull { it.id == currentModel }
}

data class ApprovalRequest(
    val requestId: String?,
    val command: String?,
    val description: String?,
    val choices: List<String>,
)

data class RunSnapshot(
    val runId: String,
    val sessionId: String?,
    val status: String,
    val lastSeq: Long,
    val pendingApproval: ApprovalRequest?,
) {
    val terminal: Boolean get() = status in TERMINAL_STATUSES

    companion object {
        val TERMINAL_STATUSES = setOf("completed", "failed", "cancelled", "interrupted")
    }
}

data class SseEvent(val id: Long, val name: String, val data: JSONObject)

data class ComponentStatus(val key: String, val label: String, val ok: Boolean, val summary: String, val detail: String?)

data class DesktopInfo(val host: String, val dnsName: String?, val port: Int, val username: String?, val rdpUri: String)

/** Error returned by the bridge with its stable error code (never an IOException: not retried). */
class BridgeException(val httpCode: Int, val code: String, message: String) : Exception(message)

// ---------------------------------------------------------------- JSON helpers

fun JSONObject.str(name: String): String? = if (isNull(name)) null else optString(name)

fun JSONObject.dbl(name: String): Double? = if (isNull(name)) null else optDouble(name).takeUnless { it.isNaN() }

fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }

fun parseApproval(o: JSONObject?): ApprovalRequest? = o?.let {
    ApprovalRequest(it.str("request_id"), it.str("command"), it.str("description"),
        it.optJSONArray("choices").strings().ifEmpty { listOf("once", "deny") })
}

fun parseRun(o: JSONObject) = RunSnapshot(
    runId = o.getString("run_id"),
    sessionId = o.str("session_id"),
    status = o.optString("status", "running"),
    lastSeq = o.optLong("last_seq", 0),
    pendingApproval = parseApproval(o.optJSONObject("pending_approval")),
)

fun parseSession(o: JSONObject) = SessionSummary(
    id = o.getString("id"),
    title = o.str("title"),
    preview = o.str("preview"),
    source = o.str("source"),
    messageCount = o.optInt("message_count", 0),
    lastActive = o.dbl("last_active") ?: o.dbl("started_at"),
    model = o.str("model"),
    pinned = o.optBoolean("pinned", false),
)

/** Build the picker list: featured models of every usable provider first, then the rest. */
fun parseCatalog(o: JSONObject): ModelCatalog {
    val cur = o.optJSONObject("current")
    val options = mutableListOf<ModelOption>()
    o.optJSONArray("providers").objects().forEach { p ->
        val slug = p.str("slug") ?: return@forEach
        val name = p.str("name") ?: slug
        val featured = p.optJSONArray("featured").strings().toSet()
        val ordered = p.optJSONArray("models").strings().sortedBy { if (it in featured) "0$it" else "1$it" }
        ordered.forEach { m ->
            options += ModelOption(slug, name, m, m.substringAfterLast('/'), p.optBoolean("current", false))
        }
    }
    return ModelCatalog(cur?.str("model"), cur?.str("provider"), options)
}
