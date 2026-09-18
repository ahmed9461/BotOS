package com.ahmed9461.botos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.semantics.Role
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
    var menu by remember(selectedId) { mutableStateOf(false) }
    var removeId by rememberSaveable { mutableStateOf<String?>(null) }
    val holder = rememberSaveableStateHolder()
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("workspace-screen")) {
        if (imeVisible) {
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(selected?.title ?: stringResource(R.string.preview), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(stringResource(if (isPreview) R.string.local_preview else R.string.live_workspace), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            ScreenHeader(stringResource(R.string.workspace), stringResource(R.string.space_intro)) {
                FilledTonalIconButton(onClick = onAdd, enabled = !snapshot.loading && !snapshot.failed && !busy,
                    modifier = Modifier.size(48.dp).testTag("add-bot"), shape = RoundedCornerShape(17.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    BotGlyph(Glyph.ADD, stringResource(R.string.add_bot), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        when {
            snapshot.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
            snapshot.failed -> InfoCard(stringResource(R.string.storage_error))
            else -> {
                if (!imeVisible) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                        item(key = WorkspaceViewModel.PREVIEW) {
                            BotTab(stringResource(R.string.preview), isPreview, { onSelect(WorkspaceViewModel.PREVIEW) }, Modifier.testTag("preview-tab"))
                        }
                        items(bots, key = { it.id }) { bot ->
                            BotTab(bot.title, selected?.id == bot.id, { onSelect(bot.id) }, Modifier.testTag("bot-tab-${bot.id}"))
                        }
                    }
                }
                holder.SaveableStateProvider(selected?.id ?: WorkspaceViewModel.PREVIEW) {
                    Column(Modifier.fillMaxSize()) {
                        if (isPreview) {
                            if (!imeVisible) {
                                Text(stringResource(R.string.preview_notice), modifier = Modifier.padding(start = 3.dp, bottom = 6.dp),
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            MessageTimelineView(timeline, onAction, modifier = Modifier.weight(1f))
                            Composer(draft, onDraft, onSend)
                        } else {
                            if (!imeVisible) Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        BotBadge(selected.title)
                                        Spacer(Modifier.width(14.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(selected.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                            Text("@${selected.username}", style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Box {
                                            IconButton(onClick = { menu = true }) { BotGlyph(Glyph.MORE, stringResource(R.string.more)) }
                                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                                DropdownMenuItem(text = { Text(stringResource(R.string.edit_bot)) }, enabled = !busy, onClick = { menu = false; onEdit(selected.id) })
                                                DropdownMenuItem(text = { Text(stringResource(R.string.move_up)) }, enabled = !busy && bots.indexOf(selected) > 0, onClick = { menu = false; onMove(selected.id, -1) })
                                                DropdownMenuItem(text = { Text(stringResource(R.string.move_down)) }, enabled = !busy && bots.indexOf(selected) < bots.lastIndex, onClick = { menu = false; onMove(selected.id, 1) })
                                                DropdownMenuItem(text = { Text(stringResource(R.string.remove)) }, enabled = !busy, onClick = { menu = false; removeId = selected.id })
                                            }
                                        }
                                    }
                                }
                            }
                            liveContent(selected)
                        }
                    }
                }
            }
        }
    }
    removeId?.let { target ->
        AlertDialog(onDismissRequest = { removeId = null }, title = { Text(stringResource(R.string.remove)) },
            text = { Text(stringResource(R.string.remove_confirm)) },
            confirmButton = { TextButton(onClick = { onDelete(target); holder.removeState(target); removeId = null }, enabled = !busy) { Text(stringResource(R.string.remove)) } },
            dismissButton = { TextButton(onClick = { removeId = null }) { Text(stringResource(R.string.cancel)) } })
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
private fun BotTab(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(16.dp), color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Box(modifier.selectable(selected, role = Role.Tab, onClick = onClick).heightIn(min = 48.dp).widthIn(max = 190.dp)
            .padding(horizontal = 18.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
