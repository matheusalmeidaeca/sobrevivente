# Esqueleto humano + animações para o seu boneco (.obj → .g3dj)

Um script Python lê a malha `.obj` do seu boneco e gera um `.g3dj` com **esqueleto de 24 ossos, pesos de pele e 9 animações**.
Tudo roda no Termux. Não precisa de Blender nem de computador.

## Suposições que eu fiz (você não respondeu, então segui com estas)

- **Malha de entrada:** o boneco que já está no jogo (`jogador.g3dj`), exportado para `exemplo/boneco.obj`.
- **Eixos do .obj:** frente = **-Z**, cima = **+Y**, unidade = **metros**.
- **Pose do .obj:** em pé, braços pendurados ao lado do corpo, pernas retas.
- **Altura:** 1,834 m (medida na própria malha, ≈ 7,5 cabeças).

Se o seu `.obj` for diferente, mude só `malha.eixo_frente`, `malha.eixo_cima` e `malha.altura_alvo_m` no `config_humano.json`. Não precisa mexer no código.

## Como usar (Termux)

```bash
cd ~/estoque-v2/projeto_sobrevivente
unzip -o ~/storage/downloads/animacao_humana.zip
cd ferramentas/animacao
pip install numpy pillow          # só na primeira vez (pillow só para a prévia)
python3 gerar_g3dj.py             # gera saida/humano.g3dj (+ humano_meta.json). ~5 s
python3 teste_animacao.py         # testes automáticos. Tem que terminar em "TUDO PASSOU"
python3 preview_animacao.py       # gera saida/previa.png (poses de lado e de frente)
```

Para usar **outra malha**: `python3 gerar_g3dj.py --obj meu_boneco.obj --saida saida/meu.g3dj`.
Se alguma pose sair do limite, o script **para com erro** (código 2) e diz qual osso, qual eixo e em que instante. Ele não entrega pose proibida.

## O que colocar no jogo

1. Copie `saida/humano.g3dj` e `saida/humano_meta.json` para `android/assets/modelos/`.
   As texturas (`textura_jogador.png`, `textura_rosto.png`, `textura_maos_jogador.png`) já estão lá.
2. **Obrigatório:** em `Constantes.kt` mude `NUM_OSSOS_SHADER = 20` para **`24`** (ou mais).
   Sem isso o shader só enxerga 20 ossos e as pernas/pés ficam travados.
3. Copie `kotlin/ExemploAnimacaoHumana.kt` para `core/src/main/kotlin/com/sobrevivente/jogo/mundo/`.
   Ele já compila com o projeto (testei). Veja `ExemploAnimacaoHumana.criar()` e `escolher()`.
4. **Nomes dos ossos mudaram.** Antes: `ombro_*`. Agora: `clavicula_*` + `braco_*`, `antebraco_*`, `mao_*`, `dedos_*`, e nas pernas `coxa_*`, `canela_*`, `pe_*`, `ponta_*` (sufixo `_dir` / `_esq`).
   O osso `mao_dir` continua existindo, então o encaixe da arma funciona do mesmo jeito.

## Esqueleto (24 ossos)

`quadril` (raiz/pelve) → `coluna1`, `coluna2`, `coluna3` → `pescoco` → `cabeca`
`coluna3` → `clavicula` → `braco` (ombro) → `antebraco` (cotovelo) → `mao` (punho) → `dedos` (dedos agrupados)
`quadril` → `coxa` (quadril) → `canela` (joelho) → `pe` (tornozelo) → `ponta` (dedos do pé)

As juntas ficam nas alturas da tabela de Winter (fração da altura), ajustadas pelas medidas reais da malha (largura das pernas, braços, comprimento do pé).

## Limites articulares (graus) — rígidos

Estão em `config_humano.json → limites`. Convenção: **flexão +**, **rotação interna +**, **abdução +**.

| Junta | Limites |
|---|---|
| Pescoço (total, dividido entre `pescoco` e `cabeca`) | flexão 50, extensão 60, rotação 80 p/ cada lado, inclinação 40 |
| Coluna (total, dividido nos 3 segmentos) | flexão 60, extensão 25, inclinação 25, rotação 45 |
| Ombro (`braco`) | flexão 180, extensão 60, abdução 180, rot. interna 70, externa 90 |
| Clavícula (ritmo escápulo-umeral) | acima de 90° de elevação do braço a clavícula acompanha |
| Cotovelo | 0 a 145 (nunca dobra para trás) |
| Antebraço | pronação/supinação 80 |
| Punho | flexão 80, extensão 70 |
| Quadril (`coxa`) | flexão 120, extensão 20, abdução 45, adução 30, rotação 45 |
| Joelho | 0 a 135 (só dobra para trás) |
| Tornozelo | dorsiflexão 20, flexão plantar 50 |

Qualquer pose fora disso é **rejeitada** (o script para). Os limites valem para todos os quadros de todas as animações.

## Pesos de pele

- Até **4 ossos por vértice**, soma sempre **1**.
- Transição suave em ombro, quadril, joelho, cotovelo e pescoço (raio de cada osso em `raios_pesos`).
- **Filtro de superfície** (`filtro_superficie`): só a parte "de tubo" do braço/perna segue o osso. Isso evita que o casaco do lado do corpo seja arrastado quando o braço sobe, e que uma perna puxe a outra.
- `nitidez` (padrão 0,36): menor = junta mais firme; maior = mais macia (mas o casaco estica mais).

## Animações (9)

`parado` (respiração + balanço de peso), `andar`, `correr`, `agachar`, `pular`, `atacar`, `dano`, `morrer`, `interagir`.

- **Marcha:** toque de calcanhar → pé plano → impulso na bola do pé → balanço. Joelho levemente dobrado no contato, bacia sobe/desce e gira, ombros giram ao contrário, braços opostos às pernas, cabeça estável (pescoço compensa o tronco), cotovelos e cabeça com leve atraso (movimento secundário).
- **Sem escorregar:** o pé fica parado no chão durante o apoio (cálculo de IK nas pernas). `andar` e `correr` são "no lugar": o jogo move o personagem. Use `humano_meta.json → animacoes.andar.velocidade_m_s` (1,3 m/s) e `correr` (3,8 m/s). Em `AnimadorHumano.acompanharVelocidade()` o tempo da animação é escalado pela velocidade real. Assim o pé não patina.
- **Mistura:** 0,10 a 0,25 s com curva suave (ease-in/ease-out), em `AnimadorHumano`.

## O que foi testado automaticamente (`teste_animacao.py`)

1. **Limites:** 11.760 rotações checadas (24 ossos × todos os quadros × 9 animações), incluindo a soma da coluna e do pescoço+cabeça. **0 violações.**
2. **Pesos:** 97.211 vértices, soma = 1, no máximo 4 ossos por vértice.
3. **Quaternions:** todos unitários.
4. **Pé não desliza:** calcanhar/bola em contato andam no máximo 3,7 mm por quadro (andar), 2,8 mm (correr). Limite do teste: 5 mm.
5. **Pé não atravessa o chão:** pior caso −4,5 mm (limite 5 mm).
6. **Pernas nunca esticadas além do alcance:** 0 casos.
7. **Malha não atravessa o chão:** pior caso −13,7 mm (limite 20 mm). Na animação `morrer` o corpo é levantado automaticamente se encostar no chão.

## O que ainda precisa ser visto a olho

- **Casaco/capuz quando o braço sobe** (pular, atacar, dano): a malha tem o casaco colado ao braço. Na prévia sobrou um pouco de "véu" (triângulos esticados) na lateral. Para melhorar, mude `nitidez` (0,30 a 0,45) e rode de novo.
- **Cotovelo e joelho muito dobrados** (agachar, correr): confira se não "embrulha" (efeito papel de bala).
- **Pés:** o solado da malha tem espessura e o pé do .obj pode ter forma diferente da que o script mediu. Se o pé parecer flutuando ou afundando 1 cm, ajuste `pe.calcanhar_atras_m` / `pe.bola_a_frente_m` no config.
- **Naturalidade geral:** ritmo da corrida, balanço dos braços e o "peso" do pulo. Isso só se julga rodando no celular.
- **Nada disso foi rodado no celular ainda.** A prévia (`previa.png`) é um desenho de pontos, não o render do jogo.

## Arquivos

| Arquivo | Para quê |
|---|---|
| `config_humano.json` | Limites, proporções, raios dos pesos, parâmetros da marcha. Tudo ajustável aqui. |
| `gerar_g3dj.py` | Script único: lê o .obj e escreve o .g3dj. |
| `teste_animacao.py` | Testes automáticos (lê só o .g3dj, não usa o gerador). |
| `preview_animacao.py` | Imagem de prévia das poses. |
| `exportar_obj_do_jogo.py` | Converte o `jogador.g3dj` do jogo em `.obj` (foi assim que fiz o `exemplo/boneco.obj`). |
| `kotlin/ExemploAnimacaoHumana.kt` | Exemplo com `AnimationController` e mistura suave. |
| `saida/` | `humano.g3dj`, `humano_meta.json`, `previa.png` já gerados. |
