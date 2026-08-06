package br.dev.ftdash.protocol

/**
 * Decodifica um frame validado em [Telemetry].
 *
 * Todos os campos de sensor são **big-endian** (só o CRC é LE). Valor final =
 * bruto × escala. Offsets e escalas vieram da correlação de capturas USB com
 * datalogs do FTManager — ver `ft-tuning-assistant/src/main/usb/telemetry.ts`
 * e `docs/reverse-engineering.md`.
 */
object FrameParser {

    /**
     * @param validate quando true, confere cabeçalho e CRC antes de decodificar.
     *        O [StreamFramer] já validou, então passa false para não pagar duas vezes.
     * @return null se o frame é inválido.
     */
    fun parse(
        buf: ByteArray,
        offset: Int = 0,
        len: Int = buf.size - offset,
        tsMs: Long = System.currentTimeMillis(),
        validate: Boolean = true,
    ): Telemetry? {
        if (len < FrameLayout.MIN_LEN || offset + len > buf.size) return null
        if (validate && !FrameValidator.isValidFrame(buf, offset, len)) return null

        val layout = FrameLayout.forLength(len)

        val lambda = layout?.let {
            val raw = u16be(buf, offset + it.lambdaRealOffset)
            if (raw == Telemetry.LAMBDA_ERROR_RAW) null else raw * 0.001f
        }
        val probe = layout?.closedLoopProbeOffset?.let {
            val raw = u16be(buf, offset + it)
            if (raw == Telemetry.LAMBDA_ERROR_RAW) null else raw * 0.001f
        }

        val flags = buf[offset + FrameLayout.FLAGS].toInt() and 0xFF

        return Telemetry(
            tsMs = tsMs,
            frameLen = len,
            layoutKnown = layout != null,
            rpm = u16be(buf, offset + FrameLayout.RPM),
            tpsPct = u16be(buf, offset + FrameLayout.TPS) * 0.1f,
            mapBar = s16be(buf, offset + FrameLayout.MAP) * 0.001f,
            airTempC = u16be(buf, offset + FrameLayout.AIR_TEMP) * 0.1f,
            engineTempC = u16be(buf, offset + FrameLayout.ENGINE_TEMP) * 0.1f,
            oilPressureBar = u16be(buf, offset + FrameLayout.OIL_PRESSURE) * 0.001f,
            fuelPressureBar = u16be(buf, offset + FrameLayout.FUEL_PRESSURE) * 0.001f,
            vbat = u16be(buf, offset + FrameLayout.VBAT) * 0.01f,
            injTimeMs = u16be(buf, offset + FrameLayout.INJ_TIME) * 0.01f,
            injDutyPct = u16be(buf, offset + FrameLayout.INJ_DUTY) * 0.1f,
            dwellMs = u16be(buf, offset + FrameLayout.DWELL) * 0.001f,
            ignitionDeg = s16be(buf, offset + FrameLayout.IGNITION) * 0.1f,
            idleActuatorPct = u16be(buf, offset + FrameLayout.IDLE_ACTUATOR).toFloat(),
            lambda = lambda,
            lambdaTarget = u16be(buf, offset + FrameLayout.LAMBDA_TARGET) * 0.001f,
            closedLoopPct = s16be(buf, offset + FrameLayout.CLOSED_LOOP) * 0.1f,
            closedLoopProbe = probe,
            cutoff = flags and FrameLayout.FLAG_CUTOFF != 0,
            ecuIdleHint = flags and FrameLayout.FLAG_IDLE_HINT != 0,
        )
    }

    fun u16be(buf: ByteArray, at: Int): Int =
        ((buf[at].toInt() and 0xFF) shl 8) or (buf[at + 1].toInt() and 0xFF)

    fun s16be(buf: ByteArray, at: Int): Int = u16be(buf, at).toShort().toInt()
}
