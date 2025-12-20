package com.example.artchat

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.artchat.databinding.ActivityChatBinding
import com.example.artchat.model.ChatMessage
import com.example.artchat.adapter.ChatMessageAdapter
import com.example.artchat.utils.PreferencesManager
import com.example.artchat.websocket.WebSocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatActivity : AppCompatActivity(), WebSocketManager.WebSocketListener {

    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: ChatMessageAdapter
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var preferences: PreferencesManager
    private lateinit var webSocketManager: WebSocketManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = PreferencesManager(this)
        setupRecyclerView()
        setupClickListeners()
        setupWebSocket()
        loadInitialMessages()
    }

    override fun onResume() {
        super.onResume()
        if (!::webSocketManager.isInitialized || !webSocketManager.isConnected()) {
            setupWebSocket()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::webSocketManager.isInitialized) {
            webSocketManager.removeListener(this)
            webSocketManager.disconnect()
        }
    }

    private fun setupRecyclerView() {
        adapter = ChatMessageAdapter(messages, preferences.getUserId())
        binding.recyclerViewMessages.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewMessages.adapter = adapter
    }

    private fun setupWebSocket() {
        val userId = preferences.getUserId()
        val token = preferences.getToken()

        webSocketManager = WebSocketManager(userId, token)
        webSocketManager.addListener(this)
        webSocketManager.connect()
    }

    private fun loadInitialMessages() {
        // Можно загрузить начальные сообщения с сервера здесь
        // Например, через Retrofit API
        binding.progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Пример загрузки сообщений
                // val response = RetrofitClient.apiService.getGlobalChatMessages("Bearer $token")
                // if (response.isSuccessful) {
                //     val loadedMessages = response.body()?.messages ?: emptyList()
                //     withContext(Dispatchers.Main) {
                //         messages.addAll(loadedMessages)
                //         adapter.notifyDataSetChanged()
                //         updateEmptyState()
                //     }
                // }

                // Для примепа просто делаем задержку
                kotlinx.coroutines.delay(500)

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    updateEmptyState()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@ChatActivity, "Ошибка загрузки сообщений", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupClickListeners() {
        // Send message
        binding.btnSend.setOnClickListener {
            val messageText = binding.etMessage.text?.toString()?.trim()
            if (!messageText.isNullOrEmpty()) {
                sendMessage(messageText)
                binding.etMessage.text?.clear()
            }
        }

        // Back button
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Attach button
        binding.btnAttach.setOnClickListener {
            showAttachOptions()
        }

        // Online users button
        binding.btnOnlineUsers.setOnClickListener {
            showOnlineUsers()
        }

        // Private chat button
        binding.btnPrivateChat.setOnClickListener {
            Toast.makeText(this, "Приватные чаты в разработке", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendMessage(text: String) {
        if (!::webSocketManager.isInitialized || !webSocketManager.isConnected()) {
            Toast.makeText(this, "Нет подключения к серверу", Toast.LENGTH_SHORT).show()
            return
        }

        webSocketManager.sendMessage(text)

        // Добавляем сообщение локально для мгновенного отображения
        val message = ChatMessage(
            id = null,
            room = "global",
            sender_id = preferences.getUserId(),
            sender_name = preferences.getDisplayName() ?: "Аноним",
            message_type = "text",
            content = text,
            drawing_url = null,
            image_url = null,
            timestamp = System.currentTimeMillis().toString()
        )

        messages.add(message)
        adapter.notifyItemInserted(messages.size - 1)
        binding.recyclerViewMessages.smoothScrollToPosition(messages.size - 1)
        updateEmptyState()
    }

    private fun showAttachOptions() {
        Toast.makeText(this, "Прикрепление файлов в разработке", Toast.LENGTH_SHORT).show()
    }

    private fun showOnlineUsers() {
        Toast.makeText(this, "Список онлайн пользователей в разработке", Toast.LENGTH_SHORT).show()
    }

    private fun updateEmptyState() {
        binding.tvEmpty.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
    }

    // WebSocket Listeners
    override fun onMessageReceived(message: ChatMessage) {
        runOnUiThread {
            // Проверяем, нет ли уже такого сообщения
            if (messages.none { it.id == message.id }) {
                messages.add(message)
                adapter.notifyItemInserted(messages.size - 1)
                binding.recyclerViewMessages.smoothScrollToPosition(messages.size - 1)
                updateEmptyState()
            }
        }
    }

    override fun onUserJoined(userId: Int, username: String) {
        runOnUiThread {
            Toast.makeText(this, "$username присоединился к чату", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onUserLeft(userId: Int, username: String) {
        runOnUiThread {
            Toast.makeText(this, "$username покинул чат", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onConnected() {
        runOnUiThread {
            Toast.makeText(this, "Подключено к чату", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDisconnected() {
        runOnUiThread {
            Toast.makeText(this, "Отключено от чата", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onError(error: String) {
        runOnUiThread {
            Toast.makeText(this, "Ошибка: $error", Toast.LENGTH_SHORT).show()
        }
    }
}