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
 *
 * @author shraggen (Original)
 * @author Gemini (Vosk Refactor & Task Implementation)
 */
class BeekeeperService : Service(), RecognitionListener, TextToSpeech.OnInitListener {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Vosk Components
    private var model: Model? = null
    private var speechService: SpeechService? = null

    // Android TextToSpeech
    private var textToSpeech: TextToSpeech? = null

    // State Management
    private var currentState: ServiceState = ServiceState.STOPPED
    private var pendingHiveNumber = -1 // Used for the multi-step "create log" command

    private enum class ServiceState {
        STOPPED, INITIALIZING, IDLE, AWOKEN, LISTENING, AWAITING_NOTE, PROCESSING
    }

    //region Service Lifecycle & State Machine
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Creating...")
        setupNotificationChannel()
        transitionToState(ServiceState.INITIALIZING)

        textToSpeech = TextToSpeech(this, this)
        LibVosk.setLogLevel(LogLevel.INFO)
        initVoskModel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand received.")
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service Destroying...")
        currentState = ServiceState.STOPPED
        serviceScope.cancel()

        speechService?.stop()
        speechService?.shutdown()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun transitionToState(newState: ServiceState) {
        serviceScope.launch(Dispatchers.Main) {
            if (currentState == newState) return@launch

            Log.i(TAG, "State Transition: $currentState -> $newState")
            currentState = newState
            updateNotification()

            when (newState) {
                ServiceState.IDLE -> {
                    pendingHiveNumber = -1
                    startRecognition(COMMAND_GRAMMAR)
                    pauseRecognition(false)
                }
                ServiceState.AWOKEN -> {
                    pauseRecognition(true)
                    speak("Yes?", UTTERANCE_ID_PROMPT_FOR_COMMAND)
                }
                ServiceState.LISTENING, ServiceState.AWAITING_NOTE -> {
                    // State entered via TTS callback, just unpause recognition
                    val grammar = if (newState == ServiceState.AWAITING_NOTE) null else COMMAND_GRAMMAR
                    startRecognition(grammar)
                    pauseRecognition(false)
                }
                ServiceState.PROCESSING -> pauseRecognition(true)
                ServiceState.STOPPED -> stopSelf()
                ServiceState.INITIALIZING -> { /* Wait for init to complete */ }
            }
        }
    }
    //endregion

    //region Initialization
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
            textToSpeech?.setOnUtteranceProgressListener(ttsListener)
            Log.d(TAG, "TTS Initialized successfully.")
        } else {
            Log.e(TAG, "TTS Initialization failed with status: $status")
            handleError("TTS could not be initialized.", true)
        }
    }

    private fun initVoskModel() {
        serviceScope.launch {
            StorageService.unpack(this@BeekeeperService, "model-en-us", "model",
                { loadedModel: Model? ->
                    this@BeekeeperService.model = loadedModel
                    Log.d(TAG, "Vosk Model Initialized successfully.")
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
    override fun onPartialResult(hypothesis: String?) {}

    override fun onResult(hypothesis: String?) {
        if (hypothesis.isNullOrEmpty()) return

        try {
            val resultJson = JSONObject(hypothesis)
            val text = resultJson.getString("text").lowercase(Locale.US)

            if (text.isBlank()) return

            Log.i(TAG, "Recognized text: '$text' in state: $currentState")

            when (currentState) {
                ServiceState.IDLE, ServiceState.LISTENING -> {
                    if (text.contains(WAKE_WORD)) {
                        transitionToState(ServiceState.AWOKEN)
                    } else if (currentState == ServiceState.LISTENING) {
                        processCommand(text)
                    }
                }
                ServiceState.AWAITING_NOTE -> saveNote(text)
                else -> Log.w(TAG, "Ignoring recognition in unhandled state: $currentState")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not parse final result JSON: $hypothesis", e)
        }
    }

    override fun onFinalResult(hypothesis: String?) {
        // If we are in a state that should be listening, restart the recognizer
        if (currentState == ServiceState.IDLE || currentState == ServiceState.LISTENING) {
            startRecognition(COMMAND_GRAMMAR)
        }
    }

    override fun onTimeout() {
        Log.w(TAG, "Recognition timeout in state: $currentState")
        transitionToState(ServiceState.IDLE)
    }

    override fun onError(e: Exception?) {
        handleError("Recognition error: ${e?.message}", false)
    }
    //endregion

    //region Command Processing Logic
    private fun processCommand(command: String) {
        transitionToState(ServiceState.PROCESSING)

        when {
            command.startsWith(CMD_CREATE_PREFIX) -> handleCreatenote(command)
            command.startsWith(CMD_READ_LOG_PREFIX) -> handleReadLog(command)
            command.startsWith(CMD_READ_TASK_PREFIX) -> handleReadTask(command)
            command == CMD_HELP_1 || command == CMD_HELP_2 -> handleHelp()
            else -> speak("Sorry, I didn't understand that command.", UTTERANCE_ID_COMMAND_RESPONSE)
        }
    }

    private fun handleCreatenote(command: String) {
        val numberStr = command.substring(CMD_CREATE_PREFIX.length).trim()
        val hiveNumber = convertSpokenNumberToInt(numberStr)
        if (hiveNumber != -1) {
            pendingHiveNumber = hiveNumber
            speak("Okay, I'm ready to record your note for beehive $hiveNumber", UTTERANCE_ID_PROMPT_FOR_NOTE)
        } else {
            speak("Sorry, I couldn't understand the hive number.", UTTERANCE_ID_COMMAND_RESPONSE)
        }
    }

    private fun handleReadLog(command: String) {
        val numberStr = command.substring(CMD_READ_LOG_PREFIX.length).trim()
        val hiveNumber = convertSpokenNumberToInt(numberStr)
        if (hiveNumber != -1) {
            fetchLastLog(hiveNumber)
        } else {
            speak("Sorry, I couldn't understand the hive number.", UTTERANCE_ID_COMMAND_RESPONSE)
        }
    }

    private fun handleReadTask(command: String) {
        val numberStr = command.substring(CMD_READ_TASK_PREFIX.length).trim()
        val hiveNumber = convertSpokenNumberToInt(numberStr)
        if (hiveNumber != -1) {
            fetchLastTask(hiveNumber)
        } else {
            speak("Sorry, I couldn't understand the hive number.", UTTERANCE_ID_COMMAND_RESPONSE)
        }
    }

    private fun handleHelp() {
        val helpText = "You can say: note for beehive 10, last note for beehive 12, or, last task for beehive 15."
        speak(helpText, UTTERANCE_ID_COMMAND_RESPONSE)
    }

    private fun fetchLastLog(hiveId: Int) {
        serviceScope.launch {
            try {
                val response = RetrofitClient.instance.getLastLogForHive(hiveId)
                val responseText = if (response.isSuccessful && !response.body()?.content.isNullOrEmpty()) {
                    "The last note is: ${response.body()?.content}"
                } else {
                    "No notes found for beehive $hiveId."
                }
                speak(responseText, UTTERANCE_ID_COMMAND_RESPONSE)
            } catch (e: Exception) {
                Log.e(TAG, "Network error fetching log", e)
                handleError("Network error while getting notes.", false)
            }
        }
    }

    private fun fetchLastTask(hiveId: Int) {
        serviceScope.launch {
            try {
                val response = RetrofitClient.instance.getLastTaskForHive(hiveId)
                val responseText = if (response.isSuccessful && !response.body()?.content.isNullOrEmpty()) {
                    "The last task is: ${response.body()?.content}"
                } else {
                    "No tasks found for beehive $hiveId."
                }
                speak(responseText, UTTERANCE_ID_COMMAND_RESPONSE)
            } catch (e: Exception) {
                Log.e(TAG, "Network error fetching task", e)
                handleError("Network error while getting tasks.", false)
            }
        }
    }

    private fun saveNote(note: String) {
        transitionToState(ServiceState.PROCESSING)
        if (pendingHiveNumber == -1 || note.isBlank()) {
            handleError("There was an error saving the note.", false)
            return
        }

        val hiveId = pendingHiveNumber
        val noteContent = note.trim()
        pendingHiveNumber = -1

        serviceScope.launch {
            try {
                val request = CreateLogRequest(hiveID = hiveId, content = noteContent)
                val response = RetrofitClient.instance.createLog(request)
                if (response.isSuccessful) {
                    Log.i(TAG, "Note recorded for hive $hiveId. Payload: '$noteContent'")
                    speak("Note saved for beehive $hiveId", UTTERANCE_ID_COMMAND_RESPONSE)
                } else {
                    Log.e(TAG, "Server error saving note: ${response.errorBody()?.string()}")
                    handleError("Server had an error saving the note.", false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network error saving note", e)
                handleError("Network error trying to save the note.", false)
            }
        }
    }
    //endregion

    //region Core Control (Vosk, TTS, Error)
    private fun startRecognition(grammar: String?) {
        if (model == null) {
            Log.e(TAG, "Model not initialized, cannot start recognition.")
            return
        }
        speechService?.stop()
        try {
            val recognizer = if (grammar != null) Recognizer(model, 16000.0f, grammar) else Recognizer(model, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(this)
            Log.i(TAG, "Recognition started. Grammar: ${grammar != null}")
        } catch (e: IOException) {
            handleError("Error starting recognition: ${e.message}", true)
        }
    }

    private fun pauseRecognition(isPaused: Boolean) {
        speechService?.setPause(isPaused)
    }

    private fun speak(text: String, utteranceId: String) {
        pauseRecognition(true)
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private val ttsListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}
        override fun onDone(utteranceId: String?) {
            serviceScope.launch(Dispatchers.Main) {
                when (utteranceId) {
                    UTTERANCE_ID_PROMPT_FOR_COMMAND -> transitionToState(ServiceState.LISTENING)
                    UTTERANCE_ID_PROMPT_FOR_NOTE -> transitionToState(ServiceState.AWAITING_NOTE)
                    UTTERANCE_ID_COMMAND_RESPONSE -> transitionToState(ServiceState.IDLE)
                }
            }
        }
        override fun onError(utteranceId: String?) { /* Deprecated */ }
        override fun onError(utteranceId: String?, errorCode: Int) {
            serviceScope.launch(Dispatchers.Main) {
                Log.e(TAG, "TTS Error for $utteranceId, code: $errorCode.")
                transitionToState(ServiceState.IDLE)
            }
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

    //region Helper Functions
    /**
     * Converts a spoken number (e.g., "one", "twelve") or a digit string ("1", "12") to an Int.
     * Returns -1 if the conversion fails.
     */
    private fun convertSpokenNumberToInt(text: String?): Int {
        if (text.isNullOrBlank()) return -1

        try {
            // First, try direct conversion for digits "10", "12", etc.
            return text.trim().toInt()
        } catch (e: NumberFormatException) {
            // Fallback to word parsing for "ten", "twelve", etc.
            return Companion.numberMap[text.lowercase(Locale.US).trim()] ?: -1
        }
    }

    private fun setupNotificationChannel() {
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "Beekeeper Service Channel", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Beekeeper Assistant")
            .setContentText(getNotificationText())
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, createNotification())
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
    //endregion

    companion object {
        private const val TAG = "BeekeeperService"
        private const val NOTIFICATION_CHANNEL_ID = "BeekeeperServiceChannel"
        private const val NOTIFICATION_ID = 1

        // Utterance IDs for TTS callbacks
        private const val UTTERANCE_ID_PROMPT_FOR_COMMAND = "PROMPT_FOR_COMMAND"
        private const val UTTERANCE_ID_PROMPT_FOR_NOTE = "PROMPT_FOR_NOTE"
        private const val UTTERANCE_ID_COMMAND_RESPONSE = "COMMAND_RESPONSE"

        // Voice commands
        private const val WAKE_WORD = "hey beekeeper"
        private const val CMD_CREATE_PREFIX = "note for beehive "
        private const val CMD_READ_LOG_PREFIX = "last note for beehive "
        private const val CMD_READ_TASK_PREFIX = "last task for beehive "
        private const val CMD_HELP_1 = "help"
        private const val CMD_HELP_2 = "what can i say"

        // This grammar string tells Vosk to prioritize these words, improving accuracy.
        private val COMMAND_GRAMMAR = """
            ["$WAKE_WORD", "$CMD_CREATE_PREFIX", "$CMD_READ_LOG_PREFIX", "$CMD_READ_TASK_PREFIX", "$CMD_HELP_1", "$CMD_HELP_2",
             "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "[unk]"]
        """.trimIndent()

        // Map for converting spoken numbers to integers.
        private val numberMap = mapOf(
            "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
            "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12
        )
    }
}