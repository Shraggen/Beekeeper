package com.bachelorthesis.beekeeperMobile.IntentRecognizer

import android.content.Context

/**
 * Defines the contract for any class that can recognize intents from text.
 */
interface IntentRecognizer {
    /**
     * Initializes the recognizer.
     * @param context The application context.
     * @param onInitialized A callback that reports whether initialization was successful.
     */
    fun initialize(context: Context, onInitialized: (success: Boolean) -> Unit)

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