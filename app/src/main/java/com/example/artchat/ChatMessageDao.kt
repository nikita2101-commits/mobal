package com.example.artchat.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Update
    suspend fun updateMessage(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE room = :room ORDER BY timestamp ASC")
    fun getMessagesByRoom(room: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE temp_id = :tempId")
    suspend fun getMessageByTempId(tempId: String): ChatMessageEntity?

    @Query("UPDATE chat_messages SET server_id = :serverId, is_synced = 1 WHERE temp_id = :tempId")
    suspend fun updateTempMessage(tempId: String, serverId: Int)

    @Query("DELETE FROM chat_messages WHERE timestamp < :oldTimestamp")
    suspend fun deleteOldMessages(oldTimestamp: Long)

    @Query("SELECT COUNT(*) FROM chat_messages WHERE room = :room")
    suspend fun getMessageCount(room: String): Int

    @Query("SELECT * FROM chat_messages WHERE server_id IS NULL AND is_synced = 0")
    suspend fun getUnsyncedMessages(): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE room = :room ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(room: String, limit: Int): List<ChatMessageEntity>

    // Простой метод для проверки соединения
    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun getTotalCount(): Int
}