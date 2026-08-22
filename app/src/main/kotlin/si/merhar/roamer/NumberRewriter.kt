package si.merhar.roamer

/**
 * Pure logic for deciding whether and how to rewrite a phone number.
 *
 * This is a fallback, not the app's main mechanism. Telecom normalizes the dialled number
 * to E.164 against the visited network's country before the redirection service is called,
 * so a local number normally arrives already prefixed and is reported as
 * [Result.PassThrough] with reason "Already international".
 *
 * Rewriting here only happens for numbers Telecom could not parse for that region and
 * therefore passed through untouched. Because a local-format number cannot be attributed
 * to a country with certainty, that rewrite is a guess, and it is gated behind
 * [fallbackEnabled] — off by default, since a home-country number in national format is
 * indistinguishable from a visited-country one and would be given the wrong prefix.
 */
object NumberRewriter {

    /** Maximum length of a number considered a "short code" (not rewritten). */
    private const val SHORT_NUMBER_MAX_LENGTH = 5

    /** Minimum length of a subscriber number (digits after the country code). */
    private const val MIN_SUBSCRIBER_LENGTH = 6

    /**
     * Maximum digits in an E.164 number, excluding the '+' (ITU-T E.164).
     *
     * A result longer than this cannot be a real number, so the input was not a national
     * number and is left alone rather than dialled as something invalid.
     */
    private const val E164_MAX_DIGITS = 15

    /**
     * Length below which a number beginning [SERVICE_NUMBER_PREFIX] is a service code.
     *
     * The 11x ranges hold short non-geographic services across Europe: the EU-harmonised
     * 116xxx numbers of social value (116000 missing children, 116117 medical on-call) and
     * national directory enquiries such as France's 118XYZ and Germany's 118xy. All are six
     * digits or fewer and none is reachable in international format, so prefixing one breaks
     * it. No geographic or mobile number in the supported countries is this short while also
     * starting with 11 — the shortest such national numbers, in Hungary and Poland, run to
     * eight and nine digits.
     */
    private const val SERVICE_NUMBER_MAX_LENGTH = 6
    private const val SERVICE_NUMBER_PREFIX = "11"

    /**
     * International access prefix accepted regardless of the country dialled from.
     *
     * ITU-T E.164 recommends 00 and every supported country except the NANP uses it, so it is
     * treated as international everywhere. Being liberal here is safe: the outcome is to pass
     * the number through untouched.
     */
    private const val UNIVERSAL_INTERNATIONAL_PREFIX = "00"

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
     * Matches the country's dial code and then requires at least [MIN_SUBSCRIBER_LENGTH]
     * digits after it, so a number too short to be a real subscriber number in that
     * country is not taken for one. This matters most for single-digit dial codes, where
     * the dial code alone is very weak evidence.
     *
     * The plain prefix match is sound only because no dial code in [CountryDialCodes] is a
     * prefix of another; `CountryDialCodesTest` asserts that property, so adding a country
     * that breaks it fails the build rather than silently mismatching numbers here.
     *
     * @param number The number to check (must start with "+")
     * @param countryIso ISO code of the target country
     * @return true if [number] starts with the country's dial code and has a plausible
     *   subscriber part
     */
    fun isDestinedForCountry(number: String, countryIso: String): Boolean {
        if (!number.startsWith("+")) return false
        val dialCode = CountryDialCodes.getDialCode(countryIso) ?: return false
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
     * @param fallbackEnabled Whether the user opted into rewriting numbers Telecom left
     *   unparsed. Every other outcome is reported regardless, so the log stays informative
     *   about what a call did even while rewriting is off.
     * @param manualCountryOverride Optional manual override for network country (used when on WiFi)
     */
    fun evaluate(
        number: String,
        simCountryIso: String,
        networkCountryIso: String,
        fallbackEnabled: Boolean = true,
        manualCountryOverride: String? = null
    ): Result {
        val cleaned = number.replace("[\\s\\-()]".toRegex(), "")

        // Already in international form, by the universal prefix or an explicit '+'
        if (cleaned.startsWith("+") || cleaned.startsWith(UNIVERSAL_INTERNATIONAL_PREFIX)) {
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

        // Harmonised and national service codes in the 11x ranges
        if (cleaned.length <= SERVICE_NUMBER_MAX_LENGTH &&
            cleaned.startsWith(SERVICE_NUMBER_PREFIX)
        ) {
            return Result.PassThrough("Service number")
        }

        val effectiveNetworkCountry = (manualCountryOverride ?: networkCountryIso).lowercase()
        val simCountry = simCountryIso.lowercase()

        // Not roaming
        if (simCountry == effectiveNetworkCountry) {
            return Result.PassThrough("Not roaming")
        }

        val plan = CountryDialCodes.getPlan(effectiveNetworkCountry)
            ?: return Result.PassThrough("Unknown country: $effectiveNetworkCountry")

        // International prefixes specific to where the call is dialled from. Needed for the
        // NANP, where international is 011 and 00 means something else entirely.
        if (plan.internationalPrefixes.any { cleaned.startsWith(it) }) {
            return Result.PassThrough("Already international")
        }

        // Everything below rewrites the number. Checked here rather than on entry so the
        // outcomes above are still reported while the fallback is off.
        if (!fallbackEnabled) {
            return Result.PassThrough("Fallback rewrite off")
        }

        // Drop the trunk prefix this country uses when dialling domestically. Longest first,
        // so Hungary's 06 is preferred over a bare 0.
        val trunkPrefix = plan.trunkPrefixes
            .sortedByDescending { it.length }
            .firstOrNull { cleaned.startsWith(it) }
        val nationalNumber = trunkPrefix?.let { cleaned.removePrefix(it) } ?: cleaned

        if (plan.dialCode.length + nationalNumber.length > E164_MAX_DIGITS) {
            return Result.PassThrough("Too long for E.164")
        }

        val rewritten = "+${plan.dialCode}$nationalNumber"
        return Result.Rewritten(
            newNumber = rewritten,
            reason = "Roaming in ${effectiveNetworkCountry.uppercase()}: $number → $rewritten"
        )
    }
}
