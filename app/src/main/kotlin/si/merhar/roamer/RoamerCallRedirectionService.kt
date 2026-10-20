package si.merhar.roamer

import android.net.Uri
import android.telecom.CallRedirectionService
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Android system service that intercepts outgoing calls and rewrites local numbers
 * with the appropriate international prefix when the user is roaming.
 *
 * Must be registered as the default call redirection app via RoleManager.
 *
 * Note: [onPlaceCall] is invoked on the main thread and must call [redirectCall] or
 * [placeCallUnmodified] before returning (or within ~5s). We read preferences with
 * [runBlocking] since DataStore reads from a warmed cache are near-instant, and calling
 * the response methods from a background coroutine is not permitted by the framework.
 *
 * Important: while enabled, this always answers with [redirectCall] and never
 * [placeCallUnmodified]. Telecom normalizes the number to E.164 against the visited
 * network's country before calling this service but places the original dial string on
 * every path that does not receive an explicit redirect, so echoing the supplied handle
 * back is what makes the international form the number actually dialed.
 *
 * [placeCallUnmodified] is therefore exactly how the disabled state is expressed: it
 * places the original dial string, leaving the call as if this app were not installed.
 */
class RoamerCallRedirectionService : CallRedirectionService() {

    private val logScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

    override fun onPlaceCall(
        handle: Uri,
        initialPhoneAccount: PhoneAccountHandle,
        allowInteractiveResponse: Boolean
    ) {
        val number = handle.schemeSpecificPart ?: run {
            redirectCall(handle, initialPhoneAccount, false)
            return
        }

        val prefs = PreferencesRepository(applicationContext)
        val enabled = runBlocking { prefs.isEnabled() }

        // Disabled means "behave as if this app were not installed", which is what placing
        // the call unmodified does: the original dial string goes out, not Telecom's
        // normalized handle.
        if (!enabled) {
            placeCallUnmodified()
            return
        }

        val telephony = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val simCountry = telephony.simCountryIso ?: ""
        val networkCountry = telephony.networkCountryIso ?: ""

        val manualCountry = runBlocking { prefs.getManualCountry().ifBlank { null } }
        val useLocalSim = runBlocking { prefs.isUseLocalSim() }
        val useNumberCountrySim = runBlocking { prefs.isUseNumberCountrySim() }
        val useFallbackRewrite = runBlocking { prefs.isUseFallbackRewrite() }

        val result = NumberRewriter.evaluate(
            number = number,
            simCountryIso = simCountry,
            networkCountryIso = networkCountry,
            fallbackEnabled = useFallbackRewrite,
            manualCountryOverride = manualCountry
        )

        val finalUri: Uri
        val reason: String?

        when (result) {
            is NumberRewriter.Result.Rewritten -> {
                finalUri = Uri.fromParts("tel", result.newNumber, null)
                reason = result.reason
            }
            is NumberRewriter.Result.PassThrough -> {
                finalUri = handle
                reason = null
            }
        }

        // Determine which SIM to route through. SIMs are only listed when a routing option
        // is on, since listing them needs READ_PHONE_STATE.
        val simSwitch = if (useNumberCountrySim || useLocalSim) {
            SimRouting.choose(
                number = finalUri.schemeSpecificPart ?: "",
                sims = callCapableSims(),
                current = initialPhoneAccount,
                simCountryIso = simCountry,
                networkCountryIso = networkCountry,
                useNumberCountrySim = useNumberCountrySim,
                useLocalSim = useLocalSim
            )
        } else {
            null
        }
        val phoneAccount = simSwitch?.sim?.account ?: initialPhoneAccount

        redirectCall(finalUri, phoneAccount, false)

        // Logging
        logScope.launch {
            val timestamp = LocalDateTime.now().format(timeFormat)
            val suffix = when (simSwitch?.rule) {
                null -> ""
                SimRouting.Rule.NUMBER_COUNTRY -> " [${simSwitch.sim.countryIso.uppercase()} SIM]"
                SimRouting.Rule.LOCAL -> " [local SIM]"
            }

            if (reason != null) {
                prefs.appendLog("[$timestamp] $reason$suffix")
            } else {
                val isRoaming = simCountry.isNotEmpty() && simCountry != networkCountry
                val passReason = (result as NumberRewriter.Result.PassThrough).reason
                if (isRoaming && passReason == "Already international") {
                    prefs.appendLog("[$timestamp] $number (system-prefixed)$suffix")
                } else if (simSwitch != null) {
                    prefs.appendLog("[$timestamp] $number$suffix")
                }
            }
        }
    }

    /**
     * Lists call-capable phone accounts with their SIM's country, skipping accounts with no
     * SIM country (such as SIP accounts).
     *
     * Requires READ_PHONE_STATE permission on Android 12+. Returns an empty list on
     * SecurityException (permission not granted), so callers fall back to the default SIM.
     */
    private fun callCapableSims(): List<SimRouting.Sim<PhoneAccountHandle>> {
        return try {
            val telecom = getSystemService(TELECOM_SERVICE) as TelecomManager
            val telephony = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            telecom.callCapablePhoneAccounts.mapNotNull { account ->
                val country = telephony.createForPhoneAccountHandle(account)
                    ?.simCountryIso?.lowercase()?.ifBlank { null }
                    ?: return@mapNotNull null
                SimRouting.Sim(account, country)
            }
        } catch (_: SecurityException) {
            // READ_PHONE_STATE not granted — fall back to default SIM
            emptyList()
        }
    }
}
