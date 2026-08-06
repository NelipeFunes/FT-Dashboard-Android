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

    /** Espelho do pico persistido, para não reescrever o DataStore à toa. */
    private var learnedMaxRpm = 0

    /** Candidato a novo pico e há quantos frames ele se sustenta. */
    private var maxCandidateRpm = 0
    private var maxCandidateFrames = 0

    init {
        observeSettings()
        observeTelemetry()
        observeSpeed()
    }

    private var sourceRestored = false

    private fun observeSettings() = viewModelScope.launch {
        container.settingsStore.settings.collect { s ->
            // A fonte escolhida era gravada e nunca lida de volta: o app abria
            // sempre no replay, mesmo com a ECU no cabo. Restaura uma vez, na
            // primeira leitura das preferências.
            if (!sourceRestored) {
                sourceRestored = true
                container.telemetryRepository.selectSource(s.sourceKind)
            }
            estimator.profile = s.gearProfile
            learnedMaxRpm = s.learnedMaxRpm
            container.simulatedSpeedSource.profile = s.gearProfile
            container.replaySource.speedMultiplier = s.replaySpeed
            _state.value = _state.value.copy(
                redlineRpm = s.effectiveRedlineRpm,
                shiftRpm = s.effectiveShiftRpm,
                maxRpm = s.effectiveMaxRpm,
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
                    learnMaxRpm(t.rpm)

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
                    // Toda tentativa de conexão zera os contadores: senão, ao
                    // trocar de fonte, a barra de status mostra a taxa e a
                    // contagem de frames da fonte anterior — dizendo "USB
                    // 15,8 Hz, 1293 frames" para um USB que nem conectou.
                    val restarting = event.state == SourceState.CONNECTING
                    val lost = event.state == SourceState.ERROR ||
                        event.state == SourceState.STALLED ||
                        restarting
                    _state.value = _state.value.copy(
                        sourceState = event.state,
                        sourceDetail = event.detail,
                        sourceKind = container.telemetryRepository.sourceKind.value,
                        hasData = if (lost) false else _state.value.hasData,
                        hz = if (restarting) 0f else _state.value.hz,
                        framesOk = if (restarting) 0 else _state.value.framesOk,
                        crcFail = if (restarting) 0 else _state.value.crcFail,
                        frameLen = if (restarting) 0 else _state.value.frameLen,
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

    /**
     * Aprende o RPM mais alto já registrado, que no modo automático é o teto da
     * barra — e, uma vez gravado, não recua.
     *
     * Justamente por não recuar, um pico falso estragaria a escala para sempre.
     * Por isso o candidato só é promovido depois de [MAX_CONFIRM_FRAMES] frames
     * seguidos acima do teto atual: ruído elétrico não sustenta rotação alta
     * por um quinto de segundo, um motor subindo de giro sustenta.
     */
    private fun learnMaxRpm(rpm: Int) {
        if (rpm <= learnedMaxRpm) {
            maxCandidateFrames = 0
            return
        }
        if (rpm >= maxCandidateRpm) {
            maxCandidateRpm = rpm
            maxCandidateFrames++
        } else {
            // ainda acima do teto, mas caindo: vale o menor da sequência
            maxCandidateRpm = rpm
            maxCandidateFrames++
        }
        if (maxCandidateFrames >= MAX_CONFIRM_FRAMES) {
            learnedMaxRpm = maxCandidateRpm
            maxCandidateFrames = 0
            viewModelScope.launch { container.settingsStore.saveLearnedMaxRpm(learnedMaxRpm) }
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
        fix.kmh?.let { ratioCapture.add(fix.tsMs, rpm, it) }

        _state.value = _state.value.copy(
            speedKmh = fix.kmh,
            speedOrigin = fix.origin,
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

        /** ~0,2 s a 17 Hz: tempo demais para ruído, tempo de menos para o motor. */
        const val MAX_CONFIRM_FRAMES = 3
    }
}
