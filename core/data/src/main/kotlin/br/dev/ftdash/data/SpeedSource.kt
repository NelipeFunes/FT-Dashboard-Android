package br.dev.ftdash.data

import kotlinx.coroutines.flow.Flow

/**
 * De onde saiu a velocidade. Existe para o painel **nunca chamar de GPS um
 * número que não veio do GPS** — num carro, velocidade inventada com cara de
 * medida é pior do que velocidade nenhuma.
 */
enum class SpeedOrigin {
    /** Fixação de verdade do GPS do aparelho. */
    GPS,

    /** Sintetizada a partir do RPM do replay, para trabalhar na bancada. */
    SIMULATED,

    /** Sem fixação: sem permissão, sem sinal, ou a última é velha demais. */
    NONE,
}

/**
 * Uma fixação de velocidade. [kmh] null significa "sem valor confiável" — a UI
 * mostra `--` e o estimador de marcha entra em `Unknown`, em vez de fingir que
 * o carro parou.
 */
data class SpeedFix(
    val tsMs: Long,
    val kmh: Float?,
    val origin: SpeedOrigin,
    val satellites: Int? = null,
)

interface SpeedSource {
    fun stream(): Flow<SpeedFix>
}
