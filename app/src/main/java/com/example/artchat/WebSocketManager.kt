package com.example.artchat.websocket

import android.util.Log
import com.example.artchat.model.ChatMessage
import com.example.artchat.utils.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class WebSocketManager(
    private val userId: Int,
    private val token: String? = null
) {

    private lateinit var webSocket: WebSocket
    private var isConnected = false
    private val listeners = mutableListOf<WebSocketListener>()
    private var client: OkHttpClient? = null

    companion object {
        private const val TAG = "WebSocketManager"
    }

    // Защита от дублирования сообщений
    private val sentMessages = ConcurrentHashMap<String, Long>() // messageKey -> timestamp
    private val receivedMessages = ConcurrentHashMap<String, Long>() // messageId -> timestamp
    private val isSending = AtomicBoolean(false)

    // Настройки для предотвращения дублирования
    private val MIN_SEND_INTERVAL = 1000L // 1 секунда между сообщениями
    private val DUPLICATE_CHECK_INTERVAL = 5000L // 5 секунд для проверки дубликатов
    private val MESSAGE_TTL = 60000L // 1 минута жизни в кэше

    // Для отслеживания отправленных временных сообщений
    private val pendingMessages = ConcurrentHashMap<String, String>() // tempId -> content

    interface WebSocketListener {
        fun onMessageReceived(message: ChatMessage)
        fun onUserJoined(userId: Int, username: String)
        fun onUserLeft(userId: Int, username: String)
        fun onConnected()
        fun onDisconnected()
        fun onError(error: String)
        fun onMessageConfirmed(tempId: String) // Новый метод для подтверждения отправки
    }

    fun addListener(listener: WebSocketListener) {
        Log.d(TAG, "Добавление слушателя, всего: ${listeners.size + 1}")
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: WebSocketListener) {
        listeners.remove(listener)
        Log.d(TAG, "Удаление слушателя, осталось: ${listeners.size}")
    }

    fun connect() {
        try {
            Log.d(TAG, "=== CONNECT ===")
            Log.d(TAG, "User ID: $userId")
            Log.d(TAG, "Token: ${token?.take(10)}...")

            // Используем getWebSocketUrl() из Config
            val url = "${Config.getWebSocketUrl()}/socket.io/?EIO=4&transport=websocket"
            Log.d(TAG, "URL: $url")

            client = OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(url)
                .apply {
                    if (!token.isNullOrBlank()) {
                        addHeader("Authorization", "Bearer $token")
                        Log.d(TAG, "Добавлен заголовок Authorization")
                    }
                }
                .build()

            webSocket = client!!.newWebSocket(request, object : okhttp3.WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "✅ WebSocket connected successfully")
                    isConnected = true

                    // Очищаем кэш старых сообщений
                    cleanupOldMessages()

                    CoroutineScope(Dispatchers.Main).launch {
                        listeners.forEach { it.onConnected() }
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "📨 Received: ${text.take(100)}...")

                    try {
                        when {
                            text.startsWith("0") -> {
                                Log.d(TAG, "Handshake получен")
                                // Socket.IO handshake - получение SID
                                handleHandshake(text)
                            }
                            text.startsWith("40") -> {
                                Log.d(TAG, "✅ Namespace connected")
                                // Namespace connected
                                // После подключения к namespace присоединяемся к чату
                                joinGlobalChat()
                            }
                            text.startsWith("42") -> {
                                Log.d(TAG, "Обработка события")
                                // Обработка событий
                                handleSocketIOEvent(text.substring(2))
                            }
                            text == "2" -> {
                                Log.d(TAG, "🏓 Ping received, sending pong")
                                // Ping - отвечаем pong
                                webSocket.send("3")
                            }
                            text.startsWith("3") -> {
                                Log.d(TAG, "🏓 Pong received")
                                // Pong - игнорируем
                            }
                            else -> {
                                Log.d(TAG, "📝 Other message: ${text.take(50)}...")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error handling message: ${e.message}")
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "❌ Connection failed: ${t.message}")
                    isConnected = false

                    // Сбрасываем флаг отправки
                    isSending.set(false)

                    CoroutineScope(Dispatchers.Main).launch {
                        listeners.forEach { it.onError(t.message ?: "Connection failed") }
                    }

                    // Try to reconnect after 3 seconds
                    CoroutineScope(Dispatchers.IO).launch {
                        kotlinx.coroutines.delay(3000)
                        if (!isConnected) {
                            Log.d(TAG, "🔄 Attempting to reconnect...")
                            connect()
                        }
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "🔌 Connection closed: $code - $reason")
                    isConnected = false

                    // Сбрасываем флаг отправки
                    isSending.set(false)

                    // Очищаем кэш
                    sentMessages.clear()
                    receivedMessages.clear()
                    pendingMessages.clear()

                    CoroutineScope(Dispatchers.Main).launch {
                        listeners.forEach { it.onDisconnected() }
                    }
                }
            })

            Log.d(TAG, "WebSocket создан и запущен")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in connect: ${e.message}")
            CoroutineScope(Dispatchers.Main).launch {
                listeners.forEach { it.onError("Connection error: ${e.message}") }
            }
        }
    }

    private fun handleHandshake(handshakeText: String) {
        try {
            Log.d(TAG, "Обработка handshake")
            // Пример handshake: "0{"sid":"Lr5em7G8lbVNBHPtAAAC","upgrades":[],"pingInterval":25000,"pingTimeout":5000}"
            val jsonString = handshakeText.substring(1)
            val jsonObject = JSONObject(jsonString)
            val sid = jsonObject.getString("sid")
            Log.d(TAG, "🤝 Handshake received. SID: $sid")

            // Отправляем подтверждение подключения
            webSocket.send("40")
            Log.d(TAG, "Отправлен ответ на handshake: '40'")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling handshake: ${e.message}")
        }
    }

    private fun handleSocketIOEvent(eventData: String) {
        try {
            Log.d(TAG, "🎯 Parsing event: ${eventData.take(100)}...")

            val jsonArray = JSONArray(eventData)
            if (jsonArray.length() >= 2) {
                val eventName = jsonArray.getString(0)
                val eventPayload = jsonArray.getJSONObject(1)

                Log.d(TAG, "Событие: $eventName")
                Log.d(TAG, "Данные: ${eventPayload.toString().take(100)}...")

                when (eventName) {
                    "new_message" -> {
                        val messageId = eventPayload.optInt("id")
                        val tempId = eventPayload.optString("temp_id", "")
                        val messageKey = if (tempId.isNotEmpty()) "temp_$tempId" else "id_$messageId"

                        Log.d(TAG, "Новое сообщение, ID: $messageId, temp_id: $tempId")

                        // Проверяем, не получали ли мы уже это сообщение
                        if (!isMessageDuplicate(messageKey)) {
                            val message = ChatMessage(
                                id = messageId,
                                room = eventPayload.optString("room", "global"),
                                sender_id = eventPayload.optInt("sender_id"),
                                sender_name = eventPayload.optString("sender_name"),
                                message_type = eventPayload.optString("message_type", "text"),
                                content = eventPayload.optString("content", ""),
                                drawing_url = eventPayload.optString("drawing_url"),
                                image_url = eventPayload.optString("image_url"),
                                timestamp = eventPayload.optString("timestamp")
                            )

                            Log.d(TAG, "💬 New message from ${message.sender_name}: ${message.content?.take(50)}...")

                            // Сохраняем в кэш полученных сообщений
                            receivedMessages[messageKey] = System.currentTimeMillis()

                            // Если это ответ на наше временное сообщение
                            if (tempId.isNotEmpty() && pendingMessages.containsKey(tempId)) {
                                Log.d(TAG, "✅ Message confirmed by server with temp_id: $tempId")
                                pendingMessages.remove(tempId)

                                // Уведомляем слушателей о подтверждении
                                CoroutineScope(Dispatchers.Main).launch {
                                    listeners.forEach { it.onMessageConfirmed(tempId) }
                                }
                            }

                            CoroutineScope(Dispatchers.Main).launch {
                                listeners.forEach { it.onMessageReceived(message) }
                            }
                        } else {
                            Log.d(TAG, "⚠️ Duplicate message ignored: $messageKey")
                        }
                    }
                    "user_joined" -> {
                        val joinedUserId = eventPayload.optInt("user_id")
                        val username = eventPayload.optString("username", "User")

                        Log.d(TAG, "👋 User joined: $username (ID: $joinedUserId)")

                        CoroutineScope(Dispatchers.Main).launch {
                            listeners.forEach { it.onUserJoined(joinedUserId, username) }
                        }
                    }
                    "user_left" -> {
                        val leftUserId = eventPayload.optInt("user_id")
                        val username = eventPayload.optString("username", "User")

                        Log.d(TAG, "👋 User left: $username (ID: $leftUserId)")

                        CoroutineScope(Dispatchers.Main).launch {
                            listeners.forEach { it.onUserLeft(leftUserId, username) }
                        }
                    }
                    "connected" -> {
                        Log.d(TAG, "✅ Socket.IO connected event received")
                    }
                    "joined" -> {
                        Log.d(TAG, "✅ Successfully joined room")
                    }
                    "message_sent" -> {
                        val tempId = eventPayload.optString("temp_id", "")
                        if (tempId.isNotEmpty()) {
                            Log.d(TAG, "✅ Message with temp_id $tempId confirmed by server")
                            // Удаляем из кэша отправленных сообщений
                            sentMessages.remove("temp_$tempId")
                            pendingMessages.remove(tempId)

                            // Уведомляем слушателей
                            CoroutineScope(Dispatchers.Main).launch {
                                listeners.forEach { it.onMessageConfirmed(tempId) }
                            }
                        }
                    }
                    "error" -> {
                        val errorMessage = eventPayload.optString("message", "Unknown error")
                        Log.e(TAG, "❌ Socket.IO error: $errorMessage")

                        // Сбрасываем флаг отправки при ошибке
                        isSending.set(false)

                        CoroutineScope(Dispatchers.Main).launch {
                            listeners.forEach { it.onError(errorMessage) }
                        }
                    }
                    else -> {
                        Log.d(TAG, "📝 Unknown event: $eventName")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parsing Socket.IO event: ${e.message}")
        }
    }

    private fun joinGlobalChat() {
        try {
            Log.d(TAG, "Присоединение к глобальному чату")
            val joinEvent = JSONObject().apply {
                put("room", "global")
                put("user_id", userId)
                if (!token.isNullOrBlank()) {
                    put("token", token)
                }
            }

            val socketIOMessage = "42[\"join\", $joinEvent]"
            webSocket.send(socketIOMessage)
            Log.d(TAG, "📤 Sent join room: $socketIOMessage")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error joining room: ${e.message}")
        }
    }

    fun sendMessage(content: String, room: String = "global"): Boolean {
        Log.d(TAG, "=== SEND MESSAGE ===")
        Log.d(TAG, "Content: '$content'")
        Log.d(TAG, "Room: $room")
        Log.d(TAG, "Is connected: $isConnected")

        if (!isConnected) {
            Log.e(TAG, "❌ Not connected, cannot send message")
            return false
        }

        // Проверяем, не отправляется ли уже сообщение
        if (isSending.get()) {
            Log.w(TAG, "⚠️ Already sending a message, please wait")
            return false
        }

        // Проверяем минимальный интервал между сообщениями
        val now = System.currentTimeMillis()
        if (isTooFrequent(now)) {
            Log.w(TAG, "⚠️ Message sent too quickly, please wait")
            return false
        }

        // Проверяем дублирование контента
        if (isDuplicateContent(content, now)) {
            Log.w(TAG, "⚠️ Duplicate message detected: ${content.take(30)}...")
            return false
        }

        try {
            // Устанавливаем флаг отправки
            isSending.set(true)
            Log.d(TAG, "isSending = true")

            // Генерируем уникальный временный ID
            val tempId = "temp_${now}_${userId}_${content.hashCode()}"

            val messageData = JSONObject().apply {
                put("room", room)
                put("content", content)
                put("message_type", "text")
                put("user_id", userId)
                put("temp_id", tempId) // Уникальный временный ID
                if (!token.isNullOrBlank()) {
                    put("token", token)
                }
            }

            val socketIOMessage = "42[\"send_message\", $messageData]"
            Log.d(TAG, "Отправка сообщения: $socketIOMessage")
            webSocket.send(socketIOMessage)

            // Сохраняем информацию об отправленном сообщении
            val messageKey = generateMessageKey(content, userId)
            sentMessages[messageKey] = now
            sentMessages["temp_$tempId"] = now
            pendingMessages[tempId] = content // Сохраняем для подтверждения

            Log.d(TAG, "📤 Sent message with temp_id: $tempId - ${content.take(30)}...")

            return true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sending message: ${e.message}")
            isSending.set(false)
            Log.d(TAG, "isSending = false (ошибка)")
            return false
        } finally {
            // Сбрасываем флаг через некоторое время
            CoroutineScope(Dispatchers.IO).launch {
                kotlinx.coroutines.delay(500)
                if (isSending.get()) {
                    isSending.set(false)
                    Log.d(TAG, "isSending = false (таймаут)")
                }
            }
        }
    }

    private fun isTooFrequent(now: Long): Boolean {
        // Проверяем, не слишком ли часто отправляются сообщения
        val lastMessageTime = sentMessages.values.maxOrNull() ?: 0
        return now - lastMessageTime < MIN_SEND_INTERVAL
    }

    private fun isDuplicateContent(content: String, now: Long): Boolean {
        val messageKey = generateMessageKey(content, userId)
        val lastSentTime = sentMessages[messageKey] ?: 0

        // Если такое же сообщение отправлялось менее 5 секунд назад - это дубликат
        return now - lastSentTime < DUPLICATE_CHECK_INTERVAL
    }

    private fun generateMessageKey(content: String, userId: Int): String {
        // Создаем уникальный ключ на основе содержания, пользователя и хэша
        return "${userId}_${content.hashCode()}_${content.length}"
    }

    private fun isMessageDuplicate(messageKey: String): Boolean {
        val now = System.currentTimeMillis()
        val timestamp = receivedMessages[messageKey]

        // Если сообщение было получено менее минуты назад - это дубликат
        return timestamp != null && now - timestamp < MESSAGE_TTL
    }

    private fun cleanupOldMessages() {
        val now = System.currentTimeMillis()

        // Удаляем старые сообщения из кэша отправленных
        sentMessages.entries.removeIf { entry ->
            now - entry.value > MESSAGE_TTL
        }

        // Удаляем старые сообщения из кэша полученных
        receivedMessages.entries.removeIf { entry ->
            now - entry.value > MESSAGE_TTL
        }

        // Очищаем старые pending сообщения
        pendingMessages.clear()

        Log.d(TAG, "🧹 Cleaned up old messages cache")
    }

    fun disconnect() {
        Log.d(TAG, "=== DISCONNECT ===")
        if (isConnected) {
            try {
                // Сбрасываем флаг отправки
                isSending.set(false)

                // Отправляем событие выхода перед закрытием
                val leaveEvent = JSONObject().apply {
                    put("user_id", userId)
                    put("room", "global")
                }
                val leaveMessage = "42[\"leave\", $leaveEvent]"
                webSocket.send(leaveMessage)

                // Закрываем соединение
                webSocket.close(1000, "User disconnected")

                // Закрываем клиент
                client?.dispatcher?.executorService?.shutdown()
                client = null

                // Очищаем кэш
                sentMessages.clear()
                receivedMessages.clear()
                pendingMessages.clear()

                Log.d(TAG, "🔌 WebSocket disconnected")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error disconnecting: ${e.message}")
            }
            isConnected = false

            CoroutineScope(Dispatchers.Main).launch {
                listeners.forEach { it.onDisconnected() }
            }
        }
    }

    fun isConnected(): Boolean = isConnected

    fun clearCache() {
        sentMessages.clear()
        receivedMessages.clear()
        pendingMessages.clear()
        Log.d(TAG, "🗑️ Message cache cleared")
    }

    fun getPendingMessages(): Map<String, String> {
        return pendingMessages.toMap()
    }

    fun hasPendingMessage(tempId: String): Boolean {
        return pendingMessages.containsKey(tempId)
    }

    fun removePendingMessage(tempId: String) {
        pendingMessages.remove(tempId)
    }
}