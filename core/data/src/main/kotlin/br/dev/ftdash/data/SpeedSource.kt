package br.dev.ftdash.data

import kotlinx.coroutines.flow.Flow

/**
 * Uma fixação de velocidade. [kmh] null significa "sem fixação confiável" — a
 * UI mostra `--` e o estimador de marcha entra em `Unknown`, em vez de fingir
 * que o carro parou.
 */
data class SpeedFix(
    val tsMs: Long,
    val kmh: Float?,
    val hasGpsFix: Boolean,
    val satellites: Int? = null,
)

interface SpeedSource {
    fun stream(): Flow<SpeedFix>
}
