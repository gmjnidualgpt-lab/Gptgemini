package com.example.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ChatGptPrimary
import com.example.ui.theme.GeminiPrimary

@Composable
fun ModelSettingsDialog(
    selectedGeminiModel: String,
    selectedChatGptModel: String,
    enableHighThinking: Boolean,
    pluginBridgeMode: Boolean,
    openAiApiKey: String,
    onGeminiModelSelected: (String) -> Unit,
    onChatGptModelSelected: (String) -> Unit,
    onToggleHighThinking: (Boolean) -> Unit,
    onTogglePluginBridge: (Boolean) -> Unit,
    onSaveOpenAiKey: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var keyInput by remember { mutableStateOf(openAiApiKey) }
    var keyVisible by remember { mutableStateOf(false) }

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
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Konfiguracja Modeli i Integracji",
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
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Gemini Models & High Thinking
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GeminiPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Model Gemini", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val geminiOptions = listOf(
                            "gemini-3.1-pro-preview" to "3.1 Pro (Złożone zadania)",
                            "gemini-3.5-flash" to "3.5 Flash (Ogólne)",
                            "gemini-3.1-flash-lite-preview" to "3.1 Flash-Lite (Szybkie)"
                        )

                        geminiOptions.forEach { (modelKey, label) ->
                            FilterChip(
                                selected = selectedGeminiModel == modelKey,
                                onClick = { onGeminiModelSelected(modelKey) },
                                label = { Text(label, fontSize = 12.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // High Thinking Toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Psychology, contentDescription = null, tint = GeminiPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("High Thinking Mode", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                }
                                Text("Głębokie rozumowanie Gemini 3.1 Pro (ThinkingLevel.HIGH)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = enableHighThinking,
                                onCheckedChange = onToggleHighThinking,
                                modifier = Modifier.testTag("toggle_high_thinking_switch")
                            )
                        }
                    }
                }

                // Section 2: ChatGPT Models
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = ChatGptPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Model ChatGPT", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val chatGptOptions = listOf(
                            "gpt-4o" to "GPT-4o (Wszechstronny flagowiec)",
                            "gpt-4o-mini" to "GPT-4o mini (Szybki i lekki)",
                            "o3-mini" to "o3-mini (Rozumowanie matematyczno-kodowe)"
                        )

                        chatGptOptions.forEach { (modelKey, label) ->
                            FilterChip(
                                selected = selectedChatGptModel == modelKey,
                                onClick = { onChatGptModelSelected(modelKey) },
                                label = { Text(label, fontSize = 12.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }
                }

                // Section 3: Wtyczka ChatGPT <-> Gemini (Plugin Bridge)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Wtyczka ChatGPT <-> Gemini", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Text("Automatyczna synteza i debata wzajemna między ChatGPT i Gemini po każdej odpowiedzi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = pluginBridgeMode,
                                onCheckedChange = onTogglePluginBridge
                            )
                        }
                    }
                }

                // Section 4: OpenAI API Key
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = ChatGptPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Klucz OpenAI API (ChatGPT)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Domyślnie aktywny jest wbudowany asystent demonstracyjny. Wpisz swój klucz sk-..., aby łączyć się bezpośrednio z oficjalnym OpenAI API:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { keyInput = it },
                            placeholder = { Text("sk-proj-...") },
                            singleLine = true,
                            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { keyVisible = !keyVisible }) {
                                    Icon(
                                        imageVector = if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (keyVisible) "Ukryj" else "Pokaż"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { onSaveOpenAiKey(keyInput) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Zapisz klucz OpenAI")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Gotowe")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
