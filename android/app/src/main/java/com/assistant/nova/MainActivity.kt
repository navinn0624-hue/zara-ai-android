package com.assistant.nova

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.assistant.nova.services.NovaWakeWordService
import com.assistant.nova.ui.NovaViewModel
import com.assistant.nova.ui.navigation.NovaAppNavigation
import com.assistant.nova.ui.theme.NovaAssistantTheme

class MainActivity : ComponentActivity() {

    private val viewModel: NovaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureLockScreenVisibility()

        setContent {
            val currentThemeMode by viewModel.themeMode.collectAsState()

            NovaAssistantTheme(themeMode = currentThemeMode) {
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    viewModel.setMicPermissionGranted(isGranted)
                    if (isGranted) {
                        startBackgroundHotwordService()
                    }
                }

                LaunchedEffect(Unit) {
                    val hasMicPermission = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                    viewModel.setMicPermissionGranted(hasMicPermission)

                    if (!hasMicPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        startBackgroundHotwordService()
                    }

                    // Check if launched by voice wake word from background / lockscreen
                    handleVoiceTriggerIntent(intent)
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NovaAppNavigation(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleVoiceTriggerIntent(intent)
    }

    private fun handleVoiceTriggerIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_AUTO_START_LISTENING, false) == true) {
            intent.removeExtra(EXTRA_AUTO_START_LISTENING)
            viewModel.onAutoVoiceTrigger()
        }
    }

    private fun configureLockScreenVisibility() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun startBackgroundHotwordService() {
        val app = application as? NovaApplication
        if (app?.preferencesManager?.isBackgroundListeningEnabled == true) {
            NovaWakeWordService.startService(this)
        }
    }

    companion object {
        const val EXTRA_AUTO_START_LISTENING = "com.assistant.nova.EXTRA_AUTO_START_LISTENING"
    }
}
