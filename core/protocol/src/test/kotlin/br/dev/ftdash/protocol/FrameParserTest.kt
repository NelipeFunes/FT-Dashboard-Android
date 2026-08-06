package br.dev.ftdash.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Trava as faixas **medidas** nos fixtures reais. Qualquer regressão de offset
 * ou de escala sai daqui como falha imediata, não como número esquisito no
 * painel do carro.
 */
class FrameParserTest {

    @Test
    fun `estrutura de todos os frames`() {
        for (fixture in Fixtures.all) {
            for (frame in Fixtures.load(fixture)) {
                assertEquals(
                    "byte 6 auto-descritivo em $fixture",
                    frame.size - 9,
                    frame[6].toInt() and 0xFF,
                )
                assertTrue("frame válido em $fixture", FrameValidator.isValidFrame(frame, 0, frame.size))
            }
        }
    }

    @Test
    fun `faixas medidas na captura de estrada de 107 bytes`() {
        val frames = Fixtures.load(Fixtures.ROAD_107).map { FrameParser.parse(it)!! }
        assertEquals(24_250, frames.size)
        assertTrue(frames.all { it.layoutKnown && it.frameLen == 107 })

        assertRange("RPM", frames.map { it.rpm.toDouble() }, 0.0, 5560.0)
        assertRange("TPS", frames.map { it.tpsPct.toDouble() }, 0.0, 100.0)
        assertRange("MAP", frames.map { it.mapBar.toDouble() }, -0.933, -0.006)
        assertRange("Vbat", frames.map { it.vbat.toDouble() }, 8.43, 13.34)
        assertRange("temp motor", frames.map { it.engineTempC.toDouble() }, 71.3, 83.9)
        assertRange("ponto", frames.map { it.ignitionDeg.toDouble() }, -15.0, 40.6)

        // Ponto de ignição negativo existe de verdade (batente de retardo) — por
        // isso o campo é s16be, não u16be.
        assertTrue("esperava ponto negativo na captura", frames.any { it.ignitionDeg < 0f })

        // 18,2 % dos frames trazem o código de erro de sonda (bruto 9990).
        assertEquals("frames com lambda inválido", 4_416, frames.count { it.lambda == null })

        // A sonda de malha fechada não existe nesta config.
        assertTrue(frames.all { it.closedLoopProbe == null })

        // Corte na desaceleração: evento comum numa puxada de estrada.
        assertEquals(7_563, frames.count { it.cutoff })
    }

    @Test
    fun `faixas medidas na captura de motor ligado de 111 bytes`() {
        val frames = Fixtures.load(Fixtures.MOTOR_111).map { FrameParser.parse(it)!! }
        assertEquals(6_982, frames.size)
        assertTrue(frames.all { it.layoutKnown && it.frameLen == 111 })

        assertRange("RPM", frames.map { it.rpm.toDouble() }, 0.0, 2101.0)
        assertRange("temp motor", frames.map { it.engineTempC.toDouble() }, 14.2, 61.7)

        // Nesta config o lambda vem do offset 65 e a sonda de malha fechada do 71,
        // que acompanha o lambda de perto (difere em <0,02 λ em 98,8 % dos frames).
        assertTrue(frames.all { it.lambda != null })
        assertTrue(frames.all { it.closedLoopProbe != null })
        val divergent = frames.count { kotlin.math.abs(it.lambda!! - it.closedLoopProbe!!) > 0.02f }
        assertTrue("sonda de malha fechada deveria acompanhar o lambda", divergent < 200)
    }

    @Test
    fun `captura de bancada tem motor parado`() {
        val frames = Fixtures.load(Fixtures.BENCH_111).map { FrameParser.parse(it)!! }
        assertEquals(2_467, frames.size)
        assertTrue("motor desligado", frames.all { it.rpm == 0 })
        assertRange("lambda", frames.mapNotNull { it.lambda?.toDouble() }, 1.777, 1.826)
    }

    @Test
    fun `frame de tamanho desconhecido decodifica sem lambda em vez de chutar offset`() {
        // Um frame de 107 B remontado com tamanho declarado diferente: o parser
        // tem que devolver o bloco comum e null no lambda, nunca ler 65.535 λ.
        val original = Fixtures.load(Fixtures.ROAD_107).first()
        val forged = forgeWithLength(original, 109)

        val t = FrameParser.parse(forged)
        assertNotNull(t)
        assertEquals(false, t!!.layoutKnown)
        assertNull("layout desconhecido não pode chutar lambda", t.lambda)
        // O bloco comum continua confiável.
        assertEquals(FrameParser.parse(original)!!.rpm, t.rpm)
    }

    @Test
    fun `frame curto demais e recusado`() {
        val short = ByteArray(40) { 0 }
        short[0] = FrameValidator.SYNC
        assertNull(FrameParser.parse(short))
    }

    /**
     * Cria um frame sintético de outro tamanho, com cabeçalho e CRC coerentes —
     * simula uma config de FTManager que ainda não mapeamos.
     */
    private fun forgeWithLength(source: ByteArray, len: Int): ByteArray {
        val out = ByteArray(len)
        source.copyInto(out, 0, 0, minOf(source.size, len) - 2)
        out[6] = (len - 9).toByte()
        val crc = Crc16Kermit.compute(out, 1, len - 2)
        Crc16Kermit.toLittleEndian(crc).copyInto(out, len - 2)
        return out
    }

    private fun assertRange(name: String, values: List<Double>, min: Double, max: Double) {
        val actualMin = values.min()
        val actualMax = values.max()
        assertEquals("$name mínimo", min, actualMin, 0.051)
        assertEquals("$name máximo", max, actualMax, 0.051)
    }
}
