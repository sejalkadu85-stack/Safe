package com.womensafety.ui.alerts

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.womensafety.databinding.ActivitySafetyTipsBinding

class SafetyTipsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySafetyTipsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySafetyTipsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
        }
    }
}
