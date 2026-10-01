package io.github.nideta231.hermesremote.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nideta231.hermesremote.ConnectionState
import io.github.nideta231.hermesremote.SessionsState
import io.github.nideta231.hermesremote.SystemState
import io.github.nideta231.hermesremote.data.DesktopInfo
import io.github.nideta231.hermesremote.data.Pairing
import io.github.nideta231.hermesremote.data.PairingParser
import io.github.nideta231.hermesremote.data.SessionSummary
import io.github.nideta231.hermesremote.data.Transport
import io.github.nideta231.hermesremote.data.TransportMode
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

// ================================================================ Sessions

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    state: SessionsState,
    currentId: String?,
    activeRunSessionId: String?,
    onOpen: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onNewChat: () -> Unit,
) {
    var menuFor by remember { mutableStateOf<SessionSummary?>(null) }
    var renaming by remember { mutableStateOf<SessionSummary?>(null) }
    var deleting by remember { mutableStateOf<SessionSummary?>(null) }

    PullToRefreshBox(isRefreshing = state.loading && state.items.isNotEmpty(), onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text("Sessions", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
                state.error?.let { Text(it, color = Bad, modifier = Modifier.padding(horizontal = 16.dp)) }
            }
            items(state.items.sortedWith(compareByDescending<SessionSummary> { it.pinned }
                .thenByDescending { it.lastActive ?: 0.0 }), key = { it.id }) { s ->
                SessionRow(s, current = s.id == currentId, running = s.id == activeRunSessionId,
                    onClick = { onOpen(s.id) }, onLongClick = { menuFor = s })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            }
            item {
                if (state.hasMore) {
                    LaunchedEffect(state.items.size) { onLoadMore() }
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp)) }
                } else if (state.items.isEmpty() && !state.loading) {
                    Text("No sessions yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
            }
        }
        if (state.loading && state.items.isEmpty()) CircularProgressIndicator(Modifier.align(Alignment.Center))
        FloatingActionButton(onClick = onNewChat, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "New chat")
        }
    }

    menuFor?.let { s ->
        AlertDialog(onDismissRequest = { menuFor = null }, title = { Text(s.displayTitle, maxLines = 2) },
            text = { Text("${s.messageCount} messages · ${s.source ?: "?"}") },
            confirmButton = { TextButton(onClick = { renaming = s; menuFor = null }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { deleting = s; menuFor = null }) { Text("Delete", color = Bad) } })
    }
    renaming?.let { s ->
        var title by remember { mutableStateOf(s.title ?: "") }
        AlertDialog(onDismissRequest = { renaming = null }, title = { Text("Rename session") },
            text = { OutlinedTextField(title, { title = it }, singleLine = true) },
            confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onRename(s.id, title.trim()); renaming = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } })
    }
    deleting?.let { s ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete session?") },
            text = { Text("\"${s.displayTitle}\" will be permanently removed from Hermes. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { onDelete(s.id); deleting = null }) { Text("Delete", color = Bad) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(s: SessionSummary, current: Boolean, running: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(Modifier.fillMaxWidth()
        .background(if (current) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (s.pinned) {
                    Text("★", color = Gold, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.size(4.dp))
                }
                Text(s.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (current || s.pinned) FontWeight.SemiBold else FontWeight.Normal)
            }
            val meta = listOfNotNull(s.source, "${s.messageCount} msgs", s.model, s.lastActive?.let { relative(it) }).joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (running) Box(Modifier.size(8.dp).background(Gold, CircleShape))
    }
}

private fun relative(epochSec: Double): String {
    val diff = System.currentTimeMillis() / 1000.0 - epochSec
    return when {
        diff < 60 -> "just now"
        diff < 3600 -> "${(diff / 60).toInt()}m ago"
        diff < 86400 -> "${(diff / 3600).toInt()}h ago"
        diff < 7 * 86400 -> "${(diff / 86400).toInt()}d ago"
        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date((epochSec * 1000).toLong()))
    }
}

// ================================================================ Desktop

private data class RdpClient(val label: String, val pkg: String?, val uri: (DesktopInfo) -> String)

private val rdpClients = listOf(
    // aFreeRDP: FreeRDP's own client; the upstream KRdp project is tested against FreeRDP.
    RdpClient("aFreeRDP", "com.freerdp.afreerdp") { d ->
        val user = d.username?.let { Uri.encode(it) + "@" } ?: ""
        "freerdp://$user${d.host}:${d.port}/connect?clipboard=%2B&gfx=&dynamic-resolution=&network=auto"
    },
    RdpClient("Other RDP app", null) { d -> d.rdpUri },
)

@Composable
fun DesktopScreen(info: Result<DesktopInfo>?, statusOk: Boolean?, onLoad: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { onLoad() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Desktop", style = MaterialTheme.typography.titleLarge)
        Text("Your real KDE Plasma session over RDP (KRdp). Log in with your PC user account password.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        when {
            info == null -> CircularProgressIndicator()
            info.isFailure -> {
                Text(info.exceptionOrNull()?.message ?: "Unavailable", color = Bad)
                OutlinedButton(onClick = onLoad) { Text("Retry") }
            }
            else -> {
                val d = info.getOrThrow()
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(when (statusOk) { true -> Ok; false -> Bad; null -> Warn }, CircleShape))
                            Spacer(Modifier.size(8.dp))
                            Text(when (statusOk) { true -> "KRdp is running"; false -> "KRdp is down on the PC"; null -> "Status unknown" })
                        }
                        Detail("Host", "${d.host}:${d.port}", context)
                        d.dnsName?.takeIf { it.isNotEmpty() }?.let { Detail("Name", it, context) }
                        d.username?.let { Detail("User", it, context) }
                    }
                }
                rdpClients.forEach { c ->
                    val installed = c.pkg == null || isInstalled(context, c.pkg)
                    Button(onClick = { launch(context, c, d) }, modifier = Modifier.fillMaxWidth(), enabled = installed || c.pkg != null) {
                        Text(if (installed) "Open in ${c.label}" else "Get ${c.label}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text("Tips: start with a moderate resolution. Microsoft's Android RD client is known not to work with KRdp; " +
                    "aFreeRDP (F-Droid / Play) is the recommended client. If the screen stays black, disable \"gfx\" in its bookmark settings.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String, context: Context) {
    Row(Modifier.fillMaxWidth().clickable { copy(context, value) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(min = 56.dp))
        Text(value, fontFamily = FontFamily.Monospace, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text("copy", style = MaterialTheme.typography.labelSmall, color = Gold)
    }
}

private fun copy(context: Context, value: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("value", value))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

private fun isInstalled(context: Context, pkg: String): Boolean =
    runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

private fun launch(context: Context, c: RdpClient, d: DesktopInfo) {
    if (c.pkg != null && !isInstalled(context, c.pkg)) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${c.pkg}"))
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/${c.pkg}/"))
        try { context.startActivity(market) } catch (e: ActivityNotFoundException) { context.startActivity(web) }
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(c.uri(d))).apply { c.pkg?.let { setPackage(it) } }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        copy(context, "${d.host}:${d.port}")
        Toast.makeText(context, "No RDP app handles this; address copied", Toast.LENGTH_LONG).show()
    }
}

// ================================================================ System

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SystemScreen(state: SystemState, pairing: Pairing?, onRefresh: () -> Unit, onUnpair: () -> Unit,
                 onApprovalMode: (String) -> Unit = {}, conn: ConnectionState = ConnectionState(),
                 onUseTransport: (Transport) -> Unit = {}, onUseAuto: () -> Unit = {}) {
    var confirmUnpair by remember { mutableStateOf(false) }
    PullToRefreshBox(isRefreshing = state.loading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("System", style = MaterialTheme.typography.titleLarge)
            ConnectionCard(conn, onUseTransport, onUseAuto)
            ApprovalModeCard(state, onPick = onApprovalMode)
            state.error?.let {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(Bad, CircleShape)); Spacer(Modifier.size(8.dp))
                            Text("Bridge unreachable", fontWeight = FontWeight.SemiBold)
                        }
                        Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                        Text("Check: same trusted Wi-Fi as the PC, or Tailscale on (here and on the PC) · the PC is on and awake · " +
                            "`systemctl --user status hermes-remote-bridge` on the PC.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            state.components.forEach { c ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(if (c.ok) Ok else Bad, CircleShape)); Spacer(Modifier.size(8.dp))
                            Text(c.label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(c.summary, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
                        }
                        c.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
                    }
                }
            }
            state.checkedAt?.let {
                Text("Checked ${DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(it))}. Pull to refresh.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            pairing?.let {
                Text("Paired as \"${it.device}\"", fontWeight = FontWeight.SemiBold)
                Text(it.url, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = { confirmUnpair = true }) { Text("Unpair this device", color = Bad, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
    if (confirmUnpair) {
        AlertDialog(onDismissRequest = { confirmUnpair = false }, title = { Text("Unpair?") },
            text = { Text("Removes the token from this device. Also run `hermes-remote-bridge revoke ${pairing?.device ?: "<name>"}` on the PC to invalidate it there.") },
            confirmButton = { TextButton(onClick = { confirmUnpair = false; onUnpair() }) { Text("Unpair", color = Bad) } },
            dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text("Cancel") } })
    }
}

// ================================================================ Pairing

@Composable
fun PairScreen(initialUri: String?, onScan: () -> Unit, onPair: suspend (Pairing) -> String?) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("http://") }
    var token by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun attempt(p: Result<Pairing>) {
        val pairing = p.getOrElse { error = it.message; return }
        busy = true; error = null
        scope.launch {
            error = onPair(pairing)
            busy = false
        }
    }

    LaunchedEffect(initialUri) {
        if (initialUri != null) attempt(runCatching { PairingParser.parseUri(initialUri) })
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.size(24.dp))
        Text("Hermes Remote", style = MaterialTheme.typography.headlineMedium, color = Gold, fontWeight = FontWeight.SemiBold)
        Text("Pair this device with the bridge on your PC. Run on the PC:", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(8.dp)) {
            Text("hermes-remote-bridge pair phone", fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.padding(10.dp))
        }
        Text("Tailscale must be connected on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onScan, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Scan pairing QR code", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        HorizontalDivider()
        Text("Or enter manually", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(url, { url = it }, label = { Text("Bridge URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        OutlinedTextField(token, { token = it }, label = { Text("Token (hrb_…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { attempt(runCatching { PairingParser.validate(url, token) }) }, enabled = !busy && token.isNotBlank(),
            modifier = Modifier.fillMaxWidth()) { Text("Connect") }
        if (busy) CircularProgressIndicator()
        error?.let { Text(it, color = Bad) }
    }
}

private val approvalModes = listOf(
    Triple("manual", "Manual", "Ask before every risky command."),
    Triple("smart", "Smart", "An AI check auto-allows safe commands, blocks dangerous ones, asks only when unsure."),
    Triple("off", "Off", "Never ask. Every command runs (same as --yolo)."),
)

@Composable
private fun ApprovalModeCard(state: SystemState, onPick: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 10.dp)) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Command approvals", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (state.approvalSaving) Text("Saving…", style = MaterialTheme.typography.labelSmall, color = Warn)
            }
            Text("Applies to Hermes everywhere: desktop, messaging platforms and this app.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
            if (state.approvalMode == null) {
                Text("Unknown — pull to refresh.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(14.dp))
            }
            approvalModes.forEach { (id, label, hint) ->
                val selected = state.approvalMode == id
                Row(Modifier.fillMaxWidth()
                        .clickable(enabled = state.approvalMode != null && !state.approvalSaving) { onPick(id) }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(label, color = if (id == "off" && selected) Bad else MaterialTheme.colorScheme.onSurface)
                        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(conn: ConnectionState, onUseTransport: (Transport) -> Unit, onUseAuto: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 10.dp)) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Connection", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (conn.searching) Text("Trying…", style = MaterialTheme.typography.labelSmall, color = Warn)
            }
            Text(conn.activeUrl?.let { url ->
                when (conn.transport) {
                    Transport.LAN -> "Using the local network · $url"
                    Transport.TAILNET -> "Using Tailscale · $url"
                    null -> url
                }
            } ?: "Not connected", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
            val hint = when {
                conn.needsRepairForLan -> "Pair again from the PC (hermes-remote-bridge pair) to use the local network securely."
                conn.transport == Transport.TAILNET && conn.pcNetwork != null && conn.pcNetworkTrusted == false ->
                    "The PC's network (${conn.pcNetwork}) isn't trusted, so it only answers over Tailscale. " +
                        "If you control it, run `hermes-remote-bridge trust` on the PC."
                conn.transport == Transport.LAN -> "Encrypted, verified as your PC"
                else -> null
            }
            hint?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
            }
            // Only offer the transport that is not in use AND advertised by the bridge, so the
            // button is never a dead end.
            if (conn.transport == Transport.TAILNET && conn.lanAvailable && !conn.needsRepairForLan) {
                TextButton(onClick = { onUseTransport(Transport.LAN) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("Switch to local network", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (conn.transport == Transport.LAN && conn.tailnetAvailable) {
                TextButton(onClick = { onUseTransport(Transport.TAILNET) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("Switch to Tailscale", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (conn.mode != TransportMode.AUTO && conn.activeUrl != null) {
                TextButton(onClick = onUseAuto, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("Automatic (prefer local network)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
