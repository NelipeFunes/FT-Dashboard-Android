package br.dev.ftdash.ui.dash.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
 * A forma é uma **cunha**: fina na esquerda, subindo de altura até a direita.
 * Isso resolve três coisas de uma vez, e é por isso que o mostrador da FT é
 * assim:
 *
 * - a altura vira uma segunda codificação da rotação, somada ao comprimento —
 *   dá para perceber "está alto" pela silhueta, sem localizar a ponta;
 * - onde a barra é fina sobra espaço **acima** dela, e é ali que o número mora,
 *   sem precisar de tarja escura por trás nem roubar largura da escala;
 * - a diagonal cruza a régua num ponto só, o que ajuda a ler a posição.
 *
 * O **degradê é da escala, não do valor**: amarelo no começo, laranja no meio,
 * vermelho no fim, sempre nas mesmas posições. A barra não muda de cor conforme
 * sobe; ela revela a cor que já estava ali.
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

    Column(
        modifier
            .fillMaxWidth()
            .background(FtBlack)
            .padding(start = 4.dp, end = 6.dp, top = 2.dp, bottom = 1.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(BLOCK_HEIGHT.scaled(scale)),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val hMin = h * MIN_HEIGHT_RATIO
                val riseFrom = (RISE_FROM_RPM.toFloat() / safeMax).coerceIn(0.1f, 0.8f)
                val riseTo = (RISE_TO_RPM.toFloat() / safeMax).coerceIn(riseFrom + 0.1f, 1f)

                /**
                 * Altura da barra em x: reta e baixa até [RISE_FROM_RPM],
                 * subindo até [RISE_TO_RPM], e reta no topo depois disso.
                 */
                fun heightAt(x: Float): Float {
                    val f = x / w
                    if (f <= riseFrom) return hMin
                    if (f >= riseTo) return h
                    val t = (f - riseFrom) / (riseTo - riseFrom)
                    // Exponencial normalizada: (e^kt − 1)/(e^k − 1). Sai rente à
                    // reta e vai ficando cada vez mais íngreme, que é a silhueta
                    // do mostrador da FT.
                    //
                    // Ela é CÔNCAVA PARA CIMA, então cresce devagar no começo —
                    // o oposto do ease-out que estava aqui. [RISE_CURVE_K]
                    // controla quanto: baixo aproxima da reta, alto joga toda a
                    // subida para o fim da faixa.
                    val e = kotlin.math.exp(RISE_CURVE_K)
                    val curved = (kotlin.math.exp(RISE_CURVE_K * t) - 1f) / (e - 1f)
                    return hMin + (h - hMin) * curved
                }

                fun wedge(from: Float, to: Float) = Path().apply {
                    moveTo(from, h - heightAt(from))
                    // A curva é aproximada por segmentos: o suficiente para o
                    // olho não distinguir, e mais simples de acertar que
                    // encaixar bézier no ponto de tangência.
                    val steps = CURVE_STEPS
                    for (i in 1..steps) {
                        val x = from + (to - from) * i / steps
                        lineTo(x, h - heightAt(x))
                    }
                    lineTo(to, h)
                    lineTo(from, h)
                    close()
                }

                clipPath(wedge(0f, w)) {
                    // Trilho: o degradê inteiro apagado, para a escala de cor
                    // estar sempre visível. Recortá-lo no ponto atual deixaria
                    // a barra como uma tarja amarela sem informação nenhuma em
                    // marcha lenta.
                    drawRect(color = Zinc800, size = Size(w, h))
                    drawRect(
                        brush = Brush.horizontalGradient(colorStops = SCALE_STOPS, startX = 0f, endX = w),
                        size = Size(w, h),
                        alpha = 0.18f,
                    )

                    if (hasData) {
                        val filled = (rpm.toFloat() / safeMax).coerceIn(0f, 1f) * w
                        if (filled > 0f) {
                            // O degradê é medido sobre a largura TOTAL e
                            // recortado no ponto atual. Medido sobre a parte
                            // preenchida, a ponta ficaria vermelha em qualquer
                            // rotação — inclusive na lenta.
                            clipPath(wedge(0f, filled)) {
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colorStops = SCALE_STOPS,
                                        startX = 0f,
                                        endX = w,
                                    ),
                                    size = Size(w, h),
                                )
                            }
                        }
                        if (blinking) {
                            drawRect(color = FtRed.copy(alpha = 0.5f), size = Size(w, h))
                        }
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
            }

            // O número mora acima da parte fina da cunha — sem tarja por trás,
            // porque ali não há barra para atrapalhar a leitura.
            Row(
                Modifier.align(Alignment.TopStart),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = if (hasData) rpm.toString() else "----",
                    style = TextStyle(
                        fontFamily = MonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 38.sp * scale,
                        fontFeatureSettings = "tnum",
                    ),
                    color = if (blinking) FtRed else if (hasData) Zinc100 else Zinc500,
                    maxLines = 1,
                )
                Text(
                    "RPM",
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp * scale),
                    color = Zinc400,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp, bottom = 5.dp),
                )
            }
        }

        // Régua em milhares, alinhada à POSIÇÃO de cada milhar na barra.
        //
        // Antes era uma Row de células de peso igual com o texto centralizado, e
        // isso punha o rótulo no MEIO da célula: o "1" caía em 6,25% da largura
        // quando 1.000 rpm está em 12,5%. Toda a régua ficava meia divisão à
        // esquerda, e o preenchimento parecia adiantado — a 1.850 rpm a barra
        // já passava do "2".
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val steps = (safeMax / 1000).coerceAtLeast(1)
            val full = maxWidth
            for (i in 1..steps) {
                val center = full * i / steps
                Box(
                    Modifier
                        .width(TICK_WIDTH)
                        .offset(x = (center - TICK_WIDTH / 2).coerceIn(0.dp, full - TICK_WIDTH)),
                    contentAlignment = Alignment.Center,
                ) {
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

/** Altura da barra no trecho baixo, em fração da altura total. */
private const val MIN_HEIGHT_RATIO = 0.34f

/**
 * Onde a barra começa e termina de subir.
 *
 * Abaixo de [RISE_FROM_RPM] ela é uma faixa constante: quase todo o tempo de
 * rua é passado aí, e uma barra que já vem crescendo desde a marcha lenta gasta
 * altura onde não há nada a destacar. Acima de [RISE_TO_RPM] ela também é
 * constante, agora no topo — passado o giro útil, o que importa é *estar* lá,
 * não quanto ainda falta para o fim da régua.
 *
 * São ROTAÇÕES, não frações da largura: amarrar à largura faria a barra mudar
 * de forma sozinha se a régua crescesse além de 8.000 pelo aprendizado do pico.
 */
private const val RISE_FROM_RPM = 4_000
private const val RISE_TO_RPM = 7_000

/**
 * Quanto a subida é exponencial.
 *
 * É o `k` de `(e^kt − 1)/(e^k − 1)`. Perto de zero a curva vira uma reta;
 * quanto maior, mais ela se cola no chão no começo e mais íngreme termina.
 *
 * Começou em 2,0 e desceu para 1,3: com 2,0 a barra só tinha ganhado 6% da
 * altura aos 4.500 rpm, e a subida parecia começar bem depois do joelho. Em
 * 1,3 são 9% aos 4.500 e 20% aos 5.000 — o formato exponencial continua, mas a
 * saída do joelho é percebida onde ela acontece.
 */
private const val RISE_CURVE_K = 1.3f

/** Segmentos usados para aproximar a curva. */
private const val CURVE_STEPS = 48

/** Largura da caixa de cada número da régua, para centrá-lo na marca. */
private val TICK_WIDTH = 20.dp

/**
 * Altura do bloco da barra.
 *
 * Precisa comportar o número **acima** da parte fina da cunha, não só a barra:
 * são os 38sp do número mais a altura mínima da cunha, com folga.
 */
private val BLOCK_HEIGHT = 64.dp

private const val SHIFT_BLINK_HALF_PERIOD_MS = 83L
