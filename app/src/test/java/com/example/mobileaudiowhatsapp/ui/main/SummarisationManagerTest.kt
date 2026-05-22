package com.example.mobileaudiowhatsapp.ui.main

import com.example.mobileaudiowhatsapp.ml.SummarisationManager
import org.junit.Assert.assertTrue
import org.junit.Test

class SummarisationManagerTest {

    @Test
    fun testSummarisePositiveSentiment() {
        val transcript = "Thank you so much. That was a great help. I am very happy and agree with your proposal."
        val summary = SummarisationManager.summarise(transcript, "9876543210", 125)
        
        assertTrue(summary.contains("Sentiment: Positive"))
        assertTrue(summary.contains("9876543210"))
        assertTrue(summary.contains("2m 5s")) // 125 seconds = 2m 5s
    }

    @Test
    fun testSummariseNegativeSentiment() {
        val transcript = "There is a big problem and this issue is wrong. I am very upset and refuse to pay."
        val summary = SummarisationManager.summarise(transcript, "9876543210", 45)
        
        assertTrue(summary.contains("Sentiment: Negative / Tense"))
        assertTrue(summary.contains("45s"))
    }

    @Test
    fun testSummariseActionItemsAndKeyPoints() {
        val transcript = "I will send the payment tomorrow. Please remind me next week."
        val summary = SummarisationManager.summarise(transcript, "9876543210", 60)
        
        assertTrue(summary.contains("Action Items:"))
        assertTrue(summary.contains("Key Points:"))
        assertTrue(summary.contains("will send the payment tomorrow"))
    }
}
