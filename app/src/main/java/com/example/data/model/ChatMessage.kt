package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class ChatMessage(
    val id: String = "",
    val userId: String = "",
    val sessionId: String = "",
    val prompt: String = "",
    val geminiResponse: String = "",
    val chatGptResponse: String = "",
    val geminiModel: String = "gemini-3.1-pro-preview",
    val chatGptModel: String = "gpt-4o",
    val geminiThinking: Boolean = true,
    val geminiThinkingContent: String = "",
    val comparisonNotes: String = "",
    val status: String = "completed",
    val isEdited: Boolean = false,
    val editedSource: String = "none",
    val editedText: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
