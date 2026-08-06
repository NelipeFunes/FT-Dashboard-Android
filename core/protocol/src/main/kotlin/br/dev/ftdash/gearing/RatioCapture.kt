package br.dev.ftdash.gearing

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Janela deslizante da razão rpm/km/h, usada no modo de calibração por
 * aprendizado guiado.
 *
 * O botão "capturar" só habilita quando a razão está **estável** — velocidade
 * acima de [MIN_CAPTURE_KMH] e coeficiente de variação abaixo de
 * [MAX_CV] na janela de [windowMs]. Sem esse portão o usuário capturaria uma
 * razão tirada no meio de uma aceleração, que não corresponde a marcha nenhuma.
 *
 * A captura grava a **mediana** da janela, não a média: mediana ignora a
 * fixação de GPS esquisita ocasional sem precisar de rejeição de outlier.
 */
class RatioCapture(private val windowMs: Long = 2000L) {

    private val ts = ArrayDeque<Long>()
    private val ratios = ArrayDeque<Double>()
    private var lastKmh = 0f

    val sampleCount: Int get() = ratios.size

    fun add(tsMs: Long, rpm: Double, kmh: Float) {
        lastKmh = kmh
        if (kmh <= 0f || rpm <= 0.0) return
        ts.addLast(tsMs)
        ratios.addLast(rpm / kmh.toDouble())
        while (ts.isNotEmpty() && tsMs - ts.first() > windowMs) {
            ts.removeFirst()
            ratios.removeFirst()
        }
    }

    fun clear() {
        ts.clear()
        ratios.clear()
        lastKmh = 0f
    }

    val mean: Double?
        get() = if (ratios.isEmpty()) null else ratios.sum() / ratios.size

    val stdDev: Double?
        get() {
            val m = mean ?: return null
            if (ratios.size < 2) return null
            val variance = ratios.sumOf { (it - m) * (it - m) } / (ratios.size - 1)
            return sqrt(variance)
        }

    /** Coeficiente de variação: desvio-padrão relativo à média. 0,03 = 3 %. */
    val coefficientOfVariation: Double?
        get() {
            val m = mean ?: return null
            val s = stdDev ?: return null
            return if (abs(m) < 1e-9) null else s / abs(m)
        }

    val median: Double?
        get() {
            if (ratios.isEmpty()) return null
            val sorted = ratios.sorted()
            val mid = sorted.size / 2
            return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
        }

    /** Pronto para capturar? É o que habilita o botão na tela. */
    val isStable: Boolean
        get() {
            if (ratios.size < MIN_SAMPLES) return false
            if (lastKmh < MIN_CAPTURE_KMH) return false
            val cv = coefficientOfVariation ?: return false
            return cv <= MAX_CV
        }

    /** Mediana da janela como razão da marcha, ou null se ainda não está estável. */
    fun capture(gear: Int, nowMs: Long = System.currentTimeMillis()): GearRatio? {
        if (!isStable) return null
        val value = median ?: return null
        return GearRatio(gear = gear, rpmPerKmh = value, source = RatioSource.LEARNED, capturedAtMs = nowMs)
    }

    companion object {
        /** Abaixo disso o erro relativo do GPS domina a medida. */
        const val MIN_CAPTURE_KMH = 25f
        const val MAX_CV = 0.03
        const val MIN_SAMPLES = 4
    }
}
