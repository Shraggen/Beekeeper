package com.bachelorthesis.beekeeperMobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bachelorthesis.beekeeperMobile.models.CreateLogRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException
import java.util.Locale

/**
 * ## BeekeeperService (Vosk Refactor)
 *
 * This service is responsible for background wake-word and command listening using the Vosk engine.
 * It has been refactored to replace Porcupine with Vosk, incorporating the logic from the
 * Vosk demo activity into a stable, long-running foreground service.
 *
 * ### Key Components:
 * 1.  **Vosk `SpeechService`:** Manages continuous audio capture and speech recognition. It's
 * restarted after each final recognition result to ensure it's always listening.
 * 2.  **Vosk `Model`:** The speech recognition model is unpacked from the assets folder on
 * service creation.
 * 3.  **State Machine:** A robust state machine (`ServiceState`) controls the service's behavior,
 * transitioning between listening for the wake-word, listening for commands, and handling
 * multi-step actions like taking a note.
 * 4.  **TextToSpeech (TTS):** Used to provide voice feedback to the user. The TTS `onDone`
 * callback is crucial for triggering state transitions after a prompt has been spoken.
 * 5.  **Coroutines:** Used for all asynchronous operations, including network requests for
 * creating and fetching logs, to avoid blocking the main thread.
 *
 * @author shraggen (Original)
 * @author Gemini (Vosk Refactor)
 */
class BeekeeperService : Service(), RecognitionListener, TextToSpeech.OnInitListener {

    // A coroutine scope for all background tasks in the service.
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Vosk Components
    private var model: Model? = null
    private var speechService: SpeechService? = null

    // Android TextToSpeech
    private var textToSpeech: TextToSpeech? = null

    // State Management
    private var currentState: ServiceState = ServiceState.STOPPED
    private var pendingHiveNumber = -1 // Used for the multi-step "create log" command

    //region State Machine
    private enum class ServiceState {
        STOPPED,
        INITIALIZING,
        IDLE,           // Listening for wake word (with grammar)
        AWOKEN,         // Wake word detected, prompting for command
        LISTENING,      // Listening for a command (with grammar)
        AWAITING_NOTE,  // Ready to listen for free-form note dictation
        PROCESSING      // Processing command/note and speaking response
    }

    /**
     * The main state transition function. Manages the service's behavior.
     */
    private fun transitionToState(newState: ServiceState) {
        // Run on the main thread to prevent race conditions.
        serviceScope.launch(Dispatchers.Main) {
            if (currentState == newState) return@launch

            Log.i(TAG, "State Transition: $currentState -> $newState")
            currentState = newState
            updateNotification() // Update the notification text with the new state

            when (newState) {
                ServiceState.IDLE -> {
                    pendingHiveNumber = -1 // Clear any pending actions
                    startRecognition(COMMAND_GRAMMAR) // Listen for wake word and commands
                    pauseRecognition(false)
                }
                ServiceState.AWOKEN -> {
                    pauseRecognition(true)
                    speak(if (isGerman()) "Ja?" else "Yes?", UTTERANCE_ID_PROMPT_FOR_COMMAND)
                }
                ServiceState.LISTENING -> {
                    // This state is entered via the TTS onDone callback after the prompt
                    pauseRecognition(false)
                }
                ServiceState.AWAITING_NOTE -> {
                    // This state is also entered via TTS onDone after the "record your note" prompt
                    startRecognition(null) // Listen for free-form dictation
                }
                ServiceState.PROCESSING -> {
                    pauseRecognition(true)
                }
                ServiceState.STOPPED -> {
                    speechService?.stop()
                    speechService = null
                    stopSelf()
                }
                ServiceState.INITIALIZING -> {
                    // Do nothing, waiting for init to complete
                }
            }
        }
    }
    //endregion

    //region Service Lifecycle
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Creating...")
        setupNotificationChannel()
        transitionToState(ServiceState.INITIALIZING)

        // Initialize TTS and Vosk Model
        textToSpeech = TextToSpeech(this, this)
        LibVosk.setLogLevel(LogLevel.INFO)
        initVoskModel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand received.")
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service Destroying...")
        currentState = ServiceState.STOPPED

        // Gracefully shut down all components.
        speechService?.stop()
        speechService?.shutdown()
        speechService = null

        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null

        model = null

        // Cancel all coroutines associated with this service.
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    //endregion

    //region Initialization
    /**
     * Initializes TextToSpeech engine.
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = getActiveLocale()
            textToSpeech?.setOnUtteranceProgressListener(ttsListener)
            Log.d(TAG, "TTS Initialized successfully.")
        } else {
            Log.e(TAG, "TTS Initialization failed with status: $status")
            handleError("TTS could not be initialized.", true)
        }
    }

    /**
     * Unpacks and loads the Vosk model from assets.
     */
    private fun initVoskModel() {
        // Model unpacking is an I/O operation, run it with the serviceScope
        serviceScope.launch {
            StorageService.unpack(this@BeekeeperService, "model-en-us", "model",
                { loadedModel: Model? ->
                    this@BeekeeperService.model = loadedModel
                    Log.d(TAG, "Vosk Model Initialized successfully.")
                    // Once the model is ready, transition to IDLE state to start listening.
                    transitionToState(ServiceState.IDLE)
                },
                { exception: IOException ->
                    handleError("Failed to unpack the Vosk model: ${exception.message}", true)
                }
            )
        }
    }
    //endregion

    //region Vosk RecognitionListener
    /**
     * Called when a partial recognition result is available.
     * We don't use this for command processing.
     */
    override fun onPartialResult(hypothesis: String?) {}

    /**
     * This is the main callback for handling recognized speech.
     */
    override fun onResult(hypothesis: String?) {
        if (hypothesis.isNullOrEmpty()) return

        try {
            val resultJson = JSONObject(hypothesis)
            val text = resultJson.getString("text").lowercase(getActiveLocale())

            if (text.isBlank()) return // Ignore empty recognitions

            Log.i(TAG, "Recognized text: '$text' in state: $currentState")

            when (currentState) {
                ServiceState.IDLE, ServiceState.LISTENING -> {
                    // In IDLE state, we listen for the wake word.
                    // In LISTENING state, we listen for a command.
                    if (text.contains(WAKE_WORD)) {
                        transitionToState(ServiceState.AWOKEN)
                    } else if (currentState == ServiceState.LISTENING) {
                        processCommand(text)
                    }
                }
                ServiceState.AWAITING_NOTE -> {
                    saveNote(text)
                }
                else -> {
                    Log.w(TAG, "Ignoring recognition result in unhandled state: $currentState")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Could not parse final result JSON: $hypothesis", e)
        }
    }

    /**
     * Called when the recognizer times out.
     * We go back to the IDLE state to wait for the wake word again.
     */
    override fun onTimeout() {
        Log.w(TAG, "Recognition timeout in state: $currentState")
        transitionToState(ServiceState.IDLE)
    }

    /**
     * Called when a final recognition result is available.
     * This is where we restart recognition to ensure the service is always listening.
     */
    override fun onFinalResult(hypothesis: String?) {
        // If we are in a state that should be listening, restart the recognizer.
        if (isListeningState()) {
            val grammar = if (currentState == ServiceState.AWAITING_NOTE) null else COMMAND_GRAMMAR
            startRecognition(grammar)
        }
    }

    /**
     * Handles errors from the SpeechService.
     */
    override fun onError(e: Exception?) {
        handleError("Recognition error: ${e?.message}", false)
    }
    //endregion

    //region Command and State Logic
    /**
     * Processes a recognized command string.
     */
    private fun processCommand(command: String) {
        transitionToState(ServiceState.PROCESSING)
        val commandLower = command.lowercase(getActiveLocale()).trim()

        when {
            commandLower.startsWith(CMD_CREATE_PREFIX) -> {
                val numberStr = commandLower.substring(CMD_CREATE_PREFIX.length).trim()
                val hiveNumber = convertSpokenNumberToInt(numberStr)
                if (hiveNumber != -1) {
                    pendingHiveNumber = hiveNumber
                    val prompt = if (isGerman()) "Okay, ich bin bereit für den Eintrag für Bienenstock $hiveNumber"
                    else "Okay, I'm ready to record your entry for beehive $hiveNumber"
                    speak(prompt, UTTERANCE_ID_PROMPT_FOR_NOTE)
                } else {
                    speak(if (isGerman()) "Entschuldigung, ich konnte die Bienenstocknummer nicht verstehen."
                    else "Sorry, I couldn't understand the hive number.", UTTERANCE_ID_COMMAND_RESPONSE)
                }
            }
            commandLower.startsWith(CMD_READ_PREFIX) -> {
                val numberStr = commandLower.substring(CMD_READ_PREFIX.length).trim()
                val hiveNumber = convertSpokenNumberToInt(numberStr)
                if (hiveNumber != -1) {
                    // Launch a coroutine to fetch data from the network
                    serviceScope.launch {
                        try {
                            val apiResponse = RetrofitClient.instance.getHive(hiveNumber)
                            val responseText = if (apiResponse.isSuccessful) {
                                val lastLog = apiResponse.body()?.logs?.lastOrNull()?.content
                                if (lastLog.isNullOrEmpty()) {
                                    if (isGerman()) "Keine Notizen für Bienenstock $hiveNumber gefunden."
                                    else "No notes found for beehive $hiveNumber."
                                } else {
                                    (if (isGerman()) "Die letzte Notiz ist: " else "The last note is: ") + lastLog
                                }
                            } else {
                                if (isGerman()) "Konnte Bienenstock $hiveNumber nicht finden."
                                else "Could not find hive $hiveNumber."
                            }
                            speak(responseText, UTTERANCE_ID_COMMAND_RESPONSE)
                        } catch (e: Exception) {
                            Log.e(TAG, "Network error while getting hive", e)
                            handleError("Network error while getting notes.", false)
                        }
                    }
                } else {
                    speak(if (isGerman()) "Entschuldigung, ich konnte die Bienenstocknummer nicht verstehen."
                    else "Sorry, I couldn't understand the hive number.", UTTERANCE_ID_COMMAND_RESPONSE)
                }
            }
            commandLower == CMD_HELP_1 || commandLower == CMD_HELP_2 -> {
                val helpText = if (isGerman()) "Sie können sagen: Eintrag für Bienenstock 10, oder, letzte Notiz für Bienenstock 12 lesen."
                else "You can say: entry for beehive 10, or, read last note for beehive 12."
                speak(helpText, UTTERANCE_ID_COMMAND_RESPONSE)
            }
            else -> {
                speak(if (isGerman()) "Entschuldigung, diesen Befehl habe ich nicht verstanden."
                else "Sorry, I didn't understand that command.", UTTERANCE_ID_COMMAND_RESPONSE)
            }
        }
    }

    /**
     * Saves the dictated note after the "create log" command.
     */
    private fun saveNote(note: String) {
        transitionToState(ServiceState.PROCESSING)
        if (pendingHiveNumber != -1 && note.isNotEmpty()) {
            val hiveId = pendingHiveNumber
            val noteContent = note.trim()
            pendingHiveNumber = -1 // Reset immediately

            // Launch a coroutine to send the data to the server
            serviceScope.launch {
                try {
                    val request = CreateLogRequest(hiveID = hiveId, content = noteContent)
                    val response = RetrofitClient.instance.createLog(request)
                    if (response.isSuccessful) {
                        Log.i(TAG, "Note recorded for hive $hiveId. Payload: '$noteContent'")
                        speak(if (isGerman()) "Notiz für Bienenstock $hiveId gespeichert"
                        else "Note saved for beehive $hiveId", UTTERANCE_ID_COMMAND_RESPONSE)
                    } else {
                        Log.e(TAG, "Server error saving note: ${response.errorBody()?.string()}")
                        handleError("Server had an error saving the note.", false)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Network error while saving note", e)
                    handleError("Network error trying to save the note.", false)
                }
            }
        } else {
            handleError("There was an error saving the note.", false)
        }
    }

    private fun handleError(errorMessage: String, isFatal: Boolean) {
        Log.e(TAG, "ERROR: $errorMessage")
        speak("Error: $errorMessage", UTTERANCE_ID_COMMAND_RESPONSE)
        if (isFatal) {
            transitionToState(ServiceState.STOPPED)
        }
    }
    //endregion

    //region Component Control
    /**
     * Starts or restarts the speech recognition service.
     * @param grammar A JSON string of allowed words/phrases, or null for free-form dictation.
     */
    private fun startRecognition(grammar: String?) {
        if (model == null) {
            Log.e(TAG, "Model not initialized, cannot start recognition.")
            return
        }
        // Stop any existing service before creating a new one
        speechService?.stop()
        speechService = null
        try {
            val recognizer = if (grammar != null) {
                Recognizer(model, 16000.0f, grammar)
            } else {
                Recognizer(model, 16000.0f)
            }
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(this)
            Log.i(TAG, "Recognition started. Grammar: ${grammar != null}")
        } catch (e: IOException) {
            handleError("Error starting recognition: ${e.message}", true)
        }
    }

    /**
     * Pauses or resumes the recognition service.
     */
    private fun pauseRecognition(isPaused: Boolean) {
        speechService?.setPause(isPaused)
        Log.d(TAG, "Recognition paused: $isPaused")
    }

    /**
     * Speaks the given text using TTS.
     */
    private fun speak(text: String, utteranceId: String) {
        if (textToSpeech?.isSpeaking == true) {
            textToSpeech?.stop()
        }
        // Pause recognition while speaking
        pauseRecognition(true)
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private val ttsListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}

        override fun onDone(utteranceId: String?) {
            // All state transitions must happen on the main thread.
            serviceScope.launch(Dispatchers.Main) {
                Log.d(TAG, "TTS onDone for $utteranceId. Current state: $currentState")
                when (utteranceId) {
                    UTTERANCE_ID_PROMPT_FOR_COMMAND -> {
                        // After saying "Yes?", transition to listening for the command.
                        transitionToState(ServiceState.LISTENING)
                    }
                    UTTERANCE_ID_PROMPT_FOR_NOTE -> {
                        // After prompting for the note, transition to awaiting the note.
                        transitionToState(ServiceState.AWAITING_NOTE)
                    }
                    UTTERANCE_ID_COMMAND_RESPONSE -> {
                        // After any other response, go back to the idle state.
                        transitionToState(ServiceState.IDLE)
                    }
                }
            }
        }

        @Deprecated("Deprecated in Java", ReplaceWith("TODO(\"Not yet implemented\")"))
        override fun onError(utteranceId: String?) {
            TODO("Not yet implemented")
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            serviceScope.launch(Dispatchers.Main) {
                Log.e(TAG, "TTS Error for $utteranceId, code: $errorCode.")
                // On any TTS error, it's safest to return to the idle state.
                transitionToState(ServiceState.IDLE)
            }
        }
    }
    //endregion

    //region Helper Functions
    private fun isListeningState(): Boolean {
        return currentState == ServiceState.IDLE ||
                currentState == ServiceState.LISTENING ||
                currentState == ServiceState.AWAITING_NOTE
    }

    private fun convertSpokenNumberToInt(text: String?): Int {
        if (text.isNullOrEmpty()) return -1
        try {
            // First, try direct conversion for digits "10", "12", etc.
            return text.trim().toInt()
        } catch (e: NumberFormatException) {
            // Fallback to word parsing for "ten", "twelve", etc.
        }
        val numberMap = mapOf(
            "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
            "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12
        )
        // Simple lookup for single words. For complex numbers, this would need expansion.
        return numberMap[text.lowercase(Locale.ROOT).trim()] ?: -1
    }

    private fun setupNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Beekeeper Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Beekeeper Assistant")
            .setContentText(getNotificationText()) // Initial text
            .setSmallIcon(R.mipmap.ic_launcher) // Make sure you have this icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val notification = createNotification()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun getNotificationText(): String = when (currentState) {
        ServiceState.STOPPED -> "Service is stopped."
        ServiceState.INITIALIZING -> "Initializing..."
        ServiceState.IDLE -> "Listening for 'hey beekeeper'..."
        ServiceState.AWOKEN -> "Awake! How can I help?"
        ServiceState.LISTENING -> "Listening for your command..."
        ServiceState.AWAITING_NOTE -> "Ready to record your note..."
        ServiceState.PROCESSING -> "Processing your request..."
    }

    companion object {
        private const val TAG = "BeekeeperService_Vosk"
        private const val NOTIFICATION_CHANNEL_ID = "BeekeeperServiceChannel"
        private const val NOTIFICATION_ID = 1

        // Utterance IDs for TTS callbacks
        private const val UTTERANCE_ID_PROMPT_FOR_COMMAND = "PROMPT_FOR_COMMAND"
        private const val UTTERANCE_ID_PROMPT_FOR_NOTE = "PROMPT_FOR_NOTE"
        private const val UTTERANCE_ID_COMMAND_RESPONSE = "COMMAND_RESPONSE"

        // Voice commands
        private const val WAKE_WORD = "hey beekeeper"
        private const val CMD_CREATE_PREFIX = "entry for beehive "
        private const val CMD_READ_PREFIX = "read last note for beehive "
        private const val CMD_HELP_1 = "help"
        private const val CMD_HELP_2 = "what can i say"

        // This grammar string tells Vosk to prioritize these words, improving accuracy.
        private val COMMAND_GRAMMAR = """
            ["$WAKE_WORD", "$CMD_CREATE_PREFIX", "$CMD_READ_PREFIX", "$CMD_HELP_1", "$CMD_HELP_2",
             "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "[unk]"]
        """.trimIndent()

        // Language Configuration
        const val LANG_ENGLISH = "en-US"
        const val LANG_GERMAN = "de-DE"
        var ACTIVE_STT_LANGUAGE = LANG_ENGLISH // Change this to LANG_GERMAN if needed

        fun getActiveLocale(): Locale {
            return if (ACTIVE_STT_LANGUAGE == LANG_GERMAN) Locale.GERMAN else Locale.US
        }

        fun isGerman(): Boolean {
            return ACTIVE_STT_LANGUAGE == LANG_GERMAN
        }
    }
    //endregion
}