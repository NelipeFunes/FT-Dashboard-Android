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

    private var lastSpeedTsMs: Long = 0
    private var lastFuelTsMs: Long = 0

    val state: TripState get() = TripState(totalKm, tripKm, fuelUsedLiters)

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
        if (kmh == null || kmh <= 0f || previous == 0L) return

        val dtMs = tsMs - previous
        if (dtMs < MIN_DT_MS || dtMs > MAX_DT_MS) return

        val km = kmh.toDouble() * (dtMs / 3_600_000.0)
        totalKm += km
        tripKm += km
    }

    /**
     * Integra o combustível consumido entre este frame e o anterior.
     *
     * @param dutyPct abertura dos bicos, 0-100
     */
    fun onInjection(tsMs: Long, dutyPct: Float, setup: FuelSetup) {
        val previous = lastFuelTsMs
        lastFuelTsMs = tsMs
        if (previous == 0L || !setup.isComplete) return
        if (dutyPct <= 0f || dutyPct > 100f) return

        val dtMs = tsMs - previous
        if (dtMs < 1 || dtMs > MAX_DT_MS) return

        // cc/min de todos os bicos com o duty atual, convertido para litros no
        // intervalo decorrido
        val ccPerMin = setup.injectorFlowCcMin * setup.injectorCount * (dutyPct / 100.0)
        fuelUsedLiters += ccPerMin * (dtMs / 60_000.0) / 1000.0
    }

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

    /** Botão "enchi o tanque": zera o consumo acumulado. */
    fun fillTank() {
        fuelUsedLiters = 0.0
    }

    /** Zera só o parcial; o total nunca é zerado por aqui. */
    fun resetTrip() {
        tripKm = 0.0
    }

    /** Recarrega o estado persistido, sem perder as marcas de tempo. */
    fun restore(state: TripState) {
        totalKm = state.totalKm
        tripKm = state.tripKm
        fuelUsedLiters = state.fuelUsedLiters
    }

    companion object {
        /** Abaixo disso o deslocamento é menor que o ruído do GPS. */
        const val MIN_DT_MS = 100L

        /** Acima disso a amostra anterior não diz mais nada sobre o intervalo. */
        const val MAX_DT_MS = 5_000L
    }
}
