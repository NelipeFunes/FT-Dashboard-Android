package br.dev.ftdash.protocol

/**
 * Constantes e construtores do protocolo USB da FT450.
 *
 * **A FT450 não é serial.** Não há baud rate, paridade nem DTR/RTS: é USB bulk
 * vendor-specific puro (`bDeviceClass = 0`), com um par de endpoints de 64 B.
 * No Android isso vira `UsbManager` + `claimInterface` + `bulkTransfer`.
 *
 * Sequência de conexão:
 *  1. abrir o device, `setConfiguration(1)`, `claimInterface(0, force = true)`;
 *  2. enviar os 4 comandos de [HANDSHAKE_PREAMBLE] no EP 0x01, com
 *     [PREAMBLE_GAP_MS] e uma leitura descartável entre cada (timeout aqui é
 *     normal e esperado);
 *  3. enviar o comando de configuração de [buildConfigCommand] — **as duas
 *     partes coladas, sem leitura entre elas**, porque a ECU só processa a
 *     mensagem de 135 B completa;
 *  4. a partir daí a ECU **transmite sozinha** a ~15-20 Hz. Não existe comando
 *     de "pedir frame" — é stream, não polling.
 *
 * O token de sessão é ecoado pela ECU nos bytes 7-8 de todo frame, o que
 * permite filtrar frames da própria sessão. Replay de bytes de uma sessão
 * antiga (token velho) **não** configura uma ECU recém-ligada — por isso o
 * comando é construído a cada conexão, não replayado.
 *
 * Portado de `ft-tuning-assistant/src/main/usb/ft450Protocol.ts`.
 */
object Ft450Protocol {

    const val VID = 0x1C5E
    const val PID = 0x1002
    const val INTERFACE = 0
    const val EP_TELEMETRY_IN = 0x81
    const val EP_COMMAND_OUT = 0x01

    /** wMaxPacketSize dos endpoints bulk. */
    const val MAX_PACKET = 64

    // Timeouts herdados do app Electron (src/main/sources/usbSource.ts), onde
    // foram ajustados contra a ECU real.
    const val READ_TIMEOUT_MS = 200
    const val WRITE_TIMEOUT_MS = 1500
    const val FIRST_FRAME_TIMEOUT_MS = 3000
    const val STALL_TIMEOUT_MS = 3000
    const val RECONNECT_INTERVAL_MS = 2000
    const val PREAMBLE_GAP_MS = 80L

    /** Respondido com a identificação/nº de série da ECU em ASCII. */
    val HELLO = hex("aa000100000000440b")

    /** Função desconhecida; não bloqueia o handshake se falhar. */
    val CMD_23B = hex("aa00010111000e0000000000000000000000000000df9a")

    val HANDSHAKE_PREAMBLE: List<ByteArray> = listOf(HELLO, CMD_23B, HELLO, HELLO)

    private val CONFIG_HEAD = hex("aa0001010c007e")

    /**
     * Corpo fixo do comando de configuração, do pcap. Idêntico entre a captura
     * de 15/07 e a do boot frio de 17/07 — só o token e o CRC mudam. Hipótese:
     * é a seleção de canais/config de tela, ou seja, é o que determina o
     * tamanho do frame de telemetria resultante (107 vs 111 B).
     */
    private val CONFIG_BODY = hex(
        "3f0100070000c000000000001c000038071880010004000000100000c40000000004" +
            "00000000000000008001006000000000080060000000000000000000000000000000" +
            "f00f000000000000000000000000000000000000000000000000000000000000000000" +
            "00000000000000000000000000000000"
    )

    private val CONFIG_END = hex("0000000030")

    const val CONFIG_COMMAND_LEN = 135
    const val CONFIG_SPLIT_AT = 128

    /**
     * Monta o comando de configuração de 135 B para um token de sessão, já com
     * o CRC correto, dividido nas duas transferências USB observadas no fio.
     *
     * Cuidado na fase 2: a primeira parte tem **exatamente 128 B**, múltiplo de
     * [MAX_PACKET]. Alguns stacks USB exigem um ZLP (zero-length packet) depois
     * disso para marcar o fim da transferência. Se a ECU não responder ao
     * handshake no Android, tentar enviar os 135 B numa transferência única é a
     * primeira coisa a testar.
     *
     * @param token inteiro 16 bits **não-nulo** (0x0000 significa "não configurada")
     * @return par (128 B, 7 B) — enviar em sequência, sem leitura entre eles
     */
    fun buildConfigCommand(token: Int): Pair<ByteArray, ByteArray> {
        require(token in 1..0xFFFF) { "token deve ser 1..0xFFFF, veio $token" }

        val body = ByteArray(CONFIG_COMMAND_LEN - 2)
        var i = 0
        CONFIG_HEAD.copyInto(body, i); i += CONFIG_HEAD.size
        body[i++] = ((token ushr 8) and 0xFF).toByte()
        body[i++] = (token and 0xFF).toByte()
        CONFIG_BODY.copyInto(body, i); i += CONFIG_BODY.size
        CONFIG_END.copyInto(body, i); i += CONFIG_END.size
        check(i == body.size) { "comando de config com $i bytes, esperado ${body.size}" }

        val crc = Crc16Kermit.compute(body, 1, body.size)
        val full = body + Crc16Kermit.toLittleEndian(crc)

        return full.copyOfRange(0, CONFIG_SPLIT_AT) to
            full.copyOfRange(CONFIG_SPLIT_AT, full.size)
    }

    /** Token de sessão aleatório, garantidamente não-nulo. */
    fun randomToken(): Int = 1 + (Math.random() * 0xFFFE).toInt()

    fun hex(s: String): ByteArray {
        require(s.length % 2 == 0) { "hex de tamanho ímpar" }
        return ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    fun toHex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
}
