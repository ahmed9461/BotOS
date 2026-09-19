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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ahmed9461.botos.design.*
import com.ahmed9461.botos.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val chatTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
internal fun MessageTimelineView(
    timeline: MessageTimeline,
    onAction: (ActionTicket) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val duration = LocalMotionMillis.current
    val followTail by remember { derivedStateOf { !listState.canScrollForward } }
    LaunchedEffect(timeline.chat, timeline.messages.lastOrNull()?.id) {
        if (timeline.messages.isNotEmpty() && (followTail || timeline.messages.takeLast(2).any { it.outgoing })) {
            if (duration == 0) {
                listState.scrollToItem(timeline.messages.lastIndex)
            } else {
                listState.animateScrollToItem(timeline.messages.lastIndex)
            }
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth().testTag("message-list"),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(7.dp),
        contentPadding = PaddingValues(vertical = 6.dp),
    ) {
        items(
            timeline.messages,
            key = { it.chat.account + "/" + it.chat.chat + "/" + it.id },
            contentType = { it.outgoing },
        ) { message ->
            MessageBubble(message, onAction)
        }
    }
}

@Composable
private fun MessageBubble(message: BotMessage, onAction: (ActionTicket) -> Unit) {
    val contentBlocks = remember(message.blocks) { message.blocks.filterNot { it is Block.Buttons } }
    val buttonBlocks = remember(message.blocks) { message.blocks.filterIsInstance<Block.Buttons>() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val bubbleMax = maxWidth * if (message.outgoing) 0.78f else 0.91f
        val buttonMax = maxWidth * if (message.outgoing) 0.82f else 0.91f
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = if (message.outgoing) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (contentBlocks.isNotEmpty() || message.delivery != DeliveryState.NONE || message.date > 0L) {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (message.outgoing) 18.dp else 6.dp,
                        bottomEnd = if (message.outgoing) 6.dp else 18.dp,
                    ),
                    color = if (message.outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    contentColor = if (message.outgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    border = if (message.outgoing) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .widthIn(max = bubbleMax)
                        .wrapContentWidth()
                        .testTag("message-bubble-" + message.id),
                ) {
                    Column(
                        Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        contentBlocks.take(ContentLimits.MAX_BLOCKS).forEach { block ->
                            key(block.id) { RenderBlock(block, message, onAction, 0) }
                        }
                        MessageMeta(message)
                    }
                }
            }
            buttonBlocks.forEach { buttons ->
                Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = if (message.outgoing) Alignment.CenterEnd else Alignment.CenterStart,
                ) {
                    RenderButtonRow(
                        block = buttons,
                        message = message,
                        onAction = onAction,
                        modifier = Modifier.width(buttonMax).testTag("message-buttons-" + message.id + "-" + buttons.id),
                    )
                }
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
    Row(
        modifier = Modifier.wrapContentWidth().testTag("message-meta-" + message.id),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (time.isNotBlank()) {
            Text(
                time,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (message.delivery != DeliveryState.NONE) {
            Text(
                stringResource(
                    when (message.delivery) {
                        DeliveryState.PENDING -> R.string.delivery_pending
                        DeliveryState.FAILED -> R.string.delivery_failed
                        DeliveryState.SENT -> R.string.delivery_sent
                        DeliveryState.NONE -> R.string.delivery_sent
                    },
                ),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = if (message.delivery == DeliveryState.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.testTag("delivery-" + message.id),
            )
        }
    }
}

@Composable
private fun StyledTextView(
    value: StyledText,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    forceRtl: Boolean? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val annotated = remember(value, primary, primaryContainer, surfaceVariant, onSurfaceVariant) {
        buildAnnotatedString {
            value.spans.forEach { span ->
                val decorations = buildList {
                    if (RichMark.UNDERLINE in span.marks || span.url != null) add(TextDecoration.Underline)
                    if (RichMark.STRIKETHROUGH in span.marks) add(TextDecoration.LineThrough)
                }
                val spanStyle = SpanStyle(
                    fontWeight = if (RichMark.BOLD in span.marks) FontWeight.Bold else null,
                    fontStyle = if (RichMark.ITALIC in span.marks) FontStyle.Italic else null,
                    fontFamily = if (RichMark.CODE in span.marks || RichMark.MATH in span.marks) FontFamily.Monospace else null,
                    textDecoration = when (decorations.size) {
                        0 -> null
                        1 -> decorations.first()
                        else -> TextDecoration.combine(decorations)
                    },
                    color = when {
                        span.url != null -> primary
                        RichMark.SPOILER in span.marks -> onSurfaceVariant
                        else -> Color.Unspecified
                    },
                    background = when {
                        RichMark.MARKED in span.marks -> primaryContainer
                        RichMark.SPOILER in span.marks -> surfaceVariant
                        RichMark.CODE in span.marks -> surfaceVariant
                        else -> Color.Unspecified
                    },
                    baselineShift = when {
                        RichMark.SUPERSCRIPT in span.marks -> BaselineShift.Superscript
                        RichMark.SUBSCRIPT in span.marks -> BaselineShift.Subscript
                        else -> null
                    },
                    fontSize = if (RichMark.SUPERSCRIPT in span.marks || RichMark.SUBSCRIPT in span.marks) 0.82.em else TextUnit.Unspecified,
                )
                withStyle(spanStyle) { append(span.text) }
            }
        }
    }
    SelectionContainer {
        Text(
            annotated,
            modifier = modifier,
            style = style.copy(
                textDirection = when (forceRtl) {
                    true -> TextDirection.Rtl
                    false -> TextDirection.Ltr
                    null -> TextDirection.Content
                },
            ),
            color = color,
            maxLines = maxLines,
            overflow = overflow,
        )
    }
}

@Composable
internal fun RenderBlock(block: Block, message: BotMessage, onAction: (ActionTicket) -> Unit, depth: Int) {
    if (depth >= ContentLimits.MAX_DEPTH) {
        Text(stringResource(R.string.content_limited))
        return
    }
    val duration = LocalMotionMillis.current
    when (block) {
        is Block.Heading -> StyledTextView(
            block.content,
            style = when (block.level) {
                1 -> MaterialTheme.typography.titleLarge
                2 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.bodyLarge
            }.copy(fontWeight = FontWeight.SemiBold),
            forceRtl = message.forceRtl,
        )

        is Block.Paragraph -> StyledTextView(
            block.content,
            style = MaterialTheme.typography.bodyLarge,
            forceRtl = message.forceRtl,
        )

        is Block.Quote -> {
            var expanded by rememberSaveable(message.chat.account, message.chat.chat, message.id, message.revision, block.id) {
                mutableStateOf(!block.expandable)
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StyledTextView(
                        block.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        forceRtl = message.forceRtl,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                    )
                    block.credit?.takeUnless(StyledText::isBlank)?.let {
                        StyledTextView(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            forceRtl = message.forceRtl,
                        )
                    }
                    if (block.expandable) {
                        TextButton(
                            onClick = { expanded = !expanded },
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                        ) {
                            Text(stringResource(if (expanded) R.string.collapse else R.string.expand))
                        }
                    }
                }
            }
        }

        is Block.Code -> CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                StyledTextView(
                    block.content,
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(11.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    forceRtl = false,
                )
            }
        }

        is Block.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            block.items.take(100).forEach {
                Text("• " + it.take(ContentLimits.MAX_TEXT), style = MaterialTheme.typography.bodyMedium)
            }
        }

        is Block.RichList -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            block.items.take(100).forEachIndexed { index, item ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Top) {
                    Text(
                        when (item.checked) {
                            true -> "☑"
                            false -> "☐"
                            null -> item.label.ifBlank { "•" }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        item.blocks.forEach { child ->
                            key(block.id + "-" + index + "-" + child.id) {
                                RenderBlock(child, message, onAction, depth + 1)
                            }
                        }
                    }
                }
            }
        }

        is Block.Table -> PlainTable(block)

        is Block.RichTable -> {
            if (!block.caption.isBlank()) {
                StyledTextView(
                    block.caption,
                    style = MaterialTheme.typography.labelLarge,
                    forceRtl = message.forceRtl,
                )
                Spacer(Modifier.height(5.dp))
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.horizontalScroll(rememberScrollState())) {
                    block.rows.take(ContentLimits.MAX_ROWS).forEachIndexed { rowIndex, row ->
                        if (rowIndex > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.padding(if (block.compact) 7.dp else 10.dp)) {
                            row.take(ContentLimits.MAX_COLUMNS).forEach { cell ->
                                StyledTextView(
                                    cell.content,
                                    modifier = Modifier.width(if (block.compact) 104.dp else 126.dp).padding(horizontal = 6.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (cell.isHeader) FontWeight.SemiBold else FontWeight.Normal,
                                    ),
                                    forceRtl = message.forceRtl,
                                )
                            }
                        }
                    }
                }
            }
        }

        is Block.Details -> {
            var expanded by rememberSaveable(message.chat.account, message.chat.chat, message.id, message.revision, block.id) {
                mutableStateOf(false)
            }
            DetailsContainer(
                title = StyledText.plain(block.title),
                expanded = expanded,
                onToggle = { expanded = !expanded },
                message = message,
                children = block.children,
                onAction = onAction,
                depth = depth,
                duration = duration,
            )
        }

        is Block.RichDetails -> {
            var expanded by rememberSaveable(message.chat.account, message.chat.chat, message.id, message.revision, block.id) {
                mutableStateOf(block.initiallyOpen)
            }
            DetailsContainer(
                title = block.header,
                expanded = expanded,
                onToggle = { expanded = !expanded },
                message = message,
                children = block.children,
                onAction = onAction,
                depth = depth,
                duration = duration,
            )
        }

        is Block.Buttons -> RenderButtonRow(block, message, onAction)

        is Block.Divider -> HorizontalDivider(
            modifier = Modifier.padding(vertical = 3.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        is Block.Math -> CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    block.expression,
                    Modifier.padding(11.dp).horizontalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        textDirection = TextDirection.Ltr,
                    ),
                )
            }
        }

        is Block.Media -> MediaCard(block.info, message.forceRtl)

        is Block.Gallery -> Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                block.children.take(ContentLimits.MAX_MEDIA).forEach { child ->
                    RenderBlock(child, message, onAction, depth + 1)
                }
                if (!block.caption.isBlank()) {
                    StyledTextView(
                        block.caption,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        forceRtl = message.forceRtl,
                    )
                }
            }
        }

        is Block.Unsupported -> Text(
            stringResource(R.string.unsupported),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PlainTable(block: Block.Table) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            if (block.headers.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(Modifier.padding(10.dp)) {
                        block.headers.take(ContentLimits.MAX_COLUMNS).forEach {
                            Text(
                                it.take(256),
                                Modifier.width(118.dp).padding(horizontal = 6.dp),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
            block.rows.take(ContentLimits.MAX_ROWS).forEach { row ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.padding(10.dp)) {
                    row.take(ContentLimits.MAX_COLUMNS).forEach {
                        Text(it.take(512), Modifier.width(118.dp).padding(horizontal = 6.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsContainer(
    title: StyledText,
    expanded: Boolean,
    onToggle: () -> Unit,
    message: BotMessage,
    children: List<Block>,
    onAction: (ActionTicket) -> Unit,
    depth: Int,
    duration: Int,
) {
    Column(
        Modifier.animateContentSize(tween(duration)),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        OutlinedButton(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
        ) {
            StyledTextView(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, forceRtl = message.forceRtl)
            BotGlyph(
                if (expanded) Glyph.UP else Glyph.DOWN,
                stringResource(if (expanded) R.string.collapse else R.string.expand),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (expanded) {
            children.take(ContentLimits.MAX_BLOCKS).forEach {
                RenderBlock(it, message, onAction, depth + 1)
            }
        }
    }
}

@Composable
internal fun RenderButtonRow(
    block: Block.Buttons,
    message: BotMessage,
    onAction: (ActionTicket) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (block.buttons.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        block.buttons.take(8).forEach { button ->
            val colors = when (button.style) {
                ButtonVisualStyle.DANGER -> ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
                ButtonVisualStyle.SUCCESS -> ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                ButtonVisualStyle.PRIMARY -> ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                ButtonVisualStyle.LINK, ButtonVisualStyle.DEFAULT -> ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                enabled = button.enabled && button.payload != ActionPayload.Unsupported,
                onClick = { onAction(ActionTicket(message.chat, message.id, message.revision, button.id)) },
                colors = colors,
                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp),
            ) {
                Text(
                    button.label.take(256),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MediaCard(info: MediaInfo, forceRtl: Boolean?) {
    val kind = stringResource(
        when (info.kind) {
            MediaKind.PHOTO -> R.string.rich_media_photo
            MediaKind.VIDEO -> R.string.rich_media_video
            MediaKind.ANIMATION -> R.string.rich_media_animation
            MediaKind.AUDIO -> R.string.rich_media_audio
            MediaKind.VOICE_NOTE -> R.string.rich_media_voice
            MediaKind.DOCUMENT -> R.string.rich_media_document
            MediaKind.MAP -> R.string.rich_media_map
            MediaKind.EMBEDDED -> R.string.rich_media_embedded
            MediaKind.CHAT_LINK -> R.string.rich_media_chat_link
        },
    )
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(kind, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            info.title.takeIf { it.isNotBlank() && it !in setOf("Embedded", "Map", "Voice") }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            val details = buildList {
                info.durationSeconds?.takeIf { it > 0 }?.let { add(formatDuration(it)) }
                info.size?.takeIf { it > 0 }?.let { add(formatSize(it)) }
                if (info.kind == MediaKind.MAP && info.latitude != null && info.longitude != null) {
                    add("%.5f, %.5f".format(info.latitude, info.longitude))
                }
            }.joinToString(" · ")
            if (details.isNotBlank()) {
                Text(details, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!info.caption.isBlank()) {
                StyledTextView(
                    info.caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    forceRtl = forceRtl,
                )
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    return if (minutes > 0) "%d:%02d".format(minutes, rest) else "0:%02d".format(rest)
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
