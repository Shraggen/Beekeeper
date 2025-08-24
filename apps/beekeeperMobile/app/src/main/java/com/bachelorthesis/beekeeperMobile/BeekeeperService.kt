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
import com.bachelorthesis.beekeeperMobile.assetManager.AssetManager
import com.bachelorthesis.beekeeperMobile.intentRecognizer.IntentRecognizer
import com.bachelorthesis.beekeeperMobile.intentRecognizer.LLMIntentRecognizer
import com.bachelorthesis.beekeeperMobile.intentRecognizer.RegexIntentRecognizer
import com.bachelorthesis.beekeeperMobile.intentRecognizer.StructuredIntent
import com.bachelorthesis.beekeeperMobile.persistance.CreateLogRequest
import com.bachelorthesis.beekeeperMobile.persistance.RetrofitClient
import com.bachelorthesis.beekeeperMobile.speechEngine.SpeechEngine
import com.bachelorthesis.beekeeperMobile.speechEngine.SpeechEngineListener
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
    private lateinit var assetManager: AssetManager

    private enum class ServiceState {
        STOPPED, INITIALIZING, IDLE, AWOKEN, AWAITING_NOTE, PROCESSING, SPEAKING
    }
    private var currentState: ServiceState = ServiceState.STOPPED

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

        assetManager = AssetManager(this)

        if (!assetManager.checkPrerequisites()) {
            Log.e(TAG, "Prerequisites not met. Models are not available.")
            handleError("AI models not found. Please run the app's main screen to download them.", true)
            return
        }

        serviceScope.launch {
            textToSpeech = TextToSpeech(this@BeekeeperService, this@BeekeeperService)
            initializeIntentRecognizerWithFallback()
            initializeSpeechEngine()
        }
    }

    private fun initializeSpeechEngine() {
        speechEngine = SpeechEngine(this, this).apply {
            val voskPath = assetManager.getVoskModelPath().absolutePath
            initialize(voskPath) { success ->
                if (success) {
                    Log.d(TAG, "Speech Engine Initialized successfully.")
                    isSpeechEngineReady = true
                    checkInitializationComplete()
                } else {
                    handleError("Speech Engine could not start", true)
                }
            }
        }
    }

    private fun initializeIntentRecognizerWithFallback() {
        Log.i(TAG, "Attempting to initialize LLM Intent Recognizer...")
        val llmRecognizer = LLMIntentRecognizer()
        val llmPath = assetManager.getLlmModelPath().absolutePath

        llmRecognizer.initialize(this, llmPath) { success ->
            if (success) {
                this.intentRecognizer = llmRecognizer
                isIntentRecognizerReady = true
                checkInitializationComplete()
            } else {
                Log.w(TAG, "LLM Recognizer failed to initialize. Falling back to Regex Recognizer.")
                llmRecognizer.close()
                val regexRecognizer = RegexIntentRecognizer()
                regexRecognizer.initialize(this) {
                    this.intentRecognizer = regexRecognizer
                    isIntentRecognizerReady = true
                    checkInitializationComplete()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
            textToSpeech?.setOnUtteranceProgressListener(ttsListener)
            isTtsReady = true
            checkInitializationComplete()
        } else {
            handleError("TTS could not be initialized.", true)
        }
    }

    private fun checkInitializationComplete() {
        if (isTtsReady && isIntentRecognizerReady && isSpeechEngineReady) {
            Log.i(TAG, "All components initialized. Service is now idle and ready.")
            transitionToState(ServiceState.IDLE)
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
        if (currentState == ServiceState.IDLE) {
            transitionToState(ServiceState.AWOKEN)
            speak("Yes?", UTTERANCE_ID_PROMPT_FOR_COMMAND)
        }
    }

    override fun onCommandTranscribed(text: String) {
        if (currentState == ServiceState.AWAITING_NOTE) {
            saveNoteFromPendingIntent(text)
        } else {
            Log.i(TAG, "Command received: '$text'. Sending to LLM.")
            transitionToState(ServiceState.PROCESSING)
            speak("Processing", UTTERANCE_ID_PROMPT_FOR_COMMAND)
            intentRecognizer?.recognizeIntent(text, ::processStructuredIntent)
        }
    }

    override fun onError(error: String) {
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

        override fun onError(utteranceId: String?) { /* Deprecated */ }
        override fun onError(utteranceId: String?, errorCode: Int) {
            handleError("A speech error occurred (code $errorCode).", false)
        }
    }
    //endregion

    private fun transitionToState(newState: ServiceState) {
        if (currentState == newState) return
        Log.i(TAG, "State Transition: $currentState -> $newState")
        currentState = newState
        updateNotification()
    }

    private fun processStructuredIntent(intent: StructuredIntent) {
        Log.d(TAG, "Processing Intent: ${intent.intentName} with entities: ${intent.entities}")
        transitionToState(ServiceState.PROCESSING)

        when (intent.intentName) {
            "create_log" -> {
                val hiveId = intent.entities["hive_id"]?.toIntOrNull()
                val content = intent.entities["content"]
                if (hiveId != null && !content.isNullOrBlank()) {
                    saveNoteForHive(hiveId, content)
                } else if (hiveId != null) {
                    this.pendingIntent = intent
                    speak("Okay, I'm ready to record your note for beehive $hiveId", UTTERANCE_ID_PROMPT_FOR_NOTE)
                } else {
                    speak("Sorry, I didn't catch the hive number for that note.", UTTERANCE_ID_COMMAND_RESPONSE)
                }
            }
            "read_last_log" -> {
                val hiveId = intent.entities["hive_id"]?.toIntOrNull()
                if (hiveId != null) {
                    fetchLastLog(hiveId)
                } else {
                    speak("Sorry, I couldn't understand which hive number you wanted the last log for.", UTTERANCE_ID_COMMAND_RESPONSE)
                }
            }
            "read_last_task" -> {
                val hiveId = intent.entities["hive_id"]?.toIntOrNull()
                if (hiveId != null) {
                    fetchLastTask(hiveId)
                } else {
                    speak("Sorry, I couldn't understand which hive number you wanted the last task for.", UTTERANCE_ID_COMMAND_RESPONSE)
                }
            }
            else -> {
                speak("Sorry, I didn't understand that command. You can ask me to create a log or read the last log or task.", UTTERANCE_ID_COMMAND_RESPONSE)
            }
        }
    }

    private fun saveNoteFromPendingIntent(noteContent: String) {
        val hiveId = pendingIntent?.entities?.get("hive_id")?.toIntOrNull()
        if (hiveId != null) {
            saveNoteForHive(hiveId, noteContent)
        } else {
            handleError("An error occurred. I lost track of which hive to save the note for.", false)
        }
        pendingIntent = null
    }

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

    private fun handleError(errorMessage: String, isFatal: Boolean) {
        Log.e(TAG, "ERROR: $errorMessage (isFatal: $isFatal)")
        if (!isFatal) {
            speak("There was an error: $errorMessage", UTTERANCE_ID_COMMAND_RESPONSE)
        } else {
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    //region Notification Helpers
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
        ServiceState.AWAITING_NOTE -> "Ready to record your note..."
        ServiceState.PROCESSING -> "Thinking..."
        ServiceState.SPEAKING -> "Speaking..."
    }
    //endregion

    companion object {
        private const val TAG = "BeekeeperService"
        private const val NOTIFICATION_CHANNEL_ID = "BeekeeperServiceChannel"
        private const val NOTIFICATION_ID = 1

        private const val UTTERANCE_ID_PROMPT_FOR_COMMAND = "PROMPT_FOR_COMMAND"
        private const val UTTERANCE_ID_PROMPT_FOR_NOTE = "PROMPT_FOR_NOTE"
        private const val UTTERANCE_ID_COMMAND_RESPONSE = "COMMAND_RESPONSE"
    }
}