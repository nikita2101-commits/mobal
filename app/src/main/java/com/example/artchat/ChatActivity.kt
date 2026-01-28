package com.example.artchat

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.artchat.database.ChatMessageEntity
import com.example.artchat.databinding.ActivityChatBinding
import com.example.artchat.model.ChatMessage
import com.example.artchat.repository.ChatRepository
import com.example.artchat.utils.PreferencesManager
import com.example.artchat.websocket.WebSocketManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ChatActivity : AppCompatActivity(), WebSocketManager.WebSocketListener {

    private lateinit var binding: ActivityChatBinding
    private lateinit var messageAdapter: MessageAdapter
    private val messageList = mutableListOf<ChatMessage>()
    private lateinit var preferences: PreferencesManager
    private lateinit var webSocketManager: WebSocketManager
    private lateinit var chatRepository: ChatRepository

    // Для предотвращения повторной отправки
    private val sentMessageIds = mutableSetOf<String>()
    private var lastSentMessage: String = ""
    private var lastSendTime: Long = 0
    private val MIN_SEND_INTERVAL = 1000L

    // Текущая комната чата
    private val currentRoom = "global"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = PreferencesManager(this)
        chatRepository = ChatRepository.getInstance(this)

        val myUserId = preferences.getUserId()

        // Инициализация WebSocket
        webSocketManager = WebSocketManager(myUserId, preferences.getToken())
        webSocketManager.addListener(this)

        // Настройка RecyclerView
        messageAdapter = MessageAdapter(messageList, myUserId)
        setupRecyclerView()

        // Настройка слушателей
        setupListeners()

        // Настройка ActionBar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Чат"

        // Загрузка сохраненных сообщений из базы данных
        loadSavedMessages()

        // Подключение к WebSocket
        connectToWebSocket()

        // Показать клавиатуру
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        // Наблюдаем за обновлениями сообщений
        observeMessages()
    }

    override fun onResume() {
        super.onResume()
        binding.etMessage.apply {
            requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocketManager.removeListener(this)
        webSocketManager.disconnect()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun setupRecyclerView() {
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
            adapter = messageAdapter
        }

        messageAdapter.registerAdapterDataObserver(object : androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                binding.rvMessages.scrollToPosition(messageAdapter.itemCount - 1)
            }
        })
    }

    private fun setupListeners() {
        binding.btnSend.setOnClickListener {
            sendMessage()
        }

        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        binding.etMessage.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                if (!event.isShiftPressed) {
                    sendMessage()
                    return@setOnKeyListener true
                }
            }
            false
        }
    }

    private fun sendMessage() {
        val messageText = binding.etMessage.text.toString().trim()

        if (messageText.isEmpty()) {
            return
        }

        // Проверка на слишком частую отправку
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSendTime < MIN_SEND_INTERVAL) {
            Toast.makeText(this, "Отправляйте сообщения немного медленнее", Toast.LENGTH_SHORT).show()
            return
        }

        // Проверка на одинаковое сообщение
        if (messageText == lastSentMessage) {
            Toast.makeText(this, "Вы уже отправили это сообщение", Toast.LENGTH_SHORT).show()
            return
        }

        lastSentMessage = messageText
        lastSendTime = currentTime

        // Создаем временное сообщение
        val tempId = "temp_${currentTime}_${preferences.getUserId()}_${messageText.hashCode()}"
        val tempMessage = ChatMessage(
            id = null,
            room = currentRoom,
            sender_id = preferences.getUserId(),
            sender_name = preferences.getDisplayName(),
            message_type = "text",
            content = messageText,
            timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date(currentTime)),
            is_read = false,
            temp_id = tempId
        )

        // Сохраняем в базу данных
        lifecycleScope.launch(Dispatchers.IO) {
            chatRepository.saveMessage(tempMessage)
        }

        // Добавляем в список
        messageAdapter.addMessage(tempMessage)
        sentMessageIds.add(tempId)

        // Отправляем через WebSocket
        val success = webSocketManager.sendMessage(messageText, currentRoom)

        if (!success) {
            Toast.makeText(this, "Ошибка отправки, сообщение сохранено локально", Toast.LENGTH_SHORT).show()
        }

        // Очищаем поле ввода
        binding.etMessage.setText("")
        binding.etMessage.requestFocus()
    }

    private fun loadSavedMessages() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Загружаем последние 100 сообщений из базы данных
                val savedMessages = chatRepository.getRecentMessages(currentRoom, 100)

                withContext(Dispatchers.Main) {
                    messageList.clear()
                    messageList.addAll(savedMessages)
                    messageAdapter.notifyDataSetChanged()

                    // Прокручиваем к последнему сообщению
                    if (messageList.isNotEmpty()) {
                        binding.rvMessages.scrollToPosition(messageList.size - 1)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChatActivity, "Ошибка загрузки сообщений", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun observeMessages() {
        lifecycleScope.launch {
            chatRepository.getMessagesByRoom(currentRoom).collect { messages ->
                // Обновляем список сообщений
                messageList.clear()
                messageList.addAll(messages)
                messageAdapter.notifyDataSetChanged()

                // Прокручиваем к последнему сообщению
                if (messageList.isNotEmpty()) {
                    binding.rvMessages.scrollToPosition(messageList.size - 1)
                }
            }
        }
    }

    private fun connectToWebSocket() {
        webSocketManager.connect()

        Handler(Looper.getMainLooper()).postDelayed({
            if (!webSocketManager.isConnected()) {
                Toast.makeText(this, "Нет подключения к серверу", Toast.LENGTH_SHORT).show()
            }
        }, 3000)

        // Пытаемся отправить неотправленные сообщения
        lifecycleScope.launch(Dispatchers.IO) {
            val unsentMessages = chatRepository.getUnsentMessages()
            unsentMessages.forEach { message ->
                message.content?.let { content ->
                    webSocketManager.sendMessage(content, currentRoom)
                }
            }
        }
    }

    // Реализация WebSocketListener
    override fun onMessageReceived(message: ChatMessage) {
        runOnUiThread {
            // Проверяем, не получали ли мы уже это сообщение
            val messageKey = "${message.id}_${message.content.hashCode()}"
            if (!sentMessageIds.contains(messageKey)) {
                // Сохраняем в базу данных
                lifecycleScope.launch(Dispatchers.IO) {
                    chatRepository.saveMessage(message)
                }
                sentMessageIds.add(messageKey)
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
            Toast.makeText(this, "$username вышел из чата", Toast.LENGTH_SHORT).show()
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

    override fun onMessageConfirmed(tempId: String) {
        runOnUiThread {
            // Находим временное сообщение в базе данных и обновляем его
            lifecycleScope.launch(Dispatchers.IO) {
                // Генерируем серверный ID (в реальном приложении это должен быть ID от сервера)
                val serverId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
                chatRepository.markMessageAsSent(tempId, serverId)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChatActivity, "Сообщение доставлено", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}