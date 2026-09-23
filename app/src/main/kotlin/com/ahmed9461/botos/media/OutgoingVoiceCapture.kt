package com.ahmed9461.botos.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import com.ahmed9461.botos.data.StagedOutgoingMedia
import com.ahmed9461.botos.telegram.runtime.AttachmentKind
import com.ahmed9461.botos.telegram.runtime.AttachmentTarget
import com.ahmed9461.botos.telegram.runtime.PreparedAttachment
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal data class RecordedVoice(val file: File, val durationSeconds: Int)

/** One foreground recording at a time. The raw file is temporary and never belongs to the upload journal. */
internal interface VoiceCapture {
    suspend fun start()
    suspend fun finish(): RecordedVoice
    fun abort()
}

internal class AndroidVoiceCapture(context: Context) : VoiceCapture {
    private val app = context.applicationContext
    private val lock = Any()
    private var recorder: MediaRecorder? = null
    private var output: File? = null

    override suspend fun start(): Unit = withContext(Dispatchers.IO) {
        synchronized(lock) {
            check(recorder == null)
            val file = File.createTempFile("botos-voice-", ".m4a", app.cacheDir)
            @Suppress("DEPRECATION")
            val next = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(app) else MediaRecorder()
            try {
                next.setAudioSource(MediaRecorder.AudioSource.MIC)
                next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                next.setAudioChannels(1)
                next.setAudioSamplingRate(16_000)
                next.setAudioEncodingBitRate(32_000)
                next.setMaxDuration(60_000)
                next.setMaxFileSize(10L * 1024 * 1024)
                next.setOutputFile(file.absolutePath)
                next.prepare()
                next.start()
                recorder = next
                output = file
            } catch (failure: Exception) {
                next.release()
                file.delete()
                throw failure
            }
        }
    }

    override suspend fun finish(): RecordedVoice = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val active = recorder ?: throw IOException("No active recording")
            val file = output ?: throw IOException("Recording output unavailable")
            recorder = null
            output = null
            try {
                // stop() throws for a too-short or invalid recording; such a file is never staged.
                active.stop()
            } catch (failure: RuntimeException) {
                file.delete()
                throw IOException("Recording too short", failure)
            } finally { active.release() }
            try {
                RecordedVoice(file, duration(file))
            } catch (failure: Exception) {
                file.delete()
                throw failure
            }
        }
    }

    override fun abort() {
        synchronized(lock) {
            val active = recorder
            recorder = null
            val file = output
            output = null
            try { active?.reset() } catch (_: RuntimeException) { /* Release and discard below. */ }
            try { active?.release() } finally { file?.delete() }
        }
    }

    private fun duration(file: File): Int {
        if (file.length() !in 1..10L * 1024 * 1024) throw IOException("Invalid recording size")
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            if (extractor.trackCount != 1) throw IOException("Invalid voice tracks")
            val track = extractor.getTrackFormat(0)
            if (track.getString(MediaFormat.KEY_MIME) != "audio/mp4a-latm" ||
                track.getInteger(MediaFormat.KEY_CHANNEL_COUNT) != 1) throw IOException("Invalid voice format")
        } finally { extractor.release() }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
            val milliseconds = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?.takeIf { it in 1_000..60_000 } ?: throw IOException("Recording too short or long")
            return ((milliseconds + 999) / 1_000).toInt()
        } finally { retriever.release() }
    }
}

/** Stops capture, imports a private copy, then offers a preview. Only the user can queue it. */
internal class OutgoingVoiceSession(private val capture: VoiceCapture, private val uploads: TelegramUploads) {
    suspend fun complete(target: AttachmentTarget): OutgoingPreview {
        var clip: RecordedVoice? = null
        var staged: StagedOutgoingMedia? = null
        try {
            clip = capture.finish()
            if (uploads.captureTarget() != target) throw VoiceTargetChanged()
            staged = uploads.stage("voice.m4a") { checkNotNull(clip).file.inputStream() }
            if (uploads.captureTarget() != target) throw VoiceTargetChanged()
            val saved = checkNotNull(staged)
            val preview = OutgoingPreview(target, saved, PreparedAttachment(saved.path,
                AttachmentKind.VOICE, saved.bytes, duration = checkNotNull(clip).durationSeconds))
            staged = null
            return preview
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                clip?.file?.delete()
                try { staged?.let { uploads.discardPreview(it) } }
                finally { capture.abort() }
            }
        }
    }
}

internal class VoiceTargetChanged : IOException("Voice target changed")
