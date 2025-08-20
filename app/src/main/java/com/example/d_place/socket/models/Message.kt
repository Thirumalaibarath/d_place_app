package com.example.d_place.socket.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


/** Simple pixel message used for sending user actions. */
@Serializable
data class Message(
    val type: String = "color_pixel",
    val x: Int,
    val y: Int,
    val color: String
)

/** Pixel event we may receive from server (includes optional session & user). */
@Serializable
data class GamePixelEvent(
    val type: String = "color_pixel",
    @SerialName("session_id") val sessionId: String? = null,
    val user: String? = null,
    val x: Int,
    val y: Int,
    val color: String
)

/** Full session state snapshot sent by server. */
@Serializable
data class GameStateMessage(
    val type: String, // "state"
    @SerialName("session_id") val sessionId: String,
    val meta: Meta,
    val users: List<String> = emptyList(),
    val pixels: Map<String, String> = emptyMap() // "x,y" -> "#RRGGBB"
) {
    @Serializable
    data class Meta(
        @SerialName("game_started") val gameStarted: Boolean,
        val width: Int,
        val height: Int,
        @SerialName("created_at") val createdAt: Long
    )
}


