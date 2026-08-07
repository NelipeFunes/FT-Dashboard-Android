package br.dev.ftdash.data

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import br.dev.ftdash.protocol.Ft450Protocol
import br.dev.ftdash.protocol.SanityFilter
import br.dev.ftdash.protocol.StreamFramer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Leitura da FT450 pelo USB host do Android.
 *
 * ⚠ **Ainda não validada no carro.** O protocolo aqui é o mesmo que já roda
 * contra a ECU real no app Electron (e cujo CRC e handshake estão cobertos por
 * teste), mas a camada `UsbManager` nunca foi exercitada contra a FT450 numa
 * multimídia. A fonte padrão do app continua sendo o replay; esta entra por
 * toque longo na barra de status ou pela aba USB da configuração.
 *
 * Coisas que provavelmente vão dar trabalho quando for a hora, em ordem de
 * probabilidade:
 *
 * 1. **Permissão.** Sem `usb_device_filter.xml` + `USB_DEVICE_ATTACHED` no
 *    manifest, o Android abre um diálogo a cada conexão — inaceitável numa tela
 *    de carro. Com o filtro, a permissão fica persistente.
 * 2. **ZLP.** A primeira parte do comando de configuração tem exatamente 128 B,
 *    múltiplo do `wMaxPacketSize` de 64. Alguns stacks exigem um pacote de
 *    tamanho zero para fechar a transferência. Se o handshake não responder,
 *    tentar [sendConfigAsSingleTransfer] antes de qualquer outra coisa.
 * 3. **USB host de verdade.** Muita multimídia chinesa tem porta USB só de
 *    dados para pendrive, com o host limitado a mass storage. `UsbManager
 *    .deviceList` vazio com a FT450 plugada indica isso.
 * 4. **Reset no arranque.** A partida do motor derruba o barramento — daí o
 *    laço de reconexão.
 */
class UsbTelemetrySource(
    private val context: Context,
    /** Onde fica o registro do que aconteceu — ver [UsbEventLog]. */
    private val log: UsbEventLog,
    /** Fallback para o caso do ZLP — ver ponto 2 do KDoc. */
    private val sendConfigAsSingleTransfer: Boolean = false,
) : TelemetrySource {

    override val kind = SourceKind.USB

    override fun stream(): Flow<TelemetryEvent> = flow {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        // Espera entre tentativas, crescente. A partida do motor derruba o
        // barramento e a ECU leva um tempo para voltar; tentar de 2 em 2
        // segundos para sempre só enche o log e gasta bateria quando o problema
        // é outro (porta sem host, cabo solto). Uma conexão que dá certo zera
        // o contador.
        var failures = 0
        var permissionRefused = false
        var absentReported = false

        suspend fun backoff() {
            failures++
            val wait = (Ft450Protocol.RECONNECT_INTERVAL_MS.toLong() * (1L shl minOf(failures - 1, 3)))
                .coerceAtMost(MAX_RECONNECT_INTERVAL_MS)
            delay(wait)
        }

        log.add("fonte USB iniciada")

        while (true) {
            emit(TelemetryEvent.Status(SourceState.CONNECTING))

            val device = findDevice(manager)
            if (device == null) {
                // Reporta o que o barramento mostra, não só "não achei": é a
                // diferença entre voltar do carro sabendo que a porta não faz
                // host e voltar sem informação nenhuma.
                val report = UsbDiagnostics.scan(context)
                emit(TelemetryEvent.Status(SourceState.ERROR, report.summary))
                // Só a primeira vez: a ECU sumida gera uma varredura por
                // segundo, e 60 linhas iguais por minuto empurrariam para fora
                // do log justamente o que interessa — o que houve antes de ela
                // sumir.
                if (!absentReported) {
                    absentReported = true
                    log.add("device ausente: ${report.summary}")
                }
                // O device sumiu: se voltar, é outra sessão e vale pedir
                // permissão de novo.
                permissionRefused = false
                // Sem backoff. Ler `deviceList` não toca no hardware, não
                // gasta nada e não incomoda ninguém — e esperar 15 s para
                // perceber que a ECU voltou é tempo de painel morto por
                // economia que não economiza nada. O backoff existe para
                // tentativa de conexão que falha, não para device ausente.
                delay(ABSENT_RESCAN_INTERVAL_MS)
                continue
            }
            if (absentReported) {
                absentReported = false
                log.add("device voltou ao barramento")
            }
            if (!manager.hasPermission(device)) {
                if (permissionRefused) {
                    // Não insistir: um diálogo de permissão reaparecendo sozinho
                    // por cima da tela do carro é pior que ficar sem telemetria.
                    emit(TelemetryEvent.Status(SourceState.ERROR, "permissão de USB negada — replugue o cabo"))
                    backoff()
                    continue
                }
                emit(TelemetryEvent.Status(SourceState.CONNECTING, "pedindo permissão de USB"))
                log.add("pedindo permissão de USB")
                if (!UsbPermission.request(context, device)) {
                    permissionRefused = true
                    emit(TelemetryEvent.Status(SourceState.ERROR, "permissão de USB negada"))
                    log.add("permissão NEGADA")
                    backoff()
                    continue
                }
            }

            val connection = manager.openDevice(device)
            if (connection == null) {
                emit(TelemetryEvent.Status(SourceState.ERROR, "não consegui abrir o device"))
                log.add("openDevice falhou (handle preso? sem permissão?)")
                backoff()
                continue
            }

            var connected = false
            var session: Session? = null
            try {
                session = Session.open(device, connection)
                if (session == null) {
                    emit(TelemetryEvent.Status(SourceState.ERROR, "interface/endpoints não encontrados"))
                    log.add("claimInterface/endpoints falharam")
                } else {
                    connected = true
                    failures = 0
                    log.add("conectado, iniciando handshake")
                    handshake(session)
                    emitFrames(session, manager)
                }
            } catch (e: Exception) {
                emit(TelemetryEvent.Status(SourceState.ERROR, e.message ?: e.javaClass.simpleName))
                log.add("erro: ${e.javaClass.simpleName} ${e.message ?: ""}")
            } finally {
                // Soltar a interface antes de fechar, sempre. O app Electron
                // que serviu de base faz isso em todo caminho de saída, e o
                // nosso não fazia: cada ciclo de reconexão deixava para trás
                // uma interface reivindicada. Numa multimídia velha, é a
                // receita para o controlador USB travar depois de algumas
                // quedas e só voltar com o cabo na mão.
                session?.release()
                connection.close()
            }

            if (connected) {
                // Perdeu depois de estar conectado: provavelmente foi a partida
                // do motor derrubando o barramento. Volta rápido, sem backoff.
                delay(Ft450Protocol.RECONNECT_INTERVAL_MS.toLong())
            } else {
                backoff()
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun findDevice(manager: UsbManager): UsbDevice? =
        manager.deviceList.values.firstOrNull {
            it.vendorId == Ft450Protocol.VID && it.productId == Ft450Protocol.PID
        }

    /**
     * Preâmbulo + comando de configuração. As leituras entre os comandos do
     * preâmbulo são descartáveis — timeout aqui é normal e não é erro.
     */
    private suspend fun handshake(s: Session) {
        val scratch = ByteArray(4096)

        for (cmd in Ft450Protocol.HANDSHAKE_PREAMBLE) {
            s.write(cmd)
            delay(Ft450Protocol.PREAMBLE_GAP_MS)
            s.read(scratch, 800)
        }

        val token = Ft450Protocol.randomToken()
        val (first, second) = Ft450Protocol.buildConfigCommand(token)
        if (sendConfigAsSingleTransfer) {
            s.write(first + second)
        } else {
            // As duas partes vão coladas, SEM leitura entre elas: a ECU só
            // processa a mensagem de 135 B completa.
            s.write(first)
            s.write(second)
        }
        s.sessionToken = token
        delay(Ft450Protocol.PREAMBLE_GAP_MS)
        s.read(scratch, 800)
    }

    /**
     * Laço de leitura.
     *
     * A regra de "caiu" é por **ausência de bytes**, não de frames válidos.
     * Essa distinção foi o defeito do primeiro teste de campo: com o motor
     * ligado, o ruído elétrico do alternador e das bobinas derruba parte dos
     * CRCs, e o app tratava isso como conexão morta — desmontava o handle,
     * refazia o handshake, conseguia alguns segundos, e caía de novo. Um cabo
     * que entrega bytes está vivo; o que os bytes trazem é outro problema, e a
     * resposta a ele é descartar frame ruim, não derrubar a conexão.
     */
    private suspend fun FlowCollector<TelemetryEvent>.emitFrames(s: Session, manager: UsbManager) {
        val framer = StreamFramer()
        val sanity = SanityFilter()
        val buffer = ByteArray(4096)

        val start = System.currentTimeMillis()
        var lastBytesMs = start
        var lastFrameMs = start
        var emitted = 0L
        var bytes = 0L
        var windowStart = start
        var streaming = false
        var degradedReported = false
        var lastHeartbeatMs = start
        var framesAtHeartbeat = 0L
        var crcAtHeartbeat = 0L

        // A ECU está no cabo e conta a própria tensão. Se ela cai junto com a
        // conexão, o problema é elétrico e nenhum ajuste de software alcança;
        // se a conexão morre com 14 V estáveis, é a porta da central ou a
        // enumeração, e aí vale insistir no software. Sem estes dois números o
        // log diz *quando* parou, mas não *sob que condição* — que é a
        // pergunta que restou.
        var lastVbat: Float? = null
        var lastRpm = 0
        var minVbat = Float.MAX_VALUE
        var minVbatRpm = 0

        while (true) {
            val n = s.read(buffer, Ft450Protocol.READ_TIMEOUT_MS)
            val now = System.currentTimeMillis()

            if (n > 0) {
                lastBytesMs = now
                bytes += n
                val frames = framer.feed(buffer, n, now)
                if (frames.isNotEmpty()) {
                    lastFrameMs = now
                    if (!streaming || degradedReported) {
                        if (!streaming) log.add("streaming (1o frame em ${now - start}ms, ${frames[0].frameLen}B)")
                        streaming = true
                        degradedReported = false
                        emit(TelemetryEvent.Status(SourceState.STREAMING))
                    }
                    for (t in frames) {
                        val clean = sanity.apply(t)
                        lastVbat = clean.vbat
                        lastRpm = clean.rpm
                        if (clean.vbat < minVbat) {
                            minVbat = clean.vbat
                            minVbatRpm = clean.rpm
                        }
                        emit(TelemetryEvent.Frame(clean))
                        emitted++
                    }
                }
            }

            // 1. O cabo morreu? Só isto justifica refazer tudo.
            val byteSilence = now - lastBytesMs
            val limit = if (streaming) Ft450Protocol.STALL_TIMEOUT_MS else Ft450Protocol.FIRST_FRAME_TIMEOUT_MS
            if (byteSilence > limit) {
                val stillThere = findDevice(manager) != null
                val why = if (stillThere) "sem dados há ${byteSilence}ms" else "FT450 sumiu do barramento"
                emit(TelemetryEvent.Status(SourceState.STALLED, why))
                // A linha que decide o diagnóstico da próxima ida ao carro:
                // quanto durou a sessão, quantos frames vieram, quantos CRCs
                // falharam e se o device ainda estava lá quando parou.
                //
                // "Ainda no barramento + zero crc" é ECU parando de mandar.
                // "Sumiu do barramento" é queda de energia ou re-enumeração —
                // e aí nenhum ajuste de timeout resolve.
                log.add(
                    "PAROU: %s | sessao %ds, %d frames, %d crcFail, %d resync".format(
                        why,
                        (now - start) / 1000,
                        framer.framesOk,
                        framer.crcFail,
                        framer.resyncs,
                    )
                )
                lastVbat?.let {
                    log.add(
                        "  ao cair: %.2fV %d rpm | minimo da sessao %.2fV a %d rpm".format(
                            it, lastRpm, minVbat, minVbatRpm,
                        )
                    )
                }
                return  // sai para o laço de reconexão
            }

            // 2. Chegando byte mas nenhum frame passa? É ruído, não queda.
            //    Avisa e continua: derrubar aqui era exatamente o bug.
            if (streaming && !degradedReported && now - lastFrameMs > DEGRADED_TIMEOUT_MS) {
                degradedReported = true
                emit(
                    TelemetryEvent.Status(
                        SourceState.STREAMING,
                        "recebendo dados corrompidos — ruído no cabo?",
                    )
                )
            }

            // Uma linha por meio minuto enquanto está vivo.
            //
            // O tally final sozinho não distingue os dois finais possíveis:
            // uma sessão que morre limpa (CRC zerado até o último segundo, e
            // então nada) é queda de energia ou porta; uma que morre depois de
            // os CRCs subirem é ruído elétrico ganhando da blindagem. São
            // problemas diferentes, com soluções diferentes — cabo e
            // aterramento num caso, alimentação no outro —, e a diferença só
            // aparece com o histórico, não com o total.
            if (streaming && now - lastHeartbeatMs >= HEARTBEAT_INTERVAL_MS) {
                val dFrames = framer.framesOk - framesAtHeartbeat
                val dCrc = framer.crcFail - crcAtHeartbeat
                log.add(
                    "%ds: +%d frames, +%d crc, %.2fV %d rpm".format(
                        (now - start) / 1000,
                        dFrames,
                        dCrc,
                        lastVbat ?: 0f,
                        lastRpm,
                    )
                )
                lastHeartbeatMs = now
                framesAtHeartbeat = framer.framesOk
                crcAtHeartbeat = framer.crcFail
            }

            if (now - windowStart >= 1000) {
                val elapsed = now - windowStart
                emit(
                    TelemetryEvent.Diagnostics(
                        framesOk = framer.framesOk,
                        crcFail = framer.crcFail,
                        resyncs = framer.resyncs,
                        hz = emitted * 1000f / elapsed,
                        bytesPerSec = (bytes * 1000 / elapsed).toInt(),
                    )
                )
                emitted = 0
                bytes = 0
                windowStart = now
            }
        }
    }

    companion object {
        /** Teto do backoff de reconexão. */
        const val MAX_RECONNECT_INTERVAL_MS = 15_000L

        /**
         * Com o device fora do barramento, varre neste ritmo fixo — sem
         * backoff. A varredura é só uma leitura de lista em memória.
         */
        const val ABSENT_RESCAN_INTERVAL_MS = 1_000L

        /**
         * Ritmo da linha periódica no log.
         *
         * Dez segundos porque é essa a escala do problema: as sessões do
         * segundo teste de campo duraram 3, 11, 28 e 54 s. Meio minuto daria
         * zero ou uma amostra em quase todas elas — o suficiente para saber
         * que houve, não para ver a tensão descendo antes de cair.
         */
        const val HEARTBEAT_INTERVAL_MS = 10_000L

        /**
         * Bytes chegando mas nenhum frame passando no CRC por este tempo já é
         * anormal o suficiente para avisar — sem derrubar a conexão.
         */
        const val DEGRADED_TIMEOUT_MS = 3_000L
    }

    /** Handle aberto: interface reivindicada e endpoints resolvidos. */
    private class Session(
        val connection: UsbDeviceConnection,
        val iface: UsbInterface,
        val epIn: UsbEndpoint,
        val epOut: UsbEndpoint,
    ) {
        var sessionToken: Int = 0

        fun write(data: ByteArray): Int =
            connection.bulkTransfer(epOut, data, data.size, Ft450Protocol.WRITE_TIMEOUT_MS)

        fun read(into: ByteArray, timeoutMs: Int): Int =
            connection.bulkTransfer(epIn, into, into.size, timeoutMs)

        /** Melhor esforço: se já estiver solta, não há o que fazer nem o que reportar. */
        fun release() {
            runCatching { connection.releaseInterface(iface) }
        }

        companion object {
            fun open(device: UsbDevice, connection: UsbDeviceConnection): Session? {
                val iface = device.getInterface(Ft450Protocol.INTERFACE)
                if (!connection.claimInterface(iface, true)) return null

                var epIn: UsbEndpoint? = null
                var epOut: UsbEndpoint? = null
                for (i in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(i)
                    if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                    when (ep.address) {
                        Ft450Protocol.EP_TELEMETRY_IN -> epIn = ep
                        Ft450Protocol.EP_COMMAND_OUT -> epOut = ep
                    }
                }
                if (epIn == null || epOut == null) return null
                return Session(connection, iface, epIn, epOut)
            }
        }
    }
}
