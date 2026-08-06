package br.dev.ftdash.gearing

import kotlin.math.PI

/**
 * Converte relações mecânicas em rpm/km/h — a base do modo de calibração
 * manual.
 *
 * ```
 * d_mm = aro·25,4 + 2·largura·(perfil/100)
 * C_m  = π·d_mm/1000
 * rpm/km/h = marcha · diferencial · 1000 / (C_m · 60)
 * ```
 *
 * O `1000/60` converte km/h em m/min: a roda dá `1000·v/C` voltas por hora, e o
 * motor gira `marcha·diferencial` vezes por volta da roda.
 */
object TireCalc {

    /** Diâmetro total do conjunto pneu+roda, em mm. */
    fun diameterMm(tire: TireSpec): Double =
        tire.rimInches * 25.4 + 2.0 * tire.widthMm * (tire.profilePct / 100.0)

    /** Circunferência de rolamento, em metros. */
    fun circumferenceM(tire: TireSpec): Double = PI * diameterMm(tire) / 1000.0

    /** rpm/km/h para uma relação de caixa, um diferencial e um pneu. */
    fun rpmPerKmh(gearboxRatio: Double, finalDrive: Double, tire: TireSpec): Double =
        rpmPerKmh(gearboxRatio, finalDrive, circumferenceM(tire))

    /** Variante para quem sabe a circunferência de rolamento direto (em metros). */
    fun rpmPerKmh(gearboxRatio: Double, finalDrive: Double, circumferenceM: Double): Double {
        require(circumferenceM > 0) { "circunferência precisa ser > 0" }
        return gearboxRatio * finalDrive * 1000.0 / (circumferenceM * 60.0)
    }

    /** Velocidade correspondente a um RPM numa marcha — usado na prévia da tela manual. */
    fun kmhAt(rpm: Int, rpmPerKmh: Double): Double =
        if (rpmPerKmh <= 0) 0.0 else rpm / rpmPerKmh
}
