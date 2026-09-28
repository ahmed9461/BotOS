package com.ahmed9461.botos.media

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahmed9461.botos.model.MediaInfo
import com.ahmed9461.botos.model.MediaKind
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

internal fun withMediaDirectory(block: (File) -> Unit) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val root = Files.createTempDirectory(context.cacheDir.toPath(), "media-fixture-").toFile()
    try { block(root) } finally { root.deleteRecursively() }
}
internal fun testPicture(root: File, name: String = "picture.png", width: Int = 80, height: Int = 60): File {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.rgb(104, 115, 170))
    val file = File(root, name)
    try { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
    finally { bitmap.recycle() }
    return file
}
internal fun testSticker(): String = """{"v":"5.7.4","w":128,"h":128,"fr":30,"ip":0,"op":30,"assets":[],"layers":[{"ty":1,"ind":1,"nm":"solid","sw":128,"sh":128,"sc":"#7567aa","ip":0,"op":30,"st":0,"sr":1,"ks":{"o":{"a":0,"k":100},"r":{"a":0,"k":0},"p":{"a":0,"k":[0,0,0]},"a":{"a":0,"k":[0,0,0]},"s":{"a":0,"k":[100,100,100]}}}]}"""

@RunWith(AndroidJUnit4::class)
class MediaDecoderTest {
    private fun rejected(block: () -> Unit) { assertTrue(runCatching(block).isFailure) }
    @Test fun imageDecodingIsRealAndRespectsTargetDimensions() = withMediaDirectory { root ->
        val file = testPicture(root, width = 1800, height = 1000)
        val result = MediaDecoder.decode(root, file.path, MediaInfo(MediaKind.PHOTO, fileId = 1, mimeType = "image/png")) as DecodedMedia.Picture
        assertTrue(result.bitmap.width <= 768 && result.bitmap.height <= 768)
        assertTrue(result.bitmap.width > result.bitmap.height)
        assertEquals(Color.rgb(104, 115, 170), result.bitmap.getPixel(0, 0))
    }
    @Test fun pathTraversalSymlinkAndEmptyOrOversizedFileAreRejected() = withMediaDirectory { root ->
        val nested = File(root, "allowed").apply { mkdir() }
        val outside = testPicture(root)
        rejected { MediaDecoder.validatedFile(nested, outside.path, MediaDecoder.IMAGE_BYTES) }
        val link = File(nested, "link.png")
        Files.createSymbolicLink(link.toPath(), outside.toPath())
        rejected { MediaDecoder.validatedFile(nested, link.path, MediaDecoder.IMAGE_BYTES) }
        val empty = File(nested, "empty").apply { writeBytes(byteArrayOf()) }
        rejected { MediaDecoder.validatedFile(nested, empty.path, 10) }
        rejected { MediaDecoder.validatedFile(root, outside.path, 1) }
    }
    @Test fun vectorTgsIsDecodedIntoAnActualComposition() = withMediaDirectory { root ->
        val file = File(root, "sticker.tgs")
        GZIPOutputStream(file.outputStream()).use { it.write(testSticker().toByteArray()) }
        val result = MediaDecoder.decode(root, file.path, MediaInfo(MediaKind.ANIMATION, fileId = 1, mimeType = "application/x-tgsticker")) as DecodedMedia.Sticker
        assertTrue(result.composition.bounds.width() > 0)
        assertEquals(1, result.composition.layers.size)
    }
    @Test fun tgsExpansionAndUntrustedAssetsAreRejected() = withMediaDirectory { root ->
        val bomb = File(root, "expanded.tgs")
        GZIPOutputStream(bomb.outputStream()).use { it.write(ByteArray(MediaDecoder.TGS_JSON_BYTES + 1) { 32 }) }
        rejected { MediaDecoder.decodeTgs(bomb) }
        rejected { MediaDecoder.validateTgsJson(testSticker().replace("\"assets\":[]", "\"assets\":[{\"id\":\"x\",\"p\":\"file:///secret\"}]")) }
        rejected { MediaDecoder.validateTgsJson(testSticker().replace("\"op\":30", "\"op\":900000")) }
    }
    @Test fun recursivePrecompositionAndDeepJsonAreRejected() {
        val recursive = """{"v":"5.7.4","w":128,"h":128,"fr":30,"ip":0,"op":30,"layers":[{"ty":0,"refId":"a"}],"assets":[{"id":"a","layers":[{"ty":0,"refId":"a"}]}]}"""
        rejected { MediaDecoder.validateTgsJson(recursive) }
        rejected { MediaDecoder.validateTgsJson("[".repeat(40) + "0" + "]".repeat(40)) }
    }
    @Test fun corruptImageFailsWithoutSubstitutingASuccessfulPlaceholder() = withMediaDirectory { root ->
        val file = File(root, "bad.png").apply { writeText("not an image") }
        rejected { MediaDecoder.decode(root, file.path, MediaInfo(MediaKind.PHOTO, fileId = 1)) }
        assertFalse(MediaDecoder.supported(MediaInfo(MediaKind.DOCUMENT, fileId = 1)))
    }
}
