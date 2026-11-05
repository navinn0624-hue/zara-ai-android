package com.assistant.nova.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Hearing
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.assistant.nova.domain.model.ThemeMode
import com.assistant.nova.ui.NovaViewModel
import com.assistant.nova.ui.theme.NeonGreen
import com.assistant.nova.ui.theme.NeonRed
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

    val currentTheme by viewModel.themeMode.collectAsState()
    val isHandsFree by viewModel.isHandsFreeEnabled.collectAsState()
    val isBackgroundListening by viewModel.isBackgroundListening.collectAsState()

    var apiKey by remember { mutableStateOf(prefs.geminiApiKey) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf(prefs.languagePreference) }
    var speechRate by remember { mutableFloatStateOf(prefs.ttsSpeechRate) }
    var speechPitch by remember { mutableFloatStateOf(prefs.ttsPitch) }

    val memories by app.manageMemoryUseCase.getMemoriesFlow().collectAsState(initial = emptyList())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "NOVA Settings",
                    color = MaterialTheme.colorScheme.onBackground,
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
                // Section 1: Appearance & Theme (Light / Dark)
                item {
                    SettingsCard(
                        title = "Appearance & Theme",
                        icon = Icons.Default.DarkMode
                    ) {
                        Text(
                            text = "Switch between Clean Arctic Light mode and Futuristic OLED Dark mode.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        ThemeMode.entries.forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.setThemeMode(mode)
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
                                            if (currentTheme == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            CircleShape
                                        )
                                        .background(if (currentTheme == mode) MaterialTheme.colorScheme.primary else Color.Transparent)
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = mode.displayName,
                                    color = if (currentTheme == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    fontWeight = if (currentTheme == mode) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Section 2: Always-On Background Wake Word ("Hey NOVA")
                item {
                    SettingsCard(
                        title = "Always-On Background Wake Word",
                        icon = Icons.Default.Hearing
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Listen when screen off or in other apps",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Whenever you say 'Hey NOVA', the phone wakes up and starts voice control.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }

                            Switch(
                                checked = isBackgroundListening,
                                onCheckedChange = {
                                    viewModel.setBackgroundListening(it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Battery settings not accessible directly", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.BatteryAlert, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Disable Battery Optimization (Recommended)", fontSize = 12.sp)
                        }
                    }
                }

                // Section 3: Hands-Free Voice Control (No Touch)
                item {
                    SettingsCard(
                        title = "Hands-Free Voice Control (No Touch)",
                        icon = Icons.Default.Mic
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "No Touch Voice Confirmation",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "When calling someone or sending SMS, NOVA asks you via voice. Reply 'Yes' or 'No' without touching the screen.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }

                            Switch(
                                checked = isHandsFree,
                                onCheckedChange = {
                                    viewModel.setHandsFreeMode(it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }

                // Section 4: Security & Gemini API Key
                item {
                    SettingsCard(
                        title = "Gemini API Security",
                        icon = Icons.Default.Key
                    ) {
                        Text(
                            text = "Your API key is stored securely in the Android Hardware Keystore (EncryptedSharedPreferences).",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("Gemini API Key", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            singleLine = true,
                            visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                    Icon(
                                        imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Visibility",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                cursorColor = MaterialTheme.colorScheme.primary
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
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Key", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Section 5: Language & Bilingual Mode
                item {
                    SettingsCard(
                        title = "Language & Code-Switching",
                        icon = Icons.Default.Language
                    ) {
                        Text(
                            text = "NOVA detects whether you are speaking Tamil, English, or conversational Tanglish ('alarm vai 7 am ku').",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                            if (selectedLanguage == lang) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            CircleShape
                                        )
                                        .background(if (selectedLanguage == lang) MaterialTheme.colorScheme.primary else Color.Transparent)
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = lang.displayName,
                                    color = if (selectedLanguage == lang) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedLanguage == lang) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Section 6: Voice & Speech Rate
                item {
                    SettingsCard(
                        title = "Voice Feedback & Pitch",
                        icon = Icons.Default.VolumeUp
                    ) {
                        Text(text = "Speech Rate (${String.format("%.1fx", speechRate)})", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                        Slider(
                            value = speechRate,
                            onValueChange = {
                                speechRate = it
                                prefs.ttsSpeechRate = it
                            },
                            valueRange = 0.7f..1.5f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(text = "Voice Pitch (${String.format("%.1fx", speechPitch)})", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                        Slider(
                            value = speechPitch,
                            onValueChange = {
                                speechPitch = it
                                prefs.ttsPitch = it
                            },
                            valueRange = 0.7f..1.5f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.secondary,
                                activeTrackColor = MaterialTheme.colorScheme.secondary,
                                inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )
                    }
                }

                // Section 7: Long-term Agent Memory
                item {
                    SettingsCard(
                        title = "Assistant Memory & Facts",
                        icon = Icons.Default.Psychology
                    ) {
                        Text(
                            text = "NOVA remembers facts you've asked it to store. You can review or delete memories at any time.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        if (memories.isEmpty()) {
                            Text(
                                text = "No persistent memories saved yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
                                        Text(text = memory.key, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text(text = memory.value, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
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
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = NeonRed
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Clear All Memories", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Section 8: Privacy & Permissions
                item {
                    SettingsCard(
                        title = "Privacy & Android Permissions",
                        icon = Icons.Default.Security
                    ) {
                        val permissions = listOf(
                            "Microphone (Always-On)" to "Listens for 'Hey NOVA' keyword in background foreground service.",
                            "Phone & Calls" to "Voice confirmation allows calling contacts 100% hands-free.",
                            "SMS & Messaging" to "Prepares messages with spoken verification.",
                            "Alarms & Timers" to "Sets clock alarms and timers directly."
                        )

                        permissions.forEach { (perm, desc) ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(text = perm, color = NeonGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(text = desc, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            content()
        }
    }
}
