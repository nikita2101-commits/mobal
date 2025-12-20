package com.example.artchat.model

data class ChatMessage(
    val id: Int? = null,
    val room: String = "global",
    val sender_id: Int,
    val sender_name: String,
    val message_type: String = "text",
    val content: String,
    val drawing_url: String? = null,
    val image_url: String? = null,
    val timestamp: String? = null,
    val is_read: Boolean = false
)

data class SendMessageRequest(
    val room: String = "global",
    val content: String,
    val message_type: String = "text",
    val drawing_url: String? = null,
    val image_url: String? = null
)