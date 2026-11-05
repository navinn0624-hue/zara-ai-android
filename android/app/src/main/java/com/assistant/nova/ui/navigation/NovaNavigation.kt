package com.assistant.nova.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.assistant.nova.ui.NovaViewModel
import com.assistant.nova.ui.screens.main.MainScreen
import com.assistant.nova.ui.screens.settings.SettingsScreen

enum class Screen {
    MAIN,
    SETTINGS
}

@Composable
fun NovaAppNavigation(viewModel: NovaViewModel) {
    var currentScreen by remember { mutableStateOf(Screen.MAIN) }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            if (targetState == Screen.SETTINGS) {
                (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                    slideOutHorizontally { width -> -width } + fadeOut()
                )
            } else {
                (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                    slideOutHorizontally { width -> width } + fadeOut()
                )
            }
        },
        label = "ScreenNavigation"
    ) { screen ->
        when (screen) {
            Screen.MAIN -> MainScreen(
                viewModel = viewModel,
                onNavigateToSettings = { currentScreen = Screen.SETTINGS }
            )
            Screen.SETTINGS -> SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { currentScreen = Screen.MAIN }
            )
        }
    }
}
