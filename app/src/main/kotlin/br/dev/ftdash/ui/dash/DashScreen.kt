package br.dev.ftdash.ui.dash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.dev.ftdash.data.SourceKind
import br.dev.ftdash.ui.dash.components.FtBigValue
import br.dev.ftdash.ui.dash.components.FtChannel
import br.dev.ftdash.ui.dash.components.FtGear
import br.dev.ftdash.ui.dash.components.FtRpmBar
import br.dev.ftdash.ui.dash.components.FtStatusBox
import br.dev.ftdash.ui.dash.components.FtTankGauge
import br.dev.ftdash.ui.dash.components.StatusBar
import br.dev.ftdash.ui.theme.Amber500
import br.dev.ftdash.ui.theme.Emerald500
import br.dev.ftdash.ui.theme.Red500
import br.dev.ftdash.ui.theme.LocalDashScale
import br.dev.ftdash.ui.theme.dashScaleFor
import br.dev.ftdash.ui.theme.Zinc100
import br.dev.ftdash.ui.theme.Zinc800
import br.dev.ftdash.ui.theme.FtBlack
import br.dev.ftdash.ui.theme.Zinc950
import kotlin.math.abs

/**
 * O painel, no layout do mostrador da própria FuelTech.
 *
 * ```
 * 8000 RPM  [======= degradê amarelo → vermelho =======]
 *            1  2  3  4  5  6  7  8  9  10
 * ─────────────┬──────────────┬──────────────────────────
 *  Odômetro    │   Marcha     │      Veloc.
 *  Total       │      5       │      112
 *  Parcial     │              │      Kmh
 * ─────────────┴──────────────┴──────────────────────────
 *  T.Motor  P.Óleo  Lambda  P.Comb  T.Ar  Ponto  Malha  Inj
 * ────────────────────────────────────────────────────────
 *  Tanque [=========]      MAP  TPS  Lenta   CUTOFF
 * ────────────────────────────────────────────────────────
 *  diagnóstico (fonte, Hz, CRC, layout, GPS)
 * ```
 *
 * O que a FT mostra e a telemetria não traz virou outra coisa: o odômetro é
 * integrado do GPS, o tanque sai do duty dos bicos, e o espaço da memória do
 * datalogger foi para canais que a ECU manda de verdade.
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
    onResetTrip: () -> Unit,
    onFillTank: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(FtBlack)) {
        // A escala sai da altura disponível, não da densidade: numa central de
        // 1280×720 sobrava espaço morto e os números continuavam do tamanho da
        // tela de 1024×600, pequenos demais para ler de relance dirigindo.
        CompositionLocalProvider(LocalDashScale provides dashScaleFor(maxHeight.value)) {
            DashContent(state, onOpenCalibration, onToggleSource, onResetTrip, onFillTank)
        }
    }
}

@Composable
private fun DashContent(
    state: DashUiState,
    onOpenCalibration: () -> Unit,
    onToggleSource: () -> Unit,
    onResetTrip: () -> Unit,
    onFillTank: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        FtRpmBar(
            rpm = state.rpm,
            peakRpm = state.peakRpm,
            shiftRpm = state.shiftRpm,
            maxRpm = state.maxRpm,
            hasData = state.hasData,
        )

        Divider()

        // ── Odômetro · Marcha · Velocidade ──
        Row(
            Modifier
                .fillMaxWidth()
                .weight(0.46f)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdometerPanelSlot(state, onResetTrip)
            Spacer(Modifier.weight(0.06f))
            FtGear(
                gear = state.gear,
                calibrated = state.gearCalibrated,
                onLongPress = onOpenCalibration,
                modifier = Modifier.weight(0.34f),
            )
            FtBigValue(
                label = when (state.speedOrigin) {
                    br.dev.ftdash.data.SpeedOrigin.GPS -> "Veloc. (GPS)"
                    br.dev.ftdash.data.SpeedOrigin.SIMULATED -> "Veloc. (simulada)"
                    br.dev.ftdash.data.SpeedOrigin.NONE -> "Veloc. (sem sinal)"
                },
                value = state.speedKmh?.let { "%.0f".format(it) } ?: "--",
                unit = "Kmh",
                valueColor = if (state.speedKmh == null) Zinc800 else Zinc100,
                modifier = Modifier.weight(0.34f),
            )
        }

        Divider()

        // ── Tira de canais do motor ──
        Row(
            Modifier
                .fillMaxWidth()
                .weight(0.30f)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            FtChannel(
                "T.Motor",
                state.fmt { "%.1f".format(state.engineTempC) },
                tempColor(state.engineTempC, state.hasData),
                Modifier.weight(1f),
            )
            FtChannel(
                "P.Oleo",
                state.fmt { "%.2f".format(state.oilPressureBar) },
                pressureColor(state.oilPressureBar, state.hasData, crit = 0.8f, warn = 1.5f),
                Modifier.weight(1f),
            )
            FtChannel(
                "S Geral",
                state.fmt { state.lambda?.let { "%.2f".format(it) } },
                lambdaColor(state.lambda, state.lambdaTarget),
                Modifier.weight(1f),
            )
            FtChannel(
                "P.Comb",
                state.fmt { "%.2f".format(state.fuelPressureBar) },
                pressureColor(state.fuelPressureBar, state.hasData, crit = 1.5f, warn = 2.5f),
                Modifier.weight(1f),
            )
            FtChannel(
                "T.Ar",
                state.fmt { "%.1f".format(state.airTempC) },
                if (state.hasData && state.airTempC > 60f) Amber500 else Zinc100,
                Modifier.weight(1f),
            )
            FtChannel(
                "Ponto",
                state.fmt { "%+.1f".format(state.ignitionDeg) },
                Zinc100,
                Modifier.weight(1f),
            )
            FtChannel(
                "Malha",
                state.fmt { "%+.1f".format(state.closedLoopPct) },
                if (state.hasData && abs(state.closedLoopPct) > 15f) Amber500 else Zinc100,
                Modifier.weight(1f),
            )
            FtChannel(
                "Inj ms",
                state.fmt { "%.2f".format(state.injTimeMs) },
                Zinc100,
                Modifier.weight(1f),
            )
        }

        Divider()

        // ── Tanque e status ──
        Row(
            Modifier
                .fillMaxWidth()
                .weight(0.24f)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FtTankGauge(
                fraction = state.fuelRemainingFraction,
                liters = state.fuelRemainingLiters,
                tankLiters = state.tankLiters,
                onFillTank = onFillTank,
                modifier = Modifier.weight(0.40f),
            )
            FtChannel("MAP", state.fmt { "%+.2f".format(state.mapBar) }, Zinc100, Modifier.weight(0.14f))
            FtChannel("TPS", state.fmt { "%.0f".format(state.tpsPct) }, Zinc100, Modifier.weight(0.12f))
            FtChannel(
                "Lenta",
                state.fmt { "%.0f".format(state.idleActuatorPct) },
                Zinc100,
                Modifier.weight(0.14f),
            )
            FtChannel("Vbat", state.fmt { "%.2f".format(state.vbat) }, vbatColor(state), Modifier.weight(0.14f))
            FtStatusBox("CUTOFF", state.hasData && state.cutoff)
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
            bytesPerSec = state.bytesPerSec,
            onLongPress = onToggleSource,
            onOpenConfig = onOpenCalibration,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Divider() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Zinc800),
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.OdometerPanelSlot(
    state: DashUiState,
    onResetTrip: () -> Unit,
) {
    br.dev.ftdash.ui.dash.components.OdometerPanel(
        totalKm = state.totalKm,
        tripKm = state.tripKm,
        averageKmPerLiter = state.averageKmPerLiter,
        instantKmPerLiter = state.instantKmPerLiter,
        onResetTrip = onResetTrip,
        modifier = Modifier.weight(0.28f),
    )
}

/** Antes do primeiro frame — e depois que a telemetria cai — tudo é "--". */
private inline fun DashUiState.fmt(format: () -> String?): String? =
    if (hasData) format() else null

private fun DashUiState.orNull(v: Float): Float? = if (hasData) v else null

/**
 * Cor das pressões. A faixa de alarme ficou onde estava quando eram barras —
 * o que mudou foi só o desenho, não o que conta como pressão baixa.
 */
private fun pressureColor(value: Float, hasData: Boolean, crit: Float, warn: Float) = when {
    !hasData -> Zinc100
    value < crit -> Red500
    value < warn -> Amber500
    else -> Emerald500
}

private fun tempColor(temp: Float, hasData: Boolean) = when {
    !hasData -> Zinc100
    temp > 110f -> Red500
    temp > 100f -> Amber500
    temp < 70f -> Amber500
    else -> Emerald500
}

private fun vbatColor(state: DashUiState) = when {
    !state.hasData -> Zinc100
    state.vbat < 11.5f -> Red500
    state.vbat < 12.5f -> Amber500
    state.vbat > 15f -> Amber500
    else -> Emerald500
}

private fun lambdaColor(lambda: Float?, target: Float) = when {
    lambda == null || target <= 0f -> Zinc100
    abs(lambda - target) <= 0.03f -> Emerald500
    abs(lambda - target) <= 0.08f -> Amber500
    else -> Red500
}

/** Alterna entre as duas fontes registradas. */
fun nextSource(current: SourceKind): SourceKind =
    if (current == SourceKind.REPLAY) SourceKind.USB else SourceKind.REPLAY
