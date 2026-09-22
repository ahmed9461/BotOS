package com.ahmed9461.botos.media

import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.airbnb.lottie.LottieDrawable
import com.ahmed9461.botos.R
import com.ahmed9461.botos.design.LocalMotionMillis
import com.ahmed9461.botos.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Presentation-only bridge. Composables do not create downloads, open sessions or resolve paths. */
internal data class MediaUiActions(
    val snapshot: MediaSnapshot,
    val request: (MediaReference, Boolean) -> Unit,
    val cancel: (MediaReference) -> Unit,
    val open: (MediaReference) -> Unit,
)
internal val LocalMediaUi = staticCompositionLocalOf<MediaUiActions?> { null }

@Composable
internal fun ReceivedMediaHost(chat: ChatKey?, vm: ReceivedMediaViewModel = viewModel(), content: @Composable () -> Unit) {
    val snapshot by vm.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(chat, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vm.controller.close()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); vm.controller.leave() }
    }
    val visible = remember(chat, snapshot) {
        snapshot.copy(items = snapshot.items.filterKeys { it.chat == chat }, selected = snapshot.selected?.takeIf { it.chat == chat })
    }
    CompositionLocalProvider(LocalMediaUi provides MediaUiActions(visible, vm.controller::request, vm.controller::cancel, vm.controller::open)) {
        content()
    }
    visible.selected?.let { ref ->
        visible.items[ref]?.content?.let { media ->
            key(ref) { ReceivedMediaViewer(media, vm.controller::close) }
        }
    }
}

/** Returns false for kinds this slice cannot open, preserving the existing explicit fallback. */
@Composable
internal fun ReceivedMediaItem(reference: MediaReference, info: MediaInfo): Boolean {
    val ui = LocalMediaUi.current ?: return false
    if (!MediaDecoder.supported(info) || reference.messageId == Long.MIN_VALUE) return false
    var visible by remember(reference) { mutableStateOf(false) }
    LaunchedEffect(reference, visible) { if (visible) ui.request(reference, true) }
    val state = ui.snapshot.items[reference]
    val kindLabel = mediaLabel(info)
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(13.dp),
        modifier = Modifier.fillMaxWidth().onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            visible = bounds.width > 1f && bounds.height > 1f
        }.testTag("received-media-${reference.blockId}")) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val decoded = state?.content
            if (decoded is DecodedMedia.Picture) {
                val ratio = (decoded.bitmap.width.toFloat() / decoded.bitmap.height).coerceIn(.65f, 1.8f)
                Image(decoded.bitmap.asImageBitmap(), kindLabel,
                    Modifier.fillMaxWidth().aspectRatio(ratio).heightIn(max = 260.dp)
                        .clickable { ui.open(reference) }.testTag("received-image-${reference.blockId}"),
                    contentScale = ContentScale.Fit)
            } else if (decoded is DecodedMedia.Sticker) {
                StickerImage(decoded, animate = false, Modifier.fillMaxWidth().height(170.dp)
                    .clickable { ui.open(reference) }.testTag("received-sticker-${reference.blockId}"))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(kindLabel, style = MaterialTheme.typography.labelLarge)
                    val detail = listOfNotNull(info.durationSeconds?.takeIf { it > 0 }?.let { "%d:%02d".format(it / 60, it % 60) },
                        info.size?.takeIf { it > 0 }?.let { mediaSize(it) }).joinToString(" · ")
                    if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (state?.stage) {
                    MediaStage.LOADING -> TextButton(onClick = { ui.cancel(reference) }, modifier = Modifier.testTag("media-cancel-${reference.blockId}")) {
                        Text(stringResource(R.string.cancel))
                    }
                    MediaStage.READY -> TextButton(onClick = { ui.open(reference) }, modifier = Modifier.testTag("media-open-${reference.blockId}")) {
                        Text(stringResource(if (decoded is DecodedMedia.Playback) R.string.media_play else R.string.media_view))
                    }
                    else -> TextButton(onClick = { ui.request(reference, false) }, modifier = Modifier.testTag("media-download-${reference.blockId}")) {
                        Text(stringResource(if (state?.stage == MediaStage.FAILED) R.string.media_retry else R.string.media_download))
                    }
                }
            }
            if (state?.stage == MediaStage.LOADING) {
                val progress = state.progress
                if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
            if (state?.stage == MediaStage.FAILED) Text(stringResource(R.string.media_unavailable),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    return true
}

@Composable
private fun mediaLabel(info: MediaInfo): String = stringResource(when {
    info.mimeType == "application/x-tgsticker" || info.mimeType == "image/webp" || (info.kind == MediaKind.ANIMATION && info.mimeType == "video/webm") -> R.string.media_sticker
    info.kind == MediaKind.PHOTO -> R.string.rich_media_photo
    info.kind == MediaKind.VIDEO -> R.string.rich_media_video
    info.kind == MediaKind.ANIMATION -> R.string.rich_media_animation
    info.kind == MediaKind.VOICE_NOTE -> R.string.rich_media_voice
    else -> R.string.rich_media_audio
})
private fun mediaSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
internal fun ReceivedMediaViewer(media: DecodedMedia, onClose: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val close by rememberUpdatedState(onClose)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) close() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.96f).heightIn(max = 600.dp).testTag("media-viewer"),
            shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose, modifier = Modifier.testTag("media-viewer-close")) { Text(stringResource(R.string.close)) }
                }
                when (media) {
                    is DecodedMedia.Picture -> if (media.animated && Build.VERSION.SDK_INT >= 28) AnimatedPicture(media) else ZoomPicture(media)
                    is DecodedMedia.Sticker -> StickerImage(media, animate = LocalMotionMillis.current != 0,
                        Modifier.fillMaxWidth().height(330.dp))
                    is DecodedMedia.Playback -> PlaybackView(media)
                }
            }
        }
    }
}

@Composable
private fun ZoomPicture(media: DecodedMedia.Picture) {
    var scale by remember(media) { mutableFloatStateOf(1f) }
    val transform = rememberTransformableState { zoom, _, _ -> scale = (scale * zoom).coerceIn(1f, 4f) }
    Box(Modifier.fillMaxWidth().height(380.dp).clipToBounds().transformable(transform)) {
        Image(media.bitmap.asImageBitmap(), stringResource(R.string.rich_media_photo),
            Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale), contentScale = ContentScale.Fit)
    }
}

@Composable
private fun StickerImage(media: DecodedMedia.Sticker, animate: Boolean, modifier: Modifier) {
    val drawable = remember(media) { LottieDrawable().apply {
        setSafeMode(true)
        setImageAssetDelegate { null }
        setComposition(media.composition)
        repeatCount = if (animate) LottieDrawable.INFINITE else 0
        progress = 0f
    } }
    DisposableEffect(drawable, animate) {
        if (animate) drawable.playAnimation() else drawable.pauseAnimation()
        onDispose { drawable.cancelAnimation() }
    }
    AndroidView(factory = { context -> ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER; setImageDrawable(drawable)
    } }, update = { it.setImageDrawable(drawable) }, modifier = modifier)
}

@Composable
private fun AnimatedPicture(media: DecodedMedia.Picture) {
    val animate = LocalMotionMillis.current != 0
    var image by remember(media) { mutableStateOf<Drawable?>(null) }
    var failed by remember(media) { mutableStateOf(false) }
    LaunchedEffect(media) {
        try {
            image = withContext(Dispatchers.IO) {
                require(Build.VERSION.SDK_INT >= 28)
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(media.file)) { decoder, info, _ ->
                    require(info.size.width in 1..32768 && info.size.height in 1..32768)
                    val ratio = minOf(1.0, 768.0 / maxOf(info.size.width, info.size.height))
                    decoder.setTargetSize((info.size.width * ratio).toInt().coerceAtLeast(1), (info.size.height * ratio).toInt().coerceAtLeast(1))
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    val drawable = image
    DisposableEffect(drawable, animate) {
        if (animate) (drawable as? Animatable)?.start()
        onDispose { (drawable as? Animatable)?.stop() }
    }
    if (drawable != null) AndroidView(factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        update = { it.setImageDrawable(drawable) }, modifier = Modifier.fillMaxWidth().height(350.dp))
    else if (failed) ZoomPicture(media)
    else Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun PlaybackView(media: DecodedMedia.Playback) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val playback = remember(media, context) { LocalMediaPlayback(context, media) }
    var error by remember(media) { mutableStateOf(false) }
    DisposableEffect(playback, owner) {
        val listener = object : Player.Listener {
            override fun onPlayerError(failure: PlaybackException) { error = true }
        }
        val lifecycle = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) playback.close()
        }
        playback.player.addListener(listener)
        owner.lifecycle.addObserver(lifecycle)
        playback.play()
        onDispose {
            owner.lifecycle.removeObserver(lifecycle)
            if (!playback.released) playback.player.removeListener(listener)
            playback.close()
        }
    }
    if (error) Text(stringResource(R.string.media_cannot_play), style = MaterialTheme.typography.bodyMedium)
    AndroidView(factory = { playerContext -> PlayerView(playerContext).apply {
        player = playback.player; useController = true
        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        controllerShowTimeoutMs = if (media.mimeType.startsWith("audio/")) 0 else 4000
    } }, modifier = Modifier.fillMaxWidth().height(if (media.mimeType.startsWith("audio/")) 140.dp else 330.dp),
        update = { it.player = playback.player })
}
