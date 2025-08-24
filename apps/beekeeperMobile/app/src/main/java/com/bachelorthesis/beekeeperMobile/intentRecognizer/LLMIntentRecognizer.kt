package com.bachelorthesis.beekeeperMobile.intentRecognizer

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

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
                    .setMaxTopK(64)
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

    override fun recognizeIntent(text: String, onResult: (intent: StructuredIntent) -> Unit) {
        if (llmInference == null) {
            onResult(StructuredIntent.UNKNOWN)
            return
        }
        scope.launch {
            try {
                val prompt = buildPromptFor(text)
                val response = llmInference?.generateResponse(prompt)
                val intent = parseLlmResponse(response)
                onResult(intent)
            } catch (e: Exception) {
                Log.e("LLMIntentRecognizer", "Error during intent recognition", e)
                onResult(StructuredIntent.UNKNOWN)
            }
        }
    }

    /**
     * MODIFIED: A much more robust prompt with system instructions and an escape hatch.
     */
    private fun buildPromptFor(text: String): String {
        return """
        You are a highly intelligent JSON formatting API. Your ONLY job is to analyze the user's text and convert it into a specific JSON format.

        **Instructions:**
        - The possible intents are: 'create_log', 'read_last_log', 'read_last_task'.
        - You must extract 'hive_id' (a number) and 'content' (the note).
        - Your entire response must be ONLY the JSON object, with no other text or formatting.

        **Examples:**
        1. User Text: "create a note for beehive 10 that says the queen is healthy"
           Your JSON Response: {"intent":"create_log","entities":{"hive_id":"10","content":"the queen is healthy"}}
        2. User Text: "read last note for hive 5"
           Your JSON Response: {"intent":"read_last_log","entities":{"hive_id":"5"}}
        3. User Text: "what's the weather like"
           Your JSON Response: {"intent":"unknown","entities":{}}

        **User Text to Process:**
        "$text"

        **Your JSON Response:**
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
            val entitiesJson = jsonObject.optJSONObject("entities")
            val entities = mutableMapOf<String, String>()

            entitiesJson?.keys()?.forEach { key ->
                entities[key] = entitiesJson.getString(key)
            }
            if (jsonObject.has("content")) {
                entities["content"] = jsonObject.getString("content")
            }

            StructuredIntent(intent, entities)
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