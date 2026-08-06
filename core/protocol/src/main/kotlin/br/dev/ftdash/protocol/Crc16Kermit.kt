package br.dev.ftdash.protocol

/**
 * CRC16/KERMIT — o checksum usado em todo o protocolo da FT450 (telemetria e
 * comandos). Polinômio 0x1021 refletido (0x8408), init 0, xorout 0.
 *
 * Nos frames de telemetria o CRC cobre do byte 1 até `len-3` (ou seja, **pula o
 * sync 0xAA**) e fica gravado nos 2 últimos bytes em **little-endian** — é o
 * único campo LE do protocolo, todo o resto é big-endian.
 *
 * Portado de `ft-tuning-assistant/src/main/usb/ft450Protocol.ts`.
 */
object Crc16Kermit {

    /** CRC de `buf[from until toExclusive]`. */
    fun compute(buf: ByteArray, from: Int, toExclusive: Int): Int {
        var crc = 0
        for (i in from until toExclusive) {
            crc = crc xor (buf[i].toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 1 != 0) (crc ushr 1) xor 0x8408 else crc ushr 1
            }
        }
        return crc and 0xFFFF
    }

    fun compute(buf: ByteArray): Int = compute(buf, 0, buf.size)

    /** Os 2 bytes do CRC como são gravados no fio: little-endian. */
    fun toLittleEndian(crc: Int): ByteArray =
        byteArrayOf((crc and 0xFF).toByte(), ((crc ushr 8) and 0xFF).toByte())
}
