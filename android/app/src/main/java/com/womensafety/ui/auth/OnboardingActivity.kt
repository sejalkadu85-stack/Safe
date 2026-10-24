package com.womensafety.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.womensafety.R
import com.womensafety.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    data class OnboardingItem(
        val title: String,
        val description: String,
        val imageRes: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pages = listOf(
            OnboardingItem(
                "Emergency SOS",
                "Press and hold SOS to get immediate help, share your location and alert your trusted contacts.",
                R.drawable.img_onboard_sos
            ),
            OnboardingItem(
                "Live Location Sharing",
                "Your real-time location helps trusted contacts and responders reach you faster.",
                R.drawable.img_onboard_location
            ),
            OnboardingItem(
                "Trusted Contacts &\nCommunity Response",
                "Get support from your trusted contacts, nearby volunteers and local responders.",
                R.drawable.img_onboard_community
            )
        )

        binding.viewPager.adapter = OnboardingAdapter(pages)

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateIndicators(position)
                if (position == pages.size - 1) {
                    binding.btnAction.text = "Get Started"
                } else {
                    binding.btnAction.text = "Next"
                }
            }
        })

        binding.btnAction.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current < pages.size - 1) {
                binding.viewPager.setCurrentItem(current + 1, true)
            } else {
                finishOnboarding()
            }
        }

        binding.btnSkip.setOnClickListener {
            finishOnboarding()
        }
    }

    private fun updateIndicators(position: Int) {
        val indicators = listOf(binding.indicator0, binding.indicator1, binding.indicator2)
        for (i in indicators.indices) {
            if (i == position) {
                indicators[i].setBackgroundResource(R.drawable.bg_dot_active)
                indicators[i].layoutParams.width = (24 * resources.displayMetrics.density).toInt()
            } else {
                indicators[i].setBackgroundResource(R.drawable.bg_dot_inactive)
                indicators[i].layoutParams.width = (8 * resources.displayMetrics.density).toInt()
            }
            indicators[i].requestLayout()
        }
    }

    private fun finishOnboarding() {
        startActivity(Intent(this, LoginActivity::class.java))
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        finish()
    }

    private class OnboardingAdapter(private val items: List<OnboardingItem>) :
        RecyclerView.Adapter<OnboardingAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivGraphic: ImageView = view.findViewById(R.id.ivGraphic)
            val tvTitle: TextView = view.findViewById(R.id.tvTitle)
            val tvDescription: TextView = view.findViewById(R.id.tvDescription)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_onboarding_page, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.ivGraphic.setImageResource(item.imageRes)
            holder.tvTitle.text = item.title
            holder.tvDescription.text = item.description
        }

        override fun getItemCount() = items.size
    }
}
