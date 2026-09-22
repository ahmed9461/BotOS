package com.ahmed9461.botos.media

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.io.PlatformTestStorageRegistry
import com.ahmed9461.botos.MessageTimelineView
import com.ahmed9461.botos.design.BotOsTheme
import com.ahmed9461.botos.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReceivedMediaUiTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val chat = ChatKey("42:1", "100:1")
    private val ref = MediaReference(chat, 1, 1, "image")
    private val info = MediaInfo(MediaKind.PHOTO, fileId = 7, size = 600,
        caption = StyledText.plain("صورة تجريبية داخل المحادثة"))
    private fun draw(theme: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
        ui.setContent {
            BotOsTheme(theme, true) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground) {
                        Box(Modifier.systemBarsPadding()) { content() }
                    }
                }
            }
        }
    }
    private fun screenshot(name: String) {
        ui.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: error("Missing screenshot")
        try { PlatformTestStorageRegistry.getInstance().openOutputFile("$name.png").use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        } } finally { bitmap.recycle() }
    }
    @Test fun photoReallyRendersInRichBubbleAndOpensThenClosesViewer() {
        val bitmap = Bitmap.createBitmap(320, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff9ea6d0.toInt()) }
        val picture = DecodedMedia.Picture(bitmap, File("/unused-fixture.png"))
        var selected by mutableStateOf(false)
        draw(ThemeMode.LIGHT) {
            val snapshot = MediaSnapshot(mapOf(ref to PresentedMedia(MediaStage.READY, content = picture)))
            CompositionLocalProvider(LocalMediaUi provides MediaUiActions(snapshot, { _, _ -> }, {}, { selected = true })) {
                MessageTimelineView(MessageTimeline(chat, listOf(BotMessage(1, chat, 1, listOf(Block.Media("image", info)), date = 1_789_000_000))), {})
            }
            if (selected) ReceivedMediaViewer(picture) { selected = false }
        }
        ui.onNodeWithTag("received-image-image").assertIsDisplayed()
        ui.onNodeWithText("صورة تجريبية داخل المحادثة").assertIsDisplayed()
        screenshot("received-media-light-ar")
        ui.onNodeWithTag("media-open-image").performClick()
        ui.onNodeWithTag("media-viewer").assertIsDisplayed()
        ui.onNodeWithTag("media-viewer-close").performClick()
        ui.onNodeWithTag("media-viewer").assertDoesNotExist()
    }
    @Test fun downloadProgressCancelAndFailureAreVisibleWithoutPretendingSuccess() {
        var cancelled = false
        var manual = false
        var current by mutableStateOf(PresentedMedia(MediaStage.LOADING, .5f))
        draw {
            CompositionLocalProvider(LocalMediaUi provides MediaUiActions(MediaSnapshot(mapOf(ref to current)),
                { _, auto -> if (!auto) manual = true }, { cancelled = true }, {})) { ReceivedMediaItem(ref, info) }
        }
        ui.onNodeWithTag("media-cancel-image").assertIsDisplayed().performClick()
        ui.runOnIdle { assertTrue(cancelled); current = PresentedMedia(MediaStage.FAILED) }
        ui.onNodeWithTag("media-open-image").assertDoesNotExist()
        ui.onNodeWithTag("media-download-image").performClick()
        ui.runOnIdle { assertTrue(manual) }
        screenshot("received-media-dark-ar")
    }
    @Test fun temporaryReplyNeverRequestsOrExposesAMediaPlayer() {
        var requested = 0
        var supported = true
        draw {
            CompositionLocalProvider(LocalMediaUi provides MediaUiActions(MediaSnapshot(), { _, _ -> requested++ }, {}, {})) {
                supported = ReceivedMediaItem(ref.copy(messageId = Long.MIN_VALUE), info)
            }
        }
        ui.onNodeWithTag("received-media-image").assertDoesNotExist()
        ui.runOnIdle { assertEquals(0, requested); assertFalse(supported) }
    }
    @Test fun realVectorStickerRendersInMessageAndOpensWithoutNetwork() = withMediaDirectory { root ->
        val file = File(root, "sticker.tgs")
        java.util.zip.GZIPOutputStream(file.outputStream()).use { it.write(testSticker().toByteArray()) }
        val sticker = MediaDecoder.decode(root, file.path, MediaInfo(MediaKind.ANIMATION, fileId = 7,
            mimeType = "application/x-tgsticker")) as DecodedMedia.Sticker
        var opened = false
        draw {
            CompositionLocalProvider(LocalMediaUi provides MediaUiActions(
                MediaSnapshot(mapOf(ref to PresentedMedia(MediaStage.READY, content = sticker))),
                { _, _ -> }, {}, { opened = true })) {
                ReceivedMediaItem(ref, info.copy(kind = MediaKind.ANIMATION, mimeType = "application/x-tgsticker"))
            }
        }
        ui.onNodeWithTag("received-sticker-image").assertIsDisplayed()
        ui.onNodeWithTag("media-open-image").performClick()
        ui.runOnIdle { assertTrue(opened) }
        screenshot("received-sticker-ar")
    }

}
