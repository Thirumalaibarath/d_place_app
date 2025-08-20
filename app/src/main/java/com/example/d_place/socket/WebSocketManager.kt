package com.example.d_place

import android.util.Log
import com.example.d_place.socket.models.Message
import com.example.d_place.socket.utils.GameJson
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

/**
 * WebSocketManager
 *
 * - Connect lobby (/ws?user=...)
 * - Connect game (/game_ws?session_id=...&user=...)
 * - Sends heartbeat JSON {"type":"ping"} periodically to keep presence TTL alive on server.
 * - Pushes inbound text messages to [incomingMessages] Channel.
 *
 * hostPort = "10.0.2.2:8080"  (NO scheme)
 */
class WebSocketManager(
    private val hostPort: String,
    private val userId: String,
    parentScope: CoroutineScope? = null,
    private val heartbeatIntervalMs: Long = 5_000L // < presenceTTL/2
) {
    companion object {
        private const val TAG = "WebSocketMgr"
    }

    private val _incoming = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val incomingMessages = _incoming.asSharedFlow()

    // If caller doesn't give a scope, make one we own.
    private val scope = parentScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client: OkHttpClient = OkHttpClient.Builder()
        // OkHttp at pingInterval sends WS-level ping frames; good for network,
        // but we ALSO send our JSON heartbeat for Redis TTL refresh.
        .build()

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null

    /**
     * Channel of inbound messages (Text). Collect in Compose or ViewModel.
     */

    /**
     * Connect to lobby presence WS.
     */
    fun connectLobby() {
        val url = "ws://$hostPort/ws?user=$userId"
        Log.d(TAG, "connectLobby → $url")
        connect(url, "lobby", sendHeartbeat = true)
    }

    /**
     * Connect to a game session WS.
     * NOTE: your backend also marks presence here (optional),
     * but we keep heartbeat active anyway unless you disable.
     */
    fun connectGame(sessionId: String) {
        val url = "ws://$hostPort/game_ws?session_id=$sessionId&user=$userId"
        Log.d(TAG, "connectGame($sessionId) → $url")
        connect(url, "game:$sessionId", sendHeartbeat = true)
    }

    /**
     * Send raw text over active WS.
     */
    fun sendMessage(text: String) {
        val ok = webSocket?.send(text) ?: false
        if (!ok) Log.w(TAG, "sendMessage failed (no socket?)")
    }

    /**
     * Convenience to send a pixel update to server.
     */
    fun sendPixel(x: Int, y: Int, color: String) {
        val msg = Message(x = x, y = y, color = color)
        val json = GameJson.encodeToString(msg)
        sendMessage(json)
    }

    /**
     * Explicitly send a ping payload (in case you want manual refresh somewhere).
     */
    fun sendPing() {
        sendMessage("""{"type":"ping"}""")
    }

    /**
     * Close and cancel heartbeat.
     */
    fun disconnect() {
        Log.d(TAG, "disconnect()")
        heartbeatJob?.cancel()
        heartbeatJob = null
        webSocket?.close(1000, "client_closing")
        webSocket = null
    }

    /**
     * Internal connect + listener + heartbeat startup.
     */
    private fun connect(url: String, socketLabel: String, sendHeartbeat: Boolean) {
        // Close any prior connection
        disconnect()

        val request = Request.Builder()
            .url(url)
            .build()

        val wsListener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "[$socketLabel] onOpen (code=${response.code})")
            }

            override fun onMessage(ws: WebSocket, text: String) {
                Log.d(TAG, "[$socketLabel] onMessage: $text")
                scope.launch { _incoming.emit(text) }
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                val text = bytes.utf8()
                Log.d(TAG, "[$socketLabel] onMessage(bytes): $text")
                scope.launch {  _incoming.emit(text) }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "[$socketLabel] onClosing: $code / $reason")
                ws.close(code, reason)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "[$socketLabel] onClosed: $code / $reason")
                stopHeartbeat()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "[$socketLabel] onFailure: ${t.message}", t)
                stopHeartbeat()
                // push an error message downstream (optional)
                scope.launch {
                    _incoming.emit("""{"type":"error","where":"$socketLabel","err":"${t.message}"}""")
                }
            }
        }

        webSocket = client.newWebSocket(request, wsListener)
    }

    private fun startHeartbeat(socketLabel: String) {
        stopHeartbeat() // ensure only one
        heartbeatJob = scope.launch {
            Log.d(TAG, "[$socketLabel] heartbeat START (interval=${heartbeatIntervalMs}ms)")
            // send immediate ping so presence TTL begins right away
            sendPing()
            while (isActive) {
                delay(heartbeatIntervalMs)
                Log.d("SOCKET", "[$socketLabel] heartbeat SEND")
                sendPing()
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
}
