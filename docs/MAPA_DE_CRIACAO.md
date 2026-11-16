# MAPA DE CRIAÇÃO v2.15 — Jogo 3D de Sobrevivência (Kotlin + Python de bastidor / Android)

**Versão do documento:** 2.15 | **Data:** 16/11/2026 | **Substitui:** v2.14
**Nome provisório:** PROJETO_SOBREVIVENTE (nome final: em aberto)

> Cole este arquivo inteiro no início de qualquer conversa com uma IA.
> Ele é a **fonte única da verdade**. O que não estiver aqui, a IA **pergunta**; ela não decide.

### Mudanças da v2.14 para a v2.15
- **F3 aprovado pelo dono (vídeo, 16/11/2026):** boneco e zumbi animados no jogo; a correção da pose de repouso dos ossos no `.g3dj` funcionou (`GRAVAR_POSE_INVERSA = False`).
- **Animação mais humana (F2b), pedido do dono:** esqueleto de **18 ossos** (novos: `pescoco`, `ombro_esq/dir`, `mao_esq/dir`); marcha com curvas de articulação humanas (contato do calcanhar, apoio, impulso, balanço), giro e queda da pelve, contra-giro dos ombros, balanço dos braços com atraso (cotovelo e mão seguem o ombro), cabeça estabilizada; parado com respiração, troca de peso e olhar; tiro, ataque e morte com sobreposição (cada osso reage com atraso). Zumbi com perna esquerda arrastando, cabeça pendendo, braço desgovernado e espasmos.
- Ciclos mais longos: parado 90, andar 36 (zumbi 42), correr 22 (zumbi 26), atirar 24, atacar 36, morrer 54 (zumbi 60) quadros a 30 fps.
- No jogo: velocidade da animação acompanha a velocidade real (joystick) para os pés não patinarem (`JOGADOR_ANIM_REF_*`); transição entre animações de 0,18 s; `numBones` = 20.
- Orçamento 24.3 (ossos ≤ 25): 18 ossos OK. Alterados: `animacao.py`, `Constantes.kt`, `Jogador.kt`, `ModeloAnimado.kt`, modelos `.g3dj`.

### Mudanças da v2.13 para a v2.14
- **F2 rodou no CI (log verde, 16/11/2026):** orçamento da seção 24.3 OK (1050 triângulos, 13 ossos, 512×512, 1,80 m, origem nos pés) e pele do Blender idêntica à conta em Python (0,000000 m). Falta o dono aprovar as poses na folha `previa_python.png`.
- **Decidido pelo dono:** formato de exportação **`.g3dj`** (carregado com `G3dModelLoader`, sem dependência extra).
- **F3 entregue (em teste):** `jogador.g3dj` e `zumbi.g3dj` (+ texturas) em `android/assets/modelos/`; jogador e zumbi usam o modelo animado no lugar das cápsulas, com a animação trocando conforme o estado. Se o arquivo faltar ou falhar ao carregar, o jogo **volta sozinho para a cápsula**. Alterados: `Constantes.kt`, `Jogador.kt`, `Inimigo.kt`, `Mundo.kt`, `TelaJogo.kt`. Novo: `mundo/ModeloAnimado.kt` (fora da seção 9, a confirmar).
- Escolhas do F3 (a confirmar): jogador usa `correr` com o joystick ≥ 60% e `andar` abaixo; `atirar` reinicia a cada tiro; zumbi persegue com `correr` a 0,55× e ataca com `atacar`; o corpo do zumbi fica 5 s no chão e some; o zumbi tem tinta (laranja ao perseguir, vermelho ao atacar) e pisca claro ao levar tiro; clarão no cano mais perto das mãos.
- **Correção no `.g3dj` (1º teste no aparelho):** o corpo saiu esticado/quebrado porque `bones` do `.g3dj` deve levar a pose de repouso (`T(+pivô)`), que o LibGDX inverte ao carregar; a v1 gravava a inversa. Constante `GRAVAR_POSE_INVERSA` em `exportar.py` (NÃO TENHO CERTEZA da correção; se ainda sair errado, inverter a constante).
- `DefaultShader` configurado com 16 ossos (`numBones`), pois o boneco tem 13 (na v2.15: 18 ossos e `numBones` = 20).

### Mudanças da v2.12 para a v2.13
- **F2 entregue (em teste):** esqueleto de 13 ossos, pesos de pele (no máximo 2 ossos por vértice), 5 animações por personagem e exportação em `.g3dj`. Novos arquivos em `ferramentas/blender/`: `animacao.py`, `exportar.py`, `gerar_boneco.py`, `previa_python.py`, `LEIAME.md`. Alterado: `.github/workflows/gerar-modelo.yml` (etapa `f2`).
- **Arquivos fora da estrutura da seção 9 (a confirmar):** `animacao.py` e `previa_python.py` (a seção 9 só previa `gerar_boneco.py`, `exportar.py` e `LEIAME.md`). Motivo: a matemática em Python puro é testável sem Blender.
- **Correção no F0:** as faces de braços e pernas saíam viradas para dentro (ordem dos anéis descendente). Corrigido em `Geometria.kt` (o sentido dos triângulos agora depende de o loft subir ou descer). Sem mudança de dimensões nem de UV.
- O zumbi tem `atacar` (loop) no lugar de `atirar`, pedido para o F3 (a confirmar).
- Formato de exportação: **proposta g3dj** (JSON nativo do LibGDX, sem dependência extra). A seção 19 continua com a decisão a confirmar.

### Mudanças da v2.11 para a v2.12
- Nova **seção 25**: caminhos, nomes e comandos **reais e já testados** para enviar arquivos ao GitHub pelo Termux (repositório, pasta local, pasta de downloads, sequência de comandos, erros já vistos, o que a IA não deve inventar).
- Nova regra 20 na seção 2: comandos de envio ao GitHub saem **somente** da seção 25.
- Sem mudança de código nem de marco.

### Mudanças da v2.10 para a v2.11
- **F0 enviado ao GitHub** (commit `ebb5ef4`); o workflow "Gerar modelo" ainda **não foi rodado/confirmado** pelo dono.
- **Build verde confirmado** (Actions, run #9) para o código dos Marcos 1.7 e 1.8 (o run #8 tinha falhado; o #9 corrigiu).
- O dono concordou em avançar para o **Marco F1** (Blender sem tela no CI). **F1 em teste** (não executado pela IA: sem Blender no ambiente da conversa).
- **Blender: a proposta anterior (4.2 LTS) foi trocada.** O 4.2 LTS saiu de suporte em julho/2026; a LTS ativa é a **4.5** (suporte até julho/2027). Proposta (a confirmar): **Blender 4.5.11 LTS**, baixado do site oficial (`download.blender.org`) como tarball Linux x64.
- `gerar-modelo.yml` agora tem uma **escolha de etapa** (`f0` ou `f1`) ao rodar; dois jobs separados.
- Novos: `ferramentas/blender/teste_f1.py` e `ferramentas/blender/LEIAME.md`. Alterado: `.github/workflows/gerar-modelo.yml`.
- **Arquivo fora da estrutura da seção 9 (a confirmar):** `ferramentas/blender/teste_f1.py` (a seção 9 só prevê `gerar_boneco.py`, `exportar.py` e `LEIAME.md` nessa pasta).
- Escolhas (a confirmar): render com **Cycles em CPU** (o EEVEE precisa de GPU/OpenGL, que o runner sem tela não garante); sem denoise; bibliotecas do sistema instaladas por `apt-get` antes de rodar o Blender; versão do Blender gravada em `versao_blender.txt` e no log.

### Mudanças da v2.9 para a v2.10
- O dono deu **"ok"** para iniciar o **Marco F0** (gerador Kotlin do boneco). **F0 em teste** (não compilado pela IA: sem compilador Kotlin no ambiente da conversa; só um protótipo descartável em Python validou geometria, UV e textura).
- Novos: `ferramentas/gerador-boneco-kotlin/` (`settings.gradle`, `build.gradle`, `LEIAME.md` e, em `src/main/kotlin/gerador/`: `Parametros.kt`, `Geometria.kt`, `Textura.kt`, `Previa.kt`, `ExportadorObj.kt`, `Main.kt`) e `.github/workflows/gerar-modelo.yml`.
- **Arquivos fora da estrutura da seção 9 (a confirmar):** os 6 `.kt` acima (a seção 9 só prevê a pasta).
- Escolhas (a confirmar): projeto Gradle **separado** do jogo (não entra no `settings.gradle` da raiz); **frente do modelo = -Z** (bate com yaw 0, sem girar no jogo); esquerda = -X; 10 lados por anel; **1.050 triângulos** (orçamento 2.000); 6 peças-grupo com textura atlas 512x512 gerada por código; saída `boneco.obj/.mtl` (jogador) **e** `zumbi.obj/.mtl` (mesma malha, outra textura); `preview.png` único com 6 quadros; `relatorio.txt`; normais planas por triângulo; a pose do zumbi continua igual à do jogador até o F2 (animações).

### Mudanças da v2.8 para a v2.9
- O dono informou que o **Marco 1.8 "deu certo"** (aprovação dele; as caixas da seção 17 continuam para o dono marcar).
- **Foco agora: estética dos personagens** (pipeline da seção 24). Os marcos F0 a F3 passam a correr **agora**, em paralelo aos 1.x; a história (Fase 2) continua reservada.
- **Decisões do dono (seção 20):** estilo de arte **low-poly realista com proporções humanas**; inimigos **humanos infectados (zumbis)**; acabamento do boneco com **textura simples de roupa 512x512** (gerada por código, sem pintura à mão nem asset externo).
- Proposta da IA (a confirmar): o F0 passa a gerar também **UVs** e a textura PNG; jogador e zumbi usam a **mesma malha-base** com texturas e poses diferentes (ver seção 24.2).

### Mudanças da v2.7 para a v2.8
- O dono pediu a **próxima fase** de novo: Marco 1.7 tratado como entregue (aprovação do dono a confirmar; nenhuma caixa marcada pela IA).
- **Marco 1.8 em teste:** mapa de teste com paredes, colisão com deslizamento, tiro bloqueado por parede, câmera que não atravessa parede e minimapa funcional. Novo: `mundo/Colisao.kt` (já previsto na seção 9). Alterados: `Constantes.kt`, `Jogador.kt`, `Inimigo.kt`, `Mundo.kt`, `CameraTerceiraPessoa.kt`, `Minimapa.kt`, `Hud.kt`, `TelaJogo.kt`.
- Mapa de teste: prédio ao norte (z de -24 a -8, x de -14 a 14) com paredes de 0,4 m e **um vão de 3 m na parede sul** (a "porta"), 2 pilares dentro e 2 caixas fora; chão do prédio mais escuro. Os 3 inimigos ficam dentro do prédio; os itens da maleta ficam ao sul, a céu aberto.
- Escolhas (a confirmar): "portas" são **vãos** sem folha que abre; os inimigos acordam pela distância mesmo atrás de parede (sem linha de visão) e, sem pathfinding, podem ficar presos em quinas; tiro bloqueado por qualquer parede; câmera se aproxima do jogador ao encostar em parede (mínimo de 20% da distância); minimapa com norte fixo (-Z para cima), mostra paredes, jogador (branco) e inimigos vivos (vermelho); lista de paredes em `Constantes.PAREDES`.

### Mudanças da v2.6 para a v2.7
- O dono pediu a **próxima fase**: Marcos 1.5 e 1.6 tratados como entregues (aprovação do dono a confirmar; nenhuma caixa marcada pela IA). O código do 1.6 (morte e reinício) já estava no `jogo_marco_1_6.zip`.
- **Marco 1.7 em teste:** maleta 2 x 4, coleta com INTERAGIR, uso de cura e de munição. Novos: `inventario/Item.kt`, `inventario/Maleta.kt`, `mundo/ItemMundo.kt`. Alterados: `Constantes.kt`, `ControlesToque.kt`, `Hud.kt`, `TelaJogo.kt`, `Mundo.kt`, `Jogador.kt` (novo `curar`), `Arma.kt` (novo `adicionarReserva`).
- **Arquivo fora da estrutura da seção 9 (a confirmar):** `mundo/ItemMundo.kt` (item largado no chão, cubo colorido).
- Escolhas (a confirmar): usar MUNICAO na maleta joga a pilha inteira na reserva da arma; item só é coletado se couber **inteiro**; o reinício esvazia a maleta e devolve os itens ao chão; usar CURA com a vida cheia não consome o item; Voltar com a maleta aberta fecha a maleta (não pausa); textos novos sem acento (a fonte padrão não foi verificada com acentos).
- Itens de teste no chão (atrás do ponto inicial, z positivo, longe dos inimigos): 8 curas e 2 munições de 12 (9 slots > 8, para testar "Maleta cheia").

### Mudanças da v2.5 para a v2.6
- **Marco 1.4 aprovado** pelo vídeo de teste (cubos, clarão, marcador de acerto, munição na tela).
- **Marco 1.5 em teste:** inimigos com máquina de estados (seção 13), dano ao jogador e morte a tiros. Novo: `mundo/Inimigo.kt`. Alterados: `Constantes.kt`, `Jogador.kt` (agora tem vida), `Mundo.kt`, `TelaJogo.kt`.
- **`mundo/Alvo.kt` removido** (os cubos de teste saíram; os inimigos assumem o papel de alvo). Apagar com `git rm`.
- Escolhas (a confirmar): o primeiro golpe do inimigo sai ao chegar a 1,2 m; a cor do inimigo muda por estado (verde parado, laranja perseguindo, vermelho atacando) só para facilitar o teste; com a vida do jogador em 0 os inimigos ficam parados (a morte é do 1.6); `INIMIGOS_ATIVOS` em `Constantes.kt` escolhe 1 a 3 inimigos.

### Mudanças da v2.4 para a v2.5
- **Marco 1.3 aprovado** pelo vídeo de teste (HUD visível: barra de vida, minimapa, mira, 3 botões, ícone de maleta no joystick).
- **Marco 1.4 em teste:** tiro hitscan, munição, recarga, alvos fixos e feedback. Novos: `mundo/Arma.kt`, `mundo/Alvo.kt`. Alterados: `Constantes.kt`, `ControlesToque.kt`, `Jogador.kt`, `Mundo.kt`, `Hud.kt`, `TelaJogo.kt`.
- **Arquivo fora da estrutura da seção 9 (a confirmar):** `mundo/Alvo.kt`, temporário, para o cubo de teste. Sai ou vira o `Inimigo` no Marco 1.5.
- Do Marco 1.4 também entrou o comportamento da seção 11: o jogador encara a mira por 0,5 s depois de atirar.

### Mudanças da v2.3 para a v2.4
- **Marco 1.2 aprovado** pelo dono (joystick, movimento relativo à câmera, câmera 3ª pessoa).
- **Marco 1.3 em teste:** HUD base. Novos: `hud/Hud.kt`, `hud/BotoesAcao.kt`, `hud/BarraVida.kt`, `hud/Minimapa.kt`, `hud/OverlayDebug.kt`. Alterados: `Constantes.kt`, `ControlesToque.kt`, `Joystick.kt`, `TelaJogo.kt`.
- **Escolha técnica (a confirmar):** o HUD do 1.3 é desenhado com `ShapeRenderer`/`SpriteBatch` em pixels, com toque tratado em `ControlesToque`, em vez de `Scene2D` com `Stage` (seções 6 e 10.1). Motivo: o joystick aprovado no 1.2 já funciona assim e o multitouch por ID de ponteiro fica num só lugar.
- Barra de vida no canto superior esquerdo; minimapa no superior direito; overlay de debug aparece abaixo da barra de vida.

### Mudanças da v2.2 para a v2.3
- **Marco 1.1 aprovado** pelo vídeo de teste (chão xadrez, cápsula com nariz, luz, câmera orbitando, 84 a 120 FPS). Pendente: confirmar a **tela cheia** (a gravação saiu em retrato e não permite ver a faixa preta lateral).
- **Marco 1.2 em teste:** joystick, movimento relativo à câmera, câmera de 3ª pessoa e giro por arrasto. Novos arquivos: `entrada/ControlesToque.kt`, `hud/Joystick.kt`, `camera/CameraTerceiraPessoa.kt`.
- Seção 16: novas armadilhas 16 e 17.

### Mudanças da v2.1 para a v2.2
- **Marco 1.0 concluído e testado** no aparelho alvo (app abre, 120 FPS, gira a tela e Home/voltar sem fechar). Versões da seção 6 **congeladas**.
- Decisão registrada: Gradle 8.5 instalado pelo CI, sem wrapper.
- Ambiente de trabalho do dono preenchido (Termux + GitHub Actions).
- Marco 1.1 em teste; inclui a correção da faixa preta lateral (recorte da câmera) e câmera provisória orbitando.

### Mudanças da v2.0 para a v2.1
- **Python entra como a ÚNICA linguagem adicional**, somente como ferramenta de bastidor (Blender sem tela no GitHub Actions). O jogo continua 100% Kotlin e o Python nunca vai dentro do APK.
- Nova **seção 24** (pipeline de ativos, marcos F0 a F3, orçamento do boneco, regras para scripts Python).
- Novas pastas `ferramentas/`, segundo workflow `gerar-modelo.yml`, regra de linguagens permitidas/proibidas e item de autoverificação para Python.
- GLSL e todas as outras linguagens: **adiados/proibidos** sem decisão do dono.
- Nova decisão em aberto: como carregar modelo com esqueleto no LibGDX (gdx-gltf ou outra via).

### Mudanças da v1 para a v2 (resumo)
- Removida a ambiguidade de build: Groovy DSL fixo, ABI arm64-v8a definida, natives do LibGDX tratados.
- Adicionado: protocolo de início de sessão, glossário, convenções de coordenadas/unidades/nomes, configuração Android obrigatória, armadilhas conhecidas, orçamento de desempenho, valores iniciais de combate/inimigo/inventário, especificação de toque (multitouch), CI detalhado, autoverificação da IA, registro de licenças.
- Nome do pacote deixou de conter "RE" (evita ligação com marca de terceiros).
- Marcos reorganizados de 7 para 9, com teste de aceite objetivo em cada um.
- Botões da Fase 1 reduzidos a 3 funcionais (sem botões "mortos").
- "Mapa fundido" marcado como interpretação a confirmar (ver seção 19).

---

## ÍNDICE
1. Como usar e protocolo de início de sessão
2. Regras obrigatórias para a IA
3. Glossário
4. Visão do jogo
5. Hardware alvo e orçamento de desempenho
6. Stack travada
7. Configuração Android obrigatória
8. Convenções (coordenadas, unidades, nomes)
9. Estrutura de pastas
10. Controles e HUD (especificação de toque)
11. Câmera e movimento
12. Combate (valores iniciais)
13. Inimigo (valores iniciais)
14. Maleta / inventário
15. CI (GitHub Actions)
16. Armadilhas conhecidas
17. Marcos da Fase 1 com teste de aceite
18. Fase 2 (reservada)
19. Decisões e pontos em aberto
20. Registro de decisões
21. Formato de resposta e autoverificação da IA
22. Estado atual
23. Registro de licenças de assets
24. Pipeline de ativos (Kotlin + Python/Blender)
25. Ambiente do dono: Termux, GitHub e caminhos reais

---

## 1. COMO USAR E PROTOCOLO DE INÍCIO DE SESSÃO

**Para o dono do projeto:** cole este arquivo + o código do arquivo que você quer mexer (se houver) + diga o marco atual.

**Para a IA, antes de escrever qualquer código, responda em no máximo 8 linhas:**
1. O marco atual (seção 22).
2. A stack e as versões (seção 6) em uma linha.
3. Três regras da seção 2 que mais se aplicam à tarefa.
4. O que você NÃO sabe e precisa que eu cole (arquivos, logs).
5. Aguarde meu "ok" só se houver dúvida; sem dúvida, prossiga.

**Nunca presuma que um arquivo existe.** Se precisa ver um arquivo, peça para eu colar. Não reconstrua de memória código que "provavelmente" já escrevi.

---

## 2. REGRAS OBRIGATÓRIAS PARA A IA

### Honestidade
1. **Não invente** API, método, parâmetro, versão, comando ou nome de classe. Se não tiver certeza, escreva "NÃO TENHO CERTEZA" e diga como eu posso conferir (ex.: ver o erro do build).
2. Não afirme que algo "funciona". Diga "deve funcionar; teste e me diga o resultado".
3. Se uma informação deste documento parecer errada ou conflitar com outra, **avise**; não escolha em silêncio.

### Escopo
4. **Um marco por vez** (seção 17). Não adiante funcionalidades futuras, nem "aproveite para melhorar".
5. **Não troque a stack** (seção 6): nada de Unity, Godot, Unreal, gdx-bullet, gdx-freetype, gdx-gltf, Box2D, Ashley/ECS etc. na Fase 1, a menos que eu decida e registre na seção 20. **Linguagens permitidas:** Kotlin (jogo e gerador Kotlin), **Python (somente ferramentas de ativos, seção 24)** e arquivos de configuração (Groovy, YAML, XML, Bash). **Proibido sem decisão minha:** C++, Rust, Lua, GLSL (adiado), Java escrito à mão e qualquer outra.
6. **Não mude versões** depois de travadas. Se o build quebrar por versão, mostre o erro, proponha a correção mínima e espere meu ok.
7. **Não renomeie nem mova** arquivos fora da estrutura da seção 9 sem me avisar.
8. Modo história é a Fase 2. **Não implemente nem planeje história agora.**

### Código
9. **Arquivos completos**, com o caminho completo na primeira linha como comentário. Proibido "...resto igual...", "// código existente" ou trechos soltos.
10. Siga as **convenções da seção 8** (nomes, unidades, coordenadas). Todos os números "mágicos" vão em `Constantes.kt`.
11. **Zero alocação por frame** em `render()`/`update()` (reutilize `Vector3`, `Matrix4`, `Ray` etc.). Ver seção 5.
12. Todo recurso descartável (`Model`, `ModelBatch`, `Stage`, `SpriteBatch`, `ShapeRenderer`, `BitmapFont`, `Texture`) deve ter `dispose()` chamado.
13. Não use APIs depreciadas se houver alternativa na versão travada. Se usar, avise.
14. Antes de entregar, rode a **autoverificação** (seção 21).

### Legal e conteúdo
15. **Nada de propriedade intelectual de terceiros**: nenhum nome, personagem, logo, ícone, mapa, som ou texto oficial de Resident Evil, Free Fire ou outro jogo, **nem em código, strings, nome de pacote, nome do app ou comentários**. As referências na seção 4 são só conceito de organização de tela e gênero.
16. Arte e áudio: **placeholders gerados por código** na Fase 1. Se um dia entrar asset externo, só com licença livre registrada na seção 23.

### Entrega
17. Português do Brasil. Passos curtos, comandos prontos para colar. Comentários no código em PT-BR.
18. Termine **toda** resposta com o RELATÓRIO (seção 21).
19. Se algo falhar, peça o **log completo** (começo do erro, não só a última linha). Não reescreva o projeto para corrigir uma linha.
20. **Envio ao GitHub:** use **somente** os caminhos, nomes e comandos da **seção 25**. Não invente pasta, usuário, remoto, token, SSH, `gh` nem outro jeito de enviar. Se faltar um dado, pergunte.

---

## 3. GLOSSÁRIO (para ninguém interpretar errado)

| Termo | Significa neste projeto |
|---|---|
| **Maleta** | Inventário do jogador (tela de itens em grade) |
| **Botão central** | O botão no centro do joystick (o "knob"), que também é o atalho da maleta |
| **HUD** | Tudo que fica sobre o jogo 3D: joystick, botões, vida, minimapa, munição |
| **Marco** | Etapa pequena com teste de aceite (seção 17) |
| **Fase 1** | Parte básica jogável, sem história |
| **Fase 2** | Modo história (só depois que eu disser) |
| **Hitscan** | Tiro instantâneo por raio (sem projétil físico) |
| **dp** | Pixel independente de densidade. Em LibGDX: `px = dp * Gdx.graphics.density` |
| **Placeholder** | Forma simples (cápsula, cubo) no lugar da arte final |
| **Dono** | Eu, o criador do projeto, quem decide e testa no celular |

---

## 4. VISÃO DO JOGO (fechada)

- **Gênero:** sobrevivência/terror em **3ª pessoa, 3D low-poly**, para Android.
- **Modo:** **MODO HISTÓRIA** (campanha). **NÃO é Battle Royale**: não há zona que fecha, queda de paraquedas, dezenas de jogadores nem matchmaking.
- **Multiplayer/online:** **não**. O app **não usa internet** (sem permissão `INTERNET`).
- **Ordem:** primeiro o básico (Fase 1). História só na Fase 2.
- **Inspirações (apenas conceito):**
  - Sobrevivência: munição escassa, inventário limitado, inimigos que ameaçam.
  - **HUD/controles de toque:** convenção de tiro mobile (joystick à esquerda, botões de ação à direita, minimapa no canto, barra de vida).
- **Maleta fundida ao joystick:** o **botão central do joystick** é também o atalho da maleta.
  - Arrastar o botão = mover o personagem.
  - Toque curto no botão (sem arrastar) = abrir/fechar a maleta.

---

## 5. HARDWARE ALVO E ORÇAMENTO DE DESEMPENHO

**Dispositivo de teste:** Xiaomi Redmi Note 13 4G (Snapdragon 685, 8 GB RAM, arquitetura **arm64-v8a**).

| Item | Limite |
|---|---|
| FPS | **≥ 30 em média por 60 s**, nunca abaixo de 25 de forma sustentada |
| Triângulos visíveis | ≤ 30.000 |
| Draw calls | ≤ 60 |
| Textura | ≤ 1024×1024, formatos comuns (PNG) |
| Inimigos simultâneos | ≤ 15 |
| Colisores estáticos | ≤ 64 |
| Alocação por frame | **0** em `render()`/`update()` |
| Antialiasing (MSAA) | desligado |
| OpenGL | GLES 2.0 (`useGL30 = false`) |

**Medição:** contador de FPS (`Gdx.graphics.framesPerSecond`) em overlay de debug (seção 10.6).

O aparelho alvo chegou a 120 FPS em cena vazia (tela de 120 Hz). A meta de 30 FPS continua sendo o piso de aceite.

---

## 6. STACK TRAVADA

| Item | Valor |
|---|---|
| Linguagem do jogo | Kotlin |
| Linguagem de ferramentas | **Python** (só bastidor, seção 24) |
| Engine | LibGDX: 3D com `g3d`, HUD com `Scene2D` |
| Módulos | `core` (lógica) + `android` (lançador) |
| Gradle DSL | **Groovy** (`build.gradle`), fixo |
| CI | GitHub Actions → **APK debug** |
| JDK | 17 |
| ABI | **arm64-v8a** obrigatória (armeabi-v7a opcional, só se eu pedir) |

**Versões (CONGELADAS em 15/11/2026; build verde no GitHub Actions e app testado no aparelho alvo):**

| Componente | Versão |
|---|---|
| LibGDX | 1.12.1 |
| Kotlin | 1.9.22 |
| Android Gradle Plugin | 8.2.2 |
| Gradle | 8.5 |
| compileSdk / targetSdk | 34 |
| minSdk | 24 |
| JVM target (Kotlin e Java) | 17 |

> Provado no Marco 1.0: build verde e app abrindo no aparelho alvo. Qualquer mudança exige decisão do dono, registrada na seção 20.

**Dependências permitidas na Fase 1:** `gdx`, `gdx-backend-android`, `gdx-platform` (natives), `kotlin-stdlib`. **Nada além disso.**

**Ferramentas de ativos (seção 24), a CONFIRMAR no marco F1:**

| Componente | Valor |
|---|---|
| Blender (sem tela) | **4.5.11 LTS (proposta, a confirmar; o 4.2 LTS saiu de suporte em julho/2026)** |
| Python do Blender | o que vem embutido no Blender (usado para `bpy`) |
| Python do sistema no CI | só se algum script fora do Blender precisar; versão a confirmar |
| Formato de saída com esqueleto | **em aberto** (seção 19) |

> O Python **nunca** entra no APK. Ele só gera arquivos no CI e na pasta `ferramentas/`.

**Observação importante:** o projeto é escrito **à mão** (não há como rodar o gerador gdx-liftoff no celular). Por isso a seção 16 lista as armadilhas de configuração.

---

## 7. CONFIGURAÇÃO ANDROID OBRIGATÓRIA

**AndroidManifest.xml**
- Sem `package=` (usa `namespace` no Gradle, padrão do AGP 8).
- `<uses-feature android:glEsVersion="0x00020000" android:required="true"/>`
- **Nenhuma permissão** (sem INTERNET, sem armazenamento, sem câmera).
- `android:screenOrientation="sensorLandscape"` (aceita os dois lados da paisagem).
- `android:configChanges="keyboard|keyboardHidden|navigation|orientation|screenSize|screenLayout|uiMode|density"` na Activity (evita recriar o jogo ao girar/redimensionar).
- `android:exported="true"` na Activity principal (obrigatório com targetSdk 31+).
- `android:hardwareAccelerated="true"`.
- `android:label` = nome provisório do app (sem marca de terceiros).

**AndroidApplicationConfiguration (no lançador)**
```
useGL30 = false
numSamples = 0
useAccelerometer = false
useCompass = false
useImmersiveMode = true
useWakelock = true   (tela não apaga durante o jogo)
```

**Tela com recorte (furo da câmera):** o HUD respeita **margem segura de 24 dp** nas laterais e no topo. Nada importante encostado na borda. A Activity define `layoutInDisplayCutoutMode = SHORT_EDGES` (API 28+, com verificação de versão) para o jogo ocupar a tela toda, inclusive ao redor do furo.

**Gradle (android):** `namespace`, `applicationId` iguais ao pacote base; `ndk { abiFilters "arm64-v8a" }`; `buildFeatures` mínimo; `debuggable` no tipo debug.

**Pacote e applicationId (provisório):** `com.sobrevivente.jogo`

---

## 8. CONVENÇÕES

### 8.1 Coordenadas e unidades (a fonte mais comum de bugs)
- Sistema **destro, Y para cima**. Chão em `y = 0`.
- **1 unidade = 1 metro.**
- **Yaw 0°** = olhando para **−Z** (padrão do LibGDX). Yaw aumenta **anti-horário visto de cima** (rotação em torno de +Y).
- **Pitch positivo** = olhando para cima.
- Todos os ângulos no código em **graus** (converta com `MathUtils.degreesToRadians` quando a API exigir radianos).
- Coordenadas de **toque**: `Gdx.input.getY()` tem origem no **topo**; `Stage` usa origem **embaixo**. Sempre converter (seção 16).
- Lógica de jogo (colisão, IA) trabalha no **plano XZ**; o Y só para altura visual.

### 8.2 Tempo
- `delta` limitado a **no máximo 1/30 s** (`minOf(Gdx.graphics.deltaTime, 1f/30f)`) para evitar "teleportes" após travadas.
- Movimento e IA multiplicados por `delta`. Nada dependente de FPS.

### 8.3 Nomes
- Identificadores (classes, funções, variáveis) em **português sem acentos**, `camelCase` para variáveis/funções e `PascalCase` para classes (ex.: `Jogador`, `velocidadeMovimento`).
- Nomes de APIs do LibGDX permanecem em inglês.
- Constantes em `Constantes.kt`, em `UPPER_SNAKE_CASE` (ex.: `VELOCIDADE_JOGADOR`).
- Arquivos: um tipo público principal por arquivo, nome do arquivo = nome da classe.
- Logs: `Gdx.app.log("TAG", "mensagem")` com TAG em maiúsculas por módulo (`"JOGADOR"`, `"HUD"`, `"IA"`).

### 8.4 Estilo
- Funções curtas (alvo ≤ 40 linhas). Sem `!!` (use verificações seguras). Sem `lateinit` desnecessário.
- Separar **lógica** (`mundo/`) de **desenho** (`hud/`, renderização). Lógica não chama API de desenho.
- Estado do jogador/inventário em `data class`/classes simples (facilita salvar na Fase 2, **sem implementar salvamento agora**).

---

## 9. ESTRUTURA DE PASTAS (fixa)

```
projeto/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── .gitignore
├── .github/workflows/build-apk.yml
├── .github/workflows/gerar-modelo.yml   (manual; marcos F0 a F2)
├── core/
│   ├── build.gradle
│   └── src/main/kotlin/com/sobrevivente/jogo/
│       ├── Jogo.kt                    (ApplicationListener / Game)
│       ├── Constantes.kt              (todos os números ajustáveis)
│       ├── telas/
│       │   └── TelaJogo.kt
│       ├── mundo/
│       │   ├── ModeloAnimado.kt       (fora do plano original; carrega .g3dj e toca animações; marco F3)
│       │   ├── Mundo.kt               (cena, colisores, atualização)
│       │   ├── Jogador.kt
│       │   ├── Inimigo.kt             (Marco 1.5)
│       │   ├── Colisao.kt             (círculo x AABB no plano XZ)
│       │   ├── Arma.kt                (Marco 1.4)
│       │   └── ItemMundo.kt           (Marco 1.7; a confirmar)
│       ├── camera/
│       │   └── CameraTerceiraPessoa.kt
│       ├── entrada/
│       │   └── ControlesToque.kt      (ponteiros, zonas, multitouch)
│       ├── inventario/
│       │   ├── Item.kt
│       │   └── Maleta.kt              (Marco 1.7; lógica sem desenho)
│       └── hud/
│           ├── Hud.kt
│           ├── Joystick.kt
│           ├── BotoesAcao.kt
│           ├── BarraVida.kt
│           ├── Minimapa.kt
│           ├── TelaMaleta.kt          (desenho da grade)
│           └── OverlayDebug.kt
├── android/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── kotlin/com/sobrevivente/jogo/android/LancadorAndroid.kt
├── ferramentas/
│   ├── gerador-boneco-kotlin/     (JVM Kotlin: gera OBJ + preview; marco F0)
│   └── blender/
│       ├── gerar_boneco.py        (Python/bpy; marco F2)
│       ├── exportar.py
│       ├── animacao.py            (fora do plano original; Python puro; marco F2)
│       ├── previa_python.py       (fora do plano original; Pillow + numpy; marco F2)
│       └── LEIAME.md
└── docs/
    └── MAPA_DE_CRIACAO.md             (este arquivo)
```

Arquivos só são criados no marco indicado. Não crie arquivos "para o futuro".

**.gitignore mínimo:** `build/`, `.gradle/`, `.idea/`, `local.properties`, `*.apk`, `*.aab`, `*.keystore`, `*.jks`, `.DS_Store`, `captures/`, `*.blend1`, `ferramentas/**/saida/`.

Modelos aprovados entram no jogo em `android/assets/modelos/` (commit manual, **só depois do aceite**). A IA confirma o caminho de assets no Marco 1.0.

---

## 10. CONTROLES E HUD (especificação de toque)

### 10.1 Princípios
- **Multitouch obrigatório:** joystick + câmera + botão ao mesmo tempo (mínimo 3 ponteiros).
- Cada toque é **dono de uma função** pelo **ID do ponteiro** desde o `touchDown` até o `touchUp`. Um dedo que começou no joystick **nunca** vira câmera, mesmo que arraste para a direita.
- Alvo mínimo de toque: **48 dp** (botões menores ficam com área de toque ampliada a 48 dp).
- `InputMultiplexer`: **1º** `Stage` do HUD (botões), **2º** `ControlesToque` (joystick/câmera).

### 10.2 Zonas da tela (em paisagem)
| Zona | Área | Função |
|---|---|---|
| Joystick | metade esquerda da tela, parte inferior (altura ≤ 60%) | movimento |
| Câmera | metade direita da tela, **exceto** onde há botões | girar câmera |
| Botões | canto inferior direito | ações |
| Minimapa | canto superior (lado a definir no Marco 1.3) | só visual |

Toque na metade esquerda **fora** da zona do joystick: ignorado (não gira câmera).

### 10.3 Joystick (fixo, não flutuante)
| Parâmetro | Valor inicial |
|---|---|
| Posição do centro | 40 dp + raio da base, a partir da margem segura esquerda/inferior |
| Raio da base | 64 dp |
| Raio do botão central | 28 dp |
| Zona morta | 12% do raio |
| Saída | vetor normalizado (−1..1 em X e Y), magnitude limitada a 1 |
| Soltar | botão central volta ao centro imediatamente |

- Movimento analógico: velocidade = magnitude × velocidade máxima.
- Arrastar o botão além do raio: ele fica preso na borda da base.

### 10.4 Maleta no botão central (toque curto × arrasto)
Conta como **toque curto** (abre a maleta) somente se **todas** forem verdadeiras:
1. O dedo começou dentro do raio do botão central.
2. Duração entre `touchDown` e `touchUp` **< 250 ms**.
3. O deslocamento máximo foi **< 8 dp** (nunca saiu da zona morta).

Caso contrário, é arrasto de movimento. Com a maleta aberta, o joystick fica **desativado** até fechar.

### 10.5 Botões de ação (Fase 1: só 3)
| Botão | Diâmetro | Posição (centro, a partir do canto inferior direito, dentro da margem segura) |
|---|---|---|
| ATIRAR | 80 dp | 24 dp + 40 dp de cada borda |
| RECARREGAR | 56 dp | à esquerda do ATIRAR, 16 dp de folga |
| INTERAGIR | 56 dp | acima do ATIRAR, 16 dp de folga |

- Feedback visual ao pressionar (escurece/escala 0,92).
- ATIRAR funciona **segurando** (tiro contínuo respeitando a cadência).
- Pular, agachar, esquivar: **fora da Fase 1** (só entram por decisão registrada).

### 10.6 Demais elementos do HUD
- **Barra de vida:** topo, lado oposto ao minimapa, 160×16 dp, número da vida ao lado.
- **Munição:** `carregador / reserva` próximo ao botão ATIRAR, texto legível (ver fonte, seção 16).
- **Mira:** pequeno ponto/cruz no **centro exato** da tela.
- **Minimapa:** quadrado 120 dp, mostra posição do jogador (ponto) e dos inimigos (pontos), orientado com o norte fixo (Marco 1.3 só placeholder; funcional no 1.8).
- **Overlay de debug:** FPS e número de inimigos; ativado/desativado por **toque longo (1 s) no canto superior esquerdo**.
- **Viewport do HUD:** `ExtendViewport(1280, 720)` no `Stage`. O mundo 3D usa a própria câmera, atualizada no `resize`.
- **Botão Voltar do Android:** alterna **pausa** (Marco 1.3 em diante).

---

## 11. CÂMERA E MOVIMENTO

| Parâmetro | Valor inicial |
|---|---|
| Tipo | `PerspectiveCamera`, FOV 67, near 0,1, far 80 |
| Pivô | ponto a 1,6 m acima do chão sobre o jogador |
| Distância atrás | 3,0 m |
| Deslocamento sobre o ombro | 0,6 m para a direita |
| Pitch mínimo / máximo | −25° / +50° |
| Sensibilidade | 0,25° por dp arrastado (converter px→dp) |
| Colisão da câmera | se parede entre pivô e câmera, aproximar (Marco 1.6); antes disso, ignorar |

**Movimento do jogador**
- Velocidade máxima: **4,0 m/s**. Raio do colisor (círculo no plano XZ): **0,4 m**. Altura visual da cápsula: **1,8 m**.
- Movimento **relativo ao yaw da câmera** (joystick para cima = ir para onde a câmera olha).
- O personagem gira suavemente para a direção do movimento. Nos **0,5 s após atirar**, ele encara a direção da mira.
- Sem pulo e sem gravidade na Fase 1 (chão plano em y=0).
- Colisão: círculo contra caixas alinhadas aos eixos (AABB), **deslizando** na parede (não grudar).

---

## 12. COMBATE (valores iniciais, todos em `Constantes.kt`)

**Modelo:** hitscan. Raio sai do **centro da câmera**, atravessa o centro da tela, alcance **30 m**. Vale o **primeiro** acerto (parede ou inimigo). Sem auxílio de mira.

| Pistola (placeholder) | Valor |
|---|---|
| Dano | 25 |
| Cadência | 2,5 tiros/s |
| Carregador | 12 |
| Reserva inicial | 36 |
| Tempo de recarga | 1,5 s |
| Tiro com carregador vazio | recarrega automático ao apertar ATIRAR (se houver reserva) |

**Jogador:** vida máxima **100**. Morreu → tela "VOCÊ MORREU" + botão REINICIAR (Marco 1.6).

Feedback mínimo: flash no cano (cubo amarelo por 0,05 s) e marcador de acerto na mira. Sem som obrigatório na Fase 1.

---

## 13. INIMIGO (valores iniciais)

Placeholder: cápsula de cor distinta, raio 0,4 m.

| Parâmetro | Valor |
|---|---|
| Vida | 100 |
| Velocidade | 2,0 m/s (menor que a do jogador) |
| Raio de visão | 12 m |
| Perde o jogador acima de | 18 m |
| Alcance de ataque | 1,2 m |
| Dano / intervalo | 10 / 1,0 s |

**Máquina de estados:** `PARADO` → (jogador a ≤ 12 m) `PERSEGUINDO` → (≤ 1,2 m) `ATACANDO` → (vida ≤ 0) `MORTO`. Volta a `PERSEGUINDO` se o jogador se afastar; volta a `PARADO` se passar de 18 m.

**Movimento:** perseguição direta com deslizamento em paredes. **Sem pathfinding na Fase 1** (A* em grade fica para decisão futura).

**Mapa de teste:** 3 inimigos iniciais; sem respawn.

---

## 14. MALETA / INVENTÁRIO (Marco 1.7)

| Parâmetro | Valor inicial |
|---|---|
| Grade | 2 linhas × 4 colunas = **8 slots** |
| Tipos de item (Fase 1) | `MUNICAO`, `CURA` |
| Empilhamento | munição: até 36 por slot; cura: 1 por slot |
| Cura | +30 de vida, consome o item |
| Abrir a maleta | **pausa o jogo** (ver seção 19, a confirmar) |
| Slot cheio | item não é coletado; mensagem "Maleta cheia" |
| Coleta | botão INTERAGIR perto do item (raio 1,5 m) |

A lógica (`inventario/Maleta.kt`) **não conhece desenho**; `hud/TelaMaleta.kt` só exibe.

---

## 15. CI (GitHub Actions)

**Arquivo:** `.github/workflows/build-apk.yml`

Requisitos do workflow:
- Gatilhos: `push` na branch `main` e `workflow_dispatch` (rodar manualmente).
- `runs-on: ubuntu-latest`.
- Passos: `actions/checkout`, `actions/setup-java` (distribuição temurin, versão 17), configurar **Gradle 8.5** (ou usar wrapper, se for commitado com o `gradle-wrapper.jar`; **escolher um e registrar na seção 20**), executar `assembleDebug` do módulo `android` com `--stacktrace`, `actions/upload-artifact` do APK.
- Caminho esperado do APK: `android/build/outputs/apk/debug/`.
- Artefato: nome `apk-debug`, retenção 14 dias.
- Versões das actions fixadas em tag maior (`@v4` etc.); a IA **deve avisar** se não tiver certeza de que a tag existe.
- Sem assinatura de release, sem segredos, sem keystore.

**Segundo workflow:** `.github/workflows/gerar-modelo.yml`, **somente `workflow_dispatch`** (manual), porque baixar/rodar o Blender é pesado. Detalhes na seção 24.

---

## 16. ARMADILHAS CONHECIDAS (a IA verifica cada uma ao gerar/alterar o projeto)

1. **Natives do LibGDX:** sem os `.so` do `gdx-platform` para a ABI, o app **fecha ao abrir** (erro de biblioteca nativa). Incluir natives de `arm64-v8a` e copiá-los para `jniLibs` conforme o modelo oficial de projeto LibGDX/Android. Se não souber a forma exata, **avise**.
2. **AGP 8:** `namespace` no Gradle; **sem** `package` no manifest.
3. **gradle.properties:** `android.useAndroidX=true`; `org.gradle.jvmargs=-Xmx2048m`.
4. **JVM target 17** igual em Kotlin e Java dos dois módulos.
5. **Toque Y invertido:** `Gdx.input.getY()` (origem topo) × `Stage`/HUD (origem baixo). Converter sempre.
6. **Ordem do `InputMultiplexer`:** Stage primeiro.
7. **Não criar recursos do LibGDX antes de `create()`** (GL ainda não existe em construtores/`init` estáticos).
8. **`ModelBuilder` sem luz = tudo preto.** Usar `Environment` com luz ambiente + 1 direcional.
9. **Fonte:** `BitmapFont` padrão fica minúscula/borrada em telas densas. Na Fase 1 usar a padrão com `getData().setScale(...)` conforme `density`. **Não adicionar gdx-freetype** sem decisão.
10. **`ShapeRenderer`/`SpriteBatch`:** nunca `begin()` de um com o outro aberto; sempre `end()` antes.
11. **Pause/resume do Android:** o contexto GL pode ser recriado; nada de estado importante dependendo de objetos GL sem tratamento.
12. **Delta time:** limitar (seção 8.2).
13. **Alocação por frame:** `Vector3()` dentro de `render()` causa travadas por coleta de lixo. Reutilizar campos.
14. **Densidade:** converter dp↔px com `Gdx.graphics.density`; não fixar pixels.
15. **Botão Voltar:** tratar (seção 10.6), senão o app fecha sem aviso.
16. **Entrada de toque:** estender `InputAdapter` (não implementar `InputProcessor` direto), para não faltar método abstrato na versão travada. Ao pausar o app, soltar todos os ponteiros (`liberarTudo()`).
17. **ShapeRenderer:** ao mudar a projeção em `resize`, chamar `updateMatrices()`; sem isso o desenho continua com a matriz antiga.

---

## 17. MARCOS DA FASE 1 (com teste de aceite)

Regra: um marco só fecha quando **eu** marcar todos os itens depois de testar no celular. A IA não marca.

**Definição de Pronto (vale para todo marco):**
- [ ] Build do GitHub Actions **verde**
- [ ] APK instala e abre sem fechar
- [ ] Não trava ao: girar o aparelho (de um lado da paisagem para o outro), apertar Home e voltar, bloquear/desbloquear a tela
- [ ] FPS dentro do orçamento (seção 5), se já houver cena 3D
- [ ] `docs/MAPA_DE_CRIACAO.md` com a seção 22 atualizada

### Marco 1.0: Projeto compila (CONCLUÍDO em 15/11/2026)
- [x] Estrutura da seção 9 criada (só o necessário para este marco)
- [x] Workflow gera o APK debug
- [x] **Teste:** instalar o APK; abre e mostra fundo de cor sólida e texto "OK" por 3 s (aprovado em 15/11/2026: abriu, "OK" e depois 120 FPS, girar a tela e Home/voltar sem fechar)
- [x] **Congelar versões** da seção 6 (feito em 15/11/2026)

### Marco 1.1: Mundo e personagem (APROVADO pelo vídeo em 15/11/2026; falta confirmar a tela cheia)
- [x] Chão plano 3D (ex.: 60×60 m) com cor
- [x] Cápsula do jogador no centro
- [x] Luz ambiente + direcional (nada preto)
- [x] "Nariz" amarelo na cápsula mostrando a frente (yaw 0 = −Z)
- [ ] Tela cheia sobre o recorte da câmera (sem faixa preta lateral)
- [x] Câmera provisória orbitando devagar (substituída no Marco 1.2)
- [x] **Teste:** cena visível, sem piscar, 30+ FPS (visto: 84 a 120 FPS)

### Marco 1.2: Movimento e câmera (APROVADO em 15/11/2026)
- [x] Joystick fixo à esquerda com zona morta e multitouch (seção 10)
- [x] Jogador anda relativo à câmera e vira na direção do movimento
- [x] Câmera 3ª pessoa segue; arrastar à direita gira (yaw e pitch com limites)
- [x] **Teste:** andar em círculo girando a câmera ao mesmo tempo, 60 s, sem perder toque, sem travar

### Marco 1.3: HUD base (APROVADO pelo vídeo em 15/11/2026)
- [x] Barra de vida, mira central, 3 botões (visuais), minimapa placeholder
- [ ] Botão central do joystick desenhado com ícone de maleta
- [ ] Margem segura de 24 dp respeitada
- [ ] Overlay de debug (toque longo) e botão Voltar = pausa
- [ ] **Teste:** botões respondem ao toque sem interferir na câmera; elementos não ficam sob o furo da câmera

### Marco 1.4: Tiro (APROVADO pelo vídeo em 15/11/2026)
- [x] ATIRAR dispara hitscan do centro da tela, com cadência e munição
- [ ] RECARREGAR funciona; carregador/reserva atualizam no HUD
- [ ] Alvos fixos de teste (cubos) reagem ao acerto
- [ ] **Teste:** esvaziar 12 tiros, recarregar, confirmar contagem 12/24

### Marco 1.5: Inimigo (entregue; aprovação do dono a confirmar)
- [ ] 1 inimigo com máquina de estados (seção 13), depois 3
- [ ] Persegue, ataca, causa dano; morre com 4 tiros de 25
- [ ] **Teste:** inimigo para de perseguir ao passar de 18 m; ao chegar a 1,2 m tira 10 de vida por segundo

### Marco 1.6: Dano, morte e reinício (entregue; aprovação do dono a confirmar; a colisão da câmera com paredes só faz sentido a partir do 1.8)
- [ ] Vida do jogador cai e chega a 0 → tela "VOCÊ MORREU" + REINICIAR
- [ ] Reiniciar restaura vida, munição e inimigos
- [ ] Câmera não atravessa paredes
- [ ] **Teste:** morrer 3 vezes seguidas sem vazamento de memória perceptível nem queda de FPS

### Marco 1.7: Maleta (entregue; aprovação do dono a confirmar)
- [ ] Toque curto no botão central abre/fecha (critérios da seção 10.4)
- [ ] Grade 2×4, itens `MUNICAO` e `CURA`, limite de slots
- [ ] Coleta com INTERAGIR; usar cura funciona
- [ ] **Teste:** arrastar o joystick **não** abre a maleta; tocar rápido abre; maleta cheia recusa item
- Como testar a coleta: os 10 cubos de teste estão atrás do ponto inicial (verdes = cura, azuis = munição); chegue a menos de 1,5 m e aperte INTERAGIR. Para testar a cura, leve dano de um inimigo antes.

### Marco 1.8: Mapa de teste e minimapa (dono informou "deu certo" em 15/11/2026)
- [ ] Paredes e portas simples (AABB), áreas interna e externa, tudo original
- [ ] Colisão com deslizamento em paredes para jogador e inimigos
- [ ] Minimapa funcional (jogador e inimigos)
- [ ] **Teste:** jogar 5 minutos sem travar, FPS ≥ 30, sem atravessar paredes

- Como testar: entre pelo vão da parede sul do prédio; encoste nas paredes andando em diagonal (deve deslizar); gire a câmera de costas para uma parede (a câmera se aproxima); atire num inimigo atrás da parede (não acerta); olhe o minimapa (você em branco, inimigos em vermelho, paredes em cinza).

> **Só depois do 1.8 estável** discutimos a Fase 2 (história).

---

## 18. FASE 2: HISTÓRIA (RESERVADA)

Não implementar nem detalhar agora. Quando o dono liberar, esta seção será preenchida com: enredo, capítulos, objetivos, diálogos, cutscenes simples, salvamento. **A IA não cria enredo por conta própria.**

---

## 19. DECISÕES E PONTOS EM ABERTO

### Decidido pelo dono
- 3D, terceira pessoa, Kotlin, Android.
- Modo história; **sem Battle Royale**.
- Primeiro o básico, depois a história.
- Maleta = botão central do joystick.
- **Python é a única linguagem adicional** (somente ferramentas de ativos). GLSL e demais linguagens: não por enquanto.

### Interpretações/escolhas técnicas adotadas (podem ser trocadas pelo dono)
- LibGDX como engine; Groovy DSL; colisão própria no plano XZ (sem gdx-bullet); hitscan; sem pathfinding.
- Pistola, inimigo e maleta com os valores das seções 12 a 14 (iniciais, para testar e ajustar).
- Abrir a maleta **pausa** o jogo (estilo sobrevivência clássico). **Confirmar.**
- **"Mapa fundido":** a ideia original era fundir o mapa de um jogo de sobrevivência com o de um jogo de tiro mobile. Como o modo é história e não battle royale, o mapa será **original**, de ambiente de sobrevivência, com **minimapa estilo mobile**. **Confirmar se é isso que o dono quer.**

- Marco 1.7 (a confirmar): usar MUNICAO joga a pilha na reserva da arma; item só entra se couber inteiro; reinício esvazia a maleta; `ItemMundo.kt` fora da seção 9.

- Marco 1.8 (a confirmar): portas como vãos; inimigos sem linha de visão e sem pathfinding; tiro bloqueado por parede; câmera aproxima ao encostar; minimapa norte fixo.

### Em aberto (a IA PERGUNTA, não decide)
- Nome final do jogo (e então o pacote).
- (Resolvido em 15/11/2026) Estilo de arte: low-poly realista, proporções humanas; inimigos = zumbis; textura de roupa 512x512 (seção 20).
- Detalhes do zumbi (ferimentos, roupa rasgada, pele) e do jogador (aparência, roupa): a IA PERGUNTA antes de gerar.
- Armas e inimigos além da pistola e do inimigo básico.
- Enredo e personagens.
- (Resolvido) Ambiente de trabalho do dono: Termux no celular (colar comandos e `git push`) e GitHub Actions para compilar. Caminhos e comandos reais: **seção 25**.
- (Resolvido em 15/11/2026) Gradle instalado pelo CI, sem wrapper: ver seção 20.
- Sons/música (por padrão: nenhum na Fase 1).
- (Resolvido em 16/11/2026) Como carregar modelo com esqueleto no LibGDX: **`.g3dj` com `G3dModelLoader`** (decisão do dono). O `.g3dj` **ainda não foi carregado no LibGDX pela IA** (sem LibGDX no ambiente): a prova é o teste do F3 no aparelho.
- `numBones` do `DefaultShader` em 20 (boneco com 18 ossos): a confirmar no aparelho (limite de uniformes do GLES2).
- Versão do Blender e método de instalação no CI: **proposta 4.5.11 LTS, tarball oficial** (a confirmar pelo resultado do F1).
- Convenção de **frente do modelo**: proposta no F0 = **-Z** (a confirmar pelo dono ao ver o preview).
- (Resolvido em 15/11/2026) Marcos F0 a F3 correm agora, em paralelo aos 1.x.

---

## 20. REGISTRO DE DECISÕES

| Data | Decisão | Quem | Motivo |
|---|---|---|---|
| 15/11/2026 | 3D em 3ª pessoa, Kotlin | Dono | Escolha do dono |
| 15/11/2026 | Modo história, sem Battle Royale | Dono | Escolha do dono |
| 15/11/2026 | Básico antes da história | Dono | Escolha do dono |
| 15/11/2026 | LibGDX + Groovy DSL + colisão própria XZ | Proposta (a confirmar) | Simplicidade e desempenho no aparelho alvo |
| 15/11/2026 | Python como única linguagem adicional (ferramentas de ativos); sem GLSL/C++/Rust/Lua | Dono | Modelagem melhor via Blender sem tela, mantendo o jogo em Kotlin |
| 15/11/2026 | Estilo de arte: low-poly realista, proporções humanas | Dono | Escolha do dono |
| 15/11/2026 | Inimigos: humanos infectados (zumbis) | Dono | Escolha do dono |
| 15/11/2026 | Boneco com textura simples de roupa 512x512 (gerada por código) | Dono | Escolha do dono |
| 15/11/2026 | Marcos F0 a F3 liberados agora, em paralelo aos 1.x | Dono (pedido de foco na estética) | Prioridade do dono |
| 15/11/2026 | Blender 4.5.11 LTS (tarball oficial) para o F1 | Proposta da IA (a confirmar) | 4.2 LTS fora de suporte; 4.5 LTS ativo até 07/2027 |
| 16/11/2026 | Exportar modelos animados em `.g3dj` | Dono | Sem dependência extra |
| 16/11/2026 | Esqueleto de 13 ossos, pele com ≤ 2 ossos por vértice, animações a 30 fps; exportar em `.g3dj` | Proposta da IA (a confirmar) | Sem dependência extra; atende ao orçamento da seção 24.3 |
| 15/11/2026 | Gradle 8.5 instalado pelo CI (sem wrapper); versões da seção 6 congeladas | Dono (validou no teste) | Build verde e app rodando no aparelho alvo |

---

## 21. FORMATO DE RESPOSTA E AUTOVERIFICAÇÃO DA IA

### Autoverificação (faça antes de enviar; marque mentalmente cada item)
- [ ] Todos os `import` necessários estão presentes e existem na versão travada
- [ ] `package` de cada arquivo bate com a pasta
- [ ] Nenhuma dependência fora da lista da seção 6
- [ ] Nenhum número mágico fora de `Constantes.kt`
- [ ] Nenhuma alocação nova dentro de `render()`/`update()`
- [ ] Todo recurso descartável tem `dispose()`
- [ ] Coordenadas/unidades conforme a seção 8.1
- [ ] Nada de nome/asset de terceiros
- [ ] Só funcionalidades do marco atual
- [ ] Todo arquivo entregue está **completo**
- [ ] Se houver Python: é só ferramenta de ativos (seção 24), sem chamada do `bpy` de que eu não tenha certeza, com `print` de log em cada etapa

### RELATÓRIO (obrigatório no fim de toda resposta)
```
RELATÓRIO
- Marco atual: X.X
- Arquivos criados/alterados (caminho completo): ...
- Comandos para rodar/colar: ...
- Teste de aceite que o dono deve fazer: (passos curtos)
- NÃO TENHO CERTEZA sobre: (lista; se nada, "nada")
- Risco conhecido: ...
- Próximo passo sugerido (um só): ...
```

---

## 22. ESTADO ATUAL (atualizar ao fim de cada sessão)

- Marco atual: **1.8 (dono informou "deu certo"; caixas a marcar por ele)**; trilha ativa de ferramentas: **F0 e F1 (rodaram no CI), F2 (rodou no CI; poses a aprovar) e F3 (entregue, em teste)**
- Último build verde confirmado: run #9 (código dos Marcos 1.7 e 1.8). F0 enviado (`ebb5ef4`); "Gerar modelo" ainda não rodado
- Versões congeladas: **sim** (seção 6)
- Arquivos existentes no repositório: tudo até o Marco 1.7 + 1 novo no 1.8 (`mundo/Colisao.kt`) e 8 alterados (depois do push)
- Problemas conhecidos: faixa preta lateral (recorte da câmera) ainda a confirmar na tela cheia; os Marcos 1.7 e 1.8 não foram compilados pela IA (sem compilador no ambiente da conversa)
- Observações: HUD sem Scene2D (ver v2.4); Voltar do Android vira pausa (ou fecha a maleta, se aberta); acentos na fonte padrão não verificados (textos do 1.7 sem acento); inimigos sem linha de visão nem pathfinding
- Marcos de ferramentas (seção 24): **F0 rodou no CI** (dentro da etapa `f2`; correção de faces entregue com o F2); **F1** não rodou isolado, mas o mesmo Blender 4.5.11 rodou no F2; **F2 rodou no CI** (orçamento OK; falta aprovar as poses); **F3 em teste** (código entregue, **não compilado pela IA**: sem LibGDX no ambiente)

- Envio ao GitHub: caminhos e comandos verificados em 15/11/2026 estão na seção 25.

---

## 23. REGISTRO DE LICENÇAS DE ASSETS

| Asset | Origem | Licença | Data | Onde é usado |
|---|---|---|---|---|
| (nenhum; Fase 1 usa só placeholders gerados por código) | | | | |

---

## 24. PIPELINE DE ATIVOS (Kotlin + Python/Blender)

### 24.1 Princípio
- Python **só gera arquivos** (modelos, previews). Não roda no celular e **não entra no APK**.
- Tudo roda no **GitHub Actions**, de forma **manual** (`gerar-modelo.yml`). O resultado volta como **artefato baixável** para eu testar.
- Estes marcos (F0 a F3) são **independentes** dos marcos 1.x. O jogo usa a cápsula placeholder se os modelos de `android/assets/modelos/` faltarem.

### 24.2 Marcos de ferramentas (com teste de aceite)

**F0: Gerador Kotlin do boneco (EM TESTE)**
- [ ] Programa JVM Kotlin sem dependências gera `boneco.obj`, `boneco.mtl` e `preview.png` (frente, lado e costas)
- [ ] Workflow manual entrega o artefato
- [ ] Partes separadas e nomeadas: cabeça, tronco, braço esq./dir., perna esq./dir.
- [ ] (proposta, a confirmar) Proporções humanas realistas (~7,5 cabeças de altura), UVs em cada parte e `textura_jogador.png` / `textura_zumbi.png` 512x512 geradas por código; mesma malha-base para os dois
- [ ] **Teste:** rodar o workflow "Gerar modelo", baixar o artefato `boneco-f0` e ver `preview.png` no celular; `relatorio.txt` deve mostrar altura 1,8 m e yMin 0 (o programa falha sozinho se não bater)
- [ ] A IA verifica (NÃO presumir) se o `ObjLoader` do LibGDX preserva as partes separadas

**F1: Blender sem tela roda no CI (EM TESTE)**
- [ ] Workflow instala/baixa o Blender (método registrado na seção 20)
- [ ] Script Python mínimo cria um cubo e renderiza `preview.png`
- [ ] **Teste:** rodar "Gerar modelo" com etapa `f1`; baixar o artefato `blender-f1` (PNG + `versao_blender.txt`); o log mostra a versão do Blender

**F2: Boneco com esqueleto e animações (EM TESTE)**
- [ ] `gerar_boneco.py` cria malha low-poly, esqueleto e vínculo malha-ossos
- [ ] 5 animações: `parado`, `andar`, `correr`, `atirar`, `morrer`
- [ ] Exporta no formato decidido (seção 19)
- [ ] Folha de preview com 1 quadro por animação
- [ ] **Teste:** rodar "Gerar modelo" com etapa `f2`; baixar o artefato `boneco-f2`; abrir `previa_python.png` no celular (poses reconhecíveis); `f2_relatorio.txt` sem "ESTOUROU", "FORA" ou "DIVERGE"
- [ ] (proposta, a confirmar) Zumbi com `atacar` no lugar de `atirar`; atenção: o script do Blender não foi executado pela IA, verificar o log do passo "Gerar esqueleto e animações"

**F3: Integração no jogo (EM TESTE)**
- [ ] Decisão sobre a dependência de carga do modelo registrada (seção 19)
- [ ] Modelo substitui a cápsula; animação troca conforme o estado (parado/andando/atirando/morto)
- [ ] **Teste:** FPS dentro do orçamento (seção 5) com o boneco e 3 inimigos

### 24.3 Orçamento do boneco (valores iniciais)
| Item | Limite |
|---|---|
| Triângulos | ≤ 2.000 |
| Ossos | ≤ 25 |
| Materiais | 1 (cor sólida ou textura ≤ 512×512) |
| Altura | 1,8 m |
| Origem | nos pés, escala aplicada (sem escala residual) |
| Unidade | 1 unidade = 1 metro |
| Animações | 30 fps; em loop: `parado`, `andar`, `correr`; sem loop: `atirar`, `morrer` |

Nomes de ossos e animações em **ASCII, português, sem acento** (ex.: `quadril`, `coluna`, `cabeca`, `braco_esq`).

### 24.4 Regras para scripts Python
1. Python 3, estilo PEP 8; identificadores em **português sem acentos**, `snake_case`; comentários em PT-BR.
2. O Blender é **Z para cima**; o jogo é **Y para cima** (seção 8.1). A conversão acontece na exportação: a IA confere as opções do exportador e **não presume**. A **frente do modelo** é decisão em aberto (seção 19).
3. O script começa **limpando a cena** (arquivo de fábrica vazio) e é **determinístico** (mesma entrada, mesma saída; nada aleatório sem semente fixa).
4. A API do `bpy` muda entre versões: a IA só usa chamadas de que tenha certeza na versão travada; se não tiver, escreve "NÃO TENHO CERTEZA" e deixa o script **imprimir diagnóstico** para eu colar.
5. Cada etapa imprime log (`print("[ETAPA] ...")`). Falha: `sys.exit(1)` com mensagem clara.
6. Sem internet dentro do script e sem baixar assets: tudo gerado por código.
7. Parâmetros (altura, cores, proporções) no **topo do arquivo**, em constantes MAIÚSCULAS.
8. Saída somente na pasta `saida/`. Não escrever fora dela.
9. Sem addons de terceiros.

### 24.5 Limites honestos
- O resultado é **low-poly procedural**: sem escultura detalhada, rosto simples, sem textura pintada à mão.
- A qualidade artística melhora por **iterações** (ajustar proporções, cores, poses), não de uma vez.
- 100% procedural: a seção 23 continua sem entradas de licença.

---

## 25. AMBIENTE DO DONO: TERMUX, GITHUB E CAMINHOS REAIS

> **Regra para a IA:** esta seção é a única fonte para enviar arquivos ao GitHub. Tudo marcado como **VERIFICADO** apareceu em captura de tela do Termux ou em página pública do GitHub em 15/11/2026. O que **não** está aqui, a IA **pergunta**; não inventa.

### 25.1 Dados verificados

| Item | Valor |
|---|---|
| Usuário no GitHub | `matheusalmeidaeca` |
| Repositório | `https://github.com/matheusalmeidaeca/sobrevivente` (público) |
| Remoto Git (`origin`) | `https://github.com/matheusalmeidaeca/sobrevivente.git` |
| Branch | `main` (única usada) |
| Página das Actions | `https://github.com/matheusalmeidaeca/sobrevivente/actions` |
| Pasta do repositório no Termux | `~/estoque-v2/projeto_sobrevivente` (= `/data/data/com.termux/files/home/estoque-v2/projeto_sobrevivente`) |
| Pasta de downloads vista pelo Termux | `~/storage/downloads/` |
| Autenticação | já funciona; o `git push` **não pediu** senha nem token |
| `unzip` | já instalado no Termux |
| Workflows existentes no repositório | `build-apk.yml` ("Build APK debug", roda sozinho a cada push na `main`) e `gerar-modelo.yml` ("Gerar modelo", manual) |

**NÃO existe** `~/sobrevivente` (dá "No such file or directory"). **Não usar.**

### 25.2 Como os arquivos chegam ao Termux
1. A IA entrega um `.zip` (cartão de arquivo na conversa). O dono baixa pelo celular; ele vai para **Downloads**.
2. O conteúdo do zip tem **caminhos relativos à raiz do repositório** (ex.: `ferramentas/...`, `.github/workflows/...`, `docs/MAPA_DE_CRIACAO.md`), **sem pasta raiz extra**. A IA deve sempre montar o zip assim.
3. `unzip -o` **sobrescreve** arquivos que já existem (é o desejado).

### 25.3 Sequência de envio (VERIFICADA)
Trocar `NOME.zip` pelo nome do zip entregue e `MENSAGEM` pela mensagem do commit:

```
cd ~/estoque-v2/projeto_sobrevivente
unzip -o ~/storage/downloads/NOME.zip
git add -A
git commit -m "MENSAGEM"
git pull --rebase origin main
git push origin main
```

- Mensagens de commit seguem o padrão já usado: `Marco 1.8: mapa de teste, colisao e minimapa`, `F0: gerador do boneco e workflow gerar-modelo`. Sem acento, curtas.
- Depois do `push`, o **Build APK debug** roda sozinho. Os arquivos em `ferramentas/` não entram no APK.

### 25.4 Rodar o workflow manual "Gerar modelo" (pelo navegador do celular)
1. Abrir a página das Actions (seção 25.1).
2. Tocar em **Gerar modelo** → **Run workflow**.
3. Escolher a etapa: `f0` (gerador Kotlin do boneco) ou `f1` (Blender sem tela). Confirmar em **Run workflow**.
4. Esperar ficar verde; abrir a execução; baixar o artefato em **Artifacts**: `boneco-f0` ou `blender-f1`.
5. Se não aparecer o botão **Run workflow**, ativar "Site para computador" no navegador.
6. Se falhar, abrir o passo que falhou e colar o **log completo** (começo do erro) para a IA.

Estado em 15/11/2026: o `gerar-modelo.yml` já está no repositório com a etapa F0; a etapa **f1** só existe depois do push do zip do F1. O workflow ainda **não foi executado/confirmado** pelo dono.

### 25.5 Erros já vistos (não repetir)
| Erro no Termux | Causa | Correção |
|---|---|---|
| `cd: ~/sobrevivente: No such file or directory` | pasta errada | usar `~/estoque-v2/projeto_sobrevivente` |
| `unzip: cannot find or open NOME.zip` | o zip está em Downloads, não na pasta do repositório | usar o caminho `~/storage/downloads/NOME.zip` |
| `nothing to commit, working tree clean` | os arquivos ainda não tinham sido descompactados | rodar o `unzip` antes do `git add` |
| `termux-setup-storage` pergunta "Do you want to continue? (y/n)" | `~/storage` já existe | **não rodar de novo**; se aparecer, responder `n` |
| `ferramentas: command not found` | nome de pasta digitado sem o comando | para listar, usar `ls ferramentas .github/workflows` |

### 25.6 O que a IA pode e não pode fazer (VERIFICADO)
- A IA **não faz push** nem roda workflow: quem faz é o dono, pelos comandos acima.
- A IA consegue abrir páginas **públicas** do GitHub (repositório e Actions). A pasta `.github/workflows` do GitHub **bloqueia acesso automatizado**: para ver o conteúdo de um workflow, pedir ao dono para colar o arquivo.
- O conteúdo do `build-apk.yml` **não foi visto** pela IA (não afirmar como o Gradle é instalado nele sem o dono colar).
- A IA não tem compilador Kotlin nem Blender na conversa: tudo é testado pelo CI do dono.

