package br.dev.ftdash.ui.dash.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.dev.ftdash.data.SourceKind
import br.dev.ftdash.data.SourceState
import br.dev.ftdash.data.SpeedOrigin
import br.dev.ftdash.ui.theme.Amber500
import br.dev.ftdash.ui.theme.Emerald500
import br.dev.ftdash.ui.theme.FtBlack
import br.dev.ftdash.ui.theme.NumberSmall
import br.dev.ftdash.ui.theme.Red500
import br.dev.ftdash.ui.theme.Zinc500
import br.dev.ftdash.ui.theme.Zinc600
import br.dev.ftdash.ui.theme.Zinc900
import androidx.compose.foundation.layout.Box

/**
 * Rodapé de diagnóstico: de onde vem o dado, a que taxa, e se o stream está
 * limpo.
 *
 * `crcFail` merece atenção: se subir junto com o uso, é ruído elétrico no cabo
 * USB — problema físico, não de software. O layout do frame também aparece
 * aqui, porque um tamanho fora dos conhecidos (107/111) é exatamente o que faz
 * o lambda sumir do painel.
 *
 * Toque longo troca a fonte entre replay e USB.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StatusBar(
    sourceKind: SourceKind,
    sourceState: SourceState,
    sourceDetail: String?,
    hz: Float,
    framesOk: Long,
    crcFail: Long,
    frameLen: Int,
    layoutKnown: Boolean,
    speedOrigin: SpeedOrigin,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val ledColor = when (sourceState) {
        SourceState.STREAMING -> Emerald500
        SourceState.CONNECTING -> Amber500
        SourceState.STALLED -> Amber500
        SourceState.ERROR -> Red500
        SourceState.IDLE -> Zinc600
    }

    Row(
        modifier
            .fillMaxWidth()
            .height(26.dp)
            // Preto como o resto do painel. A faixa cinza que havia aqui era o
            // único retângulo claro da tela e puxava o olho para o rodapé, que
            // é justamente o que menos importa dirigindo.
            .background(FtBlack)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = { },
                onLongClick = onLongPress,
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .background(ledColor, CircleShape),
        )
        Text(
            when (sourceKind) {
                SourceKind.REPLAY -> "REPLAY"
                SourceKind.USB -> "USB"
            },
            style = NumberSmall,
            color = Zinc500,
        )
        Text("%.1f Hz".format(hz), style = NumberSmall, color = Zinc500)
        Text("frames $framesOk", style = NumberSmall, color = Zinc500)
        Text(
            "crc $crcFail",
            style = NumberSmall,
            color = if (crcFail > 0) Amber500 else Zinc500,
        )
        Text(
            if (frameLen == 0) "layout --" else "layout ${frameLen}B",
            style = NumberSmall,
            color = if (layoutKnown) Zinc500 else Amber500,
        )
        Text(
            when (speedOrigin) {
                SpeedOrigin.GPS -> "gps ok"
                SpeedOrigin.SIMULATED -> "vel. simulada"
                SpeedOrigin.NONE -> "gps --"
            },
            style = NumberSmall,
            color = if (speedOrigin == SpeedOrigin.GPS) Zinc500 else Amber500,
        )

        Spacer(Modifier.weight(1f))

        if (sourceDetail != null) {
            Text(sourceDetail, style = NumberSmall, color = Zinc500)
        }
    }
}
