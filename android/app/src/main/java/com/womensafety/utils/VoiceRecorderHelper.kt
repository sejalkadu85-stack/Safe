package com.womensafety.utils

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class VoiceRecorderHelper(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    var outputFile: File? = null
        private set

    private var startTimeMillis: Long = 0L
    private var isRecording = false

    fun startRecording(): Boolean {
        try {
            val audioDir = File(context.cacheDir, "voice_notes").apply { if (!exists()) mkdirs() }
            outputFile = File(audioDir, "voice_note_${System.currentTimeMillis()}.m4a")

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile!!.absolutePath)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                prepare()
                start()
            }

            startTimeMillis = System.currentTimeMillis()
            isRecording = true
            Log.d("VoiceRecorderHelper", "Started recording to: ${outputFile?.absolutePath}")
            return true
        } catch (e: Exception) {
            Log.e("VoiceRecorderHelper", "Error starting recording: ${e.message}", e)
            releaseRecorder()
            return false
        }
    }

    fun stopRecording(): Int {
        if (!isRecording) return 0
        var durationSeconds = 0
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            durationSeconds = ((System.currentTimeMillis() - startTimeMillis) / 1000).toInt()
            Log.d("VoiceRecorderHelper", "Stopped recording. Duration: ${durationSeconds}s")
        } catch (e: Exception) {
            Log.e("VoiceRecorderHelper", "Error stopping recording: ${e.message}", e)
        } finally {
            mediaRecorder = null
            isRecording = false
        }
        return durationSeconds
    }

    fun cancelRecording() {
        try {
            if (isRecording) {
                mediaRecorder?.stop()
            }
        } catch (_: Exception) {}
        releaseRecorder()
        outputFile?.delete()
        outputFile = null
        isRecording = false
    }

    private fun releaseRecorder() {
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null
    }

    fun isCurrentlyRecording(): Boolean = isRecording
}
