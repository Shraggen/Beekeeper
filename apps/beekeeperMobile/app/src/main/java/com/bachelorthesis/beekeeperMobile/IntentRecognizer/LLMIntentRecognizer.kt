// File: LLMIntentRecognizer.kt
package com.bachelorthesis.beekeeperMobile.IntentRecognizer

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

    // Per documentation, the model must be on device.
    // We will hardcode the development path for now.
    // In production, this path will come from a ModelManager.
    private val modelPath = "/data/local/tmp/llm/gemma-3n-E2B-it-int4.task"

    override fun initialize(context: Context, onInitialized: (success: Boolean) -> Unit) {
        scope.launch {
            try {
                // Check if the model file actually exists before trying to load
                if (!File(modelPath).exists()) {
                    Log.e("LLMIntentRecognizer", "Model file not found at: $modelPath")
                    onInitialized(false)
                    return@launch
                }

                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTopK(64) // Example parameter, can be tuned
                    .build()

                llmInference = LlmInference.createFromOptions(context, options)
                Log.i("LLMIntentRecognizer", "LLM Inference initialized successfully.")
                onInitialized(true)
            } catch (e: Exception) {
                Log.e("LLMIntentRecognizer", "Failed to initialize LLM Inference", e)
                onInitialized(false)
            }
        }
    }

    override fun recognizeIntent(text: String, onResult: (intent: StructuredIntent) -> Unit) {
        if (llmInference == null) {
            Log.w("LLMIntentRecognizer", "recognizeIntent called before initialization succeeded.")
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

    private fun buildPromptFor(text: String): String {
        // This prompt engineering is key to getting reliable results.
        return """
        Analyze the following text from a beekeeper and extract the intent and entities.
        The possible intents are: 'create_log', 'read_last_log', 'read_last_task', 'unknown'.
        The entities are 'hive_id' (as a number) and 'content' (the spoken note).
        Return the result as a single line of JSON with zero formatting.

        Text: "$text"
        JSON:
        """.trimIndent()
    }

    private fun parseLlmResponse(response: String?): StructuredIntent {
        if (response.isNullOrBlank()) {
            return StructuredIntent.UNKNOWN
        }

        return try {
            // It's good practice to trim the response in case the LLM adds whitespace
            val jsonObject = JSONObject(response.trim())
            val intent = jsonObject.optString("intent", "unknown")
            val entitiesJson = jsonObject.optJSONObject("entities")
            val entities = mutableMapOf<String, String>()

            entitiesJson?.keys()?.forEach { key ->
                entities[key] = entitiesJson.getString(key)
            }
            StructuredIntent(intent, entities)
        } catch (e: Exception) {
            Log.e("LLMIntentRecognizer", "Failed to parse JSON response from LLM: $response", e)
            StructuredIntent.UNKNOWN
        }
    }

    override fun close() {
        llmInference?.close()
        llmInference = null
    }
}