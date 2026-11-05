package com.assistant.nova

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.assistant.nova.ui.NovaViewModel
import com.assistant.nova.ui.navigation.NovaAppNavigation
import com.assistant.nova.ui.theme.DarkVoid
import com.assistant.nova.ui.theme.NovaAssistantTheme

class MainActivity : ComponentActivity() {

    private val viewModel: NovaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NovaAssistantTheme {
                // Runtime Permission requester for microphone
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    viewModel.setMicPermissionGranted(isGranted)
                }

                LaunchedEffect(Unit) {
                    val hasMicPermission = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                    viewModel.setMicPermissionGranted(hasMicPermission)

                    if (!hasMicPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkVoid
                ) {
                    NovaAppNavigation(viewModel = viewModel)
                }
            }
        }
    }
}
