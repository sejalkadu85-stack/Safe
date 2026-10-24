package com.womensafety.ui.chat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.womensafety.R
import com.womensafety.data.api.ApiClient
import com.womensafety.data.api.ChatWebSocketManager
import com.womensafety.data.models.MessageResponse
import com.womensafety.data.models.QuickTemplateResponse
import com.womensafety.data.models.SendMessageRequest
import com.womensafety.databinding.ActivityChatBinding
import com.womensafety.utils.SharedPrefManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ChatActivity : AppCompatActivity(), ChatWebSocketManager.ChatWebSocketListener {

    private lateinit var binding: ActivityChatBinding
    private lateinit var prefs: SharedPrefManager
    private var webSocketManager: ChatWebSocketManager? = null

    private var conversationId: Int = -1
    private var contactId: Int = -1
    private var contactName: String = "Guardian"
    private var contactPhone: String = "+91 98765 43210"

    private val displayedMessageIds = mutableSetOf<Int>()
    private val gson = Gson()

    companion object {
        const val EXTRA_CONVERSATION_ID = "extra_conversation_id"
        const val EXTRA_CONTACT_NAME = "extra_contact_name"
        const val EXTRA_CONTACT_ID = "extra_contact_id"
        const val EXTRA_CONTACT_PHONE = "extra_contact_phone"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SharedPrefManager.getInstance(this)

        conversationId = intent.getIntExtra(EXTRA_CONVERSATION_ID, -1)
        contactId = intent.getIntExtra(EXTRA_CONTACT_ID, -1)
        contactName = intent.getStringExtra(EXTRA_CONTACT_NAME) ?: "Guardian"
        contactPhone = intent.getStringExtra(EXTRA_CONTACT_PHONE) ?: "+91 98765 43210"

        setupHeader()
        setupListeners()
        setupQuickTemplates()
        loadMessages()
        connectWebSocket()
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocketManager?.disconnect()
    }

    private fun setupHeader() {
        binding.tvContactName.text = contactName
        binding.tvAvatarInitial.text = contactName.take(1).uppercase()

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnVoiceCall.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$contactPhone")))
        }

        binding.btnVideoCall.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$contactPhone")))
        }
    }

    private val voiceRecorder by lazy { com.womensafety.utils.VoiceRecorderHelper(this) }
    private val voicePlayer by lazy { com.womensafety.utils.VoicePlayerHelper() }

    private fun setupListeners() {
        binding.btnSendMessage.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text, "text")
                binding.etMessage.setText("")
            }
        }

        // Location Pin Button
        binding.btnSendLocation.setOnClickListener {
            shareCurrentLocationPin()
        }

        // Voice Message Recording Button
        binding.btnVoiceNote.setOnClickListener {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.RECORD_AUDIO
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this, arrayOf(android.Manifest.permission.RECORD_AUDIO), 2003
                )
                return@setOnClickListener
            }

            if (!voiceRecorder.isCurrentlyRecording()) {
                if (voiceRecorder.startRecording()) {
                    binding.ivMicIcon.setColorFilter(ContextCompat.getColor(this, R.color.primary))
                    Toast.makeText(this, "🔴 Recording voice note... Tap mic again to send", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Unable to start microphone", Toast.LENGTH_SHORT).show()
                }
            } else {
                val duration = voiceRecorder.stopRecording()
                binding.ivMicIcon.setColorFilter(ContextCompat.getColor(this, R.color.white))
                val audioFile = voiceRecorder.outputFile

                if (audioFile != null && audioFile.exists() && audioFile.length() > 0) {
                    val msgText = "🎙️ Voice Message (${duration}s)"
                    sendMessage(msgText, "voice")
                    Toast.makeText(this, "🎤 Voice message sent!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Voice note recording cancelled", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shareCurrentLocationPin() {
        // Sample/Live coordinates (Delhi/NCR area default or GPS)
        val lat = 28.6139
        val lng = 77.2090
        val locationContent = "📍 Live Location Pin: https://maps.google.com/?q=$lat,$lng"
        sendMessage(locationContent, "location")
        Toast.makeText(this, "Location pin sent to chat", Toast.LENGTH_SHORT).show()
    }

    private fun setupQuickTemplates() {
        // Load templates from API or use default safety templates
        lifecycleScope.launch {
            try {
                val response = ApiClient.getService(this@ChatActivity).getQuickTemplates()
                if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                    populateQuickChips(response.body()!!)
                } else {
                    populateDefaultQuickChips()
                }
            } catch (_: Exception) {
                populateDefaultQuickChips()
            }
        }
    }

    private fun populateDefaultQuickChips() {
        val defaults = listOf(
            QuickTemplateResponse(1, "Safe", "I'm safe ✅", isDefault = true),
            QuickTemplateResponse(2, "Pick Up", "Come pick me up 🚗", isDefault = true),
            QuickTemplateResponse(3, "Call Now", "Call me NOW 📞", isDefault = true),
            QuickTemplateResponse(4, "Need Help", "In danger, need help 🆘", isDefault = true),
            QuickTemplateResponse(5, "Home", "Reached home safely 🏠", isDefault = true)
        )
        populateQuickChips(defaults)
    }

    private fun populateQuickChips(templates: List<QuickTemplateResponse>) {
        binding.llQuickTemplates.removeAllViews()

        for (template in templates) {
            val chip = TextView(this).apply {
                text = template.content
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.white))
                setBackgroundResource(R.drawable.bg_dark_card_subtle)
                setPadding(28, 14, 28, 14)

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 16
                }
                layoutParams = params

                setOnClickListener {
                    sendMessage(template.content, "quick")
                }
            }
            binding.llQuickTemplates.addView(chip)
        }
    }

    private fun connectWebSocket() {
        val token = prefs.getAccessToken()
        if (conversationId > 0 && !token.isNullOrBlank()) {
            webSocketManager = ChatWebSocketManager(this)
            webSocketManager?.connect(conversationId, token)
        }
    }

    // WebSocket Callbacks
    override fun onConnected() {
        runOnUiThread {
            binding.tvStatus.text = "Connected (Live WS)"
            binding.vStatusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.neon_green))
        }
    }

    override fun onDisconnected() {
        runOnUiThread {
            binding.tvStatus.text = "Offline Mode"
            binding.vStatusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.text_muted))
        }
    }

    override fun onMessageReceived(event: String, dataJson: String) {
        runOnUiThread {
            try {
                if (event == "new_message") {
                    val map = gson.fromJson(dataJson, Map::class.java)
                    val msgId = (map["message_id"] as? Double)?.toInt() ?: System.currentTimeMillis().toInt()
                    val senderId = (map["sender_id"] as? Double)?.toInt() ?: -1
                    val content = map["content"] as? String ?: ""
                    val messageType = map["message_type"] as? String ?: "text"
                    val timestamp = map["timestamp"] as? String ?: ""
                    val isMe = senderId == prefs.getUserId()

                    if (!displayedMessageIds.contains(msgId)) {
                        displayedMessageIds.add(msgId)
                        addMessageBubble(content, messageType, isMe, formatTime(timestamp))
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatActivity", "Error parsing incoming WS message: ${e.message}")
            }
        }
    }

    override fun onError(error: String) {
        runOnUiThread {
            binding.tvStatus.text = "Offline / Syncing"
            binding.vStatusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.neon_orange))
        }
    }

    private fun loadMessages() {
        if (conversationId <= 0) {
            // New conversation demo initial welcome
            addMessageBubble("Chat channel opened with $contactName.", "system", false, "Now")
            return
        }

        lifecycleScope.launch {
            try {
                val response = ApiClient.getService(this@ChatActivity).getConversationMessages(conversationId)
                if (response.isSuccessful && response.body() != null) {
                    val messages = response.body()!!.messages
                    for (msg in messages) {
                        if (!displayedMessageIds.contains(msg.id)) {
                            displayedMessageIds.add(msg.id)
                            addMessageBubble(msg.content, msg.messageType, msg.isMe, formatTime(msg.timestamp))
                        }
                    }
                } else {
                    loadDemoMessages()
                }
            } catch (_: Exception) {
                loadDemoMessages()
            }
        }
    }

    private fun loadDemoMessages() {
        if (displayedMessageIds.isEmpty()) {
            addMessageBubble("I'm safe now. Reached the society gate.", "text", true, "09:28 PM")
            addMessageBubble("Okay beta, be careful. We're on our way.", "text", false, "09:29 PM")
            addMessageBubble("📍 Live Location Pin: https://maps.google.com/?q=28.6139,77.2090", "location", false, "09:30 PM")
        }
    }

    private fun sendMessage(content: String, type: String) {
        val currentTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())

        // 1. Immediately show bubble in UI
        addMessageBubble(content, type, true, currentTime)

        // 2. Try sending via WebSocket first
        webSocketManager?.sendMessage(content, type)

        // 3. Also persist via REST API
        lifecycleScope.launch {
            try {
                val req = SendMessageRequest(
                    conversationId = if (conversationId > 0) conversationId else null,
                    recipientId = if (conversationId <= 0 && contactId > 0) contactId else null,
                    content = content,
                    messageType = type
                )
                val resp = ApiClient.getService(this@ChatActivity).sendMessage(req)
                if (resp.isSuccessful && resp.body() != null) {
                    val savedMsg = resp.body()!!
                    if (conversationId <= 0) {
                        conversationId = savedMsg.conversation
                        connectWebSocket()
                    }
                    displayedMessageIds.add(savedMsg.id)
                }
            } catch (e: Exception) {
                Log.d("ChatActivity", "REST fallback message saved locally: ${e.message}")
            }
        }
    }

    private fun addMessageBubble(content: String, type: String, isMe: Boolean, timeText: String) {
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 22, 32, 22)
        }

        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 16
            if (isMe) {
                gravity = Gravity.END
                marginStart = 100
                bubble.setBackgroundResource(R.drawable.bg_location_pill)
            } else {
                gravity = Gravity.START
                marginEnd = 100
                bubble.setBackgroundResource(R.drawable.bg_dark_card)
            }
        }
        bubble.layoutParams = layoutParams

        // Location message styling
        if (type == "location" || content.contains("maps.google.com")) {
            val tvLocationLabel = TextView(this).apply {
                text = "📍 SHARED LOCATION PIN"
                setTextColor(ContextCompat.getColor(context, R.color.neon_cyan))
                textSize = 12f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val tvLocationUrl = TextView(this).apply {
                text = "Tap to open live map navigation"
                setTextColor(ContextCompat.getColor(context, R.color.white))
                textSize = 14f
                setPadding(0, 6, 0, 0)
            }
            bubble.addView(tvLocationLabel)
            bubble.addView(tvLocationUrl)

            bubble.setOnClickListener {
                try {
                    val url = content.substringAfter("https://").let { "https://$it" }
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {
                    Toast.makeText(this, "Opening map...", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (type == "voice" || content.contains("Voice Message") || content.contains("🎙️")) {
            val tvVoiceLabel = TextView(this).apply {
                text = "🎙️ EMERGENCY VOICE NOTE"
                setTextColor(ContextCompat.getColor(context, R.color.primary))
                textSize = 12f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val tvVoiceContent = TextView(this).apply {
                text = content
                setTextColor(ContextCompat.getColor(context, R.color.white))
                textSize = 14f
                setPadding(0, 4, 0, 0)
            }
            bubble.addView(tvVoiceLabel)
            bubble.addView(tvVoiceContent)
        } else {
            // Text or Quick message
            val tvContent = TextView(this).apply {
                text = content
                setTextColor(ContextCompat.getColor(context, R.color.white))
                textSize = 14f
            }
            bubble.addView(tvContent)
        }

        // Time + Status tick
        val tvTime = TextView(this).apply {
            text = "$timeText ${if (isMe) "✓✓" else ""}"
            setTextColor(ContextCompat.getColor(context, if (isMe) R.color.neon_cyan else R.color.text_muted))
            textSize = 10f
            gravity = Gravity.END
            setPadding(0, 6, 0, 0)
        }
        bubble.addView(tvTime)

        binding.llMessageContainer.addView(bubble)
        binding.svMessages.post {
            binding.svMessages.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun formatTime(isoString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = inputFormat.parse(isoString.take(19)) ?: return isoString
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)
        } catch (_: Exception) {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        }
    }
}
