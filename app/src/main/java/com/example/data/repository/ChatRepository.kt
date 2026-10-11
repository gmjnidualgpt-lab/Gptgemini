package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.model.UserProfile
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ChatRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun observeSessions(userId: String): Flow<List<ChatSession>> {
        val path = "users/$userId/sessions"
        return db.collection("users").document(userId)
            .collection("sessions")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(ChatSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun createSession(
        userId: String,
        title: String,
        geminiModel: String = "gemini-3.1-pro-preview",
        chatGptModel: String = "gpt-4o",
        thinkingLevel: String = "HIGH"
    ): Result<String> {
        val sessionId = UUID.randomUUID().toString()
        val docRef = db.collection("users").document(userId)
            .collection("sessions").document(sessionId)

        val payload = mapOf(
            "id" to sessionId,
            "userId" to userId,
            "title" to title.take(200),
            "geminiModel" to geminiModel,
            "chatGptModel" to chatGptModel,
            "thinkingLevel" to thinkingLevel,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        return try {
            docRef.set(payload).await()
            Result.success(sessionId)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateSessionTitle(
        userId: String,
        sessionId: String,
        newTitle: String
    ): Result<Unit> {
        val docRef = db.collection("users").document(userId)
            .collection("sessions").document(sessionId)

        return try {
            val payload = mapOf(
                "title" to newTitle.take(200),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteSession(userId: String, sessionId: String): Result<Unit> {
        val sessionDoc = db.collection("users").document(userId)
            .collection("sessions").document(sessionId)

        return try {
            sessionDoc.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, sessionDoc.path)
            Result.failure(e)
        }
    }

    fun observeMessages(userId: String, sessionId: String): Flow<List<ChatMessage>> {
        val path = "users/$userId/sessions/$sessionId/messages"
        return db.collection("users").document(userId)
            .collection("sessions").document(sessionId)
            .collection("messages")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedBy { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun addMessage(
        userId: String,
        sessionId: String,
        message: ChatMessage
    ): Result<String> {
        val messageId = if (message.id.isNotBlank()) message.id else UUID.randomUUID().toString()
        val docRef = db.collection("users").document(userId)
            .collection("sessions").document(sessionId)
            .collection("messages").document(messageId)

        val payload = mapOf(
            "id" to messageId,
            "userId" to userId,
            "sessionId" to sessionId,
            "prompt" to message.prompt.take(10000),
            "geminiResponse" to message.geminiResponse.take(50000),
            "chatGptResponse" to message.chatGptResponse.take(50000),
            "geminiModel" to message.geminiModel,
            "chatGptModel" to message.chatGptModel,
            "geminiThinking" to message.geminiThinking,
            "geminiThinkingContent" to message.geminiThinkingContent.take(30000),
            "comparisonNotes" to message.comparisonNotes.take(20000),
            "status" to message.status,
            "isEdited" to message.isEdited,
            "editedSource" to message.editedSource,
            "editedText" to message.editedText.take(50000),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        return try {
            docRef.set(payload).await()
            Result.success(messageId)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateMessage(
        userId: String,
        sessionId: String,
        message: ChatMessage
    ): Result<Unit> {
        val docRef = db.collection("users").document(userId)
            .collection("sessions").document(sessionId)
            .collection("messages").document(message.id)

        val payload = mutableMapOf<String, Any>(
            "prompt" to message.prompt.take(10000),
            "geminiResponse" to message.geminiResponse.take(50000),
            "chatGptResponse" to message.chatGptResponse.take(50000),
            "geminiModel" to message.geminiModel,
            "chatGptModel" to message.chatGptModel,
            "geminiThinking" to message.geminiThinking,
            "geminiThinkingContent" to message.geminiThinkingContent.take(30000),
            "comparisonNotes" to message.comparisonNotes.take(20000),
            "status" to message.status,
            "isEdited" to message.isEdited,
            "editedSource" to message.editedSource,
            "editedText" to message.editedText.take(50000),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        return try {
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun syncUserProfile(profile: UserProfile): Result<Unit> {
        val docRef = db.collection("users").document(profile.userId)
        return try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mutableMapOf<String, Any>(
                    "userId" to profile.userId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                profile.email?.let { payload["email"] = it.take(200) }
                profile.displayName?.let { payload["displayName"] = it.take(100) }
                profile.photoUrl?.let { payload["photoUrl"] = it.take(500) }
                docRef.set(payload).await()
            } else {
                val payload = mutableMapOf<String, Any>(
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                profile.email?.let { payload["email"] = it.take(200) }
                profile.displayName?.let { payload["displayName"] = it.take(100) }
                profile.photoUrl?.let { payload["photoUrl"] = it.take(500) }
                docRef.update(payload).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }
}
