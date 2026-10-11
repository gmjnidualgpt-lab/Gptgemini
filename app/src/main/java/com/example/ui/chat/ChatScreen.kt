package com.example.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ChatMessage
import com.example.ui.theme.ChatGptPrimary
import com.example.ui.theme.GeminiPrimary
import com.example.ui.theme.GeminiSecondary
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onSignOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var inputPrompt by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.infoMessage) {
        uiState.infoMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearInfo()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(300.dp)
            ) {
                DrawerContent(
                    sessions = uiState.sessions,
                    activeSessionId = uiState.currentSessionId,
                    onSelectSession = { id ->
                        viewModel.selectSession(id)
                        coroutineScope.launch { drawerState.close() }
                    },
                    onNewSession = {
                        viewModel.createNewSession()
                        coroutineScope.launch { drawerState.close() }
                    },
                    onDeleteSession = { id ->
                        viewModel.deleteSession(id)
                    },
                    onSignOut = onSignOut
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Dual AI Chat",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                // Active plugin indicator badge
                                if (uiState.pluginBridgeMode) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = ChatGptPrimary.copy(alpha = 0.2f),
                                        modifier = Modifier.padding(2.dp)
                                    ) {
                                        Text(
                                            text = "WTYCZKA",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = ChatGptPrimary,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "${uiState.selectedGeminiModel.take(14)} • ${uiState.selectedChatGptModel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { coroutineScope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("drawer_menu_button")
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Historia czatów")
                        }
                    },
                    actions = {
                        // View mode toggle
                        IconButton(onClick = {
                            val nextMode = when (uiState.viewMode) {
                                ChatViewMode.STACKED -> ChatViewMode.TABS
                                ChatViewMode.TABS -> ChatViewMode.SPLIT_COLUMNS
                                ChatViewMode.SPLIT_COLUMNS -> ChatViewMode.STACKED
                            }
                            viewModel.setViewMode(nextMode)
                        }) {
                            Icon(
                                imageVector = when (uiState.viewMode) {
                                    ChatViewMode.STACKED -> Icons.Default.ViewAgenda
                                    ChatViewMode.TABS -> Icons.Default.ViewStream
                                    ChatViewMode.SPLIT_COLUMNS -> Icons.Default.ViewColumn
                                },
                                contentDescription = "Zmień widok"
                            )
                        }

                        // Settings Dialog Icon
                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier.testTag("open_settings_button")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Ustawienia modeli")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                BottomChatInput(
                    inputPrompt = inputPrompt,
                    onPromptChange = { inputPrompt = it },
                    isGenerating = uiState.isGenerating,
                    pluginMode = uiState.pluginBridgeMode,
                    onTogglePluginMode = { viewModel.togglePluginBridgeMode(!uiState.pluginBridgeMode) },
                    onSend = {
                        if (inputPrompt.isNotBlank()) {
                            viewModel.sendMessage(inputPrompt)
                            inputPrompt = ""
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (uiState.messages.isEmpty() && !uiState.isGenerating) {
                    EmptyChatWelcome(
                        onSelectPrompt = { prompt ->
                            inputPrompt = prompt
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(uiState.messages, key = { it.id }) { message ->
                            DualMessageCard(
                                message = message,
                                viewMode = uiState.viewMode,
                                isSynthesizing = uiState.synthesizingId == message.id,
                                onEditResponse = { source ->
                                    viewModel.startEditingResponse(message, source)
                                },
                                onSynthesizeComparison = {
                                    viewModel.synthesizeComparison(message)
                                },
                                onCopyText = { text ->
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Dual AI Response", text))
                                    Toast.makeText(context, "Skopiowano do schowka", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        if (uiState.isGenerating && uiState.messages.none { it.status == "generating" }) {
                            item {
                                GeneratingIndicatorCard()
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit and Cross Critique Dialog
    if (uiState.editingMessage != null) {
        EditAndCrossCritiqueDialog(
            message = uiState.editingMessage!!,
            initialSource = uiState.editingSource,
            onDismiss = { viewModel.dismissEditing() },
            onSubmit = { editedText, targetModel, instruction ->
                viewModel.resubmitEditedResponse(
                    message = uiState.editingMessage!!,
                    sourceModel = uiState.editingSource,
                    editedText = editedText,
                    targetModel = targetModel,
                    userInstruction = instruction
                )
            }
        )
    }

    // Model Settings Dialog
    if (showSettingsDialog) {
        ModelSettingsDialog(
            selectedGeminiModel = uiState.selectedGeminiModel,
            selectedChatGptModel = uiState.selectedChatGptModel,
            enableHighThinking = uiState.enableHighThinking,
            pluginBridgeMode = uiState.pluginBridgeMode,
            openAiApiKey = uiState.openAiApiKey,
            onGeminiModelSelected = { viewModel.setGeminiModel(it) },
            onChatGptModelSelected = { viewModel.setChatGptModel(it) },
            onToggleHighThinking = { viewModel.toggleHighThinking(it) },
            onTogglePluginBridge = { viewModel.togglePluginBridgeMode(it) },
            onSaveOpenAiKey = {
                viewModel.setOpenAiApiKey(it)
                showSettingsDialog = false
            },
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
private fun DualMessageCard(
    message: ChatMessage,
    viewMode: ChatViewMode,
    isSynthesizing: Boolean,
    onEditResponse: (source: String) -> Unit,
    onSynthesizeComparison: () -> Unit,
    onCopyText: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // User Prompt Bubble
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(0.88f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Twoje zapytanie",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message.prompt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Edited indicator banner
        if (message.isEdited) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Odpowiedź została zmodyfikowana z [${message.editedSource.uppercase()}] i przekazana drugiemu modelowi.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        // Dual AI Response Layout
        when (viewMode) {
            ChatViewMode.STACKED -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModelResponseCard(
                        title = "Gemini",
                        modelName = message.geminiModel,
                        content = message.geminiResponse,
                        brandColor = GeminiPrimary,
                        isHighThinking = message.geminiThinking,
                        onEdit = { onEditResponse("gemini") },
                        onCopy = { onCopyText(message.geminiResponse) }
                    )
                    ModelResponseCard(
                        title = "ChatGPT",
                        modelName = message.chatGptModel,
                        content = message.chatGptResponse,
                        brandColor = ChatGptPrimary,
                        isHighThinking = false,
                        onEdit = { onEditResponse("chatgpt") },
                        onCopy = { onCopyText(message.chatGptResponse) }
                    )
                }
            }
            ChatViewMode.SPLIT_COLUMNS -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        ModelResponseCard(
                            title = "Gemini",
                            modelName = message.geminiModel,
                            content = message.geminiResponse,
                            brandColor = GeminiPrimary,
                            isHighThinking = message.geminiThinking,
                            onEdit = { onEditResponse("gemini") },
                            onCopy = { onCopyText(message.geminiResponse) }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        ModelResponseCard(
                            title = "ChatGPT",
                            modelName = message.chatGptModel,
                            content = message.chatGptResponse,
                            brandColor = ChatGptPrimary,
                            isHighThinking = false,
                            onEdit = { onEditResponse("chatgpt") },
                            onCopy = { onCopyText(message.chatGptResponse) }
                        )
                    }
                }
            }
            ChatViewMode.TABS -> {
                var selectedTab by remember { mutableIntStateOf(0) }
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedTab = 0 }
                                    .background(if (selectedTab == 0) GeminiPrimary.copy(alpha = 0.2f) else Color.Transparent)
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Gemini",
                                    color = if (selectedTab == 0) GeminiPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedTab = 1 }
                                    .background(if (selectedTab == 1) ChatGptPrimary.copy(alpha = 0.2f) else Color.Transparent)
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "ChatGPT",
                                    color = if (selectedTab == 1) ChatGptPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }

                        Box(modifier = Modifier.padding(8.dp)) {
                            if (selectedTab == 0) {
                                ModelResponseCard(
                                    title = "Gemini",
                                    modelName = message.geminiModel,
                                    content = message.geminiResponse,
                                    brandColor = GeminiPrimary,
                                    isHighThinking = message.geminiThinking,
                                    onEdit = { onEditResponse("gemini") },
                                    onCopy = { onCopyText(message.geminiResponse) }
                                )
                            } else {
                                ModelResponseCard(
                                    title = "ChatGPT",
                                    modelName = message.chatGptModel,
                                    content = message.chatGptResponse,
                                    brandColor = ChatGptPrimary,
                                    isHighThinking = false,
                                    onEdit = { onEditResponse("chatgpt") },
                                    onCopy = { onCopyText(message.chatGptResponse) }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Synthesis & Comparison Trigger Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onSynthesizeComparison,
                enabled = !isSynthesizing && message.status == "completed",
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("synthesize_comparison_button")
            ) {
                if (isSynthesizing) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Analizowanie...", fontSize = 12.sp)
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = GeminiSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Porównaj i zoptymalizuj odpowiedzi", fontSize = 12.sp)
                }
            }
        }

        // Comparison Notes Card (if generated)
        if (message.comparisonNotes.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GeminiSecondary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = GeminiSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Synteza i bezpośrednie porównanie (AI)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = GeminiSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = message.comparisonNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelResponseCard(
    title: String,
    modelName: String,
    content: String,
    brandColor: Color,
    isHighThinking: Boolean,
    onEdit: () -> Unit,
    onCopy: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, brandColor.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(brandColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = brandColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "($modelName)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isHighThinking) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = GeminiPrimary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "🧠 High Thinking",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = GeminiPrimary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Copy button
                IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Kopiuj",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Body text
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Edit and cross-resubmit button
            OutlinedButton(
                onClick = onEdit,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = brandColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_response_${title.lowercase()}_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Edytuj i prześlij do drugiego modelu",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun GeneratingIndicatorCard() {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Gemini i ChatGPT generują odpowiedzi jednocześnie...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyChatWelcome(onSelectPrompt: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    brush = Brush.linearGradient(listOf(GeminiPrimary, ChatGptPrimary)),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Witaj w Dual AI Chat",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Rozmawiaj jednocześnie z Gemini i ChatGPT w jednym oknie.\nPorównuj odpowiedzi, edytuj je i przesyłaj do drugiego modelu w celu wzajemnej korekty.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Wypróbuj przykładowe zapytanie:",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        val promptSuggestions = listOf(
            "Napisz algorytm w Kotlin i wyjaśnij złożoność obliczeniową",
            "Porównaj teorię względności Einsteina z mechaniką kwantową",
            "Napisz przekonujący e-mail biznesowy z ofertą współpracy",
            "Stwórz 3 kreatywne pomysły na aplikację mobilną z AI"
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            promptSuggestions.forEach { suggestion ->
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPrompt(suggestion) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = GeminiSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomChatInput(
    inputPrompt: String,
    onPromptChange: (String) -> Unit,
    isGenerating: Boolean,
    pluginMode: Boolean,
    onTogglePluginMode: () -> Unit,
    onSend: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier.imePadding()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Mode pills bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                // Plugin mode chip
                FilterChip(
                    selected = pluginMode,
                    onClick = onTogglePluginMode,
                    label = {
                        Text(
                            text = if (pluginMode) "Wtyczka ChatGPT <-> Gemini: Włączona" else "Wtyczka: Wyłączona",
                            fontSize = 11.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (pluginMode) ChatGptPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ChatGptPrimary.copy(alpha = 0.2f),
                        selectedLabelColor = ChatGptPrimary
                    )
                )

                Text(
                    text = "Gemini + ChatGPT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Input Row
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = inputPrompt,
                    onValueChange = onPromptChange,
                    placeholder = { Text("Zadaj pytanie obu modelom...") },
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_text_field")
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onSend,
                    enabled = inputPrompt.isNotBlank() && !isGenerating,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("send_prompt_button")
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Wyślij zapytanie",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerContent(
    sessions: List<com.example.data.model.ChatSession>,
    activeSessionId: String?,
    onSelectSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val currentUser = FirebaseAuth.getInstance().currentUser

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // User profile header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentUser?.displayName?.take(1) ?: currentUser?.email?.take(1) ?: "U",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentUser?.displayName ?: "Zalogowany użytkownik",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentUser?.email ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // New Chat Button
        Button(
            onClick = onNewSession,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("new_chat_button")
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Nowy czat Dual AI")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Zapisane konwersacje (${sessions.size})",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(sessions, key = { it.id }) { session ->
                val isSelected = session.id == activeSessionId
                NavigationDrawerItem(
                    label = {
                        Text(
                            text = session.title.ifBlank { "Konwersacja" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    selected = isSelected,
                    onClick = { onSelectSession(session.id) },
                    badge = {
                        IconButton(
                            onClick = { onDeleteSession(session.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Usuń sesję",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Sign Out Button
        OutlinedButton(
            onClick = onSignOut,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sign_out_button")
        ) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Wyloguj się")
        }
    }
}
