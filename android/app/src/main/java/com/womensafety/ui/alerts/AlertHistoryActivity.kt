package com.womensafety.ui.alerts

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.womensafety.R
import com.womensafety.databinding.ActivityAlertHistoryBinding

class AlertHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlertHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlertHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.tabActive.setOnClickListener {
            binding.tabActive.setBackgroundResource(R.drawable.bg_tab_active)
            binding.tabActive.setTextColor(getColor(R.color.white))
            binding.tabResolved.setBackgroundResource(R.drawable.bg_tab_inactive)
            binding.tabResolved.setTextColor(getColor(R.color.text_secondary))

            binding.cardIncident1.visibility = View.VISIBLE
            binding.cardIncident2.visibility = View.GONE
            binding.cardIncident3.visibility = View.GONE
            binding.cardIncident4.visibility = View.GONE
        }

        binding.tabResolved.setOnClickListener {
            binding.tabResolved.setBackgroundResource(R.drawable.bg_tab_active)
            binding.tabResolved.setTextColor(getColor(R.color.white))
            binding.tabActive.setBackgroundResource(R.drawable.bg_tab_inactive)
            binding.tabActive.setTextColor(getColor(R.color.text_secondary))

            binding.cardIncident1.visibility = View.GONE
            binding.cardIncident2.visibility = View.VISIBLE
            binding.cardIncident3.visibility = View.VISIBLE
            binding.cardIncident4.visibility = View.VISIBLE
        }

        // Tapping active incident opens AlertSentActivity
        binding.cardIncident1.setOnClickListener {
            startActivity(Intent(this, AlertSentActivity::class.java))
        }
    }
}
