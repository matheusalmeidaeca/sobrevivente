// core/src/main/kotlin/com/sobrevivente/jogo/hud/Minimapa.kt
package com.sobrevivente.jogo.hud

import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.sobrevivente.jogo.Constantes
import com.sobrevivente.jogo.mundo.Mundo

/**
 * Marco 1.8: minimapa funcional. Mostra o chão inteiro, as paredes, o jogador (ponto branco)
 * e os inimigos vivos (pontos vermelhos). Norte fixo: -Z fica em cima, +X à direita.
 */
class Minimapa {

    private var x = 0f
    private var y = 0f
    private var lado = 0f
    private var borda = 0f
    private var raioPonto = 0f
    private var pixelsPorMetro = 0f

    fun redimensionar(larguraTela: Int, alturaTela: Int, densidade: Float) {
        lado = Constantes.MINIMAPA_LADO_DP * densidade
        borda = Constantes.MINIMAPA_BORDA_DP * densidade
        raioPonto = Constantes.MINIMAPA_PONTO_DP * densidade
        pixelsPorMetro = lado / Constantes.CHAO_TAMANHO
        val margem = Constantes.MARGEM_SEGURA_DP * densidade
        x = larguraTela - margem - lado
        y = alturaTela - margem - lado
    }

    // Mundo -> minimapa (pixels). A metade do chão fica de cada lado do centro.
    private fun telaX(mundoX: Float): Float = x + (mundoX + Constantes.CHAO_TAMANHO * 0.5f) * pixelsPorMetro

    private fun telaY(mundoZ: Float): Float = y + (Constantes.CHAO_TAMANHO * 0.5f - mundoZ) * pixelsPorMetro

    /** Chamar entre forma.begin(Filled) e forma.end(), com blending ligado. */
    fun desenhar(forma: ShapeRenderer, jogadorX: Float, jogadorZ: Float, mundo: Mundo) {
        forma.setColor(1f, 1f, 1f, Constantes.MINIMAPA_BORDA_ALFA)
        forma.rect(x, y, lado, lado)

        val fundo = Constantes.MINIMAPA_FUNDO_COR
        forma.setColor(fundo, fundo, fundo, Constantes.MINIMAPA_FUNDO_ALFA)
        forma.rect(x + borda, y + borda, lado - 2f * borda, lado - 2f * borda)

        val parede = Constantes.MINIMAPA_PAREDE_COR
        forma.setColor(parede, parede, parede, Constantes.MINIMAPA_PAREDE_ALFA)
        val caixas = Constantes.PAREDES
        for (i in 0 until caixas.size / 4) {
            val esquerda = telaX(caixas[i * 4])
            val direita = telaX(caixas[i * 4 + 2])
            val baixo = telaY(caixas[i * 4 + 3]) // maxZ fica mais embaixo na tela
            val cima = telaY(caixas[i * 4 + 1])
            forma.rect(esquerda, baixo, direita - esquerda, cima - baixo)
        }

        forma.setColor(
            Constantes.MINIMAPA_INIMIGO_COR_R,
            Constantes.MINIMAPA_INIMIGO_COR_G,
            Constantes.MINIMAPA_INIMIGO_COR_B,
            1f
        )
        for (i in mundo.inimigos.indices) {
            val inimigo = mundo.inimigos[i]
            if (!inimigo.vivo) continue
            forma.circle(
                telaX(inimigo.posicao.x), telaY(inimigo.posicao.z), raioPonto, Constantes.MINIMAPA_SEGMENTOS
            )
        }

        forma.setColor(1f, 1f, 1f, 1f)
        forma.circle(telaX(jogadorX), telaY(jogadorZ), raioPonto, Constantes.MINIMAPA_SEGMENTOS)
    }
}
