package br.dev.ftdash.ui.dash.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.dev.ftdash.gearing.GearState
import br.dev.ftdash.ui.theme.Amber500
import br.dev.ftdash.ui.theme.Emerald500
import br.dev.ftdash.ui.theme.LocalDashScale
import br.dev.ftdash.ui.theme.MonoFamily
import br.dev.ftdash.ui.theme.times
import br.dev.ftdash.ui.theme.Zinc100
import br.dev.ftdash.ui.theme.Zinc400
import br.dev.ftdash.ui.theme.Zinc500
import br.dev.ftdash.ui.theme.Zinc800
import br.dev.ftdash.ui.theme.Zinc900
import androidx.compose.foundation.ExperimentalFoundationApi

/** Rótulo pequeno, já escalado para a tela em uso. */
@Composable
private fun labelSmall(): TextStyle {
    val scale = LocalDashScale.current
    return TextStyle(
        fontFamily = MonoFamily,
        fontSize = 11.sp * scale,
        letterSpacing = 0.5.sp,
    )
}

/**
 * Valor de canal, já escalado.
 *
 * Os rótulos continuam em 11sp de propósito: o que faz a tira ser lida de
 * relance não é o tamanho absoluto, e sim a diferença entre rótulo e número.
 * Crescer os dois junto manteria a hierarquia igual e só gastaria espaço.
 */
@Composable
private fun valueSmall(): TextStyle {
    val scale = LocalDashScale.current
    return TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp * scale,
        fontFeatureSettings = "tnum",
    )
}

/**
 * Odômetro e consumo.
 *
 * A distância é integrada da velocidade do GPS — a ECU deste carro não recebe
 * sensor de roda.
 *
 * Mostra **uma** quilometragem, não total e parcial. O parcial continua sendo
 * contado por dentro, porque é ele que dá sentido à média (distância e
 * combustível do mesmo trecho), mas exibir os dois gastava duas linhas do
 * painel para um número que ninguém consulta dirigindo.
 *
 * Toque abre a configuração.
 */
@Composable
fun OdometerPanel(
    totalKm: Double,
    averageKmPerLiter: Double?,
    instantKmPerLiter: Double?,
    onOpenConfig: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clickable(onClick = onOpenConfig)
            .padding(horizontal = 8.dp),
    ) {
        OdoRow("Odometro", "%.1f".format(totalKm))

        Spacer(Modifier.height(6.dp))
        Text(
            "Media km/L",
            style = labelSmall(),
            color = Zinc400,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        // A média do tanque só aparece depois de meio litro consumido, e a
        // instantânea some com o carro parado. Nos dois casos "--" é a resposta
        // honesta: km/L parado, ou com dois decilitros de amostra, é ruído.
        OdoRow(
            "Tanque",
            averageKmPerLiter?.let { "%.1f".format(it) } ?: "--",
            if (averageKmPerLiter == null) Zinc500 else Emerald500,
        )
        OdoRow(
            "Agora",
            instantKmPerLiter?.let {
                if (it >= 99.0) ">99" else "%.1f".format(it)
            } ?: "--",
            if (instantKmPerLiter == null) Zinc500 else Zinc100,
        )
    }
}

/** Linha rótulo-à-esquerda / valor-à-direita do bloco do odômetro. */
@Composable
private fun OdoRow(label: String, value: String, valueColor: Color = Zinc100) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(label, style = labelSmall(), color = Zinc500)
        Spacer(Modifier.weight(1f))
        Text(value, style = valueSmall(), color = valueColor, maxLines = 1)
    }
}

/**
 * Um número grande com rótulo em cima e unidade embaixo — o formato da marcha
 * e da velocidade na FT.
 */
@Composable
fun FtBigValue(
    label: String,
    value: String,
    unit: String?,
    valueColor: Color,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    @OptIn(ExperimentalFoundationApi::class)
    val clickable = if (onLongPress != null) {
        Modifier.combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = { },
            onLongClick = onLongPress,
        )
    } else {
        Modifier
    }

    Column(
        modifier.then(clickable),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = labelSmall(), color = Zinc400)
        Text(
            value,
            style = TextStyle(
                fontFamily = MonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 92.sp * LocalDashScale.current,
                fontFeatureSettings = "tnum",
            ),
            color = valueColor,
            maxLines = 1,
        )
        if (unit != null) {
            Text(unit, style = labelSmall(), color = Zinc400)
        }
    }
}

/** Marcha estimada, no formato grande da FT. */
@Composable
fun FtGear(
    gear: GearState,
    calibrated: Boolean,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (text, color) = when {
        !calibrated -> "?" to Zinc500
        gear is GearState.Engaged -> gear.gear.toString() to Zinc100
        gear is GearState.Shifting -> gear.lastGear.toString() to Amber500
        gear is GearState.Neutral -> "N" to Zinc100
        else -> "-" to Zinc500
    }
    FtBigValue(
        label = if (calibrated) "Marcha" else "Marcha (calibrar)",
        value = text,
        unit = null,
        valueColor = color,
        modifier = modifier,
        onLongPress = onLongPress,
    )
}

/**
 * Valor pequeno com rótulo em cima, do jeito da FT: rótulo discreto, número
 * logo abaixo, sem moldura.
 */
@Composable
fun FtChannel(
    label: String,
    value: String?,
    valueColor: Color = Zinc100,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = labelSmall(), color = Zinc400, maxLines = 1)
        Text(
            value ?: "--",
            style = valueSmall(),
            color = if (value == null) Zinc500 else valueColor,
            maxLines = 1,
        )
    }
}

/**
 * A barra do tanque, com escala 0/meio/cheio.
 *
 * O nível não vem de boia nenhuma: é o tanque configurado menos o combustível
 * que passou pelos bicos desde o último abastecimento.
 *
 * **Toque simples abre a configuração.** Antes a palavra "configurar" aparecia
 * aqui como rótulo e não respondia a toque nenhum — convite claro para uma ação
 * que não existia —, enquanto um toque longo escondido enchia o tanque. Um
 * gesto invisível que zera o medidor de combustível, num painel usado com o
 * carro andando, é o tipo de coisa que se aciona sem querer numa lombada.
 * Encher e abastecer agora são botões escritos, na tela de configuração.
 */
@Composable
fun FtTankGauge(
    fraction: Float?,
    liters: Double?,
    tankLiters: Double,
    onOpenConfig: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = when {
        fraction == null -> Zinc500
        fraction < 0.10f -> FtRed
        fraction < 0.25f -> Amber500
        else -> Emerald500
    }

    Column(modifier.clickable(onClick = onOpenConfig)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Tanque", style = labelSmall(), color = Zinc400)
            Spacer(Modifier.weight(1f))
            // Sublinhado quando falta configurar: é o que diferencia um rótulo
            // de um convite a tocar. Sem isso a palavra parecia botão sem ser.
            Text(
                if (liters == null) "CONFIGURAR ›" else "%.1f L".format(liters),
                // Os litros são um valor como os outros da tira, e ficavam do
                // tamanho de rótulo — o único número pequeno no meio dos
                // grandes puxava o olho pelo motivo errado.
                style = if (liters == null) labelSmall() else valueSmall(),
                color = if (liters == null) Amber500 else color,
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(11.dp)
                .padding(top = 2.dp),
        ) {
            drawRect(color = Zinc900, size = size)
            if (fraction != null) {
                drawRect(color = color, size = Size(size.width * fraction, size.height))
            }
            // marcas de 1/4 sobre a barra, como a régua 0-50-100 da FT
            for (i in 1..3) {
                drawRect(
                    color = Color.Black.copy(alpha = 0.55f),
                    topLeft = Offset(size.width * i / 4f, 0f),
                    size = Size(1.5f, size.height),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text("0", style = TextStyle(fontFamily = MonoFamily, fontSize = 9.sp), color = Zinc500)
            Spacer(Modifier.weight(1f))
            Text(
                if (tankLiters > 0) "%.0f".format(tankLiters / 2) else "",
                style = TextStyle(fontFamily = MonoFamily, fontSize = 9.sp),
                color = Zinc500,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (tankLiters > 0) "%.0f".format(tankLiters) else "",
                style = TextStyle(fontFamily = MonoFamily, fontSize = 9.sp),
                color = Zinc500,
            )
        }
    }
}

/** Caixa com moldura e rótulo, para status curtos (CUTOFF, MOTOR). */
@Composable
fun FtStatusBox(
    label: String,
    active: Boolean,
    activeColor: Color = FtRed,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .background(if (active) activeColor.copy(alpha = 0.25f) else Zinc900, RoundedCornerShape(2.dp))
            .border(1.dp, if (active) activeColor else Zinc800, RoundedCornerShape(2.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(label, style = labelSmall(), color = if (active) activeColor else Zinc500)
    }
}
