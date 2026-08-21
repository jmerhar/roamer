package si.merhar.roamer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

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

    // --- Fallback gate ---

    @Test
    fun `passes through when the fallback rewrite is off`() {
        val result = NumberRewriter.evaluate(
            number = "912345678",
            simCountryIso = "nl",
            networkCountryIso = "pt",
            fallbackEnabled = false
        )
        assertIs<NumberRewriter.Result.PassThrough>(result)
        assertEquals("Fallback rewrite off", result.reason)
    }

    @Test
    fun `reports the real outcome rather than the fallback gate when already international`() {
        // Outcomes decided before the rewrite are reported whether or not the fallback is on,
        // so the log still says why a call was left alone.
        val result = NumberRewriter.evaluate(
            number = "+351912345678",
            simCountryIso = "nl",
            networkCountryIso = "pt",
            fallbackEnabled = false
        )
        assertIs<NumberRewriter.Result.PassThrough>(result)
        assertEquals("Already international", result.reason)
    }

    @Test
    fun `reports USSD codes rather than the fallback gate when the fallback is off`() {
        val result = NumberRewriter.evaluate(
            number = "*100#",
            simCountryIso = "nl",
            networkCountryIso = "pt",
            fallbackEnabled = false
        )
        assertIs<NumberRewriter.Result.PassThrough>(result)
        assertEquals("USSD/MMI code", result.reason)
    }

    @Test
    fun `reports not roaming rather than the fallback gate when the fallback is off`() {
        val result = NumberRewriter.evaluate(
            number = "0612345678",
            simCountryIso = "nl",
            networkCountryIso = "nl",
            fallbackEnabled = false
        )
        assertIs<NumberRewriter.Result.PassThrough>(result)
        assertEquals("Not roaming", result.reason)
    }

    @Test
    fun `rewrites when the fallback is explicitly on`() {
        val result = NumberRewriter.evaluate(
            number = "912345678",
            simCountryIso = "nl",
            networkCountryIso = "pt",
            fallbackEnabled = true
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assertEquals("+351912345678", result.newNumber)
    }

    @Test
    fun `home-country number in national format is misattributed while roaming`() {
        // Documents the known limitation: a Dutch mobile dialled in Portugal is
        // indistinguishable from a Portuguese number, which is why the fallback is opt-in.
        val result = NumberRewriter.evaluate(
            number = "0612345678",
            simCountryIso = "nl",
            networkCountryIso = "pt",
            fallbackEnabled = true
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assertEquals("+351612345678", result.newNumber)
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
        assertTrue(result.reason.contains("PT"))
        assertTrue(result.reason.contains("0912345678"))
        assertTrue(result.reason.contains("+351912345678"))
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

    // --- isDestinedForCountry ---

    @Test
    fun `isDestinedForCountry returns true for Portuguese number targeting Portugal`() {
        assertTrue(NumberRewriter.isDestinedForCountry("+351912345678", "pt"))
    }

    @Test
    fun `isDestinedForCountry returns true for German number targeting Germany`() {
        assertTrue(NumberRewriter.isDestinedForCountry("+491711234567", "de"))
    }

    @Test
    fun `isDestinedForCountry returns true for US number targeting US`() {
        assertTrue(NumberRewriter.isDestinedForCountry("+12025551234", "us"))
    }

    @Test
    fun `isDestinedForCountry rejects numbers carrying another country's dial code`() {
        assertFalse(NumberRewriter.isDestinedForCountry("+351912345678", "us"))
        assertFalse(NumberRewriter.isDestinedForCountry("+31612345678", "us"))
        assertFalse(NumberRewriter.isDestinedForCountry("+34612345678", "gr"))
        assertFalse(NumberRewriter.isDestinedForCountry("+491711234567", "fr"))
    }

    @Test
    fun `isDestinedForCountry returns false for number without plus prefix`() {
        assertFalse(NumberRewriter.isDestinedForCountry("351912345678", "pt"))
    }

    @Test
    fun `isDestinedForCountry returns false for unknown country`() {
        assertFalse(NumberRewriter.isDestinedForCountry("+999123456789", "zz"))
    }

    @Test
    fun `isDestinedForCountry handles case insensitive country code`() {
        assertTrue(NumberRewriter.isDestinedForCountry("+351912345678", "PT"))
    }

    // --- isDestinedForCountry: subscriber-length guard ---
    //
    // A number bearing the right dial code but too few digits after it is not dialable in
    // that country. These cases match on the dial code and are rejected solely by the
    // length guard, so they fail if the guard is weakened or removed.

    @Test
    fun `isDestinedForCountry rejects subscriber part below minimum length`() {
        assertFalse(NumberRewriter.isDestinedForCountry("+35112345", "pt"))
    }

    @Test
    fun `isDestinedForCountry accepts subscriber part at exactly minimum length`() {
        assertTrue(NumberRewriter.isDestinedForCountry("+351123456", "pt"))
    }

    @Test
    fun `isDestinedForCountry rejects a bare dial code with no subscriber part`() {
        assertFalse(NumberRewriter.isDestinedForCountry("+351", "pt"))
    }

    @Test
    fun `isDestinedForCountry rejects short number under a single-digit dial code`() {
        // US dial code is one digit, so almost any string starting "+1" matches it and the
        // length guard is the only thing standing between this and a false positive.
        assertFalse(NumberRewriter.isDestinedForCountry("+12345", "us"))
    }
}
