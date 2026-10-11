package com.example.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessage
import com.example.ui.theme.ChatGptPrimary
import com.example.ui.theme.GeminiPrimary

@Composable
fun EditAndCrossCritiqueDialog(
    message: ChatMessage,
    initialSource: String,
    onDismiss: () -> Unit,
    onSubmit: (editedText: String, targetModel: String, instruction: String?) -> Unit
) {
    val initialText = if (initialSource == "gemini") message.geminiResponse else message.chatGptResponse
    var editedContent by remember { mutableStateOf(initialText) }
    var targetModel by remember {
        mutableStateOf(if (initialSource == "gemini") "chatgpt" else "gemini")
    }

    val presetInstructions = listOf(
        "Sprawdź i skoryguj ewentualne błędy logiczne lub merytoryczne",
        "Zoptymalizuj kod i wyjaśnij ulepszenia",
        "Kontynuuj wątek i rozwiń temat w oparciu o tę wersję",
        "Oceń jakość argumentacji i dodaj brakujące aspekty"
    )
    var selectedInstruction by remember { mutableStateOf(presetInstructions[0]) }
    var customInstruction by remember { mutableStateOf("") }
    var useCustomInstruction by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = if (initialSource == "gemini") GeminiPrimary else ChatGptPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Edytuj odpowiedź ${if (initialSource == "gemini") "Gemini" else "ChatGPT"}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Zamknij")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Zmodyfikuj odpowiedź poniżej, a następnie prześlij ją do drugiego modelu w celu weryfikacji, usunięcia błędów lub kontynuacji dyskusji:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Editable Response Field
                OutlinedTextField(
                    value = editedContent,
                    onValueChange = { editedContent = it },
                    label = { Text("Zmodyfikowana treść") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .testTag("edited_response_text_field"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Target Model Selection
                Text(
                    text = "Docelowy model do recenzji i kontynuacji:",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        RadioButton(
                            selected = targetModel == "chatgpt",
                            onClick = { targetModel = "chatgpt" }
                        )
                        Text(
                            text = "ChatGPT (gpt-4o)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ChatGptPrimary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        RadioButton(
                            selected = targetModel == "gemini",
                            onClick = { targetModel = "gemini" }
                        )
                        Text(
                            text = "Gemini (3.1 Pro Thinking)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GeminiPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Presets & Instructions
                Text(
                    text = "Instrukcja dla modelu:",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    presetInstructions.forEach { preset ->
                        FilterChip(
                            selected = !useCustomInstruction && selectedInstruction == preset,
                            onClick = {
                                useCustomInstruction = false
                                selectedInstruction = preset
                            },
                            label = { Text(preset, fontSize = 12.sp) },
                            leadingIcon = if (!useCustomInstruction && selectedInstruction == preset) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }

                    FilterChip(
                        selected = useCustomInstruction,
                        onClick = { useCustomInstruction = true },
                        label = { Text("Własna instrukcja...", fontSize = 12.sp) }
                    )
                }

                if (useCustomInstruction) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customInstruction,
                        onValueChange = { customInstruction = it },
                        placeholder = { Text("Wpisz polecenie dla drugiego modelu...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalInstruction = if (useCustomInstruction) customInstruction.ifBlank { selectedInstruction } else selectedInstruction
                    onSubmit(editedContent, targetModel, finalInstruction)
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (targetModel == "gemini") GeminiPrimary else ChatGptPrimary
                ),
                modifier = Modifier.testTag("submit_edited_response_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Prześlij i optymalizuj")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Anuluj")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
