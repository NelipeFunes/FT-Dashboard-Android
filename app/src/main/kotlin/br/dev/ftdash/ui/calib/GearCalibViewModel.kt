package br.dev.ftdash.ui.calib

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.dev.ftdash.AppContainer
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

enum class CalibMode { LEARN, MANUAL }

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
    val tireWidth: String = "195",
    val tireProfile: String = "55",
    val tireRim: String = "15",
    val finalDrive: String = "4.25",
    val gearboxRatios: List<String> = listOf("3.25", "1.90", "1.36", "1.03", "0.85"),

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
                )
            }
        }
    }

    fun setMode(mode: CalibMode) {
        _state.value = _state.value.copy(mode = mode, message = null)
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

    private fun parseTire(s: CalibUiState): TireSpec? {
        val w = s.tireWidth.toIntOrNull() ?: return null
        val p = s.tireProfile.toIntOrNull() ?: return null
        val r = s.tireRim.toIntOrNull() ?: return null
        if (w !in 100..400 || p !in 20..90 || r !in 10..24) return null
        return TireSpec(w, p, r)
    }
}
