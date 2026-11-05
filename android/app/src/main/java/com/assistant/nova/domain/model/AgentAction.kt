package com.assistant.nova.domain.model

sealed interface AgentAction {
    val isSensitive: Boolean
        get() = false

    data class LaunchApp(
        val appName: String,
        val packageName: String? = null
    ) : AgentAction

    data class SetAlarm(
        val hour: Int,
        val minute: Int,
        val label: String = "NOVA Alarm"
    ) : AgentAction

    data class SetTimer(
        val durationSeconds: Int,
        val label: String = "NOVA Timer"
    ) : AgentAction

    data class OpenSetting(
        val settingType: SettingType
    ) : AgentAction

    data class PrepareCall(
        val contactName: String,
        val phoneNumber: String,
        var isApproved: Boolean = false
    ) : AgentAction {
        override val isSensitive: Boolean = true
    }

    data class PrepareMessage(
        val recipient: String,
        val messageText: String,
        var isApproved: Boolean = false
    ) : AgentAction {
        override val isSensitive: Boolean = true
    }

    data class SaveMemory(
        val key: String,
        val value: String
    ) : AgentAction

    data class RecallMemory(
        val query: String
    ) : AgentAction

    data class GeneralResponse(
        val responseText: String
    ) : AgentAction
}

enum class SettingType {
    WIFI,
    BLUETOOTH,
    DISPLAY,
    SOUND,
    BATTERY,
    GENERAL
}

sealed interface ActionResult {
    data class Success(val message: String) : ActionResult
    data class RequiresConfirmation(val action: AgentAction, val prompt: String) : ActionResult
    data class Failure(val error: String) : ActionResult
}
