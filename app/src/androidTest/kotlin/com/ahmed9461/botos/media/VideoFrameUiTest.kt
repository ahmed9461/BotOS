package com.ahmed9461.botos.media

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.io.PlatformTestStorageRegistry
import com.ahmed9461.botos.design.BotOsTheme
import com.ahmed9461.botos.model.ThemeMode
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual production viewer, real decoded frames, synthetic files only. */
@RunWith(AndroidJUnit4::class)
class VideoFrameUiTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    private fun actualVideoFrame(asset: String, mime: String, evidence: String) = withMediaDirectory { root ->
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(root, asset)
        instrumentation.context.assets.open(asset).use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        var selected by mutableStateOf(true)
        val playback = DecodedMedia.Playback(file, mime, mutedLoop = mime == "video/webm")
        ui.setContent {
            BotOsTheme(ThemeMode.DARK, true) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground) {
                        if (selected) ReceivedMediaViewer(playback) { selected = false }
                    }
                }
            }
        }
        ui.onNodeWithTag("media-viewer").assertIsDisplayed()
        ui.waitUntil(15_000) {
            ui.onAllNodes(SemanticsMatcher.expectValue(MediaFrameRenderedKey, true)).fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithTag("received-playback").assert(SemanticsMatcher.expectValue(MediaFrameRenderedKey, true))
        ui.waitForIdle()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("No rendered-frame screenshot")
        try {
            PlatformTestStorageRegistry.getInstance().openOutputFile("$evidence.png").use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bitmap.recycle() }
        ui.onNodeWithTag("media-viewer-close").performClick()
        ui.onNodeWithTag("media-viewer").assertDoesNotExist()
        ui.onNodeWithTag("received-playback").assertDoesNotExist()
    }

    @Test fun mp4RendersAFrameInsideTheProductionViewer() {
        actualVideoFrame("synthetic-video.mp4", "video/mp4", "received-video-ar")
    }

    @Test fun webmRendersAFrameInsideTheProductionViewer() {
        actualVideoFrame("synthetic-sticker.webm", "video/webm", "received-webm-ar")
    }
}
