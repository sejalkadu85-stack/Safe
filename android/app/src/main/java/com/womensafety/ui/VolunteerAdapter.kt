package com.womensafety.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.womensafety.R
import com.womensafety.data.models.Volunteer

class VolunteerAdapter(
    private var volunteers: List<Volunteer>,
    private val onRequestHelp: (Volunteer) -> Unit,
    private val onCall: (Volunteer) -> Unit
) : RecyclerView.Adapter<VolunteerAdapter.VolunteerViewHolder>() {

    fun updateList(newList: List<Volunteer>) {
        volunteers = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VolunteerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_volunteer, parent, false)
        return VolunteerViewHolder(view)
    }

    override fun onBindViewHolder(holder: VolunteerViewHolder, position: Int) {
        holder.bind(volunteers[position])
    }

    override fun getItemCount(): Int = volunteers.size

    inner class VolunteerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvVolunteerName)
        private val ivVerified: ImageView = itemView.findViewById(R.id.ivVerifiedBadge)
        private val tvVerifiedText: TextView = itemView.findViewById(R.id.tvVerifiedText)
        private val tvRating: TextView = itemView.findViewById(R.id.tvRating)
        private val tvHelpsCount: TextView = itemView.findViewById(R.id.tvHelpsCount)
        private val tvDistance: TextView = itemView.findViewById(R.id.tvDistance)
        private val tvSkills: TextView = itemView.findViewById(R.id.tvSkills)
        private val btnCall: MaterialButton = itemView.findViewById(R.id.btnCallVolunteer)
        private val btnRequest: MaterialButton = itemView.findViewById(R.id.btnRequestHelp)

        fun bind(volunteer: Volunteer) {
            val name = volunteer.user?.fullName ?: volunteer.user?.username ?: "Community Volunteer"
            tvName.text = name

            if (volunteer.isVerified) {
                ivVerified.visibility = View.VISIBLE
                tvVerifiedText.visibility = View.VISIBLE
            } else {
                ivVerified.visibility = View.GONE
                tvVerifiedText.visibility = View.GONE
            }

            tvRating.text = "⭐ ${volunteer.rating}"
            tvHelpsCount.text = "• ${volunteer.totalHelps} assists"

            // Distance formatting
            val dist = volunteer.distanceMeters
            if (dist != null) {
                tvDistance.visibility = View.VISIBLE
                if (dist >= 1000) {
                    tvDistance.text = "• %.1f km away".format(dist / 1000.0)
                } else {
                    tvDistance.text = "• ${dist.toInt()} m away"
                }
            } else {
                tvDistance.visibility = View.GONE
            }

            tvSkills.text = volunteer.skills ?: "First Aid • Self Defense • Quick Response"

            val phone = volunteer.phoneNumber ?: volunteer.user?.phoneNumber
            if (phone.isNullOrEmpty()) {
                btnCall.visibility = View.GONE
            } else {
                btnCall.visibility = View.VISIBLE
                btnCall.setOnClickListener { onCall(volunteer) }
            }

            btnRequest.setOnClickListener { onRequestHelp(volunteer) }
        }
    }
}
