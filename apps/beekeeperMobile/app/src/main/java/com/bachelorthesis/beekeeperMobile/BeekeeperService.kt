package com.bachelorthesis.beekeeperMobile

import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bachelorthesis.beekeeperMobile.models.CreateLogRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.regex.Pattern

/**
 * ## BeekeeperService Refactored
 *
 * This service has been rewritten to be more stable, efficient, and accurate.
 *
 * ### Key Changes:
 * 1.  **Coroutines for Threading:** All blocking operations (file I/O, initializations) have been
 * moved to background threads using Kotlin Coroutines (`serviceScope`). This prevents the
 * main thread from freezing, which is critical for clean audio capture and thus, for
 * accurate speech recognition. The old `Handler` has been completely removed.
 *
 * 2.  **Robust State Machine:** The state management is now clearer and safer. State transitions
 * are handled within coroutines, preventing race conditions where the app could be in one
 * state while a callback for a previous state is still pending.
 *
 * 3.  **Stable Component Lifecycles:**
 * - `PorcupineManager` and `SpeechRecognizer` are now started, stopped, and destroyed in a
 * predictable order, fixing the crashes and error spam seen in the logs.
 * - `SpeechRecognizer` is created on-demand and destroyed immediately after use, which is a
 * more stable pattern than trying to reuse a single instance.
 *
 * 4.  **Improved Command Recognition:**
 * - The commands are now more acoustically distinct to improve accuracy.
 * - A `EXTRA_PREFER_OFFLINE` flag has been added to hint to the system that we want the
 * more accurate online recognizer when available.
 *
 * @author shraggen (Original)
 * @author Gemini (Refactor)
 */
class BeekeeperService : Service() {

    // A coroutine scope for all background tasks in the service.
    // Using SupervisorJob so that if one child job fails, it doesn't cancel the whole scope.
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var porcupineManager: PorcupineManager? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null

    private val hiveLogs: MutableMap<Int, MutableList<String>> = mutableMapOf()

    // The state machine for the service. All state changes must happen on the main thread.
    private var currentState: ServiceState = ServiceState.STOPPED
        private set(value) {
            if (field != value) {
                Log.i(TAG, "State Transition: $field -> $value")
                field = value
            }
        }

    //region Lifecycle Methods
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Creating...")
        currentState = ServiceState.INITIALIZING
        setupNotificationChannel()

        // Start all initializations in the background.
        serviceScope.launch {
            try {
                // Initialize components sequentially in the background.
                initializeTts()
                initializePorcupine()
                // Once everything is ready, transition to the idle state on the main thread.
                withContext(Dispatchers.Main) {
                    transitionToState(ServiceState.IDLE)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fatal initialization error: ${e.message}", e)
                // If anything fails, stop the service.
                withContext(Dispatchers.Main) {
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand received.")
        val notification = createNotification("Listening for 'Hey Beekeeper'...")
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service Destroying...")
        currentState = ServiceState.STOPPED

        // Gracefully shut down all components.
        // This is done synchronously here to ensure everything is cleaned up before the service dies.
        try {
            porcupineManager?.stop()
            Log.d(TAG, "Porcupine stopped.")
        } catch (e: PorcupineException) {
            Log.e(TAG, "Error stopping Porcupine in onDestroy", e)
        } finally {
            porcupineManager?.delete()
            porcupineManager = null
            Log.d(TAG, "Porcupine deleted.")
        }

        speechRecognizer?.destroy()
        speechRecognizer = null
        Log.d(TAG, "SpeechRecognizer destroyed.")

        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        Log.d(TAG, "TextToSpeech shutdown.")

        // Cancel all coroutines associated with this service.
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    //endregion

    //region State Machine
    private enum class ServiceState {
        STOPPED,
        INITIALIZING,
        IDLE, // Listening for wake word
        AWOKEN, // Wake word detected, prompting user
        LISTENING, // Listening for command
        PROCESSING // Processing command and speaking response
    }

    /**
     * The main state transition function. Ensures that transitions happen on the main thread.
     */
    private fun transitionToState(newState: ServiceState) {
        // All state transitions must happen on the main thread to prevent race conditions.
        serviceScope.launch(Dispatchers.Main) {
            if (currentState == newState) {
                Log.w(TAG, "Attempted to transition to the same state: $newState")
                return@launch
            }

            val oldState = currentState
            currentState = newState
            Log.i(TAG, "Handling state change from $oldState to $newState")

            // Handle leaving the old state
            when (oldState) {
                ServiceState.IDLE -> stopPorcupine()
                ServiceState.LISTENING -> stopSpeechRecognizer()
                else -> { /* No action needed */ }
            }

            // Handle entering the new state
            when (newState) {
                ServiceState.IDLE -> startPorcupine()
                ServiceState.AWOKEN -> promptForCommand()
                ServiceState.LISTENING -> startSpeechRecognizer()
                ServiceState.STOPPED -> stopSelf()
                else -> { /* No action needed */ }
            }
        }
    }
    //endregion

    //region Component Initialization (Background Thread)
    /**
     * Initializes TextToSpeech engine. Must be called from a background thread.
     */
    private suspend fun initializeTts() = withContext(Dispatchers.Main) {
        var ttsInitialized = false
        textToSpeech = TextToSpeech(this@BeekeeperService) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = getActiveLocale()
                textToSpeech?.setOnUtteranceProgressListener(ttsListener)
                ttsInitialized = true
                Log.d(TAG, "TTS Initialized successfully.")
            } else {
                Log.e(TAG, "TTS Initialization failed with status: $status")
                throw IllegalStateException("TTS could not be initialized.")
            }
        }
        // A simple loop to wait for TTS to be ready.
        while(!ttsInitialized) {
            kotlinx.coroutines.delay(100)
        }
    }

    /**
     * Initializes Porcupine wake word engine. Must be called from a background thread.
     */
    private fun initializePorcupine() {
        if (PICOVOICE_ACCESS_KEY.isBlank()) {
            throw IllegalArgumentException("PICOVOICE_ACCESS_KEY is not set in BuildConfig.")
        }
        val modelPath = copyAssetToCache(MODEL_FILE_NAME)
        val keywordPath = copyAssetToCache(WAKE_WORD_FILE_NAME)

        porcupineManager = PorcupineManager.Builder()
            .setAccessKey(PICOVOICE_ACCESS_KEY)
            .setKeywordPath(keywordPath)
            .setModelPath(modelPath)
            .setSensitivity(0.7f)
            .build(applicationContext) {
                // This callback comes from Porcupine's internal thread.
                // Switch to the main thread to handle the state transition safely.
                transitionToState(ServiceState.AWOKEN)
            }
        Log.d(TAG, "PorcupineManager built successfully.")
    }
    //endregion

    //region Component Control
    private fun startPorcupine() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Cannot start Porcupine: RECORD_AUDIO permission not granted.")
            return
        }
        if (currentState != ServiceState.IDLE) {
            Log.w(TAG, "Cannot start Porcupine, not in IDLE state. Current: $currentState")
            return
        }
        try {
            porcupineManager?.start()
            Log.i(TAG, "Porcupine started listening for wake word.")
        } catch (e: PorcupineException) {
            Log.e(TAG, "Error starting Porcupine", e)
        }
    }

    private fun stopPorcupine() {
        try {
            porcupineManager?.stop()
            Log.i(TAG, "Porcupine stopped.")
        } catch (e: PorcupineException) {
            Log.e(TAG, "Error stopping Porcupine", e)
        }
    }

    private fun startSpeechRecognizer() {
        if (currentState != ServiceState.LISTENING) {
            Log.w(TAG, "Cannot start SpeechRecognizer, not in LISTENING state. Current: $currentState")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition not available on this device.")
            handleCommandError("Speech recognition is not available.")
            return
        }

        // Destroy any old instance before creating a new one.
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(speechRecognitionListener)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, ACTIVE_STT_LANGUAGE)
            // HINT to the system to prefer the more accurate online recognizer. Not guaranteed.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        }
        speechRecognizer?.startListening(intent)
        Log.i(TAG, "SpeechRecognizer started listening for command.")
    }

    private fun stopSpeechRecognizer() {
        speechRecognizer?.stopListening()
        speechRecognizer?.cancel()
        Log.d(TAG, "SpeechRecognizer stopped.")
    }

    private fun speak(text: String, utteranceId: String) {
        Log.d(TAG, "TTS speak: '$text' with ID '$utteranceId'")
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }
    //endregion

    //region Command and State Logic
    private fun promptForCommand() {
        val prompt = if (ACTIVE_STT_LANGUAGE == LANG_GERMAN) "Ja?" else "Yes?"
        speak(prompt, UTTERANCE_ID_PROMPT_FOR_COMMAND)
    }

    private fun processCommand(command: String) {
        transitionToState(ServiceState.PROCESSING)
        Log.d(TAG, "Processing command: '$command'")

        // Use more distinct patterns for better accuracy
        val createLogPattern = Pattern.compile("entry for beehive (\\d+) (.+)", Pattern.CASE_INSENSITIVE)
        val readLogPattern = Pattern.compile("read (?:the )?last note for beehive (\\d+)", Pattern.CASE_INSENSITIVE)
        val readTaskPattern = Pattern.compile("what is the next task for beehive (\\d+)", Pattern.CASE_INSENSITIVE)
        val helpPattern = Pattern.compile("(?:what )?can i say|help", Pattern.CASE_INSENSITIVE) // Made "what" optional

        val createMatcher = createLogPattern.matcher(command)
        val readMatcher = readLogPattern.matcher(command)
        val taskMatcher = readTaskPattern.matcher(command)
        val helpMatcher = helpPattern.matcher(command)

        serviceScope.launch {
            val response: String = when {
                createMatcher.find() -> {
                    val hiveNumber = createMatcher.group(1)?.toIntOrNull()
                    val payload = createMatcher.group(2)
                    if (hiveNumber != null && payload != null) {
                        try {
                            val request = CreateLogRequest(hiveID = hiveNumber, content = payload.trim())
                            val apiResponse = RetrofitClient.instance.createLog(request)
                            if (apiResponse.isSuccessful) {
                                "Okay, note recorded for beehive $hiveNumber."
                            } else {
                                "Failed to record note. Server responded with error."
                            }
                        } catch (e: Exception) {
                            "Failed to record note. Error: ${e.message}"
                        }
                    } else {
                        "Sorry, I couldn't understand the hive number or the note."
                    }
                }
                readMatcher.find() -> {
                    val hiveNumber = readMatcher.group(1)?.toIntOrNull()
                    if (hiveNumber != null) {
                        try {
                            val apiResponse = RetrofitClient.instance.getHive(hiveNumber)
                            if (apiResponse.isSuccessful) {
                                val hive = apiResponse.body()
                                val lastLog = hive?.logs?.lastOrNull()
                                lastLog?.content ?: "No notes found for beehive $hiveNumber."
                            } else {
                                "Could not find hive $hiveNumber."
                            }
                        } catch (e: Exception) {
                            "Failed to get notes. Error: ${e.message}"
                        }
                    } else {
                        "Sorry, I couldn't understand the hive number."
                    }
                }
                taskMatcher.find() -> {
                    val hiveNumber = taskMatcher.group(1)?.toIntOrNull()
                    "The task feature for beehive $hiveNumber is not yet implemented."
                }
                helpMatcher.find() -> {
                    "You can say things like: log for beehive 10, or, read last note for beehive 12."
                }
                else -> "Sorry, I didn't understand that command."
            }

            speak(response, UTTERANCE_ID_COMMAND_RESPONSE)
        }
    }

    private fun handleCommandError(errorMsg: String) {
        transitionToState(ServiceState.PROCESSING)
        Log.e(TAG, "Command Error: $errorMsg")
        speak("Sorry, there was an error. $errorMsg", UTTERANCE_ID_COMMAND_RESPONSE)
    }
    //endregion

    //region Listeners
    private val ttsListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}

        override fun onDone(utteranceId: String?) {
            // Callbacks are on a binder thread, switch to main to change state.
            serviceScope.launch(Dispatchers.Main) {
                Log.d(TAG, "TTS onDone for $utteranceId. Current state: $currentState")
                when (utteranceId) {
                    UTTERANCE_ID_PROMPT_FOR_COMMAND -> {
                        if (currentState == ServiceState.AWOKEN) {
                            transitionToState(ServiceState.LISTENING)
                        }
                    }
                    UTTERANCE_ID_COMMAND_RESPONSE -> {
                        if (currentState == ServiceState.PROCESSING) {
                            transitionToState(ServiceState.IDLE)
                        }
                    }
                }
            }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            serviceScope.launch(Dispatchers.Main) {
                Log.e(TAG, "TTS Error for $utteranceId, code: $errorCode. Current state: $currentState")
                // Regardless of the error, try to recover by going back to idle.
                if (currentState == ServiceState.AWOKEN || currentState == ServiceState.PROCESSING) {
                    transitionToState(ServiceState.IDLE)
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {}
    }

    private val speechRecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { Log.d(TAG, "SR: Ready for speech.") }
        override fun onBeginningOfSpeech() { Log.d(TAG, "SR: Beginning of speech.") }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { Log.d(TAG, "SR: End of speech.") }

        override fun onError(error: Int) {
            if (currentState != ServiceState.LISTENING) return // Ignore errors if not in listening state
            val errorMsg = getSpeechRecognizerErrorText(error)
            Log.e(TAG, "SR Error: $errorMsg (code: $error)")
            handleCommandError(errorMsg)
        }

        override fun onResults(results: Bundle?) {
            if (currentState != ServiceState.LISTENING) return // Ignore results if not in listening state
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val command = matches?.firstOrNull()?.lowercase(getActiveLocale())

            if (command != null) {
                processCommand(command)
            } else {
                handleCommandError("I didn't catch that. Please try again.")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
    //endregion

    //region Helper Functions
    private fun copyAssetToCache(fileName: String): String {
        val cacheFile = File(cacheDir, fileName)
        if (!cacheFile.exists()) {
            assets.open(fileName).use { inputStream ->
                FileOutputStream(cacheFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        return cacheFile.absolutePath
    }

    private fun setupNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Beekeeper Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
        Log.d(TAG, "Notification channel created.")
    }

    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Beekeeper Assistant")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun getSpeechRecognizerErrorText(errorCode: Int): String = when (errorCode) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
        SpeechRecognizer.ERROR_CLIENT -> "Client side error."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions."
        SpeechRecognizer.ERROR_NETWORK -> "Network error."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout."
        SpeechRecognizer.ERROR_NO_MATCH -> "No match found."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy."
        SpeechRecognizer.ERROR_SERVER -> "Error from server."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input."
        else -> "Unknown speech recognizer error."
    }

    companion object {
        private const val TAG = "BeekeeperService"
        private const val NOTIFICATION_CHANNEL_ID = "BeekeeperServiceChannel"
        private const val NOTIFICATION_ID = 1

        private const val UTTERANCE_ID_PROMPT_FOR_COMMAND = "PROMPT_FOR_COMMAND"
        private const val UTTERANCE_ID_COMMAND_RESPONSE = "COMMAND_RESPONSE"

        private const val PICOVOICE_ACCESS_KEY = BuildConfig.PICOVOICE_ACCESS_KEY
        private const val WAKE_WORD_FILE_NAME = "hey_beekeeper.ppn"
        private const val MODEL_FILE_NAME = "porcupine_params.pv"

        const val LANG_ENGLISH = "en-US"
        const val LANG_GERMAN = "de-DE"
        var ACTIVE_STT_LANGUAGE = LANG_ENGLISH

        fun getActiveLocale(): Locale {
            return if (ACTIVE_STT_LANGUAGE == LANG_GERMAN) Locale.GERMAN else Locale.US
        }
    }
    //endregion
}