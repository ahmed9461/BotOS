package com.ahmed9461.botos

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ahmed9461.botos.design.*
import com.ahmed9461.botos.model.*

@Composable
internal fun MessageTimelineView(timeline: MessageTimeline, onAction: (ActionTicket) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val duration = LocalMotionMillis.current
    val followTail by remember { derivedStateOf { !listState.canScrollForward } }
    LaunchedEffect(timeline.chat, timeline.messages.lastOrNull()?.id) {
        if (timeline.messages.isNotEmpty() && (followTail || timeline.messages.takeLast(2).any { it.outgoing })) {
            if (duration == 0) listState.scrollToItem(timeline.messages.lastIndex) else listState.animateScrollToItem(timeline.messages.lastIndex)
        }
    }
    LazyColumn(modifier = modifier.fillMaxWidth().testTag("message-list"), state = listState,
        verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(timeline.messages, key = { "${it.chat.account}/${it.chat.chat}/${it.id}" }, contentType = { it.outgoing }) { message ->
            Box(Modifier.fillMaxWidth(), contentAlignment = if (message.outgoing) Alignment.CenterEnd else Alignment.CenterStart) {
                Surface(shape = RoundedCornerShape(22.dp),
                    color = if (message.outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    contentColor = if (message.outgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    border = if (message.outgoing) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(if (message.outgoing) .88f else 1f)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        message.blocks.take(ContentLimits.MAX_BLOCKS).forEach { block ->
                            key(block.id) { RenderBlock(block, message, onAction, 0) }
                        }
                        if (message.delivery != DeliveryState.NONE) Text(
                            stringResource(when (message.delivery) {
                                DeliveryState.PENDING -> R.string.delivery_pending
                                DeliveryState.FAILED -> R.string.delivery_failed
                                else -> R.string.delivery_sent
                            }), style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.testTag("delivery-${message.id}"))
                        if (message.blocks.size > ContentLimits.MAX_BLOCKS) Text(stringResource(R.string.content_limited), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
internal fun RenderBlock(block: Block, message: BotMessage, onAction: (ActionTicket) -> Unit, depth: Int) {
    if (depth >= ContentLimits.MAX_DEPTH) { Text(stringResource(R.string.content_limited)); return }
    val duration = LocalMotionMillis.current
    when (block) {
        is Block.Heading -> Text(block.text.take(ContentLimits.MAX_TEXT), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        is Block.Paragraph -> SelectionContainer { Text(block.text.take(ContentLimits.MAX_TEXT), style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content)) }
        is Block.Quote -> Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Text(block.text.take(ContentLimits.MAX_TEXT), Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        is Block.Code -> CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                SelectionContainer { Text(block.text.take(ContentLimits.MAX_TEXT), Modifier.horizontalScroll(rememberScrollState()).padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr)) }
            }
        }
        is Block.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            block.items.take(100).forEach { Text("• ${it.take(ContentLimits.MAX_TEXT)}", style = MaterialTheme.typography.bodyMedium) }
        }
        is Block.Table -> Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(Modifier.padding(12.dp)) { block.headers.take(ContentLimits.MAX_COLUMNS).forEach {
                        Text(it.take(256), Modifier.width(126.dp).padding(horizontal = 8.dp), fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    } }
                }
                block.rows.take(ContentLimits.MAX_ROWS).forEach { row ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.padding(12.dp)) { row.take(ContentLimits.MAX_COLUMNS).forEach { Text(it.take(512), Modifier.width(126.dp).padding(horizontal = 8.dp), style = MaterialTheme.typography.bodyMedium) } }
                }
            }
        }
        is Block.Details -> {
            var expanded by rememberSaveable(message.chat.account, message.chat.chat, message.id, message.revision, block.id) { mutableStateOf(false) }
            Column(Modifier.animateContentSize(tween(duration)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                    Text(block.title.take(256), Modifier.weight(1f))
                    BotGlyph(if (expanded) Glyph.UP else Glyph.DOWN, stringResource(if (expanded) R.string.collapse else R.string.expand), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
                if (expanded) block.children.take(ContentLimits.MAX_BLOCKS).forEach { RenderBlock(it, message, onAction, depth + 1) }
            }
        }
        is Block.Buttons -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            block.buttons.take(8).forEach { button ->
                FilledTonalButton(enabled = button.enabled && button.payload != ActionPayload.Unsupported,
                    onClick = { onAction(ActionTicket(message.chat, message.id, message.revision, button.id)) },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                    modifier = Modifier.widthIn(min = 120.dp, max = 220.dp).heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) { Text(button.label.take(256)) }
            }
        }
        is Block.Unsupported -> Text(stringResource(R.string.unsupported), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
