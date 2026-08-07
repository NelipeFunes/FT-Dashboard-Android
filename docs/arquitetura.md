# Arquitetura

## Três módulos, e por quê

```
:core:protocol   Kotlin/JVM puro — sem Android
:core:data       Android library — fontes de dados e persistência
:app             Compose — painel e configuração
```

O parser do protocolo e toda a matemática (marcha, odômetro, combustível) vivem num módulo **sem
dependência de Android**. Não é purismo: é o que faz `./gradlew :core:protocol:test` rodar em segundos,
sem emulador, contra 33.699 frames reais. Um teste que custa caro para rodar é um teste que não roda.

## Fluxo de dados

```
                    ┌─ ReplayTelemetrySource ─┐
UsbTelemetrySource ─┤                          ├─→ TelemetryRepository ─→ DashViewModel ─→ DashScreen
                    └──────────────────────────┘         (conflate)            │
                                                                               │
GpsSpeedSource ──┐                                                        TripComputer
                 ├──→ speedFixes ────────────────────────────────────────→ GearEstimator
SimulatedSpeed ──┘                                                             │
                                                                          SettingsStore
```

### `TelemetrySource` é um `Flow`, não um `readFrame()`

A ECU **empurra** os frames sozinha a ~15-20 Hz depois do handshake. Ninguém puxa. Modelar isso como
polling — que é o que o app de tuning Electron faz — obrigaria a UI a saber sobre temporização. Com
`Flow`, trocar replay por USB real não encosta em nada acima da camada de dados.

O repositório aplica `conflate()`: se a tela recompõe mais devagar que a ECU transmite, os frames
intermediários são descartados em vez de acumular. Num painel isso é o certo — ninguém quer ver
telemetria de três segundos atrás.

### Duas fontes de velocidade, escolhidas pela fonte de telemetria

A ECU deste carro não recebe sensor de roda, então a velocidade vem do GPS. Mas o fixture de replay
não tem velocidade nenhuma (veio da ECU), o que tornaria marcha e calibração testáveis só dirigindo.

Daí `SimulatedSpeedSource`, que sintetiza km/h a partir do RPM do replay. A regra de qual usar está
**amarrada à fonte de telemetria**, não a um interruptor solto:

- fonte **REPLAY** → velocidade simulada (os frames já são de mentira)
- fonte **USB** → sempre GPS; sem fixação, o painel mostra `--`

Inventar velocidade dentro do carro em movimento seria pior que não mostrar nada, e a marcha estimada
em cima de um número inventado seria pior ainda. `SpeedFix` carrega a origem (`GPS` / `SIMULATED` /
`NONE`) e o painel escreve qual é — nunca chama de GPS o que não é.

## Persistência

`DataStore Preferences`, uma chave por coisa, com o perfil de marchas serializado como JSON num campo
só. Sem Room, sem Proto DataStore: são poucos campos que mudam raramente.

Os padrões vivem **só** em `AppSettings`. O mapeamento do disco lê de uma instância de referência
(`private val defaults = AppSettings()`) em vez de repetir literais — declarar o mesmo padrão nos dois
lugares já enganou uma vez, quando trocar o padrão da fonte para USB não teve efeito nenhum porque o
mapeamento tinha `?: SourceKind.REPLAY` escrito à mão.

O odômetro é gravado a cada 30 s, não a cada frame: a 17 Hz seria castigar a memória da central, e o
pior caso de perda a 100 km/h são 830 metros.

## Injeção de dependência

`AppContainer`, à mão. Duas telas e meia dúzia de objetos não justificam Hilt.

## Versões, e por que estas

A cadeia toda é ditada pelo JDK que vem com o Android Studio, o **25**:

| | | motivo |
|---|---|---|
| Gradle | 9.6.1 | Java 25 exige 9.1+ |
| AGP | 9.3.1 | pede Gradle 9.5+ |
| compileSdk | 37 | core-ktx 1.19 e lifecycle 2.11 exigem 37+ |
| Kotlin | 2.2.10 | é a versão de que o POM da AGP 9.3.1 depende |
| minSdk | 26 | cobre multimídia Android 8+ |
| targetSdk | 34 | de propósito: sideload em central velha, subir só traria restrições |

**A AGP 9 compila Kotlin sozinha.** Os módulos Android **não** aplicam `org.jetbrains.kotlin.android` —
aplicar junto quebra o sync com `ApplicationExtensionImpl cannot be cast to BaseExtension`, porque o
plugin avulso ainda faz cast para uma interface que a AGP 9 não expõe mais. Só o `:core:protocol`, que
é Kotlin/JVM puro e não passa pela AGP, usa o plugin `kotlin.jvm`.

O Kotlin está fixado em 2.2.10 porque é o que a AGP embute — assim o Kotlin embutido, os plugins de
compilador e o módulo JVM ficam todos no mesmo número.
