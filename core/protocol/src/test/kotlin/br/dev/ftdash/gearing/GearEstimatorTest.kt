package br.dev.ftdash.gearing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cenários sintéticos que reproduzem o que o carro faz de verdade. O que se
 * testa aqui não é "acha a marcha certa" — é **não mostrar marcha errada** e
 * **não piscar**, que é o que estraga um painel.
 */
class GearEstimatorTest {

    /** Razões de um Civic com relações típicas: 1ª bem curta, 5ª longa. */
    private val profile = GearProfile(
        ratios = listOf(
            GearRatio(1, 123.0, RatioSource.MANUAL, 0),
            GearRatio(2, 72.0, RatioSource.MANUAL, 0),
            GearRatio(3, 50.0, RatioSource.MANUAL, 0),
            GearRatio(4, 39.0, RatioSource.MANUAL, 0),
            GearRatio(5, 32.0, RatioSource.MANUAL, 0),
        ),
    )

    /**
     * Simula um trecho: alimenta RPM a ~17 Hz e fixações de GPS a 1 Hz.
     * Devolve o estado depois de cada fixação.
     */
    private fun drive(
        estimator: GearEstimator,
        seconds: Int,
        startMs: Long = 0,
        rpmAt: (Long) -> Int,
        kmhAt: (Long) -> Float?,
    ): List<GearState> {
        val states = ArrayList<GearState>()
        var t = startMs
        repeat(seconds) {
            repeat(17) {
                estimator.onRpm(t, rpmAt(t))
                t += 59
            }
            states += estimator.onSpeed(t, kmhAt(t))
        }
        return states
    }

    @Test
    fun `terceira constante fica estavel`() {
        val e = GearEstimator(profile)
        val states = drive(e, 10, rpmAt = { 3000 }, kmhAt = { 60f })

        // 3ª = 50 rpm/km/h → 3000/60 = 50,0 exato
        val last = states.last()
        assertTrue("esperava 3ª engatada, veio $last", last is GearState.Engaged && last.gear == 3)

        // depois de engatar, não pode mais sair
        val afterEngage = states.dropWhile { it !is GearState.Engaged }
        assertTrue("piscou depois de engatar", afterEngage.all { it is GearState.Engaged && it.gear == 3 })
    }

    @Test
    fun `troca de terceira para quarta nunca mostra marcha errada`() {
        val e = GearEstimator(profile)
        // 3ª a 90 km/h (4500 rpm) → troca → 4ª a 90 km/h (3510 rpm)
        val shiftAtMs = 6_000L
        val states = drive(
            e, 12,
            rpmAt = { t -> if (t < shiftAtMs) 4500 else 3510 },
            kmhAt = { 90f },
        )

        val gears = states.filterIsInstance<GearState.Engaged>().map { it.gear }.distinct()
        assertEquals("só 3ª e 4ª podem aparecer", listOf(3, 4), gears)
        assertTrue("deveria terminar em 4ª", (states.last() as GearState.Engaged).gear == 4)
    }

    @Test
    fun `embreagem pisada vira ponto morto`() {
        val e = GearEstimator(profile)
        // 4ª a 100 km/h, depois embreagem: rotação despenca para a lenta e a
        // velocidade se mantém — nenhuma marcha casa.
        val clutchAtMs = 5_000L
        val states = drive(
            e, 14,
            rpmAt = { t -> if (t < clutchAtMs) 3900 else 900 },
            kmhAt = { 100f },
        )

        assertTrue("esperava N no fim, veio ${states.last()}", states.last() is GearState.Neutral)
        // e passou por Shifting antes de admitir o ponto morto
        assertTrue(states.any { it is GearState.Shifting })
    }

    @Test
    fun `parado e desconhecido`() {
        val e = GearEstimator(profile)
        val states = drive(e, 5, rpmAt = { 850 }, kmhAt = { 0f })
        assertTrue(states.all { it is GearState.Unknown })
    }

    @Test
    fun `sem fixacao de GPS e desconhecido`() {
        val e = GearEstimator(profile)
        val states = drive(e, 5, rpmAt = { 3000 }, kmhAt = { null })
        assertTrue(states.all { it is GearState.Unknown })
    }

    @Test
    fun `perfil sem calibracao nao arrisca palpite`() {
        val e = GearEstimator(GearProfile())
        val states = drive(e, 5, rpmAt = { 3000 }, kmhAt = { 60f })
        assertTrue(states.all { it is GearState.Unknown })
    }

    @Test
    fun `ruido de meio por cento no GPS nao tira a marcha`() {
        val e = GearEstimator(profile)
        var i = 0
        val states = drive(
            e, 20,
            rpmAt = { 3000 },
            kmhAt = { 60f + (if (i++ % 2 == 0) 0.3f else -0.3f) },
        )
        val engaged = states.filterIsInstance<GearState.Engaged>()
        assertTrue("engatou pouco: ${engaged.size} de ${states.size}", engaged.size > 15)
        assertTrue(engaged.all { it.gear == 3 })
    }

    @Test
    fun `nao engata numa fixacao isolada`() {
        val e = GearEstimator(profile)
        // uma única fixação boa no meio de velocidade zerada
        e.onRpm(0, 3000)
        val s = e.onSpeed(100, 60f)
        assertTrue("uma amostra não pode engatar marcha, veio $s", s !is GearState.Engaged)
    }

    @Test
    fun `pareamento usa a media de rpm da janela e nao o instantaneo`() {
        val e = GearEstimator(profile)
        // RPM oscilando forte em torno de 3000; a média da janela é que vale
        var k = 0
        drive(e, 10, rpmAt = { if (k++ % 2 == 0) 3300 else 2700 }, kmhAt = { 60f })
        val ratio = e.instantRatio!!
        assertEquals("razão deveria ficar em ~50, veio $ratio", 50.0, ratio, 2.5)
    }
}
