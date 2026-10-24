package com.womensafety.ui.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.womensafety.R
import com.womensafety.data.models.RegisterRequest
import com.womensafety.data.repository.AuthRepository
import com.womensafety.databinding.ActivitySignUpBinding
import com.womensafety.utils.Constants
import com.womensafety.utils.Resource
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignUpBinding
    private lateinit var authRepository: AuthRepository
    private var selectedRole: String = Constants.ROLE_WOMAN
    private var isPasswordVisible: Boolean = false
    private var selectedIdProofFile: File? = null

    private val idProofTypes = listOf(
        "Aadhaar Card" to "aadhaar",
        "Driving License" to "driving_license",
        "Voter ID" to "voter_id",
        "Passport" to "passport",
        "Other Govt ID" to "other_govt_id"
    )

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleSelectedFileUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authRepository = AuthRepository(this)
        selectedRole = intent.getStringExtra(RoleSelectionActivity.EXTRA_ROLE) ?: Constants.ROLE_WOMAN

        setupRoleSelectors()
        setupIdProofSpinner()
        setupListeners()
    }

    private fun setupRoleSelectors() {
        selectRole(selectedRole)

        binding.chipRoleUser.setOnClickListener { selectRole(Constants.ROLE_WOMAN) }
        binding.chipRoleGuardian.setOnClickListener { selectRole(Constants.ROLE_GUARDIAN) }
        binding.chipRoleVolunteer.setOnClickListener { selectRole(Constants.ROLE_VOLUNTEER) }
        binding.chipRoleAdmin.setOnClickListener { selectRole(Constants.ROLE_ADMIN) }
    }

    private fun setupIdProofSpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            idProofTypes.map { it.first }
        )
        binding.spinnerIdProofType.adapter = adapter
    }

    private fun selectRole(role: String) {
        selectedRole = role

        fun updateChip(
            chip: LinearLayout,
            icon: ImageView,
            text: TextView,
            isSelected: Boolean,
            accentColor: Int
        ) {
            if (isSelected) {
                chip.setBackgroundResource(R.drawable.bg_role_chip_selected)
                icon.setColorFilter(getColor(accentColor))
                text.setTextColor(getColor(R.color.white))
                text.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                chip.setBackgroundResource(R.drawable.bg_role_chip_unselected)
                icon.setColorFilter(getColor(R.color.text_secondary))
                text.setTextColor(getColor(R.color.text_secondary))
                text.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
        }

        updateChip(binding.chipRoleUser, binding.ivRoleUser, binding.tvRoleUser, role == Constants.ROLE_WOMAN, R.color.neon_pink)
        updateChip(binding.chipRoleGuardian, binding.ivRoleGuardian, binding.tvRoleGuardian, role == Constants.ROLE_GUARDIAN, R.color.neon_cyan)
        updateChip(binding.chipRoleVolunteer, binding.ivRoleVolunteer, binding.tvRoleVolunteer, role == Constants.ROLE_VOLUNTEER, R.color.neon_orange)
        updateChip(binding.chipRoleAdmin, binding.ivRoleAdmin, binding.tvRoleAdmin, role == Constants.ROLE_ADMIN, R.color.neon_purple)

        // Only Volunteers require government ID proof verification
        binding.llIdProofSection.visibility = if (role == Constants.ROLE_VOLUNTEER) View.VISIBLE else View.GONE
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.tvLoginLink.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            finish()
        }

        // Password visibility toggle
        binding.ivPasswordToggle.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                binding.etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                binding.ivPasswordToggle.setImageResource(R.drawable.ic_visibility)
                binding.ivPasswordToggle.setColorFilter(getColor(R.color.primary))
            } else {
                binding.etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                binding.ivPasswordToggle.setImageResource(R.drawable.ic_visibility_off)
                binding.ivPasswordToggle.setColorFilter(getColor(R.color.text_secondary))
            }
            binding.etPassword.setSelection(binding.etPassword.text.length)
        }

        // File picker for ID Proof
        binding.btnPickIdDocument.setOnClickListener {
            filePickerLauncher.launch("*/*")
        }

        binding.btnSignUp.setOnClickListener {
            attemptRegister()
        }
    }

    private fun handleSelectedFileUri(uri: Uri) {
        try {
            var fileName = "id_proof_${System.currentTimeMillis()}"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }

            val cacheFile = File(cacheDir, fileName)
            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(cacheFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            selectedIdProofFile = cacheFile
            binding.tvUploadedFileName.text = fileName
            val sizeKb = (cacheFile.length() / 1024).coerceAtLeast(1)
            binding.tvUploadStatus.text = "Document attached ($sizeKb KB)"
            binding.tvUploadStatus.setTextColor(getColor(R.color.neon_cyan))
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load document: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun attemptRegister() {
        val fullName = binding.etFullName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (fullName.isEmpty()) {
            binding.etFullName.error = "Please enter your name"
            binding.etFullName.requestFocus()
            return
        }

        if (phone.isEmpty()) {
            binding.etPhone.error = "Please enter mobile number"
            binding.etPhone.requestFocus()
            return
        }

        if (email.isEmpty()) {
            binding.etEmail.error = "Please enter email address"
            binding.etEmail.requestFocus()
            return
        }

        if (password.length < 6) {
            binding.etPassword.error = "Password must be at least 6 characters"
            binding.etPassword.requestFocus()
            return
        }

        // Volunteer ID proof validation
        if (selectedRole == Constants.ROLE_VOLUNTEER && selectedIdProofFile == null) {
            Toast.makeText(
                this,
                "Please upload a Government ID proof (Aadhaar/License/Voter ID) for volunteer verification.",
                Toast.LENGTH_LONG
            ).show()
            binding.btnPickIdDocument.requestFocus()
            return
        }

        val nameParts = fullName.split(" ", limit = 2)
        val firstName = nameParts.getOrNull(0) ?: fullName
        val lastName = nameParts.getOrNull(1) ?: ""
        val username = if (email.contains("@")) email.substringBefore("@") + "_" + (100..999).random() else "user_" + (1000..9999).random()

        val fullPhoneNumber = if (phone.startsWith("+91")) phone else "+91$phone"
        val selectedIdType = if (selectedRole == Constants.ROLE_VOLUNTEER) {
            idProofTypes.getOrNull(binding.spinnerIdProofType.selectedItemPosition)?.second ?: "aadhaar"
        } else null

        val request = RegisterRequest(
            username = username,
            email = email,
            phoneNumber = fullPhoneNumber,
            firstName = firstName,
            lastName = lastName,
            role = selectedRole,
            password = password,
            confirmPassword = password,
            idProofType = selectedIdType
        )

        lifecycleScope.launch {
            setLoading(true)
            val result = authRepository.register(
                request = request,
                idProofFile = if (selectedRole == Constants.ROLE_VOLUNTEER) selectedIdProofFile else null
            )
            setLoading(false)

            when (result) {
                is Resource.Success -> {
                    if (selectedRole == Constants.ROLE_VOLUNTEER) {
                        MaterialAlertDialogBuilder(this@SignUpActivity)
                            .setTitle("ID Verification Pending")
                            .setMessage("Your volunteer registration and government ID proof have been submitted for admin verification.\n\nYou will be able to log in once your ID proof has been verified and approved by the admin.")
                            .setPositiveButton("Go to Login") { _, _ ->
                                val intent = Intent(this@SignUpActivity, LoginActivity::class.java)
                                startActivity(intent)
                                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                                finish()
                            }
                            .setCancelable(false)
                            .show()
                    } else {
                        Toast.makeText(this@SignUpActivity, "Account created successfully!", Toast.LENGTH_SHORT).show()
                        val otpIntent = Intent(this@SignUpActivity, OtpVerificationActivity::class.java).apply {
                            putExtra(OtpVerificationActivity.EXTRA_PHONE_OR_EMAIL, fullPhoneNumber)
                        }
                        startActivity(otpIntent)
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                        finish()
                    }
                }
                is Resource.Error -> {
                    val errorMessage = result.message ?: "Registration failed."
                    Toast.makeText(this@SignUpActivity, errorMessage, Toast.LENGTH_LONG).show()

                    if (errorMessage.contains("already exists", ignoreCase = true)) {
                        MaterialAlertDialogBuilder(this@SignUpActivity)
                            .setTitle("Account Already Exists")
                            .setMessage("$errorMessage\n\nWould you like to log in instead?")
                            .setPositiveButton("Log In") { _, _ ->
                                val intent = Intent(this@SignUpActivity, LoginActivity::class.java)
                                startActivity(intent)
                                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                                finish()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
                is Resource.Loading -> {
                    setLoading(true)
                }
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSignUp.isEnabled = !isLoading
    }
}

