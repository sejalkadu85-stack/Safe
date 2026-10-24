package com.womensafety.ui.contacts

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.womensafety.data.models.TrustedContactResponse
import com.womensafety.databinding.ItemTrustedContactBinding

class TrustedContactsAdapter(
    private val onCallClicked: (TrustedContactResponse) -> Unit,
    private val onSetTierClicked: (TrustedContactResponse) -> Unit,
    private val onVerifyClicked: (TrustedContactResponse) -> Unit,
    private val onMessageClicked: (TrustedContactResponse) -> Unit,
    private val onDeleteClicked: (TrustedContactResponse) -> Unit
) : ListAdapter<TrustedContactResponse, TrustedContactsAdapter.ContactViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemTrustedContactBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ContactViewHolder(
        private val binding: ItemTrustedContactBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: TrustedContactResponse) {
            val user = contact.contactUser
            val displayName = contact.name?.takeIf { it.isNotBlank() }
                ?: contact.nickname.takeIf { it.isNotBlank() }
                ?: user?.username
                ?: "Contact"

            binding.tvContactName.text = displayName
            binding.tvAvatarInitial.text = displayName.take(1).uppercase()
            binding.tvRelationshipBadge.text = contact.relationshipDisplay ?: contact.relationship.replaceFirstChar { it.uppercase() }
            binding.tvContactPhone.text = contact.phoneNumber?.takeIf { it.isNotBlank() }
                ?: user?.phoneNumber
                ?: user?.email
                ?: "+91 98765 43210"

            // Tier Badge
            val tier = contact.tier?.lowercase() ?: "primary"
            when (tier) {
                "primary" -> {
                    binding.tvTierBadge.text = "PRIMARY GUARDIAN (TIER 1)"
                    binding.tvTierBadge.setTextColor(Color.parseColor("#00E5FF"))
                    binding.tvTierBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#3300E5FF"))
                }
                "secondary" -> {
                    binding.tvTierBadge.text = "SECONDARY GUARDIAN (TIER 2)"
                    binding.tvTierBadge.setTextColor(Color.parseColor("#FF9100"))
                    binding.tvTierBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#33FF9100"))
                }
                "friend" -> {
                    binding.tvTierBadge.text = "FRIEND (TIER 2)"
                    binding.tvTierBadge.setTextColor(Color.parseColor("#00E676"))
                    binding.tvTierBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#3300E676"))
                }
                else -> {
                    binding.tvTierBadge.text = "OTHER CONTACT"
                    binding.tvTierBadge.setTextColor(Color.parseColor("#8F9BB3"))
                    binding.tvTierBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#228F9BB3"))
                }
            }

            // Verification status
            binding.tvVerifiedBadge.visibility = if (contact.isVerified) View.VISIBLE else View.GONE

            binding.btnCallContact.setOnClickListener { onCallClicked(contact) }
            binding.btnSetTier.setOnClickListener { onSetTierClicked(contact) }
            binding.btnVerifyContact.setOnClickListener { onVerifyClicked(contact) }
            binding.btnMessageContact.setOnClickListener { onMessageClicked(contact) }
            binding.btnDeleteContact.setOnClickListener { onDeleteClicked(contact) }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<TrustedContactResponse>() {
            override fun areItemsTheSame(oldItem: TrustedContactResponse, newItem: TrustedContactResponse): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: TrustedContactResponse, newItem: TrustedContactResponse): Boolean {
                return oldItem == newItem
            }
        }
    }
}
