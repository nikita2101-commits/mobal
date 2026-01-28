package com.example.artchat.utils

object Config {
    // ЗАМЕНИТЕ НА ВАШ РЕАЛЬНЫЙ IP АДРЕС
    const val SERVER_URL = "http://192.168.1.6:5000" // или ваш реальный IP
    const val WS_URL = "ws://192.168.1.6:5000" // тот же IP

    // Таймауты
    const val CONNECT_TIMEOUT = 30L
    const val READ_TIMEOUT = 30L
    const val WRITE_TIMEOUT = 30L

    // WebSocket настройки
    const val WEBSOCKET_PING_INTERVAL = 30L

    // Пути API
    const val API_BASE_PATH = "/api"
    const val SOCKET_IO_PATH = "/socket.io"

    fun getApiUrl(): String = "$SERVER_URL$API_BASE_PATH/"
    fun getWebSocketUrl(): String = "$WS_URL$SOCKET_IO_PATH"
}