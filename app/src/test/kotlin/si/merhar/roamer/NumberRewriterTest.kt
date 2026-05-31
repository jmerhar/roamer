package si.merhar.roamer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NumberRewriterTest {

    // --- Basic roaming rewrite scenarios ---

    @Test
    fun `rewrites local number when roaming in Portugal with NL SIM`() {
        val result = NumberRewriter.evaluate(
            number = "912345678",
            simCountryIso = "nl",
            networkCountryIso = "pt"
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assertEquals("+351912345678", result.newNumber)
    }

    @Test
    fun `rewrites number with trunk prefix when roaming in Portugal`() {
        val result = NumberRewriter.evaluate(
            number = "0912345678",
            simCountryIso = "nl",
            networkCountryIso = "pt"
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assertEquals("+351912345678", result.newNumber)
    }

    @Test
    fun `rewrites local number when roaming in Spain`() {
        val result = NumberRewriter.evaluate(
            number = "612345678",
            simCountryIso = "nl",
            networkCountryIso = "es"
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assertEquals("+34612345678", result.newNumber)
    }

    @Test
    fun `rewrites local number when roaming in Germany`() {
        val result = NumberRewriter.evaluate(
            number = "01711234567",
            simCountryIso = "nl",
            networkCountryIso = "de"
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assertEquals("+491711234567", result.newNumber)
    }

    @Test
    fun `rewrites local number when roaming in France`() {
        val result = NumberRewriter.evaluate(
            number = "0612345678",
            simCountryIso = "nl",
            networkCountryIso = "fr"
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assertEquals("+33612345678", result.newNumber)
    }

    @Test
    fun `rewrites local number when roaming in Italy without stripping leading zero`() {
        val result = NumberRewriter.evaluate(
            number = "0612345678",
            simCountryIso = "nl",
            networkCountryIso = "it"
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assertEquals("+390612345678", result.newNumber)
    }

    @Test
    fun `rewrites Italian mobile number without trunk prefix`() {
        val result = NumberRewriter.evaluate(
            number = "3401234567",
            simCountryIso = "nl",
            networkCountryIso = "it"
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assertEquals("+393401234567", result.newNumber)
    }

    @Test
    fun `strips trunk prefix for France but not Italy`() {
        val france = NumberRewriter.evaluate(
            number = "0612345678",
            simCountryIso = "nl",
            networkCountryIso = "fr"
        )
        assertIs<NumberRewriter.Result.Rewritten>(france)
        assertEquals("+33612345678", france.newNumber)

        val italy = NumberRewriter.evaluate(
            number = "0612345678",
            simCountryIso = "nl",
            networkCountryIso = "it"
        )
        assertIs<NumberRewriter.Result.Rewritten>(italy)
        assertEquals("+390612345678", italy.newNumber)
    }

    // --- Pass-through scenarios ---

    @Test
    fun `passes through number already starting with plus`() {
        val result = NumberRewriter.evaluate(
            number = "+351912345678",
            simCountryIso = "nl",
            networkCountryIso = "pt"
        )
        assertIs<NumberRewriter.Result.PassThrough>(result)
        assertEquals("Already international", result.reason)
    }

    @Test
    fun `passes through number starting with 00`() {
        val result = NumberRewriter.evaluate(
            number = "00351912345678",
            simCountryIso = "nl",
            networkCountryIso = "pt"
        )
        assertIs<NumberRewriter.Result.PassThrough>(result)
        assertEquals("Already international", result.reason)
    }

    @Test
    fun `passes through when not roaming`() {
        val result = NumberRewriter.evaluate(
            number = "0612345678",
            simCountryIso = "nl",
            networkCountryIso = "nl"
        )
        assertIs<NumberRewriter.Result.PassThrough>(result)
        assertEquals("Not roaming", result.reason)
    }

    @Test
    fun `passes through when disabled`() {
        val result = NumberRewriter.evaluate(
            number = "912345678",
            simCountryIso = "nl",
            networkCountryIso = "pt",
            enabled = false
        )
        assertIs<NumberRewriter.Result.PassThrough>(result)
        assertEquals("Rewriter disabled", result.reason)
    }

    // --- Short numbers / emergency ---

    @Test
    fun `passes through emergency number 112`() {
        val result = NumberRewriter.evaluate(
            number = "112",
            simCountryIso = "nl",
            networkCountryIso = "pt"
        )
        assertIs<NumberRewriter.Result.PassThrough>(result)
        assertEquals("Short number", result.reason)
    }

    @Test
    fun `passes through short service number`() {
        val result = NumberRewriter.evaluate(
            number = "1455",
            simCountryIso = "nl",
            networkCountryIso = "pt"
        )
        assertIs<NumberRewriter.Result.PassThrough>(result)
        assertEquals("Short number", result.reason)
    }

    @Test
    fun `passes through 5-digit number`() {
        val result = NumberRewriter.evaluate(
            number = "14000",
            simCountryIso = "nl",
            networkCountryIso = "pt"
        )
        assertIs<NumberRewriter.Result.PassThrough>(result)
        assertEquals("Short number", result.reason)
    }

    @Test
    fun `rewrites 6-digit number (not considered short)`() {
        val result = NumberRewriter.evaluate(
            number = "140000",
            simCountryIso = "nl",
            networkCountryIso = "pt"
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assertEquals("+351140000", result.newNumber)
    }

    // --- Formatting / whitespace handling ---

    @Test
    fun `strips spaces before evaluating`() {
        val result = NumberRewriter.evaluate(
            number = "91 234 5678",
            simCountryIso = "nl",
            networkCountryIso = "pt"
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assertEquals("+351912345678", result.newNumber)
    }

    @Test
    fun `strips dashes before evaluating`() {
        val result = NumberRewriter.evaluate(
            number = "91-234-5678",
            simCountryIso = "nl",
            networkCountryIso = "pt"
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assertEquals("+351912345678", result.newNumber)
    }

    @Test
    fun `strips parentheses before evaluating`() {
        val result = NumberRewriter.evaluate(
            number = "(091) 2345678",
            simCountryIso = "nl",
            networkCountryIso = "pt"
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assertEquals("+351912345678", result.newNumber)
    }

    @Test
    fun `passes through formatted international number with spaces`() {
        val result = NumberRewriter.evaluate(
            number = "+351 912 345 678",
            simCountryIso = "nl",
            networkCountryIso = "pt"
        )
        assertIs<NumberRewriter.Result.PassThrough>(result)
        assertEquals("Already international", result.reason)
    }

    @Test
    fun `passes through formatted 00-prefix number with dashes`() {
        val result = NumberRewriter.evaluate(
            number = "00-351-912345678",
            simCountryIso = "nl",
            networkCountryIso = "pt"
        )
        assertIs<NumberRewriter.Result.PassThrough>(result)
        assertEquals("Already international", result.reason)
    }

    // --- Manual country override ---

    @Test
    fun `uses manual override instead of network country`() {
        val result = NumberRewriter.evaluate(
            number = "912345678",
            simCountryIso = "nl",
            networkCountryIso = "nl", // would be "not roaming" without override
            manualCountryOverride = "pt"
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assertEquals("+351912345678", result.newNumber)
    }

    @Test
    fun `manual override takes precedence over network country`() {
        val result = NumberRewriter.evaluate(
            number = "612345678",
            simCountryIso = "nl",
            networkCountryIso = "pt",
            manualCountryOverride = "es"
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assertEquals("+34612345678", result.newNumber)
    }

    @Test
    fun `passes through when manual override equals SIM country`() {
        val result = NumberRewriter.evaluate(
            number = "0612345678",
            simCountryIso = "nl",
            networkCountryIso = "pt",
            manualCountryOverride = "nl"
        )
        assertIs<NumberRewriter.Result.PassThrough>(result)
        assertEquals("Not roaming", result.reason)
    }

    // --- Case insensitivity ---

    @Test
    fun `handles uppercase SIM country`() {
        val result = NumberRewriter.evaluate(
            number = "912345678",
            simCountryIso = "NL",
            networkCountryIso = "pt"
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assertEquals("+351912345678", result.newNumber)
    }

    @Test
    fun `handles uppercase network country`() {
        val result = NumberRewriter.evaluate(
            number = "912345678",
            simCountryIso = "nl",
            networkCountryIso = "PT"
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assertEquals("+351912345678", result.newNumber)
    }

    @Test
    fun `handles uppercase manual override`() {
        val result = NumberRewriter.evaluate(
            number = "912345678",
            simCountryIso = "nl",
            networkCountryIso = "nl",
            manualCountryOverride = "PT"
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assertEquals("+351912345678", result.newNumber)
    }

    // --- Unknown country ---

    @Test
    fun `passes through for unknown network country`() {
        val result = NumberRewriter.evaluate(
            number = "912345678",
            simCountryIso = "nl",
            networkCountryIso = "zz"
        )
        assertIs<NumberRewriter.Result.PassThrough>(result)
        assertEquals("Unknown country: zz", result.reason)
    }

    @Test
    fun `passes through for unknown manual override country`() {
        val result = NumberRewriter.evaluate(
            number = "912345678",
            simCountryIso = "nl",
            networkCountryIso = "pt",
            manualCountryOverride = "xx"
        )
        assertIs<NumberRewriter.Result.PassThrough>(result)
        assertEquals("Unknown country: xx", result.reason)
    }

    // --- Reason messages ---

    @Test
    fun `rewrite reason contains country and transformation`() {
        val result = NumberRewriter.evaluate(
            number = "0912345678",
            simCountryIso = "nl",
            networkCountryIso = "pt"
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assert(result.reason.contains("PT"))
        assert(result.reason.contains("0912345678"))
        assert(result.reason.contains("+351912345678"))
    }

    // --- USSD/MMI codes ---

    @Test
    fun `passes through short USSD code starting with star`() {
        val result = NumberRewriter.evaluate(
            number = "*100#",
            simCountryIso = "nl",
            networkCountryIso = "pt"
        )
        assertIs<NumberRewriter.Result.PassThrough>(result)
        assertEquals("USSD/MMI code", result.reason)
    }

    @Test
    fun `passes through long USSD code starting with star`() {
        val result = NumberRewriter.evaluate(
            number = "*123*456789#",
            simCountryIso = "nl",
            networkCountryIso = "pt"
        )
        assertIs<NumberRewriter.Result.PassThrough>(result)
        assertEquals("USSD/MMI code", result.reason)
    }

    @Test
    fun `passes through MMI code starting with hash`() {
        val result = NumberRewriter.evaluate(
            number = "#31#0612345678",
            simCountryIso = "nl",
            networkCountryIso = "pt"
        )
        assertIs<NumberRewriter.Result.PassThrough>(result)
        assertEquals("USSD/MMI code", result.reason)
    }

    @Test
    fun `passes through call forwarding USSD code`() {
        val result = NumberRewriter.evaluate(
            number = "*21*+351912345678#",
            simCountryIso = "nl",
            networkCountryIso = "pt"
        )
        assertIs<NumberRewriter.Result.PassThrough>(result)
        assertEquals("USSD/MMI code", result.reason)
    }

    // --- Edge cases ---

    @Test
    fun `passes through empty number`() {
        val result = NumberRewriter.evaluate(
            number = "",
            simCountryIso = "nl",
            networkCountryIso = "pt"
        )
        assertIs<NumberRewriter.Result.PassThrough>(result)
        assertEquals("Short number", result.reason)
    }

    @Test
    fun `passes through number that is only formatting characters`() {
        val result = NumberRewriter.evaluate(
            number = "( - )",
            simCountryIso = "nl",
            networkCountryIso = "pt"
        )
        assertIs<NumberRewriter.Result.PassThrough>(result)
        assertEquals("Short number", result.reason)
    }

    @Test
    fun `passes through when both SIM and network country are empty`() {
        val result = NumberRewriter.evaluate(
            number = "912345678",
            simCountryIso = "",
            networkCountryIso = ""
        )
        assertIs<NumberRewriter.Result.PassThrough>(result)
        assertEquals("Not roaming", result.reason)
    }

    @Test
    fun `handles empty SIM country with valid network country`() {
        val result = NumberRewriter.evaluate(
            number = "912345678",
            simCountryIso = "",
            networkCountryIso = "pt"
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assertEquals("+351912345678", result.newNumber)
    }
}
