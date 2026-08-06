package br.dev.ftdash.trip

import kotlinx.serialization.Serializable

/**
 * Dados do carro que o app não tem como descobrir sozinho.
 *
 * A vazão do bico é a peça que falta para calcular consumo: sem ela dá para
 * saber quanto tempo o bico ficou aberto, mas não quanto combustível passou.
 * Vem da especificação do bico (normalmente em cc/min a 3 bar).
 */
@Serializable
data class FuelSetup(
    val tankLiters: Double = 0.0,
    /** Vazão nominal de UM bico, em cc/min. */
    val injectorFlowCcMin: Double = 0.0,
    val injectorCount: Int = 0,
) {
    val isComplete: Boolean
        get() = tankLiters > 0 && injectorFlowCcMin > 0 && injectorCount > 0
}

/** Estado acumulado do computador de bordo. Persistido entre sessões. */
@Serializable
data class TripState(
    val totalKm: Double = 0.0,
    val tripKm: Double = 0.0,
    /** Litros queimados desde o último "enchi o tanque". */
    val fuelUsedLiters: Double = 0.0,
    /**
     * Distância desde o último "enchi o tanque".
     *
     * Separada do parcial de propósito: a média só faz sentido se distância e
     * combustível forem contados a partir do **mesmo** instante. Dividir o
     * parcial (que você zera quando quiser) pelo consumo do tanque daria um
     * número sem significado nenhum.
     */
    val kmSinceFill: Double = 0.0,
)

/**
 * Odômetro e medidor de combustível.
 *
 * **Distância** sai da velocidade do GPS integrada no tempo. Não é hodômetro de
 * precisão — erro de GPS, túnel e perda de sinal entram na conta —, mas para
 * medir uma viagem serve, e é a única fonte disponível: a ECU deste carro não
 * recebe sensor de roda.
 *
 * **Combustível** sai do duty dos bicos. Com a vazão nominal de cada bico e a
 * quantidade deles, o volume por hora é `vazão × bicos × duty`. É uma
 * aproximação com dois limites conhecidos: usa a vazão nominal (a real varia
 * com a pressão de combustível, que a ECU até informa mas cuja curva do bico
 * não temos) e ignora o combustível do enriquecimento de partida. Erra para
 * menos em pressão alta e para mais em pressão baixa.
 *
 * Por isso o tanque é do tipo "zerei ao abastecer": o usuário aperta o botão ao
 * encher, e o mostrador conta para trás. Um erro de 5 % em 40 litros é 2
 * litros — aceitável para saber que está na reserva, não para chegar no
 * lacrado.
 *
 * Não é thread-safe: uma instância por ViewModel.
 */
class TripComputer(initial: TripState = TripState()) {

    var totalKm: Double = initial.totalKm
        private set

    var tripKm: Double = initial.tripKm
        private set

    var fuelUsedLiters: Double = initial.fuelUsedLiters
        private set

    var kmSinceFill: Double = initial.kmSinceFill
        private set

    private var lastSpeedTsMs: Long = 0
    private var lastFuelTsMs: Long = 0

    /** Vazão atual em L/h, suavizada — a base da média instantânea. */
    private var smoothedFuelLh: Double? = null

    /** Velocidade suavizada com a mesma constante de tempo da vazão. */
    private var smoothedKmh: Double? = null

    val state: TripState get() = TripState(totalKm, tripKm, fuelUsedLiters, kmSinceFill)

    /**
     * Média desde o último abastecimento, em km/L.
     *
     * Null enquanto não houver combustível suficiente contado: nos primeiros
     * metros a divisão explode (100 m gastando 5 ml daria 20 km/L), e um
     * número desses no painel é pior que nenhum.
     */
    val averageKmPerLiter: Double?
        get() = if (fuelUsedLiters < MIN_FUEL_FOR_AVERAGE) null
        else kmSinceFill / fuelUsedLiters

    /**
     * Consumo agora, em km/L.
     *
     * Sai da velocidade dividida pela vazão instantânea dos bicos, os dois
     * suavizados com a mesma constante de tempo — se só um fosse filtrado, o
     * quociente daria picos a cada mudança de regime.
     *
     * Casos que não são número:
     * - parado ou quase (abaixo de [MIN_KMH_FOR_INSTANT]): km/L não significa
     *   nada com o carro parado, e a divisão tende a zero;
     * - em corte na desaceleração o consumo é literalmente zero e o resultado
     *   seria infinito — aí o valor satura em [MAX_INSTANT_KM_L], que é como os
     *   computadores de bordo de fábrica se comportam.
     */
    val instantKmPerLiter: Double?
        get() {
            val kmh = smoothedKmh ?: return null
            val lh = smoothedFuelLh ?: return null
            if (kmh < MIN_KMH_FOR_INSTANT) return null
            if (lh < MIN_FLOW_LH) return MAX_INSTANT_KM_L
            return (kmh / lh).coerceAtMost(MAX_INSTANT_KM_L)
        }

    /**
     * Integra a distância entre esta fixação e a anterior.
     *
     * Usa a velocidade da fixação atual sobre o intervalo decorrido. Intervalos
     * fora de [MIN_DT_MS]..[MAX_DT_MS] são descartados: abaixo do mínimo o
     * ruído domina, e acima dele a fixação anterior é velha demais para dizer
     * qualquer coisa sobre o que aconteceu no meio (túnel, GPS perdido, app em
     * segundo plano).
     */
    fun onSpeedFix(tsMs: Long, kmh: Float?) {
        val previous = lastSpeedTsMs
        lastSpeedTsMs = tsMs
        if (kmh == null) {
            smoothedKmh = null
            return
        }
        smoothedKmh = smooth(smoothedKmh, kmh.toDouble())
        if (kmh <= 0f || previous == 0L) return

        val dtMs = tsMs - previous
        if (dtMs < MIN_DT_MS || dtMs > MAX_DT_MS) return

        val km = kmh.toDouble() * (dtMs / 3_600_000.0)
        totalKm += km
        tripKm += km
        kmSinceFill += km
    }

    /**
     * Integra o combustível consumido entre este frame e o anterior.
     *
     * @param dutyPct abertura dos bicos, 0-100
     */
    fun onInjection(tsMs: Long, dutyPct: Float, setup: FuelSetup) {
        val previous = lastFuelTsMs
        lastFuelTsMs = tsMs
        if (!setup.isComplete) return
        if (dutyPct < 0f || dutyPct > 100f) return

        // cc/min de todos os bicos com o duty atual
        val ccPerMin = setup.injectorFlowCcMin * setup.injectorCount * (dutyPct / 100.0)
        // O duty ZERO também entra na suavização: é o corte na desaceleração, e
        // ignorá-lo faria a média instantânea travar no último valor gasto em
        // vez de mostrar que o motor parou de consumir.
        smoothedFuelLh = smooth(smoothedFuelLh, ccPerMin * 60.0 / 1000.0)

        if (previous == 0L || dutyPct <= 0f) return
        val dtMs = tsMs - previous
        if (dtMs < 1 || dtMs > MAX_DT_MS) return
        fuelUsedLiters += ccPerMin * (dtMs / 60_000.0) / 1000.0
    }

    private fun smooth(current: Double?, sample: Double): Double =
        current?.let { it + SMOOTHING * (sample - it) } ?: sample

    /** Litros que ainda devem estar no tanque, ou null se falta configuração. */
    fun remainingLiters(setup: FuelSetup): Double? {
        if (!setup.isComplete) return null
        return (setup.tankLiters - fuelUsedLiters).coerceAtLeast(0.0)
    }

    /** Fração do tanque, 0..1 — é o que a barra desenha. */
    fun remainingFraction(setup: FuelSetup): Float? {
        val liters = remainingLiters(setup) ?: return null
        return (liters / setup.tankLiters).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Botão "enchi o tanque": zera consumo e distância juntos.
     *
     * Os dois têm que zerar no mesmo instante, senão a média passa a dividir
     * uma distância antiga por um consumo novo.
     */
    fun fillTank() {
        fuelUsedLiters = 0.0
        kmSinceFill = 0.0
    }

    /** Zera só o parcial; o total nunca é zerado por aqui. */
    fun resetTrip() {
        tripKm = 0.0
    }

    /** Recarrega o estado persistido, sem perder as marcas de tempo. */
    /**
     * Recarrega o estado persistido, sem perder as marcas de tempo.
     *
     * Consumo gravado sem distância correspondente é um par impossível: só
     * acontece quando o estado vem de uma versão que ainda não contava a
     * distância desde o abastecimento. Dividir um pelo outro daria uma média
     * absurda e permanente — 36 km divididos por 6,7 L acumulados noutra
     * sessão dão 0,2 km/L, e o número ficaria assim até o próximo
     * abastecimento. Nesse caso o par é descartado e vale como tanque cheio;
     * é uma vez só, na atualização, e o usuário re-referencia no primeiro
     * abastecimento de qualquer forma.
     */
    fun restore(state: TripState) {
        totalKm = state.totalKm
        tripKm = state.tripKm
        val inconsistent = state.fuelUsedLiters > 0.0 && state.kmSinceFill <= 0.0
        fuelUsedLiters = if (inconsistent) 0.0 else state.fuelUsedLiters
        kmSinceFill = if (inconsistent) 0.0 else state.kmSinceFill
    }

    companion object {
        /** Abaixo disso o deslocamento é menor que o ruído do GPS. */
        const val MIN_DT_MS = 100L

        /** Acima disso a amostra anterior não diz mais nada sobre o intervalo. */
        const val MAX_DT_MS = 5_000L

        /** Meio litro: antes disso a média ainda é ruído dividido por ruído. */
        const val MIN_FUEL_FOR_AVERAGE = 0.5

        /** Filtro exponencial da média instantânea, aplicado à vazão e à velocidade. */
        const val SMOOTHING = 0.15

        const val MIN_KMH_FOR_INSTANT = 5.0
        const val MIN_FLOW_LH = 0.05

        /** Teto do mostrador em corte, onde o consumo é zero e a conta é infinita. */
        const val MAX_INSTANT_KM_L = 99.9
    }
}
