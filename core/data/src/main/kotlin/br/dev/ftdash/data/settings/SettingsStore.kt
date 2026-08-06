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
import br.dev.ftdash.trip.FuelSetup
import br.dev.ftdash.trip.TripState
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
/** Como a escala da barra de RPM é definida. */
enum class RpmScaleMode {
    /**
     * Igual à FT: o teto é sempre o RPM mais alto já registrado. Se o motor
     * nunca passou de 4.300, a barra vai até 4.300; no dia que passar, o novo
     * valor vira o teto e fica.
     */
    AUTO,

    /** Corte e escala digitados à mão. */
    MANUAL,
}

data class AppSettings(
    val gearProfile: GearProfile = GearProfile(),
    val rpmScaleMode: RpmScaleMode = RpmScaleMode.AUTO,
    /** Pico já registrado — a base do modo automático. Persistido. */
    val learnedMaxRpm: Int = 0,
    /** Usados só no modo manual; 0 = não configurado. */
    val redlineRpm: Int = 0,
    val shiftRpm: Int = 0,
    val maxRpm: Int = 0,
    val fuelSetup: FuelSetup = FuelSetup(),
    val trip: TripState = TripState(),
    val sourceKind: SourceKind = SourceKind.REPLAY,
    val replaySpeed: Float = 1.0f,
    /** Com o replay, sintetiza velocidade a partir do RPM em vez de usar o GPS. */
    val useSimulatedSpeed: Boolean = true,
) {
    /**
     * Escala e limites que a barra de RPM realmente usa.
     *
     * No automático os três saem do pico aprendido; enquanto não houver pico
     * (motor nunca ligado com o app aberto), usa [FLOOR_RPM] só para a barra
     * ter uma escala qualquer em vez de dividir por zero.
     */
    val effectiveMaxRpm: Int
        get() = when (rpmScaleMode) {
            RpmScaleMode.AUTO -> learnedMaxRpm.coerceAtLeast(FLOOR_RPM)
            RpmScaleMode.MANUAL -> maxRpm.takeIf { it > 0 } ?: FLOOR_RPM
        }

    val effectiveRedlineRpm: Int
        get() = when (rpmScaleMode) {
            RpmScaleMode.AUTO -> effectiveMaxRpm
            RpmScaleMode.MANUAL -> redlineRpm.takeIf { it > 0 } ?: effectiveMaxRpm
        }

    val effectiveShiftRpm: Int
        get() = when (rpmScaleMode) {
            RpmScaleMode.AUTO -> (effectiveRedlineRpm * AUTO_SHIFT_FRACTION).toInt()
            RpmScaleMode.MANUAL -> shiftRpm.takeIf { it > 0 } ?: effectiveRedlineRpm
        }

    companion object {
        /** Escala mínima, só para a barra existir antes do primeiro pico. */
        const val FLOOR_RPM = 3_000

        /** No automático o aviso de troca vem um pouco antes do pico registrado. */
        const val AUTO_SHIFT_FRACTION = 0.95f
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
            rpmScaleMode = prefs[KEY_RPM_MODE]
                ?.let { runCatching { RpmScaleMode.valueOf(it) }.getOrNull() }
                ?: RpmScaleMode.AUTO,
            learnedMaxRpm = prefs[KEY_LEARNED_MAX] ?: 0,
            redlineRpm = prefs[KEY_REDLINE] ?: 0,
            shiftRpm = prefs[KEY_SHIFT] ?: 0,
            maxRpm = prefs[KEY_MAX_RPM] ?: 0,
            sourceKind = prefs[KEY_SOURCE]
                ?.let { runCatching { SourceKind.valueOf(it) }.getOrNull() }
                ?: SourceKind.REPLAY,
            fuelSetup = FuelSetup(
                tankLiters = prefs[KEY_TANK_LITERS]?.toDouble() ?: 0.0,
                injectorFlowCcMin = prefs[KEY_INJECTOR_FLOW]?.toDouble() ?: 0.0,
                injectorCount = prefs[KEY_INJECTOR_COUNT] ?: 0,
            ),
            trip = TripState(
                totalKm = prefs[KEY_TOTAL_KM]?.toDouble() ?: 0.0,
                tripKm = prefs[KEY_TRIP_KM]?.toDouble() ?: 0.0,
                fuelUsedLiters = prefs[KEY_FUEL_USED]?.toDouble() ?: 0.0,
                kmSinceFill = prefs[KEY_KM_SINCE_FILL]?.toDouble() ?: 0.0,
            ),
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

    suspend fun saveRpmScaleMode(mode: RpmScaleMode) {
        context.dataStore.edit { it[KEY_RPM_MODE] = mode.name }
    }

    /**
     * Grava um novo pico. Só sobe: o teto aprendido não recua sozinho, é o que
     * a FT faz e é o que o usuário espera de "o mais alto já registrado".
     * Para recuar existe [resetLearnedMaxRpm].
     */
    suspend fun saveLearnedMaxRpm(rpm: Int) {
        context.dataStore.edit {
            val current = it[KEY_LEARNED_MAX] ?: 0
            if (rpm > current) it[KEY_LEARNED_MAX] = rpm
        }
    }

    suspend fun resetLearnedMaxRpm() {
        context.dataStore.edit { it[KEY_LEARNED_MAX] = 0 }
    }

    suspend fun saveFuelSetup(setup: FuelSetup) {
        context.dataStore.edit {
            it[KEY_TANK_LITERS] = setup.tankLiters.toFloat()
            it[KEY_INJECTOR_FLOW] = setup.injectorFlowCcMin.toFloat()
            it[KEY_INJECTOR_COUNT] = setup.injectorCount
        }
    }

    /**
     * Odômetro e combustível.
     *
     * Guardados como float: em km, um float tem ~0,01 km de resolução perto de
     * 100.000 km, o que é bem mais fino do que a precisão do próprio GPS. Quem
     * chama é que controla a frequência — ver o throttle no ViewModel.
     */
    suspend fun saveTrip(trip: TripState) {
        context.dataStore.edit {
            it[KEY_TOTAL_KM] = trip.totalKm.toFloat()
            it[KEY_TRIP_KM] = trip.tripKm.toFloat()
            it[KEY_FUEL_USED] = trip.fuelUsedLiters.toFloat()
            it[KEY_KM_SINCE_FILL] = trip.kmSinceFill.toFloat()
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
        val KEY_RPM_MODE = stringPreferencesKey("rpm_scale_mode")
        val KEY_LEARNED_MAX = intPreferencesKey("learned_max_rpm")
        val KEY_REDLINE = intPreferencesKey("redline_rpm")
        val KEY_SHIFT = intPreferencesKey("shift_rpm")
        val KEY_MAX_RPM = intPreferencesKey("max_rpm")
        val KEY_SOURCE = stringPreferencesKey("source_kind")
        val KEY_TANK_LITERS = floatPreferencesKey("tank_liters")
        val KEY_INJECTOR_FLOW = floatPreferencesKey("injector_flow_cc_min")
        val KEY_INJECTOR_COUNT = intPreferencesKey("injector_count")
        val KEY_TOTAL_KM = floatPreferencesKey("odo_total_km")
        val KEY_TRIP_KM = floatPreferencesKey("odo_trip_km")
        val KEY_FUEL_USED = floatPreferencesKey("fuel_used_liters")
        val KEY_KM_SINCE_FILL = floatPreferencesKey("km_since_fill")
        val KEY_REPLAY_SPEED = floatPreferencesKey("replay_speed")
        val KEY_SIMULATED_SPEED = intPreferencesKey("use_simulated_speed")
    }
}
