package com.assistant.nova.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.nova.domain.model.ChatMessage
import com.assistant.nova.domain.model.MessageSender
import com.assistant.nova.ui.theme.DarkBorder
import com.assistant.nova.ui.theme.DarkCard
import com.assistant.nova.ui.theme.DarkSurface
import com.assistant.nova.ui.theme.NeonCyan
import com.assistant.nova.ui.theme.NeonPurple
import com.assistant.nova.ui.theme.TextPrimary
import com.assistant.nova.ui.theme.TextSecondary
import com.assistant.nova.ui.theme.TextTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.sender == MessageSender.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bubbleShape = if (isUser) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }

    val bubbleBackground = if (isUser) {
        DarkCard
    } else {
        DarkSurface
    }

    val borderColor = if (isUser) NeonPurple.copy(alpha = 0.35f) else NeonCyan.copy(alpha = 0.35f)
    val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    val formattedTime = timeFormatter.format(Date(message.timestamp))

    val languageBadge = when (message.detectedLanguage) {
        "ta" -> "தமிழ்"
        "ta-Latn" -> "Tanglish"
        else -> "EN"
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(bubbleShape)
                .background(bubbleBackground)
                .border(1.dp, borderColor, bubbleShape)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "You" else "NOVA",
                        color = if (isUser) NeonPurple else NeonCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(DarkBorder)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = languageBadge,
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = formattedTime,
                            color = TextTertiary,
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = message.content,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
        }
    }
}
