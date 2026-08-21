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
        val KEY_USE_LOCAL_SIM = booleanPreferencesKey("use_local_sim")
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

    /** Whether to route local calls through a local SIM when roaming. */
    val useLocalSim: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_USE_LOCAL_SIM] ?: false
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

    suspend fun setUseLocalSim(value: Boolean) {
        context.dataStore.edit { it[KEY_USE_LOCAL_SIM] = value }
    }

    /**
     * Prepends a log entry, keeping at most [maxEntries] lines.
     *
     * Retention is delegated to [RewriteLog.prepend] so the rules stay unit-testable.
     */
    suspend fun appendLog(entry: String, maxEntries: Int = RewriteLog.MAX_ENTRIES) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_LOG] = RewriteLog.prepend(prefs[KEY_LAST_LOG] ?: "", entry, maxEntries)
        }
    }

    /**
     * Reads the current enabled state once.
     *
     * Suspends rather than blocking; the service wraps these single-shot reads in
     * `runBlocking` because it must answer Telecom on the calling thread.
     */
    suspend fun isEnabled(): Boolean = enabled.first()

    /** Reads the manual country override once. */
    suspend fun getManualCountry(): String = manualCountry.first()

    /** Reads the use-local-SIM preference once. */
    suspend fun isUseLocalSim(): Boolean = useLocalSim.first()
}
