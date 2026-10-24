package com.womensafety.ui.chat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.womensafety.data.api.ApiClient
import com.womensafety.data.api.ApiService
import com.womensafety.data.models.IncidentMessageItem
import com.womensafety.databinding.ActivityIncidentChatBinding
import com.womensafety.utils.SharedPrefManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IncidentChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIncidentChatBinding
    private lateinit var adapter: IncidentChatAdapter
    private lateinit var apiService: ApiService
    private lateinit var prefs: SharedPrefManager

    private var alertId: Int = 1
    private var victimName: String = "User"
    private var category: String = "unsafe_situation"

    companion object {
        const val EXTRA_ALERT_ID = "extra_alert_id"
        const val EXTRA_VICTIM_NAME = "extra_victim_name"
        const val EXTRA_CATEGORY = "extra_category"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIncidentChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        alertId = intent.getIntExtra(EXTRA_ALERT_ID, 1)
        victimName = intent.getStringExtra(EXTRA_VICTIM_NAME) ?: "Priya Sharma"
        category = intent.getStringExtra(EXTRA_CATEGORY) ?: "Harassment"

        prefs = SharedPrefManager.getInstance(this)
        apiService = ApiClient.getService(this)

        setupUI()
        setupListeners()
        setupQuickReplies()
        loadMessages()
        startPeriodicPolling()
    }

    private fun setupUI() {
        binding.tvChatTitle.text = "Emergency Room #$alertId"
        binding.tvCategoryBadge.text = category.replace("_", " ").uppercase()

        val myName = prefs.getUserName().takeIf { !it.isNullOrBlank() } ?: "Me"
        adapter = IncidentChatAdapter(currentUserName = myName)

        val layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.rvMessages.layoutManager = layoutManager
        binding.rvMessages.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnCallEmergency.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
        }

        binding.btnSendMessage.setOnClickListener {
            sendMessageFromInput()
        }

        binding.etMessageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessageFromInput()
                true
            } else {
                false
            }
        }
    }

    private fun setupQuickReplies() {
        binding.chipOnTheWay.setOnClickListener {
            sendChatMessage("I am on the way to your location now! Stay on the line.")
        }
        binding.chipWhereAreYou.setOnClickListener {
            sendChatMessage("Where exactly are you right now? Any landmarks nearby?")
        }
        binding.chipPoliceNotified.setOnClickListener {
            sendChatMessage("Police 112 has been notified with your GPS coordinates. Stay calm.")
        }
        binding.chipStaySafe.setOnClickListener {
            sendChatMessage("Stay in a well-lit, public location if possible. Help is arriving.")
        }
    }

    private fun sendMessageFromInput() {
        val text = binding.etMessageInput.text.toString().trim()
        if (text.isEmpty()) return
        binding.etMessageInput.setText("")
        sendChatMessage(text)
    }

    private fun sendChatMessage(content: String) {
        val myName = prefs.getUserName().takeIf { !it.isNullOrBlank() } ?: "Me"
        val myRole = prefs.getUserRole() ?: "user"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = apiService.sendIncidentChatMessage(
                    alertId,
                    mapOf("message" to content)
                )
                withContext(Dispatchers.Main) {
                    loadMessages()
                }
            } catch (e: Exception) {
                // Local demo append if offline
                withContext(Dispatchers.Main) {
                    val currentList = adapter.currentList.toMutableList()
                    currentList.add(
                        IncidentMessageItem(
                            id = System.currentTimeMillis().toInt(),
                            senderName = myName,
                            senderRole = myRole,
                            content = content,
                            timestamp = "Just now"
                        )
                    )
                    adapter.submitList(currentList) {
                        binding.rvMessages.scrollToPosition(currentList.size - 1)
                    }
                }
            }
        }
    }

    private fun loadMessages() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = apiService.getIncidentChat(alertId)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val messages = response.body()!!.messages
                        adapter.submitList(messages) {
                            if (messages.isNotEmpty()) {
                                binding.rvMessages.scrollToPosition(messages.size - 1)
                            }
                        }
                    } else {
                        displayDemoMessages()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    displayDemoMessages()
                }
            }
        }
    }

    private fun displayDemoMessages() {
        if (adapter.itemCount == 0) {
            val demoList = listOf(
                IncidentMessageItem(
                    id = 1,
                    senderName = victimName,
                    senderRole = "woman",
                    content = "EMERGENCY: Someone has been following me near the metro station. I need help!",
                    timestamp = "10:41 PM"
                ),
                IncidentMessageItem(
                    id = 2,
                    senderName = "System",
                    senderRole = "admin",
                    content = "SOS alert broadcast to Primary Guardian and closest Verified Responders.",
                    timestamp = "10:41 PM"
                ),
                IncidentMessageItem(
                    id = 3,
                    senderName = "Mom (Primary Guardian)",
                    senderRole = "guardian",
                    content = "Priya, I received your SOS! I am calling you right now, stay inside the store!",
                    timestamp = "10:42 PM"
                ),
                IncidentMessageItem(
                    id = 4,
                    senderName = "Vikram (Volunteer Responder)",
                    senderRole = "volunteer",
                    content = "I am 300m away at Deccan Gymkhana. I am heading to your coordinates now!",
                    timestamp = "10:43 PM"
                )
            )
            adapter.submitList(demoList) {
                binding.rvMessages.scrollToPosition(demoList.size - 1)
            }
        }
    }

    private fun startPeriodicPolling() {
        lifecycleScope.launch {
            while (isActive) {
                delay(4000)
                try {
                    val response = apiService.getIncidentChat(alertId)
                    if (response.isSuccessful && response.body() != null) {
                        val messages = response.body()!!.messages
                        if (messages.isNotEmpty()) {
                            adapter.submitList(messages)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
