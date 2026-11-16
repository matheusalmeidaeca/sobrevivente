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

    /** Chamar entre forma.begin(Filled) e forma.end(), com blending ligado. Cor muda com a vida; marcas a cada 25%. */
    fun desenharFormas(forma: ShapeRenderer) {
        val fundo = Constantes.BARRA_VIDA_FUNDO_COR
        forma.setColor(fundo, fundo, fundo, Constantes.BARRA_VIDA_FUNDO_ALFA)
        forma.rect(x - borda, y - borda, largura + 2f * borda, altura + 2f * borda)
        forma.setColor(0.22f, 0.04f, 0.04f, 0.85f) // parte vazia
        forma.rect(x, y, largura, altura)

        val proporcao = if (vidaMaxima > 0) {
            (vida.toFloat() / vidaMaxima.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        // verde -> amarelo -> vermelho conforme a vida cai
        val r: Float
        val g: Float
        val b: Float
        if (proporcao > 0.5f) {
            val t = (proporcao - 0.5f) * 2f
            r = 0.95f + (Constantes.BARRA_VIDA_COR_R - 0.95f) * t
            g = 0.80f + (Constantes.BARRA_VIDA_COR_G - 0.80f) * t
            b = 0.15f + (Constantes.BARRA_VIDA_COR_B - 0.15f) * t
        } else {
            val t = proporcao * 2f
            r = 0.85f + 0.10f * t
            g = 0.12f + 0.68f * t
            b = 0.10f + 0.05f * t
        }
        forma.setColor(r, g, b, 1f)
        forma.rect(x, y, largura * proporcao, altura)
        forma.setColor(1f, 1f, 1f, 0.22f) // brilho na metade de cima
        forma.rect(x, y + altura * 0.5f, largura * proporcao, altura * 0.5f)
        forma.setColor(0f, 0f, 0f, 0.35f) // marcas de 25%
        val marca = maxOf(1f, borda * 0.6f)
        for (k in 1..3) {
            forma.rect(x + largura * 0.25f * k - marca * 0.5f, y, marca, altura)
        }
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
