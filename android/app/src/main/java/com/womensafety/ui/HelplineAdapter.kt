package com.womensafety.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.womensafety.R
import com.womensafety.data.models.Helpline

class HelplineAdapter(
    private var helplines: List<Helpline>,
    private val onCallClick: (Helpline) -> Unit
) : RecyclerView.Adapter<HelplineAdapter.HelplineViewHolder>() {

    fun updateList(newList: List<Helpline>) {
        helplines = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HelplineViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_helpline, parent, false)
        return HelplineViewHolder(view)
    }

    override fun onBindViewHolder(holder: HelplineViewHolder, position: Int) {
        holder.bind(helplines[position])
    }

    override fun getItemCount(): Int = helplines.size

    inner class HelplineViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivCategoryIcon)
        private val tvName: TextView = itemView.findViewById(R.id.tvHelplineName)
        private val tvCategoryBadge: TextView = itemView.findViewById(R.id.tvCategoryBadge)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        private val tvPhoneNumber: TextView = itemView.findViewById(R.id.tvPhoneNumber)
        private val btnCall: MaterialButton = itemView.findViewById(R.id.btnCall)

        fun bind(item: Helpline) {
            tvName.text = item.name
            tvPhoneNumber.text = item.number
            tvDescription.text = item.description ?: "Emergency helpline service."
            tvCategoryBadge.text = item.category.uppercase()
            btnCall.text = "Call ${item.number}"

            // Icon and color mapping
            val context = itemView.context
            when (item.category.lowercase()) {
                "police" -> {
                    ivIcon.setImageResource(R.drawable.ic_police_neon)
                    ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.neon_cyan))
                    tvCategoryBadge.setTextColor(ContextCompat.getColor(context, R.color.neon_cyan))
                }
                "medical" -> {
                    ivIcon.setImageResource(R.drawable.ic_ambulance_neon)
                    ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.neon_green))
                    tvCategoryBadge.setTextColor(ContextCompat.getColor(context, R.color.neon_green))
                }
                "women" -> {
                    ivIcon.setImageResource(R.drawable.ic_phone_neon)
                    ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.neon_pink))
                    tvCategoryBadge.setTextColor(ContextCompat.getColor(context, R.color.neon_pink))
                }
                "emergency" -> {
                    ivIcon.setImageResource(R.drawable.ic_fire_neon)
                    ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.sos_red))
                    tvCategoryBadge.setTextColor(ContextCompat.getColor(context, R.color.sos_red))
                }
                "cyber" -> {
                    ivIcon.setImageResource(R.drawable.ic_pin_neon)
                    ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.neon_purple))
                    tvCategoryBadge.setTextColor(ContextCompat.getColor(context, R.color.neon_purple))
                }
                else -> {
                    ivIcon.setImageResource(R.drawable.ic_phone_neon)
                    ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.neon_cyan))
                    tvCategoryBadge.setTextColor(ContextCompat.getColor(context, R.color.neon_cyan))
                }
            }

            btnCall.setOnClickListener { onCallClick(item) }
            itemView.setOnClickListener { onCallClick(item) }
        }
    }
}
