package com.example.artchat.model

import com.google.gson.annotations.SerializedName

data class ChatMessage(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("room") val room: String? = null,
    @SerializedName("sender_id") val sender_id: Int? = null,
    @SerializedName("sender_name") val sender_name: String? = null,
    @SerializedName("message_type") val message_type: String? = null,
    @SerializedName("content") val content: String? = null,
    @SerializedName("drawing_url") val drawing_url: String? = null,
    @SerializedName("image_url") val image_url: String? = null,
    @SerializedName("timestamp") val timestamp: String? = null,
    @SerializedName("is_read") val is_read: Boolean? = false,
    val temp_id: String? = null // Добавляем для отслеживания временных сообщений
)