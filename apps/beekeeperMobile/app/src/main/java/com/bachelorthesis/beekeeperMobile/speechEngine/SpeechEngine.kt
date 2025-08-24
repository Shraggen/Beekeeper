package com.bachelorthesis.beekeeperMobile.speechEngine

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener as AndroidRecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener as VoskRecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.IOException
import java.util.Locale

class SpeechEngine(
    private val context: Context,
    private val listener: SpeechEngineListener
) : VoskRecognitionListener, AndroidRecognitionListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var androidSpeechRecognizer: SpeechRecognizer? = null

    private var voskModel: Model? = null
    private var voskSpeechService: SpeechService? = null
    private lateinit var recognizerIntent: Intent

    private enum class State { STOPPED, INITIALIZING, IDLE, LISTENING_COMMAND }
    @Volatile private var currentState = State.STOPPED
    private var isRetryAttempted = false

    fun initialize(modelPath: String, onInitialized: (success: Boolean) -> Unit) {
        if (currentState != State.STOPPED) return
        currentState = State.INITIALIZING

        try {
            val modelFile = File(modelPath)
            if (!modelFile.exists() || !modelFile.isDirectory) {
                listener.onError("Vosk model path does not exist or is not a directory: $modelPath")
                onInitialized(false)
                return
            }

            this.voskModel = Model(modelPath)
            Log.d(TAG, "Vosk model initialized directly from path: $modelPath")

            mainHandler.post {
                // MODIFIED: Create a robust recognizer that prefers Google but falls back gracefully.
                androidSpeechRecognizer = createRobustSpeechRecognizer()
                androidSpeechRecognizer?.setRecognitionListener(this)

                // Prepare the intent once, so we can reuse it for retries.
                recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }

                onInitialized(true)
            }
        } catch (e: Exception) {
            listener.onError("Failed to initialize Vosk model from path: ${e.message}")
            onInitialized(false)
        }
    }

    /**
     * NEW: Creates the best available SpeechRecognizer.
     * It actively looks for the Google Recognizer, which is generally the most accurate.
     * If not found, it falls back to the device's default (e.g., Samsung's service).
     */
    private fun createRobustSpeechRecognizer(): SpeechRecognizer {
        val pm = context.packageManager
        val services = pm.queryIntentServices(Intent(android.speech.RecognitionService.SERVICE_INTERFACE), 0)

        val googleService = services.find { it.serviceInfo.packageName == GOOGLE_RECOGNIZER_PACKAGE }

        return if (googleService != null) {
            Log.i(TAG, "Found Google Speech Recognizer. Using it as primary.")
            SpeechRecognizer.createSpeechRecognizer(context, android.content.ComponentName(googleService.serviceInfo.packageName, googleService.serviceInfo.name))
        } else {
            Log.w(TAG, "Google Speech Recognizer not found. Falling back to device default.")
            SpeechRecognizer.createSpeechRecognizer(context)
        }
    }

    fun startListeningForHotword() {
        if (voskModel == null || currentState == State.IDLE) return
        mainHandler.post {
            currentState = State.IDLE
            androidSpeechRecognizer?.cancel()
            voskSpeechService?.stop()
            try {
                val recognizer = Recognizer(voskModel, 16000.0f, "[\"hey beekeeper\", \"[unk]\"]")
                voskSpeechService = SpeechService(recognizer, 16000.0f)
                voskSpeechService?.startListening(this)
                Log.i(TAG, "Vosk is now listening for hotword.")
            } catch (e: IOException) {
                listener.onError("Error starting Vosk: ${e.message}")
            }
        }
    }

    fun startListeningForCommand() {
        mainHandler.post {
            currentState = State.LISTENING_COMMAND
            voskSpeechService?.stop()
            voskSpeechService = null

            androidSpeechRecognizer?.startListening(recognizerIntent)
            Log.i(TAG, "Android SpeechRecognizer is now listening for a command.")
        }
    }

    fun destroy() {
        mainHandler.post {
            currentState = State.STOPPED
            voskSpeechService?.stop()
            voskSpeechService?.shutdown()
            voskSpeechService = null
            androidSpeechRecognizer?.destroy()
            androidSpeechRecognizer = null
            voskModel = null
        }
    }

    // --- VoskRecognitionListener Implementation (Unchanged) ---
    override fun onResult(hypothesis: String?) {
        val text = hypothesis?.let { JSONObject(it).optString("text", "") }
        if (currentState == State.IDLE && text == "hey beekeeper") {
            listener.onHotwordDetected()
        }
    }
    override fun onFinalResult(hypothesis: String?) {}
    override fun onPartialResult(hypothesis: String?) {}
    override fun onError(e: Exception?) { listener.onError("Vosk error: ${e?.message}") }
    override fun onTimeout() { Log.w(TAG, "Vosk timeout.") }

    // --- AndroidRecognitionListener Implementation ---
    override fun onResults(results: Bundle?) {
        val spokenText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.getOrNull(0)
        startListeningForHotword() // Always go back to hotword listening
        if (!spokenText.isNullOrBlank()) {
            listener.onCommandTranscribed(spokenText)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {}

    /**
     * MODIFIED: Implements the graceful retry logic for spurious errors.
     */
    override fun onError(error: Int) {
        // Handle the specific, one-time "language unavailable" error by retrying once.
        if (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE && !isRetryAttempted) {
            isRetryAttempted = true
            Log.w(TAG, "Got spurious ERROR_LANGUAGE_UNAVAILABLE (14). Retrying once after a short delay...")
            mainHandler.postDelayed({
                androidSpeechRecognizer?.startListening(recognizerIntent)
            }, 500) // 500ms delay for the service to finish booting
            return
        }

        // For all other errors, or if the retry fails, report it and go back to hotword listening.
        Log.e(TAG, "AndroidSR Error: $error. Returning to hotword listening.")
        startListeningForHotword()
    }

    /**
     * MODIFIED: Reset the retry flag every time speech is successfully ready.
     */
    override fun onReadyForSpeech(params: Bundle?) {
        Log.d(TAG, "AndroidSR: Ready for speech.")
        isRetryAttempted = false // Reset the retry flag for the next command cycle.
    }

    override fun onBeginningOfSpeech() {}
    override fun onEndOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    companion object {
        private const val TAG = "SpeechEngine"
        private const val GOOGLE_RECOGNIZER_PACKAGE = "com.google.android.googlequicksearchbox"
    }
}