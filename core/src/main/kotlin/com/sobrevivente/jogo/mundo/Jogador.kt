// core/src/main/kotlin/com/sobrevivente/jogo/mundo/Jogador.kt
package com.sobrevivente.jogo.mundo

import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import com.sobrevivente.jogo.Constantes
import kotlin.math.sqrt

/**
 * Jogador placeholder: cápsula + "nariz" amarelo que mostra a frente.
 * Convenção (seção 8.1 do mapa): Y para cima; yaw 0 olha para -Z;
 * yaw positivo = anti-horário visto de cima. `posicao` é a posição dos PÉS.
 */
class Jogador : Disposable {

    val posicao = Vector3(0f, 0f, 0f)
    var yaw = 0f

    /** Vida atual (0 a JOGADOR_VIDA_MAXIMA). A morte e o reinício são do Marco 1.6. */
    var vida = Constantes.JOGADOR_VIDA_MAXIMA
        private set

    /** Modelo animado do F2 (null = usa a capsula placeholder). */
    private val modeloAnimado: Model? = CarregadorModelos.carregar(Constantes.MODELO_JOGADOR)
    val animado: Boolean = modeloAnimado != null

    private val modeloCorpo: Model
    private val modeloNariz: Model
    private val modeloClarao: Model
    val corpo: ModelInstance
    val nariz: ModelInstance
    val clarao: ModelInstance

    /** True durante os 0,05 s do clarão no cano (Marco 1.4). */
    var claraoVisivel = false
        private set

    private var tempoClarao = 0f
    private var tempoEncarar = 0f
    private var intensidadeMovimento = 0f
    private val animador: Animador

    init {
        val construtor = ModelBuilder()
        val atributos = (Usage.Position or Usage.Normal).toLong()

        modeloCorpo = modeloAnimado ?: construtor.createCapsule(
            Constantes.JOGADOR_RAIO,
            Constantes.JOGADOR_ALTURA,
            Constantes.JOGADOR_DIVISOES,
            Material(
                ColorAttribute.createDiffuse(
                    Constantes.JOGADOR_COR_R,
                    Constantes.JOGADOR_COR_G,
                    Constantes.JOGADOR_COR_B,
                    1f
                )
            ),
            atributos
        )
        modeloNariz = construtor.createBox(
            Constantes.NARIZ_LARGURA,
            Constantes.NARIZ_ALTURA,
            Constantes.NARIZ_COMPRIMENTO,
            Material(
                ColorAttribute.createDiffuse(
                    Constantes.NARIZ_COR_R,
                    Constantes.NARIZ_COR_G,
                    Constantes.NARIZ_COR_B,
                    1f
                )
            ),
            atributos
        )

        modeloClarao = construtor.createBox(
            Constantes.CLARAO_LADO,
            Constantes.CLARAO_LADO,
            Constantes.CLARAO_LADO,
            Material(
                ColorAttribute.createDiffuse(
                    Constantes.CLARAO_COR_R,
                    Constantes.CLARAO_COR_G,
                    Constantes.CLARAO_COR_B,
                    1f
                )
            ),
            atributos
        )

        corpo = ModelInstance(modeloCorpo)
        nariz = ModelInstance(modeloNariz)
        clarao = ModelInstance(modeloClarao)
        animador = Animador(corpo)
        if (animado) {
            animador.tocar("parado", -1, 1f, 0f)
            animador.atualizar(0f)
        }
        atualizarTransformacao()
    }

    fun receberDano(dano: Int) {
        vida = maxOf(0, vida - dano)
    }

    /** Recupera vida (item de cura, Marco 1.7), sem passar do máximo. */
    fun curar(valor: Int) {
        vida = minOf(Constantes.JOGADOR_VIDA_MAXIMA, vida + valor)
    }

    /**
     * Move o jogador. `entradaX/Y` vêm do joystick (-1..1; Y positivo = frente).
     * O movimento é relativo ao yaw da câmera. Sem alocação.
     */
    fun mover(dt: Float, entradaX: Float, entradaY: Float, yawCamera: Float) {
        intensidadeMovimento = minOf(1f, sqrt(entradaX * entradaX + entradaY * entradaY))
        if (entradaX == 0f && entradaY == 0f) return

        val radianos = yawCamera * MathUtils.degreesToRadians
        val seno = MathUtils.sin(radianos)
        val cosseno = MathUtils.cos(radianos)
        // frente da câmera = (-seno, -cosseno); direita da câmera = (cosseno, -seno)
        val direcaoX = -seno * entradaY + cosseno * entradaX
        val direcaoZ = -cosseno * entradaY - seno * entradaX

        posicao.x = MathUtils.clamp(
            posicao.x + direcaoX * Constantes.JOGADOR_VELOCIDADE * dt,
            -Constantes.MAPA_LIMITE,
            Constantes.MAPA_LIMITE
        )
        posicao.z = MathUtils.clamp(
            posicao.z + direcaoZ * Constantes.JOGADOR_VELOCIDADE * dt,
            -Constantes.MAPA_LIMITE,
            Constantes.MAPA_LIMITE
        )

        Colisao.resolver(posicao, Constantes.JOGADOR_RAIO) // desliza nas paredes (Marco 1.8)

        // O corpo vira suavemente para a direção do movimento
        // (a não ser que esteja encarando a mira logo após atirar).
        if (tempoEncarar <= 0f) {
            val yawAlvo = MathUtils.atan2(-direcaoX, -direcaoZ) * MathUtils.radiansToDegrees
            yaw = MathUtils.lerpAngleDeg(
                yaw,
                yawAlvo,
                minOf(1f, Constantes.JOGADOR_VELOCIDADE_GIRO * dt)
            )
        }

        atualizarTransformacao()
    }

    /**
     * Chamar todo frame com o jogo rodando. Ao atirar: encara a direção da mira por
     * PISTOLA_TEMPO_ENCARAR_S (seção 11) e mostra o clarão por CLARAO_DURACAO_S. Sem alocação.
     */
    fun atualizarTiro(dt: Float, yawCamera: Float, atirou: Boolean) {
        if (!atirou && tempoEncarar <= 0f && !claraoVisivel) return

        if (atirou) {
            tempoEncarar = Constantes.PISTOLA_TEMPO_ENCARAR_S
            tempoClarao = Constantes.CLARAO_DURACAO_S
            claraoVisivel = true
            if (animado) animador.tocar("atirar", 1, 1f, Constantes.ANIM_TRANSICAO_TIRO_S)
        }
        if (tempoEncarar > 0f) {
            tempoEncarar -= dt
            yaw = yawCamera
        }
        if (tempoClarao > 0f) {
            tempoClarao -= dt
            if (tempoClarao <= 0f) claraoVisivel = false
        }
        atualizarTransformacao()
    }

    /**
     * Escolhe e avanca a animacao (Marco F3). Chamar todo frame com o jogo rodando e a maleta fechada.
     * Prioridade: morrer > atirar (so reinicia no tiro) > correr/andar (pelo joystick) > parado.
     */
    fun atualizarAnimacao(dt: Float) {
        if (!animado) return
        val t = Constantes.ANIM_TRANSICAO_S
        if (vida <= 0) {
            animador.tocarSe("morrer", 1, 1f, t)
        } else if (tempoEncarar > 0f) {
            animador.tocarSe("atirar", 1, 1f, Constantes.ANIM_TRANSICAO_TIRO_S)
        } else if (intensidadeMovimento >= Constantes.JOGADOR_LIMIAR_CORRER) {
            animador.tocarSe("correr", -1, 1f, t)
        } else if (intensidadeMovimento > 0f) {
            animador.tocarSe("andar", -1, 1f, t)
        } else {
            animador.tocarSe("parado", -1, 1f, t)
        }
        animador.atualizar(dt)
    }

    /** Reposiciona os modelos conforme `posicao` e `yaw`. Sem alocação. */
    fun atualizarTransformacao() {
        if (animado) {
            // Modelo do F2: origem nos pes, frente = -Z.
            corpo.transform.setToTranslation(posicao.x, posicao.y, posicao.z).rotate(Vector3.Y, yaw)
        } else {
            corpo.transform.setToTranslation(
                posicao.x,
                posicao.y + Constantes.JOGADOR_ALTURA / 2f,
                posicao.z
            )
        }
        nariz.transform
            .setToTranslation(posicao.x, posicao.y + Constantes.NARIZ_ALTURA_DO_CHAO, posicao.z)
            .rotate(Vector3.Y, yaw)
            .translate(0f, 0f, -Constantes.NARIZ_DESLOCAMENTO_FRENTE)
        val alturaClarao = if (animado) Constantes.CLARAO_MODELO_ALTURA else Constantes.CLARAO_ALTURA_DO_CHAO
        val frenteClarao = if (animado) Constantes.CLARAO_MODELO_FRENTE else Constantes.CLARAO_DESLOCAMENTO_FRENTE
        clarao.transform
            .setToTranslation(posicao.x, posicao.y + alturaClarao, posicao.z)
            .rotate(Vector3.Y, yaw)
            .translate(0f, 0f, -frenteClarao)
    }

    override fun dispose() {
        modeloCorpo.dispose()
        modeloNariz.dispose()
        modeloClarao.dispose()
    }
}
