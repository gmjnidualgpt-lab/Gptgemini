package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class ChatSession(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val geminiModel: String = "gemini-3.1-pro-preview",
    val chatGptModel: String = "gpt-4o",
    val thinkingLevel: String = "HIGH",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
