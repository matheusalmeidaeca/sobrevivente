// core/src/main/kotlin/com/sobrevivente/jogo/telas/TelaJogo.kt
package com.sobrevivente.jogo.telas

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.ScreenUtils
import com.sobrevivente.jogo.Constantes
import com.sobrevivente.jogo.mundo.Jogador
import com.sobrevivente.jogo.mundo.Mundo

/** Marco 1.1: chão 3D, jogador placeholder, luz e câmera provisória orbitando. */
class TelaJogo : ScreenAdapter() {

    private val mundo = Mundo()
    private val jogador = Jogador()
    private val loteModelos = ModelBatch()
    private val lote = SpriteBatch()
    private val fonte = BitmapFont()
    private val camera = PerspectiveCamera(
        Constantes.CAMERA_FOV,
        Gdx.graphics.width.toFloat(),
        Gdx.graphics.height.toFloat()
    )

    private val texto = StringBuilder(32) // reutilizado: zero alocação por frame
    private var anguloOrbita = 0f

    init {
        camera.near = Constantes.CAMERA_NEAR
        camera.far = Constantes.CAMERA_FAR
        fonte.data.setScale(Constantes.ESCALA_FONTE * Gdx.graphics.density)
        atualizarCamera()
    }

    private fun atualizarCamera() {
        val radianos = anguloOrbita * MathUtils.degreesToRadians
        camera.position.set(
            jogador.posicao.x + MathUtils.sin(radianos) * Constantes.CAMERA_ORBITA_DISTANCIA,
            jogador.posicao.y + Constantes.CAMERA_ORBITA_ALTURA,
            jogador.posicao.z + MathUtils.cos(radianos) * Constantes.CAMERA_ORBITA_DISTANCIA
        )
        camera.up.set(0f, 1f, 0f)
        camera.lookAt(
            jogador.posicao.x,
            jogador.posicao.y + Constantes.CAMERA_ALVO_ALTURA,
            jogador.posicao.z
        )
        camera.update()
    }

    override fun render(delta: Float) {
        val dt = minOf(delta, Constantes.DELTA_MAXIMO)
        anguloOrbita = (anguloOrbita + Constantes.CAMERA_ORBITA_GRAUS_POR_SEGUNDO * dt) % 360f
        atualizarCamera()

        ScreenUtils.clear(
            Constantes.COR_CEU_R,
            Constantes.COR_CEU_G,
            Constantes.COR_CEU_B,
            1f,
            true
        )

        loteModelos.begin(camera)
        loteModelos.render(mundo.chao, mundo.ambiente)
        loteModelos.render(jogador.corpo, mundo.ambiente)
        loteModelos.render(jogador.nariz, mundo.ambiente)
        loteModelos.end()

        texto.setLength(0)
        texto.append("FPS: ").append(Gdx.graphics.framesPerSecond)
        val margem = Constantes.MARGEM_SEGURA_DP * Gdx.graphics.density

        lote.begin()
        fonte.draw(lote, texto, margem, Gdx.graphics.height - margem)
        lote.end()
    }

    override fun resize(width: Int, height: Int) {
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        camera.update()
        lote.projectionMatrix.setToOrtho2D(0f, 0f, width.toFloat(), height.toFloat())
        Gdx.app.log("TELA", "resize " + width + "x" + height)
    }

    override fun dispose() {
        loteModelos.dispose()
        lote.dispose()
        fonte.dispose()
        jogador.dispose()
        mundo.dispose()
    }
}
