package com.ahmed9461.botos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmed9461.botos.data.StoreSnapshot
import com.ahmed9461.botos.design.*
import com.ahmed9461.botos.model.*
import com.ahmed9461.botos.media.BotAvatar

/** Only saved bots are destinations. No example conversation or synthetic message source. */
@Composable
internal fun WorkspaceScreen(
    snapshot: StoreSnapshot, selectedId: String, busy: Boolean,
    onSelect: (String) -> Unit, onAdd: () -> Unit, onEdit: (String) -> Unit,
    onDelete: (String) -> Unit, onMove: (String, Int) -> Unit,
    onOpenTelegram: (String) -> Unit, liveContent: @Composable (SavedBot) -> Unit,
) {
    val bots = snapshot.workspace.bots
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val selected = bots.firstOrNull { it.id == selectedId }
    var actions by remember(selectedId) { mutableStateOf(false) }
    var switcher by remember { mutableStateOf(false) }
    var removeId by rememberSaveable { mutableStateOf<String?>(null) }
    val holder = rememberSaveableStateHolder()
    // Back closes a modal first, then returns to the actual list, never another bot action.
    BackHandler(selected != null && !switcher && !actions && removeId == null) {
        focus.clearFocus(); onSelect(WorkspaceViewModel.HOME)
    }
    Column(Modifier.fillMaxSize().testTag("workspace-screen")) {
        when {
            snapshot.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
            snapshot.failed -> Box(Modifier.padding(16.dp)) { InfoCard(stringResource(R.string.storage_error)) }
            selected == null -> BotList(bots, busy, false, onAdd) { focus.clearFocus(); onSelect(it) }
            else -> {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp)
                        .testTag("compact-bot-header"), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { focus.clearFocus(); onSelect(WorkspaceViewModel.HOME) },
                            modifier = Modifier.size(48.dp).testTag("chat-back")) {
                            BotGlyph(Glyph.BACK, stringResource(R.string.back))
                        }
                        BotAvatar(selected, 48.dp)
                        Column(Modifier.weight(1f).padding(start = 10.dp, end = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(selected.title, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("@${selected.username}", style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr),
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { switcher = true }, modifier = Modifier.size(48.dp).testTag("bot-switcher")) {
                            BotGlyph(Glyph.DOWN, stringResource(R.string.switch_bot), modifier = Modifier.size(20.dp))
                        }
                        Box {
                            IconButton(onClick = { actions = true }, enabled = !busy,
                                modifier = Modifier.size(48.dp).testTag("bot-actions")) {
                                BotGlyph(Glyph.MORE, stringResource(R.string.more))
                            }
                            DropdownMenu(actions, onDismissRequest = { actions = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.edit_short)) },
                                    leadingIcon = { BotGlyph(Glyph.EDIT) },
                                    onClick = { actions = false; onEdit(selected.id) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.add_bot)) },
                                    leadingIcon = { BotGlyph(Glyph.ADD) },
                                    onClick = { actions = false; onAdd() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.open_telegram)) },
                                    leadingIcon = { BotGlyph(Glyph.LINK) },
                                    onClick = { actions = false; onOpenTelegram(selected.username) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.move_up)) },
                                    leadingIcon = { BotGlyph(Glyph.UP) }, enabled = bots.indexOf(selected) > 0,
                                    onClick = { actions = false; onMove(selected.id, -1) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.move_down)) },
                                    leadingIcon = { BotGlyph(Glyph.DOWN) }, enabled = bots.indexOf(selected) < bots.lastIndex,
                                    onClick = { actions = false; onMove(selected.id, 1) })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { BotGlyph(Glyph.TRASH, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { actions = false; removeId = selected.id })
                            }
                        }
                    }
                }
                holder.SaveableStateProvider(selected.id) {
                    Box(Modifier.weight(1f).padding(horizontal = 8.dp)) { liveContent(selected) }
                }
            }
        }
    }
    if (switcher) BotSwitcher(bots, selectedId, { switcher = false }) { id -> switcher = false; focus.clearFocus(); onSelect(id) }
    removeId?.let { target ->
        AlertDialog(onDismissRequest = { removeId = null }, title = { Text(stringResource(R.string.remove)) },
            text = { Text(stringResource(R.string.remove_confirm)) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = { onDelete(target); holder.removeState(target); removeId = null }) {
                    Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error)
                }
            }, dismissButton = { TextButton(onClick = { removeId = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BotSwitcher(bots: List<SavedBot>, selectedId: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(bots, query) { bots.filter { it.title.contains(query, true) || it.username.contains(query, true) } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("bot-switch-sheet")) {
            Text(stringResource(R.string.switch_bot), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            SearchField(query, { query = it }, "bot-switch-search")
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                if (filtered.isEmpty()) item { Text(stringResource(R.string.no_search_results), Modifier.padding(16.dp)) }
                items(filtered, key = { it.id }) { bot ->
                    BotRow(bot, { onSelect(bot.id) }, "bot-switch-item-${bot.id}", selected = bot.id == selectedId)
                }
            }
        }
    }
}

@Composable
private fun BotList(bots: List<SavedBot>, busy: Boolean, library: Boolean, onAdd: () -> Unit, onOpen: (String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    val filtered = remember(bots, search) { bots.filter { it.title.contains(search, true) || it.username.contains(search, true) } }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag(if (library) "bot-library-list" else "chat-list")) {
        ScreenHeader(stringResource(if (library) R.string.library else R.string.chat_list_title),
            stringResource(if (library) R.string.library_intro else R.string.chat_list_subtitle)) {
            FilledTonalIconButton(onClick = onAdd, enabled = !busy, modifier = Modifier.size(48.dp).testTag("add-bot"), shape = CircleShape) {
                BotGlyph(Glyph.ADD, stringResource(R.string.add_bot), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        if (bots.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = 300.dp).padding(bottom = 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                            BotGlyph(if (library) Glyph.LIBRARY else Glyph.CHAT, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Text(stringResource(R.string.empty_bots), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.chat_empty_hint), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Button(onClick = onAdd, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.add_bot)) }
                }
            }
        } else {
            SearchField(search, { search = it }, if (library) "library-search" else "chat-search")
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.weight(1f)) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
                    if (filtered.isEmpty()) item { Text(stringResource(R.string.no_search_results), Modifier.padding(20.dp)) }
                    items(filtered, key = { it.id }) { bot ->
                        BotRow(bot, { onOpen(bot.id) }, "chat-row-${bot.id}", editing = library)
                        if (bot.id != filtered.lastOrNull()?.id) HorizontalDivider(Modifier.padding(start = 80.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun BotRow(bot: SavedBot, onClick: () -> Unit, tag: String, selected: Boolean = false, editing: Boolean = false) {
    Surface(onClick = onClick, color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f)
        else MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Row(Modifier.heightIn(min = 80.dp).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BotAvatar(bot, 52.dp, editable = false)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(bot.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("@${bot.username}", style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            when {
                selected -> BotGlyph(Glyph.CHECK, stringResource(R.string.selected_bot), tint = MaterialTheme.colorScheme.primary)
                editing -> BotGlyph(Glyph.EDIT, stringResource(R.string.edit_short), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                else -> BotGlyph(Glyph.CHAT, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, tag: String) {
    TextField(value = value, onValueChange = { onChange(it.take(80)) }, modifier = Modifier.fillMaxWidth().testTag(tag),
        singleLine = true, shape = RoundedCornerShape(16.dp), placeholder = { Text(stringResource(R.string.search_library)) },
        leadingIcon = { BotGlyph(Glyph.SEARCH, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { BotGlyph(Glyph.CLOSE, stringResource(R.string.clear_search), modifier = Modifier.size(18.dp)) } },
        colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent))
}

@Composable
internal fun ScreenHeader(title: String, subtitle: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp)); trailing()
    }
}

@Composable
internal fun BotBadge(title: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(52.dp)) {
        Box(contentAlignment = Alignment.Center) {
            val first = title.codePoints().findFirst().orElse(0)
            Text(if (first == 0) "" else String(Character.toChars(first)), style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
internal fun InfoCard(text: String) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun LibraryScreen(snapshot: StoreSnapshot, onAdd: () -> Unit, onEdit: (String) -> Unit) {
    Box(Modifier.fillMaxSize().testTag("library-screen")) {
        when {
            snapshot.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp))
            snapshot.failed -> Box(Modifier.padding(16.dp)) { InfoCard(stringResource(R.string.storage_error)) }
            else -> BotList(snapshot.workspace.bots, false, true, onAdd, onEdit)
        }
    }
}
