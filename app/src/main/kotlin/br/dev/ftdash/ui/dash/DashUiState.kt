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
    /** km/L desde o último abastecimento; null até haver consumo suficiente. */
    val averageKmPerLiter: Double? = null,
    /** km/L agora, suavizado; null parado ou sem configuração. */
    val instantKmPerLiter: Double? = null,
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
    val bytesPerSec: Int = 0,
    val framesOk: Long = 0,
    val crcFail: Long = 0,
    val frameLen: Int = 0,
    val layoutKnown: Boolean = true,
    /** Ainda não chegou nenhum frame: a tela mostra `--` em tudo. */
    val hasData: Boolean = false,
    /**
     * A fonte caiu mas o último quadro ainda está na tela.
     *
     * Existe porque no carro a conexão cai a cada poucos segundos ao acelerar,
     * e apagar tudo para reacender 2 s depois deixa o painel pior que inútil —
     * pisca tanto que não dá para ler nada. Segurar o último quadro por alguns
     * segundos cobre a reconexão inteira e a tela fica legível.
     *
     * O que não se pode é fingir que o número é atual: enquanto isto for
     * verdade o painel inteiro aparece esmaecido, e a barra de status diz o
     * que está acontecendo. Dado velho identificado é informação; dado velho
     * disfarçado de vivo é mentira.
     */
    val stale: Boolean = false,
)
