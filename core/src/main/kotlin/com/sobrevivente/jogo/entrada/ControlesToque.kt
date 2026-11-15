// core/src/main/kotlin/com/sobrevivente/jogo/entrada/ControlesToque.kt
package com.sobrevivente.jogo.entrada

import com.badlogic.gdx.InputAdapter
import com.sobrevivente.jogo.Constantes
import kotlin.math.sqrt

/**
 * Multitouch: cada dedo é "dono" de uma função pelo ID do ponteiro.
 * Metade esquerda (parte de baixo) = joystick; metade direita = girar câmera.
 * Todas as coordenadas daqui têm origem EMBAIXO-ESQUERDA (Y para cima),
 * já convertidas do Y do toque, que vem de cima (armadilha 5 do mapa).
 */
class ControlesToque : InputAdapter() {

    // Saída do joystick (-1..1). movimentoY positivo = empurrar para cima na tela.
    var movimentoX = 0f
        private set
    var movimentoY = 0f
        private set

    // Geometria do joystick em pixels (usada pelo desenho)
    var joystickAtivo = false
        private set
    var centroX = 0f
        private set
    var centroY = 0f
        private set
    var botaoX = 0f
        private set
    var botaoY = 0f
        private set
    var raioBase = 1f
        private set
    var raioBotao = 1f
        private set

    private var largura = 1f
    private var altura = 1f
    private var ponteiroJoystick = SEM_PONTEIRO
    private var ponteiroCamera = SEM_PONTEIRO
    private var cameraUltimoX = 0f
    private var cameraUltimoY = 0f
    private var giroX = 0f
    private var giroY = 0f

    fun redimensionar(larguraPx: Int, alturaPx: Int, densidade: Float) {
        largura = larguraPx.toFloat()
        altura = alturaPx.toFloat()
        raioBase = Constantes.JOYSTICK_RAIO_BASE_DP * densidade
        raioBotao = Constantes.JOYSTICK_RAIO_BOTAO_DP * densidade
        val margem = (Constantes.MARGEM_SEGURA_DP + Constantes.JOYSTICK_MARGEM_DP) * densidade
        centroX = margem + raioBase
        centroY = margem + raioBase
        liberarTudo()
    }

    /** Giro acumulado da câmera (pixels) desde a última leitura. */
    fun consumirGiroX(): Float {
        val valor = giroX
        giroX = 0f
        return valor
    }

    fun consumirGiroY(): Float {
        val valor = giroY
        giroY = 0f
        return valor
    }

    fun liberarTudo() {
        liberarJoystick()
        ponteiroCamera = SEM_PONTEIRO
        giroX = 0f
        giroY = 0f
    }

    private fun liberarJoystick() {
        ponteiroJoystick = SEM_PONTEIRO
        joystickAtivo = false
        movimentoX = 0f
        movimentoY = 0f
        botaoX = centroX
        botaoY = centroY
    }

    private fun atualizarJoystick(x: Float, y: Float) {
        val dx = x - centroX
        val dy = y - centroY
        val distancia = sqrt(dx * dx + dy * dy)
        if (distancia < 0.0001f) {
            movimentoX = 0f
            movimentoY = 0f
            botaoX = centroX
            botaoY = centroY
            return
        }

        // O botão central fica preso na borda da base.
        val fator = if (distancia > raioBase) raioBase / distancia else 1f
        botaoX = centroX + dx * fator
        botaoY = centroY + dy * fator

        val magnitude = minOf(distancia, raioBase) / raioBase
        if (magnitude < Constantes.JOYSTICK_ZONA_MORTA) {
            movimentoX = 0f
            movimentoY = 0f
        } else {
            // Reescala para 0..1 depois da zona morta.
            val escala = (magnitude - Constantes.JOYSTICK_ZONA_MORTA) /
                (1f - Constantes.JOYSTICK_ZONA_MORTA)
            movimentoX = dx / distancia * escala
            movimentoY = dy / distancia * escala
        }
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val x = screenX.toFloat()
        val y = altura - screenY

        if (x < largura * 0.5f) {
            if (ponteiroJoystick == SEM_PONTEIRO && y <= altura * Constantes.ZONA_JOYSTICK_ALTURA) {
                ponteiroJoystick = pointer
                joystickAtivo = true
                atualizarJoystick(x, y)
                return true
            }
        } else if (ponteiroCamera == SEM_PONTEIRO) {
            ponteiroCamera = pointer
            cameraUltimoX = x
            cameraUltimoY = y
            return true
        }
        return false
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        val x = screenX.toFloat()
        val y = altura - screenY

        if (pointer == ponteiroJoystick) {
            atualizarJoystick(x, y)
            return true
        }
        if (pointer == ponteiroCamera) {
            giroX += x - cameraUltimoX
            giroY += y - cameraUltimoY
            cameraUltimoX = x
            cameraUltimoY = y
            return true
        }
        return false
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (pointer == ponteiroJoystick) {
            liberarJoystick()
            return true
        }
        if (pointer == ponteiroCamera) {
            ponteiroCamera = SEM_PONTEIRO
            return true
        }
        return false
    }

    private companion object {
        const val SEM_PONTEIRO = -1
    }
}
