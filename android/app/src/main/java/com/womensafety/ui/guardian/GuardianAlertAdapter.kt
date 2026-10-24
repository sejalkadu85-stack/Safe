package com.womensafety.ui.guardian

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.womensafety.data.models.GuardianActiveAlertItem
import com.womensafety.databinding.ItemGuardianAlertBinding

class GuardianAlertAdapter(
    private val onAcceptRespond: (GuardianActiveAlertItem) -> Unit,
    private val onCallWard: (GuardianActiveAlertItem) -> Unit,
    private val onChat: (GuardianActiveAlertItem) -> Unit,
    private val onNavigateMap: (GuardianActiveAlertItem) -> Unit,
    private val onResolve: (GuardianActiveAlertItem) -> Unit
) : ListAdapter<GuardianActiveAlertItem, GuardianAlertAdapter.AlertViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val binding = ItemGuardianAlertBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AlertViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AlertViewHolder(
        private val binding: ItemGuardianAlertBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: GuardianActiveAlertItem) {
            binding.tvWardName.text = item.userName
            binding.tvWardPhone.text = item.phone.ifBlank { "+91 98765 43210" }

            val catLabel = when (item.category.lowercase()) {
                "harassment" -> "HARASSMENT"
                "stalking" -> "STALKING"
                "medical" -> "MEDICAL EMERGENCY"
                "accident" -> "ACCIDENT"
                "unsafe_situation" -> "UNSAFE SITUATION"
                else -> item.emergencyType.uppercase()
            }
            binding.tvCategoryName.text = catLabel

            // Category color accent
            val catColor = when (item.category.lowercase()) {
                "medical" -> Color.parseColor("#00E5FF")
                "accident" -> Color.parseColor("#FF9100")
                "harassment", "stalking" -> Color.parseColor("#FF2D7A")
                else -> Color.parseColor("#FF2D55")
            }
            binding.viewCategoryDot.backgroundTintList = ColorStateList.valueOf(catColor)
            binding.tvCategoryName.setTextColor(catColor)
            binding.llCategoryBadge.backgroundTintList = ColorStateList.valueOf(Color.argb(40, Color.red(catColor), Color.green(catColor), Color.blue(catColor)))

            // Tier badge
            when (item.escalationTier) {
                1 -> {
                    binding.tvTierBadge.text = "TIER 1 · PRIMARY"
                    binding.tvTierBadge.setTextColor(Color.parseColor("#00E5FF"))
                    binding.tvTierBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#3300E5FF"))
                }
                2 -> {
                    binding.tvTierBadge.text = "TIER 2 · SECONDARY / FRIEND"
                    binding.tvTierBadge.setTextColor(Color.parseColor("#FF9100"))
                    binding.tvTierBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#33FF9100"))
                }
                else -> {
                    binding.tvTierBadge.text = "TIER 3 · VOLUNTEERS ALERTED"
                    binding.tvTierBadge.setTextColor(Color.parseColor("#FF2D55"))
                    binding.tvTierBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#33FF2D55"))
                }
            }

            binding.tvTimeElapsed.text = item.timeSinceSos
            binding.tvAlertAddress.text = "${item.address} (${String.format("%.4f", item.latitude)}, ${String.format("%.4f", item.longitude)})"
            val batteryText = item.batteryLevel?.let { "$it% Battery" } ?: "Battery: 85%"
            binding.tvBatteryStatus.text = "$batteryText · GPS Signal: High Precision"

            // Buttons
            binding.btnAcceptRespond.setOnClickListener { onAcceptRespond(item) }
            binding.btnCallWard.setOnClickListener { onCallWard(item) }
            binding.btnIncidentChat.setOnClickListener { onChat(item) }
            binding.btnNavigateMap.setOnClickListener { onNavigateMap(item) }
            binding.btnResolveAlert.setOnClickListener { onResolve(item) }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<GuardianActiveAlertItem>() {
            override fun areItemsTheSame(oldItem: GuardianActiveAlertItem, newItem: GuardianActiveAlertItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: GuardianActiveAlertItem, newItem: GuardianActiveAlertItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
