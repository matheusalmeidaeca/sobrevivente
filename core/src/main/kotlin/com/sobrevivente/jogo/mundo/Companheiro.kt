// core/src/main/kotlin/com/sobrevivente/jogo/mundo/Companheiro.kt
package com.sobrevivente.jogo.mundo

import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.sobrevivente.jogo.Constantes
import kotlin.math.sqrt

/**
 * Parceiro de IA (co-op): o sobrevivente resgatado. Segue o jogador (atras e ao lado), contorna paredes pela colisao,
 * atira no zumbi mais perto que ele enxerga e, se ficar para tras, reaparece perto do jogador.
 * Limites assumidos: os zumbis so perseguem o jogador (o parceiro nao leva dano nem morre) e ele nao tem arma desenhada.
 */
class Companheiro(modelo: Model, private val animado: Boolean, x: Float, z: Float) {

    val instancia = ModelInstance(modelo)
    val posicao = Vector3(x, 0f, z)
    private val animador = Animador(instancia)
    private var yaw = 0f
    private var tiroEspera = 1.2f
    private var longe = 0f
    private var andando = false

    init {
        if (animado) {
            // jaqueta azul-esverdeada para diferenciar do protagonista
            for (i in 0 until instancia.materials.size) {
                val c = instancia.materials.get(i).get(ColorAttribute.Diffuse) as? ColorAttribute ?: continue
                c.color.set(0.55f, 0.85f, 0.95f, 1f)
            }
            animador.tocar("parado", -1, 1f, 0f)
        }
        atualizarTransformacao()
    }

    fun atualizar(dt: Float, jogador: Jogador, mundo: Mundo, aoTiro: (() -> Unit)?) {
        seguir(dt, jogador)
        atirar(dt, mundo, aoTiro)
        if (animado) {
            animador.tocarSe(if (andando) "andar" else "parado", -1, 1f, 0.2f)
            animador.atualizar(dt)
        }
        atualizarTransformacao()
    }

    private fun seguir(dt: Float, jogador: Jogador) {
        // frente do jogador (modelo olha para -Z com yaw 0): atras = +frente invertida; ao lado = perpendicular
        val ry = jogador.yaw * MathUtils.degreesToRadians
        val fx = -MathUtils.sin(ry)
        val fz = -MathUtils.cos(ry)
        val alvoX = jogador.posicao.x - fx * 2.4f + fz * 1.3f
        val alvoZ = jogador.posicao.z - fz * 2.4f - fx * 1.3f
        val dx = alvoX - posicao.x
        val dz = alvoZ - posicao.z
        val d = sqrt(dx * dx + dz * dz)
        val pjx = jogador.posicao.x - posicao.x
        val pjz = jogador.posicao.z - posicao.z
        val dJogador = sqrt(pjx * pjx + pjz * pjz)
        longe = if (dJogador > 14f) longe + dt else 0f
        if (dJogador > 45f || longe > 4f) {
            // ficou preso ou muito para tras: reaparece junto do jogador
            posicao.set(jogador.posicao.x - fx * 1.6f, jogador.posicao.y, jogador.posicao.z - fz * 1.6f)
            longe = 0f
            andando = false
            return
        }
        andando = if (andando) d > 0.7f else d > 1.5f
        if (!andando) return
        val vel = minOf(6.2f, 2.2f + d * 1.1f)
        val passo = minOf(vel * dt, d)
        posicao.x += dx / d * passo
        posicao.z += dz / d * passo
        posicao.y = jogador.posicao.y
        Colisao.resolver(posicao, Constantes.ENIMIGO_RAIO)
        virarPara(dx, dz, dt, 14f)
    }

    private fun virarPara(dx: Float, dz: Float, dt: Float, velocidade: Float) {
        val alvo = MathUtils.atan2(-dx, -dz) * MathUtils.radiansToDegrees
        var delta = (alvo - yaw) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        yaw += delta * minOf(1f, velocidade * dt)
    }

    private fun atirar(dt: Float, mundo: Mundo, aoTiro: (() -> Unit)?) {
        tiroEspera -= dt
        var alvo: Inimigo? = null
        var melhor = ALCANCE * ALCANCE
        for (i in mundo.inimigos.indices) {
            val z = mundo.inimigos[i]
            if (!z.vivo) continue
            val dx = z.posicao.x - posicao.x
            val dz = z.posicao.z - posicao.z
            val d2 = dx * dx + dz * dz
            if (d2 >= melhor) continue
            if (Navegacao.paredeEntre(posicao.x, posicao.z, z.posicao.x, z.posicao.z)) continue
            melhor = d2
            alvo = z
        }
        val a = alvo ?: return
        val dx = a.posicao.x - posicao.x
        val dz = a.posicao.z - posicao.z
        virarPara(dx, dz, dt, 10f)
        if (tiroEspera > 0f) return
        tiroEspera = INTERVALO_S + MathUtils.random(0f, 0.25f)
        val d = sqrt(dx * dx + dz * dz).coerceAtLeast(0.01f)
        mundo.danoAliado(a, DANO, dx / d, dz / d)
        aoTiro?.invoke()
    }

    private fun atualizarTransformacao() {
        if (animado) {
            instancia.transform.setToTranslation(posicao.x, posicao.y, posicao.z).rotate(Vector3.Y, yaw)
        } else {
            instancia.transform.setToTranslation(posicao.x, posicao.y + 0.9f, posicao.z).rotate(Vector3.Y, yaw)
        }
    }

    companion object {
        const val ALCANCE = 15f
        const val DANO = 22
        const val INTERVALO_S = 0.85f
    }
}
