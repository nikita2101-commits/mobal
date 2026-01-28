package com.example.artchat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.artchat.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private val messages: MutableList<ChatMessage>,
    private val myUserId: Int
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

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
                holder.tvMyMessage.text = "${message.content ?: ""} (отправляется...)"
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

    fun updateMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }
}