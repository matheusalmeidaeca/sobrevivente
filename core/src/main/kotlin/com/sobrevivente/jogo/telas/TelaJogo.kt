// core/src/main/kotlin/com/sobrevivente/jogo/telas/TelaJogo.kt
package com.sobrevivente.jogo.telas

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.ScreenUtils
import com.sobrevivente.jogo.Constantes
import com.sobrevivente.jogo.camera.CameraTerceiraPessoa
import com.sobrevivente.jogo.entrada.ControlesToque
import com.sobrevivente.jogo.hud.Hud
import com.sobrevivente.jogo.hud.Joystick
import com.sobrevivente.jogo.mundo.Arma
import com.sobrevivente.jogo.mundo.Jogador
import com.sobrevivente.jogo.mundo.Mundo

/** Marco 1.4: tiro hitscan, munição, recarga, alvos de teste e feedback de acerto (sobre o HUD do 1.3). */
class TelaJogo : ScreenAdapter() {

    private val mundo = Mundo()
    private val jogador = Jogador()
    private val arma = Arma()
    private val cameraJogo = CameraTerceiraPessoa()
    private val controles = ControlesToque()
    private val joystick = Joystick(controles)
    private val hud = Hud(controles)

    private val loteModelos = ModelBatch()
    private val lote = SpriteBatch()
    private val forma = ShapeRenderer()

    private var pausado = false

    init {
        hud.definirVida(Constantes.JOGADOR_VIDA_MAXIMA, Constantes.JOGADOR_VIDA_MAXIMA)
        cameraJogo.seguir(jogador.posicao)
    }

    override fun show() {
        Gdx.input.inputProcessor = controles
        Gdx.input.setCatchKey(Input.Keys.BACK, true) // Voltar vira pausa, não fecha o app
    }

    override fun hide() {
        Gdx.input.inputProcessor = null
        Gdx.input.setCatchKey(Input.Keys.BACK, false)
    }

    override fun pause() {
        controles.liberarTudo()
    }

    private fun alternarPausa() {
        pausado = !pausado
        hud.pausado = pausado
        controles.definirBloqueado(pausado)
        Gdx.app.log("TELA", if (pausado) "pausado" else "retomado")
    }

    override fun render(delta: Float) {
        val dt = minOf(delta, Constantes.DELTA_MAXIMO)
        val densidade = Gdx.graphics.density

        // Entrada de alto nível
        controles.atualizar(dt)
        if (controles.consumirVoltar()) alternarPausa()
        if (controles.consumirAlternarDebug()) hud.alternarDebug()

        // Lógica (parada durante a pausa)
        if (!pausado) {
            cameraJogo.girar(
                controles.consumirGiroX() / densidade,
                controles.consumirGiroY() / densidade
            )
            jogador.mover(dt, controles.movimentoX, controles.movimentoY, cameraJogo.yaw)
            cameraJogo.seguir(jogador.posicao)

            // Tiro: o raio sai da câmera, atravessa o centro da tela (onde fica a mira)
            val atirou = arma.atualizar(
                dt,
                controles.acaoPressionada(ControlesToque.ACAO_ATIRAR),
                controles.consumirPressao(ControlesToque.ACAO_RECARREGAR)
            )
            if (atirou) {
                val acertou = mundo.atirar(
                    cameraJogo.camera.position,
                    cameraJogo.camera.direction,
                    Constantes.PISTOLA_ALCANCE
                )
                if (acertou) hud.mostrarAcerto()
            }
            jogador.atualizarTiro(dt, cameraJogo.yaw, atirou)
            mundo.atualizar(dt)
            hud.atualizar(dt)
        }
        hud.definirMunicao(arma.carregador, arma.reserva, arma.recarregando)

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
        if (jogador.claraoVisivel) loteModelos.render(jogador.clarao, mundo.ambiente)
        for (i in mundo.alvos.indices) {
            loteModelos.render(mundo.alvos[i].instancia, mundo.ambiente)
        }
        loteModelos.end()

        // HUD: formas com transparência, depois textos
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        forma.begin(ShapeRenderer.ShapeType.Filled)
        joystick.desenhar(forma)
        hud.desenharFormas(forma)
        forma.end()

        lote.begin()
        hud.desenharTextos(lote, Gdx.graphics.framesPerSecond)
        lote.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    override fun resize(width: Int, height: Int) {
        cameraJogo.redimensionar(width, height)
        controles.redimensionar(width, height, Gdx.graphics.density)
        hud.redimensionar(width, height, Gdx.graphics.density)
        lote.projectionMatrix.setToOrtho2D(0f, 0f, width.toFloat(), height.toFloat())
        forma.projectionMatrix.setToOrtho2D(0f, 0f, width.toFloat(), height.toFloat())
        forma.updateMatrices()
        Gdx.app.log("TELA", "resize " + width + "x" + height)
    }

    override fun dispose() {
        loteModelos.dispose()
        lote.dispose()
        forma.dispose()
        hud.dispose()
        jogador.dispose()
        mundo.dispose()
    }
}
