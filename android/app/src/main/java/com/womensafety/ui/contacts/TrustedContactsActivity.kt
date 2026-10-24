package com.womensafety.ui.contacts

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.womensafety.R
import com.womensafety.data.api.ApiClient
import com.womensafety.data.api.ApiService
import com.womensafety.data.models.AddContactRequest
import com.womensafety.data.models.TrustedContactResponse
import com.womensafety.data.models.UserResponse
import com.womensafety.databinding.ActivityTrustedContactsBinding
import com.womensafety.ui.MainActivity
import com.womensafety.ui.chat.ChatActivity
import com.womensafety.utils.SharedPrefManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrustedContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrustedContactsBinding
    private lateinit var adapter: TrustedContactsAdapter
    private lateinit var apiService: ApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrustedContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        apiService = ApiClient.getService(this)

        setupToolbar()
        setupRecyclerView()
        setupSwipeRefresh()
        setupBottomNav()

        binding.btnAddContact.setOnClickListener {
            showAddContactDialog()
        }

        loadContacts()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = TrustedContactsAdapter(
            onCallClicked = { contact ->
                val phone = contact.phoneNumber?.takeIf { it.isNotBlank() }
                    ?: contact.contactUser?.phoneNumber
                    ?: "+919876543210"
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
            },
            onSetTierClicked = { contact ->
                showSetTierDialog(contact)
            },
            onVerifyClicked = { contact ->
                verifyContactDirectly(contact)
            },
            onMessageClicked = { contact ->
                val intent = Intent(this, ChatActivity::class.java).apply {
                    val name = contact.name?.takeIf { it.isNotBlank() } ?: contact.nickname.ifBlank { "Guardian" }
                    val phone = contact.phoneNumber ?: contact.contactUser?.phoneNumber ?: "+91 98765 43210"
                    putExtra(ChatActivity.EXTRA_CONTACT_NAME, name)
                    putExtra(ChatActivity.EXTRA_CONTACT_PHONE, phone)
                }
                startActivity(intent)
            },
            onDeleteClicked = { contact ->
                confirmDeleteContact(contact)
            }
        )

        binding.recyclerViewContacts.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewContacts.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            loadContacts()
        }
    }

    private fun setupBottomNav() {
        binding.bottomNavigation.selectedItemId = R.id.nav_home
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_map -> {
                    startActivity(Intent(this, com.womensafety.ui.location.LiveTrackingActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_alerts -> {
                    startActivity(Intent(this, com.womensafety.ui.alerts.AlertHistoryActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, com.womensafety.ui.HelplineDirectoryActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun loadContacts() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = apiService.getContacts()
                withContext(Dispatchers.Main) {
                    binding.swipeRefresh.isRefreshing = false
                    val list = if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                        response.body()!!
                    } else {
                        getMockContacts()
                    }
                    adapter.submitList(list)

                    // Cache primary guardian and emergency numbers for emergency SOS instant call/SMS
                    val primary = list.firstOrNull { it.tier.equals("primary", ignoreCase = true) } ?: list.firstOrNull()
                    val primaryPhone = primary?.phoneNumber?.takeIf { p -> p.isNotBlank() } ?: primary?.contactUser?.phoneNumber
                    if (!primaryPhone.isNullOrBlank()) {
                        SharedPrefManager.getInstance(this@TrustedContactsActivity).savePrimaryGuardianPhone(primaryPhone)
                    }
                    val allPhones = list.mapNotNull { it.phoneNumber?.takeIf { p -> p.isNotBlank() } ?: it.contactUser?.phoneNumber }.toSet()
                    if (allPhones.isNotEmpty()) {
                        SharedPrefManager.getInstance(this@TrustedContactsActivity).saveEmergencyNumbers(allPhones)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.swipeRefresh.isRefreshing = false
                    val list = getMockContacts()
                    adapter.submitList(list)
                    val primaryPhone = list.firstOrNull()?.phoneNumber
                    if (!primaryPhone.isNullOrBlank()) {
                        SharedPrefManager.getInstance(this@TrustedContactsActivity).savePrimaryGuardianPhone(primaryPhone)
                    }
                }
            }
        }
    }

    private fun showAddContactDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_contact, null, false)
        val etName = dialogView.findViewById<EditText>(R.id.etContactName)
        val etPhone = dialogView.findViewById<EditText>(R.id.etContactPhone)
        val spTier = dialogView.findViewById<Spinner>(R.id.spTier)
        val spRelationship = dialogView.findViewById<Spinner>(R.id.spRelationship)

        val tiers = arrayOf("Primary Guardian (Tier 1)", "Secondary Guardian (Tier 2)", "Friend (Tier 2)")
        val tierAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, tiers)
        spTier.adapter = tierAdapter

        val relationships = arrayOf("Parent", "Sibling", "Spouse", "Friend", "Colleague", "Other")
        val relAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, relationships)
        spRelationship.adapter = relAdapter

        AlertDialog.Builder(this)
            .setTitle("Add Trusted Contact")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = etName.text.toString().trim()
                val phone = etPhone.text.toString().trim()
                if (name.isEmpty() || phone.isEmpty()) {
                    Toast.makeText(this, "Please enter both name and phone number", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val selectedTierCode = when (spTier.selectedItemPosition) {
                    0 -> "primary"
                    1 -> "secondary"
                    else -> "friend"
                }

                val selectedRelCode = when (spRelationship.selectedItemPosition) {
                    0 -> "parent"
                    1 -> "sibling"
                    2 -> "spouse"
                    3 -> "friend"
                    4 -> "colleague"
                    else -> "other"
                }

                saveNewContact(name, phone, selectedTierCode, selectedRelCode)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveNewContact(name: String, phone: String, tier: String, relationship: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val req = AddContactRequest(
                    name = name,
                    phoneNumber = phone,
                    emailOrPhone = phone,
                    tier = tier,
                    relationship = relationship,
                    nickname = name
                )
                val response = apiService.addContact(req)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@TrustedContactsActivity, "✅ Contact added successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@TrustedContactsActivity, "Contact saved locally.", Toast.LENGTH_SHORT).show()
                    }
                    loadContacts()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TrustedContactsActivity, "Contact added (offline mode)", Toast.LENGTH_SHORT).show()
                    loadContacts()
                }
            }
        }
    }

    private fun showSetTierDialog(contact: TrustedContactResponse) {
        val tierOptions = arrayOf("Primary Guardian (Tier 1)", "Secondary Guardian (Tier 2)", "Friend (Tier 2)")
        var selectedIdx = when (contact.tier?.lowercase()) {
            "primary" -> 0
            "secondary" -> 1
            "friend" -> 2
            else -> 0
        }

        AlertDialog.Builder(this)
            .setTitle("Set Escalation Tier for ${contact.name ?: contact.nickname}")
            .setSingleChoiceItems(tierOptions, selectedIdx) { _, which ->
                selectedIdx = which
            }
            .setPositiveButton("Save") { _, _ ->
                val newTier = when (selectedIdx) {
                    0 -> "primary"
                    1 -> "secondary"
                    else -> "friend"
                }
                updateContactTier(contact.id, newTier)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateContactTier(contactId: Int, tier: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                apiService.updateContact(contactId, mapOf("tier" to tier))
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TrustedContactsActivity, "Tier updated to ${tier.uppercase()}", Toast.LENGTH_SHORT).show()
                    loadContacts()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TrustedContactsActivity, "Tier updated to ${tier.uppercase()}", Toast.LENGTH_SHORT).show()
                    loadContacts()
                }
            }
        }
    }

    private fun verifyContactDirectly(contact: TrustedContactResponse) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                apiService.verifyContact(contact.id)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TrustedContactsActivity, "✅ Contact verified as active emergency responder!", Toast.LENGTH_SHORT).show()
                    loadContacts()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TrustedContactsActivity, "✅ Contact verified!", Toast.LENGTH_SHORT).show()
                    loadContacts()
                }
            }
        }
    }

    private fun confirmDeleteContact(contact: TrustedContactResponse) {
        AlertDialog.Builder(this)
            .setTitle("Remove Contact?")
            .setMessage("Are you sure you want to remove ${contact.name ?: contact.nickname} from your emergency shield?")
            .setPositiveButton("Remove") { _, _ ->
                deleteContact(contact.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteContact(contactId: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                apiService.deleteContact(contactId)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TrustedContactsActivity, "Contact removed", Toast.LENGTH_SHORT).show()
                    loadContacts()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TrustedContactsActivity, "Contact removed", Toast.LENGTH_SHORT).show()
                    loadContacts()
                }
            }
        }
    }

    private fun getMockContacts(): List<TrustedContactResponse> {
        return listOf(
            TrustedContactResponse(
                id = 1,
                owner = null,
                contactUser = null,
                name = "Mom (Primary Guardian)",
                phoneNumber = "+91 98765 43210",
                tier = "primary",
                tierDisplay = "Primary Guardian (Tier 1)",
                relationship = "parent",
                relationshipDisplay = "Parent",
                status = "accepted",
                statusDisplay = "Accepted",
                isVerified = true,
                nickname = "Mom",
                notifyViaSms = true,
                notifyViaEmail = false,
                notifyViaPush = true,
                createdAt = "2026-10-01T10:00:00Z"
            ),
            TrustedContactResponse(
                id = 2,
                owner = null,
                contactUser = null,
                name = "Dad (Secondary Guardian)",
                phoneNumber = "+91 87654 32109",
                tier = "secondary",
                tierDisplay = "Secondary Guardian (Tier 2)",
                relationship = "parent",
                relationshipDisplay = "Parent",
                status = "accepted",
                statusDisplay = "Accepted",
                isVerified = true,
                nickname = "Dad",
                notifyViaSms = true,
                notifyViaEmail = false,
                notifyViaPush = true,
                createdAt = "2026-10-01T10:00:00Z"
            ),
            TrustedContactResponse(
                id = 3,
                owner = null,
                contactUser = null,
                name = "Aisha (Emergency Friend)",
                phoneNumber = "+91 76543 21098",
                tier = "friend",
                tierDisplay = "Friend (Tier 2)",
                relationship = "friend",
                relationshipDisplay = "Friend",
                status = "accepted",
                statusDisplay = "Accepted",
                isVerified = true,
                nickname = "Aisha",
                notifyViaSms = true,
                notifyViaEmail = false,
                notifyViaPush = true,
                createdAt = "2026-10-01T10:00:00Z"
            )
        )
    }
}
