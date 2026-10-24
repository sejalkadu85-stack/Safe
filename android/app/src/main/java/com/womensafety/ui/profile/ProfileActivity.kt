package com.womensafety.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.womensafety.R
import com.womensafety.data.api.ApiClient
import com.womensafety.databinding.ActivityProfileBinding
import com.womensafety.ui.NotificationSettingsActivity
import com.womensafety.ui.StealthDisguiseActivity
import com.womensafety.ui.auth.LoginActivity
import com.womensafety.ui.contacts.TrustedContactsActivity
import com.womensafety.utils.Constants
import com.womensafety.utils.SharedPrefManager
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var prefs: SharedPrefManager
    private var isEditMode = false

    private var originalFullName = ""
    private var originalPhone = ""
    private var originalEmail = ""

    // Universal image picker launcher
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleSelectedPhoto(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SharedPrefManager.getInstance(this)

        loadProfileFromPrefs()
        loadProfileFromApi()
        setupClickListeners()
    }

    private fun loadProfileFromPrefs() {
        val username = prefs.getUserName() ?: ""
        val savedFullName = prefs.getUserFullName() ?: username
        val email = prefs.getUserEmail() ?: ""
        val phone = prefs.getUserPhone() ?: ""
        val role = prefs.getUserRole() ?: "user"
        val photoUrl = prefs.getUserPhoto()

        originalFullName = savedFullName
        originalPhone = phone
        originalEmail = email

        binding.tvProfileName.text = savedFullName.ifEmpty { username }
        binding.tvProfileEmail.text = email
        binding.tvProfileRole.text = role.replaceFirstChar { it.uppercase() }
        binding.etFullName.setText(savedFullName.ifEmpty { username })
        binding.etPhoneNumber.setText(phone)
        binding.etEmail.setText(email)

        if (!photoUrl.isNullOrEmpty()) {
            loadPhotoIntoView(photoUrl)
        }
    }

    private fun loadProfileFromApi() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getService(this@ProfileActivity).getProfile()
                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!
                    val fullName = "${user.firstName ?: ""} ${user.lastName ?: ""}".trim()
                        .ifEmpty { user.username.ifEmpty { prefs.getUserName() ?: "" } }

                    originalFullName = fullName
                    originalPhone = user.phoneNumber ?: ""
                    originalEmail = user.email ?: ""

                    binding.tvProfileName.text = fullName
                    binding.tvProfileEmail.text = user.email ?: prefs.getUserEmail() ?: ""
                    binding.tvProfileRole.text = (user.role ?: "user").replaceFirstChar { it.uppercase() }
                    binding.etFullName.setText(fullName)
                    binding.etEmail.setText(user.email ?: "")
                    binding.etPhoneNumber.setText(user.phoneNumber ?: "")

                    prefs.saveUserFullName(fullName)
                    if (!user.phoneNumber.isNullOrEmpty()) {
                        prefs.saveUserPhone(user.phoneNumber)
                    }

                    if (!user.profilePhoto.isNullOrEmpty()) {
                        prefs.saveUserPhoto(user.profilePhoto)
                        loadPhotoIntoView(user.profilePhoto)
                    }
                }
            } catch (_: Exception) {
                // Keep cached data
            }
        }
    }

    private fun loadPhotoIntoView(url: String) {
        if (url.startsWith("content://") || url.startsWith("file://")) {
            Glide.with(this)
                .load(Uri.parse(url))
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .circleCrop()
                .into(binding.ivProfilePhoto)
            return
        }

        val fullUrl = if (url.startsWith("http")) {
            url
        } else {
            val base = Constants.BASE_URL.removeSuffix("/api/").removeSuffix("/")
            val path = if (url.startsWith("/")) url else "/$url"
            "$base$path"
        }

        Glide.with(this)
            .load(fullUrl)
            .placeholder(R.drawable.ic_profile)
            .error(R.drawable.ic_profile)
            .circleCrop()
            .into(binding.ivProfilePhoto)
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        // Top bar edit icon
        binding.btnEditProfile.setOnClickListener {
            toggleEditMode(!isEditMode)
        }

        // Section header edit toggle
        binding.btnEditToggle.setOnClickListener {
            toggleEditMode(!isEditMode)
        }

        // Save & cancel buttons
        binding.btnSaveProfile.setOnClickListener {
            saveProfile()
        }

        binding.btnCancelEdit.setOnClickListener {
            cancelEdit()
        }

        // Photo change triggers
        binding.btnChangePhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.ivProfilePhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.tvChangePhotoHint.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // Settings & Navigation
        binding.btnNotificationSettings.setOnClickListener {
            startActivity(Intent(this, NotificationSettingsActivity::class.java))
        }

        binding.btnStealthMode.setOnClickListener {
            startActivity(Intent(this, StealthDisguiseActivity::class.java))
        }

        binding.btnManageContacts.setOnClickListener {
            startActivity(Intent(this, TrustedContactsActivity::class.java))
        }

        binding.btnFaq.setOnClickListener {
            showFaqDialog()
        }

        binding.btnAbout.setOnClickListener {
            showAboutDialog()
        }

        binding.btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun toggleEditMode(edit: Boolean) {
        isEditMode = edit
        binding.etFullName.isEnabled = edit
        binding.etPhoneNumber.isEnabled = edit
        binding.etEmail.isEnabled = edit

        if (edit) {
            binding.etFullName.setBackgroundResource(R.drawable.bg_dark_input)
            binding.etPhoneNumber.setBackgroundResource(R.drawable.bg_dark_input)
            binding.etEmail.setBackgroundResource(R.drawable.bg_dark_input)

            val padH = (14 * resources.displayMetrics.density).toInt()
            val padV = (8 * resources.displayMetrics.density).toInt()
            binding.etFullName.setPadding(padH, padV, padH, padV)
            binding.etPhoneNumber.setPadding(padH, padV, padH, padV)
            binding.etEmail.setPadding(padH, padV, padH, padV)

            binding.layoutEditButtons.visibility = View.VISIBLE
            binding.btnEditToggle.text = "✖ Cancel"
            binding.btnEditProfile.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            binding.etFullName.requestFocus()
        } else {
            binding.etFullName.background = null
            binding.etPhoneNumber.background = null
            binding.etEmail.background = null

            binding.layoutEditButtons.visibility = View.GONE
            binding.btnEditToggle.text = "✏️ Edit Profile"
            binding.btnEditProfile.setImageResource(android.R.drawable.ic_menu_edit)
        }
    }

    private fun cancelEdit() {
        binding.etFullName.setText(originalFullName)
        binding.etPhoneNumber.setText(originalPhone)
        binding.etEmail.setText(originalEmail)
        toggleEditMode(false)
    }

    private fun handleSelectedPhoto(uri: Uri) {
        // Show immediate local preview
        Glide.with(this)
            .load(uri)
            .circleCrop()
            .into(binding.ivProfilePhoto)

        prefs.saveUserPhoto(uri.toString())
        Toast.makeText(this@ProfileActivity, "✅ Profile photo updated!", Toast.LENGTH_SHORT).show()

        val token = prefs.getAccessToken()
        if (token.isNullOrEmpty() || token.startsWith("demo_")) {
            return
        }

        binding.pbPhotoLoading.visibility = View.VISIBLE
        binding.ivProfilePhoto.alpha = 0.6f

        lifecycleScope.launch {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bytes = inputStream?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    val requestFile = bytes.toRequestBody("image/*".toMediaTypeOrNull(), 0, bytes.size)
                    val photoPart = MultipartBody.Part.createFormData(
                        "profile_photo",
                        "avatar_${System.currentTimeMillis()}.jpg",
                        requestFile
                    )

                    val response = ApiClient.getService(this@ProfileActivity).uploadProfilePhoto(photoPart)
                    if (response.isSuccessful) {
                        val photoUrl = response.body()?.get("profile_photo_url")
                        if (!photoUrl.isNullOrEmpty()) {
                            prefs.saveUserPhoto(photoUrl)
                            loadPhotoIntoView(photoUrl)
                        }
                    }
                }
            } catch (_: Exception) {
                // Keep local photo
            } finally {
                binding.pbPhotoLoading.visibility = View.GONE
                binding.ivProfilePhoto.alpha = 1.0f
            }
        }
    }

    private fun saveProfile() {
        val fullName = binding.etFullName.text.toString().trim()
        val phone = binding.etPhoneNumber.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()

        if (fullName.isEmpty()) {
            Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
            return
        }

        val parts = fullName.split(" ", limit = 2)
        val firstName = parts.getOrNull(0) ?: ""
        val lastName = parts.getOrNull(1) ?: ""

        // Always save locally first so changes are immediate
        prefs.saveUserFullName(fullName)
        if (phone.isNotEmpty()) prefs.saveUserPhone(phone)
        if (email.isNotEmpty()) {
            prefs.saveUserData(prefs.getUserId(), prefs.getUserName() ?: "", email, prefs.getUserRole() ?: "woman")
        }

        originalFullName = fullName
        originalPhone = phone
        originalEmail = email

        binding.tvProfileName.text = fullName
        if (email.isNotEmpty()) binding.tvProfileEmail.text = email

        val token = prefs.getAccessToken()
        val isDemo = token.isNullOrEmpty() || token.startsWith("demo_")

        if (isDemo) {
            Toast.makeText(this@ProfileActivity, "✅ Profile updated successfully!", Toast.LENGTH_SHORT).show()
            toggleEditMode(false)
            return
        }

        lifecycleScope.launch {
            try {
                binding.btnSaveProfile.isEnabled = false
                binding.btnSaveProfile.text = "Saving..."

                val body = mutableMapOf<String, Any?>(
                    "first_name" to firstName,
                    "last_name" to lastName,
                    "phone_number" to phone
                )
                if (email.isNotEmpty()) {
                    body["email"] = email
                }

                val response = ApiClient.getService(this@ProfileActivity).updateProfile(body)
                if (response.isSuccessful) {
                    Toast.makeText(this@ProfileActivity, "✅ Profile updated successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ProfileActivity, "✅ Profile saved locally", Toast.LENGTH_SHORT).show()
                }
                toggleEditMode(false)
            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, "✅ Profile saved locally", Toast.LENGTH_SHORT).show()
                toggleEditMode(false)
            } finally {
                binding.btnSaveProfile.isEnabled = true
                binding.btnSaveProfile.text = "💾 Save Changes"
            }
        }
    }

    private fun showFaqDialog() {
        AlertDialog.Builder(this)
            .setTitle("❓ Help & FAQ")
            .setMessage(
                "Q: How does SOS work?\n" +
                "A: Hold the SOS button for 2 seconds. A 5-second countdown starts, then alerts are sent to your trusted contacts.\n\n" +
                "Q: How do I add trusted contacts?\n" +
                "A: Go to Trusted Contacts from the home screen and search by phone number or email.\n\n" +
                "Q: What is Stealth Mode?\n" +
                "A: It disguises the app as a calculator. Enter a secret PIN to access SafeHer.\n\n" +
                "Q: How does the Check-In timer work?\n" +
                "A: Set a timer interval. If you don't check in when prompted, an SOS is auto-triggered.\n\n" +
                "For more help, contact: support@safeher.app"
            )
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("🛡️ About SafeHer")
            .setMessage(
                "SafeHer — Women's Safety App\n\nVersion: 1.0.0\n\n" +
                "SafeHer is a comprehensive personal safety platform designed to protect women through:\n\n" +
                "• One-tap SOS alerts to trusted contacts\n" +
                "• Real-time GPS location sharing\n" +
                "• AI-powered distress detection\n" +
                "• Secure in-app messaging\n" +
                "• Community volunteer network\n" +
                "• Safe journey tracking\n\n" +
                "Your safety is our priority. 💗\n\n" +
                "© 2026 SafeHer. All rights reserved."
            )
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Log Out")
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton("Log Out") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performLogout() {
        lifecycleScope.launch {
            try {
                val refreshToken = prefs.getRefreshToken()
                if (!refreshToken.isNullOrEmpty() && !refreshToken.startsWith("demo_")) {
                    ApiClient.getService(this@ProfileActivity).logout(mapOf("refresh" to refreshToken))
                }
            } catch (_: Exception) {}

            prefs.clear()
            val intent = Intent(this@ProfileActivity, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
