package br.dev.ftdash.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prova que a seleção de offset por tamanho de frame é necessária, e não uma
 * precaução teórica: **ler o lambda no offset errado devolve lixo grosseiro**.
 */
class FrameLayoutTest {

    @Test
    fun `no layout de 107 o offset 65 e lixo`() {
        val frames = Fixtures.load(Fixtures.ROAD_107)
        val wrong = frames.map { FrameParser.u16be(it, 65) }
        assertEquals("o offset errado chega ao teto de 16 bits", 65_535, wrong.max())
        val outOfRange = wrong.count { it > 2000 }
        assertTrue("esperava muitos valores fora da faixa física", outOfRange > 5_000)
    }

    @Test
    fun `no layout de 111 o offset 61 e sempre zero`() {
        for (fixture in listOf(Fixtures.MOTOR_111, Fixtures.BENCH_111)) {
            val frames = Fixtures.load(fixture)
            assertTrue(
                "offset 61 deveria ser zero em $fixture",
                frames.all { FrameParser.u16be(it, 61) == 0 },
            )
        }
    }

    @Test
    fun `cada layout le o lambda numa faixa fisicamente plausivel`() {
        val road = Fixtures.load(Fixtures.ROAD_107).mapNotNull { FrameParser.parse(it)!!.lambda }
        assertTrue("mínimo de estrada", road.min() >= 0.6f)

        val motor = Fixtures.load(Fixtures.MOTOR_111).mapNotNull { FrameParser.parse(it)!!.lambda }
        assertTrue("máximo com motor ligado", motor.max() <= 5.0f)
    }

    @Test
    fun `forLength so reconhece os tamanhos confirmados`() {
        assertEquals(61, FrameLayout.forLength(107)!!.lambdaRealOffset)
        assertEquals(65, FrameLayout.forLength(111)!!.lambdaRealOffset)
        assertEquals(71, FrameLayout.forLength(111)!!.closedLoopProbeOffset)
        assertNull("107 não tem sonda de malha fechada", FrameLayout.forLength(107)!!.closedLoopProbeOffset)
        assertNull("tamanho novo não pode chutar", FrameLayout.forLength(109))
        assertNull(FrameLayout.forLength(263))
    }
}
