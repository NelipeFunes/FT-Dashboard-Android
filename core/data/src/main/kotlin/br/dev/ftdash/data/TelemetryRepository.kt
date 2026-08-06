package br.dev.ftdash.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.shareIn

/**
 * Ponto único de acesso à telemetria, com troca de fonte em tempo de execução.
 *
 * `conflate()` é o que protege a tela: se a UI recompõe mais devagar que a ECU
 * transmite, os frames intermediários são descartados em vez de acumular. Num
 * painel isso é o certo — ninguém quer ver telemetria de 3 segundos atrás.
 */
class TelemetryRepository(
    private val scope: CoroutineScope,
    private val sources: Map<SourceKind, TelemetrySource>,
) {

    /**
     * Começa no replay e só troca quando as preferências chegam do disco.
     *
     * Não é o padrão do app — é o estado dos primeiros milissegundos, antes de
     * saber qual fonte o usuário escolheu. Sair tentando USB aqui só faria o
     * painel piscar um erro de conexão em toda abertura.
     */
    private val _sourceKind = MutableStateFlow(SourceKind.REPLAY)
    val sourceKind: StateFlow<SourceKind> = _sourceKind.asStateFlow()

    fun selectSource(kind: SourceKind) {
        if (sources.containsKey(kind)) _sourceKind.value = kind
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val events: Flow<TelemetryEvent> = _sourceKind
        .flatMapLatest { kind ->
            sources[kind]?.stream() ?: kotlinx.coroutines.flow.flowOf(
                TelemetryEvent.Status(SourceState.ERROR, "fonte $kind não registrada")
            )
        }
        .conflate()
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)
}
