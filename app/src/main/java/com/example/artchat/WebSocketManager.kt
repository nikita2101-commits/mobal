package com.example.artchat.websocket

import android.util.Log
import com.example.artchat.model.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebSocketManager(
    private val userId: Int,
    private val token: String? = null
) {

    private lateinit var webSocket: WebSocket
    private var isConnected = false
    private val listeners = mutableListOf<WebSocketListener>()

    interface WebSocketListener {
        fun onMessageReceived(message: ChatMessage)
        fun onUserJoined(userId: Int, username: String)
        fun onUserLeft(userId: Int, username: String)
        fun onConnected()
        fun onDisconnected()
        fun onError(error: String)
    }

    fun addListener(listener: WebSocketListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: WebSocketListener) {
        listeners.remove(listener)
    }

    fun connect() {
        // ПРАВИЛЬНЫЙ URL для Flask-SocketIO
        // Flask-SocketIO использует стандартный путь /socket.io/
        val wsUrl = "ws://10.201.240.101:5000/socket.io/?EIO=4&transport=websocket"

        // Альтернативный URL без параметров
        // val wsUrl = "ws://10.201.240.101:5000/socket.io/"

        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS) // Для keep-alive
            .build()

        val request = Request.Builder()
            .url(wsUrl)
            .apply {
                if (token != null) {
                    addHeader("Authorization", "Bearer $token")
                }
                // Flask-SocketIO ожидает определенные заголовки
                addHeader("Origin", "http://10.201.240.101:5000")
            }
            .build()

        webSocket = client.newWebSocket(request, object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "Connected to Flask-SocketIO")
                isConnected = true

                // Flask-SocketIO отправляет "40" при подключении
                // Мы отвечаем "40" для подтверждения
                webSocket.send("40")

                listeners.forEach { it.onConnected() }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocket", "Received: $text")

                // Flask-SocketIO использует специальный протокол:
                // "0" - открытие соединения
                // "40" - namespace подключен
                // "42" - событие с данными

                when {
                    text.startsWith("0") -> {
                        // Ответ на открытие соединения
                        Log.d("WebSocket", "Socket.IO handshake received")
                    }
                    text.startsWith("40") -> {
                        // Namespace подключен
                        Log.d("WebSocket", "Socket.IO namespace connected")

                        // Теперь можем присоединиться к комнате
                        joinGlobalChat()
                    }
                    text.startsWith("42") -> {
                        // Событие с данными
                        handleSocketIOEvent(text.substring(2))
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "Connection failed: ${t.message}")
                if (response != null) {
                    Log.e("WebSocket", "Response: ${response.code} ${response.message}")
                }
                isConnected = false
                listeners.forEach { it.onError(t.message ?: "Unknown error") }

                // Пробуем переподключиться через 5 секунд
                CoroutineScope(Dispatchers.IO).launch {
                    kotlinx.coroutines.delay(5000)
                    if (!isConnected) {
                        connect()
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "Connection closed: $code - $reason")
                isConnected = false
                listeners.forEach { it.onDisconnected() }
            }
        })
    }

    private fun handleSocketIOEvent(eventData: String) {
        try {
            val jsonArray = JSONObject("{\"data\": $eventData}")
            val dataArray = jsonArray.getJSONArray("data")

            if (dataArray.length() >= 2) {
                val eventName = dataArray.getString(0)
                val eventPayload = dataArray.getJSONObject(1)

                when (eventName) {
                    "new_message" -> {
                        val message = ChatMessage(
                            id = eventPayload.optInt("id"),
                            room = eventPayload.optString("room", "global"),
                            sender_id = eventPayload.optInt("sender_id"),
                            sender_name = eventPayload.optString("sender_name"),
                            message_type = eventPayload.optString("message_type", "text"),
                            content = eventPayload.optString("content", ""),
                            drawing_url = eventPayload.optString("drawing_url"),
                            image_url = eventPayload.optString("image_url"),
                            timestamp = eventPayload.optString("timestamp")
                        )
                        listeners.forEach { it.onMessageReceived(message) }
                    }
                    "user_joined" -> {
                        val joinedUserId = eventPayload.optInt("user_id")
                        listeners.forEach { it.onUserJoined(joinedUserId, "User") }
                    }
                    "user_left" -> {
                        val leftUserId = eventPayload.optInt("user_id")
                        listeners.forEach { it.onUserLeft(leftUserId, "User") }
                    }
                    "connected" -> {
                        Log.d("WebSocket", "Socket.IO connected event")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("WebSocket", "Error parsing Socket.IO event: ${e.message}")
        }
    }

    private fun joinGlobalChat() {
        val joinEvent = JSONObject().apply {
            put("room", "global")
            put("user_id", userId)
        }

        // Формат Socket.IO: "42["event_name", {data}]"
        val socketIOMessage = "42[\"join_room\", $joinEvent]"
        webSocket.send(socketIOMessage)
        Log.d("WebSocket", "Sent join room: $socketIOMessage")
    }

    fun sendMessage(content: String, room: String = "global") {
        if (!isConnected) {
            Log.e("WebSocket", "Not connected, cannot send message")
            return
        }

        val messageData = JSONObject().apply {
            put("room", room)
            put("content", content)
            put("message_type", "text")
        }

        // Формат Socket.IO
        val socketIOMessage = "42[\"send_message\", $messageData]"
        webSocket.send(socketIOMessage)
        Log.d("WebSocket", "Sent message: $socketIOMessage")
    }

    fun disconnect() {
        if (isConnected) {
            webSocket.close(1000, "User disconnected")
            isConnected = false
        }
    }

    fun isConnected(): Boolean = isConnected
}