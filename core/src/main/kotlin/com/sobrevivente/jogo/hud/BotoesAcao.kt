// core/src/main/kotlin/com/sobrevivente/jogo/hud/BotoesAcao.kt
package com.sobrevivente.jogo.hud

import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.sobrevivente.jogo.Constantes
import com.sobrevivente.jogo.entrada.ControlesToque

/**
 * Visual dos botoes circulares (F10, estilo controle de PS2): os 4 do losango levam o simbolo do PS2
 * (triangulo, circulo, X, quadrado) sobre um miolo escuro, com o nome pequeno embaixo; ATIRAR/TROCAR/CURAR sao coloridos.
 * A geometria e o toque ficam em ControlesToque; os 4 slots de arma sao desenhados pelo Hud.
 */
class BotoesAcao(
    private val controles: ControlesToque,
    private val fonte: BitmapFont
) {
    private val n = ControlesToque.NUM_BOTOES_CIRCULARES
    private val textos = arrayOf("ATIRAR", "RECARR.", "PEGAR", "TROCAR", "PULAR", "AGACHAR", "CURAR", "ATIRAR")

    // Cor do miolo de cada botao (R, G, B)
    private val cores = floatArrayOf(
        Constantes.BOTAO_ATIRAR_COR_R, Constantes.BOTAO_ATIRAR_COR_G, Constantes.BOTAO_ATIRAR_COR_B,
        0.13f, 0.14f, 0.18f, // RECARREGAR (quadrado)
        0.13f, 0.14f, 0.18f, // INTERAGIR (triangulo)
        Constantes.BOTAO_TROCAR_COR_R, Constantes.BOTAO_TROCAR_COR_G, Constantes.BOTAO_TROCAR_COR_B,
        0.13f, 0.14f, 0.18f, // PULAR (X)
        0.13f, 0.14f, 0.18f, // AGACHAR (circulo)
        0.16f, 0.50f, 0.26f, // CURAR
        Constantes.BOTAO_ATIRAR_COR_R, Constantes.BOTAO_ATIRAR_COR_G, Constantes.BOTAO_ATIRAR_COR_B
    )

    // Cor dos simbolos PS2 (quadrado rosa, triangulo verde, X azul, circulo vermelho)
    private val simboloCor = floatArrayOf(
        0f, 0f, 0f,
        0.95f, 0.55f, 0.80f,
        0.35f, 0.85f, 0.65f,
        0f, 0f, 0f,
        0.45f, 0.62f, 0.98f,
        0.95f, 0.35f, 0.40f,
        1f, 1f, 1f,
        0f, 0f, 0f
    )

    private val layouts = Array(n) { GlyphLayout() }
    private val escalas = FloatArray(n) { 1f }

    private fun temSimbolo(i: Int): Boolean = i == 1 || i == 2 || i == 4 || i == 5 || i == 6

    /** Recalcula o tamanho do texto de cada botao para caber dentro do circulo. */
    fun redimensionar(densidade: Float) {
        fonte.setColor(1f, 1f, 1f, 1f)
        for (i in 0 until n) {
            val larguraMaxima = controles.acaoRaio(i) * 2f * Constantes.BOTAO_TEXTO_LARGURA_MAX
            fonte.data.setScale(1f)
            layouts[i].setText(fonte, textos[i])
            val larguraBase = layouts[i].width
            var escala = Constantes.FONTE_ESCALA_BOTAO * densidade * (if (temSimbolo(i)) 0.62f else 1f)
            if (larguraBase > 0f && larguraBase * escala > larguraMaxima) escala = larguraMaxima / larguraBase
            escalas[i] = escala
            fonte.data.setScale(escala)
            layouts[i].setText(fonte, textos[i])
        }
    }

    /** Chamar entre forma.begin(Filled) e forma.end(), com blending ligado. */
    fun desenharFormas(forma: ShapeRenderer) {
        for (i in 0 until n) {
            val pressionado = controles.acaoPressionada(i)
            val raio = controles.acaoRaio(i) * (if (pressionado) Constantes.BOTAO_ESCALA_PRESSIONADO else 1f)
            val fator = if (pressionado) Constantes.BOTAO_ESCURECER else 1f
            val x = controles.acaoX(i)
            val y = controles.acaoY(i)
            val r = cores[i * 3] * fator
            val g = cores[i * 3 + 1] * fator
            val b = cores[i * 3 + 2] * fator
            val seg = Constantes.BOTAO_SEGMENTOS

            forma.setColor(0f, 0f, 0f, 0.28f) // sombra
            forma.circle(x, y - raio * 0.06f, raio * 1.08f, seg)
            forma.setColor(1f, 1f, 1f, if (pressionado) 0.35f else 0.60f) // aro
            forma.circle(x, y, raio * 1.02f, seg)
            forma.setColor(r * 0.55f, g * 0.55f, b * 0.55f, 0.92f) // borda escura do miolo
            forma.circle(x, y, raio * 0.96f, seg)
            forma.setColor(r, g, b, 0.90f) // miolo
            forma.circle(x, y + raio * 0.02f, raio * 0.86f, seg)
            forma.setColor(1f, 1f, 1f, if (pressionado) 0.04f else 0.14f) // brilho
            forma.circle(x - raio * 0.10f, y + raio * 0.34f, raio * 0.46f, seg)
            if (temSimbolo(i)) desenharSimbolo(forma, i, x, y + raio * 0.24f, raio * 0.34f, r, g, b, pressionado)
        }
    }

    private fun desenharSimbolo(
        forma: ShapeRenderer, i: Int, x: Float, y: Float, s: Float,
        fundoR: Float, fundoG: Float, fundoB: Float, pressionado: Boolean
    ) {
        val a = if (pressionado) 0.7f else 1f
        val cr = simboloCor[i * 3]
        val cg = simboloCor[i * 3 + 1]
        val cb = simboloCor[i * 3 + 2]
        forma.setColor(cr, cg, cb, a)
        when (i) {
            1 -> { // quadrado
                forma.rect(x - s, y - s, 2f * s, 2f * s)
                forma.setColor(fundoR, fundoG, fundoB, 1f)
                forma.rect(x - s * 0.62f, y - s * 0.62f, 1.24f * s, 1.24f * s)
            }
            2 -> { // triangulo
                forma.triangle(x, y + s * 1.1f, x - s * 0.95f, y - s * 0.55f, x + s * 0.95f, y - s * 0.55f)
                forma.setColor(fundoR, fundoG, fundoB, 1f)
                forma.triangle(x, y + s * 0.5f, x - s * 0.42f, y - s * 0.25f, x + s * 0.42f, y - s * 0.25f)
            }
            4 -> { // X
                val e = s * 0.36f
                forma.rectLine(x - s, y - s, x + s, y + s, e)
                forma.rectLine(x - s, y + s, x + s, y - s, e)
            }
            5 -> { // circulo
                forma.circle(x, y, s, Constantes.BOTAO_SEGMENTOS)
                forma.setColor(fundoR, fundoG, fundoB, 1f)
                forma.circle(x, y, s * 0.62f, Constantes.BOTAO_SEGMENTOS)
            }
            6 -> { // cruz de cura
                forma.rect(x - s * 0.22f, y - s, s * 0.44f, 2f * s)
                forma.rect(x - s, y - s * 0.22f, 2f * s, s * 0.44f)
            }
        }
    }

    /** Chamar entre lote.begin() e lote.end(). */
    fun desenharTextos(lote: SpriteBatch) {
        for (i in 0 until n) {
            val layout = layouts[i]
            fonte.data.setScale(escalas[i])
            val cy = if (temSimbolo(i)) controles.acaoY(i) - controles.acaoRaio(i) * 0.50f else controles.acaoY(i)
            fonte.draw(lote, layout, controles.acaoX(i) - layout.width * 0.5f, cy + layout.height * 0.5f)
        }
    }
}
