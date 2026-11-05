package com.assistant.nova

import com.assistant.nova.action.DeviceActionController
import com.assistant.nova.data.repository.AssistantRepository
import com.assistant.nova.domain.model.ActionResult
import com.assistant.nova.domain.model.AgentAction
import com.assistant.nova.domain.model.SettingType
import com.assistant.nova.domain.usecase.ExecuteAgentActionUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class ExecuteAgentActionUseCaseTest {

    private lateinit var mockController: DeviceActionController
    private lateinit var mockRepository: AssistantRepository
    private lateinit var useCase: ExecuteAgentActionUseCase

    @Before
    fun setUp() {
        mockController = mock(DeviceActionController::class.java)
        mockRepository = mock(AssistantRepository::class.java)
        useCase = ExecuteAgentActionUseCase(mockController, mockRepository)
    }

    @Test
    fun execute_saveMemoryAction_savesToRepository() = runTest {
        val action = AgentAction.SaveMemory(key = "hometown", value = "Chennai")

        val result = useCase.execute(action)

        assertTrue(result is ActionResult.Success)
        verify(mockRepository).saveMemory("hometown", "Chennai")
        verify(mockRepository).logAction("SaveMemory", "hometown: Chennai", "SUCCESS")
    }

    @Test
    fun execute_unapprovedSensitiveCallAction_requiresConfirmation() = runTest {
        val action = AgentAction.PrepareCall(contactName = "Mom", phoneNumber = "+919876543210")
        `when`(mockController.executeAction(action)).thenReturn(
            ActionResult.RequiresConfirmation(action, "Call Mom?")
        )

        val result = useCase.execute(action, userApproved = false)

        assertTrue(result is ActionResult.RequiresConfirmation)
        val confirmation = result as ActionResult.RequiresConfirmation
        assertEquals("Mom", (confirmation.action as AgentAction.PrepareCall).contactName)
    }

    @Test
    fun execute_safeAlarmAction_executesSuccessfully() = runTest {
        val action = AgentAction.SetAlarm(hour = 7, minute = 30, label = "Morning Walk")
        `when`(mockController.executeAction(action)).thenReturn(
            ActionResult.Success("Alarm set for 07:30")
        )

        val result = useCase.execute(action)

        assertTrue(result is ActionResult.Success)
        val success = result as ActionResult.Success
        assertEquals("Alarm set for 07:30", success.message)
    }
}
