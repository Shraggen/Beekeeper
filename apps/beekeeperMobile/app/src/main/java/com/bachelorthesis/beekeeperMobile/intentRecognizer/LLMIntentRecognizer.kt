package com.bachelorthesis.beekeeperMobile.intentRecognizer

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.Locale

class LLMIntentRecognizer : IntentRecognizer {

    private var llmInference: LlmInference? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun initialize(context: Context, modelPath: String, onInitialized: (success: Boolean) -> Unit) {
        scope.launch {
            try {
                val modelFile = File(modelPath)
                if (!modelFile.exists() || modelFile.length() == 0L) {
                    Log.e("LLMIntentRecognizer", "Model file not found or is empty at: $modelPath")
                    onInitialized(false)
                    return@launch
                }
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(1024)
                    .setMaxTopK(40)
                    .build()
                llmInference = LlmInference.createFromOptions(context, options)
                Log.i("LLMIntentRecognizer", "LLM Inference initialized successfully from path: $modelPath")
                onInitialized(true)
            } catch (e: Exception) {
                Log.e("LLMIntentRecognizer", "Failed to initialize LLM Inference", e)
                onInitialized(false)
            }
        }
    }

    // MODIFIED: Implemented new signature
    override fun recognizeIntent(text: String, locale: Locale, onResult: (intent: StructuredIntent) -> Unit) {
        if (llmInference == null) {
            onResult(StructuredIntent.UNKNOWN)
            return
        }
        scope.launch {
            try {
                // The prompt is now multilingual by default
                val prompt = buildDynamicPromptFor(text, locale)
                val response = llmInference?.generateResponse(prompt)
                val intent = parseLlmResponse(response)
                onResult(intent)
            } catch (e: Exception) {
                Log.e("LLMIntentRecognizer", "Error during intent recognition", e)
                onResult(StructuredIntent.UNKNOWN)
            }
        }
    }

    private fun buildDynamicPromptFor(text: String, locale: Locale): String {
        val baseInstructions = """
        You are a JSON formatting API for a beekeeping app. Your job is to analyze user text and convert it into a specific JSON format. You MUST generate a 'responseText' field. Your entire output must be ONLY the JSON object.

        **Instructions:**
        - Intents: 'create_log', 'read_last_log', 'read_last_task', 'unknown'.
        - Entities: 'hive_id' (number), 'content' (the note).

        """.trimIndent()

        // Select examples based on the current language
        val examples = when (locale.language) {
            "sr" -> getSerbianExamples()
            else -> getEnglishExamples() // Default to English
        }

        return """
        $baseInstructions
        $examples
        **User Text to Process:**
        "$text"

        **Your JSON Response:**
        """.trimIndent()
    }

    // NEW: Helper function for English examples
    private fun getEnglishExamples(): String {
        return """
        **English Examples:**
        1. User Text: "note for beehive 10 queen is healthy"
           Your JSON Response: {"intent":"create_log","entities":{"hive_id":"10","content":"queen is healthy"},"responseText":"Note saved for beehive 10."}
        2. User Text: "read last note for hive 5"
           Your JSON Response: {"intent":"read_last_log","entities":{"hive_id":"5"},"responseText":"Fetching the last note for beehive 5."}
        3. User Text: "note for beehive 7"
           Your JSON Response: {"intent":"create_log","entities":{"hive_id":"7"},"responseText":"Okay, what is the note for beehive 7?"}
        4. User Text: "what's the weather like"
           Your JSON Response: {"intent":"unknown","entities":{},"responseText":"Sorry, I can't help with that."}
        """.trimIndent()
    }

    // NEW: Helper function for Serbian examples
    private fun getSerbianExamples(): String {
        return """
        **Serbian (Srpski) Examples:**
        1. User Text: "beleška za košnicu 7 matica je viđena"
           Your JSON Response: {"intent":"create_log","entities":{"hive_id":"7","content":"matica je viđena"},"responseText":"Beleška sačuvana za košnicu 7."}
        2. User Text: "pročitaj poslednju belešku za košnicu 3"
           Your JSON Response: {"intent":"read_last_log","entities":{"hive_id":"3"},"responseText":"Preuzimam poslednju belešku za košnicu 3."}
        3. User Text: "beleška za košnicu 12"
           Your JSON Response: {"intent":"create_log","entities":{"hive_id":"12"},"responseText":"U redu, koja je beleška za košnicu 12?"}
        """.trimIndent()
    }

    private fun parseLlmResponse(response: String?): StructuredIntent {
        if (response.isNullOrBlank()) {
            Log.w("LLMIntentRecognizer", "LLM returned a null or empty response.")
            return StructuredIntent.UNKNOWN
        }

        val firstBrace = response.indexOf('{')
        val lastBrace = response.lastIndexOf('}')

        if (firstBrace == -1 || lastBrace == -1 || lastBrace < firstBrace) {
            Log.e("LLMIntentRecognizer", "LLM response did not contain a valid JSON object: $response")
            return StructuredIntent.UNKNOWN
        }

        val jsonString = response.substring(firstBrace, lastBrace + 1)

        return try {
            val jsonObject = JSONObject(jsonString)
            val intent = jsonObject.optString("intent", "unknown")
            val responseText = jsonObject.optString("responseText", null) // Extract the new field
            val entitiesJson = jsonObject.optJSONObject("entities")
            val entities = mutableMapOf<String, String>()

            entitiesJson?.keys()?.forEach { key ->
                entities[key] = entitiesJson.getString(key)
            }
            if (jsonObject.has("content")) {
                entities["content"] = jsonObject.getString("content")
            }

            StructuredIntent(intent, entities, responseText) // Pass responseText to constructor
        } catch (e: Exception) {
            Log.e("LLMIntentRecognizer", "Failed to parse extracted JSON string: $jsonString", e)
            StructuredIntent.UNKNOWN
        }
    }

    override fun close() {
        llmInference?.close()
        llmInference = null
    }

    override fun initialize(context: Context, onInitialized: (success: Boolean) -> Unit) {
        Log.w("LLMIntentRecognizer", "Attempted to initialize without a model path. This is not supported.")
        onInitialized(false)
    }
}