package com.bachelorthesis.beekeeperMobile.IntentRecognizer

import android.content.Context
import android.util.Log

/**
 * A legacy intent recognizer that uses simple string matching (regex-like)
 * to determine user intent. This serves as a fast, simple, and reliable
 * fallback to the LLM-based recognizer.
 */
class RegexIntentRecognizer : IntentRecognizer {

    // No complex initialization is needed for this simple class.
    override fun initialize(context: Context, onInitialized: (success: Boolean) -> Unit) {
        Log.d("RegexIntentRecognizer", "Regex Intent Recognizer initialized instantly.")
        onInitialized(true)
    }

    override fun recognizeIntent(text: String, onResult: (intent: StructuredIntent) -> Unit) {
        // This is a synchronous operation, so we can determine the result immediately.
        val lowerCaseText = text.lowercase().trim()

        val intent = when {
            // Check for the "create log" command
            lowerCaseText.startsWith(CMD_CREATE_PREFIX) -> {
                val entity = lowerCaseText.substring(CMD_CREATE_PREFIX.length).trim()
                StructuredIntent(
                    intentName = "create_log",
                    entities = mapOf("hive_id" to entity)
                )
            }

            // Check for the "read last log" command
            lowerCaseText.startsWith(CMD_READ_LOG_PREFIX) -> {
                val entity = lowerCaseText.substring(CMD_READ_LOG_PREFIX.length).trim()
                StructuredIntent(
                    intentName = "read_last_log",
                    entities = mapOf("hive_id" to entity)
                )
            }

            // Check for the "read last task" command
            lowerCaseText.startsWith(CMD_READ_TASK_PREFIX) -> {
                val entity = lowerCaseText.substring(CMD_READ_TASK_PREFIX.length).trim()
                StructuredIntent(
                    intentName = "read_last_task",
                    entities = mapOf("hive_id" to entity)
                )
            }

            // Check for help commands
            lowerCaseText == CMD_HELP_1 || lowerCaseText == CMD_HELP_2 -> {
                StructuredIntent(intentName = "help")
            }

            // If no match is found, return the UNKNOWN intent.
            else -> StructuredIntent.UNKNOWN
        }

        // Return the result via the callback.
        onResult(intent)
    }

    // No resources to release.
    override fun close() {
        Log.d("RegexIntentRecognizer", "Regex Intent Recognizer closed.")
    }

    // Companion object to hold the command constants, keeping them self-contained.
    companion object {
        private const val CMD_CREATE_PREFIX = "note for beehive"
        private const val CMD_READ_LOG_PREFIX = "last note for beehive"
        private const val CMD_READ_TASK_PREFIX = "last task for beehive"
        private const val CMD_HELP_1 = "help"
        private const val CMD_HELP_2 = "what can i say"
    }
}