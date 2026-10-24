package com.womensafety.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.womensafety.R
import com.womensafety.data.api.ApiClient
import com.womensafety.data.models.Helpline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HelplineDirectoryActivity : AppCompatActivity() {

    private lateinit var rvHelplines: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var etSearch: EditText
    private lateinit var adapter: HelplineAdapter

    private var allHelplines = mutableListOf<Helpline>()
    private var currentCategory = "all"

    // Reliable offline fallbacks in case network is offline
    private val offlineHelplines = listOf(
        Helpline(1, "National Emergency (ERSS)", "112", "emergency", "Unified 24x7 emergency response for Police, Fire, Ambulance", null, null, "India", true, true, 1),
        Helpline(2, "Women Helpline (Police)", "1091", "women", "Dedicated 24x7 toll-free emergency response for women in distress", null, null, "India", true, true, 2),
        Helpline(3, "Women in Distress (Ministry WCD)", "181", "women", "Support, rescue and referral services for domestic abuse and harassment", null, null, "India", true, true, 3),
        Helpline(4, "Police Emergency Control", "100", "police", "Direct local police station control room and PCR patrol dispatch", null, null, "India", true, true, 4),
        Helpline(5, "Emergency Medical Ambulance", "108", "medical", "Free 24/7 medical emergency ambulance service with trained EMTs", null, null, "India", true, true, 5),
        Helpline(6, "Women Power Line (Anti-Harassment)", "1090", "women", "Immediate protection from cyber stalking, harassment and obscene calls", null, null, "India", true, true, 6),
        Helpline(7, "National Cyber Crime Reporting", "1930", "cyber", "Citizen financial cyber fraud and cyber extortion helpline", null, null, "India", true, true, 7),
        Helpline(8, "Childline India", "1098", "child", "24/7 nationwide emergency phone outreach service for children", null, null, "India", true, true, 8),
        Helpline(9, "Maternal & Infant Ambulance", "102", "medical", "Free transport for pregnant mothers and neonates to nearest hospital", null, null, "India", true, true, 9),
        Helpline(10, "National Disaster Management (NDMA)", "1078", "emergency", "Disaster relief, flood rescue, and severe emergency coordination", null, null, "India", true, true, 10)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_helpline_directory)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        rvHelplines = findViewById(R.id.rvHelplines)
        progressBar = findViewById(R.id.progressBar)
        etSearch = findViewById(R.id.etSearch)

        adapter = HelplineAdapter(offlineHelplines) { helpline ->
            dialPhoneNumber(helpline.number)
        }
        rvHelplines.layoutManager = LinearLayoutManager(this)
        rvHelplines.adapter = adapter

        findViewById<ImageButton>(R.id.btnQuickDial112).setOnClickListener {
            dialPhoneNumber("112")
        }

        setupCategoryChips()
        setupSearch()
        loadHelplines()
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterList(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupCategoryChips() {
        val chipAll = findViewById<TextView>(R.id.chipAll)
        val chipWomen = findViewById<TextView>(R.id.chipWomen)
        val chipPolice = findViewById<TextView>(R.id.chipPolice)
        val chipMedical = findViewById<TextView>(R.id.chipMedical)
        val chipCyber = findViewById<TextView>(R.id.chipCyber)
        val chipChild = findViewById<TextView>(R.id.chipChild)

        val chips = listOf(
            chipAll to "all",
            chipWomen to "women",
            chipPolice to "police",
            chipMedical to "medical",
            chipCyber to "cyber",
            chipChild to "child"
        )

        for ((chip, cat) in chips) {
            chip.setOnClickListener {
                currentCategory = cat
                // Update visuals
                for ((c, _) in chips) {
                    if (c == chip) {
                        c.setBackgroundResource(R.drawable.bg_role_chip_selected)
                        c.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                    } else {
                        c.setBackgroundResource(R.drawable.bg_role_chip_unselected)
                        c.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                    }
                }
                filterList(etSearch.text.toString())
            }
        }
    }

    private fun filterList(query: String) {
        val cleanQuery = query.trim().lowercase()
        val baseList = if (allHelplines.isNotEmpty()) allHelplines else offlineHelplines

        val filtered = baseList.filter { item ->
            val matchCategory = if (currentCategory == "all") true else item.category.equals(currentCategory, ignoreCase = true)
            val matchQuery = if (cleanQuery.isEmpty()) true else (
                item.name.lowercase().contains(cleanQuery) ||
                item.number.contains(cleanQuery) ||
                (item.description ?: "").lowercase().contains(cleanQuery)
            )
            matchCategory && matchQuery
        }
        adapter.updateList(filtered)
    }

    private fun loadHelplines() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val api = ApiClient.getService(this@HelplineDirectoryActivity)
                val res = api.getHelplines()
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (res.isSuccessful && res.body()?.success == true) {
                        val list = res.body()!!.helplines
                        if (list.isNotEmpty()) {
                            allHelplines.clear()
                            allHelplines.addAll(list)
                            filterList(etSearch.text.toString())
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    // Keep using offlineHelplines seamlessly
                }
            }
        }
    }

    private fun dialPhoneNumber(number: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${number.trim()}")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open dialer for $number", Toast.LENGTH_SHORT).show()
        }
    }
}
