package br.dev.ftdash.protocol

/**
 * Um frame de telemetria decodificado. Só leitura — este app não escreve nada
 * na ECU.
 *
 * `lambda` e `closedLoopProbe` são nullable de propósito:
 *  - 18,2 % dos frames reais de estrada trazem o valor bruto 9990, que é o
 *    código de erro/sonda fria da FuelTech. Num painel de carro isso tem que
 *    virar `--`, nunca "9.99";
 *  - `closedLoopProbe` só existe na config de 111 B;
 *  - num frame de tamanho desconhecido o lambda não é decodificado.
 */
data class Telemetry(
    val tsMs: Long,
    val frameLen: Int,
    /** false quando o tamanho do frame não bate com nenhum layout conhecido. */
    val layoutKnown: Boolean,

    val rpm: Int,
    val tpsPct: Float,
    val mapBar: Float,
    val airTempC: Float,
    val engineTempC: Float,
    val oilPressureBar: Float,
    val fuelPressureBar: Float,
    val vbat: Float,
    val injTimeMs: Float,
    val injDutyPct: Float,
    val dwellMs: Float,
    val ignitionDeg: Float,
    val idleActuatorPct: Float,

    val lambda: Float?,
    val lambdaTarget: Float,
    val closedLoopPct: Float,
    val closedLoopProbe: Float?,

    val cutoff: Boolean,
    /** Hipótese não confirmada (bit0 do byte 54); só para diagnóstico. */
    val ecuIdleHint: Boolean,
) {
    companion object {
        /** Valor bruto que a FuelTech usa para "sonda inválida". */
        const val LAMBDA_ERROR_RAW = 9990
    }
}
