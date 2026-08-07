# Cálculos

Nada aqui vem da ECU pronto — marcha, distância e combustível são todos derivados. Todos moram em
`:core:protocol`, sem Android, cobertos por 81 testes.

Regra que atravessa tudo: **quando falta dado para calcular, o mostrador diz `--`**. Um painel de carro
que inventa número é pior que um painel vazio.

---

## Marcha estimada

A ECU deste carro não recebe sensor de roda, então a marcha sai do cruzamento do RPM com a velocidade
do GPS: `razão = rpm / km/h`, comparada com as razões calibradas.

Três detalhes decidem se o mostrador fica estável ou pisca:

### 1. Pareamento de taxas

O RPM chega a ~17 Hz e o GPS a ~1 Hz. Casar o RPM instantâneo com a velocidade faz a razão tremer ~10 %
sozinha, porque as duas amostras são de instantes diferentes. Cada fixação de GPS é casada com a **média
de RPM da janela de ±300 ms** em torno dela.

### 2. Histerese assimétrica

Entra na marcha com erro relativo **< 7 %**, sai só quando passa de **14 %**. A banda morta entre as duas
é o que impede a oscilação entre marchas de relação próxima (3ª e 4ª).

### 3. Confirmação por tempo, não por contagem

A taxa do GPS varia demais entre aparelhos (1 Hz numa multimídia barata, 10 Hz num chip bom). "N
amostras" significaria 4 s num caso e 0,4 s no outro. A confirmação é **1,2 s** de candidato estável,
com mínimo de 2 amostras para não engatar numa fixação isolada.

### Embreagem e ponto morto

Não há sensor de nenhum dos dois — são deduzidos do comportamento. Nada casando dentro da tolerância de
saída vira `Shifting`, que **segura a última marcha por 600 ms** (o tempo de uma troca) e depois cai
para `N`.

Estados: `Engaged(n)` · `Shifting(last)` · `Neutral` · `Unknown`.

### Calibração — dois caminhos, uma grandeza

Os dois produzem rpm/km/h e caem na mesma lista editável.

**Aprender no carro**: velocidade constante acima de 25 km/h, botão só habilita com variação abaixo de
3 % em 2 s. Grava a **mediana** da janela, não a média — mediana ignora a fixação de GPS esquisita
ocasional sem precisar de rejeição de outlier.

**Relações manuais**:
```
d_mm = aro·25,4 + 2·largura·(perfil/100)
C_m  = π·d_mm/1000
rpm/km/h = marcha · diferencial · 1000 / (C_m · 60)
```
Referência verificada: 195/55R15 → C = 1,871 m; 1ª de 3,25 com diferencial 4,25 → ~123 rpm/km/h, ou
seja 3.000 rpm a ~24 km/h.

Na prática o manual serve para começar e o aprendizado para corrigir: pneu gasto, patinagem e erro de
catálogo entram na medida real.

---

## Odômetro

Velocidade do GPS integrada no tempo. Não é hodômetro de precisão — erro de GPS e perda de sinal entram
na conta — mas é a única fonte disponível.

Intervalos fora de **100 ms a 5 s** são descartados: abaixo do mínimo o ruído domina, e acima dele a
fixação anterior é velha demais para dizer o que aconteceu no meio. Sem isso, um túnel de dez minutos
viraria dez minutos de estrada andada parado.

---

## Combustível

### Dois contadores de consumo, de propósito

O consumo responde a duas perguntas com regras opostas:

| contador | papel | ao abastecer |
|---|---|---|
| `tankUsedLiters` | nível do tanque | **diminui** |
| `tripFuelLiters` | denominador da média | **só cresce** (zera com o parcial) |

Se fossem o mesmo número, somar 10 litros encolheria o denominador e faria a média **saltar sozinha**,
sem ninguém ter andado nada.

Consequência: encher ou somar litros mexe no nível e não na média; zerar o parcial zera a média e não
mexe no nível.

### Somar litros ≠ dizer quanto tem

São duas operações diferentes, e a aba TANQUE expõe as duas no mesmo campo:

| botão | operação | quando |
|---|---|---|
| `+ SOMAR` | `tankUsedLiters -= litros` | abastecimento — entrou combustível de verdade |
| `= TENHO` | `tankUsedLiters = tanque - litros` | correção — o nível é este, esqueça a estimativa |

`= TENHO` existe porque a estimativa erra: o app fica desligado enquanto o carro anda com o rádio em
outra fonte, alguém abastece sem registrar, a vazão configurada não é exatamente a do bico. Sem uma
forma de dizer o nível de verdade, o único jeito de acertar seria encher o tanque.

E `= TENHO` **não mexe na média** — corrigir o medidor não muda quantos quilômetros o carro já fez por
litro no caminho até aqui.

### A conta

```
cc/min = vazão_efetiva · nº de bicos · duty/100
```

O duty vem do canal de abertura de bicos, que já é a fração real de tempo aberto (ver
[protocolo.md](protocolo.md) sobre semissequencial).

### Por que um valor fixo de vazão serve

O bico é uma **válvula liga/desliga**. Não existe "abrir mais" — aberto, ele passa uma vazão fixa pelo
orifício. Acelerar não faz o bico jorrar mais: a ECU o mantém **aberto por mais tempo**. O "quanto se
acelera" já está no duty.

### Correção pela pressão diferencial

A vazão fixa depende da diferença de pressão entre a linha e o coletor, e escoamento por orifício vai
com a raiz dela:

```
vazão_real = vazão_nominal · √(ΔP_real / 3 bar)
```

**Neste carro isso importa, e foi medido.** Nos 20.895 frames de estrada com o motor girando:

| | |
|---|---|
| pressão da linha | 2,93–3,70 bar, média 3,30, **desvio 0,05** |
| MAP | −0,93 a −0,01 bar, desvio 0,265 |
| diferencial | 3,19–4,47 bar |

A linha fica praticamente cravada enquanto o MAP passeia — o regulador **não** é referenciado ao
coletor. √(4,47/3,19) = 1,18: a vazão real varia **18 % entre os extremos**, e o erro seria sempre para
o mesmo lado. A correção usa canais que a telemetria já entrega, então não custa configuração.

### Limitações conhecidas

- usa a **vazão nominal** do bico como base (a curva real do bico não temos);
- ignora o enriquecimento de partida;
- a conversão lb/h → cc/min usa densidade de gasolina (0,72 g/cc). Com etanol o volume é ~9 % maior para
  a mesma massa — quem usa etanol deve digitar direto em cc/min.

Por isso o tanque é do tipo "zerei ao abastecer". Um erro de 5 % em 40 litros são 2 litros: serve para
saber que está na reserva, não para chegar no lacrado.

**Calibração empírica**, que vale mais que qualquer estimativa: encha, zere, rode, e na próxima bomba
compare os litros reais com o que o app disse. Se ele disse 30 e você pôs 34, multiplique a vazão por
34/30.

---

## Médias de consumo

**Média da viagem**: parcial ÷ combustível do mesmo trecho. Só aparece depois de meio litro consumido —
nos primeiros metros a divisão explode (100 m gastando 5 ml daria 20 km/L).

**Instantânea**: velocidade ÷ vazão atual, as duas suavizadas com a **mesma constante de tempo**.
Filtrar só uma faria o quociente dar picos a cada mudança de regime.

Dois casos não são número:
- **parado** (abaixo de 5 km/h): km/L não significa nada;
- **corte na desaceleração**: o consumo é literalmente zero e a conta seria infinita — satura em 99,9 e
  a tela mostra `>99`, como os computadores de bordo de fábrica.

O duty zero entra na suavização de propósito: ignorá-lo faria a instantânea travar no último valor
gasto em vez de mostrar que o motor parou de consumir.

---

## Escala da barra de RPM

**Automática** (padrão), igual à FT: o teto é o RPM mais alto já registrado, com piso de 8.000 (a régua
1–8). Ele sobe sozinho e não desce.

Como não recua, um pico falso estragaria a escala para sempre — por isso um candidato só é promovido
depois de **3 frames seguidos** acima do teto atual (~0,2 s a 17 Hz): ruído elétrico não sustenta
rotação alta por esse tempo, motor subindo de giro sustenta. E há botão de zerar.

O aprendizado fica **parado até as preferências carregarem** do disco. A leitura do DataStore e a
chegada dos frames são as duas assíncronas: se um frame vencesse a corrida, o pico seria promovido
partindo de zero e gravaria por cima do valor real — que, por não recuar, estaria perdido para sempre.

**Manual**: corte, aviso de troca e escala digitados.
