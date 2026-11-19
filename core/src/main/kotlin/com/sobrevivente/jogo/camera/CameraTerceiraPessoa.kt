// core/src/main/kotlin/com/sobrevivente/jogo/camera/CameraTerceiraPessoa.kt
package com.sobrevivente.jogo.camera

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.sobrevivente.jogo.Constantes
import com.sobrevivente.jogo.mundo.Colisao
import kotlin.math.sqrt

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

    /** 0 = andando (camera longe), 1 = mira levantada (camera no ombro). Suavizado. */
    var mira = 0f
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

    /**
     * Camera livre no ombro: o toque direito gira (ver `girar`); aqui so se entra/sai da mira (camera mais colada, FOV menor).
     */
    fun atualizar(dt: Float, alvo: Vector3, mirando: Boolean) {
        val k = minOf(1f, Constantes.RE4_ENTRAR_MIRA * dt)
        mira += ((if (mirando) 1f else 0f) - mira) * k
        seguir(alvo)
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

        val distancia = MathUtils.lerp(Constantes.RE4_DISTANCIA, Constantes.RE4_DISTANCIA_MIRA, mira)
        val ombro = MathUtils.lerp(Constantes.RE4_OMBRO, Constantes.RE4_OMBRO_MIRA, mira)
        camera.fieldOfView = MathUtils.lerp(Constantes.CAMERA_FOV, Constantes.RE4_FOV_MIRA, mira)
        val pivoY = alvo.y + Constantes.RE4_PIVO_ALTURA
        var cameraY = pivoY - frenteY * distancia
        if (cameraY < Constantes.CAMERA_ALTURA_MINIMA) {
            cameraY = Constantes.CAMERA_ALTURA_MINIMA // não atravessa o chão
        }

        val desejadoX = alvo.x - frenteX * distancia + direitaX * ombro
        val desejadoZ = alvo.z - frenteZ * distancia + direitaZ * ombro

        // Parede entre o jogador e a câmera: aproxima a câmera (Marco 1.8), no plano XZ.
        val dx = desejadoX - alvo.x
        val dz = desejadoZ - alvo.z
        val comprimento = sqrt(dx * dx + dz * dz)
        var fator = 1f
        if (comprimento > 0.0001f) {
            val livre = Colisao.distanciaLivreXZ(
                alvo.x, alvo.z, dx / comprimento, dz / comprimento,
                comprimento, Constantes.CAMERA_MARGEM_PAREDE
            )
            if (livre < comprimento) fator = maxOf(Constantes.CAMERA_FATOR_MINIMO, livre / comprimento)
        }
        cameraY = maxOf(Constantes.CAMERA_ALTURA_MINIMA, pivoY + (cameraY - pivoY) * fator)

        val cx = alvo.x + dx * fator; val cz = alvo.z + dz * fator
        cameraY = maxOf(cameraY, com.sobrevivente.jogo.mundo.Terreno.alturaChao(cx, cz) + 0.5f)
        camera.position.set(cx, cameraY, cz)
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
