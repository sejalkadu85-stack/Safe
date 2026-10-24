package com.womensafety.ui.alerts

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.womensafety.databinding.ActivityHowItWorksBinding

class HowItWorksActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHowItWorksBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHowItWorksBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
        }
    }
}
