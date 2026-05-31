package si.merhar.roamer

/**
 * Pure logic for deciding whether and how to rewrite a phone number.
 *
 * The rewriter adds an international prefix to local numbers when the user
 * is roaming (SIM country differs from network country).
 */
object NumberRewriter {

    /** Maximum length of a number considered a "short code" (not rewritten). */
    private const val SHORT_NUMBER_MAX_LENGTH = 5

    /** Minimum length of a subscriber number (digits after the country code). */
    private const val MIN_SUBSCRIBER_LENGTH = 6

    /**
     * Countries where the leading '0' is part of the subscriber number (no trunk prefix).
     * Italy is the notable EU exception — landline numbers include the '0' (e.g., 06 for Rome).
     */
    private val noTrunkPrefixCountries = setOf("it")

    /**
     * Result of a rewrite attempt.
     */
    sealed class Result {
        /** Number was rewritten to [newNumber]. */
        data class Rewritten(val newNumber: String, val reason: String) : Result()

        /** Number should be dialled as-is. */
        data class PassThrough(val reason: String) : Result()
    }

    /**
     * Checks whether a number (in international format) is destined for the given country.
     *
     * Guards against prefix ambiguity: e.g. "+351..." must not match dial code "3" or "35"
     * — it verifies that the digits after the dial code form a plausible subscriber number
     * (at least [MIN_SUBSCRIBER_LENGTH] digits).
     *
     * @param number The number to check (must start with "+")
     * @param countryIso ISO code of the target country
     * @return true if [number] starts with the country's dial code and has a valid subscriber part
     */
    fun isDestinedForCountry(number: String, countryIso: String): Boolean {
        if (!number.startsWith("+")) return false
        val dialCode = CountryDialCodes.getDialCode(countryIso.lowercase()) ?: return false
        if (!number.startsWith("+$dialCode")) return false
        // Ensure the remaining part after +dialCode is a plausible subscriber number
        val subscriber = number.substring(1 + dialCode.length)
        return subscriber.length >= MIN_SUBSCRIBER_LENGTH
    }

    /**
     * Evaluates whether [number] should be rewritten given the current roaming context.
     *
     * @param number The dialled number (may include trunk prefix, international prefix, etc.)
     * @param simCountryIso ISO code of the SIM's home country (e.g. "nl")
     * @param networkCountryIso ISO code of the currently connected network's country (e.g. "pt")
     * @param enabled Whether the rewriter is enabled by the user
     * @param manualCountryOverride Optional manual override for network country (used when on WiFi)
     */
    fun evaluate(
        number: String,
        simCountryIso: String,
        networkCountryIso: String,
        enabled: Boolean = true,
        manualCountryOverride: String? = null
    ): Result {
        if (!enabled) {
            return Result.PassThrough("Rewriter disabled")
        }

        val cleaned = number.replace("[\\s\\-()]".toRegex(), "")

        // Already has international prefix
        if (cleaned.startsWith("+") || cleaned.startsWith("00")) {
            return Result.PassThrough("Already international")
        }

        // USSD/MMI codes (e.g., *100#, *123*456#, #31#)
        if (cleaned.startsWith("*") || cleaned.startsWith("#")) {
            return Result.PassThrough("USSD/MMI code")
        }

        // Short/service numbers (emergency, info lines)
        if (cleaned.length <= SHORT_NUMBER_MAX_LENGTH) {
            return Result.PassThrough("Short number")
        }

        val effectiveNetworkCountry = (manualCountryOverride ?: networkCountryIso).lowercase()
        val simCountry = simCountryIso.lowercase()

        // Not roaming
        if (simCountry == effectiveNetworkCountry) {
            return Result.PassThrough("Not roaming")
        }

        // Look up the dial code for the network country
        val dialCode = CountryDialCodes.getDialCode(effectiveNetworkCountry)
            ?: return Result.PassThrough("Unknown country: $effectiveNetworkCountry")

        // Strip trunk prefix (leading 0) — except in countries where 0 is part of the number
        val withoutTrunk = if (cleaned.startsWith("0") && effectiveNetworkCountry !in noTrunkPrefixCountries) {
            cleaned.substring(1)
        } else {
            cleaned
        }

        val rewritten = "+$dialCode$withoutTrunk"
        return Result.Rewritten(
            newNumber = rewritten,
            reason = "Roaming in ${effectiveNetworkCountry.uppercase()}: $number → $rewritten"
        )
    }
}
