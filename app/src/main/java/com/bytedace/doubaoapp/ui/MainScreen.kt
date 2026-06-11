package com.bytedace.doubaoapp.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bytedace.doubaoapp.data.context.ContextPolicy
import com.bytedace.doubaoapp.data.repository.ChatRepository
import com.bytedace.doubaoapp.ui.chat.ChatScreen
import com.bytedace.doubaoapp.ui.session.SessionListScreen
import com.bytedace.doubaoapp.ui.settings.LocalLlmBenchmarkScreen
import com.bytedace.doubaoapp.ui.settings.SettingsScreen

@Composable
fun MainScreen(
    contextPolicy: ContextPolicy,
    repository: ChatRepository,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "sessionList",
        modifier = modifier.fillMaxSize(),
    ) {
        composable("sessionList") {
            SessionListScreen(
                repository = repository,
                onNavigateToChat = { sessionId ->
                    navController.navigate("chat/$sessionId")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
            )
        }

        composable(
            route = "chat/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) {
            val sessionId = it.arguments?.getString("sessionId") ?: ""
            ChatScreen(
                contextPolicy = contextPolicy,
                repository = repository,
                sessionId = sessionId,
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToSessionList = { navController.popBackStack() },
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToBenchmark = { navController.navigate("localLlmBenchmark") },
            )
        }

        composable("localLlmBenchmark") {
            LocalLlmBenchmarkScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}