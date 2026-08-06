package br.dev.ftdash.gearing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TireCalcTest {

    private val tire195_55R15 = TireSpec(widthMm = 195, profilePct = 55, rimInches = 15)

    @Test
    fun `195 55 R15 tem 1871 mm de circunferencia`() {
        assertEquals(595.5, TireCalc.diameterMm(tire195_55R15), 0.5)
        assertEquals(1.871, TireCalc.circumferenceM(tire195_55R15), 0.001)
    }

    @Test
    fun `primeira marcha curta bate com a realidade`() {
        // 1ª 3,25 · diferencial 4,25 · 195/55R15 → ~123 rpm/km/h,
        // ou seja 3.000 rpm ≈ 24 km/h. É o que se sente no carro.
        val r = TireCalc.rpmPerKmh(gearboxRatio = 3.25, finalDrive = 4.25, tire = tire195_55R15)
        assertEquals(123.0, r, 2.0)
        assertEquals(24.4, TireCalc.kmhAt(3000, r), 0.5)
    }

    @Test
    fun `marchas mais longas dao razoes menores`() {
        val ratios = listOf(3.25, 1.90, 1.36, 1.03, 0.85)
            .map { TireCalc.rpmPerKmh(it, 4.25, tire195_55R15) }
        assertTrue("razões deveriam ser decrescentes", ratios.zipWithNext().all { (a, b) -> a > b })
    }

    @Test
    fun `pneu maior alonga a marcha`() {
        val small = TireCalc.rpmPerKmh(1.0, 4.0, TireSpec(185, 60, 14))
        val big = TireCalc.rpmPerKmh(1.0, 4.0, TireSpec(215, 45, 17))
        assertTrue("aro maior → menos rpm por km/h", big < small)
    }

    @Test
    fun `kmhAt protege contra razao invalida`() {
        assertEquals(0.0, TireCalc.kmhAt(3000, 0.0), 0.0)
    }

    @Test
    fun `captura por aprendizado grava a mediana da janela estavel`() {
        val capture = RatioCapture()
        // 3ª a 80 km/h, 4000 rpm → 50 rpm/km/h, com ruído pequeno
        var t = 0L
        repeat(20) { i ->
            val noise = if (i % 3 == 0) 12.0 else -8.0
            capture.add(t, 4000.0 + noise, 80f)
            t += 100
        }
        assertTrue("deveria estar estável", capture.isStable)
        val ratio = capture.capture(gear = 3, nowMs = 999)!!
        assertEquals(3, ratio.gear)
        assertEquals(50.0, ratio.rpmPerKmh, 0.2)
        assertEquals(RatioSource.LEARNED, ratio.source)
    }

    @Test
    fun `aceleracao nao passa no portao de estabilidade`() {
        val capture = RatioCapture()
        var t = 0L
        // acelerando: rpm sobe muito mais rápido que a velocidade
        repeat(20) { i ->
            capture.add(t, 2500.0 + i * 120, 60f + i * 0.5f)
            t += 100
        }
        assertTrue("acelerando não pode ser capturado", !capture.isStable)
        assertNull(capture.capture(gear = 3))
    }

    @Test
    fun `velocidade baixa nao passa no portao`() {
        val capture = RatioCapture()
        var t = 0L
        repeat(20) {
            capture.add(t, 2000.0, 15f)  // abaixo de MIN_CAPTURE_KMH
            t += 100
        }
        assertTrue(!capture.isStable)
    }

    @Test
    fun `perfil substitui a razao de uma marcha sem duplicar`() {
        val p = GearProfile()
            .withRatio(GearRatio(3, 50.0, RatioSource.LEARNED, 1))
            .withRatio(GearRatio(1, 123.0, RatioSource.MANUAL, 2))
            .withRatio(GearRatio(3, 51.0, RatioSource.EDITED, 3))

        assertEquals(2, p.ratios.size)
        assertEquals(listOf(1, 3), p.ratios.map { it.gear })
        assertEquals(51.0, p.ratioFor(3)!!.rpmPerKmh, 0.0)
        assertEquals(RatioSource.EDITED, p.ratioFor(3)!!.source)
    }
}
