package br.dev.ftdash.ui.dash.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.dev.ftdash.ui.theme.FtBlack
import br.dev.ftdash.ui.theme.LocalDashScale
import br.dev.ftdash.ui.theme.MonoFamily
import br.dev.ftdash.ui.theme.Zinc100
import br.dev.ftdash.ui.theme.Zinc400
import br.dev.ftdash.ui.theme.Zinc500
import br.dev.ftdash.ui.theme.Zinc800
import br.dev.ftdash.ui.theme.scaled
import br.dev.ftdash.ui.theme.times

/**
 * A barra de RPM da FuelTech.
 *
 * Duas coisas a definem, e nenhuma é o gradiente por si só.
 *
 * **O degradê é da escala, não do valor**: amarelo no começo, laranja no meio,
 * vermelho no fim, sempre nas mesmas posições. A barra não muda de cor conforme
 * sobe; ela revela a cor que já estava ali. O olho aprende "vermelho é lá na
 * direita" uma vez e depois lê a posição sem interpretar cor nenhuma.
 *
 * **A forma é inclinada**, não um retângulo. As bordas cortadas em diagonal e a
 * ponta do preenchimento acompanhando a mesma inclinação são o que dá a
 * silhueta reconhecível do mostrador — e a diagonal na ponta ainda ajuda a ler
 * o avanço, porque ela cruza a escala num ponto só em vez de num bloco.
 *
 * O número fica **sobre** a barra, à esquerda, com um fundo preto recortado na
 * mesma diagonal — é assim no mostrador da FT, e ganha a largura inteira da
 * tela para a barra em vez de gastar um terço dela com texto.
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
    val barHeight = BAR_HEIGHT.scaled(scale)

    Column(
        modifier
            .fillMaxWidth()
            .background(FtBlack)
            .padding(top = 3.dp, bottom = 1.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(barHeight),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val skew = h * SKEW_RATIO
                val track = slantedPath(0f, w, h, skew)

                clipPath(track) {
                    // Trilho: o degradê inteiro em versão apagada, para a escala
                    // de cor estar sempre visível. Recortá-lo no ponto atual
                    // deixaria a barra como uma tarja amarela sem informação
                    // nenhuma em marcha lenta.
                    drawRect(color = Zinc800, size = Size(w, h))
                    drawRect(
                        brush = Brush.horizontalGradient(colorStops = SCALE_STOPS, startX = 0f, endX = w),
                        size = Size(w, h),
                        alpha = 0.16f,
                    )

                    if (hasData) {
                        val filled = (rpm.toFloat() / safeMax).coerceIn(0f, 1f) * w
                        if (filled > 0f) {
                            // O degradê é medido sobre a largura TOTAL e
                            // recortado no ponto atual. Medido sobre a parte
                            // preenchida, a ponta ficaria vermelha em qualquer
                            // rotação — inclusive na lenta.
                            clipPath(slantedPath(0f, filled, h, skew)) {
                                drawRect(
                                    brush = Brush.horizontalGradient(colorStops = SCALE_STOPS, startX = 0f, endX = w),
                                    size = Size(w, h),
                                )
                            }
                        }
                        if (blinking) {
                            drawRect(color = FtRed.copy(alpha = 0.55f), size = Size(w, h))
                        }
                        if (peakRpm > rpm) {
                            val px = (peakRpm.toFloat() / safeMax).coerceIn(0f, 1f) * w
                            // O marcador segue a mesma diagonal da barra
                            drawPath(
                                path = slantedPath(px - 2f, px + 2f, h, skew),
                                color = Zinc100,
                            )
                        }
                    }
                }
            }

            // Número por cima da barra, com o fundo recortado na mesma diagonal.
            //
            // O fundo é translúcido, não preto sólido: sólido ele engolia os
            // primeiros ~1.300 rpm da escala, e em marcha lenta a barra ficava
            // invisível justamente porque o preenchimento ainda estava atrás do
            // número. Com 78% de preto o branco continua legível e a cor da
            // barra aparece por baixo — dá para ver que há rotação ali.
            Row(
                Modifier
                    .align(Alignment.CenterStart)
                    .background(FtBlack.copy(alpha = 0.78f), SlantedEnd)
                    .padding(start = 6.dp, end = barHeight.value.times(SKEW_RATIO).dp + 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = if (hasData) rpm.toString() else "----",
                    style = TextStyle(
                        fontFamily = MonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 40.sp * scale,
                        fontFeatureSettings = "tnum",
                    ),
                    color = if (blinking) FtRed else if (hasData) Zinc100 else Zinc500,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
                Text(
                    "RPM",
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp * scale),
                    color = Zinc400,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 3.dp, bottom = 5.dp),
                )
            }
        }

        // Escala em milhares, sob a barra e na largura inteira
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

/**
 * Paralelogramo de [left] a [right], inclinado para a direita.
 *
 * O topo fica deslocado [skew] à direita da base, que é o mesmo corte usado no
 * trilho, no preenchimento e no marcador de pico — se cada um tivesse a sua
 * inclinação, as diagonais brigariam entre si.
 */
private fun DrawScope.slantedPath(left: Float, right: Float, h: Float, skew: Float) = Path().apply {
    moveTo(left + skew, 0f)
    lineTo(right + skew, 0f)
    lineTo(right, h)
    lineTo(left, h)
    close()
}

/** Fundo do número: reto na borda da tela, cortado na diagonal do lado direito. */
private val SlantedEnd = androidx.compose.foundation.shape.GenericShape { size, _ ->
    val skew = size.height * SKEW_RATIO
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width - skew, size.height)
    lineTo(0f, size.height)
    close()
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

/** Quanto o topo avança sobre a base, em fração da altura. */
private const val SKEW_RATIO = 0.30f

private val BAR_HEIGHT = 44.dp

private const val SHIFT_BLINK_HALF_PERIOD_MS = 83L
