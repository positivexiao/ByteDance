package com.bytedace.doubaoapp

import androidx.compose.runtime.Composable
import com.bytedace.doubaoapp.ui.MainScreen
import com.bytedace.doubaoapp.ui.chat.ChatScreen
import com.bytedace.doubaoapp.ui.settings.LocalLlmBenchmarkScreen
import com.bytedace.doubaoapp.ui.settings.SettingsScreen

@Composable
fun DoubaoAppContent(app: DoubaoApp) {
    MainScreen(
        repository = app.chatRepository,
        chatScreen = { sessionId, onBack, onSettings ->
            ChatScreen(
                sessionId = sessionId,
                onNavigateToSessionList = onBack,
                onNavigateToSettings = onSettings,
            )
        },
        settingsScreen = { onBack, onBenchmark ->
            SettingsScreen(
                onBack = onBack,
                onNavigateToBenchmark = onBenchmark,
            )
        },
        benchmarkScreen = { onBack ->
            LocalLlmBenchmarkScreen(onBack = onBack)
        },
    )
}
