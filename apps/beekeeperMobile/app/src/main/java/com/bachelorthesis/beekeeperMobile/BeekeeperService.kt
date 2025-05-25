package com.bachelorthesis.beekeeperMobile

import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.regex.Pattern

class BeekeeperService : Service() {

    private enum class ServiceState {
        IDLE_LISTENING_FOR_WAKE_WORD, // Porcupine is active
        PROMPTING_FOR_COMMAND,        // TTS "Yes?" is speaking, preparing for SR
        LISTENING_FOR_COMMAND,        // Android SpeechRecognizer is active
        PROCESSING_AND_SPEAKING       // Command is being processed, TTS is speaking result/error
    }
    private var currentState: ServiceState = ServiceState.IDLE_LISTENING_FOR_WAKE_WORD
    // --- End State Management ---

    companion object {
        private const val TAG = "BeekeeperService"
        private const val NOTIFICATION_CHANNEL_ID = "BeekeeperServiceChannel"
        private const val NOTIFICATION_ID = 1

        // TTS Utterance IDs
        private const val UTTERANCE_ID_PROMPT_FOR_COMMAND = "PROMPT_FOR_COMMAND"
        private const val UTTERANCE_ID_COMMAND_RESPONSE_PREFIX = "COMMAND_RESPONSE_"
        private const val UTTERANCE_ID_ERROR_PREFIX = "ERROR_" // General error prefix

        // Picovoice Configuration - IMPORTANT: Manage your Access Key securely!
        // Replace with your actual key, ideally from BuildConfig
        private const val PICOVOICE_ACCESS_KEY = BuildConfig.PICOVOICE_ACCESS_KEY
        private const val WAKE_WORD_FILE_NAME = "hey_beekeeper.ppn" // Your wake word file
        private const val MODEL_FILE_NAME = "porcupine_params.pv"
    }

    private var porcupineManager: PorcupineManager? = null
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textToSpeech: TextToSpeech

    private val hiveLogs: MutableMap<Int, MutableList<String>> = mutableMapOf()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Created. Initial State: $currentState")
        setupNotificationChannel()

        // Initialize TextToSpeech with UtteranceProgressListener
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "TTS language (US) not supported or missing data.")
                } else {
                    Log.d(TAG, "TTS Initialized successfully.")
                }
            } else {
                Log.e(TAG, "TTS Initialization failed with status: $status")
            }
        }

        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "TTS onStart: $utteranceId, Current State: $currentState")
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "TTS onDone: $utteranceId, Current State: $currentState")
                runOnUiThread {
                    when {
                        utteranceId == UTTERANCE_ID_PROMPT_FOR_COMMAND && currentState == ServiceState.PROMPTING_FOR_COMMAND -> {
                            Log.d(TAG, "TTS prompt finished, transitioning to LISTENING_FOR_COMMAND")
                            setCurrentState(ServiceState.LISTENING_FOR_COMMAND)
                        }
                        (utteranceId?.startsWith(UTTERANCE_ID_COMMAND_RESPONSE_PREFIX) == true || utteranceId?.startsWith(
                            UTTERANCE_ID_ERROR_PREFIX
                        ) == true) &&
                                currentState == ServiceState.PROCESSING_AND_SPEAKING -> {
                            Log.d(TAG, "TTS response/error finished, transitioning to IDLE")
                            setCurrentState(ServiceState.IDLE_LISTENING_FOR_WAKE_WORD)
                        }
                    }
                }
            }

            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS onError for utteranceId: $utteranceId. Current State: $currentState")
                runOnUiThread {
                    if (currentState == ServiceState.PROMPTING_FOR_COMMAND || currentState == ServiceState.PROCESSING_AND_SPEAKING) {
                        Log.d(TAG, "TTS error, transitioning to IDLE")
                        setCurrentState(ServiceState.IDLE_LISTENING_FOR_WAKE_WORD)
                    }
                }
            }
            override fun onError(utteranceId: String?, errorCode: Int) { // For newer API levels
                onError(utteranceId) // Delegate to older method for simplicity
                Log.e(TAG, "TTS onError (with errorCode $errorCode) for utteranceId: $utteranceId.")
            }
        })

        setupSpeechRecognizer()
        initializePorcupine()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand. Current State: $currentState")
        startForeground(NOTIFICATION_ID, createNotification())
        // If service is restarted, ensure it goes to a known good state.
        // onCreate would have already initialized components.
        setCurrentState(ServiceState.IDLE_LISTENING_FOR_WAKE_WORD)
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service Destroyed")
        // Clean up resources
        textToSpeech.stop()
        textToSpeech.shutdown()

        speechRecognizer.destroy()

        porcupineManager?.stop()
        porcupineManager?.delete()
        porcupineManager = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Not a bound service
    }
    // --- End Android Service Lifecycle Methods ---


    // --- Initialization Methods ---
    private fun initializePorcupine() {
        if (PICOVOICE_ACCESS_KEY.isEmpty()) {
            Log.e(TAG, "PICOVOICE_ACCESS_KEY is not set! Please replace placeholder.")
            // Consider stopping the service or notifying the user more prominently
            return
        }
        try {
            val modelPath = copyAssetToCache(MODEL_FILE_NAME)
            val keywordPath = copyAssetToCache(WAKE_WORD_FILE_NAME)

            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(PICOVOICE_ACCESS_KEY)
                .setKeywordPath(keywordPath)
                .setModelPath(modelPath)
                .setSensitivity(0.7f)
                .build(applicationContext) { keywordIndex ->
                    runOnUiThread {
                        if (keywordIndex == 0 && currentState == ServiceState.IDLE_LISTENING_FOR_WAKE_WORD) {
                            Log.i(TAG, "Wake word '${WAKE_WORD_FILE_NAME}' detected!")
                            setCurrentState(ServiceState.PROMPTING_FOR_COMMAND)
                        }
                    }
                }
            Log.d(TAG, "PorcupineManager built successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Porcupine: ${e.message}", e)
            // This is a critical failure. The service might not be able to function.
        }
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this.applicationContext)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { Log.d(TAG, "SR: Ready. Current State: $currentState") }
            override fun onBeginningOfSpeech() { Log.d(TAG, "SR: Beginning. Current State: $currentState") }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { Log.d(TAG, "SR: End of speech. Current State: $currentState") }

            override fun onError(error: Int) {
                val errorMsg = getSpeechRecognizerErrorText(error)
                Log.e(TAG, "SR Error: $errorMsg (code: $error). Current State: $currentState")
                runOnUiThread {
                    if (currentState == ServiceState.LISTENING_FOR_COMMAND) {
                        setCurrentState(ServiceState.PROCESSING_AND_SPEAKING)
                        speakText("Sorry, I had trouble understanding. $errorMsg", UTTERANCE_ID_ERROR_PREFIX + "SR_ERROR")
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                Log.d(TAG, "SR: Results received. Current State: $currentState")
                runOnUiThread {
                    if (currentState == ServiceState.LISTENING_FOR_COMMAND) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (matches != null && matches.isNotEmpty()) {
                            val command = matches[0].lowercase(Locale.ROOT)
                            Log.i(TAG, "Command received via SR: '$command'")
                            setCurrentState(ServiceState.PROCESSING_AND_SPEAKING)
                            processCommand(command)
                        } else {
                            Log.w(TAG, "SR: No matches found.")
                            setCurrentState(ServiceState.PROCESSING_AND_SPEAKING)
                            speakText("I didn't catch that. Please try again.", UTTERANCE_ID_ERROR_PREFIX + "NO_MATCH")
                        }
                    }
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }
    // --- End Initialization Methods ---


    // --- State Transition and Component Control ---
    private fun setCurrentState(newState: ServiceState) {
        if (currentState == newState && newState != ServiceState.IDLE_LISTENING_FOR_WAKE_WORD && newState != ServiceState.PROMPTING_FOR_COMMAND) {
            Log.d(TAG, "Already in state $newState or no need to re-trigger. No change.")
            return
        }
        Log.i(TAG, "State Transition: $currentState -> $newState")
        val oldState = currentState
        currentState = newState

        // Stop components based on old state or general cleanup before starting new ones
        if (oldState == ServiceState.LISTENING_FOR_COMMAND && newState != ServiceState.LISTENING_FOR_COMMAND) {
            stopSpeechRecognizer()
        }
        if (oldState == ServiceState.IDLE_LISTENING_FOR_WAKE_WORD && newState != ServiceState.IDLE_LISTENING_FOR_WAKE_WORD) {
            stopPorcupine()
        }
        if (textToSpeech.isSpeaking && (newState == ServiceState.IDLE_LISTENING_FOR_WAKE_WORD || newState == ServiceState.LISTENING_FOR_COMMAND)) {
            Log.d(TAG, "Stopping TTS as new state ($newState) doesn't expect it to be speaking.")
            textToSpeech.stop()
        }

        // Start components based on the new state
        when (newState) {
            ServiceState.IDLE_LISTENING_FOR_WAKE_WORD -> {
                stopSpeechRecognizer() // Ensure SR is off
                startPorcupine()
            }
            ServiceState.PROMPTING_FOR_COMMAND -> {
                stopPorcupine()
                stopSpeechRecognizer()
                speakText("Yes?",
                    UTTERANCE_ID_PROMPT_FOR_COMMAND
                )
            }
            ServiceState.LISTENING_FOR_COMMAND -> {
                // Porcupine should be stopped from previous state.
                // TTS for prompt should have finished (handled by onDone).
                startSpeechRecognizer()
            }
            ServiceState.PROCESSING_AND_SPEAKING -> {
                // SpeechRecognizer should have provided results and would be stopped by its callback or here.
                stopSpeechRecognizer() // Ensure SR is off
                // The actual command processing (processCommand) leads to TTS.
                // TTS onDone will transition us back to IDLE.
            }
        }
    }

    private fun startPorcupine() {
        if (porcupineManager == null) {
            Log.w(TAG, "PorcupineManager not initialized. Attempting to re-initialize.")
            initializePorcupine()
            if (porcupineManager == null) {
                Log.e(TAG, "Failed to initialize Porcupine. Cannot start.")
                // Consider a more robust error handling, maybe stop service or notify user.
                return
            }
        }
        try {
            Log.d(TAG, "Attempting to start Porcupine.")
            porcupineManager?.start()
            Log.i(TAG, "Porcupine started successfully.")
        } catch (e: PorcupineException) {
            Log.e(TAG, "Error starting Porcupine: ${e.message}")
        }
    }

    private fun stopPorcupine() {
        porcupineManager?.let {
            try {
                Log.d(TAG, "Attempting to stop Porcupine.")
                it.stop()
                Log.i(TAG, "Porcupine stopped successfully.")
            } catch (e: PorcupineException) {
                Log.e(TAG, "Error stopping Porcupine: ${e.message}")
            }
        }
    }

    private fun startSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(applicationContext)) {
            Log.e(TAG, "Speech recognition not available on this device.")
            setCurrentState(ServiceState.PROCESSING_AND_SPEAKING) // To allow TTS error
            speakText("Sorry, speech recognition is not available right now.", UTTERANCE_ID_ERROR_PREFIX + "SR_UNAVAILABLE")
            return
        }
        Log.d(TAG, "Starting SpeechRecognizer.")
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening for command...") // Optional: some devices show this
        }
        speechRecognizer.startListening(intent)
    }

    private fun stopSpeechRecognizer() {
        // Check if SR is active before trying to stop/cancel to avoid errors if not running.
        // This requires more complex tracking or relying on try-catch.
        // For simplicity, just calling stopListening. Add checks if issues arise.
        Log.d(TAG, "Stopping SpeechRecognizer.")
        speechRecognizer.stopListening() // or speechRecognizer.cancel()
    }

    private fun speakText(text: String, utteranceId: String) {
        Log.d(TAG, "TTS speak: '$text' with ID '$utteranceId'")
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }
    // --- End State Transition and Component Control ---


    // --- Command Processing ---
    private fun processCommand(command: String) {
        // This function is called when currentState is PROCESSING_AND_SPEAKING
        Log.d(TAG, "Processing command: '$command'")

        val createLogPattern = Pattern.compile("create a log for beehive (\\d+) (.+)", Pattern.CASE_INSENSITIVE)
        val createLogMatcher = createLogPattern.matcher(command)

        val readLogPattern = Pattern.compile("(?:get me the|get) last log (?:of |for )?beehive (\\d+)", Pattern.CASE_INSENSITIVE)
        val readLogMatcher = readLogPattern.matcher(command)

        var messageToSpeak: String
        var utteranceSuffix: String

        when {
            createLogMatcher.find() -> {
                try {
                    val hiveNumberStr = createLogMatcher.group(1)
                    val payload = createLogMatcher.group(2)
                    if (hiveNumberStr != null && payload != null) {
                        val hiveNumber = hiveNumberStr.toInt()
                        hiveLogs.getOrPut(hiveNumber) { mutableListOf() }.add(payload.trim())
                        messageToSpeak = "Okay, log created for beehive $hiveNumber."
                        utteranceSuffix = "LogCreated"
                        Log.i(TAG, "$messageToSpeak Payload: '$payload'")
                    } else {
                        throw IllegalArgumentException("Could not parse hive number or payload from command.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing 'create log' command: ${e.message}")
                    messageToSpeak = "Sorry, I couldn't create the log. Please try again."
                    utteranceSuffix = "CreateLogError"
                }
            }
            readLogMatcher.find() -> {
                try {
                    val hiveNumberStr = readLogMatcher.group(1)
                    if (hiveNumberStr != null) {
                        val hiveNumber = hiveNumberStr.toInt()
                        val logsForHive = hiveLogs[hiveNumber]
                        if (logsForHive.isNullOrEmpty()) {
                            messageToSpeak = "No logs found for beehive $hiveNumber."
                            utteranceSuffix = "NoLogsFound"
                        } else {
                            messageToSpeak = logsForHive.last() // Speak the log content
                            utteranceSuffix = "ReadLogData"
                            Log.i(TAG, "Reading last log for beehive $hiveNumber: '$messageToSpeak'")
                        }
                    } else {
                        throw IllegalArgumentException("Could not parse hive number for reading log.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing 'read log' command: ${e.message}")
                    messageToSpeak = "Sorry, I couldn't retrieve the log. Please try again."
                    utteranceSuffix = "ReadLogError"
                }
            }
            else -> {
                messageToSpeak = "Sorry, I didn't understand that command."
                utteranceSuffix = "UnknownCommand"
            }
        }
        speakText(messageToSpeak, UTTERANCE_ID_COMMAND_RESPONSE_PREFIX + utteranceSuffix)
    }
    // --- End Command Processing ---


    // --- Utility Methods ---
    private fun copyAssetToCache(fileName: String): String {
        val cacheFile = File(cacheDir, fileName)
        if (!cacheFile.exists()) {
            try {
                assets.open(fileName).use { inputStream ->
                    FileOutputStream(cacheFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Log.d(TAG, "Copied asset '$fileName' to cache: ${cacheFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Error copying asset '$fileName' to cache: ${e.message}", e)
                throw e // Re-throw as this is critical for Porcupine initialization
            }
        }
        return cacheFile.absolutePath
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java) // Assuming MainActivity exists
        val pendingIntentFlags =
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        return NotificationCompat.Builder(this,
            NOTIFICATION_CHANNEL_ID
        )
            .setContentTitle("Beekeeper Assistant")
            .setContentText("Listening for 'Hey Beekeeper'...")
            .setSmallIcon(R.mipmap.ic_launcher) // Ensure this icon exists
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Makes the notification non-dismissable
            .setPriority(NotificationCompat.PRIORITY_LOW) // Less intrusive
            .build()
    }

    private fun setupNotificationChannel() {
        val serviceChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Beekeeper Service Channel",
            NotificationManager.IMPORTANCE_LOW // Use LOW to be less intrusive
        ).apply {
            description = "Channel for Beekeeper Assistant background service"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
        Log.d(TAG, "Notification channel created.")
    }

    private fun runOnUiThread(action: () -> Unit) {
        // Check if already on main thread to avoid unnecessary posting
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private fun getSpeechRecognizerErrorText(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
            SpeechRecognizer.ERROR_CLIENT -> "Other client side error."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions."
            SpeechRecognizer.ERROR_NETWORK -> "Network error."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout."
            SpeechRecognizer.ERROR_NO_MATCH -> "No match found."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy."
            SpeechRecognizer.ERROR_SERVER -> "Error from server."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input."
            else -> "Unknown speech recognizer error."
        }
    }

}