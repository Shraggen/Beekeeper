package com.bachelorthesis.beekeeperMobile.speechEngine

/**
 * Defines the contract for a speech engine in the Beekeeper app.
 *
 * This interface abstracts the underlying implementation (e.g., Vosk, Whisper, Android Speech)
 * from the service that uses it, enabling dependency injection and testability.
 */
interface SpeechEngineInterface {

    /**
     * Initializes the speech engine and its underlying components.
     * The listener for events must be set before or during initialization.
     *
     * @param listener The listener that will receive events like hotword detection.
     * @param onInitialized A callback indicating the success or failure of the initialization.
     */
    fun initialize(listener: SpeechEngineListener, onInitialized: (success: Boolean) -> Unit)

    /**
     * Starts the engine in a low-power mode, listening only for the hotword.
     */
    fun startListeningForHotword()

    /**
     * Starts the engine in a high-power mode, listening for a full command.
     */
    fun startListeningForCommand()

    /**
     * Releases all resources held by the speech engine.
     */
    fun destroy()
}