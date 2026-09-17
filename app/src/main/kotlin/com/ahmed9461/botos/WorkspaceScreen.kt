package com.ahmed9461.botos

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmed9461.botos.data.StoreSnapshot
import com.ahmed9461.botos.design.*
import com.ahmed9461.botos.model.*

@Composable
internal fun WorkspaceScreen(
    snapshot: StoreSnapshot, selectedId: String, timeline: MessageTimeline, draft: String, busy: Boolean,
    onSelect: (String) -> Unit, onAdd: () -> Unit, onEdit: (String) -> Unit, onDelete: (String) -> Unit,
    onMove: (String, Int) -> Unit, onOpenTelegram: (String) -> Unit,
    onDraft: (String) -> Unit, onSend: () -> Unit, onAction: (ActionTicket) -> Unit,
) {
    val bots = snapshot.workspace.bots
    val selected = bots.firstOrNull { it.id == selectedId }
    val isPreview = selected == null
    var menu by remember { mutableStateOf(false) }
    var removeId by rememberSaveable { mutableStateOf<String?>(null) }
    val holder = rememberSaveableStateHolder()
    val duration = LocalMotionMillis.current
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.headline), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalIconButton(onClick = onAdd, enabled = !snapshot.loading && !snapshot.failed, modifier = Modifier.size(52.dp), shape = RoundedCornerShape(18.dp)) {
                BotGlyph(Glyph.ADD, stringResource(R.string.add_bot))
            }
        }
        when {
            snapshot.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text(stringResource(R.string.loading))
                }
            }
            snapshot.failed -> InfoCard(stringResource(R.string.storage_error))
            else -> {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                    item(key = WorkspaceViewModel.PREVIEW) {
                        FilterChip(selected = isPreview, onClick = { onSelect(WorkspaceViewModel.PREVIEW) }, label = { Text(stringResource(R.string.preview)) },
                            leadingIcon = { BotGlyph(Glyph.SPACE, modifier = Modifier.size(18.dp)) }, shape = RoundedCornerShape(16.dp))
                    }
                    items(bots, key = { it.id }) { bot ->
                        FilterChip(selected = selected?.id == bot.id, onClick = { onSelect(bot.id); menu = false },
                            label = { Text(bot.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp)) }, shape = RoundedCornerShape(16.dp))
                    }
                }
                holder.SaveableStateProvider(selected?.id ?: WorkspaceViewModel.PREVIEW) {
                    Column(Modifier.fillMaxSize()) {
                        if (isPreview) {
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.preview_notice), modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            Spacer(Modifier.height(10.dp))
                            MessageTimelineView(timeline, onAction, modifier = Modifier.weight(1f))
                            Composer(draft, onDraft, onSend)
                        } else if (selected != null) {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().animateContentSize(tween(duration))) {
                                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                                BotGlyph(Glyph.SPACE, modifier = Modifier.padding(14.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                            }
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
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        Text(stringResource(R.string.waiting_connection), style = MaterialTheme.typography.titleMedium)
                                        Text(stringResource(R.string.no_connection), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        OutlinedButton(onClick = { onOpenTelegram(selected.username) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                            BotGlyph(Glyph.LINK, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.open_telegram))
                                        }
                                    }
                                }
                                InfoCard(stringResource(R.string.bookmark_notice))
                            }
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
internal fun InfoCard(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Composer(draft: String, onDraft: (String) -> Unit, onSend: () -> Unit) {
    Row(Modifier.fillMaxWidth().imePadding().padding(top = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        OutlinedTextField(value = draft, onValueChange = onDraft, modifier = Modifier.weight(1f), maxLines = 4,
            shape = RoundedCornerShape(22.dp), placeholder = { Text(stringResource(R.string.composer_hint)) })
        FilledIconButton(onClick = onSend, enabled = draft.isNotBlank(), modifier = Modifier.size(54.dp), shape = RoundedCornerShape(19.dp)) {
            BotGlyph(Glyph.SEND, stringResource(R.string.send), tint = if (draft.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
