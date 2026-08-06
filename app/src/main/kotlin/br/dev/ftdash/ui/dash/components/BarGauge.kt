package br.dev.ftdash.ui.dash.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import br.dev.ftdash.ui.theme.Amber500
import br.dev.ftdash.ui.theme.Emerald500
import br.dev.ftdash.ui.theme.LabelStyle
import br.dev.ftdash.ui.theme.NumberSmall
import br.dev.ftdash.ui.theme.Red500
import br.dev.ftdash.ui.theme.Zinc400
import br.dev.ftdash.ui.theme.Zinc500
import br.dev.ftdash.ui.theme.Zinc850

/**
 * Barra horizontal fina com faixas de alarme — para grandezas em que o que
 * importa é "está na faixa boa?", não o número exato: temperaturas, pressões,
 * tensão.
 *
 * [warnBelow]/[warnAbove] pintam de âmbar; [critBelow]/[critAbove], de vermelho.
 * Fora de qualquer alarme, verde.
 */
@Composable
fun BarGauge(
    label: String,
    value: Float?,
    min: Float,
    max: Float,
    unit: String,
    modifier: Modifier = Modifier,
    decimals: Int = 1,
    warnBelow: Float? = null,
    warnAbove: Float? = null,
    critBelow: Float? = null,
    critAbove: Float? = null,
) {
    val color = gaugeColor(value, warnBelow, warnAbove, critBelow, critAbove)

    Column(modifier.padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = LabelStyle, color = Zinc400)
            Spacer(Modifier.weight(1f))
            Text(
                if (value == null) "--" else "%.${decimals}f".format(value),
                style = NumberSmall,
                color = if (value == null) Zinc500 else color,
            )
            Text(" $unit", style = NumberSmall, color = Zinc500)
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .padding(top = 2.dp),
        ) {
            val corner = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            drawRoundRect(color = Zinc850, size = size, cornerRadius = corner)
            if (value != null && max > min) {
                val frac = ((value - min) / (max - min)).coerceIn(0f, 1f)
                drawRoundRect(
                    color = color,
                    size = Size((size.width * frac).coerceAtLeast(2f), size.height),
                    cornerRadius = corner,
                )
            }
        }
    }
}

private fun gaugeColor(
    value: Float?,
    warnBelow: Float?,
    warnAbove: Float?,
    critBelow: Float?,
    critAbove: Float?,
): Color {
    if (value == null) return Zinc500
    if (critBelow != null && value < critBelow) return Red500
    if (critAbove != null && value > critAbove) return Red500
    if (warnBelow != null && value < warnBelow) return Amber500
    if (warnAbove != null && value > warnAbove) return Amber500
    return Emerald500
}
