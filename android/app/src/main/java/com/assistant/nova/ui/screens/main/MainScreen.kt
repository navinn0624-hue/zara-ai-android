package com.assistant.nova.ui.screens.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.nova.ui.NovaUiState
import com.assistant.nova.ui.NovaViewModel
import com.assistant.nova.ui.components.ActionApprovalCard
import com.assistant.nova.ui.components.ChatBubble
import com.assistant.nova.ui.components.NovaOrb
import com.assistant.nova.ui.components.OrbState
import com.assistant.nova.ui.components.VoiceWaveform
import com.assistant.nova.ui.theme.DarkBorder
import com.assistant.nova.ui.theme.DarkCard
import com.assistant.nova.ui.theme.DarkSurface
import com.assistant.nova.ui.theme.DarkVoid
import com.assistant.nova.ui.theme.NeonCyan
import com.assistant.nova.ui.theme.NeonGreen
import com.assistant.nova.ui.theme.NeonMagenta
import com.assistant.nova.ui.theme.NeonPurple
import com.assistant.nova.ui.theme.TextPrimary
import com.assistant.nova.ui.theme.TextSecondary
import com.assistant.nova.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: NovaViewModel,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var typedText by remember { mutableStateOf("") }
    val chatListState = rememberLazyListState()

    // Auto-scroll chat to latest message
    LaunchedEffect(uiState.conversations.size) {
        if (uiState.conversations.isNotEmpty()) {
            chatListState.animateScrollToItem(uiState.conversations.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkVoid)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top Bar
            TopBar(
                isApiKeySet = uiState.isApiKeySet,
                onClearChat = { viewModel.clearHistory() },
                onSettingsClick = onNavigateToSettings
            )

            // 2. Chat history or Center Orb view
            if (uiState.conversations.isEmpty()) {
                // Empty state: Hero view with Animated AI Orb & Voice Waveform
                Spacer(modifier = Modifier.weight(0.5f))

                NovaOrb(
                    state = uiState.orbState,
                    rmsAmplitude = uiState.rmsAmplitude,
                    size = 230.dp,
                    onClick = { viewModel.onMicTrigger() }
                )

                Spacer(modifier = Modifier.height(24.dp))

                VoiceWaveform(
                    isActive = uiState.orbState == OrbState.LISTENING || uiState.orbState == OrbState.SPEAKING,
                    rmsAmplitude = uiState.rmsAmplitude
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Status message pill
                StatusPill(
                    statusMessage = uiState.statusMessage,
                    liveTranscript = uiState.liveTranscript
                )

                Spacer(modifier = Modifier.weight(0.5f))

                // Suggestion chips
                QuickActionRow(onActionSelected = { prompt ->
                    viewModel.submitUserSpeech(prompt)
                })

                Spacer(modifier = Modifier.height(16.dp))
            } else {
                // Active conversation: compact orb + scrollable chat list
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LazyColumn(
                        state = chatListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.conversations, key = { it.id }) { message ->
                            ChatBubble(message = message)
                        }
                    }
                }

                // Compact Orb & Live status bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    NovaOrb(
                        state = uiState.orbState,
                        rmsAmplitude = uiState.rmsAmplitude,
                        size = 54.dp,
                        onClick = { viewModel.onMicTrigger() }
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = if (uiState.liveTranscript.isNotBlank()) "\"${uiState.liveTranscript}\"" else uiState.statusMessage,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        maxLines = 2,
                        modifier = Modifier.weight(1f)
                    )

                    VoiceWaveform(
                        isActive = uiState.orbState == OrbState.LISTENING || uiState.orbState == OrbState.SPEAKING,
                        rmsAmplitude = uiState.rmsAmplitude,
                        barCount = 8,
                        maxBarHeight = 24.dp,
                        minBarHeight = 4.dp
                    )
                }
            }

            // 3. Sensitive Action Confirmation Overlay
            AnimatedVisibility(
                visible = uiState.pendingConfirmationAction != null,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                uiState.pendingConfirmationAction?.let { action ->
                    Box(modifier = Modifier.padding(16.dp)) {
                        ActionApprovalCard(
                            action = action,
                            prompt = uiState.pendingConfirmationPrompt ?: "",
                            onConfirm = { viewModel.confirmPendingAction() },
                            onDismiss = { viewModel.dismissPendingAction() }
                        )
                    }
                }
            }

            // 4. Bottom Controls (Mic button & Text fallback)
            BottomControlBar(
                orbState = uiState.orbState,
                text = typedText,
                onTextChanged = { typedText = it },
                onSend = {
                    if (typedText.isNotBlank()) {
                        viewModel.submitUserSpeech(typedText)
                        typedText = ""
                    }
                },
                onMicClick = { viewModel.onMicTrigger() }
            )
        }
    }
}

@Composable
private fun TopBar(
    isApiKeySet: Boolean,
    onClearChat: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isApiKeySet) NeonGreen else NeonMagenta)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "NOVA",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkCard)
                    .border(1.dp, DarkBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "HEY NOVA",
                    color = NeonCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Row {
            IconButton(onClick = onClearChat) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Clear Chat",
                    tint = TextSecondary
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun StatusPill(statusMessage: String, liveTranscript: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(DarkCard)
            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(
            text = if (liveTranscript.isNotBlank()) "\"$liveTranscript\"" else statusMessage,
            color = if (liveTranscript.isNotBlank()) NeonCyan else TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun QuickActionRow(onActionSelected: (String) -> Unit) {
    val quickActions = listOf(
        "Alarm at 7 AM" to "Set an alarm for 7:00 AM",
        "WhatsApp திற" to "Open WhatsApp",
        "Tamil Chat" to "வணக்கம் நோவா! உங்களால் என்ன செய்ய முடியும்?",
        "Tanglish" to "Naalaiku weather eppadi irukkum?",
        "Timer 5 Min" to "Set a timer for 5 minutes"
    )

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp)
    ) {
        items(quickActions) { (label, command) ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCard)
                    .border(1.dp, NeonPurple.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .clickable { onActionSelected(command) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = label,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun BottomControlBar(
    orbState: OrbState,
    text: String,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Text Input Box
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(24.dp))
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = text,
                    onValueChange = onTextChanged,
                    placeholder = {
                        Text(
                            text = "Ask NOVA or say 'Hey NOVA'...",
                            color = TextTertiary,
                            fontSize = 14.sp
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = NeonCyan,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                if (text.isNotBlank()) {
                    IconButton(onClick = onSend) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = NeonCyan
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Center Pulsing Neon Mic Button
        val micGlow = if (orbState == OrbState.LISTENING) {
            Brush.radialGradient(listOf(NeonCyan, NeonPurple))
        } else {
            Brush.radialGradient(listOf(DarkCard, DarkSurface))
        }

        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(micGlow)
                .border(1.5.dp, if (orbState == OrbState.LISTENING) NeonCyan else DarkBorder, CircleShape)
                .clickable(onClick = onMicClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (orbState == OrbState.LISTENING) Icons.Default.GraphicEq else Icons.Default.Mic,
                contentDescription = "Microphone",
                tint = if (orbState == OrbState.LISTENING) DarkVoid else NeonCyan,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
