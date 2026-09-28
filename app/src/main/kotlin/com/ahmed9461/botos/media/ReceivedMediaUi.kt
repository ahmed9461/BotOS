package com.ahmed9461.botos.media

import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import com.ahmed9461.botos.design.BotGlyph
import com.ahmed9461.botos.design.Glyph
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
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
internal val MediaPanXKey = SemanticsPropertyKey<Float>("BotOSMediaPanX")
internal val MediaPanYKey = SemanticsPropertyKey<Float>("BotOSMediaPanY")
internal val MediaZoomScaleKey = SemanticsPropertyKey<Float>("BotOSMediaZoomScale")
internal val MediaFrameRenderedKey = SemanticsPropertyKey<Boolean>("BotOSMediaFrameRendered")
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
    val decoded = state?.takeIf { it.stage == MediaStage.READY }?.content
    BoxWithConstraints(Modifier.widthIn(max = 340.dp).onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        visible = bounds.width > 1f && bounds.height > 1f
    }.testTag("received-media-${reference.blockId}")) {
        when (decoded) {
            is DecodedMedia.Picture -> {
                val extent = fitMedia(decoded.bitmap.width, decoded.bitmap.height, maxWidth.value, 360f)
                Surface(onClick = { ui.open(reference) }, color = Color.Transparent, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(extent.width.dp, extent.height.dp).testTag("media-open-${reference.blockId}")) {
                    Image(decoded.bitmap.asImageBitmap(), kindLabel,
                        Modifier.fillMaxSize().testTag("received-image-${reference.blockId}"), contentScale = ContentScale.Fit)
                }
            }
            is DecodedMedia.Sticker -> {
                Surface(onClick = { ui.open(reference) }, color = Color.Transparent,
                    modifier = Modifier.size(minOf(maxWidth, 200.dp)).testTag("media-open-${reference.blockId}")) {
                    StickerImage(decoded, animate = false, Modifier.fillMaxSize().testTag("received-sticker-${reference.blockId}"))
                }
            }
            else -> Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.widthIn(min = 180.dp).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val glyph = when (info.kind) {
                            MediaKind.PHOTO -> Glyph.PHOTO
                            MediaKind.VIDEO, MediaKind.ANIMATION -> Glyph.VIDEO
                            MediaKind.VOICE_NOTE -> Glyph.MIC
                            else -> Glyph.AUDIO
                        }
                        BotGlyph(glyph, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(info.title.takeIf { it.isNotBlank() } ?: kindLabel,
                                style = MaterialTheme.typography.labelLarge, maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            val detail = listOfNotNull(info.durationSeconds?.takeIf { it > 0 }?.let { "%d:%02d".format(it / 60, it % 60) },
                                info.size?.takeIf { it > 0 }?.let { mediaSize(it) }).joinToString(" · ")
                            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        when (state?.stage) {
                            MediaStage.LOADING -> IconButton(onClick = { ui.cancel(reference) },
                                modifier = Modifier.size(48.dp).testTag("media-cancel-${reference.blockId}")) {
                                BotGlyph(Glyph.CLOSE, stringResource(R.string.cancel))
                            }
                            MediaStage.READY -> FilledTonalIconButton(onClick = { ui.open(reference) },
                                modifier = Modifier.size(48.dp).testTag("media-open-${reference.blockId}")) {
                                BotGlyph(Glyph.PLAY, stringResource(R.string.media_play))
                            }
                            else -> IconButton(onClick = { ui.request(reference, false) },
                                modifier = Modifier.size(48.dp).testTag("media-download-${reference.blockId}")) {
                                BotGlyph(if (state?.stage == MediaStage.FAILED) Glyph.REFRESH else Glyph.DOWN,
                                    stringResource(if (state?.stage == MediaStage.FAILED) R.string.media_retry else R.string.media_download))
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
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().testTag("media-viewer"), color = Color.Black, contentColor = Color.White) {
            // This is a separate window; app Scaffold insets do not apply here.
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose, modifier = Modifier.size(48.dp).testTag("media-viewer-close")) {
                        BotGlyph(Glyph.CLOSE, stringResource(R.string.close), tint = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                }
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds(), contentAlignment = Alignment.Center) {
                    when (media) {
                        is DecodedMedia.Picture -> if (media.animated && Build.VERSION.SDK_INT >= 28) AnimatedPicture(media) else ZoomPicture(media)
                        is DecodedMedia.Sticker -> StickerImage(media, animate = LocalMotionMillis.current != 0, Modifier.fillMaxSize())
                        is DecodedMedia.Playback -> PlaybackView(media)
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomPicture(media: DecodedMedia.Picture) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember(media, viewport) { mutableFloatStateOf(1f) }
    var offset by remember(media, viewport) { mutableStateOf(Offset.Zero) }
    fun clamp(value: Offset, zoom: Float): Offset {
        if (viewport.width == 0 || viewport.height == 0) return Offset.Zero
        val fitted = fitMedia(media.bitmap.width, media.bitmap.height, viewport.width.toFloat(), viewport.height.toFloat())
        val x = mediaPanLimit(fitted.width, viewport.width.toFloat(), zoom)
        val y = mediaPanLimit(fitted.height, viewport.height.toFloat(), zoom)
        return Offset(value.x.coerceIn(-x, x), value.y.coerceIn(-y, y))
    }
    fun reset() { scale = 1f; offset = Offset.Zero }
    val transform = rememberTransformableState { zoom, pan, _ ->
        val next = (scale * zoom).coerceIn(1f, 5f)
        offset = clamp(offset + pan, next)
        scale = next
    }
    val zoomIn = stringResource(R.string.media_zoom_in)
    val zoomReset = stringResource(R.string.media_zoom_reset)
    Box(Modifier.fillMaxSize().onSizeChanged { viewport = it }.clipToBounds()
        .testTag("zoom-picture").semantics {
            this[MediaZoomScaleKey] = scale
            this[MediaPanXKey] = offset.x
            this[MediaPanYKey] = offset.y
            stateDescription = "${(scale * 100).toInt()}%"
            customActions = listOf(CustomAccessibilityAction(zoomIn) { scale = (scale + 1).coerceAtMost(5f); true },
                CustomAccessibilityAction(zoomReset) { reset(); true })
        }.pointerInput(media, viewport) {
            detectTapGestures(onDoubleTap = { point ->
                if (scale > 1f) reset() else {
                    scale = 2.5f
                    val center = Offset(viewport.width / 2f, viewport.height / 2f)
                    offset = clamp((center - point) * (scale - 1f), scale)
                }
            })
        }.transformable(transform)) {
        Image(media.bitmap.asImageBitmap(), stringResource(R.string.rich_media_photo),
            Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            contentScale = ContentScale.Fit)
        FilledIconButton(onClick = { if (scale > 1f) reset() else scale = 2.5f },
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).size(48.dp).testTag("media-zoom"),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF292A30), contentColor = Color.White)) {
            BotGlyph(if (scale > 1f) Glyph.REFRESH else Glyph.ADD, if (scale > 1f) zoomReset else zoomIn, tint = Color.White)
        }
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

@androidx.annotation.RequiresApi(28)
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
        update = { it.setImageDrawable(drawable) }, modifier = Modifier.fillMaxSize())
    else if (failed) ZoomPicture(media)
    else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun PlaybackView(media: DecodedMedia.Playback) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val playback = remember(media, context) { LocalMediaPlayback(context, media) }
    var error by remember(media) { mutableStateOf(false) }
    var firstFrame by remember(media) { mutableStateOf(false) }
    DisposableEffect(playback, owner) {
        val listener = object : Player.Listener {
            override fun onPlayerError(failure: PlaybackException) { error = true }
            override fun onRenderedFirstFrame() { firstFrame = true }
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
    if (error) {
        Text(stringResource(R.string.media_cannot_play), Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium)
        return
    }
    AndroidView(factory = { playerContext -> PlayerView(playerContext).apply {
        player = playback.player; useController = true
        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        controllerShowTimeoutMs = if (media.mimeType.startsWith("audio/")) 0 else 4000
    } }, modifier = (if (media.mimeType.startsWith("audio/")) Modifier.fillMaxWidth().height(160.dp) else Modifier.fillMaxSize())
        .testTag("received-playback").semantics { this[MediaFrameRenderedKey] = firstFrame },
        update = { it.player = playback.player })
}
