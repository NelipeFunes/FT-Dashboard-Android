package br.dev.ftdash.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Ft450ProtocolTest {

    @Test
    fun `comando de configuracao tem 135 bytes divididos em 128 mais 7`() {
        val (first, second) = Ft450Protocol.buildConfigCommand(0x1234)
        assertEquals(128, first.size)
        assertEquals(7, second.size)
        assertEquals(135, first.size + second.size)
    }

    @Test
    fun `CRC do comando confere com o do app Electron`() {
        // Valor de referência conferido contra a implementação TypeScript de
        // ft450Protocol.ts para o token 0x1234: CRC 0x01f5, no fio "f5 01".
        val (first, second) = Ft450Protocol.buildConfigCommand(0x1234)
        val full = first + second
        assertEquals(0xf5.toByte(), full[133])
        assertEquals(0x01.toByte(), full[134])
        assertEquals(0x01f5, Crc16Kermit.compute(full, 1, full.size - 2))
    }

    @Test
    fun `cabecalho e token no lugar certo`() {
        val (first, _) = Ft450Protocol.buildConfigCommand(0xB059)
        assertEquals("aa0001010c007e", Ft450Protocol.toHex(first.copyOfRange(0, 7)))
        assertEquals(0xB0.toByte(), first[7])
        assertEquals(0x59.toByte(), first[8])
    }

    @Test
    fun `token muda o CRC`() {
        val a = Ft450Protocol.buildConfigCommand(0x0001).let { it.first + it.second }
        val b = Ft450Protocol.buildConfigCommand(0x0002).let { it.first + it.second }
        assertNotEquals(Ft450Protocol.toHex(a), Ft450Protocol.toHex(b))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `token zero e recusado`() {
        // 0x0000 significa "ECU não configurada" — nunca pode ir para o fio.
        Ft450Protocol.buildConfigCommand(0)
    }

    @Test
    fun `token aleatorio nunca e zero`() {
        repeat(2_000) { assertTrue(Ft450Protocol.randomToken() in 1..0xFFFF) }
    }

    @Test
    fun `preambulo do handshake bate byte a byte com a captura`() {
        assertEquals(4, Ft450Protocol.HANDSHAKE_PREAMBLE.size)
        assertEquals(9, Ft450Protocol.HELLO.size)
        assertEquals(23, Ft450Protocol.CMD_23B.size)
        assertEquals("aa000100000000440b", Ft450Protocol.toHex(Ft450Protocol.HELLO))
    }

    @Test
    fun `o token da sessao e ecoado nos frames de telemetria`() {
        // Nos frames de estrada o token gravado pela ECU é 0xB059 — é assim que
        // dá para filtrar frames de outra sessão.
        val frame = Fixtures.load(Fixtures.ROAD_107).first()
        assertEquals(0xB059, FrameValidator.sessionToken(frame, 0))
    }
}
