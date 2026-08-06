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
 * ⚠ **Fase 2, ainda não validada no carro.** O protocolo aqui é o mesmo que já
 * roda contra a ECU real no app Electron (e cujo CRC e handshake estão cobertos
 * por teste), mas a camada `UsbManager` nunca foi exercitada contra a FT450 numa
 * multimídia. Enquanto isso não acontecer, a fonte padrão do app é o replay.
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
    /** Enquanto false, a fonte só informa que está desativada. */
    private val enabled: Boolean = false,
    /** Fallback para o caso do ZLP — ver ponto 2 do KDoc. */
    private val sendConfigAsSingleTransfer: Boolean = false,
) : TelemetrySource {

    override val kind = SourceKind.USB

    override fun stream(): Flow<TelemetryEvent> = flow {
        if (!enabled) {
            emit(TelemetryEvent.Status(SourceState.IDLE, "USB desativado (fase 1: use o replay)"))
            return@flow
        }

        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        while (true) {
            emit(TelemetryEvent.Status(SourceState.CONNECTING))

            val device = findDevice(manager)
            if (device == null) {
                emit(TelemetryEvent.Status(SourceState.ERROR, "FT450 não encontrada no barramento"))
                delay(Ft450Protocol.RECONNECT_INTERVAL_MS.toLong())
                continue
            }
            if (!manager.hasPermission(device)) {
                emit(TelemetryEvent.Status(SourceState.ERROR, "sem permissão de USB para a FT450"))
                delay(Ft450Protocol.RECONNECT_INTERVAL_MS.toLong())
                continue
            }

            val connection = manager.openDevice(device)
            if (connection == null) {
                emit(TelemetryEvent.Status(SourceState.ERROR, "não consegui abrir o device"))
                delay(Ft450Protocol.RECONNECT_INTERVAL_MS.toLong())
                continue
            }

            try {
                val session = Session.open(device, connection)
                if (session == null) {
                    emit(TelemetryEvent.Status(SourceState.ERROR, "interface/endpoints não encontrados"))
                } else {
                    handshake(session)
                    emitFrames(session)
                }
            } catch (e: Exception) {
                emit(TelemetryEvent.Status(SourceState.ERROR, e.message ?: e.javaClass.simpleName))
            } finally {
                connection.close()
            }

            delay(Ft450Protocol.RECONNECT_INTERVAL_MS.toLong())
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

    private suspend fun FlowCollector<TelemetryEvent>.emitFrames(s: Session) {
        val framer = StreamFramer()
        val sanity = SanityFilter()
        val buffer = ByteArray(4096)

        var lastFrameMs = System.currentTimeMillis()
        var emitted = 0L
        var windowStart = lastFrameMs
        var streaming = false

        while (true) {
            val n = s.read(buffer, Ft450Protocol.READ_TIMEOUT_MS)
            val now = System.currentTimeMillis()

            if (n > 0) {
                val frames = framer.feed(buffer, n, now)
                if (frames.isNotEmpty()) {
                    lastFrameMs = now
                    if (!streaming) {
                        streaming = true
                        emit(TelemetryEvent.Status(SourceState.STREAMING))
                    }
                    for (t in frames) {
                        emit(TelemetryEvent.Frame(sanity.apply(t)))
                        emitted++
                    }
                }
            }

            val silence = now - lastFrameMs
            val limit = if (streaming) Ft450Protocol.STALL_TIMEOUT_MS else Ft450Protocol.FIRST_FRAME_TIMEOUT_MS
            if (silence > limit) {
                emit(TelemetryEvent.Status(SourceState.STALLED, "sem frame há ${silence}ms"))
                return  // sai para o laço de reconexão
            }

            if (now - windowStart >= 1000) {
                emit(
                    TelemetryEvent.Diagnostics(
                        framesOk = framer.framesOk,
                        crcFail = framer.crcFail,
                        resyncs = framer.resyncs,
                        hz = emitted * 1000f / (now - windowStart),
                    )
                )
                emitted = 0
                windowStart = now
            }
        }
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
