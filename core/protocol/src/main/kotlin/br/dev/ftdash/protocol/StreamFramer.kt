package br.dev.ftdash.protocol

/**
 * Remonta frames a partir de um fluxo de bytes.
 *
 * Não dá para supor que uma leitura devolva exatamente um frame: o
 * `wMaxPacketSize` do endpoint é 64 B e os frames têm 107/111 B, então no
 * Android um `bulkTransfer` tende a entregar 64 + resto. Além disso a ECU
 * intercala no mesmo endpoint 0x81 um segundo stream de ~263 B que não é
 * telemetria e precisa ser descartado.
 *
 * A política de recuperação é a conservadora: **CRC falho avança 1 byte**, não
 * o frame inteiro. Se o tamanho declarado estiver corrompido, pular `len` bytes
 * jogaria fora frames bons; avançar 1 byte re-sincroniza no próximo sync real.
 *
 * Não é thread-safe — use uma instância por fonte, numa única thread/coroutine.
 */
class StreamFramer(capacity: Int = 8192) {

    private var buf = ByteArray(capacity)
    private var start = 0
    private var end = 0

    /** Frames íntegros entregues. */
    var framesOk = 0L
        private set

    /** Frames com cabeçalho plausível mas CRC inválido. */
    var crcFail = 0L
        private set

    /** Quantas vezes foi preciso avançar 1 byte procurando o próximo sync. */
    var resyncs = 0L
        private set

    /** Bytes descartados por não pertencerem a nenhum frame válido. */
    var bytesDiscarded = 0L
        private set

    /**
     * Entrega mais bytes e devolve os frames completos que couberam neles.
     *
     * @param data buffer de origem
     * @param length quantos bytes de [data] são válidos (o resto é lixo do buffer de leitura)
     */
    fun feed(data: ByteArray, length: Int = data.size, tsMs: Long = System.currentTimeMillis()): List<Telemetry> {
        if (length <= 0) return emptyList()
        append(data, length)
        return drain(tsMs)
    }

    fun reset() {
        start = 0
        end = 0
    }

    private fun append(data: ByteArray, length: Int) {
        if (end + length > buf.size) compact(length)
        System.arraycopy(data, 0, buf, end, length)
        end += length
    }

    /** Move o que sobrou para o início; cresce o buffer se ainda não couber. */
    private fun compact(incoming: Int) {
        val remaining = end - start
        if (remaining > 0 && start > 0) {
            System.arraycopy(buf, start, buf, 0, remaining)
        }
        start = 0
        end = remaining
        if (end + incoming > buf.size) {
            buf = buf.copyOf(maxOf(buf.size * 2, end + incoming))
        }
    }

    private fun drain(tsMs: Long): List<Telemetry> {
        val out = ArrayList<Telemetry>(4)
        while (true) {
            // 1. procurar o sync
            while (start < end && buf[start] != FrameValidator.SYNC) {
                start++
                bytesDiscarded++
            }
            val available = end - start
            if (available < 7) break

            // 2. cabeçalho plausível?
            if (!FrameValidator.hasValidHeader(buf, start, available)) {
                skipOne()
                continue
            }

            // 3. tamanho declarado plausível?
            val len = FrameValidator.declaredLength(buf, start, available)
            if (len < FrameLayout.MIN_LEN || len > MAX_FRAME_LEN) {
                skipOne()
                continue
            }

            // 4. frame ainda incompleto — espera a próxima leitura
            if (available < len) break

            // 5. integridade
            if (!FrameValidator.hasValidCrc(buf, start, len)) {
                crcFail++
                skipOne()
                continue
            }

            FrameParser.parse(buf, start, len, tsMs, validate = false)?.let {
                out += it
                framesOk++
            }
            start += len
        }
        // libera espaço quando o consumo já andou bastante
        if (start > 0 && start == end) reset()
        return out
    }

    private fun skipOne() {
        start++
        bytesDiscarded++
        resyncs++
    }

    companion object {
        /** byte 6 é um u8, então o teto absoluto é 255 + 9. */
        const val MAX_FRAME_LEN = 264
    }
}
