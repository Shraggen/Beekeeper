package com.bachelorthesis.beekeeperMobile

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.util.Locale
import java.util.regex.Pattern

/**
 * ## VoskBeekeeperService
 *
 * A complete, from-scratch implementation of the Beekeeper service using the Vosk open-source
 * speech recognition toolkit for a fully offline, hands-free experience.
 *
 * ### Architecture:
 * - **100% Kotlin Coroutines:** Manages all background tasks, I/O, and audio processing without
 * blocking the main thread.
 * - **Robust State Machine:** A clear state machine (`IDLE`, `LISTENING_FOR_WAKE_WORD`, etc.)
 * controls the service's behavior, ensuring predictable and stable operation.
 * - **Unified Vosk Engine:** A single Vosk model is used for both wake-word detection and
 * full command speech-to-text, providing a consistent recognition experience.
 * - **Manual Audio Handling:** This service manually manages the `AudioRecord` stream, feeding
 * it directly to the Vosk recognizer. This provides full control at the cost of increased
 * code complexity compared to a high-level SDK.
 * - **Modular Logic:** The core business logic (`processCommand`) is decoupled from the
 * voice recognition, making it easy to maintain.
 */
class VoskBeekeeperService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listeningJob: Job? = null

    // Vosk and Audio Components
    private var voskModel: Model? = null
    // audioRecord is now managed locally within each listening job to prevent race conditions.

    // Android Components
    private var textToSpeech: TextToSpeech? = null
    private val hiveLogs: MutableMap<Int, MutableList<String>> = mutableMapOf()

    private var currentState: ServiceState = ServiceState.STOPPED
        set(value) {
            if (field != value) {
                Log.i(TAG, "State Transition: $field -> $value")
                field = value
            }
        }

    //region Lifecycle Methods
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VoskBeekeeperService Creating...")
        currentState = ServiceState.INITIALIZING
        setupNotificationChannel()

        serviceScope.launch {
            try {
                initializeTts()
                initializeVosk()
                withContext(Dispatchers.Main) {
                    transitionToState(ServiceState.IDLE)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fatal initialization error", e)
                withContext(Dispatchers.Main) { stopSelf() }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Vosk service is running...")
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "VoskBeekeeperService Destroying...")
        currentState = ServiceState.STOPPED

        // Cancelling the scope is sufficient. It will cancel all child jobs (like listeningJob)
        // and allow their 'finally' blocks to execute for proper, safe cleanup.
        serviceScope.cancel()

        textToSpeech?.stop()
        textToSpeech?.shutdown()

        voskModel?.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    //endregion

    //region State Machine
    private enum class ServiceState {
        STOPPED,
        INITIALIZING,
        IDLE,
        WAKE_WORD_DETECTED,
        LISTENING_FOR_COMMAND,
        PROCESSING
    }

    private fun transitionToState(newState: ServiceState) {
        // Run state transitions on the main thread for safety.
        serviceScope.launch(Dispatchers.Main) {
            if (currentState == newState) return@launch
            currentState = newState

            // Cancel the previous listening job to ensure only one is active at a time.
            // The 'finally' block within the job will handle its own resource cleanup.
            listeningJob?.cancel()
            listeningJob = null

            when (newState) {
                ServiceState.IDLE -> startListening(isWakeWordMode = true)
                ServiceState.WAKE_WORD_DETECTED -> promptForCommand()
                ServiceState.LISTENING_FOR_COMMAND -> startListening(isWakeWordMode = false)
                ServiceState.PROCESSING -> { /* Processing is handled by command logic */ }
                ServiceState.STOPPED -> stopSelf()
                else -> {}
            }
        }
    }
    //endregion

    //region Initialization
    private suspend fun initializeTts() = withContext(Dispatchers.Main) {
        var ttsReady = false
        textToSpeech = TextToSpeech(this@VoskBeekeeperService) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.US
                textToSpeech?.setOnUtteranceProgressListener(ttsListener)
                ttsReady = true
                Log.d(TAG, "TTS Initialized.")
            } else {
                throw IllegalStateException("TTS failed to initialize.")
            }
        }
        while (!ttsReady) { delay(100) }
    }

    private suspend fun initializeVosk() = withContext(Dispatchers.IO) {
        try {
            // Vosk requires models to be unpacked from assets to storage. This is a
            // one-time, blocking operation. It uses the StorageService from the Vosk library.
            val modelPath = StorageService.sync(
                this@VoskBeekeeperService,
                VOSK_MODEL_ASSET_PATH,
                "model"
            )
            voskModel = Model(modelPath)
            Log.d(TAG, "Vosk model loaded successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Vosk model", e)
            throw e
        }
    }
    //endregion

    //region Core Logic
    private fun startListening(isWakeWordMode: Boolean) {
        if (voskModel == null) {
            Log.e(TAG, "Cannot start listening, Vosk model not loaded.")
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Cannot start listening, RECORD_AUDIO permission not granted.")
            return
        }

        val grammar = if (isWakeWordMode) WAKE_WORD_GRAMMAR else COMMAND_GRAMMAR
        updateNotification(if (isWakeWordMode) "Listening for 'Hey Beekeeper'..." else "Listening for your command...")

        listeningJob = serviceScope.launch {
            // Each listening job now creates and manages its own AudioRecord instance locally.
            // This prevents race conditions between different jobs or with onDestroy.
            var audioRecord: AudioRecord? = null
            val recognizer = Recognizer(voskModel, 16000.0f, grammar)

            try {
                val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord could not be initialized.")
                    return@launch
                }

                audioRecord.startRecording()
                Log.i(TAG, "Vosk listening started. Wake Word Mode: $isWakeWordMode")

                val buffer = ShortArray(AUDIO_BUFFER_SIZE)
                while (isActive) {
                    val numRead = audioRecord.read(buffer, 0, buffer.size)
                    if (recognizer.acceptWaveForm(buffer, numRead)) {
                        val resultJson = recognizer.result
                        val resultText = JSONObject(resultJson).getString("text")
                        if (resultText.isNotBlank()) {
                            Log.i(TAG, "Vosk Result (isFinal=true): '$resultText'")
                            if (isWakeWordMode) {
                                if (resultText == WAKE_WORD) {
                                    withContext(Dispatchers.Main) { transitionToState(ServiceState.WAKE_WORD_DETECTED) }
                                }
                            } else {
                                withContext(Dispatchers.Main) { processCommand(resultText) }
                            }
                            // Stop listening after getting a final result
                            break
                        }
                    }
                }
            } finally {
                // This block ensures cleanup happens even if the coroutine is cancelled.
                // It safely handles its own local AudioRecord instance.
                if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop()
                }
                audioRecord?.release()
                recognizer.close()
                Log.d(TAG, "Vosk listening loop finished and resources released.")
            }
        }
    }

    private fun promptForCommand() {
        speak("Yes?", UTTERANCE_ID_PROMPT_FOR_COMMAND)
    }

    private fun processCommand(command: String) {
        transitionToState(ServiceState.PROCESSING)

        // Convert spoken numbers to digits before matching regex for easier processing.
        val processedCommand = convertNumbersToDigits(command)

        val createLogPattern = Pattern.compile("record note for beehive (\\d+) (.+)", Pattern.CASE_INSENSITIVE)
        val readLogPattern = Pattern.compile("read (?:the )?last note for beehive (\\d+)", Pattern.CASE_INSENSITIVE)
        val readTaskPattern = Pattern.compile("what is the next task for beehive (\\d+)", Pattern.CASE_INSENSITIVE)

        val response: String = when {
            createLogPattern.matcher(processedCommand).find() -> {
                val matcher = createLogPattern.matcher(processedCommand)
                matcher.find()
                val hiveNumber = matcher.group(1)?.toIntOrNull()
                val payload = matcher.group(2)
                if (hiveNumber != null && payload != null) {
                    val actualPayload = payload.replace("[unk]", "").trim()
                    hiveLogs.getOrPut(hiveNumber) { mutableListOf() }.add(actualPayload)
                    "Okay, note recorded for beehive $hiveNumber."
                } else "Sorry, I couldn't understand the command details."
            }
            readLogPattern.matcher(processedCommand).find() -> {
                val matcher = readLogPattern.matcher(processedCommand)
                matcher.find()
                val hiveNumber = matcher.group(1)?.toIntOrNull()
                if (hiveNumber != null) {
                    hiveLogs[hiveNumber]?.lastOrNull()?.let { "The last note is: $it" } ?: "No notes found for beehive $hiveNumber."
                } else "Sorry, I couldn't understand the hive number."
            }
            readTaskPattern.matcher(processedCommand).find() -> "The task feature is not yet implemented."
            else -> "Sorry, I didn't understand that command."
        }
        speak(response, UTTERANCE_ID_COMMAND_RESPONSE)
    }

    private fun convertNumbersToDigits(text: String): String {
        return text
            .replace(" one", " 1")
            .replace(" two", " 2")
            .replace(" three", " 3")
            .replace(" four", " 4")
            .replace(" five", " 5")
            .replace(" six", " 6")
            .replace(" seven", " 7")
            .replace(" eight", " 8")
            .replace(" nine", " 9")
            .replace(" zero", " 0")
            // Handle cases like "one two" -> "1 2" -> "12"
            .replace(Regex("(\\d) (\\d)"), "$1$2")
    }

    //endregion

    //region Listeners and Helpers
    private val ttsListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}
        override fun onError(utteranceId: String?) {}

        override fun onDone(utteranceId: String?) {
            // TTS callbacks are on a binder thread, switch to Main for state changes.
            serviceScope.launch(Dispatchers.Main) {
                when (utteranceId) {
                    UTTERANCE_ID_PROMPT_FOR_COMMAND -> {
                        if (currentState == ServiceState.WAKE_WORD_DETECTED) {
                            transitionToState(ServiceState.LISTENING_FOR_COMMAND)
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
    }

    private fun speak(text: String, utteranceId: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun setupNotificationChannel() {
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "Beekeeper Service", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Beekeeper Assistant")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pIntent)
            .setOngoing(true)
            .build()
    }
    //endregion

    companion object {
        private const val TAG = "VoskBeekeeperService"
        private const val NOTIFICATION_CHANNEL_ID = "VoskBeekeeperServiceChannel"
        private const val NOTIFICATION_ID = 2 // Use a different ID from the old service
        private const val UTTERANCE_ID_PROMPT_FOR_COMMAND = "PROMPT"
        private const val UTTERANCE_ID_COMMAND_RESPONSE = "RESPONSE"

        private const val SAMPLE_RATE = 16000
        private const val AUDIO_BUFFER_SIZE = 4096

        // IMPORTANT: The path in assets where you placed the Vosk model folder.
        // This must match the folder name in your 'models' module's assets.
        private const val VOSK_MODEL_ASSET_PATH = "model-en-us"

        // --- VOSK GRAMMARS ---
        private const val WAKE_WORD = "hey beekeeper"
        private val WAKE_WORD_GRAMMAR = """[ "$WAKE_WORD", "[unk]" ]"""

        // A more open grammar for commands. You can make this more specific
        // to improve accuracy if you have a very limited command set.
        private val COMMAND_GRAMMAR = """
            [ 
                "record note for beehive one two three four five six seven eight nine zero",
                "read the last note for beehive",
                "what is the next task for beehive",
                "[unk]" 
            ]
        """.trimIndent()
    }
}