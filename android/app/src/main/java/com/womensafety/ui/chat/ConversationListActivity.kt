package com.womensafety.ui.chat

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.womensafety.R
import com.womensafety.data.api.ApiClient
import com.womensafety.data.models.ConversationResponse
import com.womensafety.data.models.MessageResponse
import com.womensafety.data.models.UserResponse
import com.womensafety.databinding.ActivityConversationListBinding
import com.womensafety.ui.contacts.TrustedContactsActivity
import com.womensafety.utils.SharedPrefManager
import kotlinx.coroutines.launch

class ConversationListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConversationListBinding
    private lateinit var adapter: ConversationListAdapter
    private lateinit var prefs: SharedPrefManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConversationListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SharedPrefManager.getInstance(this)

        setupRecyclerView()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        loadConversations()
    }

    private fun setupRecyclerView() {
        val currentUserId = prefs.getUserId()
        adapter = ConversationListAdapter(currentUserId) { conversation, otherName, otherId ->
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conversation.id)
                putExtra(ChatActivity.EXTRA_CONTACT_NAME, otherName)
                putExtra(ChatActivity.EXTRA_CONTACT_ID, otherId ?: -1)
            }
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        binding.rvConversations.layoutManager = LinearLayoutManager(this)
        binding.rvConversations.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.swipeRefresh.setColorSchemeResources(R.color.neon_cyan, R.color.primary)
        binding.swipeRefresh.setOnRefreshListener {
            loadConversations()
        }

        binding.btnNewChat.setOnClickListener {
            startActivity(Intent(this, TrustedContactsActivity::class.java))
        }

        binding.btnStartChat.setOnClickListener {
            startActivity(Intent(this, TrustedContactsActivity::class.java))
        }
    }

    private fun loadConversations() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = ApiClient.getService(this@ConversationListActivity).getConversations()
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false

                if (response.isSuccessful && response.body() != null) {
                    val list = response.body()!!
                    if (list.isEmpty()) {
                        // Check if we should show mock demo list if brand new DB
                        showDemoOrEmpty(emptyList())
                    } else {
                        binding.llEmptyState.visibility = View.GONE
                        binding.rvConversations.visibility = View.VISIBLE
                        adapter.submitList(list)
                    }
                } else {
                    showDemoOrEmpty(getDemoConversations())
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                showDemoOrEmpty(getDemoConversations())
            }
        }
    }

    private fun showDemoOrEmpty(list: List<ConversationResponse>) {
        if (list.isEmpty()) {
            binding.llEmptyState.visibility = View.VISIBLE
            binding.rvConversations.visibility = View.GONE
        } else {
            binding.llEmptyState.visibility = View.GONE
            binding.rvConversations.visibility = View.VISIBLE
            adapter.submitList(list)
        }
    }

    private fun getDemoConversations(): List<ConversationResponse> {
        val guardian = UserResponse(
            id = 2,
            username = "guardian_mom",
            email = "mom@example.com",
            phoneNumber = "+91 98765 43210",
            firstName = "Mom",
            lastName = "Guardian",
            role = "guardian",
            roleDisplay = "Parent / Guardian",
            profilePhoto = null,
            isVerified = true,
            profile = null
        )
        val responder = UserResponse(
            id = 3,
            username = "responder_raj",
            email = "raj@safety.org",
            phoneNumber = "+91 91234 56789",
            firstName = "Raj",
            lastName = "Volunteer Responder",
            role = "volunteer",
            roleDisplay = "Volunteer",
            profilePhoto = null,
            isVerified = true,
            profile = null
        )

        return listOf(
            ConversationResponse(
                id = 1,
                participants = listOf(guardian),
                lastMessage = MessageResponse(
                    id = 101,
                    conversation = 1,
                    sender = guardian,
                    content = "Okay beta, be careful. We're on our way.",
                    messageType = "text",
                    timestamp = "2026-10-01T00:30:00Z",
                    isRead = false,
                    isMe = false
                ),
                unreadCount = 1,
                createdAt = "2026-10-01T00:00:00Z",
                updatedAt = "2026-10-01T00:30:00Z"
            ),
            ConversationResponse(
                id = 2,
                participants = listOf(responder),
                lastMessage = MessageResponse(
                    id = 102,
                    conversation = 2,
                    sender = responder,
                    content = "📍 Shared location near Metro Station Gate 2",
                    messageType = "location",
                    timestamp = "2026-10-01T00:25:00Z",
                    isRead = true,
                    isMe = false
                ),
                unreadCount = 0,
                createdAt = "2026-10-01T00:00:00Z",
                updatedAt = "2026-10-01T00:25:00Z"
            )
        )
    }
}
