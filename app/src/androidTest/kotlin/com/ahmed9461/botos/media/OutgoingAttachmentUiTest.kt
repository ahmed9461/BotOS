package com.ahmed9461.botos.media

import android.graphics.Bitmap
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.io.PlatformTestStorageRegistry
import com.ahmed9461.botos.data.StagedOutgoingMedia
import com.ahmed9461.botos.design.BotOsTheme
import com.ahmed9461.botos.model.ChatKey
import com.ahmed9461.botos.model.ThemeMode
import com.ahmed9461.botos.telegram.runtime.AttachmentKind
import com.ahmed9461.botos.telegram.runtime.AttachmentTarget
import com.ahmed9461.botos.telegram.runtime.PreparedAttachment
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/** Fake pixels and a fixed target; no account, file provider or real microphone is used. */
@RunWith(AndroidJUnit4::class)
class OutgoingAttachmentUiTest {
    companion object {
        @JvmStatic @BeforeClass fun arabicBeforeActivity() {
            if (Build.VERSION.SDK_INT >= 33) {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                instrumentation.runOnMainSync {
                    instrumentation.targetContext.getSystemService(LocaleManager::class.java)
                        .applicationLocales = LocaleList.forLanguageTags("ar")
                }
                instrumentation.waitForIdleSync()
            }
        }
    }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    private fun screenshot(name: String) {
        ui.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: error("Device screenshot unavailable")
        try {
            PlatformTestStorageRegistry.getInstance().openOutputFile("$name.png").use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bitmap.recycle() }
    }

    @Test fun previewShowsActualPixelsTargetCaptionAndCancellationInLightAndDarkRtl() {
        val image = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff65518f.toInt()) }
        val target = AttachmentTarget(ChatKey("42:3", "100:7"), 100, 3, 7, "fixture_bot")
        val staged = StagedOutgoingMedia("fixture", "/private/fixture-photo.jpg", 4_096)
        val file = PreparedAttachment(staged.path, AttachmentKind.PHOTO, staged.bytes, 120, 80)
        val preview = OutgoingPreview(target, staged, file, image)
        var mode by mutableStateOf(ThemeMode.LIGHT)
        var caption by mutableStateOf("")
        var shown by mutableStateOf(true)
        var sends = 0
        ui.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                BotOsTheme(mode, true) {
                    if (shown) OutgoingPreviewDialog(preview, caption, false, { caption = it }, { sends++ }, { shown = false })
                }
            }
        }
        ui.onNodeWithTag("outgoing-image").assertIsDisplayed()
        ui.onNodeWithTag("outgoing-confirm").assertIsDisplayed()
        screenshot("outgoing-preview-light-ar")
        ui.runOnIdle { mode = ThemeMode.DARK }
        screenshot("outgoing-preview-dark-ar")
        ui.onNodeWithTag("outgoing-caption").performTextInput("وصف")
        ui.onNodeWithTag("outgoing-cancel").performClick()
        ui.onNodeWithTag("outgoing-preview").assertDoesNotExist()
        ui.runOnIdle { assertTrue(caption.isNotEmpty()); assertTrue(sends == 0) }
        image.recycle()
    }
}
