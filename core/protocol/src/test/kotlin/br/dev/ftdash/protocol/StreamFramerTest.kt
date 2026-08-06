package br.dev.ftdash.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O framer é o que separa "funciona no fixture" de "funciona no fio". No
 * Android o endpoint tem pacotes de 64 B e os frames têm 107/111 B, então
 * nenhuma leitura devolve um frame inteiro e alinhado.
 */
class StreamFramerTest {

    private fun concat(frames: List<ByteArray>): ByteArray {
        val out = ByteArray(frames.sumOf { it.size })
        var i = 0
        for (f in frames) {
            f.copyInto(out, i)
            i += f.size
        }
        return out
    }

    private fun feedInChunks(framer: StreamFramer, data: ByteArray, chunk: Int): List<Telemetry> {
        val out = ArrayList<Telemetry>()
        var i = 0
        while (i < data.size) {
            val n = minOf(chunk, data.size - i)
            out += framer.feed(data.copyOfRange(i, i + n), n)
            i += n
        }
        return out
    }

    @Test
    fun `stream cortado em pacotes de 64 bytes recupera todos os frames`() {
        val frames = Fixtures.load(Fixtures.ROAD_107).take(500)
        val framer = StreamFramer()

        val decoded = feedInChunks(framer, concat(frames), 64)

        assertEquals(500, decoded.size)
        assertEquals(500L, framer.framesOk)
        assertEquals("stream limpo não deveria precisar de re-sync", 0L, framer.resyncs)
        assertEquals(0L, framer.crcFail)
        assertEquals(FrameParser.parse(frames[0])!!.rpm, decoded[0].rpm)
        assertEquals(FrameParser.parse(frames[499])!!.rpm, decoded[499].rpm)
    }

    @Test
    fun `lixo injetado entre frames nao faz perder frames bons`() {
        val frames = Fixtures.load(Fixtures.ROAD_107).take(200)
        val noise = byteArrayOf(0xAA.toByte(), 0x13, 0x37)

        val parts = ArrayList<ByteArray>()
        frames.forEachIndexed { i, f ->
            parts += f
            // um 0xAA falso a cada 10 frames: o pior caso para o re-sync
            if (i % 10 == 0) parts += noise
        }

        val framer = StreamFramer()
        val decoded = feedInChunks(framer, concat(parts), 64)

        assertEquals(200, decoded.size)
        assertTrue("deveria ter re-sincronizado", framer.resyncs > 0)
    }

    @Test
    fun `frame corrompido e descartado sem derrubar os vizinhos`() {
        val frames = Fixtures.load(Fixtures.ROAD_107).take(50).map { it.copyOf() }.toMutableList()
        // corrompe o payload do frame do meio, mantendo cabeçalho e tamanho
        frames[25][FrameLayout.RPM] = (frames[25][FrameLayout.RPM] + 7).toByte()

        val framer = StreamFramer()
        val decoded = feedInChunks(framer, concat(frames), 64)

        assertEquals("só o frame corrompido se perde", 49, decoded.size)
        assertTrue(framer.crcFail > 0)
    }

    @Test
    fun `stream de 263 bytes que nao e telemetria e ignorado`() {
        // A ECU intercala no mesmo endpoint um segundo stream com outro cabeçalho.
        val junk = ByteArray(263) { 0x5A }
        junk[0] = FrameValidator.SYNC

        val frames = Fixtures.load(Fixtures.ROAD_107).take(10)
        val framer = StreamFramer()
        val decoded = feedInChunks(framer, concat(listOf(junk) + frames), 64)

        assertEquals(10, decoded.size)
    }

    @Test
    fun `frame partido entre duas leituras espera o resto`() {
        val frame = Fixtures.load(Fixtures.ROAD_107).first()
        val framer = StreamFramer()

        assertTrue("frame incompleto não pode ser emitido", framer.feed(frame.copyOfRange(0, 60), 60).isEmpty())
        val rest = framer.feed(frame.copyOfRange(60, frame.size), frame.size - 60)
        assertEquals(1, rest.size)
    }

    @Test
    fun `os dois layouts convivem no mesmo framer`() {
        val mixed = Fixtures.load(Fixtures.ROAD_107).take(20) + Fixtures.load(Fixtures.MOTOR_111).take(20)
        val framer = StreamFramer()
        val decoded = feedInChunks(framer, concat(mixed), 64)

        assertEquals(40, decoded.size)
        assertEquals(20, decoded.count { it.frameLen == 107 })
        assertEquals(20, decoded.count { it.frameLen == 111 })
        assertTrue(decoded.all { it.layoutKnown })
    }
}
