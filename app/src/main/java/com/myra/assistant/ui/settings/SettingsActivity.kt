package com.myra.assistant.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.myra.assistant.R
import com.myra.assistant.databinding.ActivitySettingsBinding
import com.myra.assistant.databinding.DialogAddPrimeContactBinding
import com.myra.assistant.model.PrimeContact
import com.myra.assistant.service.AccessibilityHelperService
import org.json.JSONArray

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: android.content.SharedPreferences

    private val models = listOf(
        "Native Audio (Human Voice)" to "models/gemini-2.5-flash-native-audio-preview-12-2025",
        "Flash Live (Fast)" to "models/gemini-2.0-flash-live-001",
        "Pro Audio Dialog" to "models/gemini-2.5-flash-preview-native-audio-dialog"
    )

    private val voices = listOf(
        "Aoede (Female)" to "Aoede",
        "Charon (Male)" to "Charon",
        "Kore (Female)" to "Kore",
        "Fenrir (Male)" to "Fenrir",
        "Puck (Male)" to "Puck",
        "Leda (Female)" to "Leda",
        "Orus (Male)" to "Orus",
        "Zephyr (Female)" to "Zephyr"
    )

    private val primeContacts = mutableListOf<PrimeContact>()
    private lateinit var primeAdapter: PrimeContactAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)

        binding.apiKeyInput.setText(prefs.getString("api_key", ""))
        binding.userNameInput.setText(prefs.getString("user_name", "Boss"))

        setupModelSpinner()
        setupVoiceSpinner()
        setupPersonalityGroup()
        setupPrimeContacts()
        refreshAccessibilityStatus()

        binding.accessibilityStatusText.setOnClickListener {
            startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.addPrimeContactBtn.setOnClickListener { showAddPrimeContactDialog() }

        binding.saveBtn.setOnClickListener { saveSettings() }
    }

    override fun onResume() {
        super.onResume()
        refreshAccessibilityStatus()
    }

    private fun setupModelSpinner() {
        val labels = models.map { it.first }
        binding.modelSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        val savedModel = prefs.getString("gemini_model", models[0].second)
        val idx = models.indexOfFirst { it.second == savedModel }.let { if (it >= 0) it else 0 }
        binding.modelSpinner.setSelection(idx)
    }

    private fun setupVoiceSpinner() {
        val labels = voices.map { it.first }
        binding.voiceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        val savedVoice = prefs.getString("gemini_voice", voices[0].second)
        val idx = voices.indexOfFirst { it.second == savedVoice }.let { if (it >= 0) it else 0 }
        binding.voiceSpinner.setSelection(idx)
    }

    private fun setupPersonalityGroup() {
        when (prefs.getString("personality_mode", "GF")) {
            "PROFESSIONAL" -> binding.radioProfessional.isChecked = true
            "ASSISTANT" -> binding.radioAssistant.isChecked = true
            else -> binding.radioGf.isChecked = true
        }
    }

    private fun setupPrimeContacts() {
        primeContacts.clear()
        primeContacts.addAll(loadPrimeContacts())
        primeAdapter = PrimeContactAdapter(primeContacts) { position ->
            primeContacts.removeAt(position)
            primeAdapter.notifyItemRemoved(position)
            savePrimeContacts()
        }
        binding.primeContactsRecycler.layoutManager = LinearLayoutManager(this)
        binding.primeContactsRecycler.adapter = primeAdapter
    }

    private fun loadPrimeContacts(): List<PrimeContact> {
        val json = prefs.getString("prime_contacts_json", null)
        if (json != null) {
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map {
                    val obj = arr.getJSONObject(it)
                    PrimeContact(obj.getString("name"), obj.getString("number"))
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
        // Legacy migration from single prime_name / prime_number
        val legacyName = prefs.getString("prime_name", null)
        val legacyNumber = prefs.getString("prime_number", null)
        return if (!legacyName.isNullOrBlank() && !legacyNumber.isNullOrBlank()) {
            listOf(PrimeContact(legacyName, legacyNumber))
        } else {
            emptyList()
        }
    }

    private fun savePrimeContacts() {
        val arr = JSONArray()
        primeContacts.forEach { contact ->
            val obj = org.json.JSONObject()
            obj.put("name", contact.name)
            obj.put("number", contact.number)
            arr.put(obj)
        }
        prefs.edit().putString("prime_contacts_json", arr.toString()).apply()
    }

    private fun showAddPrimeContactDialog() {
        val dialogBinding = DialogAddPrimeContactBinding.inflate(layoutInflater)
        AlertDialog.Builder(this)
            .setTitle("Add Prime Contact")
            .setView(dialogBinding.root)
            .setPositiveButton("Add") { _, _ ->
                val name = dialogBinding.dialogNameInput.text.toString().trim()
                val number = dialogBinding.dialogNumberInput.text.toString().trim()
                if (name.isNotEmpty() && number.isNotEmpty()) {
                    primeContacts.add(PrimeContact(name, number))
                    primeAdapter.notifyItemInserted(primeContacts.size - 1)
                    savePrimeContacts()
                } else {
                    Toast.makeText(this, "Name aur number dono zaroori hai", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshAccessibilityStatus() {
        val enabled = AccessibilityHelperService.isEnabled(this)
        binding.accessibilityStatusText.text = if (enabled) {
            "Accessibility: ENABLED ✅"
        } else {
            "Accessibility: DISABLED ❌ (tap to enable)"
        }
        binding.accessibilityStatusText.setTextColor(
            if (enabled) 0xFF00E676.toInt() else 0xFFFF1744.toInt()
        )
    }

    private fun saveSettings() {
        val selectedModel = models[binding.modelSpinner.selectedItemPosition].second
        val selectedVoice = voices[binding.voiceSpinner.selectedItemPosition].second
        val personality = when (binding.personalityGroup.checkedRadioButtonId) {
            R.id.radioProfessional -> "PROFESSIONAL"
            R.id.radioAssistant -> "ASSISTANT"
            else -> "GF"
        }

        prefs.edit()
            .putString("api_key", binding.apiKeyInput.text.toString().trim())
            .putString("user_name", binding.userNameInput.text.toString().trim())
            .putString("gemini_model", selectedModel)
            .putString("gemini_voice", selectedVoice)
            .putString("personality_mode", personality)
            .apply()
        savePrimeContacts()

        Toast.makeText(this, "Settings Saved! App restart required.", Toast.LENGTH_SHORT).show()
        finish()
    }
}
