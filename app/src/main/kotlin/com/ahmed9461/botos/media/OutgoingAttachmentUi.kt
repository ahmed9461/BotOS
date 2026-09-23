package com.ahmed9461.botos.media

import android.content.ActivityNotFoundException
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahmed9461.botos.R
import com.ahmed9461.botos.model.ChatKey
import com.ahmed9461.botos.data.UploadStatus
import com.ahmed9461.botos.telegram.runtime.AttachmentKind
import java.io.File

@Composable
internal fun OutgoingAttachmentHost(chat: ChatKey?, vm: OutgoingAttachmentViewModel = viewModel(),
    content: @Composable (Boolean, (AttachmentKind) -> Unit, Int?) -> Unit) {
    val preview by vm.preview.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val caption by vm.caption.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val monitor by vm.monitor.collectAsStateWithLifecycle()
    val voice by vm.voice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    VoiceForegroundGuard(vm::cancelVoice)
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), vm::picked)
    val video = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), vm::picked)
    val audio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), vm::picked)
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), vm::picked)
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), vm::voicePermission)
    val target = (LocalContext.current.applicationContext as com.ahmed9461.botos.BotOsApplication)
        .telegramUploads.captureTarget()
    val visible = target?.takeIf { it.chat == chat }
    LaunchedEffect(voice?.target, visible) {
        if (voice != null && voice?.target != visible) vm.cancelVoice()
    }
    val records = monitor.records.filter { visible != null && it.accountKey == visible.chat.account && it.chatId == visible.chatId }
    val status = when {
        monitor.storageError -> R.string.outgoing_unavailable
        records.any { it.status == UploadStatus.UNKNOWN } -> R.string.outgoing_uncertain
        records.any { it.status == UploadStatus.FAILED } -> R.string.outgoing_failed
        records.any { it.status in setOf(UploadStatus.STAGED, UploadStatus.ATTEMPTED, UploadStatus.PENDING) } -> R.string.outgoing_sending
        else -> notice
    }
    val onAttach: (AttachmentKind) -> Unit = { kind ->
        if (kind == AttachmentKind.VOICE) {
            if (vm.requestVoice()) try {
                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    vm.voicePermission(true)
                } else microphone.launch(Manifest.permission.RECORD_AUDIO)
            } catch (_: Exception) { vm.voicePermission(false) }
        } else if (vm.begin(kind)) try {
            when (kind) {
                AttachmentKind.PHOTO -> photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                AttachmentKind.VIDEO -> video.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                AttachmentKind.AUDIO -> audio.launch(arrayOf("audio/*"))
                AttachmentKind.DOCUMENT -> file.launch(arrayOf("*/*"))
                AttachmentKind.VOICE -> Unit
            }
        } catch (_: ActivityNotFoundException) { vm.pickerFailed() }
        catch (_: SecurityException) { vm.pickerFailed() }
    }
    content(visible != null && !busy && preview == null && voice == null, onAttach,
        if (visible == null) null else status)
    voice?.takeIf { it.target == visible && it.phase != VoicePhase.PERMISSION }?.let { recording ->
        OutgoingVoiceDialog(recording, vm::stopVoice, vm::cancelVoice)
    }
    preview?.takeIf { it.target.chat == chat && it.target == visible }?.let { outgoing ->
        OutgoingPreviewDialog(outgoing, caption, busy, vm::editCaption, vm::send, vm::cancel)
    }
}

/** The same observer is used by the real host and the device lifecycle regression. */
@Composable
internal fun VoiceForegroundGuard(onCancel: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current
    val cancel by rememberUpdatedState(onCancel)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) cancel()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); cancel() }
    }
}

@Composable
internal fun OutgoingVoiceDialog(state: VoiceUiState, onStop: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.outgoing_record_voice)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.outgoing_to, state.target.username),
                    style = MaterialTheme.typography.titleSmall)
                Text(stringResource(when (state.phase) {
                    VoicePhase.STARTING -> R.string.outgoing_record_starting
                    VoicePhase.FINISHING -> R.string.outgoing_record_finishing
                    else -> R.string.outgoing_recording
                }), style = MaterialTheme.typography.bodyMedium)
                Text("%02d:%02d".format(state.seconds / 60, state.seconds % 60),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("outgoing-voice-timer"))
                if (state.phase == VoicePhase.STARTING || state.phase == VoicePhase.FINISHING) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton(onClick = onStop, enabled = state.phase == VoicePhase.RECORDING,
            modifier = Modifier.testTag("outgoing-voice-stop")) { Text(stringResource(R.string.outgoing_record_stop)) } },
        dismissButton = { TextButton(onClick = onCancel,
            modifier = Modifier.testTag("outgoing-voice-cancel")) { Text(stringResource(R.string.cancel)) } },
        modifier = Modifier.testTag("outgoing-voice"),
    )
}

@Composable
internal fun OutgoingPreviewDialog(preview: OutgoingPreview, caption: String, busy: Boolean,
    onCaption: (String) -> Unit, onSend: () -> Unit, onCancel: () -> Unit) {
    val kind = when (preview.attachment.kind) {
        AttachmentKind.PHOTO -> R.string.rich_media_photo
        AttachmentKind.VIDEO -> R.string.rich_media_video
        AttachmentKind.AUDIO -> R.string.rich_media_audio
        AttachmentKind.VOICE -> R.string.rich_media_voice
        AttachmentKind.DOCUMENT -> R.string.rich_media_document
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onCancel() },
        title = { Text(stringResource(R.string.outgoing_preview)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.outgoing_to, preview.target.username),
                    style = MaterialTheme.typography.titleSmall)
                Text(stringResource(kind) + " · " + stringResource(R.string.outgoing_size_kb,
                    (preview.staged.bytes + 1023) / 1024), style = MaterialTheme.typography.bodySmall)
                preview.image?.let { bitmap -> Image(bitmap.asImageBitmap(), stringResource(kind),
                    Modifier.fillMaxWidth().heightIn(max = 150.dp).testTag("outgoing-image"),
                    contentScale = ContentScale.Fit) }
                if (preview.attachment.kind == AttachmentKind.AUDIO ||
                    preview.attachment.kind == AttachmentKind.VOICE) AudioAttachmentPreview(preview.staged.path)
                if (preview.fileFallback) Text(stringResource(R.string.outgoing_as_file),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = caption, onValueChange = onCaption, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("outgoing-caption"),
                    label = { Text(stringResource(R.string.outgoing_caption)) }, maxLines = 3,
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content))
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onSend, enabled = !busy,
            modifier = Modifier.testTag("outgoing-confirm")) { Text(stringResource(R.string.send)) } },
        dismissButton = { TextButton(onClick = onCancel, enabled = !busy,
            modifier = Modifier.testTag("outgoing-cancel")) { Text(stringResource(R.string.cancel)) } },
        modifier = Modifier.testTag("outgoing-preview"),
    )
}

@Composable
private fun AudioAttachmentPreview(path: String) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var playback by remember(path) { mutableStateOf<LocalMediaPlayback?>(null) }
    var playing by remember(path) { mutableStateOf(false) }
    DisposableEffect(path, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) {
            playback?.close(); playback = null; playing = false
        } }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            playback?.close(); playback = null; playing = false
        }
    }
    TextButton(onClick = {
        try {
            val player = playback ?: LocalMediaPlayback(context,
                DecodedMedia.Playback(File(path), "audio/mp4")).also { playback = it }
            if (player.player.isPlaying) { player.player.pause(); playing = false }
            else { player.play(); playing = true }
        } catch (_: Exception) { playback?.close(); playback = null; playing = false }
    }, modifier = Modifier.testTag("outgoing-listen")) {
        Text(stringResource(if (playing) R.string.outgoing_pause else R.string.outgoing_listen))
    }
}
