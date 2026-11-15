// core/src/main/kotlin/com/sobrevivente/jogo/Jogo.kt
package com.sobrevivente.jogo

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.ScreenUtils

/**
 * Marco 1.0: só prova que o projeto compila, instala e abre.
 * Mostra fundo de cor sólida e "OK" por 3 s; depois mostra o FPS.
 */
class Jogo : ApplicationAdapter() {

    private lateinit var lote: SpriteBatch
    private lateinit var fonte: BitmapFont

    private val texto = StringBuilder(32) // reutilizado: zero alocação por frame
    private var tempoDecorrido = 0f

    override fun create() {
        lote = SpriteBatch()
        fonte = BitmapFont()
        fonte.data.setScale(Constantes.ESCALA_FONTE * Gdx.graphics.density)
        Gdx.app.log("JOGO", "create")
    }

    override fun render() {
        val delta = minOf(Gdx.graphics.deltaTime, Constantes.DELTA_MAXIMO)
        tempoDecorrido += delta

        ScreenUtils.clear(
            Constantes.COR_FUNDO_R,
            Constantes.COR_FUNDO_G,
            Constantes.COR_FUNDO_B,
            1f
        )

        texto.setLength(0)
        if (tempoDecorrido < Constantes.TEMPO_TELA_OK_SEGUNDOS) {
            texto.append("OK")
        } else {
            texto.append("FPS: ").append(Gdx.graphics.framesPerSecond)
        }

        val margem = Constantes.MARGEM_SEGURA_DP * Gdx.graphics.density

        lote.begin()
        fonte.draw(lote, texto, margem, Gdx.graphics.height - margem)
        lote.end()
    }

    override fun resize(width: Int, height: Int) {
        // O SpriteBatch usa a projeção padrão da tela; atualiza ao redimensionar.
        lote.projectionMatrix.setToOrtho2D(0f, 0f, width.toFloat(), height.toFloat())
        Gdx.app.log("JOGO", "resize " + width + "x" + height)
    }

    override fun pause() {
        Gdx.app.log("JOGO", "pause")
    }

    override fun resume() {
        Gdx.app.log("JOGO", "resume")
    }

    override fun dispose() {
        lote.dispose()
        fonte.dispose()
        Gdx.app.log("JOGO", "dispose")
    }
}
