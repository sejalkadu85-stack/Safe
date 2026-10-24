package com.womensafety.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.womensafety.data.models.DangerZoneItem
import com.womensafety.databinding.ItemDangerZoneBinding

class DangerZoneAdapter(
    private var items: List<DangerZoneItem> = emptyList(),
    private val onItemClick: (DangerZoneItem) -> Unit
) : RecyclerView.Adapter<DangerZoneAdapter.ViewHolder>() {

    fun updateData(newItems: List<DangerZoneItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDangerZoneBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemDangerZoneBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(zone: DangerZoneItem) {
            binding.tvZoneTitle.text = zone.title
            binding.tvZoneDescription.text = zone.description

            val (severityText, colorHex) = when (zone.severity.lowercase()) {
                "high" -> Pair("HIGH RISK", "#FF5252")
                "low" -> Pair("LOW RISK", "#00E676")
                else -> Pair("MODERATE", "#FFD740")
            }

            binding.tvSeverityBadge.text = severityText
            binding.tvSeverityBadge.setTextColor(Color.parseColor(colorHex))
            binding.indicatorSeverity.backgroundTintList =
                android.content.res.ColorStateList.valueOf(Color.parseColor(colorHex))

            val catIcon = when (zone.category) {
                "poor_lighting" -> "💡"
                "isolated_area" -> "🏚️"
                "theft" -> "⚠️"
                "harassment_hotspot" -> "🚨"
                else -> "📍"
            }
            binding.tvZoneCategory.text = "$catIcon ${zone.categoryDisplay ?: zone.category.replace('_', ' ').capitalize()}"

            val verifiedStr = if (zone.verified) "Verified ✅" else "Crowdsourced"
            val distStr = if (zone.distanceMeters != null) " • ${zone.distanceMeters.toInt()}m" else ""
            binding.tvReportCount.text = "${zone.reportCount} reports • $verifiedStr$distStr"

            binding.root.setOnClickListener {
                onItemClick(zone)
            }
        }
    }
}
