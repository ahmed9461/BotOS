package com.ahmed9461.botos.media

import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class MediaPlaybackTest {
    @Test fun localPcmAudioPreparesAndReleasesWithoutAnyNetworkSource() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "audio-test-").toFile()
        try {
            val pcm = ByteArray(16000) // One second of synthetic silence at 8 kHz / 16-bit mono.
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVEfmt ".toByteArray())
                putInt(16); putShort(1); putShort(1); putInt(8000); putInt(16000)
                putShort(2); putShort(16); put("data".toByteArray()); putInt(pcm.size)
            }.array()
            val file = File(directory, "silence.wav").apply { outputStream().use { it.write(header); it.write(pcm) } }
            withContext(Dispatchers.Main) {
                val playback = LocalMediaPlayback(context, DecodedMedia.Playback(file, "audio/wav"))
                try {
                    withTimeout(10_000) { while (playback.player.playbackState != Player.STATE_READY) { assertNull(playback.player.playerError); delay(20) } }
                    assertTrue(playback.player.duration > 0)
                    playback.play()
                } finally { playback.close() }
                assertTrue(playback.released)
                playback.close(); playback.play() // Idempotent disposal, no use of the released player.
            }
        } finally { directory.deleteRecursively() }
    }
    @Test fun syntheticMp4PreparesWithExpectedVideoTrack() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "video-test-").toFile()
        try {
            for ((asset, mime) in listOf("synthetic-video.mp4" to "video/mp4", "synthetic-sticker.webm" to "video/webm")) {
            val file = File(directory, asset)
            instrumentation.context.assets.open(asset).use { input -> file.outputStream().use { output -> input.copyTo(output) } }
            withContext(Dispatchers.Main) {
                val playback = LocalMediaPlayback(context, DecodedMedia.Playback(file, mime, mutedLoop = mime == "video/webm"))
                try {
                    withTimeout(10_000) { while (playback.player.playbackState != Player.STATE_READY) { assertNull(playback.player.playerError); delay(20) } }
                    assertTrue(playback.player.duration in 500..2000)
                    assertTrue(playback.player.currentTracks.groups.any { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO })
                } finally { playback.close() }
            }
            }
        } finally { directory.deleteRecursively() }
    }
}
