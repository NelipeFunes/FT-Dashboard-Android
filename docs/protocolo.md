# Protocolo USB da FT450

Portado da engenharia reversa feita no projeto `ft-tuning-assistant` (Electron/TS), cuja documentação
completa está em `docs/reverse-engineering.md` de lá. Este arquivo cobre só o que o FT Dash usa —
**leitura de telemetria**, nada de escrita.

## A FT450 não é serial

Não há baud rate, paridade nem DTR/RTS. É **USB bulk vendor-specific** puro (`bDeviceClass = 0`), com
um par de endpoints de 64 bytes.

| | |
|---|---|
| VID / PID | `0x1C5E` / `0x1002` (decimal 7262 / 4098) |
| Interface | 0 |
| EP telemetria (ECU→app) | `0x81` |
| EP comando (app→ECU) | `0x01` |
| wMaxPacketSize | 64 |

No Android: `UsbManager` → `claimInterface(0, force = true)` → `bulkTransfer`.

## Conexão

1. `openDevice`, `claimInterface`
2. Preâmbulo — 4 comandos no EP `0x01`, com 80 ms e uma **leitura descartável** entre cada (timeout ali
   é normal):
   - `aa000100000000440b` (hello — respondido com a identificação/serial da ECU)
   - `aa00010111000e0000000000000000000000000000df9a` (23 B, função desconhecida, não bloqueia)
   - hello, hello
3. Comando de configuração — **135 bytes**:
   ```
   aa0001010c007e │ token 2B BE │ corpo fixo 119B │ 0000000030 │ CRC16-KERMIT LE 2B
   ```
   Enviado em **duas transferências coladas (128 B + 7 B), sem leitura entre elas** — a ECU só processa
   a mensagem completa.
4. A partir daí a ECU **transmite sozinha** a ~15-20 Hz. Não existe comando de "pedir frame".

O **token de sessão** é um inteiro 16 bits não-nulo (`0x0000` significa "não configurada"), sorteado a
cada conexão. A ECU passa a ecoá-lo nos bytes 7-8 de todo frame — dá para filtrar frames de outra
sessão. Replay de bytes de uma sessão antiga **não** configura uma ECU recém-ligada, por isso o comando
é construído e não replayado.

⚠️ A primeira parte tem exatamente 128 B, múltiplo do `wMaxPacketSize`. Alguns stacks USB exigem um ZLP
para fechar a transferência. Se o handshake não responder, `sendConfigAsSingleTransfer = true` manda os
135 B de uma vez — é a primeira coisa a testar.

## Frame de telemetria

```
byte 0      0xAA                 sync
byte 1..5   00 80 01 0B 00       cabeçalho fixo
byte 6      tamanho − 9          auto-descritivo
byte 7..8   token da sessão      ecoado
byte 9..N−3 campos               big-endian
byte N−2,N−1 CRC16/KERMIT        little-endian
```

Todos os campos de sensor são **big-endian**. Só o CRC é little-endian.

**CRC16/KERMIT**: poly `0x1021` refletido (`0x8408`), init 0, xorout 0, calculado do **byte 1** até
`len−3` (pula o sync). Verificado contra os 33.699 frames dos três fixtures: zero falhas.

### O tamanho não é fixo, e os offsets mudam junto

Confirmados 107 B e 111 B **no mesmo carro** — a diferença é a configuração de canais/tela salva no
FTManager, não o estado do motor. Há captura com o motor sendo dado a partida no meio e o tamanho não
muda.

| offset | 107 B | 111 B |
|---|---|---|
| λ da sonda | 61 | 65 |
| sonda de malha fechada | — | 71 |

Ler no offset errado devolve até **65.535 λ**. Por isso `FrameLayout.forLength()` só reconhece tamanhos
confirmados, e um tamanho desconhecido decodifica o bloco comum e devolve `lambda = null` — nunca chuta.

### Campos comuns a todos os tamanhos

| offset | canal | tipo | escala | unidade |
|---|---|---|---|---|
| 9 | TPS | u16be | ×0,1 | % |
| 11 | MAP | **s16be** | ×0,001 | bar (negativo em vácuo) |
| 13 | temp. do ar | u16be | ×0,1 | °C |
| 15 | temp. do motor | u16be | ×0,1 | °C |
| 17 | pressão de óleo | u16be | ×0,001 | bar |
| 19 | pressão de combustível | u16be | ×0,001 | bar |
| 21 | Vbat | u16be | ×0,01 | V |
| 23 | RPM | u16be | ×1 | rpm |
| 25 | tempo de injeção | u16be | ×0,01 | ms |
| 29 | dwell | u16be | ×0,001 | ms |
| 31 | ponto de ignição | **s16be** | ×0,1 | ° |
| 33 | abertura dos bicos | u16be | ×0,1 | % |
| 37 | atuador de lenta | u16be | ×1 | % |
| 54 bit2 | CUTOFF | flag | — | bool |
| 55 | λ alvo | u16be | ×0,001 | λ (0 = malha aberta) |
| 57 | correção de malha fechada | **s16be** | ×0,1 | % |

**λ = 9990 é código de erro de sonda**, não leitura. Aparece em 18,2 % dos frames de estrada. O campo é
nullable justamente para isso virar `--` no painel em vez de "9,99".

**A abertura dos bicos é semissequencial.** O canal lê exatamente o dobro do duty calculado sobre 720° —
28,1 % contra 14,1 %, em todas as amostras. Os bicos disparam uma vez por volta do virabrequim, duas por
ciclo, e o canal já entrega a fração real de tempo aberto. Assumir sequencial puro dobraria o consumo
calculado.

## Remontagem do stream

`StreamFramer`. Não dá para supor que uma leitura devolva um frame: o endpoint tem pacotes de 64 B e os
frames têm 107/111. A ECU ainda intercala no mesmo EP um segundo stream de ~263 B que não é telemetria.

Recuperação de erro: **CRC falho avança 1 byte**, não o frame inteiro. Se o tamanho declarado estiver
corrompido, pular `len` bytes jogaria fora frames bons.

## Diferença deliberada em relação ao app de tuning

O Electron **não confere o CRC** da telemetria — só cabeçalho e tamanho — e convive com ~4 % de frames
corrompidos, tratados depois por guards de sanidade. O FT Dash confere.

Isso é melhor para a qualidade do dado, mas **causou o bug do primeiro teste de campo**: o Electron
detecta queda de conexão por ausência de *frames válidos*, e eu copiei essa regra junto. Com o motor
ligado, o ruído derruba parte dos CRCs, os frames válidos param, e o app concluía que o cabo tinha
morrido — desmontava tudo e refazia o handshake em laço.

A detecção agora é por **ausência de bytes**. Cabo que entrega bytes está vivo; o que os bytes trazem é
outro problema, e a resposta a ele é descartar o frame, não derrubar a conexão. Ver
[campo.md](campo.md).

## Faixas de sanidade

Copiadas de `shared/constants.ts` do projeto de tuning, calibradas contra meses de log real.

| canal | faixa | salto máx./frame |
|---|---|---|
| RPM | 0–12000 | 4000 |
| MAP | −1,0 a 4,0 | — |
| TPS | 0–100 | — |
| λ | 0–2 | 0,3 |
| temp. motor | −20 a 150 | 20 |
| Vbat | 6–20 | 3 |
| malha fechada | ±60 | 15 |

MAP e TPS ficam **sem limite de salto de propósito**: os dois mudam de verdade num piscar.

Depois de 10 rejeições seguidas o valor é aceito — senão uma mudança legítima de regime travaria o
mostrador para sempre.
