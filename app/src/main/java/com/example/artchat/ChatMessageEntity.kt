package com.example.artchat.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import java.util.Date

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,

    @ColumnInfo(name = "server_id")
    val serverId: Int? = null,

    @ColumnInfo(name = "room")
    val room: String,

    @ColumnInfo(name = "sender_id")
    val senderId: Int,

    @ColumnInfo(name = "sender_name")
    val senderName: String,

    @ColumnInfo(name = "message_type")
    val messageType: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "drawing_url")
    val drawingUrl: String? = null,

    @ColumnInfo(name = "image_url")
    val imageUrl: String? = null,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "is_read")
    val isRead: Boolean = false,

    @ColumnInfo(name = "temp_id")
    val tempId: String? = null,

    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false
) {
    companion object {
        fun fromChatMessage(message: com.example.artchat.model.ChatMessage): ChatMessageEntity {
            val timestampLong = try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                sdf.parse(message.timestamp ?: "")?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }

            return ChatMessageEntity(
                serverId = message.id,
                room = message.room ?: "global",
                senderId = message.sender_id ?: -1,
                senderName = message.sender_name ?: "Unknown",
                messageType = message.message_type ?: "text",
                content = message.content ?: "",
                drawingUrl = message.drawing_url,
                imageUrl = message.image_url,
                timestamp = timestampLong,
                isRead = message.is_read ?: false,
                tempId = message.temp_id,
                isSynced = message.id != null
            )
        }
    }

    fun toChatMessage(): com.example.artchat.model.ChatMessage {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
        return com.example.artchat.model.ChatMessage(
            id = serverId,
            room = room,
            sender_id = senderId,
            sender_name = senderName,
            message_type = messageType,
            content = content,
            drawing_url = drawingUrl,
            image_url = imageUrl,
            timestamp = sdf.format(Date(timestamp)),
            is_read = isRead,
            temp_id = tempId
        )
    }
}