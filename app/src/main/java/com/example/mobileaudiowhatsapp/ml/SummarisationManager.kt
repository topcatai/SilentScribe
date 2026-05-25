package com.example.mobileaudiowhatsapp.ml

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File

object SummarisationManager {
    private const val TAG = "SummarisationManager"

    @Volatile
    private var llmInference: LlmInference? = null
    private var loadedModelPath: String? = null

    @Synchronized
    private fun getLlmInference(context: Context): LlmInference? {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val modelPath = prefs.getString("llm_model_path", "")
        if (modelPath.isNullOrBlank()) {
            Log.d(TAG, "No LLM model path configured.")
            release()
            return null
        }

        if (loadedModelPath == modelPath && llmInference != null) {
            return llmInference
        }

        // Release old instance if model path changed
        release()

        val modelFile = File(modelPath)
        if (!modelFile.exists() || !modelFile.isFile) {
            Log.w(TAG, "LLM model file does not exist: $modelPath")
            return null
        }

        try {
            Log.d(TAG, "Initializing MediaPipe LlmInference with model: $modelPath")
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(512)
                .setPreferredBackend(LlmInference.Backend.GPU) // Try GPU first
                .build()
            llmInference = LlmInference.createFromOptions(context, options)
            loadedModelPath = modelPath
            Log.d(TAG, "LlmInference initialized successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize GPU-based LlmInference, falling back to CPU", e)
            try {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(512)
                    .setPreferredBackend(LlmInference.Backend.CPU) // CPU fallback
                    .build()
                llmInference = LlmInference.createFromOptions(context, options)
                loadedModelPath = modelPath
                Log.d(TAG, "LlmInference (CPU) initialized successfully.")
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to initialize CPU-based LlmInference", e2)
                release()
            }
        }

        return llmInference
    }

    @Synchronized
    fun release() {
        try {
            llmInference?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing LlmInference", e)
        }
        llmInference = null
        loadedModelPath = null
    }

    fun summarise(transcript: String, phoneNumber: String, durationSeconds: Int, context: Context? = null): String {
        val wordCount = if (transcript.isBlank()) 0 else transcript.trim().split(Regex("\\s+")).size
        val duration = formatDuration(durationSeconds)

        val llm = if (context != null) getLlmInference(context) else null
        if (llm != null && transcript.isNotBlank()) {
            try {
                val prompt = """
                    You are a professional call assistant analyzing a call transcript.
                    Transcript:
                    "$transcript"
                    
                    Provide a concise summary, main key points, and clear action items from this call.
                    Structure your response EXACTLY like this:
                    Summary: <a brief 1-2 sentence overview of the call>
                    
                    Key Points:
                    - <point 1>
                    - <point 2>
                    
                    Action Items:
                    - <action item 1>
                    - <action item 2>
                """.trimIndent()

                Log.d(TAG, "Running LLM inference...")
                val response = llm.generateResponse(prompt).trim()
                Log.d(TAG, "LLM response received.")

                if (response.isNotBlank()) {
                    return buildString {
                        appendLine("📞 Call with $phoneNumber — $duration")
                        appendLine("📊 AI Generated Summary  |  ~$wordCount words transcribed")
                        appendLine()
                        append(response)
                    }.trim()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Inference failed, falling back to rule-based summarizer", e)
            }
        }

        // Rule-based fallback if LLM is not configured, fails to initialize, or crashes
        return runRuleBasedSummary(transcript, phoneNumber, durationSeconds, wordCount, duration)
    }

    private fun runRuleBasedSummary(
        transcript: String,
        phoneNumber: String,
        durationSeconds: Int,
        wordCount: Int,
        duration: String
    ): String {
        val keyPoints = extractKeyPoints(transcript)
        val sentiment = detectSentiment(transcript)
        val actionItems = extractActionItems(transcript)

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
