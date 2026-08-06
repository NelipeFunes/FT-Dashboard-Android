package br.dev.ftdash.ui.dash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.dev.ftdash.AppContainer
import br.dev.ftdash.data.SourceKind
import br.dev.ftdash.data.SourceState
import br.dev.ftdash.data.SpeedFix
import br.dev.ftdash.data.TelemetryEvent
import br.dev.ftdash.gearing.GearEstimator
import br.dev.ftdash.gearing.GearProfile
import br.dev.ftdash.gearing.GearState
import br.dev.ftdash.gearing.RatioCapture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Junta telemetria, velocidade e marcha num único [DashUiState].
 *
 * O pico de RPM é mantido aqui e não no composable: sobrevive a recomposição e
 * a rotação de tela.
 */
class DashViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(DashUiState())
    val state: StateFlow<DashUiState> = _state.asStateFlow()

    private val estimator = GearEstimator(GearProfile())

    /** Alimentado em paralelo ao estimador, para a tela de calibração. */
    val ratioCapture = RatioCapture()

    private var peakRpmAtMs = 0L

    init {
        observeSettings()
        observeTelemetry()
        observeSpeed()
    }

    private fun observeSettings() = viewModelScope.launch {
        container.settingsStore.settings.collect { s ->
            estimator.profile = s.gearProfile
            container.simulatedSpeedSource.profile = s.gearProfile
            container.replaySource.speedMultiplier = s.replaySpeed
            _state.value = _state.value.copy(
                redlineRpm = s.redlineRpm,
                shiftRpm = s.shiftRpm,
                maxRpm = s.maxRpm,
                gearCalibrated = s.gearProfile.isCalibrated,
            )
        }
    }

    private fun observeTelemetry() = viewModelScope.launch {
        container.telemetryRepository.events.collect { event ->
            when (event) {
                is TelemetryEvent.Frame -> {
                    val t = event.telemetry
                    estimator.onRpm(t.tsMs, t.rpm)
                    container.simulatedSpeedSource.onRpm(t.rpm)

                    val prev = _state.value
                    val peak = if (t.rpm >= prev.peakRpm || t.tsMs - peakRpmAtMs > PEAK_HOLD_MS) {
                        peakRpmAtMs = t.tsMs
                        t.rpm
                    } else {
                        prev.peakRpm
                    }

                    _state.value = prev.copy(
                        rpm = t.rpm,
                        peakRpm = peak,
                        tpsPct = t.tpsPct,
                        mapBar = t.mapBar,
                        lambda = t.lambda,
                        lambdaTarget = t.lambdaTarget,
                        closedLoopPct = t.closedLoopPct,
                        ignitionDeg = t.ignitionDeg,
                        injTimeMs = t.injTimeMs,
                        injDutyPct = t.injDutyPct,
                        idleActuatorPct = t.idleActuatorPct,
                        engineTempC = t.engineTempC,
                        airTempC = t.airTempC,
                        oilPressureBar = t.oilPressureBar,
                        fuelPressureBar = t.fuelPressureBar,
                        vbat = t.vbat,
                        cutoff = t.cutoff,
                        frameLen = t.frameLen,
                        layoutKnown = t.layoutKnown,
                        hasData = true,
                    )
                }

                is TelemetryEvent.Status -> {
                    _state.value = _state.value.copy(
                        sourceState = event.state,
                        sourceDetail = event.detail,
                        sourceKind = container.telemetryRepository.sourceKind.value,
                        hasData = if (event.state == SourceState.ERROR) false else _state.value.hasData,
                    )
                }

                is TelemetryEvent.Diagnostics -> {
                    _state.value = _state.value.copy(
                        hz = event.hz,
                        framesOk = event.framesOk,
                        crcFail = event.crcFail,
                    )
                }
            }
        }
    }

    private fun observeSpeed() = viewModelScope.launch {
        container.speedFixes.collect { fix -> onSpeedFix(fix) }
    }

    private fun onSpeedFix(fix: SpeedFix) {
        val gear = estimator.onSpeed(fix.tsMs, fix.kmh)

        // A janela de captura da calibração roda em paralelo, sempre: quando o
        // usuário abre a tela de calibração, ela já tem 2 s de histórico.
        val rpm = _state.value.rpm.toDouble()
        if (fix.kmh != null) ratioCapture.add(fix.tsMs, rpm, fix.kmh)

        _state.value = _state.value.copy(
            speedKmh = fix.kmh,
            hasGpsFix = fix.hasGpsFix,
            gear = gear,
        )
    }

    /** Razão instantânea rpm/km/h — a tela de calibração mostra ao vivo. */
    val instantRatio: Double? get() = estimator.instantRatio

    fun selectSource(kind: SourceKind) = viewModelScope.launch {
        container.telemetryRepository.selectSource(kind)
        container.settingsStore.saveSourceKind(kind)
    }

    fun resetPeak() {
        _state.value = _state.value.copy(peakRpm = _state.value.rpm)
    }

    fun currentGearState(): GearState = estimator.state

    companion object {
        /** Quanto tempo o marcador de pico segura antes de voltar ao valor atual. */
        const val PEAK_HOLD_MS = 1_200L
    }
}
