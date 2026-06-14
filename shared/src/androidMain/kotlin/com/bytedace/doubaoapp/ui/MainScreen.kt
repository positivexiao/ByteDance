package com.bytedace.doubaoapp.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bytedace.doubaoapp.data.repository.ChatRepository
import com.bytedace.doubaoapp.ui.session.SessionListScreen

sealed interface AppRoute {
    data object SessionList : AppRoute
    data class Chat(val sessionId: String) : AppRoute
    data object Settings : AppRoute
    data object Benchmark : AppRoute
}

private val AppRouteSaver = Saver<AppRoute, String>(
    save = { route ->
        when (route) {
            AppRoute.SessionList -> "session_list"
            is AppRoute.Chat -> "chat:${route.sessionId}"
            AppRoute.Settings -> "settings"
            AppRoute.Benchmark -> "benchmark"
        }
    },
    restore = { saved ->
        when {
            saved == "session_list" -> AppRoute.SessionList
            saved.startsWith("chat:") -> AppRoute.Chat(saved.removePrefix("chat:"))
            saved == "settings" -> AppRoute.Settings
            saved == "benchmark" -> AppRoute.Benchmark
            else -> AppRoute.SessionList
        }
    },
)

@Composable
fun MainScreen(
    repository: ChatRepository,
    chatScreen: @Composable (sessionId: String, onBack: () -> Unit, onSettings: () -> Unit) -> Unit,
    settingsScreen: @Composable (onBack: () -> Unit, onBenchmark: () -> Unit) -> Unit,
    benchmarkScreen: @Composable (onBack: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var route by rememberSaveable(stateSaver = AppRouteSaver) {
        mutableStateOf(AppRoute.SessionList)
    }

    when (val current = route) {
        AppRoute.SessionList -> {
            SessionListScreen(
                repository = repository,
                onNavigateToChat = { sessionId -> route = AppRoute.Chat(sessionId) },
                onNavigateToSettings = { route = AppRoute.Settings },
                modifier = modifier.fillMaxSize(),
            )
        }
        is AppRoute.Chat -> {
            chatScreen(
                current.sessionId,
                { route = AppRoute.SessionList },
                { route = AppRoute.Settings },
            )
        }
        AppRoute.Settings -> {
            settingsScreen(
                { route = AppRoute.SessionList },
                { route = AppRoute.Benchmark },
            )
        }
        AppRoute.Benchmark -> {
            benchmarkScreen { route = AppRoute.Settings }
        }
    }
}
