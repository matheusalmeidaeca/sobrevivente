// core/src/main/kotlin/com/sobrevivente/jogo/camera/CameraTerceiraPessoa.kt
package com.sobrevivente.jogo.camera

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.sobrevivente.jogo.Constantes

/**
 * Câmera atrás do ombro. Convenções (seção 8.1 do mapa):
 * yaw 0 olha para -Z; yaw positivo = anti-horário visto de cima;
 * pitch positivo = olhando para cima.
 */
class CameraTerceiraPessoa {

    val camera = PerspectiveCamera(
        Constantes.CAMERA_FOV,
        Gdx.graphics.width.toFloat(),
        Gdx.graphics.height.toFloat()
    )

    var yaw = 0f
        private set
    var pitch = Constantes.CAMERA_PITCH_INICIAL
        private set

    init {
        camera.near = Constantes.CAMERA_NEAR
        camera.far = Constantes.CAMERA_FAR
    }

    /** Recebe o arrasto em dp. Arrastar para a direita vira a câmera para a direita. */
    fun girar(deltaXDp: Float, deltaYDp: Float) {
        yaw = (yaw - deltaXDp * Constantes.CAMERA_SENSIBILIDADE) % 360f
        pitch = MathUtils.clamp(
            pitch + deltaYDp * Constantes.CAMERA_SENSIBILIDADE,
            Constantes.CAMERA_PITCH_MIN,
            Constantes.CAMERA_PITCH_MAX
        )
    }

    /** `alvo` = posição dos pés do jogador. Sem alocação. */
    fun seguir(alvo: Vector3) {
        val yawRad = yaw * MathUtils.degreesToRadians
        val pitchRad = pitch * MathUtils.degreesToRadians
        val senoYaw = MathUtils.sin(yawRad)
        val cossenoYaw = MathUtils.cos(yawRad)
        val senoPitch = MathUtils.sin(pitchRad)
        val cossenoPitch = MathUtils.cos(pitchRad)

        // Direção para onde a câmera olha
        val frenteX = -senoYaw * cossenoPitch
        val frenteY = senoPitch
        val frenteZ = -cossenoYaw * cossenoPitch
        // Direita da câmera (no plano horizontal)
        val direitaX = cossenoYaw
        val direitaZ = -senoYaw

        val pivoY = alvo.y + Constantes.CAMERA_PIVO_ALTURA
        var cameraY = pivoY - frenteY * Constantes.CAMERA_DISTANCIA
        if (cameraY < Constantes.CAMERA_ALTURA_MINIMA) {
            cameraY = Constantes.CAMERA_ALTURA_MINIMA // não atravessa o chão
        }

        camera.position.set(
            alvo.x - frenteX * Constantes.CAMERA_DISTANCIA + direitaX * Constantes.CAMERA_OMBRO,
            cameraY,
            alvo.z - frenteZ * Constantes.CAMERA_DISTANCIA + direitaZ * Constantes.CAMERA_OMBRO
        )
        camera.direction.set(frenteX, frenteY, frenteZ)
        camera.up.set(0f, 1f, 0f)
        camera.update()
    }

    fun redimensionar(largura: Int, altura: Int) {
        camera.viewportWidth = largura.toFloat()
        camera.viewportHeight = altura.toFloat()
        camera.update()
    }
}
