package si.merhar.roamer

import androidx.datastore.preferences.core.mutablePreferencesOf
import si.merhar.roamer.PreferencesRepository.Companion.KEY_USE_LOCAL_SIM
import si.merhar.roamer.PreferencesRepository.Companion.KEY_USE_NUMBER_COUNTRY_SIM
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SimRoutingPreferencesTest {

    @Test
    fun `enabling local SIM turns number-country SIM off`() {
        val prefs = mutablePreferencesOf(KEY_USE_NUMBER_COUNTRY_SIM to true)
        prefs.setSimRouting(KEY_USE_LOCAL_SIM, true)
        assertEquals(true, prefs[KEY_USE_LOCAL_SIM])
        assertEquals(false, prefs[KEY_USE_NUMBER_COUNTRY_SIM])
    }

    @Test
    fun `enabling number-country SIM turns local SIM off`() {
        val prefs = mutablePreferencesOf(KEY_USE_LOCAL_SIM to true)
        prefs.setSimRouting(KEY_USE_NUMBER_COUNTRY_SIM, true)
        assertEquals(true, prefs[KEY_USE_NUMBER_COUNTRY_SIM])
        assertEquals(false, prefs[KEY_USE_LOCAL_SIM])
    }

    @Test
    fun `disabling one leaves the other as it was`() {
        val prefs = mutablePreferencesOf(KEY_USE_LOCAL_SIM to true)
        prefs.setSimRouting(KEY_USE_NUMBER_COUNTRY_SIM, false)
        assertEquals(true, prefs[KEY_USE_LOCAL_SIM])
        assertEquals(false, prefs[KEY_USE_NUMBER_COUNTRY_SIM])
    }

    @Test
    fun `disabling one does not create the other`() {
        val prefs = mutablePreferencesOf()
        prefs.setSimRouting(KEY_USE_LOCAL_SIM, false)
        assertNull(prefs[KEY_USE_NUMBER_COUNTRY_SIM])
    }
}
