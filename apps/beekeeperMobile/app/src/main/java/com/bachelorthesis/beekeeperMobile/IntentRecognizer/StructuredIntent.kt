package com.bachelorthesis.beekeeperMobile.IntentRecognizer

/**
 * A data class to hold the structured output from the intent recognizer.
 *
 * @param intentName The name of the recognized intent (e.g., "create_log").
 * @param entities A map of extracted entities (e.g., "hive_id" to "12").
 */
data class StructuredIntent(
    val intentName: String,
    val entities: Map<String, String> = emptyMap()
) {
    companion object {
        // A singleton instance for unknown or failed recognitions.
        val UNKNOWN = StructuredIntent("unknown")
    }
}