package com.example.artchat.model

data class User(
    val id: Int,
    val email: String? = null,
    val username: String,
    val display_name: String,
    val is_guest: Boolean = false,
    val avatar_color: String? = null,
    val bio: String? = null,
    val avatar_url: String? = null,
    val is_online: Boolean? = null,
    val last_seen: String? = null
)