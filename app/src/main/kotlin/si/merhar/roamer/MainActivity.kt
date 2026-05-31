package si.merhar.roamer

import android.app.role.RoleManager
import android.os.Bundle
import android.telephony.TelephonyManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Main settings screen. Shows roaming status, enable/disable toggle,
 * manual country override, and recent rewrite log.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesRepository

    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* Role granted or denied — UI will reflect via status text */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PreferencesRepository(this)

        setupRoleRequest()
        setupToggle()
        setupCountryOverride()
        setupStatus()
        setupLog()
    }

    private fun setupRoleRequest() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_REDIRECTION)) {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_REDIRECTION)
            roleRequestLauncher.launch(intent)
        }
    }

    private fun setupToggle() {
        val toggle = findViewById<MaterialSwitch>(R.id.switch_enabled)
        prefs.enabled.onEach { enabled ->
            toggle.isChecked = enabled
        }.launchIn(lifecycleScope)

        toggle.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { prefs.setEnabled(isChecked) }
        }
    }

    private fun setupCountryOverride() {
        val dropdown = findViewById<AutoCompleteTextView>(R.id.dropdown_country)
        val entries = listOf("" to "Auto-detect") + CountryDialCodes.allEntries().map {
            it.first to "${it.first.uppercase()} (+${it.second})"
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, entries.map { it.second })
        dropdown.setAdapter(adapter)

        prefs.manualCountry.onEach { country ->
            val display = entries.find { it.first == country }?.second ?: "Auto-detect"
            dropdown.setText(display, false)
        }.launchIn(lifecycleScope)

        dropdown.setOnItemClickListener { _, _, position, _ ->
            lifecycleScope.launch { prefs.setManualCountry(entries[position].first) }
        }
    }

    private fun setupStatus() {
        val statusText = findViewById<TextView>(R.id.text_status)
        val telephony = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val roleManager = getSystemService(RoleManager::class.java)

        prefs.enabled.onEach {
            val simCountry = telephony.simCountryIso?.uppercase() ?: "?"
            val networkCountry = telephony.networkCountryIso?.uppercase() ?: "?"
            val isRoaming = telephony.simCountryIso != telephony.networkCountryIso
            val hasRole = roleManager.isRoleHeld(RoleManager.ROLE_CALL_REDIRECTION)

            val status = buildString {
                if (!hasRole) {
                    append("⚠ Not set as call redirection app\n")
                }
                append("SIM: $simCountry | Network: $networkCountry")
                if (isRoaming) {
                    val dialCode = CountryDialCodes.getDialCode(telephony.networkCountryIso ?: "")
                    append("\nRoaming — will prepend +${dialCode ?: "?"}")
                } else {
                    append("\nNot roaming — calls pass through")
                }
            }
            statusText.text = status
        }.launchIn(lifecycleScope)
    }

    private fun setupLog() {
        val logText = findViewById<TextView>(R.id.text_log)
        prefs.rewriteLog.onEach { log ->
            logText.text = log.ifBlank { "No rewrites yet" }
        }.launchIn(lifecycleScope)
    }
}
