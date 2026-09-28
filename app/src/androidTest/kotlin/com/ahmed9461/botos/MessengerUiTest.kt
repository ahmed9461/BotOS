package com.ahmed9461.botos

import android.app.LocaleManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.io.PlatformTestStorageRegistry
import com.ahmed9461.botos.data.StoreSnapshot
import com.ahmed9461.botos.design.BotOsTheme
import com.ahmed9461.botos.media.*
import com.ahmed9461.botos.model.*
import com.ahmed9461.botos.telegram.runtime.*
import java.io.File
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

internal fun setMessengerTestArabic() {
    if (Build.VERSION.SDK_INT >= 33) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            instrumentation.targetContext.getSystemService(LocaleManager::class.java)
                .applicationLocales = LocaleList.forLanguageTags("ar")
        }
        instrumentation.waitForIdleSync()
    }
}

/** Synthetic inputs are confined to androidTest. Every surface below is the production component. */
@RunWith(AndroidJUnit4::class)
class MessengerUiTest {
    companion object { @JvmStatic @BeforeClass fun language() = setMessengerTestArabic() }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val chat = ChatKey("fixture", "100")
    private val bots = listOf(SavedBot("one", "first_bot", "المساعد الشخصي"),
        SavedBot("two", "second_bot", "الصور"), SavedBot("three", "study_bot", "الدراسة"),
        SavedBot("four", "files_bot", "الملفات"))
    private fun draw(mode: () -> ThemeMode = { ThemeMode.DARK }, content: @Composable () -> Unit) {
        ui.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                BotOsTheme(mode(), true) {
                    AppFrame(remember { SnackbarHostState() }, {}) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) { content() }
                    }
                }
            }
        }
    }
    private fun capture(name: String) {
        ui.waitForIdle()
        val bitmap = captureCommittedScreen()
        try { PlatformTestStorageRegistry.getInstance().openOutputFile("$name.png").use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        } } finally { bitmap.recycle() }
    }
    @Test fun realChatListSearchesSavedBotsAndSystemBackReturnsWithoutPreview() {
        var selected by mutableStateOf(WorkspaceViewModel.HOME)
        var mode by mutableStateOf(ThemeMode.LIGHT)
        draw({ mode }) {
            WorkspaceScreen(StoreSnapshot(Workspace(bots)), selected, false, { selected = it }, {}, {}, {}, { _, _ -> }, {}) {
                LiveBotPanel(ConversationState(it.username, ConversationStatus.READY, MessageTimeline(chat)),
                    "", {}, {}, {}, {}, {}, {}, {}, {}, {})
            }
        }
        ui.onNodeWithTag("chat-list").assertIsDisplayed()
        ui.onNodeWithTag("preview-mode").assertDoesNotExist()
        ui.onNodeWithTag("message-list").assertDoesNotExist()
        capture("chat-list-light-ar")
        ui.runOnIdle { mode = ThemeMode.DARK }
        capture("chat-list-dark-ar")
        ui.onNodeWithTag("chat-search").performTextInput("second")
        ui.onNodeWithTag("chat-row-one").assertDoesNotExist()
        ui.onNodeWithTag("chat-row-two").assertIsDisplayed().performClick()
        ui.onNodeWithTag("compact-bot-header").assertIsDisplayed()
        ui.onNodeWithTag("chat-list").assertDoesNotExist()
        ui.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        ui.onNodeWithTag("chat-list").assertIsDisplayed()
        ui.runOnIdle { assertEquals(WorkspaceViewModel.HOME, selected) }
    }
    @Test fun microphoneAndTextSendAreSeparateExplicitActionsAndPickerHasRealKinds() {
        var draft by mutableStateOf("")
        var sends = 0
        val choices = mutableListOf<AttachmentKind>()
        draw {
            LiveBotPanel(ConversationState("first_bot", ConversationStatus.READY, MessageTimeline(chat)), draft,
                { draft = it }, { sends++; draft = "" }, {}, {}, {}, {}, {}, {}, {},
                attachmentEnabled = true, onAttach = { choices += it })
        }
        ui.onNodeWithTag("live-voice").assertIsEnabled().performClick()
        ui.runOnIdle { assertEquals(listOf(AttachmentKind.VOICE), choices); assertEquals(0, sends) }
        ui.onNodeWithTag("live-input").performTextInput("رسالة")
        ui.onNodeWithTag("live-voice").assertDoesNotExist()
        ui.onNodeWithTag("live-send").assertIsEnabled().performClick()
        ui.onNodeWithTag("live-voice").assertIsDisplayed()
        ui.runOnIdle { assertEquals(1, sends) }
        ui.onNodeWithTag("outgoing-add").performClick()
        ui.onNodeWithTag("attachment-sheet").assertIsDisplayed()
        capture("media-picker-ar")
        ui.onNodeWithTag("outgoing-option-photo").performClick()
        ui.runOnIdle { assertEquals(listOf(AttachmentKind.VOICE, AttachmentKind.PHOTO), choices) }
    }
    @Test fun portraitPhotoHasNoOversizedFrameAndFullscreenZoomResetsPan() {
        val bitmap = Bitmap.createBitmap(320, 690, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(0xff31526a.toInt())
            drawRect(0f, 230f, 320f, 460f, Paint().apply { color = 0xffc7d8e2.toInt() })
        }
        val ref = MediaReference(chat, 7, 1, "portrait")
        val photo = DecodedMedia.Picture(bitmap, File("/unused-fixture.jpg"))
        val info = MediaInfo(MediaKind.PHOTO, width = 320, height = 690, fileId = 7,
            caption = StyledText.plain("صورة"))
        var open by mutableStateOf(false)
        draw {
            CompositionLocalProvider(LocalMediaUi provides MediaUiActions(
                MediaSnapshot(mapOf(ref to PresentedMedia(MediaStage.READY, content = photo))), { _, _ -> }, {}, { open = true })) {
                MessageTimelineView(MessageTimeline(chat, listOf(BotMessage(7, chat, 1, listOf(Block.Media("portrait", info)),
                    date = 1_789_000_000))), {})
            }
            if (open) ReceivedMediaViewer(photo) { open = false }
        }
        val image = ui.onNodeWithTag("received-image-portrait", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val bubble = ui.onNodeWithTag("message-bubble-7").fetchSemanticsNode().boundsInRoot
        val density = ui.activity.resources.displayMetrics.density
        assertEquals(320f / 690f, image.width / image.height, .005f)
        assertTrue("The bubble must follow photo width, not reserve a full-width inner card", bubble.width - image.width <= 10f * density)
        capture("media-portrait-ar")
        var windowWidth = 0
        var windowHeight = 0
        ui.activityRule.scenario.onActivity { windowWidth = it.window.decorView.width; windowHeight = it.window.decorView.height }
        ui.onNodeWithTag("media-open-portrait").performClick()
        val viewer = ui.onNodeWithTag("media-viewer").fetchSemanticsNode().boundsInWindow
        assertTrue(viewer.width >= windowWidth * .95f)
        assertTrue(viewer.height >= windowHeight * .95f)
        capture("media-fullscreen-ar")
        ui.onNodeWithTag("media-zoom").performClick()
        assertEquals(2.5f, ui.onNodeWithTag("zoom-picture").fetchSemanticsNode().config[MediaZoomScaleKey], .01f)
        ui.onNodeWithTag("zoom-picture").performTouchInput { swipeLeft() }
        assertTrue(ui.onNodeWithTag("zoom-picture").fetchSemanticsNode().config[MediaPanXKey] < 0f)
        ui.onNodeWithTag("media-zoom").performClick()
        val reset = ui.onNodeWithTag("zoom-picture").fetchSemanticsNode().config
        assertEquals(1f, reset[MediaZoomScaleKey], .01f)
        assertEquals(0f, reset[MediaPanXKey], .01f)
        assertEquals(0f, reset[MediaPanYKey], .01f)
        ui.onNodeWithTag("media-viewer-close").performClick()
        ui.onNodeWithTag("media-viewer").assertDoesNotExist()
    }
    @Test fun mediaGeometryPreservesPortraitLandscapeAndExtremeRatiosWithinBounds() {
        listOf(320 to 690, 1600 to 900, 400 to 400, 1 to 32768, 32768 to 1).forEach { (w, h) ->
            val extent = fitMedia(w, h, 320f, 360f)
            assertTrue(extent.width in 0f..320f && extent.height in 0f..360f)
            assertEquals(w.toDouble() / h, extent.width.toDouble() / extent.height,
                (w.toDouble() / h) * .00001)
        }
        assertEquals(MediaExtent(320f, 240f), fitMedia(null, null, 320f, 360f))
        assertEquals(MediaExtent(320f, 240f), fitMedia(0, -5, 320f, 360f))
        assertEquals(MediaExtent(0f, 0f), fitMedia(10, 10, 0f, 360f))
        assertThrows(IllegalArgumentException::class.java) { fitMedia(10, 10, Float.NaN, 300f) }
        assertEquals(0f, mediaPanLimit(100f, 200f, 1f), .001f)
        assertEquals(50f, mediaPanLimit(100f, 200f, 3f), .001f)
    }
    @Test fun largeTextAndNarrowChatKeepHeaderAndComposerControlsAccessible() {
        draw {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                Box(Modifier.width(320.dp).fillMaxHeight()) {
                    WorkspaceScreen(StoreSnapshot(Workspace(listOf(bots.first()))), "one", false, {}, {}, {}, {}, { _, _ -> }, {}) {
                        LiveBotPanel(ConversationState("first_bot", ConversationStatus.READY, MessageTimeline(chat)),
                            "", {}, {}, {}, {}, {}, {}, {}, {}, {}, attachmentEnabled = true)
                    }
                }
            }
        }
        ui.onNodeWithTag("compact-bot-header").assertIsDisplayed()
        ui.onNodeWithTag("chat-back").assertIsDisplayed()
        ui.onNodeWithTag("bot-switcher").assertIsDisplayed()
        ui.onNodeWithTag("live-input").assertIsDisplayed()
        val microphone = ui.onNodeWithTag("live-voice").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(microphone.width >= ui.activity.resources.displayMetrics.density * 47f)
        capture("messenger-large-text-ar")
    }
    @Test fun scrollingShowsAnExplicitJumpToLatestWithoutInventingUnreadCounts() {
        fun message(id: Int, outgoing: Boolean = false) = BotMessage(id.toLong(), chat, 1,
            listOf(Block.Paragraph("body-$id", "رسالة $id")), outgoing = outgoing,
            date = 1_789_000_000 + id.toLong())
        var messages by mutableStateOf((1..30).map { message(it, outgoing = it == 30) })
        draw { MessageTimelineView(MessageTimeline(chat, messages), {}) }
        ui.onNodeWithTag("message-list").performScrollToIndex(0)
        ui.onNodeWithTag("message-bubble-1").assertIsDisplayed()
        // Incoming content after our previous message must not move a reader away from history.
        ui.runOnIdle { messages = messages + message(31) }
        ui.onNodeWithTag("message-bubble-1").assertIsDisplayed()
        ui.onNodeWithTag("chat-jump-latest").assertIsDisplayed().performClick()
        ui.onNodeWithTag("message-bubble-31").assertIsDisplayed()
        ui.onNodeWithTag("chat-jump-latest").assertDoesNotExist()
        // A new explicit send still moves to the tail, and incoming content follows at the tail.
        ui.onNodeWithTag("message-list").performScrollToIndex(0)
        ui.runOnIdle { messages = messages + message(32, outgoing = true) }
        ui.onNodeWithTag("message-bubble-32").assertIsDisplayed()
        ui.runOnIdle { messages = messages + message(33) }
        ui.onNodeWithTag("message-bubble-33").assertIsDisplayed()
        ui.onNodeWithTag("chat-jump-latest").assertDoesNotExist()
        // Multiple layout passes must not turn following into a manual history-reading state.
        for (id in 34..37) {
            ui.runOnIdle { messages = messages + message(id) }
            ui.onNodeWithTag("message-bubble-$id").assertIsDisplayed()
            ui.onNodeWithTag("chat-jump-latest").assertDoesNotExist()
        }
        ui.onNodeWithTag("message-list").performScrollToIndex(0)
        ui.runOnIdle { messages = messages + message(38) }
        ui.onNodeWithTag("message-bubble-1").assertIsDisplayed()
        ui.onNodeWithTag("chat-jump-latest").assertIsDisplayed().performClick()
        ui.onNodeWithTag("message-bubble-38").assertIsDisplayed()
        ui.onNodeWithTag("chat-jump-latest").assertDoesNotExist()
    }
}
