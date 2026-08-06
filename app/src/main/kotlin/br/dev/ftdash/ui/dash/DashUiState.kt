package br.dev.ftdash.ui.dash

import br.dev.ftdash.data.SourceKind
import br.dev.ftdash.data.SourceState
import br.dev.ftdash.data.SpeedOrigin
import br.dev.ftdash.gearing.GearState

/**
 * Tudo que a tela desenha, num objeto só.
 *
 * Os campos são **primitivos de propósito**: passando `Float`/`Int` para cada
 * composable filho, o skipping do Compose faz cada mostrador recompor apenas
 * quando o próprio número dele muda. Passar o objeto inteiro para os filhos
 * recomporia a tela toda 17 vezes por segundo.
 */
data class DashUiState(
    val rpm: Int = 0,
    val peakRpm: Int = 0,
    val tpsPct: Float = 0f,
    val mapBar: Float = 0f,
    val lambda: Float? = null,
    val lambdaTarget: Float = 0f,
    val closedLoopPct: Float = 0f,
    val ignitionDeg: Float = 0f,
    val injTimeMs: Float = 0f,
    val injDutyPct: Float = 0f,
    val idleActuatorPct: Float = 0f,

    val engineTempC: Float = 0f,
    val airTempC: Float = 0f,
    val oilPressureBar: Float = 0f,
    val fuelPressureBar: Float = 0f,
    val vbat: Float = 0f,
    val cutoff: Boolean = false,

    val speedKmh: Float? = null,
    val speedOrigin: SpeedOrigin = SpeedOrigin.NONE,
    val gear: GearState = GearState.Unknown,
    val gearCalibrated: Boolean = false,

    val totalKm: Double = 0.0,
    val tripKm: Double = 0.0,
    /** null quando falta configurar tanque, vazão do bico ou quantidade. */
    val fuelRemainingLiters: Double? = null,
    val fuelRemainingFraction: Float? = null,
    val tankLiters: Double = 0.0,

    val redlineRpm: Int = 6_500,
    val shiftRpm: Int = 6_200,
    val maxRpm: Int = 8_000,

    val sourceKind: SourceKind = SourceKind.REPLAY,
    val sourceState: SourceState = SourceState.IDLE,
    val sourceDetail: String? = null,
    val hz: Float = 0f,
    val framesOk: Long = 0,
    val crcFail: Long = 0,
    val frameLen: Int = 0,
    val layoutKnown: Boolean = true,
    /** Ainda não chegou nenhum frame: a tela mostra `--` em tudo. */
    val hasData: Boolean = false,
)
