package com.womensafety.utils

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log

class VoicePlayerHelper {

    private var mediaPlayer: MediaPlayer? = null
    var isPlaying = false
        private set

    fun playAudio(urlOrPath: String, onComplete: () -> Unit = {}) {
        stopAudio()
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(urlOrPath)
                setOnCompletionListener {
                    this@VoicePlayerHelper.isPlaying = false
                    releasePlayer()
                    onComplete()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("VoicePlayerHelper", "MediaPlayer error: what=$what, extra=$extra")
                    this@VoicePlayerHelper.isPlaying = false
                    releasePlayer()
                    onComplete()
                    true
                }
                prepareAsync()
                setOnPreparedListener {
                    start()
                    this@VoicePlayerHelper.isPlaying = true
                }
            }
        } catch (e: Exception) {
            Log.e("VoicePlayerHelper", "Error initializing player: ${e.message}", e)
            releasePlayer()
            isPlaying = false
            onComplete()
        }
    }

    fun stopAudio() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
        } catch (_: Exception) {}
        releasePlayer()
        isPlaying = false
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }
}
