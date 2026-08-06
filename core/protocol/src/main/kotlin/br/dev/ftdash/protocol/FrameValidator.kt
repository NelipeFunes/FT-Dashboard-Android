package br.dev.ftdash.protocol

/**
 * Validação estrutural + CRC de um frame de telemetria.
 *
 * Cabeçalho: `AA 00 80 01 0B 00`, byte 6 auto-descritivo (`= len - 9`),
 * bytes 7-8 = token da sessão ecoado pela ECU.
 *
 * O app Electron de origem **não** conferia o CRC da telemetria e convivia com
 * ~4 % de frames corrompidos, tratados depois por guards de sanidade. Aqui o
 * CRC é conferido: custa nada e elimina essa classe de ruído na origem — além
 * de ser o que torna o re-sync do [StreamFramer] confiável.
 */
object FrameValidator {

    const val SYNC = 0xAA.toByte()

    private val HEADER = byteArrayOf(0x00, 0x80.toByte(), 0x01, 0x0B, 0x00)

    /** Tamanho declarado pelo byte 6, ou -1 se não há bytes suficientes. */
    fun declaredLength(buf: ByteArray, offset: Int, available: Int): Int =
        if (available < 7) -1 else (buf[offset + 6].toInt() and 0xFF) + 9

    /** Cabeçalho válido? Não confere CRC (usado para achar o limite do frame). */
    fun hasValidHeader(buf: ByteArray, offset: Int, available: Int): Boolean {
        if (available < 7 || buf[offset] != SYNC) return false
        for (i in HEADER.indices) {
            if (buf[offset + 1 + i] != HEADER[i]) return false
        }
        return true
    }

    /** CRC dos 2 últimos bytes (LE) bate com o calculado do byte 1 até `len-3`? */
    fun hasValidCrc(buf: ByteArray, offset: Int, len: Int): Boolean {
        val expected = Crc16Kermit.compute(buf, offset + 1, offset + len - 2)
        val stored = (buf[offset + len - 2].toInt() and 0xFF) or
            ((buf[offset + len - 1].toInt() and 0xFF) shl 8)
        return expected == stored
    }

    /** Frame completo e íntegro. */
    fun isValidFrame(buf: ByteArray, offset: Int, len: Int): Boolean {
        if (len < FrameLayout.MIN_LEN || offset + len > buf.size) return false
        if (!hasValidHeader(buf, offset, len)) return false
        if ((buf[offset + 6].toInt() and 0xFF) != len - 9) return false
        return hasValidCrc(buf, offset, len)
    }

    /** Token de sessão ecoado pela ECU (bytes 7-8, big-endian). */
    fun sessionToken(buf: ByteArray, offset: Int): Int =
        ((buf[offset + 7].toInt() and 0xFF) shl 8) or (buf[offset + 8].toInt() and 0xFF)
}
