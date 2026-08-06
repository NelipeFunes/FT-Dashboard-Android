package br.dev.ftdash.ui.dash.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.dev.ftdash.data.SpeedOrigin
import br.dev.ftdash.ui.theme.Amber500
import br.dev.ftdash.ui.theme.Emerald500
import br.dev.ftdash.ui.theme.LabelStyle
import br.dev.ftdash.ui.theme.NumberHuge
import br.dev.ftdash.ui.theme.NumberSmall
import br.dev.ftdash.ui.theme.Zinc100
import br.dev.ftdash.ui.theme.Zinc400
import br.dev.ftdash.ui.theme.Zinc500
import br.dev.ftdash.ui.theme.Zinc800
import br.dev.ftdash.ui.theme.Zinc900

/**
 * Velocidade pelo GPS do Android — a ECU não recebe sensor de roda neste carro.
 *
 * A etiqueta ao lado do rótulo diz a origem do número, sem eufemismo: `GPS`
 * verde para fixação de verdade, `SIMULADO` âmbar quando o valor é sintetizado
 * na bancada, `SEM GPS` âmbar quando não há fixação. Um painel que chama de GPS
 * um número inventado é pior que um painel sem velocidade.
 */
@Composable
fun SpeedReadout(
    kmh: Float?,
    origin: SpeedOrigin,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(Zinc900, RoundedCornerShape(4.dp))
            .border(1.dp, Zinc800, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("VELOCIDADE", style = LabelStyle, color = Zinc400)
            Spacer(Modifier.weight(1f))
            Text(
                when (origin) {
                    SpeedOrigin.GPS -> "GPS"
                    SpeedOrigin.SIMULATED -> "SIMULADO"
                    SpeedOrigin.NONE -> "SEM GPS"
                },
                style = NumberSmall,
                color = if (origin == SpeedOrigin.GPS) Emerald500 else Amber500,
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                text = kmh?.let { "%.0f".format(it) } ?: "--",
                style = NumberHuge,
                color = if (kmh == null) Zinc500 else Zinc100,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
            Text(
                " km/h",
                style = NumberSmall,
                color = Zinc500,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}
