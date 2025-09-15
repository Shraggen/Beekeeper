package com.bachelorthesis.beekeeperMobile.intentRecognizer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * Unit tests for the RegexIntentRecognizer.
 *
 * These tests verify that the recognizer correctly parses predefined command formats
 * for different languages and handles unknown inputs gracefully. They run on the local JVM
 * and require no emulator.
 */
class RegexIntentRecognizerTest {

    private val recognizer = RegexIntentRecognizer()

    @Test
    fun `recognizeIntent with English create log command returns correct intent`() {
        // Arrange
        // [FIX] The Regex recognizer is only responsible for identifying the hive ID
        // in a multi-turn conversation. It does not parse content. This test now
        // reflects that correct, simpler behavior.
        val inputText = "note for beehive 12"
        val locale = Locale.forLanguageTag("en-US")
        val expectedIntent = StructuredIntent("create_log", mapOf("hive_id" to "12"))
        var actualIntent: StructuredIntent? = null

        // Act
        recognizer.recognizeIntent(inputText, locale) { result ->
            actualIntent = result
        }

        // Assert
        assertEquals(expectedIntent, actualIntent)
    }

    @Test
    fun `recognizeIntent with Serbian read last log command returns correct intent`() {
        // Arrange
        val inputText = "zadnja beleška za košnicu 3"
        val locale = Locale.forLanguageTag("sr-RS")
        val expectedIntent = StructuredIntent("read_last_log", mapOf("hive_id" to "3"))
        var actualIntent: StructuredIntent? = null

        // Act
        recognizer.recognizeIntent(inputText, locale) { result ->
            actualIntent = result
        }

        // Assert
        assertEquals(expectedIntent, actualIntent)
    }

    @Test
    fun `recognizeIntent with unknown command returns UNKNOWN intent`() {
        // Arrange
        val inputText = "what is the weather like today?"
        val locale = Locale.forLanguageTag("en-US")
        val expectedIntent = StructuredIntent.UNKNOWN
        var actualIntent: StructuredIntent? = null

        // Act
        recognizer.recognizeIntent(inputText, locale) { result ->
            actualIntent = result
        }

        // Assert
        assertEquals(expectedIntent, actualIntent)
    }

    @Test
    fun `recognizeIntent with English read last task command returns correct intent`() {
        // Arrange
        val inputText = "last task for beehive 5"
        val locale = Locale.forLanguageTag("en-US")
        val expectedIntent = StructuredIntent("read_last_task", mapOf("hive_id" to "5"))
        var actualIntent: StructuredIntent? = null

        // Act
        recognizer.recognizeIntent(inputText, locale) { result ->
            actualIntent = result
        }

        // Assert
        assertEquals(expectedIntent, actualIntent)
    }
}