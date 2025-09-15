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
import android.util.Log as AndroidLog // Alias for Android's Log to avoid conflict
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bachelorthesis.beekeeperMobile.assetManager.AssetManager
import com.bachelorthesis.beekeeperMobile.intentRecognizer.IntentRecognizer
import com.bachelorthesis.beekeeperMobile.intentRecognizer.StructuredIntent
import com.bachelorthesis.beekeeperMobile.persistance.LogRepository // NEW
import com.bachelorthesis.beekeeperMobile.speechEngine.SpeechEngine
import com.bachelorthesis.beekeeperMobile.speechEngine.SpeechEngineListener
import com.bachelorthesis.beekeeperMobile.sync.SyncWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class BeekeeperService : Service(), TextToSpeech.OnInitListener, SpeechEngineListener {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var speechEngine: SpeechEngine? = null
    private var textToSpeech: TextToSpeech? = null

    // --- INJECTED DEPENDENCIES ---
    @Inject
    lateinit var assetManager: AssetManager // Injected!
    @Inject
    lateinit var intentRecognizer: IntentRecognizer // Injected!
    @Inject
    lateinit var logRepository: LogRepository

    private enum class ServiceState {
        STOPPED, INITIALIZING, IDLE, AWOKEN, AWAITING_NOTE, AWAITING_ANSWER, PROCESSING, SPEAKING
    }
    private var currentState: ServiceState = ServiceState.STOPPED

    private var isTtsReady = false
    private var isIntentRecognizerReady = false
    private var isSpeechEngineReady = false
    private var isDatabaseReady = false // NEW: Track database readiness
    private var isRepositoryReady = false // NEW: Track repository readiness


    private var pendingIntent: StructuredIntent? = null

    private lateinit var preferredLocale: Locale

    //region Service Lifecycle
    override fun onCreate() {
        super.onCreate()
        AndroidLog.d(TAG, "Service Creating...")
        setupNotificationChannel()
        transitionToState(ServiceState.INITIALIZING)

        if (!assetManager.checkPrerequisites()) {
            AndroidLog.e(TAG, "Prerequisites not met. Models are not available.")
            handleError("AI models not found. Please run the app's main screen to download them.", true)
            return
        }

        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        val languageTag = sharedPrefs.getString("preferred_language", "en-US") ?: "en-US"
        preferredLocale = Locale.forLanguageTag(languageTag)
        AndroidLog.d(TAG, "Service started with preferred language: ${preferredLocale.toLanguageTag()}")


        serviceScope.launch {
            // TextToSpeech is tied to the service lifecycle, so we still create it here.
            textToSpeech = TextToSpeech(this@BeekeeperService, this@BeekeeperService)

            // Initialize the injected recognizer
            initializeIntentRecognizer()

            // SpeechEngine is also tied to the service lifecycle (needs a listener).
            // We create it here but pass it its dependencies (which we got from DI).
            initializeSpeechEngine()

            scheduleLogSyncWorker()
        }

    }

    private fun initializeSpeechEngine() {
        // We create it here, but its dependencies are clean.
        speechEngine = SpeechEngine(this, this, assetManager) // Pass the injected AssetManager
        speechEngine?.initialize { success ->
            if (success) {
                AndroidLog.d(TAG, "Speech Engine Initialized successfully.")
                isSpeechEngineReady = true
                checkInitializationComplete()
            } else {
                handleError("Speech Engine could not start", true)
            }
        }
    }

    private fun initializeIntentRecognizer() {
        AndroidLog.i(TAG, "Initializing intent recognizer: ${intentRecognizer.javaClass.simpleName}")

        // The logic for fallback is now handled in the AppModule. Here we just initialize.
        val modelPath = assetManager.getLlmModelPath().absolutePath
        intentRecognizer.initialize(this, modelPath) { success ->
            if (success) {
                isIntentRecognizerReady = true
                checkInitializationComplete()
            } else {
                // This would happen if the LLM model file exists but is corrupt.
                // In this rare case, we can't recover, so we should error out.
                handleError("The primary intent recognizer failed to initialize.", true)
            }
        }
    }

    private fun scheduleLogSyncWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // Only run when network is available
            .build()

        val periodicSyncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            repeatInterval = 1, // Repeat every 1 hour (minimum allowed by WorkManager is 15 minutes)
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing work if already scheduled
            periodicSyncRequest
        )
        AndroidLog.d(TAG, "Log synchronization worker scheduled.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = preferredLocale
            textToSpeech?.setOnUtteranceProgressListener(ttsListener)
            isTtsReady = true
            checkInitializationComplete()
        } else {
            handleError("TTS could not be initialized.", true)
        }
    }

    private fun checkInitializationComplete() {
        // NEW: Include database and repository readiness in overall check
        if (isTtsReady && isIntentRecognizerReady && isSpeechEngineReady && isDatabaseReady && isRepositoryReady) {
            AndroidLog.i(TAG, "All components initialized. Service is now idle and ready.")
            transitionToState(ServiceState.IDLE)
            speechEngine?.startListeningForHotword()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AndroidLog.d(TAG, "Service Destroying...")
        serviceScope.cancel()
        speechEngine?.destroy()
        intentRecognizer.close()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        // No explicit shutdown for Room database needed, as it's typically managed by the app process.
    }
    //endregion

    //region SpeechEngineListener Implementation
    override fun onHotwordDetected() {
        if (currentState == ServiceState.IDLE) {
            transitionToState(ServiceState.AWOKEN)
            speak(getString(R.string.tts_response_yes), UTTERANCE_ID_PROMPT_FOR_COMMAND) // MODIFIED
        }
    }

    override fun onCommandTranscribed(text: String) {
        if (currentState == ServiceState.AWAITING_NOTE) {
            saveNoteFromPendingIntent(text)
        } else {
            AndroidLog.i(TAG, "Command received: '$text'. Sending to LLM.")
            transitionToState(ServiceState.PROCESSING)
            speak(getString(R.string.tts_response_processing), UTTERANCE_ID_PROMPT_FOR_COMMAND) // MODIFIED
            intentRecognizer.recognizeIntent(text, preferredLocale, ::processStructuredIntent)
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
                when {
                    currentState == ServiceState.AWAITING_ANSWER -> {
                        AndroidLog.d(TAG, "TTS finished prompting for answer, now listening for user's response.")
                        speechEngine?.startListeningForCommand()
                    }
                    // MODIFIED: Now we explicitly tell the engine to listen for the hotword
                    // after the final response has been spoken.
                    utteranceId == UTTERANCE_ID_COMMAND_RESPONSE -> {
                        transitionToState(ServiceState.IDLE)
                        speechEngine?.startListeningForHotword() // RE-ENGAGE HOTWORD LISTENING
                    }
                    // Legacy single-turn logic
                    utteranceId == UTTERANCE_ID_PROMPT_FOR_COMMAND -> speechEngine?.startListeningForCommand()
                    utteranceId == UTTERANCE_ID_PROMPT_FOR_NOTE -> {
                        transitionToState(ServiceState.AWAITING_NOTE)
                        speechEngine?.startListeningForCommand()
                    }
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
        AndroidLog.i(TAG, "State Transition: $currentState -> $newState")
        currentState = newState
        updateNotification()
    }

    private fun processStructuredIntent(intent: StructuredIntent) {
        AndroidLog.d(TAG, "Processing Intent: ${intent.intentName}, response: '${intent.responseText}'")
        transitionToState(ServiceState.PROCESSING)

        val responseToSpeak = intent.responseText ?: getString(R.string.tts_error_unknown_command)

        // NEW: Multi-turn conversation logic
        // We check if the intent is 'create_log' but is missing the 'content' entity.
        // This is our signal for a multi-turn conversation.
        if (intent.intentName == "create_log" && !intent.entities.containsKey("content")) {
            // Store the partial intent for later.
            this.pendingIntent = intent
            // Transition to the new state so the ttsListener knows to re-engage the microphone.
            transitionToState(ServiceState.AWAITING_ANSWER)
            speak(responseToSpeak, UTTERANCE_ID_MULTI_TURN_PROMPT) // Use a new utterance ID for clarity
        } else {
            // For all other cases, it's a single-turn command.
            speak(responseToSpeak, UTTERANCE_ID_COMMAND_RESPONSE)

            when (intent.intentName) {
                "create_log" -> {
                    val hiveId = intent.entities["hive_id"]?.toIntOrNull()
                    val content = intent.entities["content"]
                    if (hiveId != null && content != null) {
                        saveNoteForHive(hiveId, content)
                    }
                }
                "read_last_log" -> {
                    val hiveId = intent.entities["hive_id"]?.toIntOrNull()
                    if (hiveId != null) {
                        fetchLastLog(hiveId)
                    }
                }
                "read_last_task" -> {
                    val hiveId = intent.entities["hive_id"]?.toIntOrNull()
                    if (hiveId != null) {
                        fetchLastTask(hiveId)
                    }
                }
                "unknown" -> {
                    // No action needed.
                }
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
        serviceScope.launch {
            if (!::logRepository.isInitialized) {
                handleError("Log repository not initialized, cannot save note.", false)
                return@launch
            }
            try {
                val localLogEntry = logRepository.createLog(hiveId, content)
                AndroidLog.d(TAG, "Note saved locally for beehive $hiveId, local ID: ${localLogEntry.id}")
                // TTS confirmation is now handled by the LLM's responseText.
            } catch (e: Exception) {
                handleError("There was an error saving the note locally: ${e.message}", false)
            }
        }
    }

    private fun fetchLastLog(hiveId: Int) {
        serviceScope.launch {
            if (!::logRepository.isInitialized) {
                handleError("Log repository not initialized, cannot fetch log.", false)
                return@launch
            }
            try {
                val remoteLog = logRepository.fetchLastLogFromRemote(hiveId)
                val responseText = if (remoteLog != null && remoteLog.content.isNotEmpty()) {
                    getString(R.string.tts_response_last_note_is, remoteLog.content)
                } else {
                    getString(R.string.tts_response_no_notes_found, hiveId)
                }
                speak(responseText, UTTERANCE_ID_COMMAND_RESPONSE)
            } catch (e: Exception) {
                handleError("Network error while getting notes: ${e.message}", false)
            }
        }
    }

    private fun fetchLastTask(hiveId: Int) {
        serviceScope.launch {
            if (!::logRepository.isInitialized) {
                handleError("Log repository not initialized, cannot fetch task.", false)
                return@launch
            }
            try {
                val remoteTask = logRepository.fetchLastTaskFromRemote(hiveId)
                val responseText = if (remoteTask != null && remoteTask.content.isNotEmpty()) {
                    getString(R.string.tts_response_last_task_is, remoteTask.content)
                } else {
                    getString(R.string.tts_response_no_tasks_found, hiveId)
                }
                speak(responseText, UTTERANCE_ID_COMMAND_RESPONSE)
            } catch (e: Exception) {
                handleError("Network error while getting tasks: ${e.message}", false)
            }
        }
    }

    private fun handleError(errorMessage: String, isFatal: Boolean) {
        AndroidLog.e(TAG, "ERROR: $errorMessage (isFatal: $isFatal)")
        if (!isFatal) {
            val response = getString(R.string.tts_error_generic, errorMessage) // MODIFIED
            speak(response, UTTERANCE_ID_COMMAND_RESPONSE)
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
        ServiceState.AWAITING_ANSWER -> "Waiting for your response..."
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
        private const val UTTERANCE_ID_MULTI_TURN_PROMPT = "MULTI_TURN_PROMPT"
    }
}