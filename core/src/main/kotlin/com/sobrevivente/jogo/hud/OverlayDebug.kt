// core/src/main/kotlin/com/sobrevivente/jogo/hud/OverlayDebug.kt
package com.sobrevivente.jogo.hud

import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch

/** Texto de debug (FPS e inimigos). Liga/desliga por toque longo no canto superior esquerdo. */
class OverlayDebug {

    var ligado = false
        private set

    private val texto = StringBuilder(48) // reutilizado: zero alocação por frame

    fun alternar() {
        ligado = !ligado
    }

    /** Chamar entre lote.begin() e lote.end(), com a fonte já na escala do HUD. */
    fun desenhar(lote: SpriteBatch, fonte: BitmapFont, fps: Int, inimigos: Int, x: Float, yTopo: Float) {
        if (!ligado) return
        texto.setLength(0)
        texto.append("FPS: ").append(fps).append('\n')
        texto.append("Inimigos: ").append(inimigos)
        fonte.draw(lote, texto, x, yTopo)
    }
}
