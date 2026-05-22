package com.example.mobileaudiowhatsapp.ml

object SummarisationManager {

    fun summarise(transcript: String, phoneNumber: String, durationSeconds: Int): String {
        val wordCount = if (transcript.isBlank()) 0 else transcript.trim().split(Regex("\\s+")).size
        val keyPoints = extractKeyPoints(transcript)
        val sentiment = detectSentiment(transcript)
        val actionItems = extractActionItems(transcript)
        val duration = formatDuration(durationSeconds)

        return buildString {
            appendLine("📞 Call with $phoneNumber — $duration")
            appendLine("📊 Sentiment: $sentiment  |  ~$wordCount words transcribed")
            appendLine()
            if (keyPoints.isNotEmpty()) {
                appendLine("Key Points:")
                keyPoints.forEach { appendLine("  • $it") }
            } else {
                appendLine("Key Points: None detected.")
            }
            if (actionItems.isNotEmpty()) {
                appendLine()
                appendLine("Action Items:")
                actionItems.forEach { appendLine("  → $it") }
            }
        }.trim()
    }

    private fun extractKeyPoints(text: String): List<String> {
        val keywords = listOf(
            "will", "would", "agreed", "confirmed", "meet", "send", "call back",
            "payment", "amount", "rupees", "tomorrow", "next week", "address", "problem", "issue"
        )
        return text.split(Regex("[.!?]\\s*"))
            .map { it.trim() }
            .filter { sentence ->
                sentence.isNotBlank() && keywords.any { kw -> sentence.lowercase().contains(kw) }
            }
            .take(5)
    }

    private fun extractActionItems(text: String): List<String> {
        val actionPhrases = listOf(
            "will send", "will call", "will come", "need to", "have to",
            "please", "don't forget", "remind", "by tomorrow", "by next week"
        )
        return text.split(Regex("[.!?]\\s*"))
            .map { it.trim() }
            .filter { sentence ->
                sentence.isNotBlank() && actionPhrases.any { p -> sentence.lowercase().contains(p) }
            }
            .take(3)
    }

    private fun detectSentiment(text: String): String {
        val positive = listOf("thank", "great", "good", "happy", "okay", "fine", "agree", "sure")
        val negative = listOf("problem", "issue", "wrong", "angry", "upset", "not", "never", "refuse")
        val t = text.lowercase()
        val pos = positive.count { t.contains(it) }
        val neg = negative.count { t.contains(it) }
        return when {
            pos > neg + 2 -> "Positive"
            neg > pos + 2 -> "Negative / Tense"
            else          -> "Neutral"
        }
    }

    private fun formatDuration(s: Int): String {
        val m = s / 60
        val sec = s % 60
        return if (m > 0) "${m}m ${sec}s" else "${sec}s"
    }
}
