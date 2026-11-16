// core/src/main/kotlin/com/sobrevivente/jogo/mundo/Inimigo.kt
package com.sobrevivente.jogo.mundo

import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.sobrevivente.jogo.Constantes
import kotlin.math.sqrt

/**
 * Inimigo básico (seção 13 do mapa). Lógica no plano XZ.
 * Estados: PARADO -> PERSEGUINDO -> ATACANDO -> MORTO.
 * A cor muda conforme o estado (ajuda a testar no celular) e pisca ao levar tiro.
 */
class Inimigo(modelo: Model, x: Float, z: Float) {

    enum class Estado { PARADO, PERSEGUINDO, ATACANDO, MORTO }

    val instancia = ModelInstance(modelo)
    val caixa = BoundingBox()
    val posicao = Vector3(x, 0f, z) // posição dos PÉS
    private val inicioX = x
    private val inicioZ = z

    var vida = Constantes.ENIMIGO_VIDA
        private set
    var estado = Estado.PARADO
        private set

    val vivo: Boolean
        get() = estado != Estado.MORTO

    private val cor: ColorAttribute
    private val pontoMinimo = Vector3()
    private val pontoMaximo = Vector3()
    private var tempoAtaque = 0f
    private var tempoFlash = 0f

    init {
        // Cada ModelInstance tem cópia própria do material: a cor não afeta os outros inimigos.
        cor = instancia.materials.get(0).get(ColorAttribute.Diffuse) as ColorAttribute
        aplicarCor()
        atualizarTransformacao()
    }

    /** Chamar uma vez por frame (jogo rodando). Sem alocação. */
    fun atualizar(dt: Float, jogador: Jogador) {
        if (estado == Estado.MORTO) return

        if (tempoFlash > 0f) {
            tempoFlash -= dt
            if (tempoFlash <= 0f) aplicarCor()
        }

        // Jogador sem vida: os inimigos ficam parados (a morte em si é do Marco 1.6).
        if (jogador.vida <= 0) {
            if (estado != Estado.PARADO) mudarEstado(Estado.PARADO)
            return
        }

        val dx = jogador.posicao.x - posicao.x
        val dz = jogador.posicao.z - posicao.z
        val distancia = sqrt(dx * dx + dz * dz)

        when (estado) {
            Estado.PARADO -> {
                if (distancia <= Constantes.ENIMIGO_VISAO) mudarEstado(Estado.PERSEGUINDO)
            }
            Estado.PERSEGUINDO -> {
                if (distancia > Constantes.ENIMIGO_PERDE_JOGADOR) {
                    mudarEstado(Estado.PARADO)
                } else if (distancia <= Constantes.ENIMIGO_ALCANCE_ATAQUE) {
                    mudarEstado(Estado.ATACANDO)
                    tempoAtaque = 0f // o primeiro golpe sai ao chegar
                } else {
                    perseguir(dt, dx, dz, distancia)
                }
            }
            Estado.ATACANDO -> {
                if (distancia > Constantes.ENIMIGO_ALCANCE_ATAQUE) {
                    mudarEstado(Estado.PERSEGUINDO)
                } else {
                    tempoAtaque -= dt
                    if (tempoAtaque <= 0f) {
                        jogador.receberDano(Constantes.ENIMIGO_DANO)
                        tempoAtaque = Constantes.ENIMIGO_INTERVALO_ATAQUE
                    }
                }
            }
            Estado.MORTO -> Unit
        }
    }

    /** Volta à posição inicial, com vida cheia e parado. Usado ao reiniciar depois da morte do jogador. */
    fun reiniciar() {
        posicao.set(inicioX, 0f, inicioZ)
        vida = Constantes.ENIMIGO_VIDA
        estado = Estado.PARADO
        tempoAtaque = 0f
        tempoFlash = 0f
        aplicarCor()
        atualizarTransformacao()
    }

    /** Devolve true se este tiro matou o inimigo. */
    fun receberDano(dano: Int): Boolean {
        if (estado == Estado.MORTO) return false
        vida -= dano
        tempoFlash = Constantes.ENIMIGO_FLASH_S
        if (vida <= 0) {
            vida = 0
            mudarEstado(Estado.MORTO)
            return true
        }
        aplicarCor()
        return false
    }

    private fun perseguir(dt: Float, dx: Float, dz: Float, distancia: Float) {
        // Perseguição direta (sem pathfinding na Fase 1). Colisão com paredes: Marco 1.8.
        val fator = Constantes.ENIMIGO_VELOCIDADE * dt / distancia
        posicao.x = MathUtils.clamp(
            posicao.x + dx * fator,
            -Constantes.MAPA_LIMITE,
            Constantes.MAPA_LIMITE
        )
        posicao.z = MathUtils.clamp(
            posicao.z + dz * fator,
            -Constantes.MAPA_LIMITE,
            Constantes.MAPA_LIMITE
        )
        atualizarTransformacao()
    }

    private fun mudarEstado(novo: Estado) {
        estado = novo
        aplicarCor()
    }

    private fun aplicarCor() {
        if (tempoFlash > 0f) {
            cor.color.set(
                Constantes.ENIMIGO_FLASH_COR,
                Constantes.ENIMIGO_FLASH_COR,
                Constantes.ENIMIGO_FLASH_COR,
                1f
            )
            return
        }
        when (estado) {
            Estado.PARADO -> cor.color.set(
                Constantes.ENIMIGO_COR_PARADO_R,
                Constantes.ENIMIGO_COR_PARADO_G,
                Constantes.ENIMIGO_COR_PARADO_B,
                1f
            )
            Estado.PERSEGUINDO -> cor.color.set(
                Constantes.ENIMIGO_COR_PERSEGUINDO_R,
                Constantes.ENIMIGO_COR_PERSEGUINDO_G,
                Constantes.ENIMIGO_COR_PERSEGUINDO_B,
                1f
            )
            Estado.ATACANDO -> cor.color.set(
                Constantes.ENIMIGO_COR_ATACANDO_R,
                Constantes.ENIMIGO_COR_ATACANDO_G,
                Constantes.ENIMIGO_COR_ATACANDO_B,
                1f
            )
            Estado.MORTO -> Unit
        }
    }

    private fun atualizarTransformacao() {
        instancia.transform.setToTranslation(
            posicao.x,
            posicao.y + Constantes.ENIMIGO_ALTURA / 2f,
            posicao.z
        )
        // Caixa do raio de tiro: aproxima a cápsula por um prisma.
        pontoMinimo.set(
            posicao.x - Constantes.ENIMIGO_RAIO,
            posicao.y,
            posicao.z - Constantes.ENIMIGO_RAIO
        )
        pontoMaximo.set(
            posicao.x + Constantes.ENIMIGO_RAIO,
            posicao.y + Constantes.ENIMIGO_ALTURA,
            posicao.z + Constantes.ENIMIGO_RAIO
        )
        caixa.set(pontoMinimo, pontoMaximo)
    }
}
