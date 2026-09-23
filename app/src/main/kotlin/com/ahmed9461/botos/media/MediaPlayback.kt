package com.ahmed9461.botos.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.FileDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/** Only a validated private local file can reach this player. No network data-source factory. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class LocalMediaPlayback(context: Context, media: DecodedMedia.Playback) : AutoCloseable {
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(FileDataSource.Factory()))
        .build()
    var released: Boolean = false
        private set
    init {
        player.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), !media.mutedLoop)
        player.setHandleAudioBecomingNoisy(true)
        player.volume = if (media.mutedLoop) 0f else 1f
        player.repeatMode = if (media.mutedLoop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(media.file)))
        player.prepare()
    }
    fun play() { if (!released) player.play() }
    override fun close() {
        if (!released) { released = true; player.release() }
    }
}
