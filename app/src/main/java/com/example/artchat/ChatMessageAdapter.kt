package com.example.artchat.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.artchat.R
import com.example.artchat.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.*

class ChatMessageAdapter(
    private var messages: List<ChatMessage>,
    private val currentUserId: Int
) : RecyclerView.Adapter<ChatMessageAdapter.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_MY_MESSAGE = 1
        private const val VIEW_TYPE_OTHER_MESSAGE = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].sender_id == currentUserId) {
            VIEW_TYPE_MY_MESSAGE
        } else {
            VIEW_TYPE_OTHER_MESSAGE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutId = if (viewType == VIEW_TYPE_MY_MESSAGE) {
            R.layout.item_message_my
        } else {
            R.layout.item_message_other
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = messages[position]
        holder.bind(message)
    }

    override fun getItemCount(): Int = messages.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val tvSender: TextView = itemView.findViewById(R.id.tvSender)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)

        fun bind(message: ChatMessage) {
            tvMessage.text = message.content

            // Добавляем "(Вы)" для своих сообщений
            if (message.sender_id == currentUserId) {
                tvSender.text = "${message.sender_name} (Вы)"
            } else {
                tvSender.text = message.sender_name
            }

            // Исправленная строка: форматируем время из String
            tvTime.text = formatTime(message.timestamp)
        }

        // Измененный метод: принимает String? вместо Long
        private fun formatTime(timestamp: String?): String {
            return try {
                if (timestamp == null) {
                    "--:--"
                } else {
                    // Пытаемся преобразовать строку в Long
                    val timeLong = timestamp.toLongOrNull()
                    if (timeLong != null) {
                        // Если это числовой timestamp
                        val date = Date(timeLong)
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
                    } else {
                        // Если это строка даты в другом формате
                        // Можно попробовать распарсить как ISO дату или оставить как есть
                        if (timestamp.length > 5) {
                            timestamp.substring(11, 16) // Если формат "yyyy-MM-ddTHH:mm:ss"
                        } else {
                            timestamp // Если уже короткий формат
                        }
                    }
                }
            } catch (e: Exception) {
                "??:??"
            }
        }
    }

    // Метод для добавления нового сообщения
    fun addMessage(message: ChatMessage) {
        messages = messages + message
        notifyItemInserted(messages.size - 1)
    }

    // Метод для добавления нескольких сообщений
    fun addMessages(newMessages: List<ChatMessage>) {
        messages = messages + newMessages
        notifyItemRangeInserted(messages.size - newMessages.size, newMessages.size)
    }

    // Метод для обновления всех сообщений
    fun updateMessages(newMessages: List<ChatMessage>) {
        messages = newMessages
        notifyDataSetChanged()
    }

    // Метод для проверки, является ли сообщение своим
    fun isMyMessage(position: Int): Boolean {
        return messages[position].sender_id == currentUserId
    }

    // Метод для получения последнего сообщения
    fun getLastMessage(): ChatMessage? {
        return if (messages.isNotEmpty()) messages.last() else null
    }

    // Метод для очистки всех сообщений
    fun clearMessages() {
        messages = emptyList()
        notifyDataSetChanged()
    }
}