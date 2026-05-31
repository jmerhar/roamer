package si.merhar.roamer

/**
 * Static mapping of ISO 3166-1 alpha-2 country codes to E.164 dial codes
 * for EU/EEA countries and common travel destinations.
 */
object CountryDialCodes {

    private val codes = mapOf(
        // EU member states
        "at" to "43",   // Austria
        "be" to "32",   // Belgium
        "bg" to "359",  // Bulgaria
        "hr" to "385",  // Croatia
        "cy" to "357",  // Cyprus
        "cz" to "420",  // Czech Republic
        "dk" to "45",   // Denmark
        "ee" to "372",  // Estonia
        "fi" to "358",  // Finland
        "fr" to "33",   // France
        "de" to "49",   // Germany
        "gr" to "30",   // Greece
        "hu" to "36",   // Hungary
        "ie" to "353",  // Ireland
        "it" to "39",   // Italy
        "lv" to "371",  // Latvia
        "lt" to "370",  // Lithuania
        "lu" to "352",  // Luxembourg
        "mt" to "356",  // Malta
        "nl" to "31",   // Netherlands
        "pl" to "48",   // Poland
        "pt" to "351",  // Portugal
        "ro" to "40",   // Romania
        "sk" to "421",  // Slovakia
        "si" to "386",  // Slovenia
        "es" to "34",   // Spain
        "se" to "46",   // Sweden

        // EEA / closely associated
        "is" to "354",  // Iceland
        "li" to "423",  // Liechtenstein
        "no" to "47",   // Norway
        "ch" to "41",   // Switzerland

        // Other common destinations
        "gb" to "44",   // United Kingdom
        "us" to "1",    // United States
        "ca" to "1",    // Canada
        "au" to "61",   // Australia
        "tr" to "90",   // Turkey
        "rs" to "381",  // Serbia
        "ba" to "387",  // Bosnia and Herzegovina
        "me" to "382",  // Montenegro
        "mk" to "389",  // North Macedonia
        "al" to "355",  // Albania
        "xk" to "383",  // Kosovo
    )

    /**
     * Returns the E.164 dial code for a given ISO country code, or null if unknown.
     */
    fun getDialCode(countryIso: String): String? {
        return codes[countryIso.lowercase()]
    }

    /**
     * Returns all supported country entries as a list of (iso, dialCode) pairs,
     * sorted alphabetically by ISO code.
     */
    fun allEntries(): List<Pair<String, String>> {
        return codes.entries.sortedBy { it.key }.map { it.key to it.value }
    }
}
