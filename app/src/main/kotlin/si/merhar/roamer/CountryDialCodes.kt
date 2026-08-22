package si.merhar.roamer

/**
 * Numbering-plan data for EU/EEA countries and common travel destinations.
 *
 * Each country carries its E.164 dial code plus the two things needed to convert a locally
 * dialled number into international form: the trunk prefix to remove, and the international
 * access prefixes that mark a number as already international.
 *
 * Values follow each country's national numbering plan (ITU E.164 national plans; NANP for
 * US/Canada). Where a country deviates from the European norm of "trunk 0, international
 * 00" it is called out individually below.
 */
object CountryDialCodes {

    /**
     * Trunk prefix used by most of Europe, and the fallback for countries whose plan defines
     * none.
     *
     * A country with no trunk prefix cannot have a valid national number beginning with `0`,
     * so a leading `0` there is a typing habit rather than data. Removing it recovers a valid
     * number — dialling `0912345678` in Portugal yields `+351912345678` — which is why these
     * countries keep the default rather than being listed as exceptions. Italy is the one
     * country where a leading `0` is genuinely part of the subscriber number.
     */
    private val DEFAULT_TRUNK_PREFIXES = listOf("0")

    /** International access code recommended by ITU-T E.164 and used across Europe. */
    private val DEFAULT_INTERNATIONAL_PREFIXES = listOf("00")

    /**
     * How numbers are dialled within one country.
     *
     * @property dialCode E.164 country calling code, without the leading `+`
     * @property trunkPrefixes Prefixes that precede a national number when dialling
     *   domestically and are dropped in international format. Matched longest-first; empty
     *   means the country has none and a leading `0` must be preserved.
     * @property internationalPrefixes Prefixes that introduce an international number when
     *   dialling from inside this country.
     */
    data class NumberingPlan(
        val dialCode: String,
        val trunkPrefixes: List<String> = DEFAULT_TRUNK_PREFIXES,
        val internationalPrefixes: List<String> = DEFAULT_INTERNATIONAL_PREFIXES
    )

    private val plans = mapOf(
        // --- EU member states (trunk 0, international 00 unless noted) ---
        "at" to NumberingPlan("43"),   // Austria
        "be" to NumberingPlan("32"),   // Belgium
        "bg" to NumberingPlan("359"),  // Bulgaria
        "hr" to NumberingPlan("385"),  // Croatia
        "cy" to NumberingPlan("357"),  // Cyprus — plan defines no trunk prefix
        "cz" to NumberingPlan("420"),  // Czech Republic — no trunk prefix
        "dk" to NumberingPlan("45"),   // Denmark — no trunk prefix
        "ee" to NumberingPlan("372"),  // Estonia — no trunk prefix
        "fi" to NumberingPlan("358"),  // Finland
        "fr" to NumberingPlan("33"),   // France
        "de" to NumberingPlan("49"),   // Germany
        "gr" to NumberingPlan("30"),   // Greece — no trunk prefix
        // Hungary dials domestic long distance as 06 + national number, so both digits go.
        "hu" to NumberingPlan("36", trunkPrefixes = listOf("06", "0")),
        "ie" to NumberingPlan("353"),  // Ireland
        // Italy has no trunk prefix and the leading 0 of a landline is part of the
        // subscriber number: Rome is 06…, dialled internationally as +39 06….
        "it" to NumberingPlan("39", trunkPrefixes = emptyList()),
        "lv" to NumberingPlan("371"),  // Latvia — no trunk prefix
        "lt" to NumberingPlan("370"),  // Lithuania
        "lu" to NumberingPlan("352"),  // Luxembourg — no trunk prefix
        "mt" to NumberingPlan("356"),  // Malta — no trunk prefix
        "nl" to NumberingPlan("31"),   // Netherlands
        "pl" to NumberingPlan("48"),   // Poland — no trunk prefix
        "pt" to NumberingPlan("351"),  // Portugal — no trunk prefix
        "ro" to NumberingPlan("40"),   // Romania
        "sk" to NumberingPlan("421"),  // Slovakia
        "si" to NumberingPlan("386"),  // Slovenia
        "es" to NumberingPlan("34"),   // Spain — no trunk prefix
        "se" to NumberingPlan("46"),   // Sweden

        // --- EEA / closely associated ---
        "is" to NumberingPlan("354"),  // Iceland — no trunk prefix
        "li" to NumberingPlan("423"),  // Liechtenstein — no trunk prefix
        "no" to NumberingPlan("47"),   // Norway — no trunk prefix
        "ch" to NumberingPlan("41"),   // Switzerland

        // --- Other common destinations ---
        "gb" to NumberingPlan("44"),   // United Kingdom
        // NANP: long distance is dialled 1 + area code, and international is 011 + country
        // code, so neither the European trunk 0 nor 00 applies.
        "us" to NumberingPlan("1", listOf("1"), listOf("011", "00")),
        "ca" to NumberingPlan("1", listOf("1"), listOf("011", "00")),
        // Australia uses 0011 for international; it also begins 00, so both match.
        "au" to NumberingPlan("61", internationalPrefixes = listOf("0011", "00")),
        "tr" to NumberingPlan("90"),   // Turkey
        "rs" to NumberingPlan("381"),  // Serbia
        "ba" to NumberingPlan("387"),  // Bosnia and Herzegovina
        "me" to NumberingPlan("382"),  // Montenegro
        "mk" to NumberingPlan("389"),  // North Macedonia
        "al" to NumberingPlan("355"),  // Albania
        "xk" to NumberingPlan("383")   // Kosovo
    )

    /** Returns the numbering plan for a given ISO country code, or null if unknown. */
    fun getPlan(countryIso: String): NumberingPlan? = plans[countryIso.lowercase()]

    /** Returns the E.164 dial code for a given ISO country code, or null if unknown. */
    fun getDialCode(countryIso: String): String? = getPlan(countryIso)?.dialCode

    /**
     * Returns all supported country entries as a list of (iso, dialCode) pairs,
     * sorted alphabetically by ISO code.
     */
    fun allEntries(): List<Pair<String, String>> =
        plans.entries.sortedBy { it.key }.map { it.key to it.value.dialCode }
}
