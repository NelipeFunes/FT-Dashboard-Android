package br.dev.ftdash

import android.content.Context
import br.dev.ftdash.data.GpsSpeedSource
import br.dev.ftdash.data.ReplayTelemetrySource
import br.dev.ftdash.data.SimulatedSpeedSource
import br.dev.ftdash.data.SourceKind
import br.dev.ftdash.data.SpeedFix
import br.dev.ftdash.data.TelemetryRepository
import br.dev.ftdash.data.UsbBusReport
import br.dev.ftdash.data.UsbDiagnostics
import br.dev.ftdash.data.UsbEventLog
import br.dev.ftdash.data.UsbTelemetrySource
import br.dev.ftdash.data.settings.SettingsStore
import br.dev.ftdash.gearing.GearProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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

    /**
     * Ligada, mas **não é a fonte padrão** — ainda não foi validada contra a ECU
     * numa multimídia. Entra por toque longo na barra de status ou pela aba USB
     * da configuração.
     */
    /**
     * Registro do que acontece no USB. Fica no container porque a aba de
     * diagnóstico o desenha e a fonte o alimenta — e ele precisa sobreviver à
     * fonte ser recriada, senão perde justamente o evento da queda.
     */
    val usbEventLog = UsbEventLog(appContext)

    val usbSource = UsbTelemetrySource(appContext, usbEventLog)

    val telemetryRepository = TelemetryRepository(
        scope = scope,
        sources = mapOf(
            SourceKind.REPLAY to replaySource,
            SourceKind.USB to usbSource,
        ),
    )

    /** Foto do barramento USB agora — a aba de diagnóstico chama a cada abertura. */
    fun scanUsb(): UsbBusReport = UsbDiagnostics.scan(appContext)

    val gpsSpeedSource = GpsSpeedSource(appContext)
    val simulatedSpeedSource = SimulatedSpeedSource(GearProfile())

    /**
     * Velocidade: GPS de verdade ou sintetizada a partir do RPM do replay.
     *
     * A simulação é **amarrada à fonte de telemetria**, não a um interruptor
     * solto: só entra quando os próprios frames são replay. Com a ECU no cabo a
     * velocidade é sempre a do GPS, e se não houver fixação o painel mostra
     * `--`. Inventar velocidade dentro do carro em movimento seria pior do que
     * não mostrar nada — e a marcha estimada em cima de um número inventado
     * seria pior ainda.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val speedFixes: Flow<SpeedFix> = combine(
        settingsStore.settings.map { it.useSimulatedSpeed }.distinctUntilChanged(),
        telemetryRepository.sourceKind,
    ) { simulationAllowed, kind -> simulationAllowed && kind == SourceKind.REPLAY }
        .distinctUntilChanged()
        .flatMapLatest { simulated ->
            if (simulated) simulatedSpeedSource.stream() else gpsSpeedSource.stream()
        }
}
