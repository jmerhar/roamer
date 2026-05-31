package si.merhar.roamer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CountryDialCodesTest {

    // --- EU member states ---

    @Test
    fun `returns correct dial code for Netherlands`() {
        assertEquals("31", CountryDialCodes.getDialCode("nl"))
    }

    @Test
    fun `returns correct dial code for Portugal`() {
        assertEquals("351", CountryDialCodes.getDialCode("pt"))
    }

    @Test
    fun `returns correct dial code for Spain`() {
        assertEquals("34", CountryDialCodes.getDialCode("es"))
    }

    @Test
    fun `returns correct dial code for Germany`() {
        assertEquals("49", CountryDialCodes.getDialCode("de"))
    }

    @Test
    fun `returns correct dial code for France`() {
        assertEquals("33", CountryDialCodes.getDialCode("fr"))
    }

    @Test
    fun `returns correct dial code for Italy`() {
        assertEquals("39", CountryDialCodes.getDialCode("it"))
    }

    @Test
    fun `returns correct dial code for Austria`() {
        assertEquals("43", CountryDialCodes.getDialCode("at"))
    }

    @Test
    fun `returns correct dial code for Belgium`() {
        assertEquals("32", CountryDialCodes.getDialCode("be"))
    }

    @Test
    fun `returns correct dial code for Greece`() {
        assertEquals("30", CountryDialCodes.getDialCode("gr"))
    }

    @Test
    fun `returns correct dial code for Ireland`() {
        assertEquals("353", CountryDialCodes.getDialCode("ie"))
    }

    @Test
    fun `returns correct dial code for Poland`() {
        assertEquals("48", CountryDialCodes.getDialCode("pl"))
    }

    @Test
    fun `returns correct dial code for Slovenia`() {
        assertEquals("386", CountryDialCodes.getDialCode("si"))
    }

    @Test
    fun `returns correct dial code for Croatia`() {
        assertEquals("385", CountryDialCodes.getDialCode("hr"))
    }

    // --- EEA / associated ---

    @Test
    fun `returns correct dial code for Norway`() {
        assertEquals("47", CountryDialCodes.getDialCode("no"))
    }

    @Test
    fun `returns correct dial code for Switzerland`() {
        assertEquals("41", CountryDialCodes.getDialCode("ch"))
    }

    @Test
    fun `returns correct dial code for Iceland`() {
        assertEquals("354", CountryDialCodes.getDialCode("is"))
    }

    // --- Non-EU common destinations ---

    @Test
    fun `returns correct dial code for UK`() {
        assertEquals("44", CountryDialCodes.getDialCode("gb"))
    }

    @Test
    fun `returns correct dial code for US`() {
        assertEquals("1", CountryDialCodes.getDialCode("us"))
    }

    @Test
    fun `returns correct dial code for Turkey`() {
        assertEquals("90", CountryDialCodes.getDialCode("tr"))
    }

    @Test
    fun `returns correct dial code for Serbia`() {
        assertEquals("381", CountryDialCodes.getDialCode("rs"))
    }

    // --- Case insensitivity ---

    @Test
    fun `handles uppercase country code`() {
        assertEquals("351", CountryDialCodes.getDialCode("PT"))
    }

    @Test
    fun `handles mixed case country code`() {
        assertEquals("31", CountryDialCodes.getDialCode("Nl"))
    }

    // --- Unknown countries ---

    @Test
    fun `returns null for unknown country code`() {
        assertNull(CountryDialCodes.getDialCode("zz"))
    }

    @Test
    fun `returns null for empty string`() {
        assertNull(CountryDialCodes.getDialCode(""))
    }

    // --- allEntries ---

    @Test
    fun `allEntries returns sorted list`() {
        val entries = CountryDialCodes.allEntries()
        val isoKeys = entries.map { it.first }
        assertEquals(isoKeys.sorted(), isoKeys)
    }

    @Test
    fun `allEntries contains all EU member states`() {
        val entries = CountryDialCodes.allEntries().map { it.first }
        val euCountries = listOf(
            "at", "be", "bg", "hr", "cy", "cz", "dk", "ee", "fi", "fr",
            "de", "gr", "hu", "ie", "it", "lv", "lt", "lu", "mt", "nl",
            "pl", "pt", "ro", "sk", "si", "es", "se"
        )
        for (country in euCountries) {
            assertTrue(country in entries, "Missing EU country: $country")
        }
    }

    @Test
    fun `allEntries has no duplicate ISO codes`() {
        val entries = CountryDialCodes.allEntries()
        val isoKeys = entries.map { it.first }
        assertEquals(isoKeys.size, isoKeys.distinct().size)
    }

    @Test
    fun `all dial codes are non-empty numeric strings`() {
        val entries = CountryDialCodes.allEntries()
        for ((iso, dialCode) in entries) {
            assertTrue(dialCode.isNotEmpty(), "Empty dial code for $iso")
            assertTrue(dialCode.all { it.isDigit() }, "Non-numeric dial code for $iso: $dialCode")
        }
    }

    @Test
    fun `allEntries is not empty`() {
        assertTrue(CountryDialCodes.allEntries().isNotEmpty())
    }

    @Test
    fun `allEntries includes at least 27 EU countries`() {
        assertTrue(CountryDialCodes.allEntries().size >= 27)
    }
}
