package si.merhar.roamer

import si.merhar.roamer.SimRouting.Rule
import si.merhar.roamer.SimRouting.Sim
import si.merhar.roamer.SimRouting.Switch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SimRoutingTest {

    private val nl = Sim("nl-sim", "nl")
    private val si = Sim("si-sim", "si")
    private val pt = Sim("pt-sim", "pt")
    private val pt2 = Sim("pt-sim-2", "pt")

    private val slovenianNumber = "+38641123456"
    private val portugueseNumber = "+351912345678"

    /** Calls [SimRouting.choose] with an NL default SIM roaming in Portugal unless overridden. */
    private fun choose(
        number: String,
        sims: List<Sim<String>>,
        current: String = "nl-sim",
        simCountry: String = "nl",
        networkCountry: String = "pt",
        numberCountry: Boolean = false,
        local: Boolean = false
    ): Switch<String>? = SimRouting.choose(number, sims, current, simCountry, networkCountry, numberCountry, local)

    // --- simForNumber ---

    @Test
    fun `picks the SIM from the number's country`() {
        assertEquals(si, SimRouting.simForNumber(slovenianNumber, listOf(nl, si), current = "nl-sim"))
    }

    @Test
    fun `prefers the current SIM among several matches`() {
        val si2 = Sim("si-sim-2", "si")
        assertEquals(si2, SimRouting.simForNumber(slovenianNumber, listOf(si, si2), current = "si-sim-2"))
    }

    @Test
    fun `falls back to the first match when the current SIM does not match`() {
        val si2 = Sim("si-sim-2", "si")
        assertEquals(si, SimRouting.simForNumber(slovenianNumber, listOf(nl, si, si2), current = "nl-sim"))
    }

    @Test
    fun `returns null when no SIM matches the number's country`() {
        assertNull(SimRouting.simForNumber(portugueseNumber, listOf(nl, si), current = "nl-sim"))
    }

    @Test
    fun `returns null for a number not in international form`() {
        assertNull(SimRouting.simForNumber("041123456", listOf(nl, si), current = "nl-sim"))
    }

    @Test
    fun `matches SIM country case-insensitively`() {
        val upper = Sim("si-sim", "SI")
        assertEquals(upper, SimRouting.simForNumber(slovenianNumber, listOf(nl, upper), current = "nl-sim"))
    }

    // --- choose: number-country rule ---

    @Test
    fun `number-country rule switches to the SIM of the number's country while roaming`() {
        assertEquals(
            Switch(si, Rule.NUMBER_COUNTRY),
            choose(slovenianNumber, listOf(nl, si), numberCountry = true)
        )
    }

    @Test
    fun `number-country rule switches SIM when not roaming`() {
        assertEquals(
            Switch(si, Rule.NUMBER_COUNTRY),
            choose(slovenianNumber, listOf(nl, si), networkCountry = "nl", numberCountry = true)
        )
    }

    @Test
    fun `number-country rule keeps a call already on a matching SIM`() {
        assertNull(choose("+31612345678", listOf(si, nl), numberCountry = true))
    }

    @Test
    fun `number-country rule is off unless enabled`() {
        assertNull(choose(slovenianNumber, listOf(nl, si)))
    }

    // --- choose: local rule ---

    @Test
    fun `local rule switches to the visited country's SIM for a local number`() {
        assertEquals(Switch(pt, Rule.LOCAL), choose(portugueseNumber, listOf(nl, pt), local = true))
    }

    @Test
    fun `local rule does nothing when not roaming`() {
        assertNull(choose(portugueseNumber, listOf(nl, pt), simCountry = "pt", current = "nl-sim", local = true))
    }

    @Test
    fun `local rule ignores numbers for other countries`() {
        assertNull(choose(slovenianNumber, listOf(nl, si, pt), local = true))
    }

    @Test
    fun `local rule does nothing when the network country is unknown`() {
        // Both the roaming check and isDestinedForCountry, which has no dial code for a
        // blank country, exclude this; the test covers the outcome, not either guard alone.
        assertNull(choose(portugueseNumber, listOf(nl, pt), networkCountry = "", local = true))
    }

    @Test
    fun `local rule does nothing without a SIM from the visited country`() {
        assertNull(choose(portugueseNumber, listOf(nl, si), local = true))
    }

    @Test
    fun `local rule keeps a call already on a visited-country SIM`() {
        assertNull(choose(portugueseNumber, listOf(pt, pt2), current = "pt-sim-2", local = true))
    }

    // --- choose: both rules ---

    @Test
    fun `number-country rule takes precedence over the local rule`() {
        assertEquals(
            Switch(si, Rule.NUMBER_COUNTRY),
            choose(slovenianNumber, listOf(nl, si, pt), numberCountry = true, local = true)
        )
    }

    @Test
    fun `local rule never moves a call the number-country rule kept`() {
        // Default SIM is the second PT SIM; the number is Portuguese, so it already matches.
        assertNull(choose(portugueseNumber, listOf(pt, pt2), current = "pt-sim-2", numberCountry = true, local = true))
    }

    @Test
    fun `with both rules on a local number is routed by the number-country rule`() {
        // Any SIM the local rule would pick is also from the number's country.
        assertEquals(
            Switch(pt, Rule.NUMBER_COUNTRY),
            choose(portugueseNumber, listOf(nl, pt), numberCountry = true, local = true)
        )
    }

    @Test
    fun `no SIMs means no switch`() {
        assertNull(choose(slovenianNumber, emptyList(), numberCountry = true, local = true))
    }
}
