package com.example.d_place.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.d_place.WebSocketManager
import com.example.d_place.api.ApiClient
import com.example.d_place.screens.GameScreen
import com.example.d_place.screens.LobbyScreen

@Composable
fun Host(navController: NavHostController, userId: String) {
    val hostPort = "192.168.1.35:8080"
    val wsMgr = remember { WebSocketManager(hostPort, userId) }
    val api = remember { ApiClient("http://$hostPort") }
    NavHost(
        navController = navController,
        startDestination = "lobby"
    ) {
        composable("lobby") {
            LobbyScreen(
                webSocketManager = wsMgr,
                apiClient = api,
                userId = userId,
                onGameStart = { sessionId ->
                    navController.navigate("game/$sessionId")
                }
            )
        }

        composable("game/{sessionId}") { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            GameScreen(wsManager = wsMgr, sessionId = sessionId, userId = userId)
        }
    }
}
