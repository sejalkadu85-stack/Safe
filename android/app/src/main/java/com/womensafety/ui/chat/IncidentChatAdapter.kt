package com.womensafety.ui.chat

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.womensafety.data.models.IncidentMessageItem
import com.womensafety.databinding.ItemIncidentMessageBinding

class IncidentChatAdapter(
    private val currentUserName: String
) : ListAdapter<IncidentMessageItem, IncidentChatAdapter.MessageViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemIncidentMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MessageViewHolder(
        private val binding: ItemIncidentMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: IncidentMessageItem) {
            binding.tvSenderName.text = item.senderName
            binding.tvMessageContent.text = item.content

            val timeDisplay = if (item.timestamp.contains("T")) {
                val timePart = item.timestamp.substringAfter("T").take(5)
                timePart
            } else {
                item.timestamp
            }
            binding.tvMessageTime.text = timeDisplay

            // Format role badge
            val (roleTitle, roleColorHex) = when (item.senderRole.lowercase()) {
                "woman", "victim", "user" -> Pair("VICTIM", "#FF2D7A")
                "guardian", "friend" -> Pair("GUARDIAN", "#00E5FF")
                "volunteer", "responder" -> Pair("RESPONDER", "#00E676")
                "admin" -> Pair("DISPATCH", "#FF9100")
                else -> Pair(item.senderRole.uppercase(), "#00E5FF")
            }
            binding.tvRoleBadge.text = roleTitle
            val color = Color.parseColor(roleColorHex)
            binding.tvRoleBadge.setTextColor(color)
            binding.tvRoleBadge.backgroundTintList = ColorStateList.valueOf(Color.argb(40, Color.red(color), Color.green(color), Color.blue(color)))

            // Align bubble right if sent by current user
            val isMe = item.senderName.equals(currentUserName, ignoreCase = true)
            val parentLayout = binding.root as LinearLayout
            parentLayout.gravity = if (isMe) Gravity.END else Gravity.START
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<IncidentMessageItem>() {
            override fun areItemsTheSame(oldItem: IncidentMessageItem, newItem: IncidentMessageItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: IncidentMessageItem, newItem: IncidentMessageItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
