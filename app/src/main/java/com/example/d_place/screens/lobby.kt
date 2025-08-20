package com.example.d_place.screens

import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.d_place.WebSocketManager
import com.example.d_place.api.ApiClient
import kotlinx.coroutines.launch

@Composable
fun LobbyScreen(
    webSocketManager: WebSocketManager,
    apiClient: ApiClient, // For REST endpoints
    userId: String,
    onGameStart: (String) -> Unit
) {
    Log.d("LobbyScreen", "userId: $userId")
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf(listOf<String>()) }
    var sessionName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    // Connect to lobby WebSocket (presence)
    LaunchedEffect(Unit) {
        Log.d("Launched Called", "userId: $userId")
        webSocketManager.connectLobby()
        webSocketManager.incomingMessages.collect { msg ->
            Log.d("LobbyScreen", "Received message: $msg")
            messages = messages + msg
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Lobby", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        messages.forEach { Text("Message: $it") }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = sessionName,
            onValueChange = { sessionName = it },
            label = { Text("Session Name") },
            modifier = Modifier.fillMaxWidth()
        )

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color.Red)
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Create new session
            Button(
                onClick = {
                    scope.launch {
                        val result = apiClient.createSession(sessionName, userId)
                        if (result.isSuccess) {
                            onGameStart(sessionName)
                        } else {
                            error = "Failed to create session"
                        }
                    }
                }
            ) {
                Text("Start Game")
            }

            // Join existing session
            Button(
                onClick = {
                    scope.launch {
                        val result = apiClient.joinSession(sessionName, userId)
                        if (result.isSuccess) {
                            onGameStart(sessionName)
                        } else {
                            error = "Failed to join session"
                        }
                    }
                }
            ) {
                Text("Join Game")
            }
        }
    }
}

