package br.dev.ftdash.ui.dash.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import br.dev.ftdash.ui.theme.LocalDashScale
import br.dev.ftdash.ui.theme.MonoFamily
import br.dev.ftdash.ui.theme.scaled
import br.dev.ftdash.ui.theme.times
import br.dev.ftdash.ui.theme.Zinc100
import br.dev.ftdash.ui.theme.Zinc400
import br.dev.ftdash.ui.theme.Zinc500
import br.dev.ftdash.ui.theme.Zinc800
import br.dev.ftdash.ui.theme.FtBlack
import br.dev.ftdash.ui.theme.Zinc950

/**
 * A barra de RPM da FuelTech.
 *
 * A diferença para uma barra comum — e a razão de ela ser reconhecível de
 * longe — é que o **degradê é da escala, não do valor**: amarelo no começo,
 * laranja no meio, vermelho no fim, sempre nas mesmas posições. A barra não
 * muda de cor conforme sobe; ela revela a cor que já estava ali. O olho aprende
 * "vermelho é lá na direita" uma vez e depois lê a posição sem precisar
 * interpretar cor nenhuma.
 *
 * Os números da escala ficam **embaixo**, em milhares, e o valor grande fica à
 * esquerda, fora da barra.
 */
@Composable
fun FtRpmBar(
    rpm: Int,
    peakRpm: Int,
    shiftRpm: Int,
    maxRpm: Int,
    hasData: Boolean,
    modifier: Modifier = Modifier,
) {
    val scale = LocalDashScale.current
    val safeMax = maxRpm.coerceAtLeast(1000)
    val blinking = hasData && rpm >= shiftRpm &&
        (System.currentTimeMillis() / SHIFT_BLINK_HALF_PERIOD_MS) % 2L == 0L

    Row(
        modifier
            .fillMaxWidth()
            .background(FtBlack)
            .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Número grande à esquerda, como na FT
        Row(
            Modifier.width(190.dp.scaled(scale)),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = if (hasData) rpm.toString() else "----",
                style = TextStyle(
                    fontFamily = MonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 44.sp * scale,
                    fontFeatureSettings = "tnum",
                ),
                color = if (blinking) FtRed else if (hasData) Zinc100 else Zinc500,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
            Text(
                "RPM",
                style = TextStyle(fontFamily = MonoFamily, fontSize = 13.sp * scale),
                color = Zinc400,
                modifier = Modifier.padding(start = 3.dp, bottom = 6.dp),
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(Modifier.weight(1f)) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(30.dp.scaled(scale)),
            ) {
                val w = size.width
                val h = size.height

                // O trilho inteiro leva o degradê apagado, e o preenchimento
                // vem por cima com a cor viva. Só o preenchido é o valor — mas
                // deixar a escala de cor sempre visível é o que faz a barra ser
                // reconhecida de relance: em marcha lenta, um degradê recortado
                // aos 30 % seria uma tarja amarela sem informação nenhuma.
                drawRect(color = Zinc800, size = Size(w, h))
                drawRect(
                    brush = Brush.horizontalGradient(colorStops = SCALE_STOPS, startX = 0f, endX = w),
                    size = Size(w, h),
                    alpha = 0.16f,
                )

                if (hasData) {
                    val filled = (rpm.toFloat() / safeMax).coerceIn(0f, 1f) * w
                    if (filled > 0f) {
                        // O degradê é medido sobre a LARGURA TOTAL e recortado
                        // no ponto atual. Se fosse medido sobre a parte
                        // preenchida, a barra ficaria vermelha na ponta em
                        // qualquer rotação — inclusive na lenta.
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colorStops = SCALE_STOPS,
                                startX = 0f,
                                endX = w,
                            ),
                            size = Size(filled, h),
                        )
                    }
                    if (blinking) {
                        drawRect(color = FtRed.copy(alpha = 0.55f), size = Size(w, h))
                    }
                    // Marcador de pico
                    if (peakRpm > rpm) {
                        val px = (peakRpm.toFloat() / safeMax).coerceIn(0f, 1f) * w
                        drawRect(
                            color = Zinc100,
                            topLeft = Offset((px - 1.5f).coerceIn(0f, w - 3f), 0f),
                            size = Size(3f, h),
                        )
                    }
                }
            }

            // Escala em milhares, embaixo da barra
            Row(Modifier.fillMaxWidth().padding(top = 1.dp)) {
                val steps = (safeMax / 1000).coerceAtLeast(1)
                for (i in 1..steps) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            i.toString(),
                            style = TextStyle(
                                fontFamily = MonoFamily,
                                fontSize = 11.sp * scale,
                                fontFeatureSettings = "tnum",
                            ),
                            color = Zinc400,
                        )
                    }
                }
            }
        }
    }
}

/**
 * As paradas de cor da escala, iguais às da FT: amarelo no início, laranja por
 * volta de dois terços, vermelho no fim.
 */
private val SCALE_STOPS = arrayOf(
    0.00f to Color(0xFFFFE100),
    0.45f to Color(0xFFFFC400),
    0.70f to Color(0xFFFF7A00),
    0.88f to Color(0xFFFF3B00),
    1.00f to Color(0xFFE30000),
)

val FtRed = Color(0xFFE30000)

private const val SHIFT_BLINK_HALF_PERIOD_MS = 83L
