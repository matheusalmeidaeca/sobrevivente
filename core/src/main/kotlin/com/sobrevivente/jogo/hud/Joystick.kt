// core/src/main/kotlin/com/sobrevivente/jogo/hud/Joystick.kt
package com.sobrevivente.jogo.hud

import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.sobrevivente.jogo.Constantes
import com.sobrevivente.jogo.entrada.ControlesToque

/** Só desenha o joystick. A lógica fica em ControlesToque. */
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
    }
}
