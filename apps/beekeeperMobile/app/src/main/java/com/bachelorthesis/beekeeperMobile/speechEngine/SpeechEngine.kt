package com.bachelorthesis.beekeeperMobile.speechEngine

import android.content.Context
import com.bachelorthesis.beekeeperMobile.assetManager.AssetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import java.io.IOException
import android.util.Log as AndroidLog
import org.vosk.android.RecognitionListener as VoskRecognitionListener

class SpeechEngine(
    private val context: Context,
    private val listener: SpeechEngineListener,
    private val assetManager: AssetManager
) : VoskRecognitionListener {

    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Vosk components for hotword detection
    private var voskModel: Model? = null
    private var voskSpeechService: SpeechService? = null

    // NEW: WhisperTranscriber for command transcription
    private var whisperTranscriber: WhisperTranscriber? = null

    @Volatile private var currentState = State.STOPPED

    private enum class State { STOPPED, INITIALIZING, IDLE_HOTWORD, LISTENING_COMMAND }

    fun initialize(onInitialized: (success: Boolean) -> Unit) {
        if (currentState != State.STOPPED) return
        currentState = State.INITIALIZING

        engineScope.launch {
            try {
                // Initialize Vosk for hotword
                val voskModelPath = assetManager.getVoskModelPath().absolutePath
                val voskModelFile = assetManager.getVoskModelPath().absoluteFile
                if (!voskModelFile.exists() || !voskModelFile.isDirectory) {
                    listener.onError("Vosk model path is invalid: ${voskModelPath}")
                    onInitialized(false)
                    return@launch
                }
                this@SpeechEngine.voskModel = Model(voskModelPath)
                AndroidLog.d(TAG, "Vosk model initialized.")

                // Initialize WhisperTranscriber
                whisperTranscriber = WhisperTranscriber(context, assetManager) { transcribedText ->
                    listener.onCommandTranscribed(transcribedText)
                    startListeningForHotword()
                }

                // The transcriber now gets paths from AssetManager itself
                val whisperSuccess = whisperTranscriber?.initialize() ?: false
                if (!whisperSuccess) {
                    listener.onError("Failed to initialize Whisper transcriber.")
                    onInitialized(false)
                    return@launch
                }
                AndroidLog.d(TAG, "Whisper transcriber initialized.")

                onInitialized(true)

            } catch (e: Exception) {
                listener.onError("Failed to initialize SpeechEngine: ${e.message}")
                onInitialized(false)
            }
        }
    }

    fun startListeningForHotword() {
        if (voskModel == null || currentState == State.IDLE_HOTWORD) return
        currentState = State.IDLE_HOTWORD

        // Make sure the whisper transcriber is stopped
        whisperTranscriber?.stop()

        try {
            val recognizer = Recognizer(voskModel, 16000.0f, "[\"hey beekeeper\", \"[unk]\"]")
            voskSpeechService = SpeechService(recognizer, 16000.0f)
            voskSpeechService?.startListening(this)
            AndroidLog.i(TAG, "Vosk is now listening for hotword.")
        } catch (e: IOException) {
            listener.onError("Error starting Vosk: ${e.message}")
        }
    }

    fun startListeningForCommand() {
        currentState = State.LISTENING_COMMAND

        // Stop Vosk
        voskSpeechService?.stop()
        voskSpeechService = null

        // Start Whisper
        whisperTranscriber?.start()
        AndroidLog.i(TAG, "Whisper is now listening for a command.")
    }

    fun destroy() {
        engineScope.launch {
            currentState = State.STOPPED
            voskSpeechService?.stop()
            voskSpeechService?.shutdown()
            voskSpeechService = null
            whisperTranscriber?.stop()
            whisperTranscriber?.release()
            whisperTranscriber = null
            voskModel = null
            engineScope.cancel()
        }
    }

    // --- VoskRecognitionListener Implementation ---
    override fun onResult(hypothesis: String?) {
        val text = hypothesis?.let { JSONObject(it).optString("text", "") }
        if (currentState == State.IDLE_HOTWORD && text == "hey beekeeper") {
            listener.onHotwordDetected()
        }
    }
    override fun onFinalResult(hypothesis: String?) {}
    override fun onPartialResult(hypothesis: String?) {}
    override fun onError(e: Exception?) { listener.onError("Vosk error: ${e?.message}") }
    override fun onTimeout() { AndroidLog.w(TAG, "Vosk timeout.") }


    companion object {
        private const val TAG = "SpeechEngine"
    }
}