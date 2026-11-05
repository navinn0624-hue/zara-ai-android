package com.assistant.nova.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.nova.NovaApplication
import com.assistant.nova.domain.model.LanguagePreference
import com.assistant.nova.ui.NovaViewModel
import com.assistant.nova.ui.theme.DarkBorder
import com.assistant.nova.ui.theme.DarkCard
import com.assistant.nova.ui.theme.DarkSurface
import com.assistant.nova.ui.theme.DarkVoid
import com.assistant.nova.ui.theme.NeonCyan
import com.assistant.nova.ui.theme.NeonGreen
import com.assistant.nova.ui.theme.NeonPurple
import com.assistant.nova.ui.theme.NeonRed
import com.assistant.nova.ui.theme.TextPrimary
import com.assistant.nova.ui.theme.TextSecondary
import com.assistant.nova.ui.theme.TextTertiary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: NovaViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as NovaApplication
    val prefs = app.preferencesManager
    val scope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf(prefs.geminiApiKey) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf(prefs.languagePreference) }
    var wakeWordEnabled by remember { mutableStateOf(prefs.wakeWordEnabled) }
    var speechRate by remember { mutableFloatStateOf(prefs.ttsSpeechRate) }
    var speechPitch by remember { mutableFloatStateOf(prefs.ttsPitch) }

    val memories by app.manageMemoryUseCase.getMemoriesFlow().collectAsState(initial = emptyList())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkVoid)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "NOVA Settings",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Security & Gemini API Key
                item {
                    SettingsCard(
                        title = "Gemini API Security",
                        icon = Icons.Default.Key
                    ) {
                        Text(
                            text = "To keep your credentials private, your key is stored locally inside Android's Hardware Keystore (EncryptedSharedPreferences) and is never committed to GitHub.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("Gemini API Key", color = TextSecondary) },
                            singleLine = true,
                            visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                    Icon(
                                        imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Visibility",
                                        tint = TextSecondary
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = NeonCyan
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                prefs.geminiApiKey = apiKey
                                viewModel.updateApiKeyStatus()
                                Toast.makeText(context, "API Key saved securely", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                contentColor = DarkVoid
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = DarkVoid)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Key", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Section 2: Language & Bilingual Mode
                item {
                    SettingsCard(
                        title = "Language & Code-Switching",
                        icon = Icons.Default.Language
                    ) {
                        Text(
                            text = "NOVA detects whether you are speaking pure Tamil, English, or conversational Tanglish ('alarm vai 7 am ku').",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        LanguagePreference.entries.forEach { lang ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedLanguage = lang
                                        prefs.languagePreference = lang
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .border(
                                            2.dp,
                                            if (selectedLanguage == lang) NeonCyan else DarkBorder,
                                            CircleShape
                                        )
                                        .background(if (selectedLanguage == lang) NeonCyan else Color.Transparent)
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = lang.displayName,
                                    color = if (selectedLanguage == lang) NeonCyan else TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedLanguage == lang) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Section 3: Wake Word ("Hey NOVA")
                item {
                    SettingsCard(
                        title = "Wake Word ('Hey NOVA')",
                        icon = Icons.Default.Mic
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enable 'Hey NOVA' Activation",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Listens for voice trigger when assistant is active or in foreground.",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }

                            Switch(
                                checked = wakeWordEnabled,
                                onCheckedChange = {
                                    wakeWordEnabled = it
                                    prefs.wakeWordEnabled = it
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = DarkVoid,
                                    checkedTrackColor = NeonCyan,
                                    uncheckedThumbColor = TextSecondary,
                                    uncheckedTrackColor = DarkSurface
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Microphone privacy: In accordance with Android 12+ security guidelines, continuous listening requires foreground microphone permission and will display the OS privacy dot.",
                            color = TextTertiary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                // Section 4: Voice & Speech Rate
                item {
                    SettingsCard(
                        title = "Voice Feedback & Pitch",
                        icon = Icons.Default.VolumeUp
                    ) {
                        Text(text = "Speech Rate (${String.format("%.1fx", speechRate)})", color = TextPrimary, fontSize = 13.sp)
                        Slider(
                            value = speechRate,
                            onValueChange = {
                                speechRate = it
                                prefs.ttsSpeechRate = it
                            },
                            valueRange = 0.7f..1.5f,
                            colors = SliderDefaults.colors(
                                thumbColor = NeonCyan,
                                activeTrackColor = NeonCyan,
                                inactiveTrackColor = DarkBorder
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(text = "Voice Pitch (${String.format("%.1fx", speechPitch)})", color = TextPrimary, fontSize = 13.sp)
                        Slider(
                            value = speechPitch,
                            onValueChange = {
                                speechPitch = it
                                prefs.ttsPitch = it
                            },
                            valueRange = 0.7f..1.5f,
                            colors = SliderDefaults.colors(
                                thumbColor = NeonPurple,
                                activeTrackColor = NeonPurple,
                                inactiveTrackColor = DarkBorder
                            )
                        )
                    }
                }

                // Section 5: Long-term Agent Memory
                item {
                    SettingsCard(
                        title = "Assistant Memory & Facts",
                        icon = Icons.Default.Psychology
                    ) {
                        Text(
                            text = "NOVA remembers facts you've asked it to store (e.g. 'Remember my office is in Guindy'). You can view or delete memories at any time.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        if (memories.isEmpty()) {
                            Text(
                                text = "No persistent memories saved yet.",
                                color = TextTertiary,
                                fontSize = 13.sp
                            )
                        } else {
                            memories.forEach { memory ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = memory.key, color = NeonCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text(text = memory.value, color = TextPrimary, fontSize = 12.sp)
                                    }
                                    IconButton(onClick = {
                                        scope.launch {
                                            app.manageMemoryUseCase.deleteMemory(memory.key)
                                        }
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = NeonRed, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    scope.launch { app.manageMemoryUseCase.clearAll() }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkBorder, contentColor = NeonRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Clear All Memories", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Section 6: Security & Permissions Disclosure
                item {
                    SettingsCard(
                        title = "Privacy & Android Permissions",
                        icon = Icons.Default.Security
                    ) {
                        val permissions = listOf(
                            "Microphone" to "Used exclusively for on-device voice recognition and keyword detection.",
                            "Phone & Calls" to "Always requires interactive confirmation before placing any call.",
                            "SMS & Messaging" to "Never sends messages silently without your explicit approval tap.",
                            "Alarms & Timers" to "Used to set clock alarms via standard Android Intents."
                        )

                        permissions.forEach { (perm, desc) ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(text = perm, color = NeonGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(text = desc, color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            content()
        }
    }
}
