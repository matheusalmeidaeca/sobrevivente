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

    // Câmera provisória (orbita devagar; substituída no Marco 1.2)
    const val CAMERA_FOV = 67f
    const val CAMERA_NEAR = 0.1f
    const val CAMERA_FAR = 80f
    const val CAMERA_ORBITA_DISTANCIA = 6f
    const val CAMERA_ORBITA_ALTURA = 3.5f
    const val CAMERA_ALVO_ALTURA = 1.0f
    const val CAMERA_ORBITA_GRAUS_POR_SEGUNDO = 15f
}
