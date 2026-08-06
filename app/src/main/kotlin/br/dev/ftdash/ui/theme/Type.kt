package br.dev.ftdash.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Todos os números do painel são monoespaçados com **algarismos de largura
 * fixa** (`tnum`): sem isso o mostrador de RPM "respira" a cada dígito que
 * muda, e a 17 Hz isso vira um tremor visível de canto de olho.
 *
 * Se a mono do sistema desta multimídia ficar ruim, é só jogar um
 * `roboto_mono_medium.ttf` em `res/font/` e trocar [MonoFamily] por
 * `FontFamily(Font(R.font.roboto_mono_medium))`. Nada mais muda.
 */
val MonoFamily: FontFamily = FontFamily.Monospace

private val tabular = "tnum"

/** Números grandes: RPM, velocidade. */
val NumberHuge = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 44.sp,
    fontFeatureSettings = tabular,
)

val NumberLarge = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 26.sp,
    fontFeatureSettings = tabular,
)

val NumberMedium = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    fontFeatureSettings = tabular,
)

val NumberSmall = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    fontFeatureSettings = tabular,
)

/** Rótulos: caixa alta, espaçados, discretos — como no FTManager. */
val LabelStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 10.sp,
    letterSpacing = 1.2.sp,
)

val CaptionStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
)

val FtTypography = Typography(
    bodyMedium = CaptionStyle,
    labelSmall = LabelStyle,
)
