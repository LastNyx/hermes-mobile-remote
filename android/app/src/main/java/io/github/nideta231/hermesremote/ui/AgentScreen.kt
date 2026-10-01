package io.github.nideta231.hermesremote.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nideta231.hermesremote.ChatState
import io.github.nideta231.hermesremote.Link
import io.github.nideta231.hermesremote.data.ModelCatalog
import io.github.nideta231.hermesremote.data.ModelOption
import io.github.nideta231.hermesremote.data.ChatItem
import io.github.nideta231.hermesremote.data.ToolStatus

@Composable
fun AgentScreen(
    state: ChatState,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onSteer: (String) -> Unit,
    onApproval: (String) -> Unit,
    catalog: ModelCatalog? = null,
    choice: ModelOption? = null,
    onLoadModels: () -> Unit = {},
    onChooseModel: (ModelOption?) -> Unit = {},
    onTogglePin: () -> Unit = {},
    onDraft: (String) -> Unit = {},
) {
    Column(Modifier.fillMaxSize().imePadding()) {
        ChatHeader(state, catalog, choice, onLoadModels, onChooseModel, onTogglePin)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.items.isEmpty() -> EmptyChat(Modifier.align(Alignment.Center))
                else -> MessageList(state, onApproval)
            }
        }
        Composer(state, onSend, onStop, onSteer, onDraft)
    }
}

@Composable
private fun ChatHeader(
    state: ChatState,
    catalog: ModelCatalog?,
    choice: ModelOption?,
    onLoadModels: () -> Unit,
    onChooseModel: (ModelOption?) -> Unit,
    onTogglePin: () -> Unit,
) {
    var picker by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.pinned) {
                        Text("★", color = Gold, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.size(4.dp))
                    }
                    Text(state.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val (label, color) = when {
                    state.link == Link.RECONNECTING -> "Reconnecting…" to Warn
                    state.run?.status == "waiting_for_approval" -> "Waiting for your approval" to Warn
                    state.run?.status == "stopping" -> "Stopping…" to Warn
                    state.busy -> "Working…" to Gold
                    state.following -> "Live on your PC" to MaterialTheme.colorScheme.primary
                    state.sessionId == null -> "New conversation" to MaterialTheme.colorScheme.onSurfaceVariant
                    else -> "Idle" to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(color, CircleShape))
                    Spacer(Modifier.size(6.dp))
                    Text(label, style = MaterialTheme.typography.labelMedium, color = color)
                }
            }
            if (state.sessionId != null) {
                IconButton(onClick = onTogglePin) {
                    Icon(if (state.pinned) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = if (state.pinned) "Unpin session" else "Pin session",
                        tint = if (state.pinned) Gold else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        ModelRow(state, catalog, choice, onOpen = { onLoadModels(); picker = true }, onReset = { onChooseModel(null) })
        if (picker) {
            ModelPickerSheet(catalog, choice, onDismiss = { picker = false }) {
                onChooseModel(it); picker = false
            }
        }
    }
}

@Composable
private fun ModelRow(state: ChatState, catalog: ModelCatalog?, choice: ModelOption?, onOpen: () -> Unit, onReset: () -> Unit) {
    val label = choice?.let { "${it.providerName}: ${it.label}" }
        ?: state.sessionModel
        ?: catalog?.currentModel
        ?: "Model"
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onOpen, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            modifier = Modifier.weight(1f, fill = false)) {
            Text("Model: $label ▾", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (choice != null) {
            TextButton(onClick = onReset, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                Text("reset", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ModelPickerSheet(catalog: ModelCatalog?, choice: ModelOption?, onDismiss: () -> Unit, onPick: (ModelOption?) -> Unit) {
    if (catalog == null) {
        AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
            title = { Text("Loading models…") }, text = { Text("Fetching the catalog from your PC.") })
        return
    }
    var query by remember { mutableStateOf("") }
    // Flatten to header/option rows so the list is a single items() pass.
    val rows = remember(catalog, query) {
        val filtered = catalog.options.filter {
            query.isBlank() || it.label.contains(query, true) || it.providerName.contains(query, true) || it.id.contains(query, true)
        }
        buildList {
            filtered.groupBy { it.provider to it.providerName }.forEach { (key, options) ->
                add(ModelRowItem.Header(key.second))
                options.forEach { add(ModelRowItem.Option(it)) }
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(null); onDismiss() }) { Text("Use session default") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Model") },
        text = {
            Column {
                OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("Filter") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.size(8.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(rows) { row ->
                        when (row) {
                            is ModelRowItem.Header -> Text(row.label, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                            is ModelRowItem.Option -> {
                                val o = row.option
                                val selected = choice?.id == o.id && choice?.provider == o.provider
                                Row(Modifier.fillMaxWidth()
                                    .clickable { onPick(o) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(o.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                    if (o.current) Text("current", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    if (rows.isEmpty()) {
                        item {
                            Text("No model matches “$query”.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }
        },
    )
}

private sealed interface ModelRowItem {
    data class Header(val label: String) : ModelRowItem
    data class Option(val option: ModelOption) : ModelRowItem
}

@Composable
private fun EmptyChat(modifier: Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Hermes", style = MaterialTheme.typography.headlineSmall, color = Gold, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.size(8.dp))
        Text("Runs on your PC with its full toolset. Ask for anything.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MessageList(state: ChatState, onApproval: (String) -> Unit) {
    val listState = rememberLazyListState()
    var follow by remember { mutableStateOf(true) }
    // Follow the stream unless the user scrolled up to read.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }.collect { (scrolling, canFwd) ->
            if (scrolling) follow = !canFwd
        }
    }
    val lastText = (state.items.lastOrNull() as? ChatItem.Assistant)?.text?.length ?: 0
    // The keyboard shrinks the viewport and LazyColumn keeps the top anchored, which would push the
    // newest item (often an approval card) out of view. Re-pin to the bottom on resize too.
    val viewport by remember { derivedStateOf { listState.layoutInfo.viewportSize.height } }
    LaunchedEffect(state.items.size, lastText, viewport) {
        if (follow && state.items.isNotEmpty()) listState.scrollToItem(state.items.lastIndex, Int.MAX_VALUE / 2)
    }
    // An unanswered approval blocks the agent: always bring it into view, even mid-read.
    val pendingApproval = state.items.indexOfLast { it is ChatItem.Approval && it.decided == null }
    LaunchedEffect(pendingApproval, viewport) {
        if (pendingApproval >= 0) { follow = true; listState.scrollToItem(pendingApproval) }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.items, key = { it.key }) { item ->
            when (item) {
                is ChatItem.User -> UserBubble(item)
                is ChatItem.Assistant -> AssistantBlock(item)
                is ChatItem.Tool -> ToolRow(item)
                is ChatItem.Approval -> ApprovalCard(item, enabled = state.run?.status == "waiting_for_approval", onApproval)
                is ChatItem.Notice -> Text(item.text, style = MaterialTheme.typography.labelMedium,
                    color = if (item.error) Bad else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun UserBubble(item: ChatItem.User) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
            modifier = Modifier.widthIn(max = 560.dp).padding(start = 48.dp)) {
            Text(item.text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = if (item.pending) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun AssistantBlock(item: ChatItem.Assistant) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        MarkdownText(item.text + if (item.streaming) " ▍" else "")
    }
}

@Composable
private fun ToolRow(item: ChatItem.Tool) {
    var open by rememberSaveable(item.key) { mutableStateOf(false) }
    val color = when (item.status) {
        ToolStatus.RUNNING -> Gold
        ToolStatus.OK -> Ok
        ToolStatus.FAILED -> Bad
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.clickable(enabled = item.result != null) { open = !open }.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.status == ToolStatus.RUNNING) CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = color)
                else Box(Modifier.size(8.dp).background(color, CircleShape))
                Spacer(Modifier.size(8.dp))
                Text(item.name, fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
                Spacer(Modifier.size(8.dp))
                Text(item.args, fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = if (open) 20 else 1,
                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                item.durationSec?.let {
                    Text(" %.1fs".format(it), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (open && item.result != null) {
                Text(item.result.take(4000), fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApprovalCard(item: ChatItem.Approval, enabled: Boolean, onApproval: (String) -> Unit) {
    Surface(color = Color(0xFF2E2614), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("Approval needed", color = Warn, fontWeight = FontWeight.SemiBold)
            item.request.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
            item.request.command?.let {
                Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, modifier = Modifier.padding(top = 8.dp)
                    .fillMaxWidth().background(Color(0x33000000), RoundedCornerShape(6.dp)).padding(8.dp))
            }
            if (item.decided != null) {
                Text("Answered: ${item.decided}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            } else {
                FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.request.choices.forEach { choice ->
                        val label = mapOf("once" to "Allow once", "session" to "Allow session", "always" to "Always", "deny" to "Deny")[choice] ?: choice
                        val pad = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        if (choice == "deny") OutlinedButton(onClick = { onApproval(choice) }, enabled = enabled, contentPadding = pad) {
                            Text(label, fontSize = 13.sp, maxLines = 1, softWrap = false)
                        }
                        else Button(onClick = { onApproval(choice) }, enabled = enabled, contentPadding = pad) {
                            Text(label, fontSize = 13.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Composer(state: ChatState, onSend: (String) -> Unit, onStop: () -> Unit, onSteer: (String) -> Unit,
                     onDraft: (String) -> Unit) {
    // The draft comes from the ViewModel, not local state: this composable leaves the tree whenever
    // the user changes tab or opens a session, and rememberSaveable would drop the text with it.
    val text = state.draft
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = text, onValueChange = onDraft,
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (state.busy) "Steer the running task…" else "Message Hermes…") },
                maxLines = 6, shape = RoundedCornerShape(20.dp),
            )
            Spacer(Modifier.size(8.dp))
            if (state.busy && text.isBlank()) {
                FilledIconButton(onClick = onStop, enabled = state.run?.status != "stopping",
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Bad), modifier = Modifier.size(52.dp)) {
                    Box(Modifier.size(14.dp).background(Color.White, RoundedCornerShape(2.dp)))
                }
            } else {
                FilledIconButton(
                    onClick = {
                        if (state.busy) onSteer(text) else onSend(text)
                        onDraft("")
                    },
                    enabled = text.isNotBlank() && !state.sending,
                    modifier = Modifier.size(52.dp),
                ) {
                    if (state.sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(if (state.busy) "↪" else "↑", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
