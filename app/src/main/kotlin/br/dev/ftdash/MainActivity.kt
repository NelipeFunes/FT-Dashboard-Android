package br.dev.ftdash

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import br.dev.ftdash.ui.calib.CalibMode
import br.dev.ftdash.ui.calib.GearCalibScreen
import br.dev.ftdash.ui.calib.GearCalibViewModel
import br.dev.ftdash.ui.dash.DashScreen
import br.dev.ftdash.ui.dash.DashViewModel
import br.dev.ftdash.ui.dash.nextSource
import br.dev.ftdash.ui.theme.FtDashTheme

private enum class Screen { DASH, CALIBRATION }

class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* sem permissão o painel continua funcionando, só sem velocidade real */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Painel de carro: a tela não pode apagar no meio de uma viagem.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        goImmersive()

        container = AppContainer(applicationContext, lifecycleScope)

        locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)

        setContent {
            FtDashTheme {
                AppRoot(container)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goImmersive()
    }

    /** Esconde as barras do sistema; elas voltam com um swipe e somem de novo. */
    private fun goImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

@Composable
private fun AppRoot(container: AppContainer) {
    // Duas telas só — Navigation-Compose aqui seria uma dependência a mais para
    // resolver um `if`.
    var screen by rememberSaveable { mutableStateOf(Screen.DASH) }

    /** Aba em que a configuração deve abrir; null mantém a última usada. */
    var pendingTab by remember { mutableStateOf<CalibMode?>(null) }

    val dashViewModel: DashViewModel = viewModel(factory = factory { DashViewModel(container) })
    val dashState by dashViewModel.state.collectAsStateWithLifecycle()

    when (screen) {
        Screen.DASH -> DashScreen(
            state = dashState,
            onOpenCalibration = {
                pendingTab = null
                screen = Screen.CALIBRATION
            },
            // O painel manda em qual aba a configuração abre: tocar no tanque
            // e cair na aba de marchas seria mandar o usuário procurar.
            onOpenFuelConfig = {
                pendingTab = CalibMode.FUEL
                screen = Screen.CALIBRATION
            },
            onToggleSource = { dashViewModel.selectSource(nextSource(dashState.sourceKind)) },
        )

        Screen.CALIBRATION -> {
            val calibViewModel: GearCalibViewModel = viewModel(
                factory = factory { GearCalibViewModel(container, dashViewModel) },
            )
            val calibState by calibViewModel.state.collectAsStateWithLifecycle()

            LaunchedEffect(pendingTab) {
                pendingTab?.let {
                    calibViewModel.setMode(it)
                    pendingTab = null
                }
            }

            GearCalibScreen(
                state = calibState,
                manualPreview = if (calibState.mode == CalibMode.MANUAL) {
                    calibViewModel.manualPreview()
                } else {
                    emptyList()
                },
                onSetMode = calibViewModel::setMode,
                onSelectGear = calibViewModel::selectGear,
                onCapture = { calibViewModel.captureSelectedGear() },
                onRemoveGear = { calibViewModel.removeGear(it) },
                onEditRatio = { gear, ratio -> calibViewModel.editRatio(gear, ratio) },
                onManualField = { w, p, r, f, i, g ->
                    calibViewModel.updateManualField(w, p, r, f, i, g)
                },
                onApplyManual = { calibViewModel.applyManual() },
                onSetRpmMode = { calibViewModel.setRpmScaleMode(it) },
                onRpmField = { redline, shift, max ->
                    calibViewModel.updateRpmField(redline, shift, max)
                },
                onApplyRpm = { calibViewModel.applyRpmLimits() },
                onResetPeak = { calibViewModel.resetLearnedMax() },
                onFuelField = { tank, flow, count ->
                    calibViewModel.updateFuelField(tank, flow, count)
                },
                onApplyFuel = { calibViewModel.applyFuelSetup() },
                onFillTank = { dashViewModel.fillTank() },
                onAddFuelField = { calibViewModel.updateAddFuelField(it) },
                onAddFuel = { liters -> dashViewModel.addFuel(liters) },
                onResetTrip = { dashViewModel.resetTrip() },
                onSetInjectorUnit = { calibViewModel.setInjectorUnit(it) },
                flowCcMin = calibViewModel.injectorFlowCcMin(),
                onRescanUsb = { calibViewModel.scanUsb() },
                onUseUsb = { calibViewModel.useUsbSource() },
                onUseReplay = { calibViewModel.useReplaySource() },
                onClose = { screen = Screen.DASH },
            )
        }
    }
}

/** Fábrica de ViewModel de uma linha, já que não há grafo de DI. */
private inline fun <reified T : ViewModel> factory(crossinline create: () -> T) =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
    }
