package com.ahmed9461.botos.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import android.util.AtomicFile
import com.ahmed9461.botos.model.SavedBot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

/** Local bookmark decoration. Never a Telegram profile change or part of a native session. */
class LocalAvatarStore(private val directory: File) {
    private val mutex = Mutex()
    private var initialized = false
    private var allowed = emptySet<String>()
    private val _versions = MutableStateFlow<Map<String, String>>(emptyMap())
    val versions = _versions.asStateFlow()

    /** Called only with a successfully loaded workspace, never a loading/error placeholder. */
    suspend fun synchronize(keys: Set<String>) = withContext(Dispatchers.IO) {
        require(keys.size <= 100 && keys.all { it.length in 1..128 })
        mutex.withLock {
            prepare()
            allowed = keys.toSet()
            val keep = keys.associateBy { filename(it) }
            directory.listFiles().orEmpty().filter { it.name.matches(Regex("[a-f0-9]{64}\\.jpg(?:\\.bak|\\.new)?")) }
                .forEach { file ->
                    val base = file.name.removeSuffix(".bak").removeSuffix(".new")
                    if (base !in keep) AtomicFile(File(directory, base)).delete()
                }
            _versions.value = keys.mapNotNull { key ->
                val file = atomic(key)
                if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) null
                else runCatching { key to digest(file) }.getOrNull()
            }.toMap()
        }
    }

    suspend fun replace(key: String, open: () -> InputStream) = withContext(Dispatchers.IO) {
        mutex.withLock {
            prepare()
            require(key in allowed) { "Unknown bookmark" }
            val source = File.createTempFile("avatar-import-", ".tmp", directory)
            try {
                open().use { input -> source.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_INPUT) throw IOException("Avatar input exceeds limit")
                        output.write(buffer, 0, count)
                    }
                    if (total == 0L) throw IOException("Empty avatar input")
                } }
                val bitmap = AvatarDecoder.decode(source, 512) ?: throw IOException("Invalid avatar image")
                try {
                    coroutineContext.ensureActive()
                    val file = atomic(key)
                    val stream = file.startWrite()
                    try {
                        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream))
                        if (stream.channel.size() > MAX_STORED) throw IOException("Avatar output exceeds limit")
                        file.finishWrite(stream)
                    } catch (failure: Exception) { file.failWrite(stream); throw failure }
                    _versions.value = _versions.value + (key to digest(file))
                } finally { bitmap.recycle() }
            } finally { source.delete() }
        }
    }

    suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            prepare()
            require(key in allowed) { "Unknown bookmark" }
            atomic(key).delete()
            _versions.value = _versions.value - key
        }
    }

    suspend fun decode(key: String, expectedVersion: String): Bitmap? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (key !in allowed || _versions.value[key] != expectedVersion) return@withLock null
            // Serialized with AtomicFile writers; no partially written picture is decoded.
            atomic(key).openRead().use { }
            AvatarDecoder.decode(atomic(key).baseFile, DISPLAY_SIDE)
        }
    }

    private fun prepare() {
        if (initialized) return
        check(directory.isDirectory || directory.mkdirs())
        directory.listFiles().orEmpty().filter { it.name.startsWith("avatar-import-") && it.name.endsWith(".tmp") }.forEach { it.delete() }
        initialized = true
    }
    private fun atomic(key: String) = AtomicFile(File(directory, filename(key)))
    private fun filename(key: String): String {
        require(key.length in 1..128)
        return hex(MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))) + ".jpg"
    }
    private fun digest(file: AtomicFile): String {
        val hash = MessageDigest.getInstance("SHA-256")
        var total = 0L
        file.openRead().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_STORED) throw IOException("Invalid stored avatar")
                hash.update(buffer, 0, count)
            }
        }
        if (total == 0L) throw IOException("Empty stored avatar")
        return hex(hash.digest())
    }
    companion object {
        const val MAX_INPUT = 16L * 1024 * 1024
        const val MAX_STORED = 512L * 1024
        const val DISPLAY_SIDE = 160
        fun key(bot: SavedBot): String = bot.id + "/" + bot.username
        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    }
}

/** Bounds are checked before pixel allocation, including on API26/27. */
object AvatarDecoder {
    fun decode(file: File, side: Int): Bitmap? {
        require(side in 1..512)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0 || width.toLong() * height > 100_000_000L) return null
        val scale = minOf(1.0, side.toDouble() / max(width, height))
        val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
        if (Build.VERSION.SDK_INT >= 28) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val factor = minOf(1.0, side.toDouble() / max(info.size.width, info.size.height))
                decoder.setTargetSize((info.size.width * factor).roundToInt().coerceAtLeast(1),
                    (info.size.height * factor).roundToInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            }
        }
        var sample = 1
        while (max(width, height) / sample > side * 2) sample *= 2
        val original = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: return null
        val scaled = Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true)
        if (scaled !== original) original.recycle()
        val orientation = runCatching { ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
        val matrix = Matrix().apply { when (orientation) {
            2 -> setScale(-1f, 1f)
            3 -> setRotate(180f)
            4 -> setScale(1f, -1f)
            5 -> { setRotate(90f); postScale(-1f, 1f) }
            6 -> setRotate(90f)
            7 -> { setRotate(-90f); postScale(-1f, 1f) }
            8 -> setRotate(-90f)
        } }
        val rotated = Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true)
        if (rotated !== scaled) scaled.recycle()
        return rotated
    }
}
