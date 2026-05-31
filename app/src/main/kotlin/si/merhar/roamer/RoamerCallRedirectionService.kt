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
 * Important: We always use [redirectCall] rather than [placeCallUnmodified] because the
 * Telecom framework normalizes numbers (adds international prefix) before invoking this
 * service, but [placeCallUnmodified] reverts to the original pre-normalization number.
 * Using [redirectCall] with the received handle ensures the normalized number is dialed.
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

        val telephony = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val simCountry = telephony.simCountryIso ?: ""
        val networkCountry = telephony.networkCountryIso ?: ""

        val prefs = PreferencesRepository(applicationContext)
        val enabled = runBlocking { prefs.isEnabled() }
        val manualCountry = runBlocking { prefs.getManualCountry().ifBlank { null } }
        val useLocalSim = runBlocking { prefs.isUseLocalSim() }

        val result = NumberRewriter.evaluate(
            number = number,
            simCountryIso = simCountry,
            networkCountryIso = networkCountry,
            enabled = enabled,
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

        // Determine which SIM to route through
        val phoneAccount = if (useLocalSim) {
            resolveLocalSim(finalUri, networkCountry, simCountry) ?: initialPhoneAccount
        } else {
            initialPhoneAccount
        }

        redirectCall(finalUri, phoneAccount, false)

        // Logging
        logScope.launch {
            val timestamp = LocalDateTime.now().format(timeFormat)
            val usedLocal = phoneAccount != initialPhoneAccount

            if (reason != null) {
                val suffix = if (usedLocal) " [local SIM]" else ""
                prefs.appendLog("[$timestamp] $reason$suffix")
            } else {
                val isRoaming = simCountry.isNotEmpty() && simCountry != networkCountry
                if (enabled && isRoaming) {
                    val passReason = (result as NumberRewriter.Result.PassThrough).reason
                    if (passReason == "Already international") {
                        val suffix = if (usedLocal) " [local SIM]" else ""
                        prefs.appendLog("[$timestamp] $number (system-prefixed)$suffix")
                    }
                }
            }
        }
    }

    /**
     * If roaming and the dialed number is destined for the network country,
     * finds a SIM whose [TelephonyManager.getSimCountryIso] matches the network country.
     *
     * Returns null if no local SIM is found, if not roaming, or if permission is missing.
     */
    private fun resolveLocalSim(
        uri: Uri,
        networkCountry: String,
        simCountry: String
    ): PhoneAccountHandle? {
        // Only relevant when roaming
        if (networkCountry.isBlank() || simCountry == networkCountry) return null

        // Check if the number is destined for the network country
        val number = uri.schemeSpecificPart ?: return null
        if (!NumberRewriter.isDestinedForCountry(number, networkCountry)) return null

        return findLocalSimAccount(networkCountry)
    }

    /**
     * Queries TelecomManager for a call-capable phone account whose SIM country
     * matches [targetCountry].
     *
     * Requires READ_PHONE_STATE permission on Android 12+. Returns null on
     * SecurityException (permission not granted) or if no matching account is found.
     */
    private fun findLocalSimAccount(targetCountry: String): PhoneAccountHandle? {
        return try {
            val telecom = getSystemService(TELECOM_SERVICE) as TelecomManager
            val accounts = telecom.callCapablePhoneAccounts

            for (account in accounts) {
                val tm = (getSystemService(TELEPHONY_SERVICE) as TelephonyManager)
                    .createForPhoneAccountHandle(account)
                val accountCountry = tm?.simCountryIso?.lowercase() ?: continue
                if (accountCountry == targetCountry.lowercase()) {
                    return account
                }
            }
            null
        } catch (_: SecurityException) {
            // READ_PHONE_STATE not granted — fall back to default SIM
            null
        }
    }
}
