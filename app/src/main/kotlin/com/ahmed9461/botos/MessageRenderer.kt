package com.ahmed9461.botos

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.ahmed9461.botos.design.*
import com.ahmed9461.botos.model.*
import com.ahmed9461.botos.telegram.runtime.PendingReply
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val LocalRichActionIds = staticCompositionLocalOf<Set<String>> { emptySet() }
private val LocalRichActivation = staticCompositionLocalOf<(String) -> Unit> { {} }
private val chatTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
internal fun MessageTimelineView(timeline: MessageTimeline, onAction: (ActionTicket) -> Unit, modifier: Modifier = Modifier,
    pending: PendingReply? = null, onStopPending: (Long) -> Unit = {}, busy: Boolean = false) {
    val listState = rememberLazyListState()
    val duration = LocalMotionMillis.current
    val followTail by remember { derivedStateOf { !listState.canScrollForward } }
    val currentPending = pending?.takeIf { it.content.chat == timeline.chat }
    var followPending by remember(timeline.chat) { mutableStateOf(true) }
    var programmaticScroll by remember(timeline.chat) { mutableStateOf(false) }
    LaunchedEffect(timeline.chat, listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }.collect { (scrolling, hasMore) ->
            if (!programmaticScroll && scrolling) followPending = !hasMore
            if (!programmaticScroll && !hasMore) followPending = true
        }
    }
    LaunchedEffect(timeline.chat, timeline.messages.lastOrNull()?.id) {
        if (currentPending == null && timeline.messages.isNotEmpty() && (followTail || timeline.messages.takeLast(2).any { it.outgoing })) {
            if (duration == 0) listState.scrollToItem(timeline.messages.lastIndex)
            else listState.animateScrollToItem(timeline.messages.lastIndex)
        }
    }
    // Follow a growing reply only while the reader remains at the tail. No whole-report animation.
    LaunchedEffect(timeline.chat, currentPending?.draftId, currentPending?.content?.revision) {
        if (currentPending == null || !followPending || listState.isScrollInProgress) return@LaunchedEffect
        val index = timeline.messages.size
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > index }
        withFrameNanos { }
        if (!followPending) return@LaunchedEffect
        programmaticScroll = true
        try {
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.scrollToItem(index)
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull { it.index == index }
            if (last != null) {
                val extra = last.offset + last.size - listState.layoutInfo.viewportEndOffset
                if (extra > 0) listState.scrollBy(extra.toFloat())
            }
        } finally { programmaticScroll = false }
    }
    LazyColumn(modifier.fillMaxWidth().testTag("message-list"), state = listState,
        verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
        items(timeline.messages, key = { "${it.chat.account}/${it.chat.chat}/${it.id}" }, contentType = { it.outgoing }) {
            MessageBubble(it, onAction)
        }
        currentPending?.let { reply ->
            item(key = "pending/${timeline.chat.account}/${timeline.chat.chat}/${reply.draftId}", contentType = "pending") {
                Column(Modifier.fillMaxWidth().testTag("pending-reply"), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    MessageBubble(reply.content, {})
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(if (reply.stopped) R.string.pending_stopped else R.string.pending_writing),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).testTag("pending-status"))
                        if (reply.canStop && !reply.stopped) TextButton(onClick = { onStopPending(reply.draftId) }, enabled = !busy,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("pending-stop")) {
                            Text(stringResource(R.string.pending_stop), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: BotMessage, onAction: (ActionTicket) -> Unit) {
    val content = remember(message.blocks) { message.blocks.filterNot { it is Block.Buttons } }
    val buttons = remember(message.blocks) { message.blocks.filterIsInstance<Block.Buttons>() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maximum = maxWidth * if (message.outgoing) .78f else .91f
        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (message.outgoing) AbsoluteAlignment.Right else AbsoluteAlignment.Left,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (content.isNotEmpty() || message.delivery != DeliveryState.NONE || message.date > 0L) {
                Surface(shape = RoundedCornerShape(18.dp),
                    color = if (message.outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    contentColor = if (message.outgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    border = if (message.outgoing) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.widthIn(max = maximum)
                        .then(if (buttons.isNotEmpty()) Modifier.width(maximum) else Modifier.wrapContentWidth())
                        .testTag("message-bubble-${message.id}")) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CompositionLocalProvider(LocalRichActionIds provides message.inlineActions.filter { it.enabled }.map { it.id }.toSet(),
                            LocalRichActivation provides { id -> onAction(ActionTicket(message.chat, message.id, message.revision, id)) }) {
                            content.take(ContentLimits.MAX_BLOCKS).forEach { block -> key(block.id) { RenderBlock(block, message, onAction, 0) } }
                        }
                        if (!message.isFull) Text(stringResource(R.string.rich_partial), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MessageMeta(message)
                    }
                }
            }
            buttons.forEach { row ->
                RenderButtonRow(row, message, onAction, Modifier.width(maximum).testTag("message-buttons-${message.id}-${row.id}"))
            }
        }
    }
}

@Composable
private fun MessageMeta(message: BotMessage) {
    if (message.date <= 0L && message.delivery == DeliveryState.NONE) return
    val time = remember(message.date) {
        if (message.date <= 0L) "" else runCatching {
            chatTimeFormatter.format(Instant.ofEpochSecond(message.date).atZone(ZoneId.systemDefault()))
        }.getOrDefault("")
    }
    Row(Modifier.wrapContentWidth().testTag("message-meta-${message.id}"), horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (time.isNotBlank()) Text(time, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (message.delivery != DeliveryState.NONE) Text(stringResource(when (message.delivery) {
            DeliveryState.PENDING -> R.string.delivery_pending
            DeliveryState.FAILED -> R.string.delivery_failed
            else -> R.string.delivery_sent
        }), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = if (message.delivery == DeliveryState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("delivery-${message.id}"))
    }
}

/** Clickable text produces a revision-bound action, never an automatic ACTION_VIEW. */
@Composable
private fun StyledTextView(value: StyledText, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified, forceRtl: Boolean? = null, maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val activate by rememberUpdatedState(LocalRichActivation.current)
    val permitted = LocalRichActionIds.current
    var revealed by remember(value) { mutableStateOf(false) }
    val annotated = remember(value, primary, primaryContainer, surfaceVariant, onSurfaceVariant, revealed, permitted) {
        buildAnnotatedString {
            value.spans.forEach { span ->
                val actionId = span.actionId
                val decorations = buildList {
                    if (RichMark.UNDERLINE in span.marks || span.url != null) add(TextDecoration.Underline)
                    if (RichMark.STRIKETHROUGH in span.marks) add(TextDecoration.LineThrough)
                }
                val spanStyle = SpanStyle(
                    fontWeight = if (RichMark.BOLD in span.marks) FontWeight.Bold else null,
                    fontStyle = if (RichMark.ITALIC in span.marks) FontStyle.Italic else null,
                    fontFamily = if (RichMark.CODE in span.marks || RichMark.MATH in span.marks) FontFamily.Monospace else null,
                    textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
                    color = when { span.url != null -> primary; RichMark.SPOILER in span.marks -> onSurfaceVariant; else -> Color.Unspecified },
                    background = when {
                        RichMark.MARKED in span.marks -> primaryContainer
                        RichMark.SPOILER in span.marks || RichMark.CODE in span.marks -> surfaceVariant
                        else -> Color.Unspecified
                    },
                    baselineShift = when {
                        RichMark.SUPERSCRIPT in span.marks -> BaselineShift.Superscript
                        RichMark.SUBSCRIPT in span.marks -> BaselineShift.Subscript
                        else -> null
                    },
                    fontSize = if (RichMark.SUPERSCRIPT in span.marks || RichMark.SUBSCRIPT in span.marks) .82.em else TextUnit.Unspecified,
                )
                withStyle(spanStyle) {
                    if (RichMark.SPOILER in span.marks && !revealed) {
                        // Hidden text is absent from the semantics/selection tree until revealed.
                        withLink(LinkAnnotation.Clickable("reveal") { revealed = true }) { append("••••") }
                    } else if (actionId != null && actionId in permitted) {
                        withLink(LinkAnnotation.Clickable(actionId) { activate(actionId) }) { append(span.text) }
                    } else append(span.text)
                }
            }
        }
    }
    SelectionContainer {
        Text(annotated, modifier = modifier, style = style.copy(textDirection = when (forceRtl) {
            true -> TextDirection.Rtl; false -> TextDirection.Ltr; null -> TextDirection.Content
        }), color = color, maxLines = maxLines, overflow = overflow)
    }
}

@Composable
internal fun RenderBlock(block: Block, message: BotMessage, onAction: (ActionTicket) -> Unit, depth: Int) {
    if (depth >= ContentLimits.MAX_DEPTH) { Text(stringResource(R.string.content_limited)); return }
    val duration = LocalMotionMillis.current
    when (block) {
        is Block.Heading -> StyledTextView(block.content, style = when (block.level) {
            1 -> MaterialTheme.typography.titleLarge
            2 -> MaterialTheme.typography.titleMedium
            else -> MaterialTheme.typography.bodyLarge
        }.copy(fontWeight = FontWeight.SemiBold), forceRtl = message.forceRtl)
        is Block.Paragraph -> StyledTextView(block.content, Modifier.testTag("rich-text-${block.id}"),
            style = MaterialTheme.typography.bodyLarge, forceRtl = message.forceRtl)
        is Block.Quote -> {
            var expanded by rememberSaveable(message.chat.account, message.chat.chat, message.id, message.revision, block.id) {
                mutableStateOf(!block.expandable)
            }
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StyledTextView(block.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        forceRtl = message.forceRtl, maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis)
                    block.credit?.takeUnless(StyledText::isBlank)?.let {
                        StyledTextView(it, style = MaterialTheme.typography.labelSmall, forceRtl = message.forceRtl)
                    }
                    if (block.expandable) TextButton(onClick = { expanded = !expanded },
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)) {
                        Text(stringResource(if (expanded) R.string.collapse else R.string.expand))
                    }
                }
            }
        }
        is Block.RichQuote -> Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                block.children.forEach { RenderBlock(it, message, onAction, depth + 1) }
                if (!block.credit.isBlank()) StyledTextView(block.credit, style = MaterialTheme.typography.labelSmall, forceRtl = message.forceRtl)
            }
        }
        is Block.Code -> CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                StyledTextView(block.content, Modifier.horizontalScroll(rememberScrollState()).padding(11.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), forceRtl = false)
            }
        }
        is Block.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            block.items.take(100).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
        is Block.RichList -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            block.items.take(100).forEachIndexed { index, item ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Top) {
                    Text(when (item.checked) { true -> "☑"; false -> "☐"; null -> item.label.ifBlank { "•" } },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        item.blocks.forEach { child -> key("${block.id}-$index-${child.id}") { RenderBlock(child, message, onAction, depth + 1) } }
                    }
                }
            }
        }
        is Block.Table -> PlainTable(block)
        is Block.RichTable -> {
            if (!block.caption.isBlank()) {
                StyledTextView(block.caption, style = MaterialTheme.typography.labelLarge, forceRtl = message.forceRtl)
                Spacer(Modifier.height(5.dp))
            }
            Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = MaterialTheme.colorScheme.surface) { RichTableGrid(block, message.forceRtl) }
        }
        is Block.Details -> {
            var expanded by rememberSaveable(message.chat.account, message.chat.chat, message.id, message.revision, block.id) { mutableStateOf(false) }
            DetailsContainer(StyledText.plain(block.title), expanded, { expanded = !expanded }, message, block.children, onAction, depth, duration)
        }
        is Block.RichDetails -> {
            var expanded by rememberSaveable(message.chat.account, message.chat.chat, message.id, message.revision, block.id) { mutableStateOf(block.initiallyOpen) }
            DetailsContainer(block.header, expanded, { expanded = !expanded }, message, block.children, onAction, depth, duration)
        }
        is Block.Buttons -> RenderButtonRow(block, message, onAction)
        is Block.Divider -> HorizontalDivider(Modifier.padding(vertical = 3.dp), color = MaterialTheme.colorScheme.outlineVariant)
        is Block.Math -> CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(block.expression, Modifier.padding(11.dp).horizontalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr))
            }
        }
        is Block.Media -> {
            val reference = MediaReference(message.chat, message.id, message.revision, block.id)
            if (com.ahmed9461.botos.media.ReceivedMediaItem(reference, block.info)) {
                if (!block.info.caption.isBlank()) StyledTextView(block.info.caption,
                    style = MaterialTheme.typography.bodySmall, forceRtl = message.forceRtl)
            } else MediaCard(block.info, message.forceRtl)
        }
        is Block.Gallery -> Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                block.children.take(ContentLimits.MAX_MEDIA).forEach { RenderBlock(it, message, onAction, depth + 1) }
                if (!block.caption.isBlank()) StyledTextView(block.caption, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, forceRtl = message.forceRtl)
            }
        }
        is Block.Unsupported -> Text(stringResource(R.string.unsupported), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
    }
}

private data class TablePlacement(val row: Int, val column: Int, val rows: Int, val columns: Int, val cell: RichTableCell)

@Composable
private fun RichTableGrid(table: Block.RichTable, forceRtl: Boolean?) {
    val rows = table.rows.take(ContentLimits.MAX_ROWS)
    val columns = rows.maxOfOrNull { it.size }?.coerceAtMost(ContentLimits.MAX_COLUMNS) ?: 0
    if (columns == 0) return
    val placements = remember(rows, columns) {
        val occupied = Array(rows.size) { BooleanArray(columns) }
        buildList {
            rows.forEachIndexed { r, row -> row.take(columns).forEachIndexed { c, cell ->
                if (!cell.invisible && !occupied[r][c]) {
                    val spanC = cell.colspan.coerceIn(1, columns - c)
                    val spanR = cell.rowspan.coerceIn(1, rows.size - r)
                    for (rr in r until r + spanR) for (cc in c until c + spanC) occupied[rr][cc] = true
                    add(TablePlacement(r, c, spanR, spanC, cell))
                }
            } }
        }
    }
    val direction = when (forceRtl) { true -> LayoutDirection.Rtl; false -> LayoutDirection.Ltr; null -> LocalLayoutDirection.current }
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Layout(content = {
                placements.forEach { entry ->
                    Surface(color = if (entry.cell.isHeader) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(.5.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        StyledTextView(entry.cell.content, Modifier.padding(if (table.compact) 7.dp else 10.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (entry.cell.isHeader) FontWeight.SemiBold else FontWeight.Normal),
                            forceRtl = forceRtl)
                    }
                }
            }) { measurables, constraints ->
                val unit = (if (table.compact) 104.dp else 126.dp).roundToPx()
                val placeables = measurables.mapIndexed { index, measurable ->
                    val width = unit * placements[index].columns
                    measurable.measure(Constraints(minWidth = width, maxWidth = width))
                }
                val heights = IntArray(rows.size) { 32.dp.roundToPx() }
                placements.forEachIndexed { index, entry ->
                    val current = (entry.row until entry.row + entry.rows).sumOf { heights[it] }
                    val missing = (placeables[index].height - current).coerceAtLeast(0)
                    if (missing > 0) heights[entry.row + entry.rows - 1] += missing
                }
                val offsets = IntArray(rows.size + 1)
                heights.forEachIndexed { index, height -> offsets[index + 1] = offsets[index] + height }
                layout(constraints.constrainWidth(unit * columns), constraints.constrainHeight(offsets.last())) {
                    placeables.forEachIndexed { index, placeable ->
                        val entry = placements[index]
                        placeable.placeRelative(entry.column * unit, offsets[entry.row])
                    }
                }
            }
        }
    }
}

@Composable
private fun PlainTable(block: Block.Table) {
    Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            if (block.headers.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.padding(10.dp)) { block.headers.take(ContentLimits.MAX_COLUMNS).forEach {
                    Text(it.take(256), Modifier.width(118.dp).padding(horizontal = 6.dp), fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                } }
            }
            block.rows.take(ContentLimits.MAX_ROWS).forEach { row ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.padding(10.dp)) { row.take(ContentLimits.MAX_COLUMNS).forEach {
                    Text(it.take(512), Modifier.width(118.dp).padding(horizontal = 6.dp), style = MaterialTheme.typography.bodySmall)
                } }
            }
        }
    }
}

@Composable
private fun DetailsContainer(title: StyledText, expanded: Boolean, onToggle: () -> Unit, message: BotMessage,
    children: List<Block>, onAction: (ActionTicket) -> Unit, depth: Int, duration: Int) {
    Column(Modifier.animateContentSize(tween(duration)), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        OutlinedButton(onClick = onToggle, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)) {
            Text(title.plainText(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            BotGlyph(if (expanded) Glyph.UP else Glyph.DOWN, stringResource(if (expanded) R.string.collapse else R.string.expand),
                modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        }
        if (expanded) children.take(ContentLimits.MAX_BLOCKS).forEach { RenderBlock(it, message, onAction, depth + 1) }
    }
}

@Composable
internal fun RenderButtonRow(block: Block.Buttons, message: BotMessage, onAction: (ActionTicket) -> Unit, modifier: Modifier = Modifier) {
    if (block.buttons.isEmpty()) return
    // Telegram row order is physical left-to-right, independent of the surrounding UI locale.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            block.buttons.take(8).forEach { button ->
                val colors = when (button.style) {
                    ButtonVisualStyle.DANGER -> ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer)
                    ButtonVisualStyle.SUCCESS -> ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (MaterialTheme.colorScheme.background.luminance() < .5f) Color(0xFF304B3C) else Color(0xFFD9EBDD),
                        contentColor = if (MaterialTheme.colorScheme.background.luminance() < .5f) Color(0xFFE4F2E7) else Color(0xFF203D2B))
                    ButtonVisualStyle.PRIMARY -> ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    ButtonVisualStyle.LINK -> ButtonDefaults.filledTonalButtonColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.primary)
                    ButtonVisualStyle.DEFAULT -> ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(enabled = button.enabled && button.payload != ActionPayload.Unsupported,
                    onClick = { onAction(ActionTicket(message.chat, message.id, message.revision, button.id)) },
                    colors = colors, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp)) {
                    Text(button.label.take(256), style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Content, textAlign = TextAlign.Center),
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun MediaCard(info: MediaInfo, forceRtl: Boolean?) {
    val kind = stringResource(when (info.kind) {
        MediaKind.PHOTO -> R.string.rich_media_photo
        MediaKind.VIDEO -> R.string.rich_media_video
        MediaKind.ANIMATION -> R.string.rich_media_animation
        MediaKind.AUDIO -> R.string.rich_media_audio
        MediaKind.VOICE_NOTE -> R.string.rich_media_voice
        MediaKind.DOCUMENT -> R.string.rich_media_document
        MediaKind.MAP -> R.string.rich_media_map
        MediaKind.EMBEDDED -> R.string.rich_media_embedded
        MediaKind.CHAT_LINK -> R.string.rich_media_chat_link
    })
    Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(kind, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            info.title.takeIf { it.isNotBlank() && it !in setOf("Embedded", "Map", "Voice") }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            val details = buildList {
                info.durationSeconds?.takeIf { it > 0 }?.let { add("%d:%02d".format(it / 60, it % 60)) }
                info.size?.takeIf { it > 0 }?.let { add(formatSize(it)) }
                if (info.kind == MediaKind.MAP && info.latitude != null && info.longitude != null) add("%.5f, %.5f".format(info.latitude, info.longitude))
            }.joinToString(" · ")
            if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!info.caption.isBlank()) StyledTextView(info.caption, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, forceRtl = forceRtl)
            Text(stringResource(R.string.rich_media_limited), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
