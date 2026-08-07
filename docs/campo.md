# Testes de campo

O que só o carro respondeu, e o que ainda está em aberto.

---

## Teste 1 — 2026-08-06, versão 0.9.0

**A comunicação com a ECU funcionou.** Esse era o risco de verdade do projeto: todo o protocolo estava
coberto por teste contra 33.699 frames reais, mas a camada `UsbManager` do Android nunca tinha falado
com a FT450. Falou.

Quatro problemas relatados, todos corrigidos na 0.9.1.

### 1. A comunicação caía ao ligar o motor

Com a ignição ligada funcionava; ao dar partida, caía. Replugar o cabo devolvia alguns segundos e caía
de novo.

**Causa:** a detecção de queda era por ausência de **frames válidos**, copiada do app de tuning
(`usbSource.ts:230`, `lastValidFrameAt` + `STALL_MS = 3000`). Mas o app de tuning **não confere CRC** —
só cabeçalho e tamanho — e o FT Dash confere. Com o motor girando, o ruído do alternador e das bobinas
derruba parte dos CRCs; os frames válidos param; a detecção conclui que o cabo morreu. Desmonta o
handle, refaz o handshake, pega alguns frames bons, repete.

Um laço de autodestruição que **só aparecia com o motor ligado** — exatamente quando o app precisa
funcionar. Sozinha, a validação de CRC é uma melhoria. Sozinha, a detecção por frames funciona há meses
no carro. Juntas, se destroem.

**Correção:** a regra passou a ser **ausência de bytes**. Cabo que entrega bytes está vivo. Bytes
correndo sem frame válido por 3 s viram um aviso ("recebendo dados corrompidos"), sem reconectar.

Junto: backoff crescente na reconexão (2 s → 15 s), perda depois de conectado volta rápido sem backoff
(é o caso da partida), e permissão negada não é mais pedida em laço.

### 2. A câmera de ré era interrompida — problema de segurança

Engatando a ré, a câmera abria e depois de um tempo o painel tomava a tela **com o carro andando de ré**.

**Causa:** o `intent-filter` de `USB_DEVICE_ATTACHED` estava na MainActivity. A cada reconexão do
barramento — que naquele momento acontecia a cada poucos segundos, pelo bug acima — o Android relançava
o app e o trazia para a frente.

**Correção:** o aviso vai para uma activity invisível que só fecha. O `device_filter` continua nela
porque é ele que concede a permissão de USB de forma **persistente**; perder isso traria de volta o
diálogo de permissão aparecendo sozinho no meio do trânsito.

**Efeito colateral:** o app não abre mais sozinho ao plugar o cabo. Para subir junto com a central,
marque o FT Dash como app de inicialização nas configurações dela.

### 3. Tela de 1280×720

O layout foi desenhado em 1024×600 e os números ficavam do mesmo tamanho na tela maior — espaço morto e
dígitos pequenos demais. Resolvido com `LocalDashScale`.

### 4. Faltava um botão de configuração explícito

O acesso era só por toque longo. Bom como atalho para quem já sabe, inútil para achar pela primeira vez.

---

## Fatos de hardware confirmados na engenharia reversa

Coisas que **não** são bug e vão acontecer:

- **A partida do motor derruba o barramento USB.** Está documentado no projeto de tuning: *"a queda de
  tensão do arranque derruba o barramento — o FTManager aborta o datalog, e até o datalogger da própria
  FT parou nos testes"*. Cair no instante da partida é esperado; o que não é normal é não voltar depois.

- **A ECU não muda de protocolo com o motor ligado.** Verificado: a captura `ft-frames-motor-111.txt`
  pegou uma partida de verdade (1.604 frames com RPM 0, depois 256 → 374 → 762 → 951 → 1120) e o tamanho
  do frame é 111 B antes e depois. Os dois tamanhos (107/111) são configuração de canais do FTManager.

---

## Em aberto para o próximo teste

**O mais importante: `B/s` e `crc` com o motor ligado.** Ficam na aba USB da configuração. São eles que
distinguem os dois cenários:

| observação | diagnóstico |
|---|---|
| `crc` alto e subindo, `B/s` correndo | era a interação CRC × detecção de vivacidade — resolvido |
| `crc` baixo e a conexão cai mesmo assim | reset de barramento de verdade; o que importa é voltar sozinha |
| `B/s` zerado por muito tempo | problema elétrico ou de cabo, não de software |

Se o `crc` vier altíssimo e o painel travar, o próximo passo é um **modo tolerante** que aceita frame
sem CRC — exatamente o que o app de tuning faz, e que roda nesse carro há meses. Não foi feito ainda de
propósito: colocar as duas mudanças juntas estragaria o experimento.

**Confirmar que a câmera de ré não é mais interrompida.**

**Vazão do bico.** ~240 cc/min é uma aposta inicial. Uma estimativa a partir da própria telemetria
(3.669 frames em carga, modelo de VE) deu ~190 cc/min, com faixa de 172–214 conforme o VE assumido. O
valor autoritativo está na configuração de injetores do FTManager, em lb/h. O ajuste definitivo é
empírico e leva um tanque.

**Nada do visual foi visto no carro.** Da 0.9.1 até a 0.9.13 são treze versões só na bancada. A barra de
RPM pode ler diferente com o motor de verdade varrendo a faixa toda, em vez do replay que fica entre
1.400 e 2.400 rpm quase o tempo inteiro.
