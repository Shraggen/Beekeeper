// File: SpeechEngine.kt
package com.bachelorthesis.beekeeperMobile

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
import org.vosk.android.StorageService
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

    private enum class State { STOPPED, INITIALIZING, IDLE, LISTENING_COMMAND }
    @Volatile private var currentState = State.STOPPED

    fun initialize(onInitialized: (success: Boolean) -> Unit) {
        if (currentState != State.STOPPED) return
        currentState = State.INITIALIZING

        mainHandler.post {
            androidSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            androidSpeechRecognizer?.setRecognitionListener(this)

            StorageService.unpack(context, "model-en-us", "model",
                { model ->
                    this.voskModel = model
                    Log.d("SpeechEngine", "Vosk model initialized.")
                    // Don't start listening yet, just report success.
                    // The service will transition to IDLE and call startListeningForHotword.
                    onInitialized(true)
                },
                { exception ->
                    listener.onError("Failed to initialize Vosk model: ${exception.message}")
                    onInitialized(false)
                }
            )
        }
    }

    fun startListeningForHotword() {
        if (voskModel == null || currentState == State.IDLE) return
        mainHandler.post {
            currentState = State.IDLE
            androidSpeechRecognizer?.cancel() // Ensure Android SR is fully cancelled.
            voskSpeechService?.stop() // Cleanly stop any previous instance.
            try {
                val recognizer = Recognizer(voskModel, 16000.0f, "[\"hey beekeeper\", \"[unk]\"]")
                voskSpeechService = SpeechService(recognizer, 16000.0f)
                voskSpeechService?.startListening(this)
                Log.i("SpeechEngine", "Vosk is now listening for hotword.")
            } catch (e: IOException) {
                listener.onError("Error starting Vosk: ${e.message}")
            }
        }
    }

    fun startListeningForCommand() {
        mainHandler.post {
            currentState = State.LISTENING_COMMAND
            // REFACTOR: Stop Vosk completely to release the mic.
            voskSpeechService?.stop()
            voskSpeechService = null

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            androidSpeechRecognizer?.startListening(intent)
            Log.i("SpeechEngine", "Android SpeechRecognizer is now listening for a command.")

            //After Testing this wasnt needed anymore
            /*// REFACTOR: Introduce a tiny delay to give the OS time to free the microphone.
            mainHandler.postDelayed({
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                }
                androidSpeechRecognizer?.startListening(intent)
                Log.i("SpeechEngine", "Android SpeechRecognizer is now listening for a command.")
            }, 250) // 250ms delay*/
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
        }
    }

    // --- VoskRecognitionListener ---
    override fun onResult(hypothesis: String?) {
        val text = hypothesis?.let { JSONObject(it).optString("text", "") }
        if (currentState == State.IDLE && text == "hey beekeeper") {
            listener.onHotwordDetected()
        }
    }

    // REFACTOR: FIX FOR THE CRASH. Implement the method with an empty body.
    override fun onFinalResult(hypothesis: String?) {
        /* This is called on stop. We don't need to do anything with it. */
    }

    override fun onPartialResult(hypothesis: String?) { /* Not used */ }
    override fun onError(e: Exception?) { listener.onError("Vosk error: ${e?.message}") }
    override fun onTimeout() { Log.w("SpeechEngine", "Vosk timeout.") }

    // --- AndroidRecognitionListener ---
    override fun onResults(results: Bundle?) {
        val spokenText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.getOrNull(0)
        // Always go back to hotword listening after a result or lack thereof.
        startListeningForHotword()
        if (!spokenText.isNullOrBlank()) {
            listener.onCommandTranscribed(spokenText)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val spokenText = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.getOrNull(0)
        Log.i("SpeechEngine", "Partial result: $spokenText")
    }

    override fun onError(error: Int) {
        // This is no longer a problem, but the logging is good.
        Log.w("SpeechEngine", "AndroidSR Error: $error. Returning to hotword listening.")
        startListeningForHotword()
    }

    override fun onReadyForSpeech(params: Bundle?) { Log.d("SpeechEngine", "AndroidSR: Ready for speech.") }
    override fun onBeginningOfSpeech() { }
    override fun onEndOfSpeech() { }
    override fun onRmsChanged(rmsdB: Float) { }
    override fun onBufferReceived(buffer: ByteArray?) { }
    override fun onEvent(eventType: Int, params: Bundle?) { }
}