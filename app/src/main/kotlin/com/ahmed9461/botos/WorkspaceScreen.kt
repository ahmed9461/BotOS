package com.ahmed9461.botos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmed9461.botos.data.StoreSnapshot
import com.ahmed9461.botos.design.*
import com.ahmed9461.botos.model.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkspaceScreen(
    snapshot: StoreSnapshot, selectedId: String, timeline: MessageTimeline, draft: String, busy: Boolean,
    onSelect: (String) -> Unit, onAdd: () -> Unit, onEdit: (String) -> Unit, onDelete: (String) -> Unit,
    onMove: (String, Int) -> Unit, onOpenTelegram: (String) -> Unit,
    onDraft: (String) -> Unit, onSend: () -> Unit, onAction: (ActionTicket) -> Unit,
    liveContent: @Composable (SavedBot) -> Unit,
) {
    val bots = snapshot.workspace.bots
    val selected = bots.firstOrNull { it.id == selectedId }
    val isPreview = selected == null
    val imeVisible = WindowInsets.isImeVisible
    var actionMenu by remember(selectedId) { mutableStateOf(false) }
    var switchMenu by remember { mutableStateOf(false) }
    var removeId by rememberSaveable { mutableStateOf<String?>(null) }
    val holder = rememberSaveableStateHolder()
    val horizontalPadding = if (selected == null) 20.dp else 12.dp

    Column(
        Modifier.fillMaxSize().padding(horizontal = horizontalPadding).testTag("workspace-screen"),
    ) {
        when {
            snapshot.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
            snapshot.failed -> {
                if (!imeVisible) ScreenHeader(stringResource(R.string.workspace), stringResource(R.string.space_intro))
                InfoCard(stringResource(R.string.storage_error))
            }
            else -> {
                if (selected != null) {
                    CompactBotHeader(
                        bot = selected,
                        bots = bots,
                        busy = busy,
                        imeVisible = imeVisible,
                        switchExpanded = switchMenu,
                        onSwitchExpanded = { switchMenu = it },
                        actionExpanded = actionMenu,
                        onActionExpanded = { actionMenu = it },
                        onSelect = onSelect,
                        onAdd = onAdd,
                        onEdit = onEdit,
                        onMove = onMove,
                        onRemove = { removeId = selected.id },
                    )
                } else if (imeVisible) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 40.dp).testTag("preview-mode"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.preview), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        BotSwitcher(
                            bots = bots,
                            selectedId = null,
                            expanded = switchMenu,
                            onExpanded = { switchMenu = it },
                            onSelect = onSelect,
                        )
                    }
                } else {
                    ScreenHeader(stringResource(R.string.workspace), stringResource(R.string.space_intro)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            BotSwitcher(
                                bots = bots,
                                selectedId = null,
                                expanded = switchMenu,
                                onExpanded = { switchMenu = it },
                                onSelect = onSelect,
                            )
                            FilledTonalIconButton(
                                onClick = onAdd,
                                enabled = !busy,
                                modifier = Modifier.size(44.dp).testTag("add-bot"),
                                shape = RoundedCornerShape(15.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            ) {
                                BotGlyph(
                                    Glyph.ADD,
                                    stringResource(R.string.add_bot),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }

                holder.SaveableStateProvider(selected?.id ?: WorkspaceViewModel.PREVIEW) {
                    Column(Modifier.fillMaxSize()) {
                        if (isPreview) {
                            if (!imeVisible) {
                                Text(
                                    stringResource(R.string.preview_notice),
                                    modifier = Modifier.padding(start = 3.dp, bottom = 6.dp).testTag("preview-mode"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            MessageTimelineView(timeline, onAction, modifier = Modifier.weight(1f))
                            Composer(draft, onDraft, onSend)
                        } else {
                            liveContent(selected)
                        }
                    }
                }
            }
        }
    }

    removeId?.let { target ->
        AlertDialog(
            onDismissRequest = { removeId = null },
            title = { Text(stringResource(R.string.remove)) },
            text = { Text(stringResource(R.string.remove_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(target)
                        holder.removeState(target)
                        removeId = null
                    },
                    enabled = !busy,
                ) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { removeId = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun CompactBotHeader(
    bot: SavedBot,
    bots: List<SavedBot>,
    busy: Boolean,
    imeVisible: Boolean,
    switchExpanded: Boolean,
    onSwitchExpanded: (Boolean) -> Unit,
    actionExpanded: Boolean,
    onActionExpanded: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .padding(top = if (imeVisible) 2.dp else 7.dp, bottom = 5.dp)
            .heightIn(min = 46.dp)
            .testTag("compact-bot-header"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompactBotBadge(bot.title)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                bot.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "@${bot.username}",
                style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!imeVisible) {
            FilledTonalIconButton(
                onClick = onAdd,
                enabled = !busy,
                modifier = Modifier.size(38.dp).testTag("add-bot"),
                shape = RoundedCornerShape(13.dp),
            ) {
                BotGlyph(Glyph.ADD, stringResource(R.string.add_bot), modifier = Modifier.size(18.dp))
            }
        }
        BotSwitcher(
            bots = bots,
            selectedId = bot.id,
            expanded = switchExpanded,
            onExpanded = onSwitchExpanded,
            onSelect = onSelect,
        )
        Box {
            IconButton(
                onClick = { onActionExpanded(true) },
                enabled = !busy,
                modifier = Modifier.size(38.dp).testTag("bot-actions"),
            ) {
                BotGlyph(Glyph.MORE, stringResource(R.string.more), modifier = Modifier.size(19.dp))
            }
            DropdownMenu(
                expanded = actionExpanded,
                onDismissRequest = { onActionExpanded(false) },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit_bot)) },
                    onClick = { onActionExpanded(false); onEdit(bot.id) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.move_up)) },
                    enabled = bots.indexOf(bot) > 0,
                    onClick = { onActionExpanded(false); onMove(bot.id, -1) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.move_down)) },
                    enabled = bots.indexOf(bot) < bots.lastIndex,
                    onClick = { onActionExpanded(false); onMove(bot.id, 1) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remove)) },
                    onClick = { onActionExpanded(false); onRemove() },
                )
            }
        }
    }
}

@Composable
private fun BotSwitcher(
    bots: List<SavedBot>,
    selectedId: String?,
    expanded: Boolean,
    onExpanded: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
) {
    Box {
        IconButton(
            onClick = { onExpanded(!expanded) },
            modifier = Modifier.size(38.dp).testTag("bot-switcher"),
        ) {
            BotGlyph(
                if (expanded) Glyph.UP else Glyph.DOWN,
                stringResource(R.string.switch_bot),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpanded(false) },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.preview)) },
                onClick = {
                    onExpanded(false)
                    onSelect(WorkspaceViewModel.PREVIEW)
                },
                trailingIcon = {
                    if (selectedId == null) {
                        Text("✓", color = MaterialTheme.colorScheme.primary)
                    }
                },
                modifier = Modifier.testTag("bot-switch-item-preview"),
            )
            bots.forEach { saved ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(saved.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "@${saved.username}",
                                style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    },
                    onClick = {
                        onExpanded(false)
                        onSelect(saved.id)
                    },
                    trailingIcon = {
                        if (selectedId == saved.id) {
                            Text("✓", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier.testTag("bot-switch-item-${saved.id}"),
                )
            }
        }
    }
}

@Composable
private fun CompactBotBadge(title: String) {
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(38.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                title.take(1),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
internal fun ScreenHeader(title: String, subtitle: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp)); trailing()
    }
}

@Composable
internal fun BotBadge(title: String) {
    Surface(shape = RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(52.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(title.take(1), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
internal fun InfoCard(text: String) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Composer(draft: String, onDraft: (String) -> Unit, onSend: () -> Unit) {
    // No imePadding here. BotOsApp is the sole inset owner.
    Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp)) {
        Surface(modifier = Modifier.fillMaxWidth().testTag("composer-bar"), shape = RoundedCornerShape(25.dp),
            color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                BasicTextField(value = draft, onValueChange = onDraft, maxLines = 4,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, textDirection = TextDirection.Content),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    modifier = Modifier.weight(1f).testTag("composer-input").heightIn(min = 48.dp),
                    decorationBox = { field ->
                        Box(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
                            if (draft.isEmpty()) Text(stringResource(R.string.composer_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            field()
                        }
                    })
                FilledIconButton(onClick = onSend, enabled = draft.isNotBlank(), modifier = Modifier.size(48.dp).testTag("send-preview"), shape = RoundedCornerShape(18.dp)) {
                    BotGlyph(Glyph.SEND, stringResource(R.string.send), tint = if (draft.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun LibraryScreen(snapshot: StoreSnapshot, onAdd: () -> Unit, onEdit: (String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    val bots = snapshot.workspace.bots
    val filtered = remember(bots, search) { bots.filter { it.title.contains(search, true) || it.username.contains(search, true) } }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("library-screen")) {
        ScreenHeader(stringResource(R.string.library), stringResource(R.string.library_intro)) {
            FilledTonalIconButton(onClick = onAdd, enabled = !snapshot.loading && !snapshot.failed, modifier = Modifier.size(48.dp), shape = RoundedCornerShape(17.dp)) {
                BotGlyph(Glyph.ADD, stringResource(R.string.add_bot), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        when {
            snapshot.loading -> CircularProgressIndicator(Modifier.size(28.dp))
            snapshot.failed -> InfoCard(stringResource(R.string.storage_error))
            bots.isEmpty() -> {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        BotGlyph(Glyph.LIBRARY, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.empty_bots), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.library_empty_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onAdd, shape = RoundedCornerShape(16.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.add_bot)) }
                    }
                }
            }
            else -> {
                OutlinedTextField(search, { search = it.take(80) }, modifier = Modifier.fillMaxWidth().testTag("library-search"),
                    singleLine = true, shape = RoundedCornerShape(18.dp), placeholder = { Text(stringResource(R.string.search_library)) })
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.saved_count, bots.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (filtered.isEmpty()) item { InfoCard(stringResource(R.string.no_search_results)) }
                    items(filtered, key = { it.id }) { bot ->
                        Surface(onClick = { onEdit(bot.id) }, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                BotBadge(bot.title)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(bot.title, style = MaterialTheme.typography.titleMedium)
                                    Text("@${bot.username}", style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(stringResource(R.string.edit_short), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
