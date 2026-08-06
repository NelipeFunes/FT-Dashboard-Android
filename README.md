# FT Dash Android

Painel de monitoramento **somente leitura** da FuelTech FT450, para rodar na multimídia Android do carro.

O protocolo veio da engenharia reversa feita em `../ft-tuning-assistant` (Electron/TS): enumeração USB,
handshake, formato do frame e o mapa de offsets dos sensores. Nada aqui escreve na ECU.

Duas informações não vêm da FT: a **velocidade** (GPS do próprio Android) e a **marcha** (estimada a
partir de velocidade × RPM, com calibração).

## Estado

| Parte | Situação |
|---|---|
| Parser do protocolo | pronto, coberto por teste contra 33.699 frames reais |
| Lógica de marcha + calibração | pronta, coberta por teste |
| Painel (UI) | pronto |
| Leitura por USB | **escrita, mas nunca executada contra a ECU** — ver abaixo |

A fonte padrão é o **replay** de frames reais gravados do carro (`app/src/main/assets/fixtures/replay-107.txt`),
então o app funciona por inteiro sem carro e sem ECU. Toque longo na barra de status alterna replay ⇄ USB.

## Como abrir

Precisa de **Android Studio** (traz JDK, SDK e Gradle). Não há JDK nesta máquina, então nada aqui foi
compilado ainda.

1. Android Studio → *Open* → `C:\Users\User\ft-dash-android`
2. Deixe sincronizar; ele gera o `local.properties` e o wrapper do Gradle
3. *Run* no emulador ou na multimídia via ADB

Rodar só os testes do protocolo (rápido, JVM puro, sem emulador):

```bash
./gradlew :core:protocol:test
```

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
