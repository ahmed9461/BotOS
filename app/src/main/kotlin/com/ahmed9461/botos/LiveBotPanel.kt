package com.ahmed9461.botos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.ahmed9461.botos.design.BotGlyph
import com.ahmed9461.botos.design.Glyph
import com.ahmed9461.botos.model.ActionTicket
import com.ahmed9461.botos.telegram.runtime.ConversationIssue
import com.ahmed9461.botos.telegram.runtime.ConversationState
import com.ahmed9461.botos.telegram.runtime.ConversationStatus

@Composable
internal fun LiveBotPanel(
    state: ConversationState, draft: String, onDraft: (String) -> Unit,
    onSend: () -> Unit, onStart: () -> Unit, onAction: (ActionTicket) -> Unit,
    onReload: () -> Unit, onAccount: () -> Unit, onOpenTelegram: () -> Unit,
    onDismiss: () -> Unit, onConfirmUrl: () -> Unit,
) {
    Column(Modifier.fillMaxSize().testTag("live-bot-panel"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state.status) {
            ConversationStatus.SIGN_IN -> {
                InfoCard(stringResource(R.string.live_sign_in_hint))
                Button(onClick = onAccount, modifier = Modifier.fillMaxWidth().testTag("live-connect-account")) { Text(stringResource(R.string.account_connect)) }
            }
            ConversationStatus.LOADING -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
            ConversationStatus.NOT_A_BOT, ConversationStatus.FAILED -> {
                InfoCard(stringResource(if (state.status == ConversationStatus.NOT_A_BOT) R.string.live_not_bot else R.string.live_loading_error))
                OutlinedButton(onClick = onReload, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.account_retry)) }
                TextButton(onClick = onOpenTelegram) { Text(stringResource(R.string.open_telegram)) }
            }
            ConversationStatus.READY -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.live_connected), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = onStart, enabled = !state.busy, modifier = Modifier.testTag("live-start")) { Text(stringResource(R.string.live_start)) }
                    IconButton(onClick = onReload, enabled = !state.busy) { BotGlyph(Glyph.SPACE, stringResource(R.string.account_retry), modifier = Modifier.size(18.dp)) }
                }
                state.timeline?.let { MessageTimelineView(it, onAction, Modifier.weight(1f)) } ?: Spacer(Modifier.weight(1f))
                state.keyboard?.let { message ->
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).testTag("live-reply-keyboard")) {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            message.blocks.forEach { RenderBlock(it, message, onAction, 0) }
                        }
                    }
                }
                state.issue?.let { issue ->
                    Text(stringResource(when (issue) {
                        ConversationIssue.UNCERTAIN -> R.string.live_uncertain
                        ConversationIssue.STALE_ACTION -> R.string.live_stale
                        ConversationIssue.UNSUPPORTED -> R.string.live_unsupported
                        ConversationIssue.OVERFLOW -> R.string.live_reload_needed
                        ConversationIssue.CONNECTION -> R.string.live_send_error
                    }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(draft, onDraft, modifier = Modifier.weight(1f).testTag("live-input"),
                        maxLines = 4, shape = RoundedCornerShape(22.dp), placeholder = { Text(stringResource(R.string.live_message_hint)) },
                        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content))
                    FilledIconButton(onClick = onSend, enabled = draft.isNotBlank() && !state.busy,
                        modifier = Modifier.size(52.dp).testTag("live-send"), shape = RoundedCornerShape(18.dp)) {
                        BotGlyph(Glyph.SEND, stringResource(R.string.send))
                    }
                }
            }
        }
    }
    val proposedUrl = state.proposedUrl
    val notice = state.notice
    if (proposedUrl != null) AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.live_open_link)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            notice?.let { Text(it) }
            Text(proposedUrl, style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
        } }, confirmButton = { TextButton(onClick = onConfirmUrl) { Text(stringResource(R.string.live_open)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
    else if (!notice.isNullOrBlank()) AlertDialog(onDismissRequest = onDismiss,
        text = { Text(notice) }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
}
