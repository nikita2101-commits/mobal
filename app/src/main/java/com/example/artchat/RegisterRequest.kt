package com.example.artchat.model

data class RegisterRequest(
    val email: String,
    val password: String,
    val username: String,
    val display_name: String,
    val avatar_color: String? = "#6200EE",
    val bio: String? = ""
)