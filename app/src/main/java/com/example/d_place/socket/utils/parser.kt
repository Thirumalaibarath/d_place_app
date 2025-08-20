package com.example.d_place.socket.utils

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.example.d_place.socket.models.GamePixelEvent
import com.example.d_place.socket.models.GameStateMessage
import com.example.d_place.socket.models.Message
import kotlinx.serialization.json.Json

val GameJson = Json {
    ignoreUnknownKeys = true
    isLenient = true   // tolerate minor formatting issues (extra spaces, etc.)
    encodeDefaults = true
}

fun parseColorPixelMessage(raw: String): Message? =
    runCatching { GameJson.decodeFromString<Message>(raw) }.getOrNull()

fun parseGamePixelEvent(raw: String): GamePixelEvent? =
    runCatching { GameJson.decodeFromString<GamePixelEvent>(raw) }.getOrNull()

fun parseGameState(raw: String): GameStateMessage? =
    runCatching { GameJson.decodeFromString<GameStateMessage>(raw) }.getOrNull()

