package com.ahmed9461.botos.media

import android.content.ActivityNotFoundException
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import com.ahmed9461.botos.design.BotGlyph
import com.ahmed9461.botos.design.Glyph
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OutgoingVoiceDialog(state: VoiceUiState, onStop: () -> Unit, onCancel: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onCancel, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).testTag("outgoing-voice"),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.outgoing_record_voice), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.outgoing_to, state.target.username),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    BotGlyph(Glyph.MIC, modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            Text("%02d:%02d".format(java.util.Locale.ROOT, state.seconds / 60, state.seconds % 60),
                style = MaterialTheme.typography.displaySmall.copy(textDirection = TextDirection.Ltr),
                modifier = Modifier.testTag("outgoing-voice-timer"))
            Text(stringResource(when (state.phase) {
                VoicePhase.STARTING -> R.string.outgoing_record_starting
                VoicePhase.FINISHING -> R.string.outgoing_record_finishing
                else -> R.string.voice_recording_hint
            }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (state.phase == VoicePhase.STARTING || state.phase == VoicePhase.FINISHING) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("outgoing-voice-cancel")) {
                    BotGlyph(Glyph.TRASH, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.cancel))
                }
                Button(onClick = onStop, enabled = state.phase == VoicePhase.RECORDING,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("outgoing-voice-stop")) {
                    BotGlyph(Glyph.STOP, modifier = Modifier.size(18.dp), tint = if (state.phase == VoicePhase.RECORDING)
                        MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.voice_finish))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OutgoingPreviewDialog(preview: OutgoingPreview, caption: String, busy: Boolean,
    onCaption: (String) -> Unit, onSend: () -> Unit, onCancel: () -> Unit) {
    val currentBusy by rememberUpdatedState(busy)
    val kind = when (preview.attachment.kind) {
        AttachmentKind.PHOTO -> R.string.rich_media_photo
        AttachmentKind.VIDEO -> R.string.rich_media_video
        AttachmentKind.AUDIO -> R.string.rich_media_audio
        AttachmentKind.VOICE -> R.string.rich_media_voice
        AttachmentKind.DOCUMENT -> R.string.rich_media_document
    }
    ModalBottomSheet(onDismissRequest = { if (!busy) onCancel() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
            confirmValueChange = { !currentBusy || it != SheetValue.Hidden }), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(horizontal = 20.dp).padding(bottom = 16.dp).testTag("outgoing-preview"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.attachment_sheet_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.outgoing_to, preview.target.username), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                preview.image?.let { bitmap ->
                    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val fitted = fitMedia(bitmap.width, bitmap.height, maxWidth.value, 230f)
                        Image(bitmap.asImageBitmap(), stringResource(kind), Modifier.size(fitted.width.dp, fitted.height.dp)
                            .testTag("outgoing-image"), contentScale = ContentScale.Fit)
                    }
                }
                if (preview.attachment.kind == AttachmentKind.AUDIO || preview.attachment.kind == AttachmentKind.VOICE) {
                    AudioAttachmentPreview(preview.staged.path)
                }
                val name = preview.attachment.fileName.takeIf { it.isNotBlank() }
                if (name != null) Text(name, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(stringResource(kind) + " · " + stringResource(R.string.outgoing_size_kb, (preview.staged.bytes + 1023) / 1024),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (preview.fileFallback) Text(stringResource(R.string.outgoing_as_file),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = caption, onValueChange = onCaption, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("outgoing-caption"), shape = RoundedCornerShape(16.dp),
                    label = { Text(stringResource(R.string.outgoing_caption)) }, maxLines = 3,
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content))
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("outgoing-cancel")) {
                    Text(stringResource(R.string.cancel))
                }
                Button(onClick = onSend, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("outgoing-confirm")) {
                    BotGlyph(Glyph.SEND, modifier = Modifier.size(20.dp), tint = if (busy) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.send))
                }
            }
        }
    }
}

@Composable
private fun AudioAttachmentPreview(path: String) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var playback by remember(path) { mutableStateOf<LocalMediaPlayback?>(null) }
    var playing by remember(path) { mutableStateOf(false) }
    var failed by remember(path) { mutableStateOf(false) }
    val active = playback
    DisposableEffect(active, lifecycle) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayerError(error: PlaybackException) { failed = true; playing = false }
        }
        active?.player?.addListener(listener)
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) {
            active?.close(); playback = null; playing = false
        } }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            if (active != null && !active.released) active.player.removeListener(listener)
            active?.close()
        }
    }
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            TextButton(onClick = {
                try {
                    if (failed) { playback?.close(); playback = null; failed = false }
                    val player = playback ?: LocalMediaPlayback(context,
                        DecodedMedia.Playback(File(path), "audio/mp4")).also { playback = it }
                    if (player.player.isPlaying) player.player.pause() else player.play()
                } catch (_: Exception) { playback?.close(); playback = null; playing = false; failed = true }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("outgoing-listen")) {
                BotGlyph(if (playing) Glyph.PAUSE else Glyph.PLAY, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(if (playing) R.string.outgoing_pause else R.string.outgoing_listen))
            }
            if (failed) Text(stringResource(R.string.media_playback_error), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
        }
    }
}
