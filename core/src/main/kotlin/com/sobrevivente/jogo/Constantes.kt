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
}
