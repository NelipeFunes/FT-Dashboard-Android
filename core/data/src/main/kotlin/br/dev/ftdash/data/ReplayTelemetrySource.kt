package br.dev.ftdash.data

import android.content.Context
import br.dev.ftdash.protocol.Ft450Protocol
import br.dev.ftdash.protocol.SanityFilter
import br.dev.ftdash.protocol.StreamFramer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * Reproduz frames reais gravados do carro, em loop.
 *
 * Os bytes passam pelo **mesmo [StreamFramer]** do caminho USB, cortados em
 * pedaços de 64 B como o endpoint faria. Não é firula: garante que o framer e o
 * re-sync são exercitados todo dia no desenvolvimento, e não só uma vez no
 * teste unitário.
 *
 * Asset: `assets/fixtures/replay-107.txt`, 3.000 frames de uma volta de estrada.
 */
class ReplayTelemetrySource(
    private val context: Context,
    private val assetPath: String = DEFAULT_ASSET,
    /** 1,0 = tempo real (~16,7 Hz). 2,0 = dobrado. */
    @Volatile var speedMultiplier: Float = 1.0f,
) : TelemetrySource {

    override val kind = SourceKind.REPLAY

    override fun stream(): Flow<TelemetryEvent> = flow {
        emit(TelemetryEvent.Status(SourceState.CONNECTING, "carregando $assetPath"))

        val frames = withContext(Dispatchers.IO) { loadFrames() }
        if (frames.isEmpty()) {
            emit(TelemetryEvent.Status(SourceState.ERROR, "nenhum frame em $assetPath"))
            return@flow
        }

        val framer = StreamFramer()
        val sanity = SanityFilter()
        emit(TelemetryEvent.Status(SourceState.STREAMING, "${frames.size} frames"))

        var index = 0
        var emitted = 0L
        var windowStart = System.currentTimeMillis()

        while (true) {
            val frame = frames[index]
            index = (index + 1) % frames.size

            // corta em pacotes de 64 B, como o endpoint bulk entrega
            var at = 0
            while (at < frame.size) {
                val n = minOf(Ft450Protocol.MAX_PACKET, frame.size - at)
                for (t in framer.feed(frame.copyOfRange(at, at + n), n)) {
                    emit(TelemetryEvent.Frame(sanity.apply(t)))
                    emitted++
                }
                at += n
            }

            val now = System.currentTimeMillis()
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

            val interval = (FRAME_INTERVAL_MS / speedMultiplier.coerceAtLeast(0.1f)).toLong()
            delay(interval.coerceAtLeast(1))
        }
    }

    private fun loadFrames(): List<ByteArray> =
        context.assets.open(assetPath).bufferedReader().useLines { lines ->
            lines.map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { Ft450Protocol.hex(it) }
                .toList()
        }

    companion object {
        const val DEFAULT_ASSET = "fixtures/replay-107.txt"

        /** ~16,7 Hz, dentro da faixa medida da ECU real (15-20 Hz). */
        const val FRAME_INTERVAL_MS = 60f
    }
}
