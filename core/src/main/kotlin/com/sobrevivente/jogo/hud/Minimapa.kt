// core/src/main/kotlin/com/sobrevivente/jogo/hud/Minimapa.kt
package com.sobrevivente.jogo.hud

import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.sobrevivente.jogo.Constantes

/** Marco 1.3: minimapa placeholder (quadrado + ponto central). Funcional só no Marco 1.8. */
class Minimapa {

    private var x = 0f
    private var y = 0f
    private var lado = 0f
    private var borda = 0f
    private var raioPonto = 0f

    fun redimensionar(larguraTela: Int, alturaTela: Int, densidade: Float) {
        lado = Constantes.MINIMAPA_LADO_DP * densidade
        borda = Constantes.MINIMAPA_BORDA_DP * densidade
        raioPonto = Constantes.MINIMAPA_PONTO_DP * densidade
        val margem = Constantes.MARGEM_SEGURA_DP * densidade
        x = larguraTela - margem - lado
        y = alturaTela - margem - lado
    }

    /** Chamar entre forma.begin(Filled) e forma.end(), com blending ligado. */
    fun desenhar(forma: ShapeRenderer) {
        forma.setColor(1f, 1f, 1f, Constantes.MINIMAPA_BORDA_ALFA)
        forma.rect(x, y, lado, lado)

        val fundo = Constantes.MINIMAPA_FUNDO_COR
        forma.setColor(fundo, fundo, fundo, Constantes.MINIMAPA_FUNDO_ALFA)
        forma.rect(x + borda, y + borda, lado - 2f * borda, lado - 2f * borda)

        forma.setColor(1f, 1f, 1f, 1f)
        forma.circle(x + lado * 0.5f, y + lado * 0.5f, raioPonto, Constantes.MINIMAPA_SEGMENTOS)
    }
}
