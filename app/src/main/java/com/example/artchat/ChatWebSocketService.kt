package com.example.artchat

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.artchat.MainActivity
import com.example.artchat.R
import com.example.artchat.model.ChatMessage
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ChatWebSocketService : Service() {

    private lateinit var webSocket: WebSocket
    private val gson = Gson()
    private var isConnected = false

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "chat_service_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        connectWebSocket()
        return START_STICKY
    }

    private fun connectWebSocket() {
        val client = OkHttpClient.Builder()
            .readTimeout(3, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url("ws://10.201.240.101:5000/api/")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Parse message
                try {
                    val jsonObject = JSONObject(text)
                    val event = jsonObject.optString("event")

                    if (event == "new_message") {
                        val data = jsonObject.optJSONObject("data")
                        val message = ChatMessage(
                            id = data?.optInt("id") ?: 0,
                            room = data?.optString("room") ?: "global",
                            sender_id = data?.optInt("sender_id") ?: 0,
                            sender_name = data?.optString("sender_name") ?: "Unknown",
                            content = data?.optString("content") ?: "",
                            timestamp = data?.optString("timestamp") ?: ""
                        )

                        // Show notification
                        showMessageNotification(message)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                // Try to reconnect after 5 seconds
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isConnected) {
                        connectWebSocket()
                    }
                }, 5000)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
            }
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Chat Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background chat service"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ArtChat")
            .setContentText("Chat service is running")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use your icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun showMessageNotification(message: ChatMessage) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("New message from ${message.sender_name}")
            .setContentText(message.content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        webSocket.close(1000, "Service destroyed")
    }
}