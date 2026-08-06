package br.dev.ftdash.ui.dash.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import br.dev.ftdash.ui.theme.LabelStyle
import br.dev.ftdash.ui.theme.NumberLarge
import br.dev.ftdash.ui.theme.NumberSmall
import br.dev.ftdash.ui.theme.Zinc100
import br.dev.ftdash.ui.theme.Zinc400
import br.dev.ftdash.ui.theme.Zinc500
import br.dev.ftdash.ui.theme.Zinc800
import br.dev.ftdash.ui.theme.Zinc900

/**
 * Card de um valor: rótulo em caixa alta, número grande em mono, unidade
 * discreta. É o tijolo da grade central do painel.
 *
 * [value] é `String?` — `null` vira `--`. Um mostrador de carro nunca deve
 * inventar zero para dado que não chegou.
 */
@Composable
fun ValueTile(
    label: String,
    value: String?,
    unit: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Zinc100,
    secondary: String? = null,
) {
    Column(
        modifier
            .background(Zinc900, RoundedCornerShape(4.dp))
            .border(1.dp, Zinc800, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = LabelStyle, color = Zinc400)
            if (secondary != null) {
                Text(
                    "  $secondary",
                    style = NumberSmall,
                    color = Zinc500,
                )
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value ?: "--",
                style = NumberLarge,
                color = if (value == null) Zinc500 else valueColor,
            )
            if (unit.isNotEmpty()) {
                Text(
                    " $unit",
                    style = NumberSmall,
                    color = Zinc500,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
    }
}
