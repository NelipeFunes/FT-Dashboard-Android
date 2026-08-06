package br.dev.ftdash.ui.dash.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import br.dev.ftdash.ui.theme.Amber500
import br.dev.ftdash.ui.theme.Emerald500
import br.dev.ftdash.ui.theme.NumberHuge
import br.dev.ftdash.ui.theme.NumberSmall
import br.dev.ftdash.ui.theme.Red500
import br.dev.ftdash.ui.theme.Zinc500
import br.dev.ftdash.ui.theme.Zinc100
import br.dev.ftdash.ui.theme.Zinc850
import br.dev.ftdash.ui.theme.Zinc950

/**
 * A faixa de RPM no topo do painel — o "gráfico crescendo" do FTManager.
 *
 * Decisões que importam quando isto está no para-brisa a 100 km/h:
 *
 * - **Cor progressiva.** Verde até 85 % do corte, âmbar daí até o corte,
 *   vermelho depois. A transição é contínua, então dá para perceber a
 *   aproximação do corte pela periferia da visão, sem ler número nenhum.
 * - **Marcador de pico.** Uma linha clara segura o RPM máximo recente por
 *   ~1,2 s. Numa passagem rápida de marcha dá para ver onde a rotação chegou
 *   mesmo tendo desviado o olho.
 * - **Shift light.** Acima do RPM de troca a barra inteira pisca a ~6 Hz. O
 *   piscar vem do relógio no momento do desenho, não de uma animação em laço:
 *   como a telemetria já redesenha a 17 Hz, sai de graça.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun RpmBar(
    rpm: Int,
    peakRpm: Int,
    redlineRpm: Int,
    shiftRpm: Int,
    maxRpm: Int,
    hasData: Boolean,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()

    Box(
        modifier
            .fillMaxWidth()
            .height(88.dp)
            .background(Zinc950)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val barH = h * 0.62f
            val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            val safeMax = maxRpm.coerceAtLeast(1000)

            fun x(value: Int) = (value.toFloat() / safeMax).coerceIn(0f, 1f) * w

            // trilho
            drawRoundRect(color = Zinc850, size = Size(w, barH), cornerRadius = corner)

            // zona vermelha como fundo, para a barra "entrar" nela
            val redlineX = x(redlineRpm)
            if (redlineX < w) {
                drawRect(
                    color = Red500.copy(alpha = 0.18f),
                    topLeft = Offset(redlineX, 0f),
                    size = Size(w - redlineX, barH),
                )
            }

            if (hasData) {
                val fillW = x(rpm)
                val fillColor = rpmColor(rpm, redlineRpm)
                val blinking = rpm >= shiftRpm &&
                    (System.currentTimeMillis() / SHIFT_BLINK_HALF_PERIOD_MS) % 2L == 0L

                drawRoundRect(
                    color = if (blinking) Red500 else fillColor,
                    size = Size(fillW.coerceAtLeast(2f), barH),
                    cornerRadius = corner,
                )

                // marcador de pico
                if (peakRpm > rpm) {
                    val px = x(peakRpm)
                    drawRect(
                        color = Zinc100,
                        topLeft = Offset((px - 1.5f).coerceIn(0f, w - 3f), 0f),
                        size = Size(3f, barH),
                    )
                }
            }

            // escala: traço maior + rótulo a cada 1.000, menor a cada 500
            var tick = 0
            while (tick <= safeMax) {
                val tx = x(tick)
                val major = tick % 1000 == 0
                drawRect(
                    color = if (major) Zinc500 else Zinc850,
                    topLeft = Offset(tx.coerceAtMost(w - 1f), barH + 2.dp.toPx()),
                    size = Size(1.5f, if (major) 7.dp.toPx() else 4.dp.toPx()),
                )
                if (major && tick > 0) {
                    val label = (tick / 1000).toString()
                    val layout = measurer.measure(label, NumberSmall.copy(color = Zinc500))
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(
                            (tx - layout.size.width / 2f).coerceIn(0f, w - layout.size.width),
                            barH + 10.dp.toPx(),
                        ),
                    )
                }
                tick += 500
            }

            // o número, dentro da própria barra e alinhado à direita
            val text = if (hasData) rpm.toString() else "----"
            val numberLayout = measurer.measure(
                text,
                NumberHuge.copy(color = if (hasData) Zinc100 else Zinc500),
            )
            drawText(
                textLayoutResult = numberLayout,
                topLeft = Offset(
                    w - numberLayout.size.width - 8.dp.toPx(),
                    (barH - numberLayout.size.height) / 2f,
                ),
            )
            val unitLayout = measurer.measure("RPM", NumberSmall.copy(color = Zinc500))
            drawText(
                textLayoutResult = unitLayout,
                topLeft = Offset(
                    w - numberLayout.size.width - unitLayout.size.width - 14.dp.toPx(),
                    (barH - unitLayout.size.height) / 2f,
                ),
            )
        }
    }
}

/**
 * Verde até 60 % do corte, transição contínua para âmbar até 85 % e daí para
 * vermelho no corte. Contínua de propósito: a mudança gradual de cor é o que dá
 * para captar sem tirar o olho da estrada.
 */
private fun rpmColor(rpm: Int, redlineRpm: Int): Color {
    val t = rpm.toFloat() / redlineRpm.coerceAtLeast(1)
    return when {
        t >= 1f -> Red500
        t <= 0.60f -> Emerald500
        t <= 0.85f -> lerp(Emerald500, Amber500, (t - 0.60f) / 0.25f)
        else -> lerp(Amber500, Red500, (t - 0.85f) / 0.15f)
    }
}

/** ~6 Hz de piscada: rápido o suficiente para chamar atenção, sem virar estroboscópio. */
private const val SHIFT_BLINK_HALF_PERIOD_MS = 83L
