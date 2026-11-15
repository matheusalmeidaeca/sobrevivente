// core/src/main/kotlin/com/sobrevivente/jogo/hud/Joystick.kt
package com.sobrevivente.jogo.hud

import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.sobrevivente.jogo.Constantes
import com.sobrevivente.jogo.entrada.ControlesToque

/** Só desenha o joystick (e o ícone de maleta no botão central). A lógica fica em ControlesToque. */
class Joystick(private val controles: ControlesToque) {

    /** Chamar entre forma.begin(Filled) e forma.end(), com blending ligado. */
    fun desenhar(forma: ShapeRenderer) {
        forma.setColor(1f, 1f, 1f, Constantes.JOYSTICK_ALFA_BASE)
        forma.circle(
            controles.centroX,
            controles.centroY,
            controles.raioBase,
            Constantes.JOYSTICK_SEGMENTOS
        )
        forma.setColor(1f, 1f, 1f, Constantes.JOYSTICK_ALFA_BOTAO)
        forma.circle(
            controles.botaoX,
            controles.botaoY,
            controles.raioBotao,
            Constantes.JOYSTICK_SEGMENTOS
        )
        desenharIconeMaleta(forma, controles.botaoX, controles.botaoY, controles.raioBotao)
    }

    /** Maleta feita de retângulos: corpo, alça (2 hastes + barra) e fecho. Segue o botão. */
    private fun desenharIconeMaleta(forma: ShapeRenderer, x: Float, y: Float, raio: Float) {
        val corpoLargura = raio * Constantes.ICONE_MALETA_LARGURA
        val corpoAltura = raio * Constantes.ICONE_MALETA_ALTURA
        val alcaLargura = raio * Constantes.ICONE_MALETA_ALCA_LARGURA
        val alcaAltura = raio * Constantes.ICONE_MALETA_ALCA_ALTURA
        val espessura = raio * Constantes.ICONE_MALETA_ALCA_ESPESSURA

        val corpoBaixo = y - (corpoAltura + alcaAltura) * 0.5f
        val corpoTopo = corpoBaixo + corpoAltura
        val alcaEsquerda = x - alcaLargura * 0.5f

        val cor = Constantes.ICONE_MALETA_COR
        forma.setColor(cor, cor, cor, Constantes.ICONE_MALETA_ALFA)
        forma.rect(x - corpoLargura * 0.5f, corpoBaixo, corpoLargura, corpoAltura)
        forma.rect(alcaEsquerda, corpoTopo, espessura, alcaAltura)
        forma.rect(x + alcaLargura * 0.5f - espessura, corpoTopo, espessura, alcaAltura)
        forma.rect(alcaEsquerda, corpoTopo + alcaAltura - espessura, alcaLargura, espessura)

        val claro = Constantes.ICONE_MALETA_FECHO_COR
        forma.setColor(claro, claro, claro, Constantes.ICONE_MALETA_ALFA)
        val fechoLargura = raio * Constantes.ICONE_MALETA_FECHO_LARGURA
        val fechoAltura = raio * Constantes.ICONE_MALETA_FECHO_ALTURA
        forma.rect(
            x - fechoLargura * 0.5f,
            corpoBaixo + (corpoAltura - fechoAltura) * 0.5f,
            fechoLargura,
            fechoAltura
        )
    }
}
