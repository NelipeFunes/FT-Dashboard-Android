package br.dev.ftdash.gearing

import kotlin.math.abs

sealed interface GearState {
    /** Sem informação suficiente: parado, GPS sem fixação, motor abaixo da lenta. */
    data object Unknown : GearState

    /** Nenhuma marcha casa: embreagem pisada ou ponto morto. */
    data object Neutral : GearState

    /** Trocando — segura a última marcha para o mostrador não piscar. */
    data class Shifting(val lastGear: Int) : GearState

    /** Marcha engatada, com o erro relativo do casamento (0,03 = 3 %). */
    data class Engaged(val gear: Int, val error: Double) : GearState
}

/**
 * Estima a marcha engatada cruzando o RPM da ECU com a velocidade do GPS.
 *
 * Três detalhes fazem a diferença entre um mostrador estável e um que pisca
 * feito árvore de Natal:
 *
 * 1. **Pareamento de taxas.** O RPM chega a ~17 Hz e o GPS a ~1 Hz. Casar o RPM
 *    instantâneo com a velocidade faz a razão tremer ~10 % sozinha, porque as
 *    duas amostras são de instantes diferentes. Aqui cada fixação de GPS é
 *    casada com a **média de RPM da janela de ±300 ms** em torno do timestamp
 *    dela.
 *
 * 2. **Histerese assimétrica.** Entra numa marcha com erro < [GearProfile.enterTolerance]
 *    e sai só quando o erro passa de [GearProfile.exitTolerance]. A banda morta
 *    entre as duas é o que impede a oscilação entre marchas de relação próxima
 *    (3ª e 4ª).
 *
 * 3. **Confirmação por tempo, não por contagem.** A taxa do GPS varia muito
 *    entre aparelhos (1 Hz numa multimídia barata, 5-10 Hz num chip bom).
 *    Exigir "N amostras" daria 4 s de atraso num caso e 0,4 s no outro, então a
 *    confirmação é [ENTER_HOLD_MS] de candidato estável (com um mínimo de 2
 *    amostras para não engatar numa fixação isolada).
 *
 * Não há sensor de embreagem nem de ponto morto: os dois são deduzidos do
 * comportamento — nada casando dentro da tolerância de saída vira
 * [GearState.Shifting], que segura a última marcha por [SHIFT_HOLD_MS] (o
 * tempo de uma troca) e depois cai para [GearState.Neutral].
 *
 * Não é thread-safe — uma instância por ViewModel.
 */
class GearEstimator(
    @Volatile var profile: GearProfile,
) {

    private val rpmTs = LongArray(RPM_WINDOW)
    private val rpmVal = IntArray(RPM_WINDOW)
    private var rpmCount = 0
    private var rpmHead = 0

    private var candidateGear: Int? = null
    private var candidateSinceMs = 0L
    private var candidateSamples = 0

    private var shiftingSinceMs = 0L

    var state: GearState = GearState.Unknown
        private set

    /** Última razão medida (rpm/km/h) — a tela de calibração mostra isso ao vivo. */
    var instantRatio: Double? = null
        private set

    /** Alimenta o histórico de RPM, na taxa da telemetria. */
    fun onRpm(tsMs: Long, rpm: Int) {
        rpmTs[rpmHead] = tsMs
        rpmVal[rpmHead] = rpm
        rpmHead = (rpmHead + 1) % RPM_WINDOW
        if (rpmCount < RPM_WINDOW) rpmCount++
    }

    /**
     * Processa uma fixação de GPS e devolve o novo estado.
     *
     * @param kmh null quando não há fixação ou ela está obsoleta
     */
    fun onSpeed(tsMs: Long, kmh: Float?): GearState {
        val rpm = averageRpmAround(tsMs)

        if (kmh == null || kmh < MIN_KMH || rpm == null || rpm < MIN_RPM) {
            instantRatio = null
            resetCandidate()
            state = GearState.Unknown
            return state
        }

        // A razão é medida ANTES de checar a calibração: é justamente o número
        // que a tela de aprendizado mostra ao vivo, e ali o perfil ainda está
        // vazio por definição. Só o casamento de marcha depende do perfil.
        val ratio = rpm / kmh.toDouble()
        instantRatio = ratio

        if (!profile.isCalibrated) {
            resetCandidate()
            state = GearState.Unknown
            return state
        }

        val best = profile.ratios
            .filter { it.rpmPerKmh > 0 }
            .minByOrNull { abs(ratio - it.rpmPerKmh) / it.rpmPerKmh }

        if (best == null) {
            resetCandidate()
            state = GearState.Unknown
            return state
        }
        val bestError = abs(ratio - best.rpmPerKmh) / best.rpmPerKmh

        // 1. A marcha atual ainda serve? Sai só além da tolerância de saída.
        val current = (state as? GearState.Engaged)?.gear
        if (current != null) {
            val currentRatio = profile.ratioFor(current)
            val currentError = currentRatio
                ?.let { abs(ratio - it.rpmPerKmh) / it.rpmPerKmh }
                ?: Double.MAX_VALUE
            if (currentError <= profile.exitTolerance) {
                resetCandidate()
                state = GearState.Engaged(current, currentError)
                return state
            }
            // saiu da marcha: assume troca em curso e segura o mostrador
            shiftingSinceMs = tsMs
            state = GearState.Shifting(current)
        }

        // 2. Candidato precisa se manter estável antes de virar marcha engatada.
        if (bestError <= profile.enterTolerance) {
            if (candidateGear == best.gear) {
                candidateSamples++
            } else {
                candidateGear = best.gear
                candidateSinceMs = tsMs
                candidateSamples = 1
            }
            val heldMs = tsMs - candidateSinceMs
            if (candidateSamples >= MIN_CONFIRM_SAMPLES && heldMs >= ENTER_HOLD_MS) {
                resetCandidate()
                state = GearState.Engaged(best.gear, bestError)
                return state
            }
        } else {
            resetCandidate()
        }

        // 3. Nada casou. Segura a última marcha durante a troca, depois N.
        state = when (val s = state) {
            // Enquanto houver candidato em confirmação, continua segurando a
            // última marcha: piscar um N no meio de uma troca é pior do que
            // atrasar a nova marcha em meio segundo.
            is GearState.Shifting ->
                if (candidateGear == null && tsMs - shiftingSinceMs > SHIFT_HOLD_MS) {
                    GearState.Neutral
                } else {
                    s
                }

            is GearState.Engaged -> {
                shiftingSinceMs = tsMs
                GearState.Shifting(s.gear)
            }

            else -> GearState.Neutral
        }
        return state
    }

    fun reset() {
        rpmCount = 0
        rpmHead = 0
        resetCandidate()
        instantRatio = null
        state = GearState.Unknown
    }

    private fun resetCandidate() {
        candidateGear = null
        candidateSamples = 0
        candidateSinceMs = 0
    }

    /**
     * Média do RPM na janela de ±[PAIR_WINDOW_MS] em torno de [tsMs]. Devolve
     * null se não houver amostra nenhuma na janela (telemetria caiu).
     */
    private fun averageRpmAround(tsMs: Long): Double? {
        var sum = 0L
        var n = 0
        for (i in 0 until rpmCount) {
            if (abs(rpmTs[i] - tsMs) <= PAIR_WINDOW_MS) {
                sum += rpmVal[i]
                n++
            }
        }
        return if (n == 0) null else sum.toDouble() / n
    }

    companion object {
        /** ~2 s de histórico a 17 Hz, folga suficiente para a janela de pareamento. */
        const val RPM_WINDOW = 48
        const val PAIR_WINDOW_MS = 300L

        /** Abaixo disso a razão rpm/km/h é ruído puro. */
        const val MIN_KMH = 8f
        const val MIN_RPM = 600.0

        const val ENTER_HOLD_MS = 1200L
        const val MIN_CONFIRM_SAMPLES = 2

        /** Tempo de uma troca de marcha — segura o mostrador antes de mostrar N. */
        const val SHIFT_HOLD_MS = 600L
    }
}
