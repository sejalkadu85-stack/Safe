package com.womensafety.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.womensafety.data.models.ConversationResponse
import com.womensafety.databinding.ItemConversationBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ConversationListAdapter(
    private val currentUserId: Int,
    private val onItemClick: (ConversationResponse, otherParticipantName: String, otherParticipantId: Int?) -> Unit
) : RecyclerView.Adapter<ConversationListAdapter.ViewHolder>() {

    private val conversations = mutableListOf<ConversationResponse>()

    fun submitList(list: List<ConversationResponse>) {
        conversations.clear()
        conversations.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConversationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(conversations[position])
    }

    override fun getItemCount(): Int = conversations.size

    inner class ViewHolder(private val binding: ItemConversationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(conversation: ConversationResponse) {
            // Find other participant
            val other = conversation.participants.firstOrNull { it.id != currentUserId }
                ?: conversation.participants.firstOrNull()

            val displayName = when {
                other != null -> {
                    val fullName = "${other.firstName.orEmpty()} ${other.lastName.orEmpty()}".trim()
                    if (fullName.isNotEmpty()) fullName else other.username
                }
                else -> "Safety Contact #${conversation.id}"
            }

            binding.tvParticipantName.text = displayName
            binding.tvAvatarInitial.text = displayName.take(1).uppercase()

            // Last message preview
            val lastMsg = conversation.lastMessage
            if (lastMsg != null) {
                val prefix = if (lastMsg.isMe) "You: " else ""
                val typeTag = when (lastMsg.messageType) {
                    "alert" -> "🆘 "
                    "location" -> "📍 "
                    "quick" -> "⚡ "
                    else -> ""
                }
                binding.tvLastMessage.text = "$prefix$typeTag${lastMsg.content}"
                binding.tvTimestamp.text = formatTimestamp(lastMsg.timestamp)
            } else {
                binding.tvLastMessage.text = "No messages yet"
                binding.tvTimestamp.text = conversation.createdAt?.let { formatTimestamp(it) } ?: ""
            }

            // Unread badge
            if (conversation.unreadCount > 0) {
                binding.tvUnreadBadge.visibility = View.VISIBLE
                binding.tvUnreadBadge.text = conversation.unreadCount.toString()
            } else {
                binding.tvUnreadBadge.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                onItemClick(conversation, displayName, other?.id)
            }
        }

        private fun formatTimestamp(isoString: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val date = inputFormat.parse(isoString.take(19)) ?: return isoString
                SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)
            } catch (_: Exception) {
                isoString.takeLast(8).take(5)
            }
        }
    }
}
