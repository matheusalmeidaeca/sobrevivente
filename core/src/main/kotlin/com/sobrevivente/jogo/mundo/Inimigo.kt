// core/src/main/kotlin/com/sobrevivente/jogo/mundo/Inimigo.kt
package com.sobrevivente.jogo.mundo

import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.sobrevivente.jogo.Constantes
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Inimigo básico (seção 13 do mapa). Lógica no plano XZ.
 * Estados: PARADO (espera) <-> VAGANDO (anda sem rumo perto da origem) -> PERSEGUINDO (A* ao redor das paredes); zumbis que nascem longe ficam CACANDO (vem andando devagar ate o jogador)
 * -> ATACANDO (so com visao livre) -> MORTO.
 * A cor muda conforme o estado (ajuda a testar no celular) e pisca ao levar tiro.
 */
class Inimigo(modelo: Model, x: Float, z: Float, private val animado: Boolean = false) {

    enum class Estado { PARADO, VAGANDO, CACANDO, PERSEGUINDO, ATACANDO, MORTO }

    val instancia = ModelInstance(modelo)
    val caixa = BoundingBox()
    val posicao = Vector3(x, 0f, z) // posição dos PÉS
    private var inicioX = x
    private var inicioZ = z

    var vida = Constantes.ENIMIGO_VIDA
        private set
    var estado = Estado.PARADO
        private set

    val vivo: Boolean
        get() = estado != Estado.MORTO && !desativado

    /** Fora do jogo (pool): nao desenha, nao pensa, nao leva tiro. */
    var desativado = false
        private set
    private var cacador = false

    /** Pode ser reutilizado no respawn: desativado, ou morto e o corpo ja sumiu. */
    val disponivelParaNascer: Boolean
        get() = desativado || (estado == Estado.MORTO && tempoMorto >= Constantes.CORPO_DURACAO_S)

    private val cor: ColorAttribute?
    private val animador = Animador(instancia)
    private var yaw = 0f
    private var tempoMorto = 0f

    /** Desenhar? Vivo, ou (modelo animado) ate CORPO_DURACAO_S depois de morrer. */
    val visivel: Boolean
        get() = !desativado && (vivo || (animado && tempoMorto < Constantes.CORPO_DURACAO_S))
    private val pontoMinimo = Vector3()
    private val pontoMaximo = Vector3()
    private var tempoAtaque = 0f
    private var tempoFlash = 0f

    // Trajeto atual (copia propria: a Navegacao reaproveita os mesmos arrays para todos)
    private val trajetoX = FloatArray(MAX_TRAJETO)
    private val trajetoZ = FloatArray(MAX_TRAJETO)
    private var trajetoN = 0
    private var trajetoI = 0
    private var tempoReplano = 0f
    private var tempoEspera = MathUtils.random(Constantes.ENIMIGO_ESPERA_MIN_S, Constantes.ENIMIGO_ESPERA_MAX_S)
    private var rumoYaw = 0f // para onde o corpo quer olhar andando

    init {
        // Cada ModelInstance tem cópia própria do material: a cor não afeta os outros inimigos.
        cor = instancia.materials.get(0).get(ColorAttribute.Diffuse) as? ColorAttribute
        aplicarCor()
        atualizarTransformacao()
        if (animado) {
            animador.tocar("parado", -1, 1f, 0f)
            animador.atualizar(0f)
        }
    }

    /** Chamar uma vez por frame (jogo rodando). Sem alocação. */
    fun atualizar(dt: Float, jogador: Jogador) {
        if (desativado) return
        if (estado == Estado.MORTO) {
            tempoMorto += dt
            if (tempoFlash > 0f) {
                tempoFlash -= dt
                if (tempoFlash <= 0f) aplicarCor()
            }
            if (animado) animador.atualizar(dt) // termina a animacao de morrer e segura a ultima pose
            return
        }
        if (animado) animador.atualizar(dt)

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
                if (distancia <= Constantes.ENIMIGO_VISAO) {
                    iniciarPerseguicao()
                } else {
                    tempoEspera -= dt
                    if (tempoEspera <= 0f) iniciarVagar()
                }
            }
            Estado.VAGANDO -> {
                if (distancia <= Constantes.ENIMIGO_VISAO) {
                    iniciarPerseguicao()
                } else if (!seguirTrajeto(dt, Constantes.ENIMIGO_VELOCIDADE_VAGAR)) {
                    mudarEstado(Estado.PARADO)
                    tempoEspera = MathUtils.random(Constantes.ENIMIGO_ESPERA_MIN_S, Constantes.ENIMIGO_ESPERA_MAX_S)
                }
            }
            Estado.CACANDO -> {
                if (distancia <= Constantes.ENIMIGO_VISAO) {
                    iniciarPerseguicao()
                } else {
                    tempoReplano -= dt
                    if (tempoReplano <= 0f) {
                        tempoReplano = Constantes.ENIMIGO_CACA_REPLANO_S + MathUtils.random(0f, 0.3f)
                        planejar(jogador.posicao.x, jogador.posicao.z)
                    }
                    if (!seguirTrajeto(dt, Constantes.ENIMIGO_VELOCIDADE_CACA)) tempoReplano = 0f
                }
            }
            Estado.PERSEGUINDO -> {
                if (distancia > Constantes.ENIMIGO_PERDE_JOGADOR) {
                    if (cacador) {
                        mudarEstado(Estado.CACANDO)
                        tempoReplano = 0f
                    } else {
                        mudarEstado(Estado.PARADO)
                        tempoEspera = MathUtils.random(Constantes.ENIMIGO_ESPERA_MIN_S, Constantes.ENIMIGO_ESPERA_MAX_S)
                    }
                } else if (distancia <= Constantes.ENIMIGO_ALCANCE_ATAQUE &&
                    abs(jogador.posicao.y - posicao.y) < Constantes.ENIMIGO_DY_ATAQUE &&
                    !Navegacao.paredeEntre(posicao.x, posicao.z, jogador.posicao.x, jogador.posicao.z)
                ) {
                    mudarEstado(Estado.ATACANDO)
                    tempoAtaque = Constantes.ENIMIGO_ATAQUE_IMPACTO_S // o golpe acerta quando o braco chega
                } else {
                    tempoReplano -= dt
                    if (tempoReplano <= 0f) {
                        tempoReplano = Constantes.ENIMIGO_REPLANEJAR_S + MathUtils.random(0f, 0.1f)
                        planejar(jogador.posicao.x, jogador.posicao.z)
                    }
                    if (!seguirTrajeto(dt, Constantes.ENIMIGO_VELOCIDADE)) {
                        // sem caminho (ou chegou ao fim): encara o jogador e tenta de novo no proximo replano
                        rumoYaw = MathUtils.atan2(-dx, -dz) * MathUtils.radiansToDegrees
                        tempoReplano = 0f
                    }
                }
            }
            Estado.ATACANDO -> {
                rumoYaw = MathUtils.atan2(-dx, -dz) * MathUtils.radiansToDegrees
                if (distancia > Constantes.ENIMIGO_ALCANCE_ATAQUE * 1.15f ||
                    abs(jogador.posicao.y - posicao.y) >= Constantes.ENIMIGO_DY_ATAQUE ||
                    Navegacao.paredeEntre(posicao.x, posicao.z, jogador.posicao.x, jogador.posicao.z)
                ) {
                    iniciarPerseguicao()
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

        if (animado) {
            yaw = MathUtils.lerpAngleDeg(yaw, rumoYaw, minOf(1f, Constantes.ENIMIGO_VELOCIDADE_GIRO * dt))
            atualizarTransformacao()
        }
    }

    private fun iniciarPerseguicao() {
        if (estado != Estado.PERSEGUINDO) mudarEstado(Estado.PERSEGUINDO)
        tempoReplano = 0f
        trajetoN = 0
        trajetoI = 0
    }

    /** Escolhe um ponto livre perto da origem e calcula o caminho ate la. Sem ponto/caminho: fica esperando mais um pouco. */
    private fun iniciarVagar() {
        for (tentativa in 0 until 6) {
            val ang = MathUtils.random(0f, MathUtils.PI2)
            val r = MathUtils.random(2f, Constantes.ENIMIGO_VAGAR_RAIO)
            val alvoX = inicioX + MathUtils.cos(ang) * r
            val alvoZ = inicioZ + MathUtils.sin(ang) * r
            if (!Navegacao.livre(alvoX, alvoZ)) continue
            planejar(alvoX, alvoZ)
            if (trajetoN > 0) {
                mudarEstado(Estado.VAGANDO)
                return
            }
        }
        tempoEspera = MathUtils.random(Constantes.ENIMIGO_ESPERA_MIN_S, Constantes.ENIMIGO_ESPERA_MAX_S)
    }

    private fun planejar(alvoX: Float, alvoZ: Float) {
        val n = Navegacao.buscar(posicao.x, posicao.z, alvoX, alvoZ)
        trajetoN = minOf(n, MAX_TRAJETO)
        trajetoI = 0
        for (i in 0 until trajetoN) {
            trajetoX[i] = Navegacao.caminhoX[i]
            trajetoZ[i] = Navegacao.caminhoZ[i]
        }
    }

    /** Anda ate o proximo ponto do trajeto na velocidade dada. Devolve false quando nao ha mais para onde ir. */
    private fun seguirTrajeto(dt: Float, velocidade: Float): Boolean {
        while (trajetoI < trajetoN) {
            val px = trajetoX[trajetoI] - posicao.x
            val pz = trajetoZ[trajetoI] - posicao.z
            val d = sqrt(px * px + pz * pz)
            val ultimo = trajetoI == trajetoN - 1
            if (d <= Constantes.ENIMIGO_PONTO_CHEGADA && !(ultimo && estado == Estado.PERSEGUINDO)) {
                trajetoI++
                continue
            }
            if (d < 0.001f) {
                trajetoI++
                continue
            }
            val passo = minOf(velocidade * dt, d)
            val antesX = posicao.x
            val antesZ = posicao.z
            posicao.x = MathUtils.clamp(posicao.x + px / d * passo, -Constantes.MAPA_LIMITE, Constantes.MAPA_LIMITE)
            posicao.z = MathUtils.clamp(posicao.z + pz / d * passo, -Constantes.MAPA_LIMITE, Constantes.MAPA_LIMITE)
            Colisao.resolver(posicao, Constantes.ENIMIGO_RAIO)
            Colisao.resolverBloqueiosInimigo(posicao, Constantes.ENIMIGO_RAIO)
            val mx = posicao.x - antesX
            val mz = posicao.z - antesZ
            if (mx * mx + mz * mz > 1e-8f) rumoYaw = MathUtils.atan2(-mx, -mz) * MathUtils.radiansToDegrees
            return true
        }
        return false
    }

    /** Tira do jogo (pool): some e fica esperando o respawn. */
    fun desativar() {
        desativado = true
        cacador = false
        estado = Estado.MORTO
        tempoMorto = Constantes.CORPO_DURACAO_S
        tempoFlash = 0f
    }

    /** Respawn: aparece em (x, z) com vida cheia e vem cacando o jogador. */
    fun nascer(x: Float, z: Float) {
        desativado = false
        cacador = true
        posicao.set(x, 0f, z)
        vida = Constantes.ENIMIGO_VIDA
        tempoAtaque = 0f
        tempoFlash = 0f
        tempoMorto = 0f
        trajetoN = 0
        trajetoI = 0
        tempoReplano = 0f
        yaw = 0f
        rumoYaw = 0f
        mudarEstado(Estado.CACANDO)
        atualizarTransformacao()
    }

    /** Muda o ponto inicial (zumbi do pool que volta a ser inicial). */
    fun redefinirPosicao(x: Float, z: Float) {
        inicioX = x
        inicioZ = z
    }

    /** Volta à posição inicial, com vida cheia e parado. Usado ao reiniciar depois da morte do jogador. */
    fun reiniciar() {
        desativado = false
        cacador = false
        posicao.set(inicioX, 0f, inicioZ)
        vida = Constantes.ENIMIGO_VIDA
        estado = Estado.PARADO
        tempoAtaque = 0f
        tempoFlash = 0f
        tempoMorto = 0f
        yaw = 0f
        rumoYaw = 0f
        trajetoN = 0
        trajetoI = 0
        tempoEspera = MathUtils.random(Constantes.ENIMIGO_ESPERA_MIN_S, Constantes.ENIMIGO_ESPERA_MAX_S)
        aplicarCor()
        atualizarTransformacao()
        if (animado) {
            animador.tocar("parado", -1, 1f, 0f)
            animador.atualizar(0f)
        }
    }

    /** Devolve true se este tiro matou o inimigo. */
    fun receberDano(dano: Int): Boolean {
        if (estado == Estado.MORTO || desativado) return false
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

    private fun mudarEstado(novo: Estado) {
        estado = novo
        aplicarCor()
        if (!animado) return
        val t = Constantes.ANIM_TRANSICAO_S
        when (novo) {
            Estado.PARADO -> animador.tocar("parado", -1, 1f, t)
            Estado.CACANDO -> animador.tocar(
                "andar", -1, Constantes.ENIMIGO_VELOCIDADE_CACA / Constantes.ENIMIGO_VAGAR_REF, Constantes.ANIM_TRANSICAO_MARCHA_S
            )
            Estado.VAGANDO -> animador.tocar(
                "andar", -1, Constantes.ENIMIGO_VELOCIDADE_VAGAR / Constantes.ENIMIGO_VAGAR_REF, Constantes.ANIM_TRANSICAO_MARCHA_S
            )
            Estado.PERSEGUINDO -> animador.tocar("correr", -1, Constantes.ZUMBI_ANIM_VELOCIDADE, Constantes.ANIM_TRANSICAO_MARCHA_S)
            Estado.ATACANDO -> animador.tocar("atacar", -1, Constantes.ENIMIGO_ATAQUE_ANIM_VEL, t)
            Estado.MORTO -> animador.tocar("morrer", 1, 1f, t)
        }
    }

    /** Cor com textura: branco = textura pura; flash e tinta de estado multiplicam a textura. */
    private fun aplicarTinta(c: ColorAttribute) {
        if (tempoFlash > 0f) {
            c.color.set(
                Constantes.ZUMBI_TINTA_FLASH,
                Constantes.ZUMBI_TINTA_FLASH,
                Constantes.ZUMBI_TINTA_FLASH,
                1f
            )
            return
        }
        when (estado) {
            Estado.PARADO, Estado.VAGANDO, Estado.CACANDO -> c.color.set(1f, 1f, 1f, 1f)
            Estado.PERSEGUINDO -> c.color.set(
                Constantes.ZUMBI_TINTA_PERSEGUINDO_R,
                Constantes.ZUMBI_TINTA_PERSEGUINDO_G,
                Constantes.ZUMBI_TINTA_PERSEGUINDO_B,
                1f
            )
            Estado.ATACANDO -> c.color.set(
                Constantes.ZUMBI_TINTA_ATACANDO_R,
                Constantes.ZUMBI_TINTA_ATACANDO_G,
                Constantes.ZUMBI_TINTA_ATACANDO_B,
                1f
            )
            Estado.MORTO -> c.color.set(1f, 1f, 1f, 1f)
        }
    }

    private fun aplicarCor() {
        val atributo = cor ?: return
        if (animado) {
            aplicarTinta(atributo)
            return
        }
        if (tempoFlash > 0f) {
            atributo.color.set(
                Constantes.ENIMIGO_FLASH_COR,
                Constantes.ENIMIGO_FLASH_COR,
                Constantes.ENIMIGO_FLASH_COR,
                1f
            )
            return
        }
        when (estado) {
            Estado.PARADO, Estado.VAGANDO, Estado.CACANDO -> atributo.color.set(
                Constantes.ENIMIGO_COR_PARADO_R,
                Constantes.ENIMIGO_COR_PARADO_G,
                Constantes.ENIMIGO_COR_PARADO_B,
                1f
            )
            Estado.PERSEGUINDO -> atributo.color.set(
                Constantes.ENIMIGO_COR_PERSEGUINDO_R,
                Constantes.ENIMIGO_COR_PERSEGUINDO_G,
                Constantes.ENIMIGO_COR_PERSEGUINDO_B,
                1f
            )
            Estado.ATACANDO -> atributo.color.set(
                Constantes.ENIMIGO_COR_ATACANDO_R,
                Constantes.ENIMIGO_COR_ATACANDO_G,
                Constantes.ENIMIGO_COR_ATACANDO_B,
                1f
            )
            Estado.MORTO -> Unit
        }
    }

    private companion object {
        const val MAX_TRAJETO = 96
    }

    private fun atualizarTransformacao() {
        if (animado) {
            // Modelo do F2: origem nos pes, frente = -Z.
            instancia.transform.setToTranslation(posicao.x, posicao.y, posicao.z).rotate(Vector3.Y, yaw)
        } else {
            instancia.transform.setToTranslation(
                posicao.x,
                posicao.y + Constantes.ENIMIGO_ALTURA / 2f,
                posicao.z
            )
        }
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
