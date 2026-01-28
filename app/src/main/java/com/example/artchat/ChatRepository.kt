package com.example.artchat.repository

import android.content.Context
import androidx.lifecycle.asFlow
import com.example.artchat.database.ChatDatabase
import com.example.artchat.database.ChatMessageEntity
import com.example.artchat.model.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class ChatRepository private constructor(context: Context) {

    private val database = ChatDatabase.getDatabase(context)
    private val dao = database.chatMessageDao()

    companion object {
        @Volatile
        private var INSTANCE: ChatRepository? = null

        fun getInstance(context: Context): ChatRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = ChatRepository(context)
                INSTANCE = instance
                instance
            }
        }
    }

    fun getMessagesByRoom(room: String): Flow<List<ChatMessage>> {
        return dao.getMessagesByRoom(room).map { entities ->
            entities.map { it.toChatMessage() }
        }
    }

    suspend fun saveMessage(message: ChatMessage): Long {
        val entity = ChatMessageEntity.fromChatMessage(message)
        return dao.insertMessage(entity)
    }

    suspend fun saveMessages(messages: List<ChatMessage>) {
        val entities = messages.map { ChatMessageEntity.fromChatMessage(it) }
        dao.insertMessages(entities)
    }

    suspend fun updateTempMessage(tempId: String, serverId: Int) {
        dao.updateTempMessage(tempId, serverId)
    }

    suspend fun getRecentMessages(room: String, limit: Int = 100): List<ChatMessage> {
        return dao.getRecentMessages(room, limit).map { it.toChatMessage() }
    }

    suspend fun cleanupOldMessages(days: Int = 30) {
        val oldTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        dao.deleteOldMessages(oldTimestamp)
    }

    suspend fun getUnsentMessages(): List<ChatMessage> {
        return dao.getUnsyncedMessages().map { it.toChatMessage() }
    }

    suspend fun markMessageAsSent(tempId: String, serverId: Int) {
        dao.updateTempMessage(tempId, serverId)
    }

    suspend fun getMessageCount(room: String): Int {
        return dao.getMessageCount(room)
    }
}