package br.dev.ftdash.ui.calib

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.dev.ftdash.gearing.GearProfile
import br.dev.ftdash.gearing.RatioCapture
import br.dev.ftdash.gearing.RatioSource
import br.dev.ftdash.gearing.TireCalc
import br.dev.ftdash.ui.theme.Amber500
import br.dev.ftdash.ui.theme.Emerald500
import br.dev.ftdash.ui.theme.LabelStyle
import br.dev.ftdash.ui.theme.NumberLarge
import br.dev.ftdash.ui.theme.NumberMedium
import br.dev.ftdash.ui.theme.NumberSmall
import br.dev.ftdash.ui.theme.Zinc100
import br.dev.ftdash.ui.theme.Zinc400
import br.dev.ftdash.ui.theme.Zinc500
import br.dev.ftdash.ui.theme.Zinc800
import br.dev.ftdash.ui.theme.Zinc900
import br.dev.ftdash.ui.theme.Zinc950

/**
 * Tela única de calibração, com os dois modos em abas e a lista de razões
 * embaixo — a mesma lista para os dois, editável na mão.
 */
@Composable
fun GearCalibScreen(
    state: CalibUiState,
    manualPreview: List<Pair<Int, Double>>,
    onSetMode: (CalibMode) -> Unit,
    onSelectGear: (Int) -> Unit,
    onCapture: () -> Unit,
    onRemoveGear: (Int) -> Unit,
    onEditRatio: (Int, Double) -> Unit,
    onManualField: (String?, String?, String?, String?, Int?, String?) -> Unit,
    onApplyManual: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Zinc950)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CALIBRACAO DE MARCHA", style = LabelStyle, color = Zinc400)
            Spacer(Modifier.weight(1f))
            ModeTab("APRENDER NO CARRO", state.mode == CalibMode.LEARN) { onSetMode(CalibMode.LEARN) }
            Spacer(Modifier.width(6.dp))
            ModeTab("RELACOES MANUAIS", state.mode == CalibMode.MANUAL) { onSetMode(CalibMode.MANUAL) }
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = onClose) { Text("FECHAR", style = LabelStyle, color = Zinc400) }
        }

        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.weight(0.55f)) {
                when (state.mode) {
                    CalibMode.LEARN -> LearnModePanel(state, onSelectGear, onCapture)
                    CalibMode.MANUAL -> ManualModePanel(state, manualPreview, onManualField, onApplyManual)
                }
            }
            Box(Modifier.weight(0.45f)) {
                RatioList(state.profile, onRemoveGear, onEditRatio)
            }
        }

        if (state.message != null) {
            Text(state.message, style = NumberSmall, color = Amber500)
        }
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = LabelStyle,
        color = if (selected) Zinc100 else Zinc500,
        modifier = Modifier
            .background(
                if (selected) Emerald500.copy(alpha = 0.18f) else Zinc900,
                RoundedCornerShape(3.dp),
            )
            .border(
                1.dp,
                if (selected) Emerald500 else Zinc800,
                RoundedCornerShape(3.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/**
 * Aprendizado guiado: escolha a marcha, ande em velocidade constante, capture.
 *
 * O botão só habilita quando a razão está estável de verdade — não adianta
 * capturar no meio de uma aceleração, o número não corresponde a marcha
 * nenhuma.
 */
@Composable
private fun LearnModePanel(
    state: CalibUiState,
    onSelectGear: (Int) -> Unit,
    onCapture: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Zinc900, RoundedCornerShape(4.dp))
            .border(1.dp, Zinc800, RoundedCornerShape(4.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Engate a marcha, mantenha velocidade constante acima de " +
                "${RatioCapture.MIN_CAPTURE_KMH.toInt()} km/h e toque em capturar.",
            style = NumberSmall,
            color = Zinc400,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (gear in 1..6) {
                GearChip(gear, state.selectedGear == gear, state.profile.ratioFor(gear) != null) {
                    onSelectGear(gear)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LiveValue("RPM", state.rpm.toString())
            LiveValue("KM/H", state.speedKmh?.let { "%.0f".format(it) } ?: "--")
            LiveValue("RAZAO", state.instantRatio?.let { "%.1f".format(it) } ?: "--")
        }

        val cv = state.stabilityCv
        Column {
            Text("ESTABILIDADE", style = LabelStyle, color = Zinc400)
            Text(
                when {
                    cv == null -> "sem amostras"
                    cv <= RatioCapture.MAX_CV -> "estavel (%.1f%% de variacao)".format(cv * 100)
                    else -> "instavel (%.1f%% de variacao)".format(cv * 100)
                },
                style = NumberSmall,
                color = when {
                    cv == null -> Zinc500
                    cv <= RatioCapture.MAX_CV -> Emerald500
                    else -> Amber500
                },
            )
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onCapture,
            enabled = state.canCapture,
            colors = ButtonDefaults.buttonColors(
                containerColor = Emerald500,
                contentColor = Zinc950,
                disabledContainerColor = Zinc800,
                disabledContentColor = Zinc500,
            ),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("CAPTURAR ${state.selectedGear}a MARCHA", style = LabelStyle)
        }
    }
}

/**
 * Entrada manual das relações. A prévia à direita de cada campo é o que pega
 * erro de digitação: se a 3ª disser que 3.000 rpm dão 200 km/h, algo está
 * errado antes de o carro sair da garagem.
 */
@Composable
private fun ManualModePanel(
    state: CalibUiState,
    preview: List<Pair<Int, Double>>,
    onField: (String?, String?, String?, String?, Int?, String?) -> Unit,
    onApply: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Zinc900, RoundedCornerShape(4.dp))
            .border(1.dp, Zinc800, RoundedCornerShape(4.dp))
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("PNEU", style = LabelStyle, color = Zinc400)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumField("Largura", state.tireWidth, Modifier.weight(1f)) {
                onField(it, null, null, null, null, null)
            }
            NumField("Perfil", state.tireProfile, Modifier.weight(1f)) {
                onField(null, it, null, null, null, null)
            }
            NumField("Aro", state.tireRim, Modifier.weight(1f)) {
                onField(null, null, it, null, null, null)
            }
        }

        Text("DIFERENCIAL", style = LabelStyle, color = Zinc400)
        NumField("Relacao final", state.finalDrive, Modifier.fillMaxWidth(), decimal = true) {
            onField(null, null, null, it, null, null)
        }

        Text("RELACOES DA CAIXA", style = LabelStyle, color = Zinc400)
        state.gearboxRatios.forEachIndexed { i, value ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumField("${i + 1}a", value, Modifier.width(120.dp), decimal = true) {
                    onField(null, null, null, null, i, it)
                }
                Spacer(Modifier.width(10.dp))
                val ratio = preview.firstOrNull { it.first == i + 1 }?.second
                Text(
                    if (ratio == null) {
                        "--"
                    } else {
                        "%.1f rpm/km/h · 3.000 rpm = %.0f km/h".format(
                            ratio,
                            TireCalc.kmhAt(3000, ratio),
                        )
                    },
                    style = NumberSmall,
                    color = Zinc500,
                )
            }
        }

        Button(
            onClick = onApply,
            colors = ButtonDefaults.buttonColors(
                containerColor = Emerald500,
                contentColor = Zinc950,
            ),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("CALCULAR E SALVAR", style = LabelStyle)
        }
    }
}

/** Lista final — a mesma para os dois modos, com a origem de cada razão à vista. */
@Composable
private fun RatioList(
    profile: GearProfile,
    onRemove: (Int) -> Unit,
    onEdit: (Int, Double) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Zinc900, RoundedCornerShape(4.dp))
            .border(1.dp, Zinc800, RoundedCornerShape(4.dp))
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("RAZOES GRAVADAS", style = LabelStyle, color = Zinc400)

        if (profile.ratios.isEmpty()) {
            Text("Nenhuma marcha calibrada ainda.", style = NumberSmall, color = Zinc500)
        }

        for (ratio in profile.ratios) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${ratio.gear}a", style = NumberMedium, color = Zinc100, modifier = Modifier.width(36.dp))
                NumField(
                    label = "rpm/km/h",
                    value = "%.1f".format(ratio.rpmPerKmh),
                    modifier = Modifier.width(120.dp),
                    decimal = true,
                ) { text ->
                    text.toDoubleOrNull()?.takeIf { it > 0 }?.let { onEdit(ratio.gear, it) }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    when (ratio.source) {
                        RatioSource.LEARNED -> "APRENDIDA"
                        RatioSource.MANUAL -> "MANUAL"
                        RatioSource.EDITED -> "EDITADA"
                    },
                    style = LabelStyle,
                    color = if (ratio.source == RatioSource.LEARNED) Emerald500 else Zinc500,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onRemove(ratio.gear) }) {
                    Text("X", style = LabelStyle, color = Zinc500)
                }
            }
        }
    }
}

@Composable
private fun GearChip(gear: Int, selected: Boolean, calibrated: Boolean, onClick: () -> Unit) {
    Text(
        "$gear",
        style = NumberMedium,
        color = when {
            selected -> Zinc950
            calibrated -> Emerald500
            else -> Zinc500
        },
        modifier = Modifier
            .background(if (selected) Emerald500 else Zinc950, RoundedCornerShape(3.dp))
            .border(1.dp, if (calibrated) Emerald500 else Zinc800, RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun LiveValue(label: String, value: String) {
    Column {
        Text(label, style = LabelStyle, color = Zinc400)
        Text(value, style = NumberLarge, color = Zinc100)
    }
}

@Composable
private fun NumField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = LabelStyle) },
        singleLine = true,
        textStyle = NumberSmall,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = modifier,
    )
}
