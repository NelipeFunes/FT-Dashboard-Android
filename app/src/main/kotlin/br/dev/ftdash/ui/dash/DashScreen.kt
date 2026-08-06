package br.dev.ftdash.ui.dash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.dev.ftdash.data.SourceKind
import br.dev.ftdash.ui.dash.components.BarGauge
import br.dev.ftdash.ui.dash.components.FlagChip
import br.dev.ftdash.ui.dash.components.GearIndicator
import br.dev.ftdash.ui.dash.components.RpmBar
import br.dev.ftdash.ui.dash.components.SpeedReadout
import br.dev.ftdash.ui.dash.components.StatusBar
import br.dev.ftdash.ui.dash.components.ValueTile
import br.dev.ftdash.ui.theme.Amber500
import br.dev.ftdash.ui.theme.Emerald500
import br.dev.ftdash.ui.theme.Red500
import br.dev.ftdash.ui.theme.Zinc100
import br.dev.ftdash.ui.theme.Zinc950
import kotlin.math.abs

/**
 * O painel, em paisagem.
 *
 * Layout: barra de RPM no topo ocupando a largura toda; embaixo, três colunas —
 * marcha e velocidade à esquerda, os canais de acerto no centro, a saúde do
 * motor à direita; barra de status no rodapé.
 *
 * Cada composable filho recebe **primitivos**, não o [DashUiState] inteiro: é o
 * que permite ao Compose pular a recomposição dos mostradores cujo número não
 * mudou, com a telemetria chegando a 17 Hz.
 */
@Composable
fun DashScreen(
    state: DashUiState,
    onOpenCalibration: () -> Unit,
    onToggleSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Zinc950),
    ) {
        RpmBar(
            rpm = state.rpm,
            peakRpm = state.peakRpm,
            redlineRpm = state.redlineRpm,
            shiftRpm = state.shiftRpm,
            maxRpm = state.maxRpm,
            hasData = state.hasData,
        )

        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Coluna esquerda: o que o motorista olha de relance.
            Column(
                Modifier
                    .weight(0.26f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GearIndicator(
                    gear = state.gear,
                    calibrated = state.gearCalibrated,
                    onLongPress = onOpenCalibration,
                    modifier = Modifier.weight(1f),
                )
                SpeedReadout(
                    kmh = state.speedKmh,
                    origin = state.speedOrigin,
                )
            }

            // Coluna central: os canais de acerto.
            Column(
                Modifier
                    .weight(0.45f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ValueTile(
                        label = "MAP",
                        value = state.fmt { "%+.2f".format(state.mapBar) },
                        unit = "bar",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    ValueTile(
                        label = "TPS",
                        value = state.fmt { "%.0f".format(state.tpsPct) },
                        unit = "%",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    ValueTile(
                        label = "LAMBDA",
                        // Sonda em erro (bruto 9990) vira "--", nunca um número
                        // que pareça leitura boa.
                        value = state.lambda?.let { "%.2f".format(it) },
                        unit = "",
                        valueColor = lambdaColor(state.lambda, state.lambdaTarget),
                        secondary = if (state.lambdaTarget > 0f) {
                            "alvo %.2f".format(state.lambdaTarget)
                        } else {
                            // emparelha com "alvo 0.95" e cabe na largura do card
                            "sem alvo"
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ValueTile(
                        label = "PONTO",
                        value = state.fmt { "%+.1f".format(state.ignitionDeg) },
                        unit = "°",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    ValueTile(
                        label = "MALHA",
                        value = state.fmt { "%+.1f".format(state.closedLoopPct) },
                        unit = "%",
                        valueColor = if (abs(state.closedLoopPct) > 15f) Amber500 else Zinc100,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    ValueTile(
                        label = "INJ",
                        value = state.fmt { "%.2f".format(state.injTimeMs) },
                        unit = "ms",
                        // o tempo de injeção sozinho diz pouco sem o duty
                        secondary = state.fmt { "duty %.0f%%".format(state.injDutyPct) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }

            // Coluna direita: saúde do motor.
            Column(
                Modifier
                    .weight(0.29f)
                    .fillMaxHeight()
                    .padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                BarGauge(
                    label = "TEMP. MOTOR",
                    value = state.orNull(state.engineTempC),
                    min = 0f, max = 130f, unit = "C",
                    warnBelow = 70f, warnAbove = 100f, critAbove = 110f,
                )
                BarGauge(
                    label = "TEMP. AR",
                    value = state.orNull(state.airTempC),
                    min = 0f, max = 80f, unit = "C",
                    warnAbove = 60f,
                )
                BarGauge(
                    label = "PRESSAO OLEO",
                    value = state.orNull(state.oilPressureBar),
                    min = 0f, max = 8f, unit = "bar", decimals = 2,
                    critBelow = 0.8f, warnBelow = 1.5f,
                )
                BarGauge(
                    label = "PRESSAO COMB.",
                    value = state.orNull(state.fuelPressureBar),
                    min = 0f, max = 6f, unit = "bar", decimals = 2,
                    warnBelow = 2.5f,
                )
                BarGauge(
                    label = "BATERIA",
                    value = state.orNull(state.vbat),
                    min = 8f, max = 16f, unit = "V", decimals = 2,
                    critBelow = 11.5f, warnBelow = 12.5f, warnAbove = 15f,
                )
                BarGauge(
                    label = "ATUADOR LENTA",
                    value = state.orNull(state.idleActuatorPct),
                    min = 0f, max = 100f, unit = "%", decimals = 0,
                )

                Spacer(Modifier.weight(1f))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FlagChip("CUTOFF", state.cutoff)
                }
            }
        }

        StatusBar(
            sourceKind = state.sourceKind,
            sourceState = state.sourceState,
            sourceDetail = state.sourceDetail,
            hz = state.hz,
            framesOk = state.framesOk,
            crcFail = state.crcFail,
            frameLen = state.frameLen,
            layoutKnown = state.layoutKnown,
            speedOrigin = state.speedOrigin,
            onLongPress = onToggleSource,
        )
    }
}

/** Antes do primeiro frame, tudo é "--". */
private inline fun DashUiState.fmt(format: () -> String): String? =
    if (hasData) format() else null

private fun DashUiState.orNull(v: Float): Float? = if (hasData) v else null

private fun lambdaColor(lambda: Float?, target: Float) = when {
    lambda == null || target <= 0f -> Zinc100
    abs(lambda - target) <= 0.03f -> Emerald500
    abs(lambda - target) <= 0.08f -> Amber500
    else -> Red500
}

/** Alterna entre as duas fontes registradas. */
fun nextSource(current: SourceKind): SourceKind =
    if (current == SourceKind.REPLAY) SourceKind.USB else SourceKind.REPLAY
