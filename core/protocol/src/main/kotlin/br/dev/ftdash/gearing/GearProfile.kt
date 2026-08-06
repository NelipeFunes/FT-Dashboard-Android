package br.dev.ftdash.gearing

import kotlinx.serialization.Serializable

/** De onde veio a razão de uma marcha. */
@Serializable
enum class RatioSource {
    /** Capturada no carro, andando em velocidade constante. */
    LEARNED,

    /** Calculada a partir de relações da caixa + diferencial + pneu. */
    MANUAL,

    /** Digitada direto pelo usuário na lista final. */
    EDITED,
}

/**
 * Pneu no formato brasileiro: 195/55 R15 → `width = 195`, `profile = 55`,
 * `rimInches = 15`.
 */
@Serializable
data class TireSpec(
    val widthMm: Int,
    val profilePct: Int,
    val rimInches: Int,
)

/**
 * A razão de uma marcha, em **rpm por km/h** — a única grandeza que o
 * estimador usa. Os dois modos de calibração convergem para ela, o que deixa
 * aprendizado e entrada manual comparáveis na mesma tela.
 */
@Serializable
data class GearRatio(
    val gear: Int,
    val rpmPerKmh: Double,
    val source: RatioSource,
    val capturedAtMs: Long,
)

@Serializable
data class GearProfile(
    val name: String = "Padrão",
    val ratios: List<GearRatio> = emptyList(),
    /** Relação do diferencial, quando conhecida (só o modo manual usa). */
    val finalDrive: Double? = null,
    val tire: TireSpec? = null,
    /** Relações da caixa por marcha, do modo manual (índice 0 = 1ª). */
    val gearboxRatios: List<Double> = emptyList(),
    /** Erro relativo máximo para *entrar* numa marcha. */
    val enterTolerance: Double = 0.07,
    /** Erro relativo a partir do qual *sai* da marcha atual. Ver [GearEstimator]. */
    val exitTolerance: Double = 0.14,
) {
    val isCalibrated: Boolean get() = ratios.isNotEmpty()

    fun ratioFor(gear: Int): GearRatio? = ratios.firstOrNull { it.gear == gear }

    /** Insere ou substitui a razão de uma marcha, mantendo a lista ordenada. */
    fun withRatio(ratio: GearRatio): GearProfile =
        copy(ratios = (ratios.filterNot { it.gear == ratio.gear } + ratio).sortedBy { it.gear })

    fun withoutGear(gear: Int): GearProfile =
        copy(ratios = ratios.filterNot { it.gear == gear })
}
