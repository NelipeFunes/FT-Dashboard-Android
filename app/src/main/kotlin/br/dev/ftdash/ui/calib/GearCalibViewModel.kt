package br.dev.ftdash.ui.calib

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.dev.ftdash.AppContainer
import br.dev.ftdash.data.SourceKind
import br.dev.ftdash.data.UsbBusReport
import br.dev.ftdash.data.UsbEventLog
import br.dev.ftdash.data.settings.RpmScaleMode
import br.dev.ftdash.trip.FuelSetup
import br.dev.ftdash.gearing.GearProfile
import br.dev.ftdash.gearing.GearRatio
import br.dev.ftdash.gearing.RatioSource
import br.dev.ftdash.gearing.TireCalc
import br.dev.ftdash.gearing.TireSpec
import br.dev.ftdash.ui.dash.DashViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CalibMode { LEARN, MANUAL, RPM, FUEL, USB }

/**
 * Unidade da vazão do bico.
 *
 * A FuelTech trabalha em **lb/h** — o FTManager, o site e o material de
 * treinamento dela usam essa unidade. Obrigar a converter à mão antes de
 * digitar é convite a erro de conta num número que multiplica todo o cálculo de
 * combustível, então o app aceita as duas e converte.
 */
enum class InjectorUnit(val label: String) {
    CC_MIN("cc/min"),
    LB_H("lb/h"),
}

data class CalibUiState(
    val mode: CalibMode = CalibMode.LEARN,
    val profile: GearProfile = GearProfile(),

    // --- modo aprendizado ---
    val selectedGear: Int = 1,
    val instantRatio: Double? = null,
    val stabilityCv: Double? = null,
    val canCapture: Boolean = false,
    val speedKmh: Float? = null,
    val rpm: Int = 0,

    // --- modo manual ---
    // Vazios de propósito: o app não inventa dados do carro do usuário.
    val tireWidth: String = "",
    val tireProfile: String = "",
    val tireRim: String = "",
    val finalDrive: String = "",
    val gearboxRatios: List<String> = List(5) { "" },

    // --- aba de RPM ---
    val rpmScaleMode: RpmScaleMode = RpmScaleMode.AUTO,
    val learnedMaxRpm: Int = 0,
    val manualRedline: String = "",
    val manualShift: String = "",
    val manualMaxRpm: String = "",

    // --- aba de combustível ---
    val tankLiters: String = "",
    val injectorFlow: String = "",
    /** Unidade em que o campo do bico está sendo digitado. */
    val injectorUnit: InjectorUnit = InjectorUnit.CC_MIN,
    val injectorCount: String = "",
    val fuelUsedLiters: Double = 0.0,
    val fuelRemainingLiters: Double? = null,
    /** Campo do abastecimento parcial. */
    val addFuelLiters: String = "",

    // --- aba de USB ---
    val usbReport: UsbBusReport? = null,
    /** Contadores ao vivo do stream — saíram do painel, mas continuam aqui. */
    val hz: Float = 0f,
    val bytesPerSec: Int = 0,
    val framesOk: Long = 0,
    val crcFail: Long = 0,
    val frameLen: Int = 0,
    val sourceKind: SourceKind = SourceKind.REPLAY,
    /** Histórico com hora, mais recente primeiro — ver [br.dev.ftdash.data.UsbEventLog]. */
    val usbLog: List<UsbEventLog.Entry> = emptyList(),
    val usbLogPath: String? = null,

    val message: String? = null,
)

/**
 * Calibração de marcha nos dois caminhos.
 *
 * Eles não competem: os dois produzem a mesma grandeza (rpm/km/h) e caem na
 * mesma lista editável. Na prática o manual serve para começar com uma
 * estimativa razoável e o aprendizado serve para corrigi-la com o carro real —
 * pneu gasto, patinagem, erro de relação de catálogo, tudo entra na medida.
 */
class GearCalibViewModel(
    private val container: AppContainer,
    private val dashViewModel: DashViewModel,
) : ViewModel() {

    private val _state = MutableStateFlow(CalibUiState())
    val state: StateFlow<CalibUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.settingsStore.settings.collect { s ->
                _state.value = _state.value.copy(
                    profile = s.gearProfile,
                    finalDrive = s.gearProfile.finalDrive?.toString() ?: _state.value.finalDrive,
                    tireWidth = s.gearProfile.tire?.widthMm?.toString() ?: _state.value.tireWidth,
                    tireProfile = s.gearProfile.tire?.profilePct?.toString() ?: _state.value.tireProfile,
                    tireRim = s.gearProfile.tire?.rimInches?.toString() ?: _state.value.tireRim,
                    gearboxRatios = s.gearProfile.gearboxRatios
                        .takeIf { it.isNotEmpty() }
                        ?.map { it.toString() }
                        ?: _state.value.gearboxRatios,
                    tankLiters = s.fuelSetup.tankLiters.takeIf { it > 0 }?.let { "%.0f".format(it) }
                        ?: _state.value.tankLiters,
                    injectorFlow = s.fuelSetup.injectorFlowCcMin.takeIf { it > 0 }
                        ?.let { "%.0f".format(it) } ?: _state.value.injectorFlow,
                    injectorCount = s.fuelSetup.injectorCount.takeIf { it > 0 }?.toString()
                        ?: _state.value.injectorCount,
                    fuelUsedLiters = s.trip.tankUsedLiters,
                    fuelRemainingLiters = if (s.fuelSetup.isComplete) {
                        (s.fuelSetup.tankLiters - s.trip.tankUsedLiters).coerceAtLeast(0.0)
                    } else {
                        null
                    },
                    rpmScaleMode = s.rpmScaleMode,
                    learnedMaxRpm = s.learnedMaxRpm,
                    manualRedline = s.redlineRpm.takeIf { it > 0 }?.toString()
                        ?: _state.value.manualRedline,
                    manualShift = s.shiftRpm.takeIf { it > 0 }?.toString()
                        ?: _state.value.manualShift,
                    manualMaxRpm = s.maxRpm.takeIf { it > 0 }?.toString()
                        ?: _state.value.manualMaxRpm,
                )
            }
        }
        viewModelScope.launch {
            container.usbEventLog.entries.collect { entries ->
                _state.value = _state.value.copy(
                    usbLog = entries,
                    usbLogPath = container.usbEventLog.path,
                )
            }
        }
        viewModelScope.launch {
            // Espelha os números vivos do painel na tela de calibração.
            dashViewModel.state.collect { dash ->
                val capture = dashViewModel.ratioCapture
                _state.value = _state.value.copy(
                    instantRatio = dashViewModel.instantRatio,
                    stabilityCv = capture.coefficientOfVariation,
                    canCapture = capture.isStable,
                    speedKmh = dash.speedKmh,
                    rpm = dash.rpm,
                    hz = dash.hz,
                    bytesPerSec = dash.bytesPerSec,
                    framesOk = dash.framesOk,
                    crcFail = dash.crcFail,
                    frameLen = dash.frameLen,
                    sourceKind = dash.sourceKind,
                )
            }
        }
    }

    fun setMode(mode: CalibMode) {
        _state.value = _state.value.copy(mode = mode, message = null)
        if (mode == CalibMode.USB) scanUsb()
    }

    fun updateFuelField(tank: String? = null, flow: String? = null, count: String? = null) {
        val s = _state.value
        _state.value = s.copy(
            tankLiters = tank ?: s.tankLiters,
            injectorFlow = flow ?: s.injectorFlow,
            injectorCount = count ?: s.injectorCount,
            message = null,
        )
    }

    fun setInjectorUnit(unit: InjectorUnit) {
        _state.value = _state.value.copy(injectorUnit = unit, message = null)
    }

    /** Vazão digitada, sempre em cc/min — é como o cálculo trabalha. */
    fun injectorFlowCcMin(): Double? {
        val typed = _state.value.injectorFlow.replace(',', '.').toDoubleOrNull() ?: return null
        if (typed <= 0) return null
        return when (_state.value.injectorUnit) {
            InjectorUnit.CC_MIN -> typed
            InjectorUnit.LB_H -> FuelSetup.lbPerHourToCcMin(typed)
        }
    }

    fun updateAddFuelField(liters: String) {
        _state.value = _state.value.copy(addFuelLiters = liters, message = null)
    }

    /** Litros a somar, ou null se o campo não tem um número usável. */
    fun addFuelLitersValue(): Double? =
        _state.value.addFuelLiters.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

    fun applyFuelSetup() = viewModelScope.launch {
        val s = _state.value
        val setup = FuelSetup(
            tankLiters = s.tankLiters.replace(',', '.').toDoubleOrNull() ?: 0.0,
            injectorFlowCcMin = injectorFlowCcMin() ?: 0.0,
            injectorCount = s.injectorCount.toIntOrNull() ?: 0,
        )
        if (!setup.isComplete) {
            _state.value = s.copy(
                message = "Preencha tanque, vazão do bico e quantidade de bicos.",
            )
            return@launch
        }
        container.settingsStore.saveFuelSetup(setup)
        _state.value = _state.value.copy(
            message = "Tanque de %.0f L, %d bicos de %.0f cc/min.".format(
                setup.tankLiters, setup.injectorCount, setup.injectorFlowCcMin,
            ),
        )
    }

    /** Varre o barramento e mostra o que a multimídia enxerga. */
    fun scanUsb() {
        _state.value = _state.value.copy(usbReport = container.scanUsb())
    }

    /** Troca a fonte de telemetria para o USB e volta para o painel. */
    fun useUsbSource() = viewModelScope.launch {
        container.telemetryRepository.selectSource(SourceKind.USB)
        container.settingsStore.saveSourceKind(SourceKind.USB)
        _state.value = _state.value.copy(message = "Fonte trocada para USB.")
    }

    fun useReplaySource() = viewModelScope.launch {
        container.telemetryRepository.selectSource(SourceKind.REPLAY)
        container.settingsStore.saveSourceKind(SourceKind.REPLAY)
        _state.value = _state.value.copy(message = "Fonte trocada para replay.")
    }

    fun selectGear(gear: Int) {
        _state.value = _state.value.copy(selectedGear = gear)
    }

    /** Grava a mediana da janela estável como razão da marcha selecionada. */
    fun captureSelectedGear() = viewModelScope.launch {
        val gear = _state.value.selectedGear
        val ratio = dashViewModel.ratioCapture.capture(gear)
        if (ratio == null) {
            _state.value = _state.value.copy(
                message = "Razão ainda instável — mantenha velocidade constante acima de 25 km/h.",
            )
            return@launch
        }
        val updated = _state.value.profile.withRatio(ratio)
        container.settingsStore.saveGearProfile(updated)
        _state.value = _state.value.copy(
            message = "%dª marcha gravada: %.1f rpm/km/h".format(gear, ratio.rpmPerKmh),
        )
    }

    fun removeGear(gear: Int) = viewModelScope.launch {
        container.settingsStore.saveGearProfile(_state.value.profile.withoutGear(gear))
    }

    fun updateManualField(
        tireWidth: String? = null,
        tireProfile: String? = null,
        tireRim: String? = null,
        finalDrive: String? = null,
        gearIndex: Int? = null,
        gearboxRatio: String? = null,
    ) {
        val s = _state.value
        _state.value = s.copy(
            tireWidth = tireWidth ?: s.tireWidth,
            tireProfile = tireProfile ?: s.tireProfile,
            tireRim = tireRim ?: s.tireRim,
            finalDrive = finalDrive ?: s.finalDrive,
            gearboxRatios = if (gearIndex != null && gearboxRatio != null) {
                s.gearboxRatios.toMutableList().also { it[gearIndex] = gearboxRatio }
            } else {
                s.gearboxRatios
            },
            message = null,
        )
    }

    /** Prévia do modo manual: rpm/km/h por marcha, ou null se falta dado. */
    fun manualPreview(): List<Pair<Int, Double>> {
        val s = _state.value
        val tire = parseTire(s) ?: return emptyList()
        val final = s.finalDrive.toDoubleOrNull() ?: return emptyList()
        return s.gearboxRatios.mapIndexedNotNull { i, text ->
            text.toDoubleOrNull()
                ?.takeIf { it > 0 }
                ?.let { (i + 1) to TireCalc.rpmPerKmh(it, final, tire) }
        }
    }

    fun applyManual() = viewModelScope.launch {
        val s = _state.value
        val preview = manualPreview()
        if (preview.isEmpty()) {
            _state.value = s.copy(message = "Confira o pneu, o diferencial e as relações.")
            return@launch
        }
        val now = System.currentTimeMillis()
        // Substitui só as marchas presentes no formulário, preservando as que já
        // foram aprendidas no carro e não estão sendo recalculadas.
        var profile = s.profile.copy(
            finalDrive = s.finalDrive.toDoubleOrNull(),
            tire = parseTire(s),
            gearboxRatios = s.gearboxRatios.mapNotNull { it.toDoubleOrNull() },
        )
        for ((gear, ratio) in preview) {
            profile = profile.withRatio(GearRatio(gear, ratio, RatioSource.MANUAL, now))
        }
        container.settingsStore.saveGearProfile(profile)
        _state.value = _state.value.copy(message = "${preview.size} marchas calculadas e salvas.")
    }

    /** Edição fina de uma razão já existente, venha ela de onde vier. */
    fun editRatio(gear: Int, rpmPerKmh: Double) = viewModelScope.launch {
        val updated = _state.value.profile.withRatio(
            GearRatio(gear, rpmPerKmh, RatioSource.EDITED, System.currentTimeMillis()),
        )
        container.settingsStore.saveGearProfile(updated)
    }

    // ---- aba de RPM ----

    fun setRpmScaleMode(mode: RpmScaleMode) = viewModelScope.launch {
        container.settingsStore.saveRpmScaleMode(mode)
    }

    fun updateRpmField(redline: String? = null, shift: String? = null, max: String? = null) {
        val s = _state.value
        _state.value = s.copy(
            manualRedline = redline ?: s.manualRedline,
            manualShift = shift ?: s.manualShift,
            manualMaxRpm = max ?: s.manualMaxRpm,
            message = null,
        )
    }

    fun applyRpmLimits() = viewModelScope.launch {
        val s = _state.value
        val redline = s.manualRedline.toIntOrNull()
        val shift = s.manualShift.toIntOrNull()
        val max = s.manualMaxRpm.toIntOrNull()

        if (redline == null || redline !in 1_000..12_000) {
            _state.value = s.copy(message = "Corte precisa estar entre 1.000 e 12.000 rpm.")
            return@launch
        }
        // Escala e aviso têm ordem obrigatória; sem isso a barra fica sem
        // sentido (aviso depois do corte, corte fora da escala).
        val effectiveMax = (max ?: redline).coerceAtLeast(redline)
        val effectiveShift = (shift ?: redline).coerceIn(500, redline)

        container.settingsStore.saveRpmLimits(redline, effectiveShift, effectiveMax)
        container.settingsStore.saveRpmScaleMode(RpmScaleMode.MANUAL)
        _state.value = _state.value.copy(
            manualShift = effectiveShift.toString(),
            manualMaxRpm = effectiveMax.toString(),
            message = "Corte em $redline rpm, aviso em $effectiveShift, escala até $effectiveMax.",
        )
    }

    fun resetLearnedMax() = viewModelScope.launch {
        container.settingsStore.resetLearnedMaxRpm()
        _state.value = _state.value.copy(message = "Pico zerado — vai reaprender na próxima subida de giro.")
    }

    private fun parseTire(s: CalibUiState): TireSpec? {
        val w = s.tireWidth.toIntOrNull() ?: return null
        val p = s.tireProfile.toIntOrNull() ?: return null
        val r = s.tireRim.toIntOrNull() ?: return null
        if (w !in 100..400 || p !in 20..90 || r !in 10..24) return null
        return TireSpec(w, p, r)
    }
}
