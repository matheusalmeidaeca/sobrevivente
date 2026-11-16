// core/src/main/kotlin/com/sobrevivente/jogo/hud/BotoesAcao.kt
package com.sobrevivente.jogo.hud

import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.sobrevivente.jogo.Constantes
import com.sobrevivente.jogo.entrada.ControlesToque

/** Marco 1.3: só o visual dos 4 botões. A geometria e o toque ficam em ControlesToque. */
class BotoesAcao(
    private val controles: ControlesToque,
    private val fonte: BitmapFont
) {
    private val textos = arrayOf("ATIRAR", "RECARREGAR", "INTERAGIR", "TROCAR", "PULAR", "AGACHAR")

    // Cor de cada botão: R, G, B (3 valores por botão, na ordem das ações)
    private val cores = floatArrayOf(
        Constantes.BOTAO_ATIRAR_COR_R, Constantes.BOTAO_ATIRAR_COR_G, Constantes.BOTAO_ATIRAR_COR_B,
        Constantes.BOTAO_RECARREGAR_COR_R, Constantes.BOTAO_RECARREGAR_COR_G, Constantes.BOTAO_RECARREGAR_COR_B,
        Constantes.BOTAO_INTERAGIR_COR_R, Constantes.BOTAO_INTERAGIR_COR_G, Constantes.BOTAO_INTERAGIR_COR_B,
        Constantes.BOTAO_TROCAR_COR_R, Constantes.BOTAO_TROCAR_COR_G, Constantes.BOTAO_TROCAR_COR_B,
        Constantes.BOTAO_PULAR_COR_R, Constantes.BOTAO_PULAR_COR_G, Constantes.BOTAO_PULAR_COR_B,
        Constantes.BOTAO_AGACHAR_COR_R, Constantes.BOTAO_AGACHAR_COR_G, Constantes.BOTAO_AGACHAR_COR_B
    )

    // Um layout por botão, calculado uma vez no resize (zero alocação por frame)
    private val layouts = Array(ControlesToque.NUM_ACOES) { GlyphLayout() }

    /** Recalcula o tamanho do texto de cada botão para caber dentro do círculo. */
    fun redimensionar(densidade: Float) {
        fonte.setColor(1f, 1f, 1f, 1f)
        for (i in 0 until ControlesToque.NUM_ACOES) {
            val larguraMaxima = controles.acaoRaio(i) * 2f * Constantes.BOTAO_TEXTO_LARGURA_MAX

            fonte.data.setScale(1f)
            layouts[i].setText(fonte, textos[i])
            val larguraBase = layouts[i].width

            var escala = Constantes.FONTE_ESCALA_BOTAO * densidade
            if (larguraBase > 0f && larguraBase * escala > larguraMaxima) {
                escala = larguraMaxima / larguraBase
            }
            fonte.data.setScale(escala)
            layouts[i].setText(fonte, textos[i])
        }
    }

    /** Chamar entre forma.begin(Filled) e forma.end(), com blending ligado. */
    fun desenharFormas(forma: ShapeRenderer) {
        for (i in 0 until ControlesToque.NUM_ACOES) {
            val pressionado = controles.acaoPressionada(i)
            val raio = controles.acaoRaio(i) *
                (if (pressionado) Constantes.BOTAO_ESCALA_PRESSIONADO else 1f)
            val fator = if (pressionado) Constantes.BOTAO_ESCURECER else 1f
            forma.setColor(
                cores[i * 3] * fator,
                cores[i * 3 + 1] * fator,
                cores[i * 3 + 2] * fator,
                Constantes.BOTAO_ALFA
            )
            forma.circle(controles.acaoX(i), controles.acaoY(i), raio, Constantes.BOTAO_SEGMENTOS)
        }
    }

    /** Chamar entre lote.begin() e lote.end(). */
    fun desenharTextos(lote: SpriteBatch) {
        for (i in 0 until ControlesToque.NUM_ACOES) {
            val layout = layouts[i]
            fonte.draw(
                lote,
                layout,
                controles.acaoX(i) - layout.width * 0.5f,
                controles.acaoY(i) + layout.height * 0.5f
            )
        }
    }
}
