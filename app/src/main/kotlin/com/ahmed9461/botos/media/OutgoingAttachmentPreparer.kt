package com.ahmed9461.botos.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.ahmed9461.botos.data.StagedOutgoingMedia
import com.ahmed9461.botos.telegram.runtime.AttachmentKind
import com.ahmed9461.botos.telegram.runtime.AttachmentTarget
import com.ahmed9461.botos.telegram.runtime.PreparedAttachment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal data class OutgoingPreview(
    val target: AttachmentTarget,
    val staged: StagedOutgoingMedia,
    val attachment: PreparedAttachment,
    val image: Bitmap? = null,
    val fileFallback: Boolean = false,
) {
    override fun toString() = "OutgoingPreview(kind=${attachment.kind}, bytes=${staged.bytes})"
}

/** All decoding and probing happens off Main and only from an app-private, quota-checked copy. */
internal class OutgoingAttachmentPreparer(context: Context, private val uploads: TelegramUploads) {
    private val resolver = context.applicationContext.contentResolver

    suspend fun prepare(target: AttachmentTarget, uri: Uri, requested: AttachmentKind): OutgoingPreview {
        require(uri.scheme == "content")
        val name = withContext(Dispatchers.IO) {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }?.take(160) ?: "attachment.bin"
        }
        return prepare(target, name, requested) { resolver.openInputStream(uri) ?: throw IOException("Attachment unavailable") }
    }

    /** Test seam uses the same private staging and probe as ContentResolver. */
    internal suspend fun prepare(target: AttachmentTarget, name: String, requested: AttachmentKind,
        open: () -> InputStream): OutgoingPreview = withContext(Dispatchers.IO) {
        require(requested != AttachmentKind.VOICE)
        val original = uploads.stage(name, open)
        if (requested != AttachmentKind.PHOTO) {
            val video = if (requested == AttachmentKind.VIDEO) videoInfo(original.path, name) else null
            val audio = if (requested == AttachmentKind.AUDIO) audioInfo(original.path, name) else null
            val kind = when (requested) {
                AttachmentKind.VIDEO -> if (video != null) AttachmentKind.VIDEO else AttachmentKind.DOCUMENT
                AttachmentKind.AUDIO -> if (audio != null) AttachmentKind.AUDIO else AttachmentKind.DOCUMENT
                else -> AttachmentKind.DOCUMENT
            }
            val file = PreparedAttachment(original.path, kind, original.bytes,
                width = video?.first ?: 0, height = video?.second ?: 0,
                duration = video?.third ?: audio ?: 0,
                fileName = File(original.path).name.substringAfter('-', "attachment.bin"))
            return@withContext OutgoingPreview(target, original, file,
                image = if (video != null && maxOf(video.first, video.second) <= 1920) videoFrame(original.path) else null,
                fileFallback = kind == AttachmentKind.DOCUMENT &&
                requested != AttachmentKind.DOCUMENT)
        }
        var photo: StagedOutgoingMedia? = null
        var originalRemoved = false
        try {
            val source = File(original.path)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w !in 1..100_000 || h !in 1..100_000 || w.toLong() * h > 100_000_000L ||
                maxOf(w, h).toLong() > minOf(w, h).toLong() * 20L) throw IOException("Invalid photo dimensions")
            var sample = 1
            while (maxOf(w, h) / sample > 3200) sample *= 2
            val decoded = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: throw IOException("Photo unavailable")
            val side = maxOf(decoded.width, decoded.height)
            val output = try {
                if (side > 1600) {
                    val factor = 1600.0 / side
                    Bitmap.createScaledBitmap(decoded, (decoded.width * factor).toInt().coerceAtLeast(1),
                        (decoded.height * factor).toInt().coerceAtLeast(1), true)
                } else decoded
            } finally { if (side > 1600) decoded.recycle() }
            try {
                if (maxOf(output.width, output.height) > 1600 ||
                    maxOf(output.width, output.height) > minOf(output.width, output.height) * 20 ||
                    output.width + output.height > 10_000) throw IOException("Photo limits exceeded")
                val bytes = ByteArrayOutputStream().use { out ->
                    if (!output.compress(Bitmap.CompressFormat.JPEG, 85, out)) throw IOException("Photo encoding failed")
                    out.toByteArray()
                }
                if (bytes.size !in 1..10 * 1024 * 1024) throw IOException("Photo too large")
                uploads.discardPreview(original)
                originalRemoved = true
                photo = uploads.stage("photo.jpg") { ByteArrayInputStream(bytes) }
                val scaled = Bitmap.createScaledBitmap(output,
                    (output.width * minOf(1.0, 320.0 / maxOf(output.width, output.height))).toInt().coerceAtLeast(1),
                    (output.height * minOf(1.0, 320.0 / maxOf(output.width, output.height))).toInt().coerceAtLeast(1), true)
                val preview = if (scaled === output) output.copy(Bitmap.Config.ARGB_8888, false) else scaled
                OutgoingPreview(target, checkNotNull(photo), PreparedAttachment(checkNotNull(photo).path,
                    AttachmentKind.PHOTO, checkNotNull(photo).bytes, output.width, output.height), preview)
            } finally { output.recycle() }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            photo?.let { withContext(NonCancellable) { uploads.discardPreview(it) } }
            throw failure
        } finally { if (!originalRemoved) withContext(NonCancellable) { uploads.discardPreview(original) } }
    }

    private fun videoInfo(path: String, name: String): Triple<Int, Int, Int>? {
        if (!name.endsWith(".mp4", ignoreCase = true)) return null
        return try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(path)
                if (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) != "video/mp4") return null
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    ?.takeIf { it in 1..3_600_000L } ?: return null
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(path)
                    var video: Pair<Int, Int>? = null
                    for (i in 0 until extractor.trackCount) {
                        val track = extractor.getTrackFormat(i)
                        when (track.getString(MediaFormat.KEY_MIME)) {
                            "video/avc" -> {
                                if (video != null) return null
                                val width = track.getInteger(MediaFormat.KEY_WIDTH)
                                val height = track.getInteger(MediaFormat.KEY_HEIGHT)
                                if (width !in 1..65536 || height !in 1..65536) return null
                                video = width to height
                            }
                            "audio/mp4a-latm" -> Unit
                            else -> return null
                        }
                    }
                    video?.let { Triple(it.first, it.second, ((duration + 999) / 1000).toInt()) }
                } finally { extractor.release() }
            } finally { retriever.release() }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }

    private fun videoFrame(path: String): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(path)
                val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
                val side = maxOf(frame.width, frame.height)
                if (side <= 320) frame else try {
                    val ratio = 320.0 / side
                    Bitmap.createScaledBitmap(frame, (frame.width * ratio).toInt().coerceAtLeast(1),
                        (frame.height * ratio).toInt().coerceAtLeast(1), true)
                } finally { frame.recycle() }
            } finally { retriever.release() }
        } catch (_: Exception) { null }
    }

    private fun audioInfo(path: String, name: String): Int? {
        if (!name.endsWith(".mp3", true) && !name.endsWith(".m4a", true)) return null
        return try {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(path)
                if (extractor.trackCount != 1) return null
                val mime = extractor.getTrackFormat(0).getString(MediaFormat.KEY_MIME)
                if (mime != "audio/mpeg" && mime != "audio/mp4a-latm") return null
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(path)
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                        ?.takeIf { it in 1..3_600_000L } ?: return null
                    ((duration + 999) / 1000).toInt()
                } finally { retriever.release() }
            } finally { extractor.release() }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }
}
