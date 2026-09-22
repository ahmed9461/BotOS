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

@Composable
internal fun LiveBotPanel(
    state: ConversationState, draft: String, onDraft: (String) -> Unit,
    onSend: () -> Unit, onStart: () -> Unit, onAction: (ActionTicket) -> Unit,
    onReload: () -> Unit, onAccount: () -> Unit, onOpenTelegram: () -> Unit,
    onDismiss: () -> Unit, onConfirmUrl: () -> Unit,
    onStopPending: (Long) -> Unit = {},
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
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 34.dp).testTag("live-toolbar"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onStart,
                        enabled = !state.busy,
                        modifier = Modifier.heightIn(min = 34.dp).testTag("live-start"),
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
                    ) {
                        Text(stringResource(R.string.live_start), style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(
                        onClick = onReload,
                        enabled = !state.busy,
                        modifier = Modifier.size(34.dp).testTag("live-reload"),
                    ) {
                        BotGlyph(
                            Glyph.SPACE,
                            stringResource(R.string.account_retry),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

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
                LiveComposer(draft, onDraft, onSend, state.busy)
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

@Composable
private fun LiveComposer(
    draft: String,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    busy: Boolean,
) {
    // BotOsApp owns IME/system insets. This composer must not add them again.
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            BasicTextField(
                value = draft,
                onValueChange = onDraft,
                enabled = !busy,
                maxLines = 4,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textDirection = TextDirection.Content,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp).testTag("live-input"),
                decorationBox = { field ->
                    Box(
                        Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (draft.isEmpty()) {
                            Text(
                                stringResource(R.string.live_message_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        field()
                    }
                },
            )
        }
        FilledIconButton(
            onClick = onSend,
            enabled = draft.isNotBlank() && !busy,
            modifier = Modifier.size(46.dp).testTag("live-send"),
            shape = RoundedCornerShape(16.dp),
        ) {
            BotGlyph(Glyph.SEND, stringResource(R.string.send), modifier = Modifier.size(20.dp))
        }
    }
}
