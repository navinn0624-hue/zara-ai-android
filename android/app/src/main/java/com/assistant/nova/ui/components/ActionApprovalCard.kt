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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.nova.domain.model.AgentAction
import com.assistant.nova.ui.theme.DarkBorder
import com.assistant.nova.ui.theme.DarkCard
import com.assistant.nova.ui.theme.DarkVoid
import com.assistant.nova.ui.theme.NeonCyan
import com.assistant.nova.ui.theme.NeonPurple
import com.assistant.nova.ui.theme.NeonRed
import com.assistant.nova.ui.theme.TextPrimary
import com.assistant.nova.ui.theme.TextSecondary

@Composable
fun ActionApprovalCard(
    action: AgentAction,
    prompt: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(20.dp)
    val actionIcon = when (action) {
        is AgentAction.PrepareCall -> Icons.Default.Call
        is AgentAction.PrepareMessage -> Icons.Default.Message
        else -> Icons.Default.Security
    }

    val actionTitle = when (action) {
        is AgentAction.PrepareCall -> "Confirm Phone Call"
        is AgentAction.PrepareMessage -> "Confirm Outgoing Message"
        else -> "Action Approval Required"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(DarkCard)
            .border(1.5.dp, Brush.horizontalGradient(listOf(NeonCyan, NeonPurple)), cardShape)
            .padding(20.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkBorder)
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = actionIcon,
                        contentDescription = null,
                        tint = NeonCyan
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = actionTitle,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "User approval required by security policy",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = prompt,
                color = TextPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = NeonRed
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = NeonRed)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Decline", fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = DarkVoid
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = DarkVoid)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Approve", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
