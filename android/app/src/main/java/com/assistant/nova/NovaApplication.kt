package com.assistant.nova

import android.app.Application
import com.assistant.nova.data.local.NovaDatabase
import com.assistant.nova.data.local.PreferencesManager
import com.assistant.nova.data.remote.GeminiApiClient
import com.assistant.nova.data.repository.AssistantRepository
import com.assistant.nova.data.repository.AssistantRepositoryImpl
import com.assistant.nova.domain.usecase.DetectLanguageUseCase
import com.assistant.nova.domain.usecase.ExecuteAgentActionUseCase
import com.assistant.nova.domain.usecase.ManageMemoryUseCase
import com.assistant.nova.domain.usecase.ProcessVoiceInputUseCase
import com.assistant.nova.action.DeviceActionController

class NovaApplication : Application() {

    lateinit var database: NovaDatabase
        private set

    lateinit var preferencesManager: PreferencesManager
        private set

    lateinit var geminiApiClient: GeminiApiClient
        private set

    lateinit var repository: AssistantRepository
        private set

    lateinit var deviceActionController: DeviceActionController
        private set

    lateinit var detectLanguageUseCase: DetectLanguageUseCase
        private set

    lateinit var manageMemoryUseCase: ManageMemoryUseCase
        private set

    lateinit var executeAgentActionUseCase: ExecuteAgentActionUseCase
        private set

    lateinit var processVoiceInputUseCase: ProcessVoiceInputUseCase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = NovaDatabase.getInstance(this)
        preferencesManager = PreferencesManager(this)
        geminiApiClient = GeminiApiClient(preferencesManager)
        deviceActionController = DeviceActionController(this)

        repository = AssistantRepositoryImpl(
            conversationDao = database.conversationDao(),
            userMemoryDao = database.userMemoryDao(),
            actionLogDao = database.actionLogDao(),
            geminiApiClient = geminiApiClient
        )

        detectLanguageUseCase = DetectLanguageUseCase()
        manageMemoryUseCase = ManageMemoryUseCase(repository)
        executeAgentActionUseCase = ExecuteAgentActionUseCase(deviceActionController, repository)
        processVoiceInputUseCase = ProcessVoiceInputUseCase(
            repository = repository,
            detectLanguageUseCase = detectLanguageUseCase,
            manageMemoryUseCase = manageMemoryUseCase
        )
    }

    companion object {
        lateinit var instance: NovaApplication
            private set
    }
}
