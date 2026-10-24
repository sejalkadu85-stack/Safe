package com.womensafety.data.api

import android.util.Log
import com.google.gson.Gson
import com.womensafety.utils.Constants
import okhttp3.*
import java.util.concurrent.TimeUnit

class ChatWebSocketManager(
    private val listener: ChatWebSocketListener
) {

    interface ChatWebSocketListener {
        fun onConnected()
        fun onDisconnected()
        fun onMessageReceived(event: String, dataJson: String)
        fun onError(error: String)
    }

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    fun connect(conversationId: Int, token: String) {
        disconnect()

        val baseUrl = Constants.BASE_URL
            .replace("http://", "ws://")
            .replace("https://", "wss://")
            .removeSuffix("/")

        val wsUrl = "$baseUrl/ws/chat/$conversationId/?token=$token"
        Log.d("ChatWebSocket", "Connecting to WebSocket: $wsUrl")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("ChatWebSocket", "WebSocket Connected!")
                listener.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("ChatWebSocket", "WebSocket Message: $text")
                try {
                    val map = gson.fromJson(text, Map::class.java)
                    val event = map["event"] as? String ?: "new_message"
                    val dataObj = map["data"]
                    val dataJson = gson.toJson(dataObj)
                    listener.onMessageReceived(event, dataJson)
                } catch (e: Exception) {
                    listener.onMessageReceived("raw", text)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("ChatWebSocket", "WebSocket Closing: $reason (code: $code)")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("ChatWebSocket", "WebSocket Closed")
                listener.onDisconnected()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("ChatWebSocket", "WebSocket Error: ${t.message}", t)
                listener.onError(t.message ?: "Connection error")
            }
        })
    }

    fun sendMessage(content: String, messageType: String = "text") {
        val payload = mapOf(
            "type" to "message",
            "content" to content,
            "message_type" to messageType
        )
        val json = gson.toJson(payload)
        webSocket?.send(json)
    }

    fun sendTyping(isTyping: Boolean) {
        val payload = mapOf(
            "type" to "typing",
            "is_typing" to isTyping
        )
        val json = gson.toJson(payload)
        webSocket?.send(json)
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "User left chat")
        } catch (_: Exception) {}
        webSocket = null
    }
}
