// In apps/beekeeperMobile/app/src/main/java/com/bachelorthesis/beekeeperMobile/speechEngine/WhisperTranscriber.kt

package com.bachelorthesis.beekeeperMobile.speechEngine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.bachelorthesis.beekeeperMobile.assetManager.AssetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WhisperTranscriber(
    private val context: Context,
    private val listener: (String) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var recordingJob: Job? = null
    private var whisperContextPtr: Long = 0L

    private var vadModelPath: String = ""

    private var audioRecord: AudioRecord? = null
    private var bufferSizeInBytes: Int = 0

    private val nThreads = Runtime.getRuntime().availableProcessors().coerceAtMost(8)

    companion object {
        private const val TAG = "WhisperTranscriber"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        // We can process audio in larger chunks now since VAD handles silence
        private const val CHUNK_DURATION_SECONDS = 5
    }

    // MODIFIED: Pass AssetManager to get model paths
    suspend fun initialize(assetManager: AssetManager): Boolean = withContext(Dispatchers.IO) {
        val modelPath = assetManager.getWhisperModelPath().absolutePath
        vadModelPath = assetManager.getVadModelPath().absolutePath

        Log.d(TAG, "Initializing with model: $modelPath")

        // Call the simplified initContext
        whisperContextPtr = LibWhisper.initContext(modelPath)

        if (whisperContextPtr == 0L) {
            Log.e(TAG, "Failed to initialize Whisper context.")
            return@withContext false
        }
        Log.d(TAG, "Whisper context initialized successfully.")
        return@withContext true
    }

    fun start() {
        if (recordingJob?.isActive == true) {
            Log.w(TAG, "Already recording.")
            return
        }
        recordingJob = scope.launch {
            if (!setupAudioRecord()) return@launch

            audioRecord?.startRecording()
            Log.i(TAG, "Recording started (with internal VAD).")

            val audioBuffer = ShortArray(SAMPLE_RATE * CHUNK_DURATION_SECONDS)
            while (isActive) {
                val readSize = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                if (readSize > 0) {
                    val floatArray = convertShortArrayToFloatArray(audioBuffer, readSize)
                    val result = LibWhisper.transcribe(whisperContextPtr, nThreads, floatArray, vadModelPath)
                    if (result.isNotBlank()) {
                        withContext(Dispatchers.Main) {
                            listener(result)
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        recordingJob?.cancel()
        recordingJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        Log.i(TAG, "Recording stopped.")
    }

    suspend fun release() = withContext(Dispatchers.IO) {
        if (whisperContextPtr != 0L) {
            LibWhisper.releaseContext(whisperContextPtr)
            whisperContextPtr = 0L
            Log.i(TAG, "Whisper context released.")
        }
    }

    private fun setupAudioRecord(): Boolean {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted.")
            return false
        }
        bufferSizeInBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSizeInBytes == AudioRecord.ERROR || bufferSizeInBytes == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Failed to get min buffer size.")
            return false
        }
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSizeInBytes
        )
        return true
    }

    private fun convertShortArrayToFloatArray(shortData: ShortArray, readSize: Int): FloatArray {
        val floatData = FloatArray(readSize)
        for (i in 0 until readSize) {
            floatData[i] = shortData[i] / 32768.0f
        }
        return floatData
    }
}