package com.example.artchat.model

data class UpdateProfileRequest(
    val username: String? = null,
    val display_name: String? = null,
    val bio: String? = null,
    val avatar_color: String? = null
)