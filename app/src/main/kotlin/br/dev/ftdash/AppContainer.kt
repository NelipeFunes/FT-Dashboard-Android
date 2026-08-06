package br.dev.ftdash

import android.content.Context
import br.dev.ftdash.data.GpsSpeedSource
import br.dev.ftdash.data.ReplayTelemetrySource
import br.dev.ftdash.data.SimulatedSpeedSource
import br.dev.ftdash.data.SourceKind
import br.dev.ftdash.data.SpeedFix
import br.dev.ftdash.data.TelemetryRepository
import br.dev.ftdash.data.UsbTelemetrySource
import br.dev.ftdash.data.settings.SettingsStore
import br.dev.ftdash.gearing.GearProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * Injeção de dependência à mão. Duas telas e meia dúzia de objetos não
 * justificam Hilt — o custo de um grafo anotado aqui seria só cerimônia.
 */
class AppContainer(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext

    val settingsStore = SettingsStore(appContext)

    val replaySource = ReplayTelemetrySource(appContext)

    /** Fase 2: desligado até ser validado contra a ECU numa multimídia real. */
    val usbSource = UsbTelemetrySource(appContext, enabled = false)

    val telemetryRepository = TelemetryRepository(
        scope = scope,
        sources = mapOf(
            SourceKind.REPLAY to replaySource,
            SourceKind.USB to usbSource,
        ),
    )

    val gpsSpeedSource = GpsSpeedSource(appContext)
    val simulatedSpeedSource = SimulatedSpeedSource(GearProfile())

    /**
     * Velocidade: GPS de verdade ou sintetizada a partir do RPM do replay.
     *
     * Cai para a simulada também quando falta permissão de localização — assim
     * a marcha e a tela de calibração continuam demonstráveis na bancada, em
     * vez de a tela simplesmente não fazer nada.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val speedFixes: Flow<SpeedFix> = settingsStore.settings
        .map { it.useSimulatedSpeed }
        .distinctUntilChanged()
        .flatMapLatest { simulated ->
            if (simulated || !gpsSpeedSource.hasPermission()) {
                simulatedSpeedSource.stream()
            } else {
                gpsSpeedSource.stream()
            }
        }
}
