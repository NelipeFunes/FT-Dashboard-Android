package br.dev.ftdash.protocol

/**
 * Rejeita valores fisicamente impossíveis e saltos absurdos entre frames,
 * substituindo pelo último valor bom.
 *
 * Faixas e limites de salto copiados de
 * `ft-tuning-assistant/src/shared/constants.ts` (`sanity_ranges` / `jump_limits`),
 * que foram calibrados contra meses de log real. MAP e TPS ficam **sem limite de
 * salto de propósito**: os dois mudam de verdade num piscar (pé no acelerador).
 *
 * O detalhe que evita o modo de falha clássico: depois de [MAX_CONSECUTIVE_REJECTS]
 * rejeições seguidas do mesmo canal, o novo valor é aceito. Senão um único valor
 * bom seguido de mudança legítima de regime travaria o mostrador para sempre.
 *
 * Não é thread-safe — uma instância por fonte.
 */
class SanityFilter {

    private class Guard(
        val min: Float,
        val max: Float,
        /** null = sem limite de salto. */
        val maxJump: Float?,
    ) {
        var last: Float? = null
        var rejects = 0

        fun accept(value: Float): Float {
            val prev = last
            val inRange = !value.isNaN() && value >= min && value <= max
            val jumpOk = prev == null || maxJump == null || kotlin.math.abs(value - prev) <= maxJump

            if (inRange && jumpOk) {
                last = value
                rejects = 0
                return value
            }

            rejects++
            // Um valor fisicamente plausível que insiste deixa de ser ruído e
            // passa a ser mudança de regime: aceita e segue. Já um valor fora
            // da faixa física nunca é aceito, por mais que se repita.
            if (inRange && rejects > MAX_CONSECUTIVE_REJECTS) {
                last = value
                rejects = 0
                return value
            }
            return prev ?: value
        }
    }

    private val rpm = Guard(0f, 12_000f, 4_000f)
    private val map = Guard(-1.0f, 4.0f, null)
    private val tps = Guard(0f, 100f, null)
    private val lambda = Guard(0f, 2f, 0.3f)
    private val lambdaTarget = Guard(0f, 2f, 0.3f)
    private val engineTemp = Guard(-20f, 150f, 20f)
    private val airTemp = Guard(-20f, 120f, null)
    private val vbat = Guard(6f, 20f, 3f)
    private val closedLoop = Guard(-60f, 60f, 15f)
    private val ignition = Guard(-30f, 60f, null)
    private val idleActuator = Guard(0f, 100f, null)

    /** Quantos valores foram substituídos desde o início da sessão. */
    var rejected = 0L
        private set

    fun apply(t: Telemetry): Telemetry {
        val before = snapshot(t)
        val filtered = t.copy(
            rpm = rpm.accept(t.rpm.toFloat()).toInt(),
            mapBar = map.accept(t.mapBar),
            tpsPct = tps.accept(t.tpsPct),
            // lambda null (erro de sonda) atravessa intacto: não é ruído, é informação.
            lambda = t.lambda?.let { lambda.accept(it) },
            lambdaTarget = lambdaTarget.accept(t.lambdaTarget),
            engineTempC = engineTemp.accept(t.engineTempC),
            airTempC = airTemp.accept(t.airTempC),
            vbat = vbat.accept(t.vbat),
            closedLoopPct = closedLoop.accept(t.closedLoopPct),
            ignitionDeg = ignition.accept(t.ignitionDeg),
            idleActuatorPct = idleActuator.accept(t.idleActuatorPct),
        )
        if (snapshot(filtered) != before) rejected++
        return filtered
    }

    private fun snapshot(t: Telemetry) = listOf(
        t.rpm.toFloat(), t.mapBar, t.tpsPct, t.lambda ?: -1f, t.lambdaTarget,
        t.engineTempC, t.airTempC, t.vbat, t.closedLoopPct, t.ignitionDeg, t.idleActuatorPct,
    )

    companion object {
        const val MAX_CONSECUTIVE_REJECTS = 10
    }
}
