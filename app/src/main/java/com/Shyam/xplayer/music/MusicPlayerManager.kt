package com.Shyam.xplayer.music

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.PowerManager
import java.io.File

data class MusicTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val path: String,
    val uri: Uri
)

object MusicPlayerManager {

    private var mediaPlayer: MediaPlayer? = null
    var playlist = ArrayList<MusicTrack>()
    var currentIndex = -1
    var isPlaying = false
        private set

    var onTrackChanged: ((MusicTrack?) -> Unit)? = null
    var onPlaybackStateChanged: ((Boolean) -> Unit)? = null

    fun getCurrentTrack(): MusicTrack? {
        return if (currentIndex in playlist.indices) playlist[currentIndex] else null
    }

    fun playTrack(context: Context, tracks: List<MusicTrack>, index: Int) {
        playlist.clear()
        playlist.addAll(tracks)
        currentIndex = index.coerceIn(0, playlist.size - 1)
        playCurrent(context)
    }

    private fun playCurrent(context: Context) {
        val track = getCurrentTrack() ?: return
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null

            mediaPlayer = MediaPlayer().apply {
                setWakeMode(context.applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                if (track.path.isNotEmpty() && File(track.path).exists()) {
                    setDataSource(track.path)
                } else {
                    setDataSource(context, track.uri)
                }
                prepare()
                start()
            }

            isPlaying = true
            onTrackChanged?.invoke(track)
            onPlaybackStateChanged?.invoke(true)

            mediaPlayer?.setOnCompletionListener {
                playNext(context)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isPlaying = false
            onPlaybackStateChanged?.invoke(false)
        }
    }

    fun togglePlayPause(context: Context) {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                isPlaying = false
                onPlaybackStateChanged?.invoke(false)
            } else {
                player.start()
                isPlaying = true
                onPlaybackStateChanged?.invoke(true)
            }
        } ?: run {
            if (currentIndex in playlist.indices) {
                playCurrent(context)
            }
        }
    }

    fun playNext(context: Context) {
        if (playlist.isEmpty()) return
        currentIndex = (currentIndex + 1) % playlist.size
        playCurrent(context)
    }

    fun playPrevious(context: Context) {
        if (playlist.isEmpty()) return
        currentIndex = if (currentIndex - 1 < 0) playlist.size - 1 else currentIndex - 1
        playCurrent(context)
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
    }

    fun getDuration(): Int {
        return mediaPlayer?.duration ?: 0
    }

    fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition ?: 0
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // Ignore
        }
        mediaPlayer = null
        isPlaying = false
        onPlaybackStateChanged?.invoke(false)
        onTrackChanged?.invoke(null)
    }
}
