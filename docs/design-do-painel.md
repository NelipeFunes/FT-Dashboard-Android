# Design do painel

O painel é lido de relance, com o carro andando. Quase toda decisão aqui sai disso.

## Regras que atravessam tudo

**O painel é só de leitura.** Tocar em qualquer mostrador abre a configuração; mudar estado só acontece
em botão com nome. Já houve toque longo escondido que enchia o tanque e outro que zerava o parcial —
gestos invisíveis, irreversíveis, fáceis de acionar sem querer numa lombada e impossíveis de descobrir
de propósito.

**Dado ausente é `--`, nunca zero.** Vale para antes do primeiro frame, para sonda em erro, para GPS sem
fixação e para o que falta configurar.

**A origem do número é declarada.** `Veloc. (GPS)` / `(simulada)` / `(sem sinal)`. Um painel que chama
de GPS um número sintetizado é pior que um painel sem velocidade.

**Preto puro (`#000000`).** Qualquer cinza no fundo rouba contraste dos números e, à noite, vira um
retângulo acinzentado luminoso no canto do para-brisa. Isso inclui o `windowBackground` do tema, que é o
que aparece no instante entre abrir o app e o Compose desenhar.

## Layout

```
1878 RPM  ▁▁▁▁▁▁▁▂▃▅███   ← cunha, largura inteira
          1 2 3 4 5 6 7 8
─────────────┬──────────────┬──────────────
 Odômetro    │   Marcha     │   Veloc.
 Média km/L  │      ?       │    15  Kmh
─────────────┴──────────────┴──────────────
 T.Motor P.Óleo S Geral P.Comb T.Ar P.Ignição Malha Inj
──────────────────────────────────────────
 Tanque ▓▓▓▓░  MAP TPS Lenta Vbat  CUTOFF
──────────────────────────────────────────
 ● USB                            CONFIG
```

## A barra de RPM

O elemento mais trabalhado do painel, e o que mais mudou.

### O degradê é da escala, não do valor

Amarelo no começo, laranja no meio, vermelho no fim — **sempre nas mesmas posições**. A barra não muda
de cor conforme sobe; ela revela a cor que já estava ali. O olho aprende "vermelho é lá na direita" uma
vez e depois lê a posição sem interpretar cor nenhuma.

Implementação: o degradê é medido sobre a **largura total** e recortado no ponto atual. Medido sobre a
parte preenchida, a ponta ficaria vermelha em qualquer rotação — inclusive na lenta.

O trilho inteiro leva o degradê apagado (18 %), para a escala de cor estar sempre visível. Só o
preenchido é o valor, mas um degradê recortado aos 30 % seria uma tarja amarela sem informação nenhuma.

### A forma é uma cunha

Fina até 4.000 rpm, subindo até 7.000, platô depois. Resolve três coisas de uma vez:

- a **altura** vira uma segunda codificação da rotação, somada ao comprimento — dá para perceber "está
  alto" pela silhueta, sem localizar a ponta;
- onde a barra é fina sobra espaço **acima** dela, e é ali que o número mora — sem tarja escura por trás
  e sem roubar largura da escala;
- passado o giro útil o que importa é *estar* lá, não quanto falta para o fim da régua — daí o platô.

A curva de subida é exponencial, `(e^kt − 1)/(e^k − 1)` com **k = 1,3**: sai rente à reta e vai ficando
íngreme, que é a silhueta da FT.

Constantes para afinar, todas no topo de `FtRpmBar.kt`:

| constante | valor | efeito |
|---|---|---|
| `RISE_FROM_RPM` | 4.000 | onde começa a subir |
| `RISE_TO_RPM` | 7.000 | onde chega ao topo |
| `MIN_HEIGHT_RATIO` | 0,34 | espessura da parte reta |
| `RISE_CURVE_K` | 1,3 | quanto a curva é exponencial (perto de 0 vira reta) |
| `BLOCK_HEIGHT` | 64 dp | altura do bloco todo |

Os pontos de subida são **rotações**, não frações da largura: se a régua crescer além de 8.000 pelo
aprendizado do pico, os 4.000 e 7.000 continuam onde estão em vez de a barra mudar de forma sozinha.

### A régua

Cada número é posicionado na **posição do seu milhar**. Já foi uma `Row` de células de peso igual com o
texto centralizado, e isso punha o rótulo no meio da célula: o "1" caía em 6,25 % da largura quando
1.000 rpm está em 12,5 %. Toda a régua ficava meia divisão à esquerda e o preenchimento parecia
adiantado.

## Tipografia

Mono com algarismos de largura fixa (`tnum`) em tudo que é número. Sem isso o mostrador respira a cada
dígito que muda, e a 17 Hz isso vira tremor visível de canto de olho.

Rótulos em 11sp, valores em 22sp. O que faz a tira ser lida de relance não é o tamanho absoluto e sim a
**diferença** entre rótulo e número — crescer os dois junto manteria a hierarquia igual gastando espaço.

### Escala por tela

`LocalDashScale`, derivado da altura útil em **dp**. A referência é 400 dp, que é a altura da central de
1024×600 a 240 dpi — não 600. Confundir pixel com dp aqui é fácil e passa despercebido: com 600 como
referência a escala dava 0,9 numa tela maior e travava no piso, deixando tudo do mesmo tamanho com o
espaço sobrando em volta.

Teto de 1,5×: acima disso os números viram outdoor e o painel perde a densidade de informação.

## Cores de alarme

| | |
|---|---|
| Verde `#10B981` | no alvo |
| Âmbar `#F59E0B` | atenção |
| Vermelho `#EF4444` | crítico |

Aplicadas a temperatura do motor, pressões, bateria, λ contra o alvo e malha fechada. O λ compara com o
**alvo da própria ECU**, não com um valor fixo.

## Barra de status

Mostra contadores **só quando há o que investigar** — fonte fora de streaming, CRC quebrado ou layout
desconhecido. No caso normal sobra o LED, o nome da fonte, a origem da velocidade e o botão CONFIG.

"frames 387" e "layout 107B" não ajudam a dirigir, mas jogá-los fora tiraria o que responde "por que
parou" no carro. Os contadores ao vivo ficam sempre disponíveis na aba **USB** da configuração.

## Coisas que a FT tem e aqui viraram outra coisa

| na FT | aqui |
|---|---|
| Odômetro | integrado do GPS |
| Tanque | calculado do duty dos bicos |
| Memória do datalogger | canais que a ECU manda (MAP, TPS, Lenta, Vbat) |
| Régua 1–10 | 1–8 |
| Parte não preenchida em branco | escura — numa tela de 10" à noite, branco ofusca |
