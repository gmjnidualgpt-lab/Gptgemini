package com.example.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.ChatGptService
import com.example.data.ai.GeminiService
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class ChatViewMode {
    SPLIT_COLUMNS,
    STACKED,
    TABS
}

data class ChatUiState(
    val sessions: List<ChatSession> = emptyList(),
    val currentSessionId: String? = null,
    val currentSession: ChatSession? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val geminiGenerating: Boolean = false,
    val chatGptGenerating: Boolean = false,
    val synthesizingId: String? = null,
    val viewMode: ChatViewMode = ChatViewMode.STACKED,
    val selectedGeminiModel: String = "gemini-3.1-pro-preview",
    val selectedChatGptModel: String = "gpt-4o",
    val enableHighThinking: Boolean = true,
    val pluginBridgeMode: Boolean = false,
    val openAiApiKey: String = "",
    val editingMessage: ChatMessage? = null,
    val editingSource: String = "gemini",
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class ChatViewModel(
    private val repository: ChatRepository,
    private val currentUserId: String,
    private val geminiService: GeminiService = GeminiService(),
    private val chatGptService: ChatGptService = ChatGptService()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var messagesJob: Job? = null

    init {
        observeSessions()
    }

    private fun observeSessions() {
        viewModelScope.launch {
            repository.observeSessions(currentUserId).collectLatest { sessionsList ->
                _uiState.update { state ->
                    val activeId = state.currentSessionId ?: sessionsList.firstOrNull()?.id
                    val activeSession = sessionsList.find { it.id == activeId }
                    state.copy(
                        sessions = sessionsList,
                        currentSessionId = activeId,
                        currentSession = activeSession
                    )
                }

                val targetSessionId = _uiState.value.currentSessionId
                if (targetSessionId != null) {
                    observeMessagesForSession(targetSessionId)
                }
            }
        }
    }

    private fun observeMessagesForSession(sessionId: String) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            repository.observeMessages(currentUserId, sessionId).collectLatest { msgs ->
                _uiState.update { it.copy(messages = msgs) }
            }
        }
    }

    fun selectSession(sessionId: String) {
        if (_uiState.value.currentSessionId == sessionId) return
        val session = _uiState.value.sessions.find { it.id == sessionId }
        _uiState.update {
            it.copy(
                currentSessionId = sessionId,
                currentSession = session,
                messages = emptyList()
            )
        }
        observeMessagesForSession(sessionId)
    }

    fun createNewSession(initialTitle: String = "Nowa konwersacja Dual AI") {
        viewModelScope.launch {
            val result = repository.createSession(
                userId = currentUserId,
                title = initialTitle,
                geminiModel = _uiState.value.selectedGeminiModel,
                chatGptModel = _uiState.value.selectedChatGptModel,
                thinkingLevel = if (_uiState.value.enableHighThinking) "HIGH" else "DEFAULT"
            )
            result.onSuccess { newId ->
                selectSession(newId)
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = "Nie udało się utworzyć czatu: ${err.message}") }
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(currentUserId, sessionId)
            if (_uiState.value.currentSessionId == sessionId) {
                val remaining = _uiState.value.sessions.filter { it.id != sessionId }
                val next = remaining.firstOrNull()
                _uiState.update {
                    it.copy(
                        currentSessionId = next?.id,
                        currentSession = next,
                        messages = emptyList()
                    )
                }
                next?.let { observeMessagesForSession(it.id) }
            }
        }
    }

    fun renameSession(sessionId: String, newTitle: String) {
        viewModelScope.launch {
            repository.updateSessionTitle(currentUserId, sessionId, newTitle)
        }
    }

    fun sendMessage(userPrompt: String) {
        val trimmed = userPrompt.trim()
        if (trimmed.isBlank() || _uiState.value.isGenerating) return

        viewModelScope.launch {
            var sessionId = _uiState.value.currentSessionId
            if (sessionId == null) {
                val titlePreview = if (trimmed.length > 30) trimmed.take(27) + "..." else trimmed
                val newSessionResult = repository.createSession(
                    userId = currentUserId,
                    title = titlePreview,
                    geminiModel = _uiState.value.selectedGeminiModel,
                    chatGptModel = _uiState.value.selectedChatGptModel,
                    thinkingLevel = if (_uiState.value.enableHighThinking) "HIGH" else "DEFAULT"
                )
                sessionId = newSessionResult.getOrNull()
                if (sessionId == null) {
                    _uiState.update { it.copy(errorMessage = "Błąd inicjalizacji sesji.") }
                    return@launch
                }
                selectSession(sessionId)
            }

            val messageId = UUID.randomUUID().toString()
            val initialMessage = ChatMessage(
                id = messageId,
                userId = currentUserId,
                sessionId = sessionId,
                prompt = trimmed,
                geminiResponse = "Generowanie odpowiedzi z Gemini...",
                chatGptResponse = "Generowanie odpowiedzi z ChatGPT...",
                geminiModel = _uiState.value.selectedGeminiModel,
                chatGptModel = _uiState.value.selectedChatGptModel,
                geminiThinking = _uiState.value.enableHighThinking,
                status = "generating"
            )

            repository.addMessage(currentUserId, sessionId, initialMessage)

            _uiState.update {
                it.copy(
                    isGenerating = true,
                    geminiGenerating = true,
                    chatGptGenerating = true
                )
            }

            val geminiDeferred = async {
                geminiService.generate(
                    prompt = trimmed,
                    model = _uiState.value.selectedGeminiModel,
                    enableHighThinking = _uiState.value.enableHighThinking
                )
            }

            val chatGptDeferred = async {
                chatGptService.generate(
                    prompt = trimmed,
                    customApiKey = _uiState.value.openAiApiKey.ifBlank { null },
                    model = _uiState.value.selectedChatGptModel
                )
            }

            val geminiRes = geminiDeferred.await()
            val chatGptRes = chatGptDeferred.await()

            val finalGeminiText = geminiRes.getOrElse { "Błąd Gemini: ${it.message}" }
            val finalChatGptText = chatGptRes.getOrElse { "Błąd ChatGPT: ${it.message}" }

            val completedMessage = initialMessage.copy(
                geminiResponse = finalGeminiText,
                chatGptResponse = finalChatGptText,
                status = "completed"
            )

            repository.updateMessage(currentUserId, sessionId, completedMessage)

            _uiState.update {
                it.copy(
                    isGenerating = false,
                    geminiGenerating = false,
                    chatGptGenerating = false
                )
            }

            // If plugin bridge mode is active, trigger an automated cross-critique!
            if (_uiState.value.pluginBridgeMode) {
                triggerPluginBridgeDebate(sessionId, completedMessage)
            }
        }
    }

    private fun triggerPluginBridgeDebate(sessionId: String, baseMessage: ChatMessage) {
        viewModelScope.launch {
            _uiState.update { it.copy(infoMessage = "Tryb Wtyczki: ChatGPT i Gemini analizują nawzajem swoje odpowiedzi...") }

            val synthResult = geminiService.synthesizeComparison(
                prompt = baseMessage.prompt,
                geminiAnswer = baseMessage.geminiResponse,
                chatGptAnswer = baseMessage.chatGptResponse
            )

            synthResult.onSuccess { synthesisText ->
                val updatedWithNotes = baseMessage.copy(
                    comparisonNotes = synthesisText
                )
                repository.updateMessage(currentUserId, sessionId, updatedWithNotes)
                _uiState.update { it.copy(infoMessage = "Zsynchronizowano analizę porównawczą!") }
            }
        }
    }

    fun startEditingResponse(message: ChatMessage, sourceModel: String) {
        _uiState.update {
            it.copy(
                editingMessage = message,
                editingSource = sourceModel
            )
        }
    }

    fun dismissEditing() {
        _uiState.update { it.copy(editingMessage = null) }
    }

    fun resubmitEditedResponse(
        message: ChatMessage,
        sourceModel: String,
        editedText: String,
        targetModel: String,
        userInstruction: String?
    ) {
        val sessionId = _uiState.value.currentSessionId ?: return
        dismissEditing()

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGenerating = true,
                    infoMessage = "Wysyłanie zmodyfikowanej odpowiedzi do $targetModel..."
                )
            }

            // 1. Mark original message as edited
            val updatedOriginal = message.copy(
                isEdited = true,
                editedSource = sourceModel,
                editedText = editedText
            )
            repository.updateMessage(currentUserId, sessionId, updatedOriginal)

            // 2. Generate critique or continuation from the other model
            val newTurnPrompt = "Zrewidowana odpowiedź [$sourceModel]:\n$editedText\n\nInstrukcja użytkownika: ${userInstruction ?: "Oceń i zoptymalizuj"}"
            val newTurnId = UUID.randomUUID().toString()

            val initialTurnMsg = ChatMessage(
                id = newTurnId,
                userId = currentUserId,
                sessionId = sessionId,
                prompt = newTurnPrompt,
                geminiResponse = if (targetModel == "gemini") "Gemini weryfikuje edytowaną odpowiedź..." else "-",
                chatGptResponse = if (targetModel == "chatgpt") "ChatGPT weryfikuje edytowaną odpowiedź..." else "-",
                geminiModel = _uiState.value.selectedGeminiModel,
                chatGptModel = _uiState.value.selectedChatGptModel,
                geminiThinking = _uiState.value.enableHighThinking,
                status = "generating",
                isEdited = true,
                editedSource = sourceModel,
                editedText = editedText
            )

            repository.addMessage(currentUserId, sessionId, initialTurnMsg)

            if (targetModel == "gemini") {
                val geminiCritique = geminiService.crossCritique(
                    userPrompt = message.prompt,
                    editedText = editedText,
                    sourceModel = sourceModel,
                    customInstruction = userInstruction
                )
                val responseText = geminiCritique.getOrElse { "Błąd weryfikacji Gemini: ${it.message}" }
                val finishedTurn = initialTurnMsg.copy(
                    geminiResponse = responseText,
                    chatGptResponse = "[Zmodyfikowana treść bazowa od $sourceModel]",
                    status = "completed"
                )
                repository.updateMessage(currentUserId, sessionId, finishedTurn)
            } else {
                val chatGptCritique = chatGptService.crossCritique(
                    userPrompt = message.prompt,
                    editedText = editedText,
                    sourceModel = sourceModel,
                    customApiKey = _uiState.value.openAiApiKey.ifBlank { null },
                    customInstruction = userInstruction
                )
                val responseText = chatGptCritique.getOrElse { "Błąd weryfikacji ChatGPT: ${it.message}" }
                val finishedTurn = initialTurnMsg.copy(
                    chatGptResponse = responseText,
                    geminiResponse = "[Zmodyfikowana treść bazowa od $sourceModel]",
                    status = "completed"
                )
                repository.updateMessage(currentUserId, sessionId, finishedTurn)
            }

            _uiState.update {
                it.copy(
                    isGenerating = false,
                    infoMessage = "Otrzymano odpowiedź zoptymalizowaną przez $targetModel!"
                )
            }
        }
    }

    fun synthesizeComparison(message: ChatMessage) {
        val sessionId = _uiState.value.currentSessionId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(synthesizingId = message.id) }

            val result = geminiService.synthesizeComparison(
                prompt = message.prompt,
                geminiAnswer = message.geminiResponse,
                chatGptAnswer = message.chatGptResponse
            )

            result.onSuccess { synthesisText ->
                val updated = message.copy(comparisonNotes = synthesisText)
                repository.updateMessage(currentUserId, sessionId, updated)
                _uiState.update {
                    it.copy(
                        synthesizingId = null,
                        infoMessage = "Porównanie wygenerowane pomyślnie!"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        synthesizingId = null,
                        errorMessage = "Błąd generowania porównania: ${err.message}"
                    )
                }
            }
        }
    }

    fun setViewMode(mode: ChatViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun setGeminiModel(model: String) {
        _uiState.update { it.copy(selectedGeminiModel = model) }
    }

    fun setChatGptModel(model: String) {
        _uiState.update { it.copy(selectedChatGptModel = model) }
    }

    fun toggleHighThinking(enabled: Boolean) {
        _uiState.update { it.copy(enableHighThinking = enabled) }
    }

    fun togglePluginBridgeMode(enabled: Boolean) {
        _uiState.update { it.copy(pluginBridgeMode = enabled) }
    }

    fun setOpenAiApiKey(apiKey: String) {
        _uiState.update { it.copy(openAiApiKey = apiKey.trim()) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearInfo() {
        _uiState.update { it.copy(infoMessage = null) }
    }
}
