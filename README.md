# FT Dash Android

Painel de monitoramento **somente leitura** da FuelTech FT450, para rodar na multimídia Android do carro.

O protocolo veio da engenharia reversa feita em `../ft-tuning-assistant` (Electron/TS): enumeração USB,
handshake, formato do frame e o mapa de offsets dos sensores. Nada aqui escreve na ECU.

Duas informações não vêm da FT: a **velocidade** (GPS do próprio Android) e a **marcha** (estimada a
partir de velocidade × RPM, com calibração).

## Estado

| Parte | Situação |
|---|---|
| Parser do protocolo | **53 testes passando**, incluindo os 33.699 frames reais no CRC |
| Lógica de marcha + calibração | pronta, coberta por teste |
| Painel (UI) | **rodando** e verificado em AVD Android 11, 1024×600 |
| Calibração de marcha | verificada ponta a ponta: relações salvas, marcha aparece no painel |
| Escala de RPM | automática (pico registrado, como na FT) ou manual |
| Leitura por USB | ligada, com aba de diagnóstico — **nunca executada contra a ECU** |

A fonte padrão é o **replay** de frames reais gravados do carro (`app/src/main/assets/fixtures/replay-107.txt`),
então o app funciona por inteiro sem carro e sem ECU. Toque longo na barra de status alterna replay ⇄ USB.

## Como abrir

Android Studio → *Open* → a pasta do projeto. O `local.properties` não é versionado; o Studio o gera no
primeiro sync.

Rodar só os testes do protocolo (rápido, JVM puro, sem emulador):

```bash
./gradlew :core:protocol:test
```

Gerar o APK de debug:

```bash
./gradlew :app:assembleDebug
```

### Versões, e por que estas

A cadeia toda é ditada pelo JDK que vem com o Android Studio, o **25**: Java 25 exige Gradle 9.1+, daí
Gradle 9.6.1 → AGP 9.3.0 (que pede Gradle 9.5+) → compileSdk 37 (o core-ktx 1.19 e o lifecycle 2.11
exigem 37+). `targetSdk` continua 34 de propósito — multimídia velha, app sideload.

A AGP 9 compila Kotlin sozinha ("built-in Kotlin"), então os módulos Android **não** aplicam
`org.jetbrains.kotlin.android` — aplicar junto quebra o sync, porque o plugin avulso ainda faz cast do
bloco `android {}` para `BaseExtension`, interface que a AGP 9 não expõe mais. Só o `:core:protocol`, que
é Kotlin/JVM puro, usa o plugin `kotlin.jvm`. O Kotlin está fixado em **2.2.10** porque é a versão de que
o POM da AGP 9.3.0 depende — assim o Kotlin embutido, os plugins de compilador e o módulo JVM ficam no
mesmo número.

## Estrutura

```
core/protocol/   Kotlin/JVM puro — CRC, parser, framer, sanidade, marcha. Onde ficam os testes.
core/data/       Android — fontes de telemetria (replay/USB), GPS, DataStore.
app/             Compose — painel em paisagem e tela de calibração.
```

O parser vive num módulo sem Android de propósito: os testes contra os fixtures rodam em segundos, sem
emulador.

## Calibração de marcha

Toque longo no indicador de marcha abre a tela. Dois caminhos, mesma lista editável no fim:

- **Aprender no carro** — escolha a marcha, ande em velocidade constante acima de 25 km/h e capture. O
  botão só habilita quando a razão rpm/km/h está estável (variação abaixo de 3 % em 2 s); grava a mediana.
- **Relações manuais** — pneu, diferencial e relações da caixa. A prévia ao lado de cada campo mostra
  quanto dá em km/h a 3.000 rpm, o que pega erro de digitação antes de sair da garagem.

Na prática o manual serve para começar e o aprendizado para corrigir: pneu gasto, patinagem e erro de
catálogo entram na medida real.

## Computador de bordo

Nada disso vem da ECU — é tudo calculado, e mostra `--` em vez de inventar quando falta configuração.

- **Odômetro** (total e parcial): velocidade do GPS integrada no tempo. Toque longo zera o parcial.
- **Tanque**: capacidade configurada menos o que passou pelos bicos. Precisa dos três campos da aba
  *TANQUE* — capacidade, vazão nominal de **um** bico em cc/min, e quantos são.
- **Média km/L**: parcial dividido pelo combustível do mesmo trecho. Zera junto com o parcial.
- **Instantânea**: velocidade dividida pela vazão atual, ambas suavizadas com a mesma constante de tempo.
  Em corte de combustível o consumo é zero e a conta daria infinito — satura e mostra `>99`.

Abastecer tem duas formas: *ENCHI O TANQUE* (cheio) ou *COLOQUEI [x] L* (parcial, nunca ultrapassa a
capacidade).

O consumo é contado **duas vezes**, de propósito, porque responde a duas perguntas com regras opostas: o
nível do tanque diminui ao abastecer, o denominador da média só cresce. Fossem o mesmo número, somar 10
litros faria a média saltar sozinha sem ninguém ter andado nada.

Duas limitações conhecidas do cálculo de combustível: usa a **vazão nominal** do bico, e a real varia com
a pressão (erra para menos em pressão alta, para mais em baixa); e ignora o enriquecimento de partida.
Serve para saber que está na reserva, não para chegar no lacrado.

## Escala da barra de RPM

Aba **RPM** da configuração, dois modos:

- **Automático** (padrão), igual ao da FT: o teto é sempre o RPM mais alto já registrado. Enquanto o
  motor não passar de 4.300, a barra vai até 4.300; no dia que passar, o novo valor vira o teto e fica.
  Como ele não recua sozinho, um pico falso estragaria a escala para sempre — por isso um candidato só é
  promovido depois de 3 frames seguidos acima do teto atual. O botão *zerar* é a única forma de baixá-lo.
- **Manual**: corte, aviso de troca e escala digitados.

## Diagnóstico de USB

Aba **USB** da configuração. Lista todo device do barramento com VID:PID, nome, interfaces e endpoints,
e diz em uma linha em qual caso você está: aparelho que não declara USB host, barramento vazio, FT com
outro VID:PID, ou só falta de permissão.

Ela existe porque a maior incógnita do projeto é se a porta USB da central faz host de verdade. Sem essa
tela, uma tentativa que falha no carro volta como "não funcionou"; com ela, volta sabendo qual é o caso —
e se é do tipo que software resolve.

## Ciclo de ignição

Testado na AVD, em três frentes:

| Cenário | Resultado |
|---|---|
| Processo morto e reaberto (3× seguidas) | volta streaming; pico de RPM, perfil de marcha e fonte escolhida intactos |
| Tela apagada 20 s e religada | volta sozinho |
| App em segundo plano 20 s (acima do `WhileSubscribed` de 5 s) | volta sozinho, sem ANR |

**O que o app NÃO faz:** iniciar sozinho quando a central liga. Um receiver de `BOOT_COMPLETED` não
resolveria — desde o Android 10 um app em segundo plano não pode abrir uma Activity, então ele falharia
em silêncio. Os dois caminhos que funcionam de verdade:

1. o `intent-filter` de `USB_DEVICE_ATTACHED` já no manifest, que **pode** abrir a Activity — se a
   central entregar esse intent;
2. marcar o FT Dash como app de inicialização nas configurações da própria central, que a maioria dessas
   multimídias oferece.

## Quando for ligar o USB de verdade

`UsbTelemetrySource` está completo (handshake, comando de configuração com token e CRC, reconexão), mas
nunca rodou contra a FT450 num aparelho Android. Ligue com `enabled = true` em `AppContainer`. Ordem de
suspeitos, se não funcionar:

1. **Sem permissão** — o `usb_device_filter.xml` com VID 7262 / PID 4098 deveria dar permissão
   persistente. Sem ele o Android pergunta a cada conexão.
2. **ZLP** — a primeira parte do comando de configuração tem exatamente 128 B, múltiplo do
   `wMaxPacketSize` de 64. Alguns stacks exigem um pacote de tamanho zero para fechar a transferência.
   Tente `sendConfigAsSingleTransfer = true`.
3. **USB host limitado** — muita multimídia chinesa só faz host para pendrive. `UsbManager.deviceList`
   vazio com a FT plugada indica isso, e aí não tem software que resolva.
4. **Reset na partida** — a partida do motor derruba o barramento; o laço de reconexão cobre isso.

## Coisas medidas, não presumidas

- CRC16/KERMIT (poly refletido 0x8408, do byte 1 até `len-3`, gravado em little-endian): 33.699 frames,
  zero falhas.
- **O offset do lambda depende do tamanho do frame**: 61 na config de 107 B, 65 na de 111 B. Ler no
  offset errado devolve até 65.535 λ — por isso um tamanho desconhecido nunca chuta, mostra `--`.
- 18,2 % dos frames de estrada trazem λ = 9.990, o código de erro/sonda fria da FuelTech. Vira `--`,
  nunca "9,99".
- A ECU transmite sozinha a ~15-20 Hz depois do handshake. É stream, não polling.
