package com.womensafety.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.womensafety.databinding.ActivityRoleSelectionBinding
import com.womensafety.utils.Constants

class RoleSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRoleSelectionBinding

    companion object {
        const val EXTRA_ROLE = "extra_selected_role"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoleSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.cardRoleUser.setOnClickListener {
            proceedWithRole(Constants.ROLE_WOMAN)
        }

        binding.cardRoleGuardian.setOnClickListener {
            proceedWithRole(Constants.ROLE_GUARDIAN)
        }

        binding.cardRoleVolunteer.setOnClickListener {
            proceedWithRole(Constants.ROLE_VOLUNTEER)
        }

        binding.cardRoleAdmin.setOnClickListener {
            proceedWithRole(Constants.ROLE_ADMIN)
        }
    }

    private fun proceedWithRole(role: String) {
        val intent = Intent(this, SignUpActivity::class.java).apply {
            putExtra(EXTRA_ROLE, role)
        }
        startActivity(intent)
    }
}
