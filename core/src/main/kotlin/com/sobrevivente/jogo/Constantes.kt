// core/src/main/kotlin/com/sobrevivente/jogo/Constantes.kt
package com.sobrevivente.jogo

/** Todos os números ajustáveis do jogo ficam aqui (regra 10 do mapa). */
object Constantes {
    // Tempo
    const val DELTA_MAXIMO = 1f / 30f

    // HUD / texto
    const val MARGEM_SEGURA_DP = 24f
    const val ESCALA_FONTE = 1.5f

    // Céu (cor de fundo)
    const val COR_CEU_R = 0.10f
    const val COR_CEU_G = 0.14f
    const val COR_CEU_B = 0.12f

    // Chão: quadrados alternados claro/escuro (ajuda a perceber movimento)
    const val CHAO_TAMANHO = 160f
    const val CHAO_LADO_QUADRADO = 2f
    const val CHAO_CLARO_R = 0.40f
    const val CHAO_CLARO_G = 0.46f
    const val CHAO_CLARO_B = 0.28f
    const val CHAO_ESCURO_R = 0.34f
    const val CHAO_ESCURO_G = 0.40f
    const val CHAO_ESCURO_B = 0.24f

    // Jogador (placeholder: cápsula + "nariz" que mostra a frente)
    const val JOGADOR_RAIO = 0.4f
    const val JOGADOR_ALTURA = 1.8f
    const val JOGADOR_DIVISOES = 16
    const val JOGADOR_COR_R = 0.75f
    const val JOGADOR_COR_G = 0.25f
    const val JOGADOR_COR_B = 0.20f
    const val NARIZ_LARGURA = 0.2f
    const val NARIZ_ALTURA = 0.2f
    const val NARIZ_COMPRIMENTO = 0.5f
    const val NARIZ_ALTURA_DO_CHAO = 1.4f
    const val NARIZ_DESLOCAMENTO_FRENTE = 0.45f
    const val NARIZ_COR_R = 0.95f
    const val NARIZ_COR_G = 0.85f
    const val NARIZ_COR_B = 0.20f

    // Jogador: movimento (m/s e 1/s)
    const val JOGADOR_VELOCIDADE = 4.0f
    const val JOGADOR_VELOCIDADE_GIRO = 12f
    // Limite do chão enquanto não há paredes (Marco 1.8)
    const val MAPA_LIMITE = CHAO_TAMANHO / 2f - JOGADOR_RAIO

    // Jogador: vida (seção 12 do mapa)
    const val JOGADOR_VIDA_MAXIMA = 100

    // Luz
    const val LUZ_AMBIENTE_R = 0.45f
    const val LUZ_AMBIENTE_G = 0.45f
    const val LUZ_AMBIENTE_B = 0.50f
    const val LUZ_SOL_R = 0.90f
    const val LUZ_SOL_G = 0.88f
    const val LUZ_SOL_B = 0.80f
    const val LUZ_SOL_DIR_X = -0.6f
    const val LUZ_SOL_DIR_Y = -1.0f
    const val LUZ_SOL_DIR_Z = -0.4f

    // Câmera de terceira pessoa (seção 11 do mapa)
    const val CAMERA_FOV = 67f
    const val CAMERA_NEAR = 0.1f
    const val CAMERA_FAR = 120f
    const val CAMERA_DISTANCIA = 3.0f
    const val CAMERA_OMBRO = 0.6f
    const val CAMERA_PIVO_ALTURA = 1.6f
    const val CAMERA_ALTURA_MINIMA = 0.3f
    const val CAMERA_PITCH_INICIAL = -10f
    const val CAMERA_PITCH_MIN = -25f
    const val CAMERA_PITCH_MAX = 50f
    const val CAMERA_SENSIBILIDADE = 0.25f // graus por dp arrastado

    // Joystick (seção 10.3 do mapa)
    const val JOYSTICK_RAIO_BASE_DP = 64f
    const val JOYSTICK_RAIO_BOTAO_DP = 28f
    const val JOYSTICK_MARGEM_DP = 40f
    const val JOYSTICK_ZONA_MORTA = 0.12f
    const val ZONA_JOYSTICK_ALTURA = 0.6f
    const val JOYSTICK_SEGMENTOS = 32
    const val JOYSTICK_ALFA_BASE = 0.20f
    const val JOYSTICK_ALFA_BOTAO = 0.50f

    // Ícone da maleta no botão central (proporções em relação ao raio do botão)
    const val ICONE_MALETA_LARGURA = 1.1f
    const val ICONE_MALETA_ALTURA = 0.7f
    const val ICONE_MALETA_ALCA_LARGURA = 0.5f
    const val ICONE_MALETA_ALCA_ALTURA = 0.3f
    const val ICONE_MALETA_ALCA_ESPESSURA = 0.1f
    const val ICONE_MALETA_FECHO_LARGURA = 0.2f
    const val ICONE_MALETA_FECHO_ALTURA = 0.2f
    const val ICONE_MALETA_COR = 0.10f
    const val ICONE_MALETA_ALFA = 0.85f
    const val ICONE_MALETA_FECHO_COR = 0.90f

    // Botões de ação (seção 10.5 do mapa)
    const val BOTAO_ATIRAR_DIAMETRO_DP = 80f
    const val BOTAO_PEQUENO_DIAMETRO_DP = 56f
    const val BOTAO_FOLGA_DP = 16f
    const val BOTAO_ALVO_MINIMO_DP = 48f
    const val BOTAO_ESCALA_PRESSIONADO = 0.92f
    const val BOTAO_ESCURECER = 0.6f
    const val BOTAO_ALFA = 0.55f
    const val BOTAO_SEGMENTOS = 32
    const val BOTAO_TEXTO_LARGURA_MAX = 0.8f // fração do diâmetro
    const val BOTAO_ATIRAR_COR_R = 0.85f
    const val BOTAO_ATIRAR_COR_G = 0.20f
    const val BOTAO_ATIRAR_COR_B = 0.18f
    const val BOTAO_RECARREGAR_COR_R = 0.20f
    const val BOTAO_RECARREGAR_COR_G = 0.50f
    const val BOTAO_RECARREGAR_COR_B = 0.90f
    const val BOTAO_INTERAGIR_COR_R = 0.20f
    const val BOTAO_INTERAGIR_COR_G = 0.70f
    const val BOTAO_INTERAGIR_COR_B = 0.35f

    // Fontes do HUD (multiplicadas pela densidade)
    const val FONTE_ESCALA_HUD = 0.8f
    const val FONTE_ESCALA_BOTAO = 0.9f // máximo; encolhe se o texto não couber

    // Barra de vida (seção 10.6)
    const val BARRA_VIDA_LARGURA_DP = 160f
    const val BARRA_VIDA_ALTURA_DP = 16f
    const val BARRA_VIDA_BORDA_DP = 2f
    const val BARRA_VIDA_ESPACO_TEXTO_DP = 8f
    const val BARRA_VIDA_COR_R = 0.25f
    const val BARRA_VIDA_COR_G = 0.80f
    const val BARRA_VIDA_COR_B = 0.30f
    const val BARRA_VIDA_FUNDO_COR = 0.05f
    const val BARRA_VIDA_FUNDO_ALFA = 0.65f

    // Minimapa placeholder (seção 10.6; funcional no Marco 1.8)
    const val MINIMAPA_LADO_DP = 120f
    const val MINIMAPA_BORDA_DP = 2f
    const val MINIMAPA_PONTO_DP = 4f
    const val MINIMAPA_FUNDO_COR = 0.05f
    const val MINIMAPA_FUNDO_ALFA = 0.50f
    const val MINIMAPA_BORDA_ALFA = 0.60f
    const val MINIMAPA_SEGMENTOS = 16

    // Mira central
    const val MIRA_COMPRIMENTO_DP = 8f
    const val MIRA_ESPESSURA_DP = 2f
    const val MIRA_FOLGA_DP = 4f
    const val MIRA_ALFA = 0.85f

    // Overlay de debug (toque longo no canto superior esquerdo)
    const val DEBUG_TOQUE_LONGO_S = 1.0f
    const val DEBUG_ZONA_LARGURA_DP = 220f
    const val DEBUG_ZONA_ALTURA_DP = 72f
    const val DEBUG_TEXTO_DESLOCAMENTO_DP = 12f

    // Pausa
    const val PAUSA_ALFA_FUNDO = 0.55f
    const val PAUSA_ESPACO_DP = 12f

    // Pistola placeholder (seção 12 do mapa)
    const val PISTOLA_DANO = 25
    const val PISTOLA_CADENCIA = 2.5f // tiros por segundo
    const val PISTOLA_CARREGADOR = 12
    const val PISTOLA_RESERVA_INICIAL = 36
    const val PISTOLA_RECARGA_S = 1.5f
    const val PISTOLA_ALCANCE = 30f // metros, a partir da câmera
    const val PISTOLA_TEMPO_ENCARAR_S = 0.5f // jogador encara a mira depois de atirar (seção 11)

    // Armas do F4 (valores iniciais para testar; ajustar jogando)
    const val FUZIL_DANO = 18
    const val FUZIL_CADENCIA = 8f
    const val FUZIL_CARREGADOR = 30
    const val FUZIL_RESERVA_INICIAL = 90
    const val FUZIL_RECARGA_S = 2.2f
    const val FUZIL_ALCANCE = 45f
    const val ESCOPETA_DANO = 14 // por bago
    const val ESCOPETA_PROJETEIS = 6
    const val ESCOPETA_DISPERSAO = 0.045f // radianos
    const val ESCOPETA_CADENCIA = 1.1f
    const val ESCOPETA_CARREGADOR = 6
    const val ESCOPETA_RESERVA_INICIAL = 18
    const val ESCOPETA_RECARGA_S = 2.6f
    const val ESCOPETA_ALCANCE = 18f
    const val ESCOPETA_TEMPO_ENCARAR_S = 0.7f
    const val TROCAR_ARMA_ATRASO_S = 0.35f
    const val ARMA_RECARGA_ANIM_S = 2.0f // duracao da animacao recarregar_* (60 quadros / 30 fps) a 1x
    const val ARMA_PALMA_Y = -0.08f // centro da palma no osso mao_dir (igual a pegada.py)
    const val MODELO_ARMA_PISTOLA = "modelos/arma_pistola.g3dj"
    const val MODELO_ARMA_FUZIL = "modelos/arma_fuzil.g3dj"
    const val MODELO_ARMA_ESCOPETA = "modelos/arma_escopeta.g3dj"
    const val BOTAO_TROCAR_COR_R = 0.80f
    const val BOTAO_TROCAR_COR_G = 0.55f
    const val BOTAO_TROCAR_COR_B = 0.10f

    // Clarão no cano (cubo amarelo)
    const val CLARAO_DURACAO_S = 0.05f
    const val CLARAO_LADO = 0.15f
    const val CLARAO_ALTURA_DO_CHAO = 1.4f
    const val CLARAO_DESLOCAMENTO_FRENTE = 0.9f
    const val CLARAO_COR_R = 1.0f
    const val CLARAO_COR_G = 0.90f
    const val CLARAO_COR_B = 0.10f

    // Inimigo básico (seção 13 do mapa)
    const val ENIMIGO_VIDA = 100
    const val ENIMIGO_VELOCIDADE = 2.0f // m/s (menor que a do jogador)
    const val ENIMIGO_VISAO = 12f // começa a perseguir a até 12 m
    const val ENIMIGO_PERDE_JOGADOR = 18f // volta a ficar parado acima de 18 m
    const val ENIMIGO_ALCANCE_ATAQUE = 1.2f
    const val ENIMIGO_DANO = 10
    const val ENIMIGO_INTERVALO_ATAQUE = 1.0f // segundos entre golpes
    // Navegacao e vagar dos inimigos (pedido do dono, v2.16)
    const val NAV_CELULA = 0.5f // tamanho da celula da grade do A* (m)
    const val NAV_MARGEM = 0.08f // folga extra alem do raio do inimigo ao engordar as paredes (m)
    const val ENIMIGO_REPLANEJAR_S = 0.35f // de quanto em quanto tempo recalcula o caminho ate o jogador
    const val ENIMIGO_PONTO_CHEGADA = 0.3f // distancia para considerar que chegou a um ponto do caminho (m)
    const val ENIMIGO_VELOCIDADE_VAGAR = 0.9f // m/s andando sem rumo
    const val ENIMIGO_VAGAR_RAIO = 7f // vaga ate 7 m do ponto de origem
    const val ENIMIGO_ESPERA_MIN_S = 1.5f
    const val ENIMIGO_ESPERA_MAX_S = 4.0f
    const val ENIMIGO_VAGAR_REF = 1.0f // m/s que a animacao "andar" do zumbi cobre com 1x
    const val ENIMIGO_ATAQUE_ANIM_VEL = 1.2f // 72 quadros / 30 fps / 1,2 = 2,0 s para 2 golpes (1 golpe por segundo)
    const val ENIMIGO_ATAQUE_IMPACTO_S = 0.47f // tempo do inicio do golpe ate acertar (quadro 17 / 30 / 1,2)
    const val ENIMIGO_RAIO = 0.4f
    const val ENIMIGO_ALTURA = 1.8f
    const val ENIMIGO_DIVISOES = 16
    const val ENIMIGO_FLASH_S = 0.10f
    const val ENIMIGO_FLASH_COR = 1.0f // branco ao levar tiro
    // Cor por estado (ajuda a testar): parado = verde, perseguindo = laranja, atacando = vermelho
    const val ENIMIGO_COR_PARADO_R = 0.35f
    const val ENIMIGO_COR_PARADO_G = 0.55f
    const val ENIMIGO_COR_PARADO_B = 0.30f
    const val ENIMIGO_COR_PERSEGUINDO_R = 0.90f
    const val ENIMIGO_COR_PERSEGUINDO_G = 0.60f
    const val ENIMIGO_COR_PERSEGUINDO_B = 0.15f
    const val ENIMIGO_COR_ATACANDO_R = 0.85f
    const val ENIMIGO_COR_ATACANDO_G = 0.12f
    const val ENIMIGO_COR_ATACANDO_B = 0.12f
    /** Quantos inimigos entram no mapa (1 a 3). Use 1 para testar um só. */
    const val INIMIGOS_ATIVOS = 3

    // Marcador de acerto na mira
    const val ACERTO_DURACAO_S = 0.15f
    const val ACERTO_FOLGA_DP = 6f
    const val ACERTO_COMPRIMENTO_DP = 8f
    const val ACERTO_ESPESSURA_DP = 2.5f
    const val ACERTO_COR_R = 1.0f
    const val ACERTO_COR_G = 0.20f
    const val ACERTO_COR_B = 0.20f

    // Texto de munição (ao lado esquerdo do botão RECARREGAR)
    const val MUNICAO_LARGURA_DP = 160f
    const val MUNICAO_ESPACO_DP = 8f

    // Tela de morte e botão REINICIAR (Marco 1.6)
    const val TEXTO_MORTE = "VOCÊ MORREU"
    const val TEXTO_REINICIAR = "REINICIAR"
    const val MORTE_FUNDO_R = 0.35f
    const val MORTE_FUNDO_G = 0.02f
    const val MORTE_FUNDO_B = 0.02f
    const val MORTE_FUNDO_ALFA = 0.65f
    const val MORTE_ESCALA_TITULO = 2.0f
    const val REINICIAR_LARGURA_DP = 200f
    const val REINICIAR_ALTURA_DP = 56f
    const val REINICIAR_ESPACO_DP = 24f // entre o título e o botão
    const val REINICIAR_ESCALA_TEXTO = 1.0f
    const val REINICIAR_COR_R = 0.20f
    const val REINICIAR_COR_G = 0.50f
    const val REINICIAR_COR_B = 0.90f

    // ---------------- Marco 1.7: Maleta (seção 14 do mapa) ----------------
    const val MALETA_COLUNAS = 4
    const val MALETA_LINHAS = 2
    const val MALETA_SLOTS = MALETA_COLUNAS * MALETA_LINHAS
    const val MALETA_MUNICAO_POR_SLOT = 36
    const val MALETA_CURA_POR_SLOT = 1
    const val CURA_VALOR = 30 // vida recuperada por item de cura

    // Toque curto no botão central abre/fecha a maleta (seção 10.4)
    const val MALETA_TOQUE_CURTO_MS = 250L
    const val MALETA_TOQUE_DESLOC_DP = 8f

    // Coleta com INTERAGIR (seção 14)
    const val INTERAGIR_RAIO = 1.5f // metros, no plano XZ

    // Desenho da grade
    const val MALETA_SLOT_LADO_DP = 72f
    const val MALETA_SLOT_ESPACO_DP = 8f
    const val MALETA_CENTRO_ALTURA = 0.52f // fração da altura da tela onde fica o centro da grade
    const val MALETA_FUNDO_ALFA = 0.50f
    const val MALETA_SLOT_BORDA_DP = 2f
    const val MALETA_SLOT_BORDA_COR = 0.70f
    const val MALETA_SLOT_FUNDO_COR = 0.12f
    const val MALETA_ITEM_ESCURECER = 0.75f // escurece a cor do item para o texto branco ficar legível
    const val MALETA_ESCALA_TITULO = 1.2f
    const val MALETA_TITULO_ESPACO_DP = 14f
    const val MALETA_DICA_ESPACO_DP = 12f
    const val TEXTO_MALETA = "MALETA"
    const val TEXTO_MALETA_DICA = "Toque no item para usar. Botao central ou VOLTAR fecha."
    const val ROTULO_CURA = "CURA"
    const val ROTULO_MUNICAO = "MUN"

    // Mensagens curtas na tela (sem acento: a fonte padrão não foi verificada com acentos)
    const val MSG_PEGOU_CURA = "Pegou: cura"
    const val MSG_PEGOU_MUNICAO = "Pegou: municao"
    const val MSG_MALETA_CHEIA = "Maleta cheia"
    const val MSG_USOU_CURA = "Curou +$CURA_VALOR"
    const val MSG_USOU_MUNICAO = "Municao na reserva"
    const val MSG_VIDA_CHEIA = "Vida cheia"
    const val MENSAGEM_DURACAO_S = 1.5f
    const val MENSAGEM_ALTURA = 0.18f // fração da altura da tela (texto centrado na largura)
    const val MENSAGEM_ESCALA = 1.0f

    // Itens no chão (cubos de teste)
    const val ITEM_TIPO_CURA = 0
    const val ITEM_TIPO_MUNICAO = 1
    const val ITEM_MUNICAO_QUANTIDADE = 12
    const val ITEM_CURA_QUANTIDADE = 1
    const val ITEM_LADO = 0.4f
    const val ITEM_FOLGA_CHAO = 0.05f
    const val ITEM_COR_CURA_R = 0.25f
    const val ITEM_COR_CURA_G = 0.85f
    const val ITEM_COR_CURA_B = 0.35f
    const val ITEM_COR_MUNICAO_R = 0.30f
    const val ITEM_COR_MUNICAO_G = 0.55f
    const val ITEM_COR_MUNICAO_B = 0.95f

    // ---------------- Marco 1.8: mapa de teste, colisão e minimapa ----------------
    const val PAREDE_ALTURA = 3f
    const val PAREDE_COR_R = 0.55f
    const val PAREDE_COR_G = 0.52f
    const val PAREDE_COR_B = 0.48f
    const val PISO_INTERNO_ESPESSURA = 0.02f
    const val PISO_INTERNO_COR_R = 0.24f
    const val PISO_INTERNO_COR_G = 0.21f
    const val PISO_INTERNO_COR_B = 0.19f

    /**
     * Paredes e obstáculos, 4 números por caixa: minX, minZ, maxX, maxZ (metros).
     * Prédio ao norte (z negativo) com um vão de 3 m na parede sul (a "porta"), 2 pilares dentro
     * e 2 caixas fora. As paredes têm 0,4 m de espessura.
     */

    // ---- VILA (gerada por ferramentas/vila/gerar_vila.py; NAO editar a mao) ----
    /** Paredes e obstaculos (minX, minZ, maxX, maxZ). Ver PAREDES_BASE / PAREDES_ALTURA / PAREDES_TIPO. */
    val PAREDES = floatArrayOf(
        -34f, -32f, -18f, -31.6f,
        -34f, -18.4f, -27.5f, -18f,
        -24.5f, -18.4f, -18f, -18f,
        -34f, -31.6f, -33.6f, -18.4f,
        -18.4f, -31.6f, -18f, -18.4f,
        -31.4f, -24.75f, -18.4f, -24.55f,
        -28f, -28f, -27f, -27f,
        14f, -32f, 28f, -31.6f,
        14f, -22.4f, 19.5f, -22f,
        22.5f, -22.4f, 28f, -22f,
        14f, -31.6f, 14.4f, -22.4f,
        27.6f, -31.6f, 28f, -22.4f,
        -32f, 10f, -27.5f, 10.4f,
        -24.5f, 10f, -20f, 10.4f,
        -32f, 19.6f, -20f, 20f,
        -32f, 10.4f, -31.6f, 19.6f,
        -20.4f, 10.4f, -20f, 19.6f,
        18f, 12f, 30f, 12.4f,
        18f, 23.6f, 30f, 24f,
        18f, 12.4f, 18.4f, 16.5f,
        18f, 19.5f, 18.4f, 23.6f,
        29.6f, 12.4f, 30f, 23.6f,
        -56f, -6f, -47f, -5.6f,
        -56f, 1.6f, -47f, 2f,
        -56f, -5.6f, -55.6f, 1.6f,
        -47.4f, -5.6f, -47f, -3.5f,
        -47.4f, -0.5f, -47f, 1.6f,
        40f, -8f, 50f, -7.6f,
        40f, 0.6f, 50f, 1f,
        40f, -7.6f, 40.4f, -5f,
        40f, -2f, 40.4f, 0.6f,
        49.6f, -7.6f, 50f, 0.6f,
        -10f, 40f, -4.5f, 40.4f,
        -1.5f, 40f, 4f, 40.4f,
        -10f, 49.6f, 4f, 50f,
        -10f, 40.4f, -9.6f, 49.6f,
        3.6f, 40.4f, 4f, 49.6f,
        30f, 36f, 40f, 36.4f,
        30f, 45.6f, 40f, 46f,
        30f, 36.4f, 30.4f, 39.5f,
        30f, 42.5f, 30.4f, 45.6f,
        39.6f, 36.4f, 40f, 45.6f,
        -48f, 36f, -37f, 36.4f,
        -48f, 44.6f, -37f, 45f,
        -48f, 36.4f, -47.6f, 44.6f,
        -37.4f, 36.4f, -37f, 39f,
        -37.4f, 42f, -37f, 44.6f,
        55f, 30f, 64f, 30.4f,
        55f, 37.6f, 64f, 38f,
        55f, 30.4f, 55.4f, 32.5f,
        55f, 35.5f, 55.4f, 37.6f,
        63.6f, 30.4f, 64f, 37.6f,
        -60f, -48f, -50f, -47.6f,
        -60f, -39.4f, -56.5f, -39f,
        -53.5f, -39.4f, -50f, -39f,
        -60f, -47.6f, -59.6f, -39.4f,
        -50.4f, -47.6f, -50f, -39.4f,
        58f, -30f, 67f, -29.6f,
        58f, -21.4f, 67f, -21f,
        58f, -29.6f, 58.4f, -27f,
        58f, -24f, 58.4f, -21.4f,
        66.6f, -29.6f, 67f, -21.4f,
        -12f, -62f, 12f, -61.6f,
        -12f, -46.4f, -2f, -46f,
        2f, -46.4f, 12f, -46f,
        -12f, -61.6f, -11.6f, -46.4f,
        11.6f, -61.6f, 12f, -46.4f,
        38f, -52f, 58f, -51.6f,
        38f, -38.4f, 58f, -38f,
        38f, -51.6f, 38.4f, -47f,
        38f, -43f, 38.4f, -38.4f,
        57.6f, -51.6f, 58f, -38.4f,
        -1.2f, -7.2f, 1.2f, -4.8f,
        7.5f, 5.5f, 8.5f, 6.5f,
        -9.5f, 6.5f, -8.5f, 7.5f,
        11.5f, -10.5f, 12.5f, -9.5f,
        -14.5f, -8.5f, -13.5f, -7.5f,
        24.5f, 27.5f, 25.5f, 28.5f,
        -25.5f, 27.5f, -24.5f, 28.5f,
        -38.5f, -2.5f, -37.5f, -1.5f,
        46.5f, 7.5f, 47.5f, 8.5f,
        -4.5f, 27.5f, -3.5f, 28.5f,
        8.5f, 29.5f, 9.5f, 30.5f,
        -38f, -12.1f, -35f, -11.9f,
        -35f, -12.1f, -32f, -11.9f,
        -32f, -12.1f, -29f, -11.9f,
        -20f, -12.1f, -17f, -11.9f,
        -17f, -12.1f, -14f, -11.9f,
        -14f, -12.1f, -11f, -11.9f,
        -11f, -12.1f, -10f, -11.9f,
        -38f, 1.9f, -35f, 2.1f,
        -35f, 1.9f, -34f, 2.1f,
        10f, 23.9f, 13f, 24.1f,
        13f, 23.9f, 16f, 24.1f,
        25f, 23.9f, 28f, 24.1f,
        28f, 23.9f, 31f, 24.1f,
        31f, 23.9f, 34f, 24.1f,
        34f, 23.9f, 36f, 24.1f,
        -20.1f, 22f, -19.9f, 25f,
        -20.1f, 31f, -19.9f, 34f,
        -20.1f, 34f, -19.9f, 36f,
        31.9f, -14f, 32.1f, -11f,
        31.9f, -11f, 32.1f, -8f,
        31.9f, -2f, 32.1f, 1f,
        31.9f, 1f, 32.1f, 2f,
        -62f, 7.9f, -59f, 8.1f,
        -59f, 7.9f, -56f, 8.1f,
        -56f, 7.9f, -53f, 8.1f,
        -47f, 7.9f, -44f, 8.1f,
        -44f, 7.9f, -41f, 8.1f,
        -41f, 7.9f, -40f, 8.1f,
        59.9f, -8f, 60.1f, -5f,
        59.9f, -5f, 60.1f, -2f,
        59.9f, 4f, 60.1f, 7f,
        59.9f, 7f, 60.1f, 10f,
        59.9f, 10f, 60.1f, 12f,
        -30f, 51.9f, -27f, 52.1f,
        -27f, 51.9f, -24f, 52.1f,
        -18f, 51.9f, -15f, 52.1f,
        -15f, 51.9f, -12f, 52.1f,
        46f, 49.9f, 49f, 50.1f,
        49f, 49.9f, 52f, 50.1f,
        58f, 49.9f, 61f, 50.1f,
        61f, 49.9f, 64f, 50.1f,
        -8f, -44.1f, -5f, -43.9f,
        -5f, -44.1f, -2f, -43.9f,
        4f, -44.1f, 7f, -43.9f,
        7f, -44.1f, 10f, -43.9f,
        10f, -44.1f, 13f, -43.9f,
        13f, -44.1f, 14f, -43.9f,
        -45.3f, -22.3f, -44.7f, -21.7f,
        -48.3f, -20.3f, -47.7f, -19.7f,
        -70.3f, -10.3f, -69.7f, -9.7f,
        -72.3f, 19.7f, -71.7f, 20.3f,
        -66.3f, 41.7f, -65.7f, 42.3f,
        -60.3f, 59.7f, -59.7f, 60.3f,
        -30.3f, 65.7f, -29.7f, 66.3f,
        -12.3f, 69.7f, -11.7f, 70.3f,
        9.7f, 67.7f, 10.3f, 68.3f,
        29.7f, 61.7f, 30.3f, 62.3f,
        49.7f, 69.7f, 50.3f, 70.3f,
        67.7f, 59.7f, 68.3f, 60.3f,
        71.7f, 39.7f, 72.3f, 40.3f,
        73.7f, 9.7f, 74.3f, 10.3f,
        69.7f, -14.3f, 70.3f, -13.7f,
        65.7f, -44.3f, 66.3f, -43.7f,
        49.7f, -70.3f, 50.3f, -69.7f,
        29.7f, -72.3f, 30.3f, -71.7f,
        5.7f, -76.3f, 6.3f, -75.7f,
        -24.3f, -72.3f, -23.7f, -71.7f,
        -50.3f, -70.3f, -49.7f, -69.7f,
        -70.3f, -56.3f, -69.7f, -55.7f,
        -76.3f, -34.3f, -75.7f, -33.7f,
        19.7f, -20.3f, 20.3f, -19.7f,
        23.7f, -16.3f, 24.3f, -15.7f,
        -8.3f, 51.7f, -7.7f, 52.3f,
        3.7f, 13.7f, 4.3f, 14.3f,
        -14.3f, 17.7f, -13.7f, 18.3f,
        61.7f, 3.7f, 62.3f, 4.3f,
        -44.3f, 59.7f, -43.7f, 60.3f
    )
    /** Altura da base de cada parede (0 = chao; 3,2 = guarda-corpo do andar alto). */
    val PAREDES_BASE = floatArrayOf(0f, 0f, 0f, 0f, 0f, 3.2f, 3.2f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    val PAREDES_ALTURA = floatArrayOf(6.4f, 6.4f, 6.4f, 6.4f, 6.4f, 1f, 1f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 5f, 5f, 5f, 5f, 5f, 4.5f, 4.5f, 4.5f, 4.5f, 4.5f, 1.2f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 6f, 6f, 6f, 6f, 6f, 6f, 6f, 6f, 6f, 6f, 6f, 6f, 6f, 6f, 6f, 6f, 6f, 6f, 6f, 6f, 6f, 6f, 6f, 6f, 6f, 6f, 6f, 6f, 6f, 6f)
    /** 0 casa, 1 pedra, 2 cerca/caixote, 3 celeiro, 4 guarda-corpo, 5 tronco. */
    val PAREDES_TIPO = intArrayOf(0, 0, 0, 0, 0, 4, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 3, 3, 3, 3, 3, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5)
    /** Piso interno das casas (minX, minZ, maxX, maxZ). */
    val PISOS = floatArrayOf(-33.6f, -31.6f, -18.4f, -18.4f, 14.4f, -31.6f, 27.6f, -22.4f, -31.6f, 10.4f, -20.4f, 19.6f, 18.4f, 12.4f, 29.6f, 23.6f, -55.6f, -5.6f, -47.4f, 1.6f, 40.4f, -7.6f, 49.6f, 0.6f, -9.6f, 40.4f, 3.6f, 49.6f, 30.4f, 36.4f, 39.6f, 45.6f, -47.6f, 36.4f, -37.4f, 44.6f, 55.4f, 30.4f, 63.6f, 37.6f, -59.6f, -47.6f, -50.4f, -39.4f, 58.4f, -29.6f, 66.6f, -21.4f, -11.6f, -61.6f, 11.6f, -46.4f, 38.4f, -51.6f, 57.6f, -38.4f)
    /** Andares altos: (minX, minZ, maxX, maxZ) e altura. */
    val ALTOS = floatArrayOf(-33.6f, -31.6f, -18.4f, -24.6f)
    val ALTOS_ALTURA = floatArrayOf(3.2f)
    /** Rampas/escadas: (minX, minZ, maxX, maxZ), altura em baixo e em cima, e para onde sobem (0=-Z, 1=+Z, 2=+X, 3=-X). */
    val RAMPAS = floatArrayOf(-33.6f, -24.6f, -31.4f, -19f)
    val RAMPAS_ALTURAS = floatArrayOf(0f, 3.2f)
    val RAMPAS_EIXO = intArrayOf(0)
    /** Area que os zumbis nao pisam (escada). */
    val BLOQUEIOS_INIMIGO = floatArrayOf(-33.6f, -24.6f, -31.4f, -19f)
    val ITENS_XZ = floatArrayOf(-21f, -28.5f, -31f, -29f, -24f, -22f, 0f, 3f, 3f, 3f, -3f, 3f, 21f, -26f, 24f, -24f, -26f, 16f, 24f, 18f, -52f, -2f, 44f, -3f, -3f, 46f, 34f, 42f, -43f, 40f, 0f, -56f, 6f, -57f, -6f, -57f, 48f, -46f, 44f, -44f, -56f, -44f, 62f, -26f)
    val ITENS_Y = floatArrayOf(3.2f, 3.2f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    /** 0 cura, 1 municao, 2 fuzil, 3 escopeta. */
    val ITENS_TIPO = intArrayOf(3, 1, 0, 0, 1, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 2, 0, 1, 1, 0, 0, 1)
    val INIMIGOS_XZ = floatArrayOf(-10f, -14f, 12f, 10f, -22f, 22f, 26f, -20f, 4f, 34f, -40f, -6f, 48f, 20f)
    /** Pontos onde zumbis novos podem nascer (longe do jogador e fora da vista). */
    val SPAWNS_XZ = floatArrayOf(-74f, -74f, 74f, -74f, -74f, 74f, 74f, 74f, 0f, -76f, 0f, 76f, -76f, 0f, 76f, 0f, -60f, -30f, 60f, 20f, 40f, 72f, -40f, 72f, -70f, 30f, 70f, -50f, -30f, -72f, 30f, 74f)

    // Câmera contra paredes
    const val CAMERA_MARGEM_PAREDE = 0.3f
    const val CAMERA_FATOR_MINIMO = 0.2f // a câmera nunca chega mais perto que 20% da distância

    // Minimapa funcional (norte fixo: -Z para cima)
    const val MINIMAPA_PAREDE_COR = 0.80f
    const val MINIMAPA_PAREDE_ALFA = 0.90f
    const val MINIMAPA_INIMIGO_COR_R = 1.0f
    const val MINIMAPA_INIMIGO_COR_G = 0.25f
    const val MINIMAPA_INIMIGO_COR_B = 0.25f

    // Marco F3: personagens animados (modelos .g3dj gerados no F2, copiados para android/assets/modelos)
    const val MODELO_JOGADOR = "modelos/jogador.g3dj"
    const val MODELO_ZUMBI = "modelos/zumbi.g3dj"
    /** Ossos por modelo no shader (o boneco tem 18; o padrao do LibGDX e 12). */
    const val NUM_OSSOS_SHADER = 20
    const val ANIM_TRANSICAO_MARCHA_S = 0.25f   // fusao andar <-> correr <-> parado (mais longa = mais suave)
    const val ANIM_FILTRO_ENTRADA = 9f          // filtro do joystick (1/s)
    const val ANIM_FILTRO_VELOCIDADE = 6f       // filtro da velocidade de reproducao (1/s)
    const val ANIM_LIMIAR_INICIAR = 0.12f
    const val ANIM_LIMIAR_PARAR = 0.05f
    const val ANIM_TRANSICAO_S = 0.22f
    const val ANIM_TRANSICAO_TIRO_S = 0.12f
    /** Joystick acima disto = correr; abaixo = andar. */
    const val JOGADOR_LIMIAR_CORRER = 0.6f
    /** Velocidade de reproducao do ciclo `correr` do zumbi (o ciclo cobre ~2,1 m/s; o zumbi anda a 2 m/s). */
    /** Velocidade (m/s) que cada ciclo do jogador cobre a 1x; a animacao acelera/desacelera com o joystick (sem patinar). */
    const val JOGADOR_ANIM_REF_ANDAR = 1.25f
    const val JOGADOR_ANIM_REF_CORRER = 2.9f
    const val ZUMBI_ANIM_VELOCIDADE = 0.95f
    const val ENIMIGO_VELOCIDADE_GIRO = 8f
    /** Quanto tempo o corpo do zumbi fica visivel depois de morrer. */
    const val CORPO_DURACAO_S = 5f
    /** Clarao no cano com o modelo animado (maos na altura do peito, ~0,85 m a frente). */
    const val CLARAO_MODELO_ALTURA = 1.22f
    const val CLARAO_MODELO_FRENTE = 0.85f
    /** Tinta do zumbi com textura (branco = textura pura). */
    const val ZUMBI_TINTA_PERSEGUINDO_R = 1.0f
    const val ZUMBI_TINTA_PERSEGUINDO_G = 0.85f
    const val ZUMBI_TINTA_PERSEGUINDO_B = 0.65f
    const val ZUMBI_TINTA_ATACANDO_R = 1.0f
    const val ZUMBI_TINTA_ATACANDO_G = 0.60f
    const val ZUMBI_TINTA_ATACANDO_B = 0.55f
    const val ZUMBI_TINTA_FLASH = 2.2f

    // ---- F5: movimento estilo CoD Mobile (sem deslizar), vila e respawn ----
    const val JOGADOR_SPRINT_FATOR = 1.3f // corrida automatica com o joystick no maximo
    const val JOGADOR_SPRINT_LIMIAR = 0.92f
    const val JOGADOR_FATOR_AGACHADO = 0.5f
    const val JOGADOR_FATOR_ATIRANDO = 0.7f // andar atirando/recarregando
    const val JOGADOR_PULO_VELOCIDADE = 7.2f // m/s para cima (altura ~1,3 m: passa por cercas de 1 m)
    const val GRAVIDADE = 20f
    const val DEGRAU_MAXIMO = 0.6f // sobe sozinho degraus/rampas ate isso
    const val BOTAO_PULAR_COR_R = 0.60f
    const val BOTAO_PULAR_COR_G = 0.35f
    const val BOTAO_PULAR_COR_B = 0.80f
    const val BOTAO_AGACHAR_COR_R = 0.35f
    const val BOTAO_AGACHAR_COR_G = 0.45f
    const val BOTAO_AGACHAR_COR_B = 0.55f
    const val ITEM_TIPO_FUZIL = 2
    const val ITEM_TIPO_ESCOPETA = 3
    const val PAREDE_COR_CASA_R = 0.48f
    const val PAREDE_COR_CASA_G = 0.34f
    const val PAREDE_COR_CASA_B = 0.22f
    const val PAREDE_COR_PEDRA_R = 0.58f
    const val PAREDE_COR_PEDRA_G = 0.58f
    const val PAREDE_COR_PEDRA_B = 0.56f
    const val PAREDE_COR_CERCA_R = 0.55f
    const val PAREDE_COR_CERCA_G = 0.42f
    const val PAREDE_COR_CERCA_B = 0.27f
    const val PAREDE_COR_CELEIRO_R = 0.55f
    const val PAREDE_COR_CELEIRO_G = 0.20f
    const val PAREDE_COR_CELEIRO_B = 0.16f
    const val PAREDE_COR_GRADE_R = 0.32f
    const val PAREDE_COR_GRADE_G = 0.22f
    const val PAREDE_COR_GRADE_B = 0.14f
    const val PAREDE_COR_TRONCO_R = 0.30f
    const val PAREDE_COR_TRONCO_G = 0.22f
    const val PAREDE_COR_TRONCO_B = 0.12f
    const val ALTO_ESPESSURA = 0.2f
    const val ALTO_COR_R = 0.42f
    const val ALTO_COR_G = 0.32f
    const val ALTO_COR_B = 0.20f
    const val RAMPA_COR_R = 0.50f
    const val RAMPA_COR_G = 0.40f
    const val RAMPA_COR_B = 0.25f
    const val INIMIGOS_POOL = 14 // zumbis que podem existir ao mesmo tempo (vivos + corpos)
    const val SPAWN_MAX_VIVOS = 9
    const val SPAWN_INTERVALO_S = 7f
    const val SPAWN_DIST_MIN = 45f // so nasce a mais de 45 m do jogador
    const val SPAWN_COS_VISAO = 0.25f // atras/lado da camera: cos do angulo com a frente < 0,25 (~75 graus)
    const val ENIMIGO_VELOCIDADE_CACA = 1.5f // zumbi novo vem andando ate o jogador
    const val ENIMIGO_CACA_REPLANO_S = 1.5f
    const val ENIMIGO_DY_ATAQUE = 1.5f // so ataca se estiver na mesma altura (andar de cima fica seguro)
    const val ITEM_ARMA_ESCALA_Y = 0.15f
}
