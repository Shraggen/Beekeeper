package com.bachelorthesis.beekeeperMobile.intentRecognizer

import android.content.Context

/**
 * Defines the contract for any class that can recognize intents from text.
 */
interface IntentRecognizer {
    /**
     * Initializes the recognizer. This is for recognizers that do not
     * require an external model file (e.g., Regex).
     * @param context The application context.
     * @param onInitialized A callback that reports whether initialization was successful.
     */
    fun initialize(context: Context, onInitialized: (success: Boolean) -> Unit)

    /**
     * ADDED: Initializes a recognizer that requires a path to a model file (e.g., LLM).
     * Classes that don't need this can ignore it, thanks to the default implementation.
     * @param context The application context.
     * @param modelPath The absolute path to the model file.
     * @param onInitialized A callback that reports whether initialization was successful.
     */
    fun initialize(context: Context, modelPath: String, onInitialized: (success: Boolean) -> Unit) {
        // Default implementation is empty.
        // Subclasses that need a model path MUST override this method.
    }

    /**
     * Processes a string of text to find a structured intent.
     * @param text The raw text from the user.
     * @param onResult A callback that returns the found StructuredIntent.
     */
    fun recognizeIntent(text: String, onResult: (intent: StructuredIntent) -> Unit)

    /**
     * Releases any resources held by the recognizer.
     */
    fun close()
}