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

## Teste 2 — 2026-08-06, versão 0.9.14

**Não resolveu.** Relato: liga o carro, o app manda algumas informações e depois para. Depois disso a
aba USB passa a mostrar **"barramento vazio"**.

Isso muda o diagnóstico. "Barramento vazio" não é ruído derrubando CRC — é a FT450 **sumindo da lista de
devices do Android**. Nenhum ajuste de timeout, de tolerância a CRC ou de handshake alcança esse
cenário: não há com quem falar. A correção do teste 1 endereçou o problema errado, ou endereçou um
problema real que escondia este.

Duas causas possíveis, e elas pedem respostas diferentes:

| hipótese | como se distingue |
|---|---|
| a ECU/porta cai e **volta**, e o app não reconecta | o log mostra "device voltou ao barramento" e depois falha ao abrir |
| a porta USB da central **morre até replugar o cabo** | o log fica em "device ausente" para sempre, e replugar ressuscita |

### Um defeito nosso, achado ao investigar

`releaseInterface` **nunca era chamado**. O `UsbTelemetrySource` fechava a conexão sem soltar a
interface reivindicada — em todo ciclo de reconexão. O app Electron que serviu de base solta em todo
caminho de saída (`usbSource.ts:132` e `:719`); a porta Android não copiou isso.

Não é comprovadamente a causa do relato, mas é exatamente o tipo de coisa que trava o controlador USB de
uma central velha depois de algumas quedas seguidas — e o sintoma seria este: cai, e o device não volta
mais até o cabo sair e entrar.

Também corrigido: com o device ausente, o backoff crescia até 15 s entre varreduras. Varrer a lista de
devices é uma leitura em memória, não toca no hardware — não havia o que economizar, e o custo era até
15 s de painel morto depois que a ECU já tinha voltado. Agora a varredura de device ausente é fixa em
1 s; o backoff ficou só para tentativa de conexão que falha.

### O histórico de USB

O motivo de o teste 1 e o teste 2 terem voltado sem resposta é o mesmo: **o painel só mostra o agora**,
e o defeito é uma sequência. Quando alguém olha a tela, o instante que interessa já passou, e dentro do
carro não existe logcat.

A aba USB agora tem um histórico com hora, mais recente em cima, no painel da direita. Ele grava em
`Android/data/br.dev.ftdash/files/usb-log.txt`, que sobrevive a fechar o app e à central desligar.

A linha que decide tudo é a de queda:

```
PAROU: FT450 sumiu do barramento | sessao 47s, 812 frames, 3 crcFail, 1 resync
```

- **"sumiu do barramento"** → o device caiu; é energia ou re-enumeração, não protocolo.
- **"sem dados há Nms"** com o device ainda presente → a ECU parou de mandar com o cabo vivo; aí sim é
  handshake ou sessão.
- **`crcFail` alto** → ruído elétrico; é o caso que justificaria o modo tolerante.

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

**Trazer o log.** Abrir CONFIG → USB depois da queda e fotografar o painel da direita, ou copiar
`Android/data/br.dev.ftdash/files/usb-log.txt`. É a única coisa que importa desta vez — sem ele, a
terceira ida ao carro volta igual às duas primeiras.

**A pergunta a responder:** depois de cair, a FT volta ao barramento sozinha? O log responde direto:
"device voltou ao barramento" aparece ou não aparece.

- **Não volta nunca, e replugar o cabo resolve** → é a porta USB da central travando. O
  `releaseInterface` que faltava é o suspeito; se voltar a acontecer mesmo com ele, não há saída por
  software na porta atual.
- **Volta e cai de novo em ciclo** → é energia. A porta provavelmente está no ACC e oscila com o
  arranque, ou o cabo não segura corrente.
- **Nunca sai do barramento e para de mandar dados** → aí sim é protocolo, e o alvo passa a ser o
  handshake.

Se o `crcFail` do log vier altíssimo, o próximo passo é um **modo tolerante** que aceita frame sem CRC —
exatamente o que o app de tuning faz, e que roda nesse carro há meses. Não foi feito ainda de propósito:
colocar as duas mudanças juntas estragaria o experimento.

**Confirmar que a câmera de ré não é mais interrompida.**

**Vazão do bico.** ~240 cc/min é uma aposta inicial. Uma estimativa a partir da própria telemetria
(3.669 frames em carga, modelo de VE) deu ~190 cc/min, com faixa de 172–214 conforme o VE assumido. O
valor autoritativo está na configuração de injetores do FTManager, em lb/h. O ajuste definitivo é
empírico e leva um tanque.

**Nada do visual foi visto no carro.** Da 0.9.1 até a 0.9.13 são treze versões só na bancada. A barra de
RPM pode ler diferente com o motor de verdade varrendo a faixa toda, em vez do replay que fica entre
1.400 e 2.400 rpm quase o tempo inteiro.
