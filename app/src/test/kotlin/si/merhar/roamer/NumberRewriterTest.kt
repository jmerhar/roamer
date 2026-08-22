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

    // --- Country-specific trunk prefixes ---
    //
    // Most of Europe dials domestically with a leading 0 that is dropped internationally.
    // Three countries in the supported set do not follow that pattern.

    @Test
    fun `strips Hungary's two-digit 06 trunk prefix`() {
        // Hungary dials domestic long distance as 06 + national number, so both digits go.
        // Budapest 06 1 234 5678 is +36 1 234 5678, not +36 61 234 5678.
        val result = NumberRewriter.evaluate(
            number = "0612345678",
            simCountryIso = "nl",
            networkCountryIso = "hu",
            fallbackEnabled = true
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assertEquals("+3612345678", result.newNumber)
    }

    @Test
    fun `strips Hungary's trunk prefix for a mobile number`() {
        val result = NumberRewriter.evaluate(
            number = "06301234567",
            simCountryIso = "nl",
            networkCountryIso = "hu",
            fallbackEnabled = true
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assertEquals("+36301234567", result.newNumber)
    }

    @Test
    fun `leaves a Hungarian number dialled without its trunk prefix alone`() {
        val result = NumberRewriter.evaluate(
            number = "12345678",
            simCountryIso = "nl",
            networkCountryIso = "hu",
            fallbackEnabled = true
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assertEquals("+3612345678", result.newNumber)
    }

    @Test
    fun `strips the NANP trunk prefix 1 in the United States`() {
        // NANP long distance is 1 + area code; the national number itself is 10 digits and
        // never begins with 1, so +1 1 202... would be wrong.
        val result = NumberRewriter.evaluate(
            number = "12025551234",
            simCountryIso = "nl",
            networkCountryIso = "us",
            fallbackEnabled = true
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assertEquals("+12025551234", result.newNumber)
    }

    @Test
    fun `keeps a ten-digit NANP number intact`() {
        val result = NumberRewriter.evaluate(
            number = "2025551234",
            simCountryIso = "nl",
            networkCountryIso = "us",
            fallbackEnabled = true
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assertEquals("+12025551234", result.newNumber)
    }

    @Test
    fun `strips the NANP trunk prefix in Canada`() {
        val result = NumberRewriter.evaluate(
            number = "14165551234",
            simCountryIso = "nl",
            networkCountryIso = "ca",
            fallbackEnabled = true
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assertEquals("+14165551234", result.newNumber)
    }

    @Test
    fun `does not treat a leading zero as a trunk prefix in the United States`() {
        // No NANP national number starts with 0, so this is not a national number at all.
        val result = NumberRewriter.evaluate(
            number = "0123456789",
            simCountryIso = "nl",
            networkCountryIso = "us",
            fallbackEnabled = true
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assertEquals("+10123456789", result.newNumber)
    }

    // --- Country-specific international access prefixes ---

    @Test
    fun `treats 011 as international when dialling from the NANP`() {
        // 011 is the NANP international access code. Prefixing it would produce +1 11 44...
        val result = NumberRewriter.evaluate(
            number = "011442079460958",
            simCountryIso = "nl",
            networkCountryIso = "us",
            fallbackEnabled = true
        )
        assertIs<NumberRewriter.Result.PassThrough>(result)
        assertEquals("Already international", result.reason)
    }

    @Test
    fun `treats 0011 as international when dialling from Australia`() {
        val result = NumberRewriter.evaluate(
            number = "0011442079460958",
            simCountryIso = "nl",
            networkCountryIso = "au",
            fallbackEnabled = true
        )
        assertIs<NumberRewriter.Result.PassThrough>(result)
        assertEquals("Already international", result.reason)
    }

    @Test
    fun `still treats 00 as international in Europe`() {
        val result = NumberRewriter.evaluate(
            number = "00442079460958",
            simCountryIso = "nl",
            networkCountryIso = "pt",
            fallbackEnabled = true
        )
        assertIs<NumberRewriter.Result.PassThrough>(result)
        assertEquals("Already international", result.reason)
    }

    // --- Service numbers in the 11x ranges ---

    @Test
    fun `passes through EU harmonised 116 service numbers`() {
        // 116000 (missing children) and 116117 (medical on-call) are six-digit harmonised
        // numbers with no international form; prefixing one makes it unreachable.
        for (number in listOf("116000", "116117", "116123")) {
            val result = NumberRewriter.evaluate(
                number = number,
                simCountryIso = "nl",
                networkCountryIso = "pt",
                fallbackEnabled = true
            )
            assertIs<NumberRewriter.Result.PassThrough>(result, "should not rewrite $number")
            assertEquals("Service number", result.reason)
        }
    }

    @Test
    fun `passes through directory enquiry numbers in the 118 range`() {
        // France uses 118XYZ and Germany 118xy for directory enquiries.
        for (number in listOf("118712", "11833")) {
            val result = NumberRewriter.evaluate(
                number = number,
                simCountryIso = "nl",
                networkCountryIso = "fr",
                fallbackEnabled = true
            )
            assertIs<NumberRewriter.Result.PassThrough>(result, "should not rewrite $number")
        }
    }

    @Test
    fun `still rewrites a geographic number that merely starts with 11`() {
        // Polish area codes begin at 12, so a nine-digit number starting 1 is geographic and
        // must not be caught by the service-number rule.
        val result = NumberRewriter.evaluate(
            number = "118765432",
            simCountryIso = "nl",
            networkCountryIso = "pl",
            fallbackEnabled = true
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assertEquals("+48118765432", result.newNumber)
    }

    @Test
    fun `still rewrites a seven-digit number starting with 11`() {
        // One digit past the service-number range, so the rule must not apply.
        val result = NumberRewriter.evaluate(
            number = "1161234",
            simCountryIso = "nl",
            networkCountryIso = "pt",
            fallbackEnabled = true
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assertEquals("+3511161234", result.newNumber)
    }

    // --- E.164 length ceiling ---

    @Test
    fun `passes through a number that would exceed the E164 digit limit`() {
        // E.164 allows at most 15 digits; a longer result cannot be a real number, so the
        // input was not a national number to begin with.
        val result = NumberRewriter.evaluate(
            number = "1234567890123456",
            simCountryIso = "nl",
            networkCountryIso = "pt",
            fallbackEnabled = true
        )
        assertIs<NumberRewriter.Result.PassThrough>(result)
        assertEquals("Too long for E.164", result.reason)
    }

    @Test
    fun `rewrites a number that lands exactly on the E164 digit limit`() {
        // +351 plus 12 digits is 15 digits total, which is allowed.
        val result = NumberRewriter.evaluate(
            number = "123456789012",
            simCountryIso = "nl",
            networkCountryIso = "pt",
            fallbackEnabled = true
        )
        assertIs<NumberRewriter.Result.Rewritten>(result)
        assertEquals("+351123456789012", result.newNumber)
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
