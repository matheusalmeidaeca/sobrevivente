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
    const val CHAO_TAMANHO = 60f
    const val CHAO_LADO_QUADRADO = 2f
    const val CHAO_CLARO_R = 0.42f
    const val CHAO_CLARO_G = 0.45f
    const val CHAO_CLARO_B = 0.40f
    const val CHAO_ESCURO_R = 0.30f
    const val CHAO_ESCURO_G = 0.33f
    const val CHAO_ESCURO_B = 0.29f

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
    const val CAMERA_FAR = 80f
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
    /** Posições (x, z) dos inimigos, em metros; o jogador começa em (0, 0). Todos começam parados. */
    val INIMIGOS_XZ = floatArrayOf(
        0f, -14f,
        -9f, -12f,
        10f, -13f
    )

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
    /** Posições (x, z) dos itens, em metros. Ficam atrás do jogador, longe dos inimigos (z positivo). */
    val ITENS_XZ = floatArrayOf(
        -6f, 5f, -3f, 5f, 0f, 5f, 3f, 5f, 6f, 5f,
        -6f, 9f, -3f, 9f, 0f, 9f, 3f, 9f, 6f, 9f
    )
    /** Tipo de cada item (mesma ordem de ITENS_XZ): 8 curas e 2 munições. 9 slots > 8: testa "Maleta cheia". */
    val ITENS_TIPO = intArrayOf(
        ITEM_TIPO_CURA, ITEM_TIPO_CURA, ITEM_TIPO_CURA, ITEM_TIPO_CURA, ITEM_TIPO_CURA,
        ITEM_TIPO_CURA, ITEM_TIPO_CURA, ITEM_TIPO_CURA, ITEM_TIPO_MUNICAO, ITEM_TIPO_MUNICAO
    )

    // ---------------- Marco 1.8: mapa de teste, colisão e minimapa ----------------
    const val PAREDE_ALTURA = 3f
    const val PAREDE_COR_R = 0.55f
    const val PAREDE_COR_G = 0.52f
    const val PAREDE_COR_B = 0.48f
    const val PISO_INTERNO_ESPESSURA = 0.02f
    const val PISO_INTERNO_COR_R = 0.24f
    const val PISO_INTERNO_COR_G = 0.21f
    const val PISO_INTERNO_COR_B = 0.19f
    /** Piso da área interna (minX, minZ, maxX, maxZ): o prédio. */
    val PISO_INTERNO = floatArrayOf(-14f, -24f, 14f, -8f)

    /**
     * Paredes e obstáculos, 4 números por caixa: minX, minZ, maxX, maxZ (metros).
     * Prédio ao norte (z negativo) com um vão de 3 m na parede sul (a "porta"), 2 pilares dentro
     * e 2 caixas fora. As paredes têm 0,4 m de espessura.
     */
    val PAREDES = floatArrayOf(
        // prédio: norte, oeste, leste
        -14.4f, -24.4f, 14.4f, -24f,
        -14.4f, -24f, -14f, -8f,
        14f, -24f, 14.4f, -8f,
        // prédio: sul, com vão entre x = -1,5 e x = 1,5
        -14.4f, -8.4f, -1.5f, -8f,
        1.5f, -8.4f, 14.4f, -8f,
        // pilares dentro do prédio
        -6f, -18f, -4.6f, -16.6f,
        4.6f, -18f, 6f, -16.6f,
        // caixas na área externa
        -8f, -4f, -6f, -2f,
        6f, -5f, 8f, -3f
    )

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
}
