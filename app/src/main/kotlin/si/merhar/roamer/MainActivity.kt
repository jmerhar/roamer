package si.merhar.roamer

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.TelephonyManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CompoundButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.DynamicColors
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Main settings screen. Shows roaming status, enable/disable toggle,
 * local SIM toggle, manual country override, and recent rewrite log.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesRepository

    /**
     * Bumped to force the status card to recompute.
     *
     * Roaming state lives in TelephonyManager, not in a Flow, so nothing emits when the
     * device changes network. Re-reading on resume keeps the card from showing whatever was
     * true when the screen was first opened.
     */
    private val statusTick = MutableStateFlow(0)

    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { statusTick.update { it + 1 } }

    private val phoneStatePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            // Local SIM routing cannot work without it, so do not leave the toggle on.
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
        setupFallbackRewriteToggle()
        setupCountryOverride()
        setupStatus()
        setupLog()
    }

    override fun onResume() {
        super.onResume()
        statusTick.update { it + 1 }
    }

    private fun setupRoleRequest() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_REDIRECTION)) {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_REDIRECTION)
            roleRequestLauncher.launch(intent)
        }
    }

    private fun setupToggle() {
        bindSwitch(findViewById(R.id.switch_enabled), prefs.enabled) { isChecked ->
            lifecycleScope.launch { prefs.setEnabled(isChecked) }
        }
    }

    private fun setupLocalSimToggle() {
        bindSwitch(findViewById(R.id.switch_local_sim), prefs.useLocalSim) { isChecked ->
            lifecycleScope.launch { prefs.setUseLocalSim(isChecked) }

            // Stored optimistically; the permission callback switches it back off if the
            // request is declined, so the stored value always matches what can actually run.
            val needsPermission = isChecked && ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED

            if (needsPermission) {
                phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
            }
        }
    }

    /**
     * Keeps [toggle] in step with [values], reporting only changes the user made.
     *
     * The preference and the switch observe each other: the Flow drives the switch and the
     * switch writes back. Applying an incoming value with the listener attached would look
     * like a user edit and be written straight back, so it is detached for the update.
     */
    private fun bindSwitch(
        toggle: MaterialSwitch,
        values: Flow<Boolean>,
        onUserChange: (Boolean) -> Unit
    ) {
        val listener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
            onUserChange(isChecked)
        }
        toggle.setOnCheckedChangeListener(listener)

        values.onEach { value ->
            if (toggle.isChecked != value) {
                toggle.setOnCheckedChangeListener(null)
                toggle.isChecked = value
                toggle.setOnCheckedChangeListener(listener)
            }
        }.launchIn(lifecycleScope)
    }

    private fun setupFallbackRewriteToggle() {
        bindSwitch(findViewById(R.id.switch_fallback_rewrite), prefs.useFallbackRewrite) { isChecked ->
            lifecycleScope.launch { prefs.setUseFallbackRewrite(isChecked) }
        }
    }

    private fun setupCountryOverride() {
        val dropdown = findViewById<AutoCompleteTextView>(R.id.dropdown_country)
        val entries = listOf("" to getString(R.string.country_auto_detect)) +
            CountryDialCodes.allEntries().map { (iso, dialCode) ->
                iso to getString(R.string.country_entry, iso.uppercase(), dialCode)
            }
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            entries.map { it.second }
        )
        dropdown.setAdapter(adapter)

        prefs.manualCountry.onEach { country ->
            val display = entries.find { it.first == country }?.second
                ?: getString(R.string.country_auto_detect)
            dropdown.setText(display, false)
        }.launchIn(lifecycleScope)

        dropdown.setOnItemClickListener { _, _, position, _ ->
            lifecycleScope.launch { prefs.setManualCountry(entries[position].first) }
        }
    }

    private fun setupStatus() {
        val statusText = findViewById<TextView>(R.id.text_status)
        combine(
            prefs.enabled,
            prefs.manualCountry,
            prefs.useFallbackRewrite,
            statusTick
        ) { enabled, manualCountry, fallback, _ ->
            Triple(enabled, manualCountry, fallback)
        }.onEach { (enabled, manualCountry, fallback) ->
            statusText.text = buildStatusText(enabled, manualCountry, fallback)
        }.launchIn(lifecycleScope)
    }

    /**
     * Renders the status card.
     *
     * Mirrors [NumberRewriter.evaluate]'s decision — including honouring the manual
     * override — so the card describes what calls will actually do rather than what the
     * network alone suggests.
     */
    private fun buildStatusText(
        enabled: Boolean,
        manualCountry: String,
        fallbackEnabled: Boolean
    ): String {
        val telephony = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val simCountry = telephony.simCountryIso.orEmpty().lowercase()
        val networkCountry = telephony.networkCountryIso.orEmpty().lowercase()
        val effectiveCountry = manualCountry.ifBlank { networkCountry }
        val unknown = getString(R.string.status_unknown_country_code)
        val roleManager = getSystemService(RoleManager::class.java)

        return buildString {
            if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_REDIRECTION)) {
                appendLine(getString(R.string.status_no_role))
            }
            append(
                getString(
                    R.string.status_sim_and_network,
                    simCountry.uppercase().ifEmpty { unknown },
                    networkCountry.uppercase().ifEmpty { unknown }
                )
            )
            if (manualCountry.isNotBlank()) {
                appendLine()
                append(getString(R.string.status_override_active, manualCountry.uppercase()))
            }
            appendLine()
            append(
                when {
                    !enabled -> getString(R.string.status_disabled)
                    simCountry == effectiveCountry -> getString(R.string.status_not_roaming)
                    // Roaming: Android supplies the prefix either way. The fallback only
                    // changes what happens to numbers it could not recognise.
                    !fallbackEnabled -> getString(R.string.status_roaming_system)
                    else -> CountryDialCodes.getDialCode(effectiveCountry)
                        ?.let { getString(R.string.status_roaming, it) }
                        ?: getString(R.string.status_roaming_unsupported)
                }
            )
        }
    }

    private fun setupLog() {
        val logText = findViewById<TextView>(R.id.text_log)
        prefs.rewriteLog.onEach { log ->
            logText.text = log.ifBlank { getString(R.string.log_empty) }
        }.launchIn(lifecycleScope)
    }
}
