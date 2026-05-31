package si.merhar.roamer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "roamer_prefs")

/**
 * Persists user preferences using Jetpack DataStore.
 */
class PreferencesRepository(private val context: Context) {

    companion object {
        val KEY_ENABLED = booleanPreferencesKey("enabled")
        val KEY_MANUAL_COUNTRY = stringPreferencesKey("manual_country")
        val KEY_LAST_LOG = stringPreferencesKey("last_log")
    }

    /** Whether call rewriting is enabled. */
    val enabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ENABLED] ?: true
    }

    /** Manual country override (empty string means auto-detect). */
    val manualCountry: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_MANUAL_COUNTRY] ?: ""
    }

    /** Recent rewrite log entries (newline-separated, most recent first). */
    val rewriteLog: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_LOG] ?: ""
    }

    suspend fun setEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_ENABLED] = value }
    }

    suspend fun setManualCountry(countryIso: String) {
        context.dataStore.edit { it[KEY_MANUAL_COUNTRY] = countryIso }
    }

    /**
     * Appends a log entry, keeping at most [maxEntries] lines.
     */
    suspend fun appendLog(entry: String, maxEntries: Int = 20) {
        context.dataStore.edit { prefs ->
            val existing = prefs[KEY_LAST_LOG] ?: ""
            val lines = existing.lines().filter { it.isNotBlank() }.toMutableList()
            lines.add(0, entry)
            if (lines.size > maxEntries) {
                lines.subList(maxEntries, lines.size).clear()
            }
            prefs[KEY_LAST_LOG] = lines.joinToString("\n")
        }
    }

    /** Blocking read of enabled state (for use in the service). */
    suspend fun isEnabled(): Boolean = enabled.first()

    /** Blocking read of manual country override. */
    suspend fun getManualCountry(): String = manualCountry.first()
}
