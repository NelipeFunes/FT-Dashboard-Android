package br.dev.ftdash.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Fator de escala do painel, derivado da altura útil da tela.
 *
 * O layout nasceu numa central de 1024×600 e, numa de 1280×720, ficava com os
 * números do mesmo tamanho boiando em faixas mais altas — muito espaço morto e
 * dígitos pequenos demais para se ler de relance dirigindo. Escalar por
 * `dp` fixo não resolve: `dp` já é independente de densidade, então uma tela
 * maior em polegadas continua mostrando o mesmo tamanho aparente.
 *
 * O que interessa aqui é **quanto espaço há**, não quantos pixels. Por isso a
 * escala sai da altura em dp, com a referência sendo a tela de origem.
 *
 * É `compositionLocalOf` e não parâmetro porque atravessaria uma dúzia de
 * composables sem ninguém fazer nada com ele no meio do caminho.
 */
val LocalDashScale = compositionLocalOf { 1f }

/**
 * Altura **em dp** da tela para a qual os tamanhos originais foram desenhados.
 *
 * A central de referência tem 1024×600 **pixels** a 240 dpi, o que dá 400 dp de
 * altura — não 600. Confundir os dois aqui é fácil e passa despercebido: com
 * 600 como referência, a escala dava 0,9 numa tela maior e travava no piso,
 * deixando tudo do mesmo tamanho de antes com o espaço sobrando em volta.
 */
const val DASH_REFERENCE_HEIGHT_DP = 400f

/**
 * Limites da escala.
 *
 * Teto porque acima de ~1,5× os números viram outdoor e o painel perde a
 * densidade de informação que é a graça dele. Piso em 1 porque encolher em
 * tela pequena é pior que apertar: número ilegível não serve para nada.
 */
const val DASH_SCALE_MIN = 1.0f
const val DASH_SCALE_MAX = 1.5f

fun dashScaleFor(availableHeightDp: Float): Float =
    (availableHeightDp / DASH_REFERENCE_HEIGHT_DP).coerceIn(DASH_SCALE_MIN, DASH_SCALE_MAX)

operator fun TextUnit.times(scale: Float): TextUnit = (value * scale).sp

fun Dp.scaled(scale: Float): Dp = (value * scale).dp
