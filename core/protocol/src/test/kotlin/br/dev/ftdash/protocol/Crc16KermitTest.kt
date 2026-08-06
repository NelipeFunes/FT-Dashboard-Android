package br.dev.ftdash.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Linha de base do protocolo: os 33.699 frames reais dos três fixtures passam
 * no CRC16/KERMIT com **zero falhas**. Se este teste quebrar, a fórmula do CRC
 * ou o recorte de bytes mudou — nada mais adianta.
 */
class Crc16KermitTest {

    @Test
    fun `todos os frames dos fixtures passam no CRC`() {
        var total = 0
        var failures = 0
        for (fixture in Fixtures.all) {
            for (frame in Fixtures.load(fixture)) {
                total++
                if (!FrameValidator.hasValidCrc(frame, 0, frame.size)) failures++
            }
        }
        assertTrue("esperava dezenas de milhares de frames, veio $total", total > 30_000)
        assertEquals("frames com CRC inválido", 0, failures)
    }

    @Test
    fun `CRC conhecido do primeiro frame de estrada`() {
        val frame = Fixtures.load(Fixtures.ROAD_107).first()
        // o frame termina em "cd19", ou seja 0x19cd em little-endian
        assertEquals(0x19cd, Crc16Kermit.compute(frame, 1, frame.size - 2))
    }

    @Test
    fun `um byte corrompido invalida o frame`() {
        val frame = Fixtures.load(Fixtures.ROAD_107).first().copyOf()
        assertTrue(FrameValidator.isValidFrame(frame, 0, frame.size))
        frame[FrameLayout.RPM] = (frame[FrameLayout.RPM] + 1).toByte()
        assertFalse(FrameValidator.isValidFrame(frame, 0, frame.size))
    }

    @Test
    fun `CRC vai para o fio em little-endian`() {
        assertArrayEquals(byteArrayOf(0xcd.toByte(), 0x19), Crc16Kermit.toLittleEndian(0x19cd))
    }
}
