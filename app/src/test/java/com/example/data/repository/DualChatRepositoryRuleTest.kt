package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.ChatMessage
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class DualChatRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createSession_authenticatedUser_success() = runBlocking {
        val uid = signInTestUser("alice@test.com")
        val repo = ChatRepository(firestore)

        val result = withTimeout(5000L) {
            repo.createSession(uid, "Test Session", "gemini-3.1-pro-preview", "gpt-4o", "HIGH")
        }

        assertTrue(result.isSuccess)
        val sessionId = result.getOrThrow()
        assertTrue(sessionId.isNotEmpty())
    }

    @Test
    fun addAndObserveMessages_authenticatedUser_success() = runBlocking {
        val uid = signInTestUser("alice@test.com")
        val repo = ChatRepository(firestore)
        val sessionId = withTimeout(5000L) {
            repo.createSession(uid, "Conversation", "gemini-3.1-pro-preview", "gpt-4o", "HIGH").getOrThrow()
        }

        val msgId = UUID.randomUUID().toString()
        val chatMsg = ChatMessage(
            id = msgId,
            userId = uid,
            sessionId = sessionId,
            prompt = "What is gravity?",
            geminiResponse = "Gemini answer on gravity",
            chatGptResponse = "ChatGPT answer on gravity",
            geminiModel = "gemini-3.1-pro-preview",
            chatGptModel = "gpt-4o",
            geminiThinking = true
        )

        val addResult = withTimeout(5000L) {
            repo.addMessage(uid, sessionId, chatMsg)
        }
        assertTrue(addResult.isSuccess)

        val observed = withTimeout(5000L) {
            repo.observeMessages(uid, sessionId).first { list -> list.any { it.id == msgId } }
        }
        assertTrue(observed.any { it.id == msgId })
    }

    @Test
    fun updateMessage_userEditsResponse_success() = runBlocking {
        val uid = signInTestUser("alice@test.com")
        val repo = ChatRepository(firestore)
        val sessionId = withTimeout(5000L) {
            repo.createSession(uid, "Editing Session", "gemini-3.1-pro-preview", "gpt-4o", "HIGH").getOrThrow()
        }

        val msgId = UUID.randomUUID().toString()
        val chatMsg = ChatMessage(
            id = msgId,
            userId = uid,
            sessionId = sessionId,
            prompt = "Write a haiku",
            geminiResponse = "Original Gemini haiku",
            chatGptResponse = "Original ChatGPT haiku"
        )
        withTimeout(5000L) { repo.addMessage(uid, sessionId, chatMsg).getOrThrow() }

        val editedMsg = chatMsg.copy(
            isEdited = true,
            editedSource = "gemini",
            editedText = "Polished and refined haiku text"
        )
        val updateResult = withTimeout(5000L) {
            repo.updateMessage(uid, sessionId, editedMsg)
        }
        assertTrue(updateResult.isSuccess)
    }

    @Test
    fun observeMessages_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = ChatRepository(firestore)

        try {
            withTimeout(3000L) {
                repo.observeMessages("unauth_user", "session_123").first()
            }
            fail("Expected FirebaseFirestoreException but succeeded")
        } catch (e: Exception) {
            val fse = generateSequence<Throwable>(e) { it.cause }.firstOrNull { it is FirebaseFirestoreException } as? FirebaseFirestoreException
            assertTrue("Expected FirebaseFirestoreException in cause chain", fse != null)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fse?.code)
        }
    }
}
