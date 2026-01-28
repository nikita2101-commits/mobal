package com.example.artchat.model

import com.google.gson.annotations.SerializedName

data class SendMessageRequest(
    @SerializedName("sender_id")
    val sender_id: Int? = null,

    @SerializedName("sender_name")
    val sender_name: String,

    @SerializedName("content")
    val content: String,

    @SerializedName("room_id")
    val room_id: Int? = null,

    @SerializedName("is_private")
    val is_private: Boolean = false,

    @SerializedName("recipient_id")
    val recipient_id: Int? = null,

    @SerializedName("message_type")
    val message_type: String = "text"
)