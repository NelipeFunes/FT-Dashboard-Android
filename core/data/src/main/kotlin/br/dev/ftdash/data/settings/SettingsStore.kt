package br.dev.ftdash.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.dev.ftdash.data.SourceKind
import br.dev.ftdash.gearing.GearProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Estado persistente do app.
 *
 * O perfil de marchas vai como **um blob JSON numa chave só**, não como Proto
 * DataStore: são poucos campos, mudam raramente e a serialização já existe.
 * Trocar isso por um schema tipado seria cerimônia sem retorno.
 */
data class AppSettings(
    val gearProfile: GearProfile = GearProfile(),
    val redlineRpm: Int = DEFAULT_REDLINE,
    val shiftRpm: Int = DEFAULT_SHIFT,
    val maxRpm: Int = DEFAULT_MAX_RPM,
    val sourceKind: SourceKind = SourceKind.REPLAY,
    val replaySpeed: Float = 1.0f,
    /** Com o replay, sintetiza velocidade a partir do RPM em vez de usar o GPS. */
    val useSimulatedSpeed: Boolean = true,
) {
    companion object {
        const val DEFAULT_REDLINE = 6_500
        const val DEFAULT_SHIFT = 6_200
        const val DEFAULT_MAX_RPM = 8_000
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ft_dash_settings")

class SettingsStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            gearProfile = prefs[KEY_GEAR_PROFILE]
                ?.let { runCatching { json.decodeFromString<GearProfile>(it) }.getOrNull() }
                ?: GearProfile(),
            redlineRpm = prefs[KEY_REDLINE] ?: AppSettings.DEFAULT_REDLINE,
            shiftRpm = prefs[KEY_SHIFT] ?: AppSettings.DEFAULT_SHIFT,
            maxRpm = prefs[KEY_MAX_RPM] ?: AppSettings.DEFAULT_MAX_RPM,
            sourceKind = prefs[KEY_SOURCE]
                ?.let { runCatching { SourceKind.valueOf(it) }.getOrNull() }
                ?: SourceKind.REPLAY,
            replaySpeed = prefs[KEY_REPLAY_SPEED] ?: 1.0f,
            useSimulatedSpeed = (prefs[KEY_SIMULATED_SPEED] ?: 1) == 1,
        )
    }

    suspend fun saveGearProfile(profile: GearProfile) {
        context.dataStore.edit { it[KEY_GEAR_PROFILE] = json.encodeToString(profile) }
    }

    suspend fun saveRpmLimits(redline: Int, shift: Int, max: Int) {
        context.dataStore.edit {
            it[KEY_REDLINE] = redline
            it[KEY_SHIFT] = shift
            it[KEY_MAX_RPM] = max
        }
    }

    suspend fun saveSourceKind(kind: SourceKind) {
        context.dataStore.edit { it[KEY_SOURCE] = kind.name }
    }

    suspend fun saveReplaySpeed(speed: Float) {
        context.dataStore.edit { it[KEY_REPLAY_SPEED] = speed }
    }

    suspend fun saveUseSimulatedSpeed(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SIMULATED_SPEED] = if (enabled) 1 else 0 }
    }

    private companion object {
        val KEY_GEAR_PROFILE = stringPreferencesKey("gear_profile_json")
        val KEY_REDLINE = intPreferencesKey("redline_rpm")
        val KEY_SHIFT = intPreferencesKey("shift_rpm")
        val KEY_MAX_RPM = intPreferencesKey("max_rpm")
        val KEY_SOURCE = stringPreferencesKey("source_kind")
        val KEY_REPLAY_SPEED = floatPreferencesKey("replay_speed")
        val KEY_SIMULATED_SPEED = intPreferencesKey("use_simulated_speed")
    }
}
