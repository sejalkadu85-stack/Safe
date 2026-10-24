package com.womensafety.ui.alerts

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.womensafety.databinding.ActivityEmergencyNumbersBinding

class EmergencyNumbersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEmergencyNumbersBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmergencyNumbersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnCallPolice.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
        }

        binding.btnCallAmbulance.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:108")))
        }

        binding.btnCallFire.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:101")))
        }
    }
}
