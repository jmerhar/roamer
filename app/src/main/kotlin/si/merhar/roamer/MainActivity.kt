package si.merhar.roamer

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.TelephonyManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.DynamicColors
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Main settings screen. Shows roaming status, enable/disable toggle,
 * local SIM toggle, manual country override, and recent rewrite log.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesRepository

    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* Role granted or denied — UI will reflect via status text */ }

    private val phoneStatePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            // Permission denied — revert the toggle
            lifecycleScope.launch { prefs.setUseLocalSim(false) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        prefs = PreferencesRepository(this)

        setupRoleRequest()
        setupToggle()
        setupLocalSimToggle()
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

    private fun setupLocalSimToggle() {
        val toggle = findViewById<MaterialSwitch>(R.id.switch_local_sim)
        prefs.useLocalSim.onEach { enabled ->
            toggle.isChecked = enabled
        }.launchIn(lifecycleScope)

        toggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Request READ_PHONE_STATE if not already granted
                val hasPermission = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    lifecycleScope.launch { prefs.setUseLocalSim(true) }
                } else {
                    // Save optimistically — will be reverted if denied
                    lifecycleScope.launch { prefs.setUseLocalSim(true) }
                    phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                }
            } else {
                lifecycleScope.launch { prefs.setUseLocalSim(false) }
            }
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
            logText.text = log.ifBlank { getString(R.string.log_empty) }
        }.launchIn(lifecycleScope)
    }
}
