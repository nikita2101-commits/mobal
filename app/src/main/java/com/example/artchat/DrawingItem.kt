package com.example.artchat.model

import android.graphics.Bitmap

data class DrawingItem(
    val id: Int,
    val title: String,
    val filePath: String,
    val thumbnail: Bitmap,
    val date: Long
)