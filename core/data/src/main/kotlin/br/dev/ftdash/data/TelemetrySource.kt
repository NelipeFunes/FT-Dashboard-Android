package br.dev.ftdash.data

import br.dev.ftdash.protocol.Telemetry
import kotlinx.coroutines.flow.Flow

enum class SourceKind { REPLAY, USB }

enum class SourceState {
    IDLE,
    CONNECTING,

    /** Recebendo frames normalmente. */
    STREAMING,

    /** Conectado, mas sem frame válido há mais tempo do que deveria. */
    STALLED,
    ERROR,
}

sealed interface TelemetryEvent {
    data class Frame(val telemetry: Telemetry) : TelemetryEvent
    data class Status(val state: SourceState, val detail: String? = null) : TelemetryEvent
    data class Diagnostics(
        val framesOk: Long,
        val crcFail: Long,
        val resyncs: Long,
        val hz: Float,
    ) : TelemetryEvent
}

/**
 * Fonte de telemetria. É um **Flow, não um `readFrame()`**: a ECU empurra os
 * frames sozinha a ~15-20 Hz depois do handshake, ninguém puxa. Essa é a
 * diferença estrutural em relação ao app Electron de origem, e é o que permite
 * trocar replay por USB real sem encostar na UI.
 *
 * O flow é *cold* — quem liga os pontos é o [TelemetryRepository].
 */
interface TelemetrySource {
    val kind: SourceKind
    fun stream(): Flow<TelemetryEvent>
}
