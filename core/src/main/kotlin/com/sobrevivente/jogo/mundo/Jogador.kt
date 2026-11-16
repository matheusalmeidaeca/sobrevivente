// core/src/main/kotlin/com/sobrevivente/jogo/mundo/Jogador.kt
package com.sobrevivente.jogo.mundo

import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
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
    private var intensidadeSuave = 0f   // filtrada: evita tremer entre andar/correr
    private var andando = false           // histerese parado <-> andar
    private var correndo = false          // histerese andar <-> correr
    private var fatorAnim = 1f            // velocidade de reproducao suavizada
    private val animador: Animador
    private var velY = 0f
    private var noChao = true
    var agachado = false
        private set
    private var fatorMov = 1f
    private var recargaTravada = false
    private var recargaAndando = false

    // Armas (F4): cada uma e um modelo estatico preso ao osso mao_dir do esqueleto.
    private val modelosArma = arrayOfNulls<Model>(TipoArma.values().size)
    private val instanciasArma = arrayOfNulls<ModelInstance>(TipoArma.values().size)
    private var armaAtual = TipoArma.PISTOLA
    private var recarregandoArma = false
    private var noMao: Node? = null
    private val deslocamentoMao = Matrix4().setToTranslation(0f, Constantes.ARMA_PALMA_Y, 0f).rotate(Vector3.X, -90f)

    /** Arma que aparece na mao (null = sem modelo; o jogo continua sem a arma desenhada). */
    val armaVisivel: ModelInstance?
        get() = if (animado) instanciasArma[armaAtual.ordinal] else null

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
            noMao = corpo.getNode("mao_dir", true)
            for (tipo in TipoArma.values()) {
                val m = CarregadorModelos.carregar(tipo.modelo)
                modelosArma[tipo.ordinal] = m
                if (m != null) instanciasArma[tipo.ordinal] = ModelInstance(m)
            }
            animador.tocar(nomeAnim("parado"), -1, 1f, 0f)
            animador.atualizar(0f)
        }
        atualizarTransformacao()
        atualizarArma()
    }

    /** Troca a arma na mao (chamar quando Arma.tipo mudar). */
    fun definirArma(tipo: TipoArma) {
        armaAtual = tipo
    }

    /** True enquanto a arma esta recarregando (toca recarregar_<arma>). */
    fun definirRecarga(recarregando: Boolean) {
        recarregandoArma = recarregando
    }

    /** Nome da animacao desta arma (cai para a sem arma se o modelo nao tiver a versao armada). */
    private fun nomeAnim(base: String): String {
        val nome = base + "_" + armaAtual.sufixo
        return if (corpo.getAnimation(nome) != null) nome else base
    }

    /** Prende a arma a mao e o clarao ao cano. Chamar depois de animar e de posicionar o corpo. Sem alocacao. */
    private fun atualizarArma() {
        val instancia = armaVisivel ?: return
        val mao = noMao ?: return
        instancia.transform.set(corpo.transform).mul(mao.globalTransform).mul(deslocamentoMao)
        clarao.transform.set(instancia.transform).translate(armaAtual.bocaX, armaAtual.bocaY, armaAtual.bocaZ)
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
        val temEntrada = entradaX != 0f || entradaY != 0f
        val mirando = tempoEncarar > 0f || recarregandoArma

        // Estilo CoD Mobile: joystick no maximo = corrida; agachado e mirando/recarregando andam mais devagar. Sem deslizar.
        fatorMov = when {
            agachado -> Constantes.JOGADOR_FATOR_AGACHADO
            mirando -> Constantes.JOGADOR_FATOR_ATIRANDO
            intensidadeMovimento >= Constantes.JOGADOR_SPRINT_LIMIAR -> Constantes.JOGADOR_SPRINT_FATOR
            else -> 1f
        }

        if (temEntrada) {
            val radianos = yawCamera * MathUtils.degreesToRadians
            val seno = MathUtils.sin(radianos)
            val cosseno = MathUtils.cos(radianos)
            // frente da câmera = (-seno, -cosseno); direita da câmera = (cosseno, -seno)
            val direcaoX = -seno * entradaY + cosseno * entradaX
            val direcaoZ = -cosseno * entradaY - seno * entradaX
            val v = Constantes.JOGADOR_VELOCIDADE * fatorMov

            posicao.x = MathUtils.clamp(posicao.x + direcaoX * v * dt, -Constantes.MAPA_LIMITE, Constantes.MAPA_LIMITE)
            posicao.z = MathUtils.clamp(posicao.z + direcaoZ * v * dt, -Constantes.MAPA_LIMITE, Constantes.MAPA_LIMITE)
            Colisao.resolver(posicao, Constantes.JOGADOR_RAIO) // desliza nas paredes (Marco 1.8)

            // O corpo vira suavemente para a direção do movimento
            // (a não ser que esteja encarando a mira logo após atirar).
            if (tempoEncarar <= 0f) {
                val yawAlvo = MathUtils.atan2(-direcaoX, -direcaoZ) * MathUtils.radiansToDegrees
                yaw = MathUtils.lerpAngleDeg(yaw, yawAlvo, minOf(1f, Constantes.JOGADOR_VELOCIDADE_GIRO * dt))
            }
        }

        // Vertical: gravidade, pulo, rampas e andar alto (Terreno).
        val chaoY = Terreno.alturaSuporte(posicao.x, posicao.z, posicao.y)
        if (noChao && velY <= 0f && chaoY >= posicao.y - 0.01f) {
            posicao.y = chaoY // sobe degrau/rampa
        } else {
            velY -= Constantes.GRAVIDADE * dt
            posicao.y += velY * dt
            if (posicao.y <= chaoY && velY <= 0f) {
                posicao.y = chaoY
                velY = 0f
                noChao = true
            } else {
                noChao = false
            }
        }
        if (noChao && chaoY < posicao.y - 0.01f) noChao = false // saiu da borda: cai
        if (temEntrada || !noChao) Colisao.resolver(posicao, Constantes.JOGADOR_RAIO)

        atualizarTransformacao()
    }

    /** PULAR: so no chao e em pe. */
    fun pular() {
        if (noChao && !agachado && vida > 0) {
            velY = Constantes.JOGADOR_PULO_VELOCIDADE
            noChao = false
        }
    }

    /** AGACHAR: liga/desliga (so no chao). */
    fun alternarAgachar() {
        if (noChao) agachado = !agachado
    }

    /**
     * Chamar todo frame com o jogo rodando. Ao atirar: encara a direção da mira por
     * PISTOLA_TEMPO_ENCARAR_S (seção 11) e mostra o clarão por CLARAO_DURACAO_S. Sem alocação.
     */
    fun atualizarTiro(dt: Float, yawCamera: Float, atirou: Boolean) {
        if (!atirou && tempoEncarar <= 0f && !claraoVisivel) return

        if (atirou) {
            tempoEncarar = armaAtual.tempoEncarar
            tempoClarao = Constantes.CLARAO_DURACAO_S
            claraoVisivel = true
            if (animado && !andando && !agachado && noChao) animador.tocar(nomeAnim("atirar"), 1, 1f, Constantes.ANIM_TRANSICAO_TIRO_S)
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
        val marcha = Constantes.ANIM_TRANSICAO_MARCHA_S
        intensidadeSuave += (intensidadeMovimento - intensidadeSuave) * minOf(1f, Constantes.ANIM_FILTRO_ENTRADA * dt)
        if (!andando && intensidadeMovimento > Constantes.ANIM_LIMIAR_INICIAR) andando = true
        if (andando && intensidadeSuave < Constantes.ANIM_LIMIAR_PARAR && intensidadeMovimento < 0.01f) andando = false
        if (!correndo && intensidadeSuave >= Constantes.JOGADOR_LIMIAR_CORRER + 0.1f) correndo = true
        if (correndo && intensidadeSuave < Constantes.JOGADOR_LIMIAR_CORRER - 0.1f) correndo = false
        if (!andando) correndo = false
        if (!recarregandoArma) recargaTravada = false

        val mirando = tempoEncarar > 0f
        // velocidade da animacao de marcha acompanha a velocidade real (inclui sprint/agachado/mirando)
        val ref = if (correndo) Constantes.JOGADOR_ANIM_REF_CORRER else Constantes.JOGADOR_ANIM_REF_ANDAR
        val alvo = MathUtils.clamp(intensidadeSuave * Constantes.JOGADOR_VELOCIDADE * fatorMov / ref, 0.6f, 1.6f)
        fatorAnim += (alvo - fatorAnim) * minOf(1f, Constantes.ANIM_FILTRO_VELOCIDADE * dt)

        if (vida <= 0) {
            animador.tocarSe("morrer", 1, 1f, t)
        } else if (!noChao) {
            animador.tocarSe(nomeAnimOu("pular", "parado"), 1, 1f, t)
        } else if (recarregandoArma && corpo.getAnimation(nomeAnim("recarregar")) != null) {
            if (!recargaTravada) { // a versao andando ou parada e escolhida no inicio e vale ate o fim da recarga
                recargaAndando = andando && !agachado
                recargaTravada = true
            }
            val nome = if (recargaAndando) nomeAnimOu("andarrecarregar", "recarregar") else nomeAnim("recarregar")
            animador.tocarSe(nome, 1, Constantes.ARMA_RECARGA_ANIM_S / armaAtual.recargaS, t)
        } else if (mirando && andando) {
            // atirando andando: pernas andam, bracos miram (antes ele deslizava parado)
            animador.tocarSe(nomeAnimOu(if (agachado) "andaragachar" else "andarmirar", "andar"), -1, fatorAnim, marcha)
            animador.definirVelocidade(fatorAnim)
        } else if (mirando && !agachado) {
            animador.tocarSe(nomeAnim("atirar"), 1, 1f, Constantes.ANIM_TRANSICAO_TIRO_S)
        } else if (agachado) {
            if (andando) {
                animador.tocarSe(nomeAnimOu("andaragachar", "andar"), -1, fatorAnim, marcha)
                animador.definirVelocidade(fatorAnim)
            } else {
                animador.tocarSe(nomeAnimOu("agachar", "parado"), -1, 1f, t)
            }
        } else if (!andando) {
            animador.tocarSe(nomeAnim("parado"), -1, 1f, t)
        } else if (correndo) {
            animador.tocarSe(nomeAnim("correr"), -1, fatorAnim, marcha)
            animador.definirVelocidade(fatorAnim)
        } else {
            animador.tocarSe(nomeAnim("andar"), -1, fatorAnim, marcha)
            animador.definirVelocidade(fatorAnim)
        }
        animador.atualizar(dt)
        atualizarArma()
    }

    /** Como nomeAnim, mas se a versao armada de `base` nao existir usa a de `alternativa`. */
    private fun nomeAnimOu(base: String, alternativa: String): String {
        val nome = base + "_" + armaAtual.sufixo
        return if (corpo.getAnimation(nome) != null) nome else nomeAnim(alternativa)
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
        if (armaVisivel == null) {
            clarao.transform
                .setToTranslation(posicao.x, posicao.y + alturaClarao, posicao.z)
                .rotate(Vector3.Y, yaw)
                .translate(0f, 0f, -frenteClarao)
        } else {
            atualizarArma()
        }
    }

    override fun dispose() {
        modeloCorpo.dispose()
        modeloNariz.dispose()
        modeloClarao.dispose()
        for (m in modelosArma) m?.dispose()
    }
}
