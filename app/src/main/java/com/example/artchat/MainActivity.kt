package com.example.artchat

import android.content.Context
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.artchat.databinding.ActivityChatBinding
import com.example.artchat.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var messageAdapter: MainMessageAdapter // Используем другой адаптер
    private val messageList = mutableListOf<ChatMessage>() // Используем модель ChatMessage
    private val myUserId = 1 // Измените на Int
    private var isConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Настройка RecyclerView
        setupRecyclerView()

        // Настройка слушателей
        setupListeners()

        // Подключение к WebSocket
        connectToWebSocket()

        // Показать клавиатуру при запуске
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    override fun onResume() {
        super.onResume()
        // Установить фокус на поле ввода
        binding.etMessage.apply {
            requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun setupRecyclerView() {
        messageAdapter = MainMessageAdapter(messageList, myUserId)
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
            adapter = messageAdapter
        }

        // Прокрутка к последнему сообщению при добавлении
        messageAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                binding.rvMessages.scrollToPosition(messageAdapter.itemCount - 1)
            }
        })
    }

    private fun setupListeners() {
        // Отправка по кнопке
        binding.btnSend.setOnClickListener {
            sendMessage()
        }

        // Отправка по actionSend с клавиатуры
        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        // Обработка Enter (без Shift - отправка, с Shift - новая строка)
        binding.etMessage.setOnKeyListener { v, keyCode, event ->
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
        if (messageText.isNotEmpty()) {
            if (isConnected) {
                // Создаем временное сообщение
                val tempMessage = ChatMessage(
                    id = null,
                    room = "main_chat", // или другой идентификатор комнаты
                    sender_id = myUserId,
                    sender_name = "Я",
                    message_type = "text",
                    content = messageText,
                    timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()),
                    is_read = false,
                    temp_id = UUID.randomUUID().toString()
                )

                // Добавляем в список
                messageAdapter.addMessage(tempMessage)

                // Отправляем на сервер
                sendMessageToServer(messageText)

                // Очищаем поле ввода
                binding.etMessage.setText("")

                // Возвращаем фокус
                binding.etMessage.requestFocus()
            } else {
                Toast.makeText(this, "Нет подключения к серверу", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun connectToWebSocket() {
        // Ваш код подключения к WebSocket
        isConnected = true
    }

    private fun sendMessageToServer(message: String) {
        // Ваш код отправки сообщения
    }

    override fun onDestroy() {
        super.onDestroy()
        // Закрыть WebSocket соединение
    }
}

// Создайте отдельный адаптер для MainActivity
class MainMessageAdapter(
    private val messages: MutableList<ChatMessage>,
    private val myUserId: Int
) : RecyclerView.Adapter<MainMessageAdapter.MessageViewHolder>() {

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMyMessage: TextView = itemView.findViewById(R.id.tvMyMessage)
        val tvMyTime: TextView = itemView.findViewById(R.id.tvMyTime)
        val tvOtherMessage: TextView = itemView.findViewById(R.id.tvOtherMessage)
        val tvOtherTime: TextView = itemView.findViewById(R.id.tvOtherTime)
        val layoutMyMessage: LinearLayout = itemView.findViewById(R.id.layoutMyMessage)
        val layoutOtherMessage: LinearLayout = itemView.findViewById(R.id.layoutOtherMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]

        // Форматирование времени
        val timeString = try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date = inputFormat.parse(message.timestamp ?: "")
            val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            message.timestamp ?: ""
        }

        val isMyMessage = message.sender_id == myUserId

        if (isMyMessage) {
            holder.layoutMyMessage.visibility = View.VISIBLE
            holder.layoutOtherMessage.visibility = View.GONE
            holder.tvMyMessage.text = message.content ?: ""
            holder.tvMyTime.text = timeString

            // Полупрозрачность для временных сообщений
            if (message.temp_id != null) {
                holder.layoutMyMessage.alpha = 0.7f
            } else {
                holder.layoutMyMessage.alpha = 1f
            }
        } else {
            holder.layoutMyMessage.visibility = View.GONE
            holder.layoutOtherMessage.visibility = View.VISIBLE
            holder.tvOtherMessage.text = message.content ?: ""
            holder.tvOtherTime.text = timeString
        }
    }

    override fun getItemCount(): Int {
        return messages.size
    }

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }
}