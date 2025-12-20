package com.example.artchat.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File

object FileUtils {

    fun getGalleryDirectory(context: Context): File {
        val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "ArtChat")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return directory
    }

    fun getDrawingFile(context: Context, fileName: String): File {
        val directory = getGalleryDirectory(context)
        return File(directory, fileName)
    }

    fun getUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
    }

    fun saveBitmapToFile(context: Context, bitmap: android.graphics.Bitmap, fileName: String): File? {
        return try {
            val file = getDrawingFile(context, fileName)
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            java.io.FileOutputStream(file).use { fos ->
                fos.write(stream.toByteArray())
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}