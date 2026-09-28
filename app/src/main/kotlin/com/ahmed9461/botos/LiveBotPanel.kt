package com.ahmed9461.botos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.ahmed9461.botos.design.BotGlyph
import com.ahmed9461.botos.design.Glyph
import com.ahmed9461.botos.model.ActionTicket
import com.ahmed9461.botos.model.Block
import com.ahmed9461.botos.telegram.runtime.ConversationIssue
import com.ahmed9461.botos.telegram.runtime.ConversationState
import com.ahmed9461.botos.telegram.runtime.ConversationStatus
import com.ahmed9461.botos.telegram.runtime.AttachmentKind

@Composable
internal fun LiveBotPanel(
    state: ConversationState, draft: String, onDraft: (String) -> Unit,
    onSend: () -> Unit, onStart: () -> Unit, onAction: (ActionTicket) -> Unit,
    onReload: () -> Unit, onAccount: () -> Unit, onOpenTelegram: () -> Unit,
    onDismiss: () -> Unit, onConfirmUrl: () -> Unit,
    onStopPending: (Long) -> Unit = {},
    attachmentEnabled: Boolean = false,
    onAttach: (AttachmentKind) -> Unit = {},
    attachmentNotice: Int? = null,
) {
    Column(
        Modifier.fillMaxSize().testTag("live-bot-panel"),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        when (state.status) {
            ConversationStatus.SIGN_IN -> {
                InfoCard(stringResource(R.string.live_sign_in_hint))
                Button(
                    onClick = onAccount,
                    modifier = Modifier.fillMaxWidth().testTag("live-connect-account"),
                ) { Text(stringResource(R.string.account_connect)) }
            }

            ConversationStatus.LOADING -> Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }

            ConversationStatus.NOT_A_BOT, ConversationStatus.FAILED -> {
                InfoCard(
                    stringResource(
                        if (state.status == ConversationStatus.NOT_A_BOT) {
                            R.string.live_not_bot
                        } else {
                            R.string.live_loading_error
                        },
                    ),
                )
                OutlinedButton(onClick = onReload, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.account_retry))
                }
                TextButton(onClick = onOpenTelegram) { Text(stringResource(R.string.open_telegram)) }
            }

            ConversationStatus.READY -> {
                state.timeline?.let {
                    MessageTimelineView(it, onAction, Modifier.weight(1f), state.pending, onStopPending, state.busy)
                } ?: Spacer(Modifier.weight(1f))

                state.keyboard?.let { message ->
                    val rows = message.blocks.filterIsInstance<Block.Buttons>()
                    if (rows.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 168.dp)
                                .testTag("live-reply-keyboard"),
                        ) {
                            Column(
                                Modifier.verticalScroll(rememberScrollState()).padding(6.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                rows.forEach { row ->
                                    RenderButtonRow(row, message, onAction, Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }

                state.issue?.let { issue ->
                    Text(
                        stringResource(
                            when (issue) {
                                ConversationIssue.UNCERTAIN -> R.string.live_uncertain
                                ConversationIssue.STALE_ACTION -> R.string.live_stale
                                ConversationIssue.UNSUPPORTED -> R.string.live_unsupported
                                ConversationIssue.OVERFLOW -> R.string.live_reload_needed
                                ConversationIssue.CONNECTION -> R.string.live_send_error
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                attachmentNotice?.let { message ->
                    Text(stringResource(message), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("outgoing-status"))
                }
                LiveComposer(draft, onDraft, onSend, state.busy, attachmentEnabled, onAttach, onStart, onReload)
            }
        }
    }

    val proposedUrl = state.proposedUrl
    val notice = state.notice
    if (proposedUrl != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.live_open_link)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    notice?.let { Text(it) }
                    Text(proposedUrl, style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmUrl) { Text(stringResource(R.string.live_open)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            },
        )
    } else if (!notice.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            text = { Text(notice) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveComposer(
    draft: String, onDraft: (String) -> Unit, onSend: () -> Unit, busy: Boolean,
    attachmentEnabled: Boolean, onAttach: (AttachmentKind) -> Unit,
    onStart: () -> Unit, onReload: () -> Unit,
) {
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    var options by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var commands by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(attachmentEnabled, busy) {
        if (!attachmentEnabled || busy) options = false
        if (busy) commands = false
    }
    // Only the app owns IME insets. A modal sheet owns its separate window.
    Row(Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 5.dp).testTag("composer-bar"),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        IconButton(onClick = { focus.clearFocus(); options = true }, enabled = attachmentEnabled && !busy,
            modifier = Modifier.size(48.dp).testTag("outgoing-add")) {
            BotGlyph(Glyph.ADD, stringResource(R.string.outgoing_add), modifier = Modifier.size(26.dp),
                tint = if (attachmentEnabled && !busy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(25.dp), color = MaterialTheme.colorScheme.surface) {
            Row(verticalAlignment = Alignment.Bottom) {
                BasicTextField(value = draft, onValueChange = onDraft, enabled = !busy, maxLines = 5,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface,
                        textDirection = TextDirection.Content),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("live-input"),
                    decorationBox = { field ->
                        Box(Modifier.padding(start = 15.dp, end = 2.dp, top = 12.dp, bottom = 12.dp), contentAlignment = Alignment.CenterStart) {
                            if (draft.isEmpty()) Text(stringResource(R.string.live_message_hint),
                                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            field()
                        }
                    })
                Box {
                    IconButton(onClick = { commands = true }, enabled = !busy,
                        modifier = Modifier.size(48.dp).testTag("live-commands")) {
                        BotGlyph(Glyph.COMMAND, stringResource(R.string.chat_commands), modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(commands, onDismissRequest = { commands = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.live_start)) },
                            leadingIcon = { BotGlyph(Glyph.PLAY) },
                            onClick = { commands = false; onStart() }, modifier = Modifier.testTag("live-start"))
                        DropdownMenuItem(text = { Text(stringResource(R.string.account_retry)) },
                            leadingIcon = { BotGlyph(Glyph.REFRESH) },
                            onClick = { commands = false; onReload() }, modifier = Modifier.testTag("live-reload"))
                    }
                }
            }
        }
        val hasText = draft.isNotBlank()
        val enabled = !busy && (hasText || attachmentEnabled)
        FilledIconButton(onClick = { if (hasText) onSend() else { focus.clearFocus(); onAttach(AttachmentKind.VOICE) } },
            enabled = enabled, modifier = Modifier.size(48.dp).testTag(if (hasText) "live-send" else "live-voice"),
            shape = androidx.compose.foundation.shape.CircleShape) {
            BotGlyph(if (hasText) Glyph.SEND else Glyph.MIC,
                stringResource(if (hasText) R.string.send else R.string.outgoing_record_voice),
                modifier = Modifier.size(23.dp), tint = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (options) {
        ModalBottomSheet(onDismissRequest = { options = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp).testTag("attachment-sheet"),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.attachment_sheet_title), style = MaterialTheme.typography.titleLarge)
                val choices = listOf(
                    Triple(AttachmentKind.PHOTO, R.string.outgoing_choose_photo, Glyph.PHOTO),
                    Triple(AttachmentKind.VIDEO, R.string.outgoing_choose_video, Glyph.VIDEO),
                    Triple(AttachmentKind.DOCUMENT, R.string.outgoing_choose_file, Glyph.FILE),
                    Triple(AttachmentKind.AUDIO, R.string.outgoing_choose_audio, Glyph.AUDIO),
                    Triple(AttachmentKind.VOICE, R.string.outgoing_record_voice, Glyph.MIC))
                choices.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (kind, label, glyph) ->
                            Surface(onClick = { options = false; onAttach(kind) },
                                modifier = Modifier.weight(1f).testTag("outgoing-option-${kind.name.lowercase()}"),
                                shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Column(Modifier.heightIn(min = 96.dp).padding(horizontal = 6.dp, vertical = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    BotGlyph(glyph, modifier = Modifier.size(27.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(stringResource(label), style = MaterialTheme.typography.labelLarge,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                }
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
