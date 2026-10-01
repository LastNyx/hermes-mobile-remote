package io.github.nideta231.hermesremote

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import android.Manifest
import android.os.Build
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import io.github.nideta231.hermesremote.data.Notifier
import io.github.nideta231.hermesremote.ui.AgentScreen
import io.github.nideta231.hermesremote.ui.DesktopScreen
import io.github.nideta231.hermesremote.ui.HermesTheme
import io.github.nideta231.hermesremote.ui.PairScreen
import io.github.nideta231.hermesremote.ui.SessionsScreen
import io.github.nideta231.hermesremote.ui.SystemScreen

enum class Tab(val label: String, val path: String) {
    AGENT("Agent", "M4,4h16v12H7l-3,3z"),
    SESSIONS("Sessions", "M4,5h16v2H4z M4,11h16v2H4z M4,17h10v2H4z"),
    DESKTOP("Desktop", "M3,4h18v12H3z M5,6v8h14V6z M9,18h6v2H9z"),
    SYSTEM("System", "M12,3a9,9 0 1,0 0.01,0z M11,7h2v6h-2z M11,15h2v2h-2z"),
}

private fun icon(path: String) = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(androidx.compose.ui.graphics.Color.White))
    .build()

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val pairUri = mutableStateOf<String?>(null)
    private val openAgentTab = mutableStateOf(false)

    private val scanner = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { pairUri.value = it }
    }

    // Asked once, right after pairing succeeds: without this the app can still run a turn, it
    // just cannot say anything when the turn ends while the phone is in a pocket.
    private val notifPermission = registerForActivityResult(RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        Notifier.ensureChannels(this)
        // Already-paired installs (upgrades) never pass through pairing, so ask here too.
        // Android itself stops showing the prompt after the user declines twice.
        if (vm.pairing.value != null) askForNotifications()
        setContent { HermesTheme { App() } }
    }

    /** Android 13+ gates notifications behind a runtime permission; below that they are on. */
    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) return
        runCatching { notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        // Tapped a notification: go straight to the chat it is about.
        intent?.getStringExtra(Notifier.EXTRA_OPEN_SESSION)?.let { sid ->
            intent.removeExtra(Notifier.EXTRA_OPEN_SESSION)
            vm.openSession(sid)
            openAgentTab.value = true
        }
        val data = intent?.data ?: return
        if (data.scheme == "hermesremote") {
            pairUri.value = data.toString()
            if (vm.pairing.value != null) vm.unpair() // explicit re-pair from a new code
        }
    }

    private fun scan() {
        scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Scan the code shown by `hermes-remote-bridge pair`")
            .setBeepEnabled(false).setOrientationLocked(false))
    }

    @Composable
    private fun App() {
        val pairing by vm.pairing.collectAsState()
        if (pairing == null) {
            Scaffold { pad ->
                Box(Modifier.padding(pad)) {
                    PairScreen(pairUri.value, onScan = ::scan, onPair = {
                        vm.pair(it).also { err ->
                            if (err == null) {
                                pairUri.value = null
                                askForNotifications()
                            }
                        }
                    })
                }
            }
            return
        }

        val chat by vm.chat.collectAsState()
        val sessions by vm.sessions.collectAsState()
        val system by vm.system.collectAsState()
        val desktop by vm.desktop.collectAsState()
        val models by vm.models.collectAsState()
        val choice by vm.modelChoice.collectAsState()
        val conn by vm.connection.collectAsState()
        val reasoning by vm.reasoning.collectAsState()
        val commands by vm.commands.collectAsState()
        val openModelPicker by vm.openModelPicker.collectAsState()
        val update by vm.update.collectAsState()
        val toast by vm.toast.collectAsState()
        var tab by rememberSaveable { mutableStateOf(Tab.AGENT) }
        if (openAgentTab.value) { tab = Tab.AGENT; openAgentTab.value = false }
        val snack = remember { SnackbarHostState() }
        LaunchedEffect(toast) { toast?.let { snack.showSnackbar(it); vm.consumeToast() } }

        val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 720
        val activeRunSession = chat.sessionId.takeIf { chat.busy }
        val desktopOk = system.components.firstOrNull { it.key == "desktop" }?.ok

        val agent: @Composable () -> Unit = {
            AgentScreen(chat, vm::send, vm::stop, vm::steer, vm::answerApproval,
                models, choice, vm::loadModels, vm::chooseModel, vm::togglePin, vm::setDraft,
                reasoning, vm::setReasoning, commands, vm::loadCommands, openModelPicker, vm::modelPickerOpened)
        }
        val sessionList: @Composable (Boolean) -> Unit = { switchTab ->
            SessionsScreen(sessions, chat.sessionId, activeRunSession,
                onOpen = { vm.openSession(it); if (switchTab) tab = Tab.AGENT },
                onRefresh = vm::refreshSessions, onLoadMore = vm::loadMoreSessions,
                onRename = vm::rename, onDelete = vm::delete, onNewChat = { vm.newChat(); tab = Tab.AGENT })
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                if (!wide) NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(selected = tab == t, onClick = {
                            tab = t
                            if (t == Tab.SESSIONS) vm.refreshSessions()
                            if (t == Tab.SYSTEM) vm.refreshStatus()
                        }, icon = { Icon(icon(t.path), t.label) }, label = { Text(t.label, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) })
                    }
                }
            },
        ) { pad ->
            if (wide) {
                Row(Modifier.fillMaxSize().padding(pad)) {
                    NavigationRail {
                        Tab.entries.filter { it != Tab.SESSIONS }.forEach { t ->
                            NavigationRailItem(selected = tab == t || (tab == Tab.SESSIONS && t == Tab.AGENT), onClick = {
                                tab = t
                                if (t == Tab.SYSTEM) vm.refreshStatus()
                            }, icon = { Icon(icon(t.path), t.label) }, label = { Text(t.label, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) })
                        }
                    }
                    when (tab) {
                        Tab.AGENT, Tab.SESSIONS -> {
                            Box(Modifier.width(320.dp).fillMaxHeight()) { sessionList(false) }
                            VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                            Box(Modifier.weight(1f)) { agent() }
                        }
                        Tab.DESKTOP -> DesktopScreen(desktop, desktopOk, vm::loadDesktop)
                        Tab.SYSTEM -> SystemScreen(system, pairing, vm::refreshStatus, vm::unpair, vm::setApprovalMode, conn, vm::useTransport, vm::useAutoTransport,
                            update, { vm.checkForUpdate() }, vm::installUpdate)
                    }
                }
            } else {
                Box(Modifier.fillMaxSize().padding(pad)) {
                    when (tab) {
                        Tab.AGENT -> agent()
                        Tab.SESSIONS -> sessionList(true)
                        Tab.DESKTOP -> DesktopScreen(desktop, desktopOk, vm::loadDesktop)
                        Tab.SYSTEM -> SystemScreen(system, pairing, vm::refreshStatus, vm::unpair, vm::setApprovalMode, conn, vm::useTransport, vm::useAutoTransport,
                            update, { vm.checkForUpdate() }, vm::installUpdate)
                    }
                }
            }
        }
    }
}
