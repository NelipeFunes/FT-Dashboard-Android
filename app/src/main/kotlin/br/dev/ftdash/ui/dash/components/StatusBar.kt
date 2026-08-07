package br.dev.ftdash.ui.dash.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.dev.ftdash.data.SourceKind
import br.dev.ftdash.data.SourceState
import br.dev.ftdash.data.SpeedOrigin
import br.dev.ftdash.ui.theme.Amber500
import br.dev.ftdash.ui.theme.Emerald500
import br.dev.ftdash.ui.theme.FtBlack
import br.dev.ftdash.ui.theme.LabelStyle
import br.dev.ftdash.ui.theme.NumberSmall
import br.dev.ftdash.ui.theme.Red500
import br.dev.ftdash.ui.theme.Zinc300
import br.dev.ftdash.ui.theme.Zinc700
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
    bytesPerSec: Int,
    onLongPress: () -> Unit,
    onOpenConfig: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }

    /**
     * Há algo a investigar?
     *
     * Stream limpo, sem CRC quebrado e com layout reconhecido é o caso normal —
     * e no caso normal os contadores não dizem nada que ajude a dirigir.
     */
    val showDiagnostics = sourceState != SourceState.STREAMING ||
        crcFail > 0 ||
        !layoutKnown

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
        // Contadores só quando há o que investigar.
        //
        // Dirigindo, "frames 387" e "layout 107B" não ajudam ninguém — são
        // instrumento de diagnóstico ocupando a tela o tempo todo. Mas jogá-los
        // fora tiraria justamente o que responde "por que parou" no carro. Então
        // eles aparecem quando algo está diferente do esperado e somem quando
        // não está. Ficam sempre disponíveis na aba USB da configuração.
        if (showDiagnostics) {
            Text("%.1f Hz".format(hz), style = NumberSmall, color = Zinc500)
            // Bytes/s ao lado dos frames: é o que separa "cabo caiu" de "cabo
            // vivo entregando lixo". Com bytes correndo e CRC subindo, o
            // problema é ruído elétrico, não conexão.
            Text("${bytesPerSec}B/s", style = NumberSmall, color = Zinc500)
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
        }
        Text(
            when (speedOrigin) {
                SpeedOrigin.GPS -> "gps ok"
                SpeedOrigin.SIMULATED -> "vel. simulada"
                SpeedOrigin.NONE -> "gps --"
            },
            style = NumberSmall,
            color = if (speedOrigin == SpeedOrigin.GPS) Zinc500 else Amber500,
        )

        // O detalhe é o único item elástico da barra: é ele que encolhe quando
        // falta espaço. Sem isso, numa tela de 1024 os contadores espremiam o
        // botão CONFIG até ele quebrar em duas linhas.
        Text(
            // O detalhe também só aparece quando há o que investigar: com o
            // stream limpo ele é informativo ("3000 frames") e não muda nada
            // para quem está dirigindo.
            if (showDiagnostics) sourceDetail.orEmpty() else "",
            style = NumberSmall,
            color = Zinc500,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )

        // Botão de verdade, não toque longo escondido. Toque longo é bom para
        // atalho de quem já sabe; para achar a configuração pela primeira vez,
        // parado no carro, precisa estar escrito na tela.
        Spacer(Modifier.width(8.dp))
        Text(
            "CONFIG",
            style = LabelStyle,
            color = Zinc300,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .background(Zinc900, RoundedCornerShape(3.dp))
                .border(1.dp, Zinc700, RoundedCornerShape(3.dp))
                .clickable(onClick = onOpenConfig)
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}
