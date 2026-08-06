package br.dev.ftdash.ui.dash.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
 * Card de um valor: cabeçalho com rótulo e unidade, número grande em mono
 * embaixo.
 *
 * Três linhas, cada informação com a sua: rótulo e unidade em cima, número no
 * meio, contexto embaixo. Tudo que tentou dividir linha com o número deu
 * errado numa tela de multimídia — a unidade ao lado ou quebrava em coluna
 * (b / a / r) ou cortava o número (-0.85 virando -0.8), e o texto de contexto
 * disputando o cabeçalho engolia o rótulo. Assim o número tem a largura
 * inteira do card, que é o certo: é ele que se lê de relance.
 *
 * [value] é `String?` — `null` vira `--`. Um mostrador de carro nunca deve
 * inventar zero para dado que não chegou.
 *
 * [secondary] é o contexto do número (o λ alvo, o duty da injeção).
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
            Text(
                label,
                style = LabelStyle,
                color = Zinc400,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(unit, style = NumberSmall, color = Zinc500, maxLines = 1, softWrap = false)
            }
        }
        Text(
            value ?: "--",
            style = NumberLarge,
            color = if (value == null) Zinc500 else valueColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.fillMaxWidth(),
        )
        if (secondary != null) {
            Text(
                secondary,
                style = NumberSmall,
                color = Zinc500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
