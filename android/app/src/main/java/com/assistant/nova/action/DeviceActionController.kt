package com.assistant.nova.action

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log
import com.assistant.nova.domain.model.ActionResult
import com.assistant.nova.domain.model.AgentAction
import com.assistant.nova.domain.model.SettingType

class DeviceActionController(private val context: Context) {

    fun executeAction(action: AgentAction): ActionResult {
        return when (action) {
            is AgentAction.LaunchApp -> launchApp(action.appName, action.packageName)
            is AgentAction.SetAlarm -> setAlarm(action.hour, action.minute, action.label)
            is AgentAction.SetTimer -> setTimer(action.durationSeconds, action.label)
            is AgentAction.OpenSetting -> openSetting(action.settingType)
            is AgentAction.PrepareCall -> {
                if (!action.isApproved) {
                    ActionResult.RequiresConfirmation(
                        action = action,
                        prompt = "Do you want me to place a call to ${action.contactName} (${action.phoneNumber.ifBlank { "number" }})?"
                    )
                } else {
                    executePhoneCall(action.phoneNumber)
                }
            }
            is AgentAction.PrepareMessage -> {
                if (!action.isApproved) {
                    ActionResult.RequiresConfirmation(
                        action = action,
                        prompt = "Ready to send message to ${action.recipient}:\n\"${action.messageText}\"\nProceed?"
                    )
                } else {
                    executeSendMessage(action.recipient, action.messageText)
                }
            }
            is AgentAction.SaveMemory -> ActionResult.Success("Memory saved: ${action.key} = ${action.value}")
            is AgentAction.RecallMemory -> ActionResult.Success("Memory query: ${action.query}")
            is AgentAction.GeneralResponse -> ActionResult.Success(action.responseText)
        }
    }

    private fun launchApp(appName: String, explicitPackage: String?): ActionResult {
        val pm = context.packageManager

        // 1. Try explicit package if provided
        if (!explicitPackage.isNullOrBlank()) {
            val launchIntent = pm.getLaunchIntentForPackage(explicitPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return ActionResult.Success("Launched $explicitPackage")
            }
        }

        // 2. Scan installed applications
        try {
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val normalizedQuery = appName.trim().lowercase()

            val targetApp = installedApps.firstOrNull { appInfo ->
                val label = pm.getApplicationLabel(appInfo).toString().lowercase()
                label == normalizedQuery || label.contains(normalizedQuery)
            }

            if (targetApp != null) {
                val launchIntent = pm.getLaunchIntentForPackage(targetApp.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    val label = pm.getApplicationLabel(targetApp)
                    return ActionResult.Success("Opened $label")
                }
            }

            return ActionResult.Failure("Could not find installed application matching '$appName'")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app: $appName", e)
            return ActionResult.Failure("Failed to launch $appName: ${e.message}")
        }
    }

    private fun setAlarm(hour: Int, minute: Int, label: String): ActionResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.Success("Alarm set for ${String.format("%02d:%02d", hour, minute)}")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting alarm", e)
            ActionResult.Failure("Failed to set alarm: ${e.message}")
        }
    }

    private fun setTimer(durationSeconds: Int, label: String): ActionResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, durationSeconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.Success("Timer set for $durationSeconds seconds")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting timer", e)
            ActionResult.Failure("Failed to set timer: ${e.message}")
        }
    }

    private fun openSetting(type: SettingType): ActionResult {
        val actionString = when (type) {
            SettingType.WIFI -> Settings.ACTION_WIFI_SETTINGS
            SettingType.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS
            SettingType.DISPLAY -> Settings.ACTION_DISPLAY_SETTINGS
            SettingType.SOUND -> Settings.ACTION_SOUND_SETTINGS
            SettingType.BATTERY -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            SettingType.GENERAL -> Settings.ACTION_SETTINGS
        }

        return try {
            val intent = Intent(actionString).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.Success("Opened ${type.name.lowercase()} settings")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening setting $type", e)
            ActionResult.Failure("Could not open ${type.name.lowercase()} settings: ${e.message}")
        }
    }

    private fun executePhoneCall(phoneNumber: String): ActionResult {
        return try {
            val cleanNumber = phoneNumber.filter { it.isDigit() || it == '+' }
            val intent = if (cleanNumber.isNotBlank()) {
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Intent.ACTION_DIAL).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            ActionResult.Success("Dialing $cleanNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Error placing call", e)
            ActionResult.Failure("Call failed: ${e.message}")
        }
    }

    private fun executeSendMessage(recipient: String, text: String): ActionResult {
        return try {
            val uri = Uri.parse("smsto:$recipient")
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                putExtra("sms_body", text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.Success("Prepared message for $recipient")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            ActionResult.Failure("Message preparation failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "DeviceActionController"
    }
}
