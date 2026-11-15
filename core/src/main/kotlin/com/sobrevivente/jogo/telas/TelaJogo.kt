// core/src/main/kotlin/com/sobrevivente/jogo/telas/TelaJogo.kt
package com.sobrevivente.jogo.telas

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.ScreenUtils
import com.sobrevivente.jogo.Constantes
import com.sobrevivente.jogo.camera.CameraTerceiraPessoa
import com.sobrevivente.jogo.entrada.ControlesToque
import com.sobrevivente.jogo.hud.Joystick
import com.sobrevivente.jogo.mundo.Jogador
import com.sobrevivente.jogo.mundo.Mundo

/** Marco 1.2: joystick, movimento relativo à câmera e câmera de terceira pessoa. */
class TelaJogo : ScreenAdapter() {

    private val mundo = Mundo()
    private val jogador = Jogador()
    private val cameraJogo = CameraTerceiraPessoa()
    private val controles = ControlesToque()
    private val joystick = Joystick(controles)

    private val loteModelos = ModelBatch()
    private val lote = SpriteBatch()
    private val forma = ShapeRenderer()
    private val fonte = BitmapFont()

    private val texto = StringBuilder(32) // reutilizado: zero alocação por frame

    init {
        fonte.data.setScale(Constantes.ESCALA_FONTE * Gdx.graphics.density)
        cameraJogo.seguir(jogador.posicao)
    }

    override fun show() {
        Gdx.input.inputProcessor = controles
    }

    override fun hide() {
        Gdx.input.inputProcessor = null
    }

    override fun pause() {
        controles.liberarTudo()
    }

    override fun render(delta: Float) {
        val dt = minOf(delta, Constantes.DELTA_MAXIMO)
        val densidade = Gdx.graphics.density

        // Lógica
        cameraJogo.girar(
            controles.consumirGiroX() / densidade,
            controles.consumirGiroY() / densidade
        )
        jogador.mover(dt, controles.movimentoX, controles.movimentoY, cameraJogo.yaw)
        cameraJogo.seguir(jogador.posicao)

        // Desenho 3D
        ScreenUtils.clear(
            Constantes.COR_CEU_R,
            Constantes.COR_CEU_G,
            Constantes.COR_CEU_B,
            1f,
            true
        )
        loteModelos.begin(cameraJogo.camera)
        loteModelos.render(mundo.chao, mundo.ambiente)
        loteModelos.render(jogador.corpo, mundo.ambiente)
        loteModelos.render(jogador.nariz, mundo.ambiente)
        loteModelos.end()

        // Joystick (formas com transparência)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        forma.begin(ShapeRenderer.ShapeType.Filled)
        joystick.desenhar(forma)
        forma.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // Texto de FPS
        texto.setLength(0)
        texto.append("FPS: ").append(Gdx.graphics.framesPerSecond)
        val margem = Constantes.MARGEM_SEGURA_DP * densidade
        lote.begin()
        fonte.draw(lote, texto, margem, Gdx.graphics.height - margem)
        lote.end()
    }

    override fun resize(width: Int, height: Int) {
        cameraJogo.redimensionar(width, height)
        controles.redimensionar(width, height, Gdx.graphics.density)
        lote.projectionMatrix.setToOrtho2D(0f, 0f, width.toFloat(), height.toFloat())
        forma.projectionMatrix.setToOrtho2D(0f, 0f, width.toFloat(), height.toFloat())
        forma.updateMatrices()
        Gdx.app.log("TELA", "resize " + width + "x" + height)
    }

    override fun dispose() {
        loteModelos.dispose()
        lote.dispose()
        forma.dispose()
        fonte.dispose()
        jogador.dispose()
        mundo.dispose()
    }
}
