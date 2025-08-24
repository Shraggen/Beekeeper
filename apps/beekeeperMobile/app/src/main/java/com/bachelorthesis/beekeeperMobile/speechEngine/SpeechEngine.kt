package com.bachelorthesis.beekeeperMobile.speechEngine

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener as AndroidRecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log as AndroidLog // Alias for Android's Log to avoid conflict
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

    @Volatile private var currentState = State.STOPPED
    private var isRetryAttempted = false

    // NEW: Store the preferred language
    private var preferredLocale: Locale = Locale.getDefault() // Default to device locale

    private enum class State { STOPPED, INITIALIZING, IDLE, LISTENING_COMMAND }

    // MODIFIED: Accepts preferredLocale
    fun initialize(modelPath: String, preferredLocale: Locale, onInitialized: (success: Boolean) -> Unit) {
        if (currentState != State.STOPPED) return
        currentState = State.INITIALIZING
        this.preferredLocale = preferredLocale // Store the provided locale

        try {
            val modelFile = File(modelPath)
            if (!modelFile.exists() || !modelFile.isDirectory) {
                listener.onError("Vosk model path does not exist or is not a directory: $modelPath")
                onInitialized(false)
                return
            }

            this.voskModel = Model(modelPath)
            AndroidLog.d(TAG, "Vosk model initialized directly from path: $modelPath")

            mainHandler.post {
                androidSpeechRecognizer = createRobustSpeechRecognizer()
                androidSpeechRecognizer?.setRecognitionListener(this)

                // MODIFIED: Use preferredLocale here
                recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, preferredLocale.toLanguageTag()) // Use the stored preferred locale
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    // Note: EXTRA_PREFER_OFFLINE is intentionally left out as per ADR-003
                    // The Android SpeechRecognizer will decide whether to use online or offline based on its own logic and language pack availability.
                }

                onInitialized(true)
            }
        } catch (e: Exception) {
            listener.onError("Failed to initialize Vosk model from path: ${e.message}")
            onInitialized(false)
        }
    }

    private fun createRobustSpeechRecognizer(): SpeechRecognizer {
        val pm = context.packageManager
        val services = pm.queryIntentServices(Intent(android.speech.RecognitionService.SERVICE_INTERFACE), 0)

        val googleService = services.find { it.serviceInfo.packageName == GOOGLE_RECOGNIZER_PACKAGE }

        return if (googleService != null) {
            AndroidLog.i(TAG, "Found Google Speech Recognizer. Using it as primary.")
            SpeechRecognizer.createSpeechRecognizer(context, android.content.ComponentName(googleService.serviceInfo.packageName, googleService.serviceInfo.name))
        } else {
            AndroidLog.w(TAG, "Google Speech Recognizer not found. Falling back to device default.")
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
                // Vosk hotword is typically language-agnostic or uses a very simple model
                // The current hotword "hey beekeeper" is simple enough not to require specific language model selection for Vosk itself.
                val recognizer = Recognizer(voskModel, 16000.0f, "[\"hey beekeeper\", \"[unk]\"]")
                voskSpeechService = SpeechService(recognizer, 16000.0f)
                voskSpeechService?.startListening(this)
                AndroidLog.i(TAG, "Vosk is now listening for hotword.")
            } catch (e: IOException) {
                listener.onError("Error starting Vosk: ${e.message}")
            }
        }
    }

    // REMOVED: Locale parameter from startListeningForCommand - it's now set during initialization
    fun startListeningForCommand() {
        mainHandler.post {
            currentState = State.LISTENING_COMMAND
            voskSpeechService?.stop()
            voskSpeechService = null

            // The recognizerIntent already has the preferredLocale set during initialize
            androidSpeechRecognizer?.startListening(recognizerIntent)
            AndroidLog.i(TAG, "Android SpeechRecognizer is now listening for a command in ${preferredLocale.toLanguageTag()}.")
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
    override fun onTimeout() { AndroidLog.w(TAG, "Vosk timeout.") }

    // --- AndroidRecognitionListener Implementation ---
    override fun onResults(results: Bundle?) {
        val spokenText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.getOrNull(0)
        if (!spokenText.isNullOrBlank()) {
            listener.onCommandTranscribed(spokenText)
        } else {
            // If there's no result, we should go back to listening.
            startListeningForHotword()
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {}

    override fun onError(error: Int) {
        if (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE && !isRetryAttempted) {
            isRetryAttempted = true
            AndroidLog.w(TAG, "Got spurious ERROR_LANGUAGE_UNAVAILABLE (14). Retrying once after a short delay...")
            mainHandler.postDelayed({
                androidSpeechRecognizer?.startListening(recognizerIntent)
            }, 500) // 500ms delay for the service to finish booting
            return
        }

        AndroidLog.e(TAG, "AndroidSR Error: $error. Returning to hotword listening.")
        startListeningForHotword()
    }

    override fun onReadyForSpeech(params: Bundle?) {
        AndroidLog.d(TAG, "AndroidSR: Ready for speech.")
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