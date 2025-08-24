package com.bachelorthesis.beekeeperMobile.intentRecognizer

import android.content.Context
import android.util.Log
import java.util.Locale

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

    // MODIFIED: Implemented new signature and added logic for Serbian
    override fun recognizeIntent(text: String, locale: Locale, onResult: (intent: StructuredIntent) -> Unit) {
        val lowerCaseText = text.lowercase().trim()

        val intent = if (locale.language == "sr") {
            // Serbian Commands
            when {
                lowerCaseText.startsWith(CMD_CREATE_PREFIX_SR) -> {
                    val entity = lowerCaseText.substring(CMD_CREATE_PREFIX_SR.length).trim()
                    StructuredIntent("create_log", mapOf("hive_id" to entity))
                }
                lowerCaseText.startsWith(CMD_READ_LOG_PREFIX_SR) -> {
                    val entity = lowerCaseText.substring(CMD_READ_LOG_PREFIX_SR.length).trim()
                    StructuredIntent("read_last_log", mapOf("hive_id" to entity))
                }
                lowerCaseText.startsWith(CMD_READ_TASK_PREFIX_SR) -> {
                    val entity = lowerCaseText.substring(CMD_READ_TASK_PREFIX_SR.length).trim()
                    StructuredIntent("read_last_task", mapOf("hive_id" to entity))
                }
                else -> StructuredIntent.UNKNOWN
            }
        } else {
            // English Commands (default)
            when {
                lowerCaseText.startsWith(CMD_CREATE_PREFIX_EN) -> {
                    val entity = lowerCaseText.substring(CMD_CREATE_PREFIX_EN.length).trim()
                    StructuredIntent("create_log", mapOf("hive_id" to entity))
                }
                lowerCaseText.startsWith(CMD_READ_LOG_PREFIX_EN) -> {
                    val entity = lowerCaseText.substring(CMD_READ_LOG_PREFIX_EN.length).trim()
                    StructuredIntent("read_last_log", mapOf("hive_id" to entity))
                }
                lowerCaseText.startsWith(CMD_READ_TASK_PREFIX_EN) -> {
                    val entity = lowerCaseText.substring(CMD_READ_TASK_PREFIX_EN.length).trim()
                    StructuredIntent("read_last_task", mapOf("hive_id" to entity))
                }
                lowerCaseText == CMD_HELP_1_EN || lowerCaseText == CMD_HELP_2_EN -> {
                    StructuredIntent("help")
                }
                else -> StructuredIntent.UNKNOWN
            }
        }

        onResult(intent)
    }

    // No resources to release.
    override fun close() {
        Log.d("RegexIntentRecognizer", "Regex Intent Recognizer closed.")
    }

    // Companion object to hold the command constants, keeping them self-contained.
    companion object {
        // English
        private const val CMD_CREATE_PREFIX_EN = "note for beehive"
        private const val CMD_READ_LOG_PREFIX_EN = "last note for beehive"
        private const val CMD_READ_TASK_PREFIX_EN = "last task for beehive"
        private const val CMD_HELP_1_EN = "help"
        private const val CMD_HELP_2_EN = "what can i say"

        // Serbian
        private const val CMD_CREATE_PREFIX_SR = "beleška za košnicu" // "note for hive"
        private const val CMD_READ_LOG_PREFIX_SR = "zadnja beleška za košnicu" // "last note for hive"
        private const val CMD_READ_TASK_PREFIX_SR = "zadnji zadatak za košnicu" // "last task for hive"
    }
}