package com.bachelorthesis.beekeeperMobile.speechEngine

object LibWhisper {
    init {
        // This should match the library name in CMakeLists.txt
        System.loadLibrary("beekeeper_whisper")
    }

    /**
     * Initializes a whisper_context from a model file.
     * @param modelPath The path to the ggml model file.
     * @return A pointer to the whisper_context, or 0 if failed.
     */
    external fun initContext(modelPath: String): Long

    /**
     * Releases the whisper_context.
     * @param contextPtr The pointer to the whisper_context.
     */
    external fun releaseContext(contextPtr: Long)

    /**
     * Transcribes an audio buffer using a specified number of threads.
     * @param contextPtr Pointer to the whisper_context.
     * @param nThreads The number of threads to use for computation.
     * @param audioData The audio data in 32-bit PCM format.
     * @param language The language to use for transcription.
     * @return The transcribed text.
     */
    external fun transcribe(contextPtr: Long, nThreads: Int, audioData: FloatArray, language: String = "auto"): String

    /**
     * Returns a map of supported languages and their full names.
     */
    external fun getLanguages() : Map<String, String>
}