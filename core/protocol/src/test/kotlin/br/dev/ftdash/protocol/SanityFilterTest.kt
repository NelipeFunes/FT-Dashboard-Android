package br.dev.ftdash.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SanityFilterTest {

    private fun sample(
        rpm: Int = 900,
        vbat: Float = 13.0f,
        engineTempC: Float = 85f,
        lambda: Float? = 0.98f,
        mapBar: Float = -0.6f,
    ) = Telemetry(
        tsMs = 0, frameLen = 107, layoutKnown = true,
        rpm = rpm, tpsPct = 0f, mapBar = mapBar, airTempC = 25f, engineTempC = engineTempC,
        oilPressureBar = 3f, fuelPressureBar = 3f, vbat = vbat, injTimeMs = 2f, injDutyPct = 5f,
        dwellMs = 3f, ignitionDeg = 15f, idleActuatorPct = 50f,
        lambda = lambda, lambdaTarget = 1.0f, closedLoopPct = 0f, closedLoopProbe = null,
        cutoff = false, ecuIdleHint = false,
    )

    @Test
    fun `salto absurdo de rpm e rejeitado`() {
        val f = SanityFilter()
        assertEquals(800, f.apply(sample(rpm = 800)).rpm)
        // +5200 rpm num frame (60 ms) é ruído, não motor
        assertEquals(800, f.apply(sample(rpm = 6000)).rpm)
    }

    @Test
    fun `subida rapida mas plausivel passa`() {
        val f = SanityFilter()
        f.apply(sample(rpm = 800))
        assertEquals(4000, f.apply(sample(rpm = 4000)).rpm)
    }

    @Test
    fun `rejeicoes consecutivas destravam o canal`() {
        val f = SanityFilter()
        f.apply(sample(rpm = 800))
        // um degrau real de regime é rejeitado no começo...
        repeat(SanityFilter.MAX_CONSECUTIVE_REJECTS) {
            assertEquals(800, f.apply(sample(rpm = 6000)).rpm)
        }
        // ...mas não trava o mostrador para sempre
        assertEquals(6000, f.apply(sample(rpm = 6000)).rpm)
    }

    @Test
    fun `valor fora da faixa fisica e substituido pelo ultimo bom`() {
        val f = SanityFilter()
        assertEquals(13.0f, f.apply(sample(vbat = 13.0f)).vbat, 0.001f)
        // 0,82 V aparece de verdade nos fixtures durante a partida
        assertEquals(13.0f, f.apply(sample(vbat = 0.82f)).vbat, 0.001f)
    }

    @Test
    fun `map e tps nao tem limite de salto`() {
        val f = SanityFilter()
        f.apply(sample(mapBar = -0.9f))
        // pé no acelerador: o MAP salta de verdade e não pode ser filtrado
        assertEquals(0.4f, f.apply(sample(mapBar = 0.4f)).mapBar, 0.001f)
    }

    @Test
    fun `lambda invalido atravessa como null`() {
        val f = SanityFilter()
        f.apply(sample(lambda = 0.95f))
        // erro de sonda é informação, não ruído: continua null até o fim
        assertNull(f.apply(sample(lambda = null)).lambda)
    }

    @Test
    fun `telemetria real passa quase inteira`() {
        val f = SanityFilter()
        val frames = Fixtures.load(Fixtures.ROAD_107).take(5_000).map { FrameParser.parse(it)!! }
        frames.forEach { f.apply(it) }
        // Captura de estrada limpa: o filtro é rede de segurança, não deveria
        // estar corrigindo o tempo todo.
        assertTrue("substituições demais: ${f.rejected}", f.rejected < frames.size / 10)
    }
}
