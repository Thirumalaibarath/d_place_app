package com.example.d_place.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.d_place.WebSocketManager
import com.example.d_place.socket.models.Message
import com.example.d_place.socket.utils.GameJson
import com.example.d_place.socket.utils.parseGamePixelEvent
import com.example.d_place.socket.utils.parseGameState
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

@Composable
fun GameScreen(
    wsManager: WebSocketManager,
    sessionId: String,
    userId: String
) {
    // ----- palette -----
    val colorList = listOf(
        Color(0xFFF44336), // Red 500
        Color(0xFF4CAF50), // Green 500
        Color(0xFF2196F3), // Blue 500
        Color(0xFFFFC107), // Amber 500
        Color(0xFF9C27B0)  // Purple 500
    )

    // ----- state -----
    val scope = rememberCoroutineScope()

    // latest raw msg (debug)
    var latestRaw  = remember { mutableStateOf<String?>(null) }

    // last pixel event (debug)
    var lastPixel  = remember { mutableStateOf<Message?>(null) }

    // error string
    var errorText  = remember { mutableStateOf<String?>(null) }

    // chosen paint color
    var chosen  = remember { mutableStateOf(Color.Red) }

    // grid dimensions (defaults until server state arrives)
    var gridWidth  = remember { mutableStateOf(5) }
    var gridHeight  = remember { mutableStateOf(9) }

    // pixel grid (Pair(x,y) -> Color)
    val grid = remember { mutableStateMapOf<Pair<Int,Int>, Color>() }

    // helper: get current color for a cell
    fun cellColor(x: Int, y: Int): Color =
        grid[Pair(x,y)] ?: Color.White

    // helper: set cell color
    fun setCellColor(x: Int, y: Int, color: Color) {
        grid[Pair(x,y)] = color
    }

    // connect to game ws when entering / session changes
    LaunchedEffect(sessionId) {
        try {
            wsManager.disconnect()              // close lobby
            wsManager.connectGame(sessionId)    // open game_ws
        } catch (t: Throwable) {
            errorText.value = "WS connect failed: ${t.message}"
        }
        Log.d("HEY","i'm open")
        // collect server messages
        wsManager.incomingMessages.collect{ raw ->
            latestRaw.value = raw
            Log.d("HEY",raw)
            // full state?
            val stateMsg = parseGameState(raw)
            Log.d("STATE",stateMsg.toString())
            if (stateMsg != null && stateMsg.type == "state") {
                Log.d("STATE","hey its loaded !")
                // update dims
                gridWidth.value = stateMsg.meta.width
                gridHeight.value = stateMsg.meta.height

                // apply all pixels: map "x,y" -> "#HEX"
                stateMsg.pixels.forEach { (coord, hex) ->
                    val (xStr, yStr) = coord.split(',').let {
                        it.getOrElse(0) { "0" } to it.getOrElse(1) { "0" }
                    }
                    val x = xStr.toIntOrNull() ?: return@forEach
                    val y = yStr.toIntOrNull() ?: return@forEach
                    setCellColor(x, y, hex.toComposeColor())
                }

                // done handling state; go wait for next incoming message
                return@collect
            }

            // single pixel event?
            val pixMsg = parseGamePixelEvent(raw)
            if (pixMsg != null && pixMsg.type == "color_pixel") {
                lastPixel = mutableStateOf(
                    Message(
                        type = "color_pixel",
                        x = pixMsg.x,
                        y = pixMsg.y,
                        color = pixMsg.color
                    )
                )
                setCellColor(pixMsg.x, pixMsg.y, pixMsg.color.toComposeColor())
                return@collect
            }

        }
    }

    // ----- UI -----
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.systemBars.asPaddingValues()),
        contentAlignment = Alignment.TopCenter
    ) {
        // grid center
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // y = row
                repeat(gridHeight.value) { y ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // x = column
                        repeat(gridWidth.value) { x ->
                            val cellCol = cellColor(x, y)
                            Box(
                                modifier = Modifier
                                    .size(25.dp)
                                    .background(color = cellCol)
                                    .border(1.dp, Color.Black)
                                    .clickable {
                                        // optimistic local update
                                        setCellColor(x, y, chosen.value)

                                        // send to server
                                        wsManager.sendPixel(x, y, chosen.value.toHexCode())

                                        // debug record
                                        lastPixel = mutableStateOf(
                                            Message(
                                                type = "color_pixel",
                                                x = x,
                                                y = y,
                                                color = chosen.value.toHexCode()
                                            )
                                        )
                                    }
                            )
                        }
                    }
                }
            }
        }

        // chosen color display
        Text(
            text = "Chosen: ${chosen.value.toHexCode()}",
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 30.dp)
        )

        // palette bottom row
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 30.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
        ) {
            colorList.forEach { c ->
                Box(
                    modifier = Modifier
                        .size(25.dp)
                        .background(color = c)
                        .border(1.dp, Color.Black)
                        .clickable { chosen.value = c }
                )
            }
        }

        // error overlay (optional)
        if (errorText.value != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Text("Error:", color = Color.Red, fontSize = 12.sp)
                Text(errorText.value ?: "", color = Color.Red, fontSize = 12.sp)
            }
        }
    }
}

// Convert Compose Color -> "#RRGGBB"
fun Color.toHexCode(): String {
    val r = (red * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue * 255).toInt().coerceIn(0, 255)
    return String.format("#%02X%02X%02X", r, g, b)
}

// Convert "#RRGGBB" (or "#AARRGGBB") -> Color
fun String.toComposeColor(default: Color = Color.White): Color {
    val s = trim()
    if (!s.startsWith("#")) return default
    return try {
        // Parse hex ignoring leading '#'
        val hex = s.drop(1)
        when (hex.length) {
            6 -> {
                val v = hex.toLong(16)
                val r = ((v shr 16) and 0xFF).toInt()
                val g = ((v shr 8) and 0xFF).toInt()
                val b = (v and 0xFF).toInt()
                Color(r, g, b)
            }
            8 -> { // ARGB
                val v = hex.toLong(16)
                val a = ((v shr 24) and 0xFF).toInt()
                val r = ((v shr 16) and 0xFF).toInt()
                val g = ((v shr 8) and 0xFF).toInt()
                val b = (v and 0xFF).toInt()
                Color(red = r / 255f, green = g / 255f, blue = b / 255f, alpha = a / 255f)
            }
            else -> default
        }
    } catch (_: Exception) {
        default
    }
}



