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
    /** Vazão nominal de UM bico, em cc/min, na pressão de referência. */
    val injectorFlowCcMin: Double = 0.0,
    val injectorCount: Int = 0,
    /**
     * Pressão diferencial em que a vazão nominal foi medida.
     *
     * 3 bar é a convenção com que praticamente todo bico é especificado.
     */
    val ratedPressureBar: Double = 3.0,
) {
    val isComplete: Boolean
        get() = tankLiters > 0 && injectorFlowCcMin > 0 && injectorCount > 0

    companion object {
        /**
         * lb/h → cc/min, na convenção da indústria: gasolina a 0,72 g/cc.
         *
         * `453,592 g/lb ÷ 0,72 g/cc ÷ 60 min/h ≈ 10,5`
         *
         * Existe porque a FuelTech trabalha em **lb/h**, não em cc/min — o
         * FTManager, o site e o material de treinamento dela usam lb/h. Obrigar
         * a converter à mão antes de digitar é convite a erro de conta num
         * número que multiplica todo o cálculo de combustível.
         *
         * A conversão é de massa para volume, então depende da densidade. O
         * fator abaixo é o da gasolina, que é como os bicos são especificados.
         * Com etanol o volume real é ~9% maior para a mesma massa — quem usa
         * etanol deve digitar direto em cc/min.
         */
        const val LB_H_TO_CC_MIN = 10.5

        fun lbPerHourToCcMin(lbh: Double): Double = lbh * LB_H_TO_CC_MIN
    }
}

/** Estado acumulado do computador de bordo. Persistido entre sessões. */
@Serializable
data class TripState(
    val totalKm: Double = 0.0,
    val tripKm: Double = 0.0,
    /**
     * Litros queimados na viagem atual — o denominador da média.
     *
     * **Só cresce**, e zera junto com [tripKm]. É o par que dá sentido à média:
     * distância e combustível contados a partir do mesmo instante.
     */
    val tripFuelLiters: Double = 0.0,
    /**
     * Litros queimados desde o último abastecimento — define o nível do tanque.
     *
     * **Diminui ao abastecer.** É por isso que existem dois contadores de
     * consumo: se este fosse também o denominador da média, colocar 10 litros
     * faria a média saltar sozinha, porque o denominador encolheria sem que
     * ninguém tivesse andado nada.
     */
    val tankUsedLiters: Double = 0.0,
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

    var tripFuelLiters: Double = initial.tripFuelLiters
        private set

    var tankUsedLiters: Double = initial.tankUsedLiters
        private set

    private var lastSpeedTsMs: Long = 0
    private var lastFuelTsMs: Long = 0

    /** Vazão atual em L/h, suavizada — a base da média instantânea. */
    private var smoothedFuelLh: Double? = null

    /** Velocidade suavizada com a mesma constante de tempo da vazão. */
    private var smoothedKmh: Double? = null

    val state: TripState get() = TripState(totalKm, tripKm, tripFuelLiters, tankUsedLiters)

    /**
     * Média da viagem atual, em km/L — parcial dividido pelo combustível
     * queimado no mesmo trecho.
     *
     * Zera junto com o parcial e **não é afetada por abastecer**: encher o
     * tanque muda quanto ainda há para andar, não quanto o carro fez por litro
     * no caminho até aqui.
     *
     * Null enquanto não houver combustível suficiente contado: nos primeiros
     * metros a divisão explode (100 m gastando 5 ml daria 20 km/L), e um
     * número desses no painel é pior que nenhum.
     */
    val averageKmPerLiter: Double?
        get() = if (tripFuelLiters < MIN_FUEL_FOR_AVERAGE) null
        else tripKm / tripFuelLiters

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
    }

    /**
     * Integra o combustível consumido entre este frame e o anterior.
     *
     * @param dutyPct abertura dos bicos, 0-100
     */
    /**
     * Corrige a vazão pela pressão diferencial real sobre o bico.
     *
     * O bico é uma válvula liga/desliga: aberto, ele passa uma vazão fixa pelo
     * orifício, e é o **tempo aberto** que muda com a aceleração. Mas essa
     * vazão fixa depende da diferença de pressão entre a linha e o coletor, e
     * escoamento por orifício vai com a raiz da diferença:
     *
     * ```
     * vazão_real = vazão_nominal · √(ΔP_real / ΔP_nominal)
     * ```
     *
     * Neste carro isso importa. Medido nos 20.895 frames de estrada: a pressão
     * da linha fica praticamente cravada em 3,30 bar (desvio de 0,05), ou seja
     * o regulador **não** é referenciado ao coletor. Como o MAP varia de −0,93
     * a −0,01 bar, o diferencial oscila de 3,19 a 4,47 bar — e a vazão real
     * junto: √(4,47/3,19) = 1,18, ou seja **18% entre os extremos**. Ignorar
     * isso seria errar sempre para o mesmo lado, consumindo mais em marcha
     * lenta (muito vácuo) do que a conta admitiria.
     *
     * Sem leitura de pressão utilizável, devolve a vazão nominal sem inventar
     * correção.
     */
    private fun effectiveFlowCcMin(setup: FuelSetup, differentialBar: Double?): Double {
        if (differentialBar == null || setup.ratedPressureBar <= 0) return setup.injectorFlowCcMin
        if (differentialBar !in MIN_DIFFERENTIAL_BAR..MAX_DIFFERENTIAL_BAR) return setup.injectorFlowCcMin
        return setup.injectorFlowCcMin * kotlin.math.sqrt(differentialBar / setup.ratedPressureBar)
    }

    /**
     * @param differentialBar pressão da linha menos a do coletor, em bar; null
     *        quando não há leitura confiável
     */
    fun onInjection(tsMs: Long, dutyPct: Float, setup: FuelSetup, differentialBar: Double? = null) {
        val previous = lastFuelTsMs
        lastFuelTsMs = tsMs
        if (!setup.isComplete) return
        if (dutyPct < 0f || dutyPct > 100f) return

        // cc/min de todos os bicos com o duty atual
        val ccPerMin = effectiveFlowCcMin(setup, differentialBar) *
            setup.injectorCount * (dutyPct / 100.0)
        // O duty ZERO também entra na suavização: é o corte na desaceleração, e
        // ignorá-lo faria a média instantânea travar no último valor gasto em
        // vez de mostrar que o motor parou de consumir.
        smoothedFuelLh = smooth(smoothedFuelLh, ccPerMin * 60.0 / 1000.0)

        if (previous == 0L || dutyPct <= 0f) return
        val dtMs = tsMs - previous
        if (dtMs < 1 || dtMs > MAX_DT_MS) return
        val liters = ccPerMin * (dtMs / 60_000.0) / 1000.0
        tripFuelLiters += liters
        tankUsedLiters += liters
    }

    private fun smooth(current: Double?, sample: Double): Double =
        current?.let { it + SMOOTHING * (sample - it) } ?: sample

    /** Litros que ainda devem estar no tanque, ou null se falta configuração. */
    fun remainingLiters(setup: FuelSetup): Double? {
        if (!setup.isComplete) return null
        return (setup.tankLiters - tankUsedLiters).coerceAtLeast(0.0)
    }

    /** Fração do tanque, 0..1 — é o que a barra desenha. */
    fun remainingFraction(setup: FuelSetup): Float? {
        val liters = remainingLiters(setup) ?: return null
        return (liters / setup.tankLiters).toFloat().coerceIn(0f, 1f)
    }

    /** Botão "enchi o tanque": o nível volta ao topo. */
    fun fillTank() {
        tankUsedLiters = 0.0
    }

    /**
     * Abastecimento parcial: soma [liters] ao nível atual.
     *
     * Nunca ultrapassa a capacidade — pôr 20 litros num tanque que só tinha
     * espaço para 12 resulta em cheio, não em 8 litros de crédito escondido
     * que fariam o mostrador mentir por uma semana.
     *
     * **Não mexe na média.** Combustível que entra no tanque não muda quanto o
     * carro fez por litro no caminho até aqui; quem zera a média é o parcial.
     */
    fun addFuel(liters: Double, setup: FuelSetup) {
        if (liters <= 0.0) return
        tankUsedLiters = (tankUsedLiters - liters).coerceAtLeast(0.0)
    }

    /**
     * Define quanto há no tanque **agora**, em litros.
     *
     * Diferente de [addFuel]: aquele soma ao que a conta acha que existe, este
     * substitui a conta. É o que se usa quando se sabe o nível de verdade —
     * olhando a boia do carro, ou depois de uma parada em que o app estava
     * desligado e o consumo não foi contado.
     *
     * Como corrige a estimativa em vez de registrar um abastecimento, também
     * **não mexe na média**: quanto o carro fez por litro no caminho até aqui
     * não muda porque alguém acertou o medidor.
     */
    fun setRemainingLiters(liters: Double, setup: FuelSetup) {
        if (!setup.isComplete || liters < 0.0) return
        tankUsedLiters = (setup.tankLiters - liters).coerceIn(0.0, setup.tankLiters)
    }

    /**
     * Zera o parcial e a média junto — os dois são a mesma viagem.
     *
     * O total e o nível do tanque não são tocados.
     */
    fun resetTrip() {
        tripKm = 0.0
        tripFuelLiters = 0.0
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
        tankUsedLiters = state.tankUsedLiters

        // Parcial gravado sem combustível correspondente é um par impossível:
        // só acontece vindo de uma versão que ainda não contava o combustível
        // da viagem. Deixar assim faria a média dividir uma distância antiga
        // por um consumo novo e dar um número absurdo e permanente — 36 km
        // sobre os primeiros 0,5 L dariam 72 km/L. O parcial recomeça; o
        // odômetro total nunca é afetado.
        val inconsistent = state.tripKm > 0.0 && state.tripFuelLiters <= 0.0
        tripKm = if (inconsistent) 0.0 else state.tripKm
        tripFuelLiters = if (inconsistent) 0.0 else state.tripFuelLiters
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

        /** Faixa em que um diferencial lido faz sentido; fora dela, não corrige. */
        const val MIN_DIFFERENTIAL_BAR = 1.0
        const val MAX_DIFFERENTIAL_BAR = 10.0
    }
}
