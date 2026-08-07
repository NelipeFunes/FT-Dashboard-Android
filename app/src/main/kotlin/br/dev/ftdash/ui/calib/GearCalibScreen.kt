package br.dev.ftdash.ui.calib

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.dev.ftdash.data.UsbBusReport
import br.dev.ftdash.data.settings.RpmScaleMode
import br.dev.ftdash.ui.theme.Red500
import br.dev.ftdash.ui.theme.Zinc600
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
import br.dev.ftdash.ui.theme.FtBlack
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
    onSetRpmMode: (RpmScaleMode) -> Unit,
    onRpmField: (String?, String?, String?) -> Unit,
    onApplyRpm: () -> Unit,
    onResetPeak: () -> Unit,
    onFuelField: (String?, String?, String?) -> Unit,
    onApplyFuel: () -> Unit,
    onFillTank: () -> Unit,
    onAddFuelField: (String) -> Unit,
    onAddFuel: (Double) -> Unit,
    onSetTankLevel: (Double) -> Unit,
    onResetTrip: () -> Unit,
    onSetInjectorUnit: (InjectorUnit) -> Unit,
    flowCcMin: Double?,
    onRescanUsb: () -> Unit,
    onUseUsb: () -> Unit,
    onUseReplay: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(FtBlack)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CONFIGURACAO", style = LabelStyle, color = Zinc400)
            Spacer(Modifier.weight(1f))
            ModeTab("APRENDER", state.mode == CalibMode.LEARN) { onSetMode(CalibMode.LEARN) }
            Spacer(Modifier.width(6.dp))
            ModeTab("RELACOES", state.mode == CalibMode.MANUAL) { onSetMode(CalibMode.MANUAL) }
            Spacer(Modifier.width(6.dp))
            ModeTab("RPM", state.mode == CalibMode.RPM) { onSetMode(CalibMode.RPM) }
            Spacer(Modifier.width(6.dp))
            ModeTab("TANQUE", state.mode == CalibMode.FUEL) { onSetMode(CalibMode.FUEL) }
            Spacer(Modifier.width(6.dp))
            ModeTab("USB", state.mode == CalibMode.USB) { onSetMode(CalibMode.USB) }
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
                    CalibMode.RPM -> RpmModePanel(state, onSetRpmMode, onRpmField, onApplyRpm, onResetPeak)
                    CalibMode.FUEL -> FuelModePanel(state, onFuelField, onApplyFuel, onFillTank, onAddFuelField, onAddFuel, onSetTankLevel, onResetTrip, onSetInjectorUnit, flowCcMin)
                    CalibMode.USB -> UsbModePanel(state, onRescanUsb, onUseUsb, onUseReplay)
                }
            }
            Box(Modifier.weight(0.45f)) {
                // Na aba USB o painel da direita vira o histórico. A lista de
                // relações não tem nada a ver com diagnóstico de barramento, e
                // o log é justamente o que precisa de espaço para ser lido —
                // ou fotografado — dentro do carro.
                if (state.mode == CalibMode.USB) {
                    UsbLogPanel(state)
                } else {
                    RatioList(state.profile, onRemoveGear, onEditRatio)
                }
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

        // O texto explica o que falta para o botão liberar. Dizer "estavel" com
        // o botão desabilitado, sem falar da velocidade, deixa o usuário
        // achando que o app travou.
        val cv = state.stabilityCv
        val kmh = state.speedKmh
        Column {
            Text("ESTABILIDADE", style = LabelStyle, color = Zinc400)
            Text(
                when {
                    kmh == null -> "sem velocidade — o GPS precisa de fixação"
                    kmh < RatioCapture.MIN_CAPTURE_KMH ->
                        "acelere acima de %.0f km/h".format(RatioCapture.MIN_CAPTURE_KMH)
                    cv == null -> "coletando amostras..."
                    cv <= RatioCapture.MAX_CV -> "estavel (%.1f%% de variacao)".format(cv * 100)
                    else -> "instavel (%.1f%%) — segure a velocidade".format(cv * 100)
                },
                style = NumberSmall,
                color = if (state.canCapture) Emerald500 else Amber500,
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
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        // 5 marchas + pneu + diferencial + botão têm que caber sem rolar: o
        // usuário mexe nisso parado no acostamento, não sentado à mesa.
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("PNEU  (ex.: 195 / 55 R15)", style = LabelStyle, color = Zinc400)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumField("", state.tireWidth, Modifier.weight(1f), placeholder = "largura") {
                onField(it, null, null, null, null, null)
            }
            NumField("", state.tireProfile, Modifier.weight(1f), placeholder = "perfil") {
                onField(null, it, null, null, null, null)
            }
            NumField("", state.tireRim, Modifier.weight(1f), placeholder = "aro") {
                onField(null, null, it, null, null, null)
            }
        }

        NumField(
            "DIFEREN.",
            state.finalDrive,
            Modifier.fillMaxWidth(),
            decimal = true,
            placeholder = "relacao final",
        ) { onField(null, null, null, it, null, null) }

        Text("RELACOES DA CAIXA", style = LabelStyle, color = Zinc400)
        state.gearboxRatios.forEachIndexed { i, value ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumField(
                    "${i + 1}a",
                    value,
                    Modifier.width(150.dp),
                    decimal = true,
                    placeholder = "—",
                ) { onField(null, null, null, null, i, it) }
                Spacer(Modifier.width(8.dp))
                val ratio = preview.firstOrNull { it.first == i + 1 }?.second
                Text(
                    if (ratio == null) {
                        ""
                    } else {
                        "%.1f rpm/km/h · 3.000 = %.0f km/h".format(ratio, TireCalc.kmhAt(3000, ratio))
                    },
                    style = NumberSmall,
                    color = Zinc500,
                    maxLines = 1,
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

/**
 * Escala da barra de RPM: automática ou manual.
 *
 * O automático copia o comportamento da FT — o teto é sempre o RPM mais alto já
 * registrado, e ele não recua sozinho. Enquanto o motor não passar de 4.300, a
 * barra vai até 4.300; no dia que passar, o novo valor vira o teto e fica. Por
 * isso existe o botão de zerar: é a única forma de fazer o teto descer, útil
 * depois de um pico esquisito ou de uma troca de motor.
 */
@Composable
private fun RpmModePanel(
    state: CalibUiState,
    onSetMode: (RpmScaleMode) -> Unit,
    onField: (String?, String?, String?) -> Unit,
    onApply: () -> Unit,
    onResetPeak: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Zinc900, RoundedCornerShape(4.dp))
            .border(1.dp, Zinc800, RoundedCornerShape(4.dp))
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ModeTab("AUTOMATICO", state.rpmScaleMode == RpmScaleMode.AUTO) {
                onSetMode(RpmScaleMode.AUTO)
            }
            ModeTab("MANUAL", state.rpmScaleMode == RpmScaleMode.MANUAL) {
                onSetMode(RpmScaleMode.MANUAL)
            }
        }

        if (state.rpmScaleMode == RpmScaleMode.AUTO) {
            Text(
                "O teto da barra é o RPM mais alto já registrado, como na FT. " +
                    "Ele sobe sozinho quando você passa do recorde e não desce.",
                style = NumberSmall,
                color = Zinc400,
            )
            Column {
                Text("PICO REGISTRADO", style = LabelStyle, color = Zinc400)
                Text(
                    if (state.learnedMaxRpm > 0) state.learnedMaxRpm.toString() else "ainda nenhum",
                    style = NumberLarge,
                    color = if (state.learnedMaxRpm > 0) Emerald500 else Zinc500,
                )
            }
            Button(
                onClick = onResetPeak,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Zinc950,
                    contentColor = Zinc400,
                ),
                shape = RoundedCornerShape(4.dp),
            ) {
                Text("ZERAR PICO", style = LabelStyle)
            }
        } else {
            Text(
                "Corte é onde a barra fica vermelha. Aviso é onde ela pisca. " +
                    "Escala é o fim da barra — deixe vazio para usar o corte.",
                style = NumberSmall,
                color = Zinc400,
            )
            NumField("CORTE", state.manualRedline, Modifier.fillMaxWidth(), placeholder = "rpm") {
                onField(it, null, null)
            }
            NumField("AVISO", state.manualShift, Modifier.fillMaxWidth(), placeholder = "rpm") {
                onField(null, it, null)
            }
            NumField("ESCALA", state.manualMaxRpm, Modifier.fillMaxWidth(), placeholder = "rpm") {
                onField(null, null, it)
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
                Text("SALVAR", style = LabelStyle)
            }
        }
    }
}

/**
 * Tanque e bicos.
 *
 * O nível do tanque não vem de boia nenhuma — a ECU não manda isso. Ele é
 * calculado: tanque cheio menos o que passou pelos bicos desde o último "enchi
 * o tanque". Para isso o app precisa de três números que só o dono do carro
 * sabe: capacidade do tanque, vazão nominal de cada bico e quantos são.
 *
 * A conta usa a vazão nominal, e a real varia com a pressão de combustível.
 * Erra para menos em pressão alta e para mais em pressão baixa — bom o
 * suficiente para saber que está na reserva, não para chegar no lacrado.
 */
@Composable
private fun FuelModePanel(
    state: CalibUiState,
    onField: (String?, String?, String?) -> Unit,
    onApply: () -> Unit,
    onFillTank: () -> Unit,
    onAddFuelField: (String) -> Unit,
    onAddFuel: (Double) -> Unit,
    onSetTankLevel: (Double) -> Unit,
    onResetTrip: () -> Unit,
    onSetInjectorUnit: (InjectorUnit) -> Unit,
    flowCcMin: Double?,
) {
    // Sem tanque, vazão e número de bicos, somar litros não significa nada —
    // os botões ficam apagados em vez de aceitar um toque que não faz efeito.
    val configured = state.fuelRemainingLiters != null

    Column(
        Modifier
            .fillMaxSize()
            .background(Zinc900, RoundedCornerShape(4.dp))
            .border(1.dp, Zinc800, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "O nível sai do consumo dos bicos, não de boia.",
            style = NumberSmall,
            color = Zinc400,
        )

        NumField("TANQUE", state.tankLiters, Modifier.fillMaxWidth(), placeholder = "litros") {
            onField(it, null, null)
        }
        // A FuelTech trabalha em lb/h; o cálculo trabalha em cc/min. Aceitar as
        // duas e mostrar a conversão ao vivo evita que o número mais sensível
        // da conta dependa de uma multiplicação feita de cabeça.
        Row(verticalAlignment = Alignment.CenterVertically) {
            NumField(
                "BICO",
                state.injectorFlow,
                Modifier.weight(1f),
                decimal = true,
                placeholder = "vazao de UM bico",
            ) { onField(null, it, null) }
            Spacer(Modifier.width(6.dp))
            for (unit in InjectorUnit.entries) {
                ModeTab(unit.label, state.injectorUnit == unit) { onSetInjectorUnit(unit) }
                Spacer(Modifier.width(4.dp))
            }
        }
        if (state.injectorUnit == InjectorUnit.LB_H && flowCcMin != null) {
            Text(
                "= %.0f cc/min  (gasolina, 0,72 g/cc)".format(flowCcMin),
                style = NumberSmall,
                color = Zinc500,
            )
        }
        NumField("QUANTOS", state.injectorCount, Modifier.fillMaxWidth(), placeholder = "n. de bicos") {
            onField(null, null, it)
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
            Text("SALVAR", style = LabelStyle)
        }

        Column(Modifier.padding(top = 6.dp)) {
            Text("NO TANQUE AGORA", style = LabelStyle, color = Zinc400)
            Text(
                state.fuelRemainingLiters?.let { "%.1f L".format(it) } ?: "falta configurar",
                style = NumberLarge,
                color = if (state.fuelRemainingLiters != null) Emerald500 else Zinc500,
            )
            Text(
                "consumidos %.2f L".format(state.fuelUsedLiters),
                style = NumberSmall,
                color = Zinc500,
            )
        }

        // Lado a lado para a aba caber inteira em 720p sem rolagem: com o
        // usuário parado no posto, precisar arrastar para achar o botão é a
        // diferença entre usar e desistir.
        //
        // Zerar o parcial mora aqui e não no painel: lá era um toque longo
        // invisível, fácil de acionar sem querer numa lombada e impossível de
        // descobrir de propósito.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = onFillTank,
                enabled = configured,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Zinc950,
                    contentColor = Zinc400,
                    disabledContainerColor = Zinc900,
                    disabledContentColor = Zinc600,
                ),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text("ENCHI", style = LabelStyle)
            }
            Button(
                onClick = onResetTrip,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Zinc950,
                    contentColor = Zinc400,
                ),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                modifier = Modifier.weight(1.6f),
            ) {
                Text("ZERAR MEDIA", style = LabelStyle)
            }
        }

        // Abastecimento parcial: nem toda parada de posto é tanque cheio.
        //
        // Os valores redondos cobrem quase toda parada real e resolvem em um
        // toque — digitar num teclado numérico dentro do carro, parado no
        // posto, é o caminho lento. O campo livre continua para o resto.
        Text("ABASTECI  (+)", style = LabelStyle, color = Zinc400)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (liters in QUICK_FUEL_LITERS) {
                QuickFuelButton(liters, configured) { onAddFuel(liters.toDouble()) }
            }
        }

        // Um campo, duas ações. "+ SOMAR" registra um abastecimento; "= TENHO"
        // corrige o nível para o que há de fato no tanque — olhando a boia, ou
        // depois de uma parada com o app desligado, em que o consumo não foi
        // contado. Somar 20 num tanque de nível desconhecido não é a mesma
        // coisa que dizer que ele tem 20, e os símbolos deixam isso à vista.
        Row(verticalAlignment = Alignment.CenterVertically) {
            NumField(
                "LITROS",
                state.addFuelLiters,
                Modifier.weight(1f),
                decimal = true,
                placeholder = "quantidade",
            ) { onAddFuelField(it) }
            Spacer(Modifier.width(6.dp))
            val typed = state.addFuelLiters.replace(',', '.').toDoubleOrNull()
            val usable = configured && typed != null && typed >= 0
            Button(
                onClick = {
                    typed?.let {
                        onAddFuel(it)
                        onAddFuelField("")
                    }
                },
                enabled = usable && (typed ?: 0.0) > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Emerald500,
                    contentColor = Zinc950,
                    disabledContainerColor = Zinc800,
                    disabledContentColor = Zinc500,
                ),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text("+ SOMAR", style = LabelStyle)
            }
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = {
                    typed?.let {
                        onSetTankLevel(it)
                        onAddFuelField("")
                    }
                },
                enabled = usable,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Zinc950,
                    contentColor = Emerald500,
                    disabledContainerColor = Zinc900,
                    disabledContentColor = Zinc600,
                ),
                border = BorderStroke(1.dp, if (usable) Emerald500 else Zinc800),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text("= TENHO", style = LabelStyle)
            }
        }
    }
}

/** Litros dos botões de atalho — as quantias que se pede no posto. */
private val QUICK_FUEL_LITERS = listOf(10, 15, 20, 25)

@Composable
private fun RowScope.QuickFuelButton(liters: Int, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Zinc950,
            contentColor = Emerald500,
            disabledContainerColor = Zinc900,
            disabledContentColor = Zinc600,
        ),
        border = BorderStroke(1.dp, if (enabled) Emerald500 else Zinc800),
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        modifier = Modifier.weight(1f),
    ) {
        Text("+$liters L", style = LabelStyle)
    }
}

/**
 * O que a multimídia enxerga no barramento USB.
 *
 * A maior incógnita do projeto é se a porta USB da central faz host de verdade
 * ou só monta pendrive. Sem esta tela, uma tentativa que falha no carro volta
 * como "não funcionou". Com ela, volta sabendo qual dos casos é: barramento
 * vazio (sem solução por software), FT com outro VID/PID, ou só falta de
 * permissão.
 */
@Composable
private fun UsbModePanel(
    state: CalibUiState,
    onRescan: () -> Unit,
    onUseUsb: () -> Unit,
    onUseReplay: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Zinc900, RoundedCornerShape(4.dp))
            .border(1.dp, Zinc800, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val report = state.usbReport

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ModeTab("RELER", false, onRescan)
            ModeTab("USAR USB", false, onUseUsb)
            ModeTab("USAR REPLAY", false, onUseReplay)
        }

        // Contadores ao vivo. Saíram do painel porque lá não ajudam a dirigir,
        // mas é aqui que se responde "por que parou" — e é o que interessa
        // anotar num teste de campo.
        Text("STREAM AGORA", style = LabelStyle, color = Zinc400)
        Text(
            "%s · %.1f Hz · %d B/s".format(state.sourceKind.name, state.hz, state.bytesPerSec),
            style = NumberSmall,
            color = Zinc100,
        )
        Text(
            "frames %d · crc %d · layout %s".format(
                state.framesOk,
                state.crcFail,
                if (state.frameLen == 0) "--" else "${state.frameLen}B",
            ),
            style = NumberSmall,
            color = if (state.crcFail > 0) Amber500 else Zinc500,
        )

        if (report == null) {
            Text("Lendo o barramento...", style = NumberSmall, color = Zinc500)
            return@Column
        }

        Text(report.summary, style = NumberMedium, color = statusColor(report))

        if (!report.hostFeatureDeclared) {
            Text(
                "O aparelho não declara USB host. Nesse caso não há software " +
                    "que resolva — a porta só serve para pendrive.",
                style = NumberSmall,
                color = Amber500,
            )
        }

        Text("VID:PID esperado da FT450: 1c5e:1002", style = NumberSmall, color = Zinc500)

        if (report.devices.isEmpty()) {
            Text(
                "Nenhum device no barramento. Com a FT plugada e ligada, isso " +
                    "aponta para a porta não fazer host — ou para um cabo OTG " +
                    "que só carrega.",
                style = NumberSmall,
                color = Zinc400,
            )
        }

        for (device in report.devices) {
            Column(Modifier.padding(top = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        device.idHex,
                        style = NumberSmall,
                        color = if (device.isFt450) Emerald500 else Zinc100,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(device.label, style = NumberSmall, color = Zinc400, maxLines = 1)
                    if (device.isFt450) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (device.hasPermission) "COM PERMISSAO" else "SEM PERMISSAO",
                            style = LabelStyle,
                            color = if (device.hasPermission) Emerald500 else Amber500,
                        )
                    }
                }
                for (iface in device.interfaces) {
                    Text(
                        "  iface ${iface.id} classe ${iface.interfaceClass} · " +
                            iface.endpoints.joinToString(" "),
                        style = NumberSmall,
                        color = Zinc600,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun statusColor(report: UsbBusReport) = when {
    report.ft450?.hasPermission == true -> Emerald500
    report.ft450 != null -> Amber500
    else -> Amber500
}

/**
 * O que aconteceu no USB, com hora, mais recente em cima.
 *
 * O painel mostra só o agora, e o defeito que estamos caçando é uma sequência:
 * conecta, transmite, cai. Quando alguém olha a tela, o momento que interessa
 * já passou — e dentro do carro não há logcat. Aqui a sequência fica parada,
 * pronta para ser fotografada com o celular no lugar do teste.
 */
@Composable
private fun UsbLogPanel(state: CalibUiState) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Zinc900, RoundedCornerShape(4.dp))
            .border(1.dp, Zinc800, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("HISTORICO DO USB", style = LabelStyle, color = Zinc400)

        if (state.usbLog.isEmpty()) {
            Text(
                "Nada registrado ainda. Toque em USAR USB.",
                style = NumberSmall,
                color = Zinc500,
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            for (entry in state.usbLog) {
                Row {
                    Text(entry.clock, style = NumberSmall, color = Zinc500)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        entry.text,
                        style = NumberSmall,
                        // Só as linhas de queda e de erro ganham cor. Se tudo
                        // fosse colorido, nada saltaria aos olhos.
                        color = when {
                            entry.text.startsWith("PAROU") -> Amber500
                            entry.text.startsWith("erro") ||
                                entry.text.contains("NEGADA") ||
                                entry.text.contains("falh") -> Red500
                            else -> Zinc100
                        },
                    )
                }
            }
        }

        state.usbLogPath?.let {
            Text("Arquivo: $it", style = LabelStyle, color = Zinc600, maxLines = 2)
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
                    label = "",
                    value = "%.1f".format(ratio.rpmPerKmh),
                    modifier = Modifier.width(90.dp),
                    decimal = true,
                ) { text ->
                    text.toDoubleOrNull()?.takeIf { it > 0 }?.let { onEdit(ratio.gear, it) }
                }
                Spacer(Modifier.width(8.dp))
                Text("rpm/km/h", style = NumberSmall, color = Zinc600, maxLines = 1)
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

/**
 * Campo numérico compacto.
 *
 * O `OutlinedTextField` do Material tem ~56 dp de altura mais o rótulo
 * flutuante: com ele, só duas marchas cabiam na tela e eram precisos quatro
 * arrastes para chegar no botão de salvar. Parado no acostamento isso é ruim.
 * Este aqui gasta ~34 dp e cabe a lista inteira de uma vez.
 */
@Composable
private fun NumField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
    placeholder: String = "",
    onChange: (String) -> Unit,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (label.isNotEmpty()) {
            Text(
                label,
                style = LabelStyle,
                color = Zinc400,
                maxLines = 1,
                modifier = Modifier.width(58.dp),
            )
        }
        Box(
            Modifier
                .weight(1f)
                .background(Zinc950, RoundedCornerShape(3.dp))
                .border(1.dp, Zinc800, RoundedCornerShape(3.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, style = NumberSmall, color = Zinc600)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = NumberSmall.copy(color = Zinc100),
                cursorBrush = SolidColor(Emerald500),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
