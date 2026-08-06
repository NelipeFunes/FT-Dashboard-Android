package br.dev.ftdash.protocol

/**
 * Mapa de offsets do frame de telemetria.
 *
 * O frame **não tem tamanho fixo**: varia com a configuração de canais/tela
 * salva no FTManager (107 B e 111 B confirmados no mesmo carro). E o que é pior,
 * **nem todos os offsets são estáveis entre configurações** — o lambda da sonda
 * fica em 61 na config de 107 B e em 65 na de 111 B.
 *
 * Isso foi medido, não presumido: nos fixtures reais, ler o lambda no offset
 * errado devolve valores de até 65.535 λ. Por isso a regra aqui é dura —
 * **tamanho desconhecido nunca chuta offset**, devolve `lambda = null` e a UI
 * mostra `--`.
 *
 * O bloco 9..38 (TPS, MAP, temperaturas, pressões, Vbat, RPM, injeção, dwell,
 * ponto, bicos, atuador de lenta) e o bloco 54..58 (flags, λ alvo, malha
 * fechada) não mudaram em nenhuma das três capturas, então são comuns.
 */
data class FrameLayout(
    /** Tamanho total do frame em bytes, incluindo sync e CRC. */
    val len: Int,
    /** Offset do lambda medido pela sonda (u16be ×0.001). */
    val lambdaRealOffset: Int,
    /** Offset da "sonda de malha fechada"; só existe na config de 111 B. */
    val closedLoopProbeOffset: Int?,
) {
    companion object {
        // ---- Offsets comuns a todas as configs observadas ----
        const val TPS = 9                // u16be ×0.1 %
        const val MAP = 11               // s16be ×0.001 bar (negativo em vácuo)
        const val AIR_TEMP = 13          // u16be ×0.1 °C
        const val ENGINE_TEMP = 15       // u16be ×0.1 °C
        const val OIL_PRESSURE = 17      // u16be ×0.001 bar
        const val FUEL_PRESSURE = 19     // u16be ×0.001 bar
        const val VBAT = 21              // u16be ×0.01 V
        const val RPM = 23               // u16be ×1
        const val INJ_TIME = 25          // u16be ×0.01 ms
        const val DWELL = 29             // u16be ×0.001 ms
        const val IGNITION = 31          // s16be ×0.1 ° (batente negativo existe)
        const val INJ_DUTY = 33          // u16be ×0.1 %
        const val IDLE_ACTUATOR = 37     // u16be ×1 %
        const val FLAGS = 54             // byte de status
        const val LAMBDA_TARGET = 55     // u16be ×0.001 (0 = malha aberta)
        const val CLOSED_LOOP = 57       // s16be ×0.1 %

        /** bit2 do byte 54 — corte na desaceleração. Confirmado em 2 logs (98,8 % / 99,1 %). */
        const val FLAG_CUTOFF = 0x04

        /**
         * bit0 do byte 54 — batia 99,8 % com a coluna "Lenta" na captura de
         * bancada, mas só 70-92 % nas de estrada. **Hipótese**, exibido apenas
         * como diagnóstico.
         */
        const val FLAG_IDLE_HINT = 0x01

        /** Offset mais distante que o parser lê em qualquer layout, com folga. */
        const val MIN_LEN = 74

        val LAYOUT_107 = FrameLayout(len = 107, lambdaRealOffset = 61, closedLoopProbeOffset = null)
        val LAYOUT_111 = FrameLayout(len = 111, lambdaRealOffset = 65, closedLoopProbeOffset = 71)

        /** `null` quando o tamanho não é conhecido — o parser degrada sem lambda. */
        fun forLength(len: Int): FrameLayout? = when (len) {
            107 -> LAYOUT_107
            111 -> LAYOUT_111
            else -> null
        }
    }
}
