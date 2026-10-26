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
                    showDemoOrEmpty(list)
                } else {
                    showDemoOrEmpty(emptyList())
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                showDemoOrEmpty(emptyList())
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

    private fun getDemoConversations(): List<ConversationResponse> = emptyList()
}
