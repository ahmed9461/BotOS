package com.ahmed9461.botos

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Disposable emulator marker only; never opens Telegram or reads a real user's account. */
@RunWith(AndroidJUnit4::class)
class OwnerUpdateProbeTest {
    @Test fun appPrivateMarkerSurvivesReplacement() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val stage = InstrumentationRegistry.getArguments().getString("botos.updateStage") ?: "roundtrip"
        require(stage in setOf("write", "verify", "roundtrip"))
        if (stage != "roundtrip") assertEquals("com.ahmed9461.botos.app", context.packageName)
        val file = File(context.filesDir, "botos-update-probe.txt")
        val prefs = context.getSharedPreferences("botos_update_probe", 0)
        val marker = "BotOS synthetic update persistence v1"
        if (stage != "verify") {
            file.writeText(marker)
            assertTrue(prefs.edit().putString("marker", marker).commit())
        }
        assertTrue("Private file was removed", file.isFile)
        assertEquals(marker, file.readText())
        assertEquals(marker, prefs.getString("marker", null))
        if (stage != "write") {
            assertTrue(file.delete())
            assertTrue(prefs.edit().clear().commit())
        }
    }
}
