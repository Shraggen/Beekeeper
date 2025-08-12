// File: BeekeeperService.kt
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
import com.bachelorthesis.beekeeperMobile.IntentRecognizer.IntentRecognizer
import com.bachelorthesis.beekeeperMobile.IntentRecognizer.LLMIntentRecognizer
import com.bachelorthesis.beekeeperMobile.IntentRecognizer.RegexIntentRecognizer
import com.bachelorthesis.beekeeperMobile.IntentRecognizer.StructuredIntent
import com.bachelorthesis.beekeeperMobile.models.CreateLogRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

class BeekeeperService : Service(), TextToSpeech.OnInitListener, SpeechEngineListener {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var speechEngine: SpeechEngine? = null
    private var intentRecognizer: IntentRecognizer? = null
    private var textToSpeech: TextToSpeech? = null

    private enum class ServiceState {
        STOPPED,
        INITIALIZING,
        IDLE,
        AWOKEN,
        AWAITING_NOTE,
        PROCESSING,
        SPEAKING
    }
    private var currentState: ServiceState = ServiceState.STOPPED

    // REFACTOR: Add flags to track asynchronous initialization
    private var isTtsReady = false
    private var isIntentRecognizerReady = false
    private var isSpeechEngineReady = false

    private var pendingIntent: StructuredIntent? = null

    //region Service Lifecycle
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Creating...")
        setupNotificationChannel()
        transitionToState(ServiceState.INITIALIZING)

        // Start all initializations in parallel
        textToSpeech = TextToSpeech(this, this)
        speechEngine = SpeechEngine(this, this).apply {
            // REFACTOR: Modify SpeechEngine to report back on successful init
            initialize { success ->
                if (success) {
                    Log.d(TAG, "Speech Engine Initialized successfully.")
                    isSpeechEngineReady = true
                    checkInitializationComplete()
                } else {
                    handleError("Speech Engine could not start", true)
                }
            }
        }

        initializeIntentRecognizerWithFallback()
    }

    private fun initializeIntentRecognizerWithFallback() {
        Log.i(TAG, "Attempting to initialize LLM Intent Recognizer...")
        val llmRecognizer = LLMIntentRecognizer()

        llmRecognizer.initialize(this) { success ->
            if (success) {
                // Success! We use the powerful LLM recognizer.
                Log.i(TAG, "LLM Recognizer initialized successfully. Setting as active recognizer.")
                this.intentRecognizer = llmRecognizer
                isIntentRecognizerReady = true
                checkInitializationComplete()
            } else {
                // Failure! Fall back to the simple and reliable Regex recognizer.
                Log.w(TAG, "LLM Recognizer failed to initialize. Falling back to Regex Recognizer.")
                llmRecognizer.close() // Clean up the failed LLM recognizer

                val regexRecognizer = RegexIntentRecognizer()
                regexRecognizer.initialize(this) {
                    // The regex recognizer initializes instantly and never fails.
                    this.intentRecognizer = regexRecognizer
                    isIntentRecognizerReady = true // We can still signal readiness for the service to start
                    checkInitializationComplete()
                }
            }
        }
    }
    // REFACTOR: Add onStartCommand to call startForeground immediately
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // This is the immediate fix for the crash.
        // We post a notification right away to tell the OS we are a valid foreground service.
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
            textToSpeech?.setOnUtteranceProgressListener(ttsListener)
            Log.d(TAG, "TTS Initialized successfully.")
            isTtsReady = true
            checkInitializationComplete()
        } else {
            handleError("TTS could not be initialized.", true)
        }
    }

    // REFACTOR: New method to check if all async tasks are done
    private fun checkInitializationComplete() {
        // This method is called from every init callback.
        // It only proceeds when ALL components are ready.
        if (isTtsReady && isIntentRecognizerReady && isSpeechEngineReady) {
            Log.i(TAG, "All components initialized. Service is now idle and ready.")
            transitionToState(ServiceState.IDLE)
            // REFACTOR: Explicitly tell the engine to start now that everything is ready.
            speechEngine?.startListeningForHotword()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service Destroying...")
        serviceScope.cancel()
        speechEngine?.destroy()
        intentRecognizer?.close()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
    //endregion

    //region SpeechEngineListener Implementation
    override fun onHotwordDetected() {
        // Only act on the hotword if we are fully idle and ready
        if (currentState == ServiceState.IDLE) {
            transitionToState(ServiceState.AWOKEN)
            speak("Yes?", UTTERANCE_ID_PROMPT_FOR_COMMAND)
        }
    }

    override fun onCommandTranscribed(text: String) {
        // If we were waiting for a note, handle it directly.
        if (currentState == ServiceState.AWAITING_NOTE) {
            saveNoteFromPendingIntent(text)
        } else {
            // Otherwise, it's a new command to be processed by the LLM.
            Log.i(TAG, "Command received: '$text'. Sending to LLM.")
            transitionToState(ServiceState.PROCESSING)
            intentRecognizer?.recognizeIntent(text, ::processStructuredIntent)
        }
    }

    override fun onError(error: String) {
        Log.w(TAG, "TTS onError (deprecated) called for utteranceId: $error")
        handleError(error, isFatal = false)
    }
    //endregion

    //region TTS Listener and Control
    private fun speak(text: String, utteranceId: String) {
        transitionToState(ServiceState.SPEAKING)
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private val ttsListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}

        override fun onDone(utteranceId: String?) {
            serviceScope.launch(Dispatchers.Main) {
                when (utteranceId) {
                    UTTERANCE_ID_PROMPT_FOR_COMMAND -> speechEngine?.startListeningForCommand()
                    UTTERANCE_ID_PROMPT_FOR_NOTE -> {
                        transitionToState(ServiceState.AWAITING_NOTE)
                        speechEngine?.startListeningForCommand()
                    }
                    UTTERANCE_ID_COMMAND_RESPONSE -> transitionToState(ServiceState.IDLE)
                }
            }
        }

        // Deprecated but must be implemented
        override fun onError(utteranceId: String?) {
            Log.w(TAG, "TTS onError (deprecated) called for $utteranceId")
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            handleError("A speech error occurred (code $errorCode).", false)
        }
    }
    //endregion

    // FILLED-IN: The core logic for handling state changes and updating the notification.
    private fun transitionToState(newState: ServiceState) {
        if (currentState == newState) return
        Log.i(TAG, "State Transition: $currentState -> $newState")
        currentState = newState
        updateNotification()
    }

    // FILLED-IN: The core logic for handling the structured intent from the LLM.
    private fun processStructuredIntent(intent: StructuredIntent) {
        Log.d(TAG, "Processing Intent: ${intent.intentName} with entities: ${intent.entities}")
        transitionToState(ServiceState.PROCESSING)

        when (intent.intentName) {
            "create_log" -> handleCreateLog(intent)
            "read_last_log" -> handleReadLastLog(intent)
            "read_last_task" -> handleReadLastTask(intent)
            else -> speak("Sorry, I didn't understand that command.", UTTERANCE_ID_COMMAND_RESPONSE)
        }
    }

    // FILLED-IN: Handlers for each specific intent.
    private fun handleCreateLog(intent: StructuredIntent) {
        val hiveId = intent.entities["hive_id"]?.toIntOrNull()
        val content = intent.entities["content"]

        if (hiveId == null) {
            speak("I understood you want to create a note, but I didn't catch the hive number.", UTTERANCE_ID_COMMAND_RESPONSE)
            return
        }

        if (content.isNullOrBlank()) {
            this.pendingIntent = intent
            speak("Okay, I'm ready to record your note for beehive $hiveId", UTTERANCE_ID_PROMPT_FOR_NOTE)
        } else {
            this.pendingIntent = null
            saveNoteForHive(hiveId, content)
        }
    }

    private fun handleReadLastLog(intent: StructuredIntent) {
        val hiveId = intent.entities["hive_id"]?.toIntOrNull()
        if (hiveId != null) fetchLastLog(hiveId) else speak("Sorry, I couldn't understand which hive number.", UTTERANCE_ID_COMMAND_RESPONSE)
    }

    private fun handleReadLastTask(intent: StructuredIntent) {
        val hiveId = intent.entities["hive_id"]?.toIntOrNull()
        if (hiveId != null) fetchLastTask(hiveId) else speak("Sorry, I couldn't understand which hive number.", UTTERANCE_ID_COMMAND_RESPONSE)
    }

    // FILLED-IN: Logic for handling multi-step note creation.
    private fun saveNoteFromPendingIntent(noteContent: String) {
        val hiveId = pendingIntent?.entities?.get("hive_id")?.toIntOrNull()
        if (hiveId != null) {
            saveNoteForHive(hiveId, noteContent)
        } else {
            handleError("An error occurred. I lost track of which hive to save the note for.", false)
        }
        pendingIntent = null // Clear the pending intent
    }

    // FILLED-IN: Network call to save the note.
    private fun saveNoteForHive(hiveId: Int, content: String) {
        transitionToState(ServiceState.PROCESSING)
        serviceScope.launch {
            try {
                val request = CreateLogRequest(hiveID = hiveId, content = content)
                val response = RetrofitClient.instance.createLog(request)
                if (response.isSuccessful) {
                    speak("Note saved for beehive $hiveId", UTTERANCE_ID_COMMAND_RESPONSE)
                } else {
                    handleError("The server had an error saving the note.", false)
                }
            } catch (e: Exception) {
                handleError("There was a network error trying to save the note.", false)
            }
        }
    }

    // FILLED-IN: Network call to fetch the last log.
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
                handleError("Network error while getting notes.", false)
            }
        }
    }

    // FILLED-IN: Network call to fetch the last task.
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
                handleError("Network error while getting tasks.", false)
            }
        }
    }

    // FILLED-IN: Centralized error handling logic.
    private fun handleError(errorMessage: String, isFatal: Boolean) {
        Log.e(TAG, "ERROR: $errorMessage (isFatal: $isFatal)")
        // Don't get stuck in an error loop. Always go back to IDLE unless it's fatal.
        if (!isFatal) {
            speak("There was an error: $errorMessage", UTTERANCE_ID_COMMAND_RESPONSE)
        } else {
            // For fatal errors, just log and stop.
            stopSelf()
        }
    }

    // FILLED-IN: Required dummy implementation.
    override fun onBind(intent: Intent?): IBinder? = null

    // FILLED-IN: Notification helper functions.
    private fun setupNotificationChannel() {
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "Beekeeper Service Channel", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Beekeeper Assistant")
            .setContentText(getNotificationText())
            .setSmallIcon(R.mipmap.ic_launcher) // Ensure you have this icon
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
        ServiceState.AWAITING_NOTE -> "Ready to record your note..."
        ServiceState.PROCESSING -> "Thinking..."
        ServiceState.SPEAKING -> "Speaking..."
    }

    // FILLED-IN: Companion object with all necessary constants.
    companion object {
        private const val TAG = "BeekeeperService"
        private const val NOTIFICATION_CHANNEL_ID = "BeekeeperServiceChannel"
        private const val NOTIFICATION_ID = 1

        private const val UTTERANCE_ID_PROMPT_FOR_COMMAND = "PROMPT_FOR_COMMAND"
        private const val UTTERANCE_ID_PROMPT_FOR_NOTE = "PROMPT_FOR_NOTE"
        private const val UTTERANCE_ID_COMMAND_RESPONSE = "COMMAND_RESPONSE"
    }
}