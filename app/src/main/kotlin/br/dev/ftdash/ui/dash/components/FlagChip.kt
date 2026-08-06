package br.dev.ftdash.ui.dash.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.dev.ftdash.ui.theme.LabelStyle
import br.dev.ftdash.ui.theme.Red500
import br.dev.ftdash.ui.theme.Red900
import br.dev.ftdash.ui.theme.Zinc500
import br.dev.ftdash.ui.theme.Zinc800
import br.dev.ftdash.ui.theme.Zinc900

/** Flag booleana da ECU (CUTOFF). Apagada é discreta; acesa salta aos olhos. */
@Composable
fun FlagChip(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = LabelStyle,
        color = if (active) Red500 else Zinc500,
        modifier = modifier
            .background(
                if (active) Red900.copy(alpha = 0.45f) else Zinc900,
                RoundedCornerShape(3.dp),
            )
            .border(
                1.dp,
                if (active) Red500.copy(alpha = 0.6f) else Zinc800,
                RoundedCornerShape(3.dp),
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}
