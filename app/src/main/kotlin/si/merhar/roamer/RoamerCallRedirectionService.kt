package si.merhar.roamer

import android.net.Uri
import android.telecom.CallRedirectionService
import android.telecom.PhoneAccountHandle
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
            placeCallUnmodified()
            return
        }

        val telephony = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val simCountry = telephony.simCountryIso ?: ""
        val networkCountry = telephony.networkCountryIso ?: ""

        val prefs = PreferencesRepository(applicationContext)
        val enabled = runBlocking { prefs.isEnabled() }
        val manualCountry = runBlocking { prefs.getManualCountry().ifBlank { null } }

        val result = NumberRewriter.evaluate(
            number = number,
            simCountryIso = simCountry,
            networkCountryIso = networkCountry,
            enabled = enabled,
            manualCountryOverride = manualCountry
        )

        when (result) {
            is NumberRewriter.Result.Rewritten -> {
                val newUri = Uri.fromParts("tel", result.newNumber, null)
                redirectCall(newUri, initialPhoneAccount, true)
                // Log asynchronously to avoid blocking the call
                logScope.launch {
                    val timestamp = LocalDateTime.now().format(timeFormat)
                    prefs.appendLog("[$timestamp] ${result.reason}")
                }
            }
            is NumberRewriter.Result.PassThrough -> {
                placeCallUnmodified()
            }
        }
    }
}
