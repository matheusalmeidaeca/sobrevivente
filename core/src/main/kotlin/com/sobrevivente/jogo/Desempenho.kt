// core/src/main/kotlin/com/sobrevivente/jogo/Desempenho.kt
package com.sobrevivente.jogo

/**
 * Regulador de desempenho: mede o tempo dos quadros e, se o FPS cai, reduz sozinho o que e so enfeite
 * (sombras e distancia de desenho). Sobe a qualidade de volta quando o aparelho folga.
 * Nivel 0 = tudo; 1 = sombras so de perto e desenho ate 110 m; 2 = sem sombras e desenho ate 70 m.
 * Nao mexe na logica do jogo (colisao, zumbis, missao): so no que e desenhado.
 */
object Desempenho {
    var nivel = 0
        private set
    /** Desligue para travar na qualidade maxima. */
    var automatico = true

    private var acumulado = 0f
    private var quadros = 0
    private var lento = 0f      // segundos seguidos abaixo de 40 FPS
    private var folga = 0f      // segundos seguidos acima de 56 FPS

    /** Chamar uma vez por quadro com o delta real (sem limitar). */
    fun medir(delta: Float) {
        if (!automatico) return
        acumulado += delta
        quadros++
        if (acumulado < 0.5f) return
        val fps = quadros / acumulado
        val janela = acumulado
        acumulado = 0f
        quadros = 0
        if (fps < 40f) { lento += janela; folga = 0f }
        else if (fps > 56f) { folga += janela; lento = 0f }
        else { lento = 0f; folga = 0f }
        if (lento >= 2f && nivel < 2) { nivel++; lento = 0f }
        if (folga >= 8f && nivel > 0) { nivel--; folga = 0f }
    }

    fun reiniciar() { nivel = 0; acumulado = 0f; quadros = 0; lento = 0f; folga = 0f }

    val sombras: Boolean get() = nivel < 2
    /** Raio (m) em que paredes entram na sombra. */
    val raioSombra: Float get() = if (nivel == 0) 1e9f else 45f
    /** Raio (m) de desenho de paredes e zumbis. */
    val raioDesenho: Float get() = when (nivel) { 0 -> 1e9f; 1 -> 110f; else -> 70f }
}
