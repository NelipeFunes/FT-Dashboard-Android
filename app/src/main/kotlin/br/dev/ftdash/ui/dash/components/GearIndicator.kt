package br.dev.ftdash.ui.dash.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.dev.ftdash.gearing.GearState
import br.dev.ftdash.ui.theme.Amber500
import br.dev.ftdash.ui.theme.Emerald500
import br.dev.ftdash.ui.theme.LabelStyle
import br.dev.ftdash.ui.theme.MonoFamily
import br.dev.ftdash.ui.theme.NumberSmall
import br.dev.ftdash.ui.theme.Zinc100
import br.dev.ftdash.ui.theme.Zinc400
import br.dev.ftdash.ui.theme.Zinc500
import br.dev.ftdash.ui.theme.Zinc800
import br.dev.ftdash.ui.theme.Zinc900
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * A marcha estimada, em dígito grande.
 *
 * A entrada da calibração é um **toque longo aqui**, sem botão visível: numa
 * tela de carro, todo controle que aparece é um controle que alguém aperta sem
 * querer numa lombada.
 *
 * Estados e o que cada um significa para o motorista:
 * `–` sem informação (parado, sem GPS ou sem calibração) · `N` embreagem
 * pisada ou ponto morto · dígito âmbar durante uma troca · dígito verde com a
 * marcha confirmada.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GearIndicator(
    gear: GearState,
    calibrated: Boolean,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (text, color) = when {
        !calibrated -> "–" to Zinc500
        gear is GearState.Engaged -> gear.gear.toString() to Emerald500
        gear is GearState.Shifting -> gear.lastGear.toString() to Amber500
        gear is GearState.Neutral -> "N" to Zinc100
        else -> "–" to Zinc500
    }

    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier
            .background(Zinc900, RoundedCornerShape(4.dp))
            .border(1.dp, Zinc800, RoundedCornerShape(4.dp))
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = { },
                onLongClick = onLongPress,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (calibrated) "MARCHA" else "MARCHA · CALIBRAR",
            style = LabelStyle,
            color = Zinc400,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = TextStyle(
                    fontFamily = MonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 96.sp,
                    fontFeatureSettings = "tnum",
                ),
                color = color,
            )
        }
        if (!calibrated) {
            Text(
                "toque longo aqui",
                style = NumberSmall,
                color = Zinc500,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}
