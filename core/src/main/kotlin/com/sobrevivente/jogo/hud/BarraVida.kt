// core/src/main/kotlin/com/sobrevivente/jogo/hud/BarraVida.kt
package com.sobrevivente.jogo.hud

import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.sobrevivente.jogo.Constantes

/** Barra de vida no canto superior esquerdo, com o número ao lado (seção 10.6). */
class BarraVida {

    var vida = Constantes.JOGADOR_VIDA_MAXIMA
    var vidaMaxima = Constantes.JOGADOR_VIDA_MAXIMA

    private var x = 0f
    private var y = 0f
    private var largura = 0f
    private var altura = 0f
    private var borda = 0f
    private var espacoTexto = 0f

    private val texto = StringBuilder(8) // reutilizado: zero alocação por frame

    fun redimensionar(larguraTela: Int, alturaTela: Int, densidade: Float) {
        largura = Constantes.BARRA_VIDA_LARGURA_DP * densidade
        altura = Constantes.BARRA_VIDA_ALTURA_DP * densidade
        borda = Constantes.BARRA_VIDA_BORDA_DP * densidade
        espacoTexto = Constantes.BARRA_VIDA_ESPACO_TEXTO_DP * densidade
        val margem = Constantes.MARGEM_SEGURA_DP * densidade
        x = margem
        y = alturaTela - margem - altura
    }

    /** Y da base da barra, para posicionar o overlay de debug logo abaixo. */
    fun baseY(): Float = y - borda

    /** Chamar entre forma.begin(Filled) e forma.end(), com blending ligado. */
    fun desenharFormas(forma: ShapeRenderer) {
        val fundo = Constantes.BARRA_VIDA_FUNDO_COR
        forma.setColor(fundo, fundo, fundo, Constantes.BARRA_VIDA_FUNDO_ALFA)
        forma.rect(x - borda, y - borda, largura + 2f * borda, altura + 2f * borda)

        val proporcao = if (vidaMaxima > 0) {
            (vida.toFloat() / vidaMaxima.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        forma.setColor(
            Constantes.BARRA_VIDA_COR_R,
            Constantes.BARRA_VIDA_COR_G,
            Constantes.BARRA_VIDA_COR_B,
            1f
        )
        forma.rect(x, y, largura * proporcao, altura)
    }

    /** Chamar entre lote.begin() e lote.end(), com a fonte já na escala do HUD. */
    fun desenharTexto(lote: SpriteBatch, fonte: BitmapFont) {
        texto.setLength(0)
        texto.append(vida)
        fonte.draw(
            lote,
            texto,
            x + largura + espacoTexto,
            y + altura * 0.5f + fonte.capHeight * 0.5f
        )
    }
}
