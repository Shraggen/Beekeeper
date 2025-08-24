package com.bachelorthesis.beekeeperMobile.speechEngine

interface SpeechEngineListener {
    /**
     * Called when the hotword is successfully detected.
     */
    fun onHotwordDetected()

    /**
     * Called when the full command has been transcribed by the speech recognizer.
     * @param text The transcribed text from the user's command.
     */
    fun onCommandTranscribed(text: String)

    /**
     * Called when an error occurs in the speech recognition process.
     * @param error A description of the error.
     */
    fun onError(error: String)
}