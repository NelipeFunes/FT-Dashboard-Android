package br.dev.ftdash.data

import br.dev.ftdash.gearing.GearProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

/**
 * Velocidade sintética a partir do RPM do replay.
 *
 * O fixture gravado tem RPM mas não tem velocidade — ele veio da ECU, e a ECU
 * do carro não recebe o sensor de roda. Sem isto, marcha, histerese e a tela de
 * calibração só dariam para testar dirigindo.
 *
 * A simulação escolhe uma marcha e a troca quando o RPM sobe demais ou cai
 * demais, exatamente o que o motorista faz — então o [br.dev.ftdash.gearing.GearEstimator]
 * enfrenta trocas de verdade, com o transitório e tudo.
 */
class SimulatedSpeedSource(
    @Volatile var profile: GearProfile,
) : SpeedSource {

    @Volatile
    private var latestRpm: Int = 0

    /** O ViewModel chama isto a cada frame de telemetria. */
    fun onRpm(rpm: Int) {
        latestRpm = rpm
    }

    override fun stream(): Flow<SpeedFix> = flow {
        var gear = 1
        while (true) {
            val ratios = profile.ratios.sortedBy { it.gear }
            val rpm = latestRpm

            val kmh = if (ratios.isEmpty() || rpm < 500) {
                null
            } else {
                gear = gear.coerceIn(ratios.first().gear, ratios.last().gear)
                if (rpm > UPSHIFT_RPM && gear < ratios.last().gear) gear++
                if (rpm < DOWNSHIFT_RPM && gear > ratios.first().gear) gear--

                val ratio = ratios.first { it.gear == gear }.rpmPerKmh
                val base = rpm / ratio
                // ±0,4 km/h de ruído: é a ordem de grandeza de um GPS civil
                (base + Random.nextDouble(-0.4, 0.4)).toFloat().coerceAtLeast(0f)
            }

            emit(SpeedFix(System.currentTimeMillis(), kmh, hasGpsFix = kmh != null))
            delay(FIX_INTERVAL_MS)
        }
    }

    companion object {
        /** 1 Hz, o pior caso realista de multimídia. */
        const val FIX_INTERVAL_MS = 1_000L
        const val UPSHIFT_RPM = 4_200
        const val DOWNSHIFT_RPM = 1_600
    }
}
