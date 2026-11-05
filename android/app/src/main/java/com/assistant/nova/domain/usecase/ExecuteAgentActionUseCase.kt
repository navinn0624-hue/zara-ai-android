package com.assistant.nova.domain.usecase

import com.assistant.nova.action.DeviceActionController
import com.assistant.nova.data.repository.AssistantRepository
import com.assistant.nova.domain.model.ActionResult
import com.assistant.nova.domain.model.AgentAction

class ExecuteAgentActionUseCase(
    private val actionController: DeviceActionController,
    private val repository: AssistantRepository
) {

    suspend fun execute(action: AgentAction, userApproved: Boolean = false): ActionResult {
        // Handle approved state for sensitive actions
        val preparedAction = when (action) {
            is AgentAction.PrepareCall -> action.copy(isApproved = userApproved)
            is AgentAction.PrepareMessage -> action.copy(isApproved = userApproved)
            else -> action
        }

        // Special handling for memory actions
        if (preparedAction is AgentAction.SaveMemory) {
            repository.saveMemory(preparedAction.key, preparedAction.value)
            repository.logAction("SaveMemory", "${preparedAction.key}: ${preparedAction.value}", "SUCCESS")
            return ActionResult.Success("Saved to memory: ${preparedAction.key}")
        }

        val result = actionController.executeAction(preparedAction)

        val logStatus = when (result) {
            is ActionResult.Success -> "SUCCESS"
            is ActionResult.RequiresConfirmation -> "AWAITING_CONFIRMATION"
            is ActionResult.Failure -> "FAILED"
        }
        val actionName = preparedAction::class.java.simpleName
        repository.logAction(actionName, result.toString(), logStatus)

        return result
    }
}
