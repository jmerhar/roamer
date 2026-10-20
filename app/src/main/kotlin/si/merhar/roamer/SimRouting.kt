package si.merhar.roamer

/**
 * Pure SIM-selection rules, kept out of [RoamerCallRedirectionService] so they are testable
 * without TelecomManager or a Context.
 */
object SimRouting {

    /**
     * A call-capable SIM.
     *
     * @property account Opaque account identifier; a `PhoneAccountHandle` in the service.
     * @property countryIso ISO country code of the SIM's home network (e.g. "si")
     */
    data class Sim<T>(val account: T, val countryIso: String)

    /** Which setting moved a call to another SIM. */
    enum class Rule {
        /** The SIM is from the country the dialled number belongs to. */
        NUMBER_COUNTRY,

        /** The device is roaming and the SIM is from the visited country. */
        LOCAL
    }

    /** A decision to place a call on [sim] instead of the account Telecom chose. */
    data class Switch<T>(val sim: Sim<T>, val rule: Rule)

    /**
     * Decides whether a call should move from [current] to another SIM.
     *
     * The number-country rule is tried first. When it finds a matching SIM — including
     * [current] itself — its answer is final, so the local rule never moves a call that is
     * already on a SIM from the number's country. The local rule applies only while roaming,
     * to numbers destined for the visited country.
     *
     * @param number The number being dialled, in international form for any rule to match
     * @param sims Call-capable SIMs with their home countries
     * @param current The account Telecom chose for the call
     * @param simCountryIso Home country of the default SIM, used to detect roaming
     * @param networkCountryIso Country of the network the device is connected to
     * @return the SIM to switch to, or null to keep [current]
     */
    fun <T> choose(
        number: String,
        sims: List<Sim<T>>,
        current: T,
        simCountryIso: String,
        networkCountryIso: String,
        useNumberCountrySim: Boolean,
        useLocalSim: Boolean
    ): Switch<T>? {
        if (useNumberCountrySim) {
            val match = simForNumber(number, sims, current)
            if (match != null) {
                return if (match.account == current) null else Switch(match, Rule.NUMBER_COUNTRY)
            }
        }

        if (useLocalSim) {
            val roaming = networkCountryIso.isNotBlank() &&
                !simCountryIso.equals(networkCountryIso, ignoreCase = true)
            if (roaming && NumberRewriter.isDestinedForCountry(number, networkCountryIso)) {
                val local = preferCurrent(
                    sims.filter { it.countryIso.equals(networkCountryIso, ignoreCase = true) },
                    current
                )
                if (local != null && local.account != current) return Switch(local, Rule.LOCAL)
            }
        }

        return null
    }

    /**
     * Picks the SIM whose country matches the country [number] is destined for.
     *
     * Matching uses [NumberRewriter.isDestinedForCountry], so [number] must be in
     * international form. When several SIMs match (two SIMs from one country, or a US and a
     * Canadian SIM sharing `+1`), [current] wins if it is one of them, so a call already on a
     * suitable SIM is never moved.
     *
     * @return the matching SIM, or null if none matches
     */
    fun <T> simForNumber(number: String, sims: List<Sim<T>>, current: T): Sim<T>? =
        preferCurrent(sims.filter { NumberRewriter.isDestinedForCountry(number, it.countryIso) }, current)

    /** Returns the SIM for [current] if it is among [candidates], else the first candidate. */
    private fun <T> preferCurrent(candidates: List<Sim<T>>, current: T): Sim<T>? =
        candidates.firstOrNull { it.account == current } ?: candidates.firstOrNull()
}
