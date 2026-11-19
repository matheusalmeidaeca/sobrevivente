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
    private val modeloAnimado: Model? = CarregadorModelos.carregar(
        if (Constantes.USAR_ESQUELETO_HUMANO) Constantes.MODELO_JOGADOR_HUMANO else Constantes.MODELO_JOGADOR
    )
    val animado: Boolean = modeloAnimado != null
    /** Modelo do protagonista para o parceiro de IA reutilizar (null = capsula). */
    val modeloAnimadoCompartilhado: Model?
        get() = modeloAnimado

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
    private var pousou = false
    private var noChao = true
    private var estavaNoAr = false
    private var tempoPouso = 0f
    private var yawAnterior = 0f
    // F10: dor/desequilibrio (golpe do zumbi) e recuo leve (ao atirar). Os ossos do tronco/cabeca/ombros sao girados
    // por cima da animacao: o personagem continua andando com dor.
    private val reacao = ReacaoImpacto()
    private val recuo = ReacaoImpacto()
    private var empurraoX = 0f
    private var empurraoZ = 0f
    private var tempoDor = 0f
    private val nomesReacao =
        if (Constantes.USAR_ESQUELETO_HUMANO) arrayOf("coluna2", "pescoco", "cabeca", "clavicula_esq", "clavicula_dir")
        else arrayOf("coluna", "pescoco", "cabeca", "ombro_esq", "ombro_dir")
    private val nosReacao = arrayOfNulls<Node>(5)
    private val baseReacao = Array(5) { Matrix4() }
    private var reacaoAplicada = false
    private var inclinacao = 0f
    var agachado = false
        private set
    private var fatorMov = 1f
    // Deslize (F11): correndo + AGACHAR = desliza ~0,75 s na direcao da corrida, rapido e baixo (estilo shooter mobile)
    private var tempoDeslize = 0f
    private var ultDirX = 0f
    private var ultDirZ = -1f
    val deslizando: Boolean
        get() = tempoDeslize > 0f
    private var recargaTravada = false
    private var recargaAndando = false

    // Armas (F4): cada uma e um modelo estatico preso ao osso mao_dir do esqueleto.
    private val modelosArma = arrayOfNulls<Model>(TipoArma.values().size)
    private val instanciasArma = arrayOfNulls<ModelInstance>(TipoArma.values().size)
    private var armaAtual = TipoArma.PISTOLA
    private var recarregandoArma = false
    private var noMao: Node? = null
    private val deslocamentoMao = Matrix4().setToTranslation(0f, Constantes.ARMA_PALMA_Y, 0f).rotate(Vector3.X, -90f)

    // Faca (4o slot): lamina + cabo, caixas simples presas a mao. Golpe = arco rapido da lamina + avanco do corpo.
    private var facaAtiva = false
    private var tempoGolpe = 0f
    private lateinit var modeloFacaLamina: Model
    private lateinit var modeloFacaCabo: Model
    private lateinit var instanciaFacaLamina: ModelInstance
    private lateinit var instanciaFacaCabo: ModelInstance

    /** Arma que aparece na mao (null = sem modelo; o jogo continua sem a arma desenhada). Com a faca: a lamina. */
    val armaVisivel: ModelInstance?
        get() = if (!animado) null else if (facaAtiva) instanciaFacaLamina else instanciasArma[armaAtual.ordinal]

    /** Cabo da faca (null se a faca nao esta na mao). Desenhar junto com armaVisivel. */
    val facaCabo: ModelInstance?
        get() = if (animado && facaAtiva) instanciaFacaCabo else null

    /** 0..1: quanto o jogador esta sentindo dor agora (HUD: vinheta vermelha). */
    val dor: Float
        get() = minOf(1f, reacao.intensidade)

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

        modeloFacaLamina = construtor.createBox(0.022f, 0.012f, 0.24f,
            Material(ColorAttribute.createDiffuse(0.72f, 0.74f, 0.78f, 1f)), atributos)
        modeloFacaCabo = construtor.createBox(0.030f, 0.030f, 0.11f,
            Material(ColorAttribute.createDiffuse(0.10f, 0.08f, 0.07f, 1f)), atributos)
        instanciaFacaLamina = ModelInstance(modeloFacaLamina)
        instanciaFacaCabo = ModelInstance(modeloFacaCabo)

        corpo = ModelInstance(modeloCorpo)
        nariz = ModelInstance(modeloNariz)
        clarao = ModelInstance(modeloClarao)
        animador = Animador(corpo)
        if (animado) {
            noMao = corpo.getNode("mao_dir", true)
            for (i in nomesReacao.indices) nosReacao[i] = corpo.getNode(nomesReacao[i], true)
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

    /** Equipa/desequipa a faca (4o slot). Com a faca nao ha arma de fogo na mao nem clarao. */
    fun definirFaca(ativa: Boolean) {
        facaAtiva = ativa
    }

    /** Golpe de faca: arco da lamina e um avanco do corpo na direcao (dx, dz) da camera. */
    fun golpearFaca(dx: Float, dz: Float) {
        if (vida <= 0) return
        tempoGolpe = Constantes.FACA_GOLPE_S
        recuo.disparar(dx, dz, 0.55f, 0.35f)
    }

    /** True enquanto a arma esta recarregando (toca recarregar_<arma>). */
    fun definirRecarga(recarregando: Boolean) {
        recarregandoArma = recarregando
    }

    /** Nome da animacao desta arma (cai para a sem arma se o modelo nao tiver a versao armada). */
    private fun nomeAnim(base: String): String {
        if (facaAtiva) return base
        val nome = base + "_" + armaAtual.sufixo
        return if (corpo.getAnimation(nome) != null) nome else base
    }

    /** Prende a arma a mao e o clarao ao cano. Chamar depois de animar e de posicionar o corpo. Sem alocacao. */
    private fun atualizarArma() {
        val instancia = armaVisivel ?: return
        val mao = noMao ?: return
        if (facaAtiva) {
            val t = if (tempoGolpe > 0f) 1f - tempoGolpe / Constantes.FACA_GOLPE_S else 0f
            val arco = MathUtils.sin(MathUtils.PI * t) * 80f
            instanciaFacaCabo.transform.set(corpo.transform).mul(mao.globalTransform).mul(deslocamentoMao)
                .rotate(Vector3.X, arco).translate(0f, 0f, 0.02f)
            instancia.transform.set(instanciaFacaCabo.transform).translate(0f, 0f, -0.175f)
            return
        }
        instancia.transform.set(corpo.transform).mul(mao.globalTransform).mul(deslocamentoMao)
        clarao.transform.set(instancia.transform).translate(armaAtual.bocaX, armaAtual.bocaY, armaAtual.bocaZ)
    }

    /**
     * Golpe de zumbi: (dx, dz) = direcao do golpe (de quem bateu para o jogador, plano XZ). O corpo se inclina/contorce
     * NESSA direcao, leva um empurrao e anda um pouco mais devagar por IMPACTO_TEMPO_S, mas continua controlavel.
     */
    fun receberImpacto(dx: Float, dz: Float, forca: Float) {
        if (vida <= 0) return
        reacao.disparar(dx, dz, forca)
        val l = sqrt(dx * dx + dz * dz)
        if (l > 1e-4f) {
            empurraoX = MathUtils.clamp(empurraoX + dx / l * Constantes.IMPACTO_EMPURRAO_MS * forca, -4f, 4f)
            empurraoZ = MathUtils.clamp(empurraoZ + dz / l * Constantes.IMPACTO_EMPURRAO_MS * forca, -4f, 4f)
        }
    }

    /** Recuo leve ao atirar (sem empurrao nem lentidao): desequilibra um pouco para tras. */
    fun receberRecuo(dx: Float, dz: Float, forca: Float) {
        if (vida <= 0) return
        recuo.disparar(dx, dz, forca, 0.5f)
    }

    /** Aplica dor/recuo nos ossos por cima da animacao. Chamar logo depois de `animador.atualizar`. */
    private fun aplicarReacao(restaurou: Boolean) {
        reacao.calcular(yaw)
        recuo.calcular(yaw)
        if (!reacao.ativa && !recuo.ativa) {
            if (restaurou) corpo.calculateTransforms()
            return
        }
        for (i in nosReacao.indices) {
            val n = nosReacao[i] ?: return
            baseReacao[i].set(n.localTransform)
        }
        val p = reacao.pitch + recuo.pitch
        val r = reacao.roll + recuo.roll
        val g = reacao.giro + recuo.giro
        val dor = minOf(1.2f, reacao.intensidade)
        nosReacao[0]!!.localTransform.rotate(Vector3.X, -p * 0.55f).rotate(Vector3.Z, r * 0.55f).rotate(Vector3.Y, g * 0.5f)
        nosReacao[1]!!.localTransform.rotate(Vector3.X, -p * 0.30f + dor * 9f).rotate(Vector3.Z, r * 0.3f)
        nosReacao[2]!!.localTransform.rotate(Vector3.Z, MathUtils.sin(tempoDor * 32f) * dor * 5f).rotate(Vector3.X, dor * 6f)
        nosReacao[3]!!.localTransform.rotate(Vector3.X, -dor * 14f).rotate(Vector3.Z, dor * 8f)
        nosReacao[4]!!.localTransform.rotate(Vector3.X, -dor * 14f).rotate(Vector3.Z, -dor * 8f)
        corpo.calculateTransforms()
        reacaoAplicada = true
    }

    fun receberDano(dano: Int) {
        vida = maxOf(0, vida - dano)
    }

    /** Recupera vida (item de cura, Marco 1.7), sem passar do máximo. */
    fun curar(valor: Int) {
        vida = minOf(Constantes.JOGADOR_VIDA_MAXIMA, vida + valor)
    }

    private var giroRapidoRestante = 0f
    private var giroRapidoTravado = false

    /** Mantem a pose de mira (bracos levantados) enquanto a mira estiver ligada. */
    fun manterMira() {
        if (tempoEncarar < 0.25f) tempoEncarar = 0.25f
    }

    /**
     * F18: movimento estilo Resident Evil 4 (Leon), "tank". `entradaX/Y` vem do analogico (-1..1; Y positivo = frente).
     *  - Sem mira: Y para frente anda/corre na direcao em que o corpo olha; X gira o corpo no lugar (ou em curva andando);
     *    Y todo para tras faz a meia-volta rapida (180 graus) e so.
     *  - Mirando: o personagem fica parado; X gira devagar o corpo (a mira acompanha).
     * Sem alocacao.
     */
    fun mover(dt: Float, entradaX: Float, entradaY: Float, mirando: Boolean, camYaw: Float) {
        val recarregando = recarregandoArma
        if (reacao.ativa) tempoDor += dt

        // --- camera livre (toque direito): o analogico anda RELATIVO a camera; o corpo vira para onde anda ---
        val mag = minOf(1f, kotlin.math.sqrt(entradaX * entradaX + entradaY * entradaY))
        val cr = camYaw * MathUtils.degreesToRadians
        val camFx = -MathUtils.sin(cr)
        val camFz = -MathUtils.cos(cr)
        val camDx = MathUtils.cos(cr)
        val camDz = -MathUtils.sin(cr)
        var dirX = camFx * entradaY + camDx * entradaX
        var dirZ = camFz * entradaY + camDz * entradaX
        val dl = kotlin.math.sqrt(dirX * dirX + dirZ * dirZ)
        if (dl > 0.0001f) { dirX /= dl; dirZ /= dl }
        if (tempoDeslize <= 0f) {
            if (mirando) {
                yaw = MathUtils.lerpAngleDeg(yaw, camYaw, minOf(1f, 18f * dt))
            } else if (mag > 0.12f) {
                val alvoYaw = MathUtils.atan2(-dirX, -dirZ) * MathUtils.radiansToDegrees
                yaw = MathUtils.lerpAngleDeg(yaw, alvoYaw, minOf(1f, 14f * dt))
            }
            yaw = ((yaw % 360f) + 360f) % 360f
        }

        // --- avanco ---
        val avanco = if (mag > 0.12f) mag else 0f
        intensidadeMovimento = avanco
        val deslocando = avanco > 0.05f
        val frenteX = dirX
        val frenteZ = dirZ

        fatorMov = when {
            agachado -> Constantes.JOGADOR_FATOR_AGACHADO
            recarregando -> Constantes.JOGADOR_FATOR_ATIRANDO
            mirando -> 0.55f
            avanco >= Constantes.JOGADOR_SPRINT_LIMIAR -> Constantes.JOGADOR_SPRINT_FATOR
            else -> 0.8f // andar (o Leon anda mais devagar do que corre)
        }
        if (reacao.ativa) fatorMov *= Constantes.IMPACTO_FATOR_VEL

        // empurrao do golpe (decai rapido); o jogador continua andando por cima dele
        val empurrando = empurraoX != 0f || empurraoZ != 0f
        if (empurrando) {
            posicao.x = MathUtils.clamp(posicao.x + empurraoX * dt, -Constantes.MAPA_LIMITE, Constantes.MAPA_LIMITE)
            posicao.z = MathUtils.clamp(posicao.z + empurraoZ * dt, -Constantes.MAPA_LIMITE, Constantes.MAPA_LIMITE)
            val d = maxOf(0f, 1f - 7f * dt)
            empurraoX *= d
            empurraoZ *= d
            if (kotlin.math.abs(empurraoX) < 0.02f && kotlin.math.abs(empurraoZ) < 0.02f) {
                empurraoX = 0f
                empurraoZ = 0f
            }
        }

        if (tempoDeslize > 0f) {
            tempoDeslize -= dt
            val k = MathUtils.clamp(tempoDeslize / Constantes.DESLIZE_S, 0f, 1f)
            val v = Constantes.JOGADOR_VELOCIDADE * Constantes.JOGADOR_SPRINT_FATOR * (0.55f + Constantes.DESLIZE_VEL * k)
            posicao.x = MathUtils.clamp(posicao.x + ultDirX * v * dt, -Constantes.MAPA_LIMITE, Constantes.MAPA_LIMITE)
            posicao.z = MathUtils.clamp(posicao.z + ultDirZ * v * dt, -Constantes.MAPA_LIMITE, Constantes.MAPA_LIMITE)
            Colisao.resolver(posicao, Constantes.JOGADOR_RAIO)
            if (tempoDeslize <= 0f) agachado = false
        } else if (deslocando) {
            ultDirX = frenteX
            ultDirZ = frenteZ
            val v = Constantes.JOGADOR_VELOCIDADE * fatorMov * avanco.coerceAtLeast(0.6f)
            posicao.x = MathUtils.clamp(posicao.x + frenteX * v * dt, -Constantes.MAPA_LIMITE, Constantes.MAPA_LIMITE)
            posicao.z = MathUtils.clamp(posicao.z + frenteZ * v * dt, -Constantes.MAPA_LIMITE, Constantes.MAPA_LIMITE)
            Colisao.resolver(posicao, Constantes.JOGADOR_RAIO) // desliza nas paredes (Marco 1.8)
        }

        // Vertical: gravidade, pulo, rampas e andar alto (Terreno).
        val chaoY = Terreno.alturaSuporte(posicao.x, posicao.z, posicao.y)
        if (noChao && velY <= 0f && chaoY >= posicao.y - 0.45f) {
            posicao.y = chaoY // sobe degrau/rampa
        } else {
            velY -= Constantes.GRAVIDADE * dt
            posicao.y += velY * dt
            if (posicao.y <= chaoY && velY <= 0f) {
                if (velY < -3f) pousou = true
                posicao.y = chaoY
                velY = 0f
                noChao = true
            } else {
                noChao = false
            }
        }
        if (noChao && chaoY < posicao.y - 0.46f) noChao = false // saiu da borda: cai
        if (deslocando || !noChao || empurrando || tempoDeslize > 0f) Colisao.resolver(posicao, Constantes.JOGADOR_RAIO)

        atualizarTransformacao()
    }

    /** Andando/correndo no chao (para o som dos passos). */
    val andandoNoChao: Boolean
        get() = noChao && !agachado && intensidadeMovimento > 0.05f && tempoDeslize <= 0f

    /** Correndo de verdade no chao (sprint): serve para a poeira dos pes. */
    val sprintando: Boolean
        get() = noChao && !agachado && fatorMov > 1.2f && intensidadeMovimento > 0.5f

    /** Acabou de pousar de um pulo? (true uma vez, para a poeira). */
    fun consumirPouso(): Boolean {
        val v = pousou
        pousou = false
        return v
    }

    /** Ha, logo a frente de quem anda, um obstaculo baixo (<= 1,1 m) que da para pular? */
    val podePular: Boolean
        get() = noChao && !agachado && vida > 0 && tempoDeslize <= 0f && intensidadeMovimento > 0.3f &&
            Colisao.obstaculoPulavel(posicao.x, posicao.z, ultDirX, ultDirZ, posicao.y)

    /** PULAR: so no chao e em pe. */
    fun pular() {
        if (tempoDeslize > 0f) { // pular saindo do deslize: levanta e salta, mantendo o impulso
            tempoDeslize = 0f
            agachado = false
        }
        if (noChao && !agachado && vida > 0) {
            velY = Constantes.JOGADOR_PULO_VELOCIDADE
            noChao = false
        }
    }

    /** AGACHAR: liga/desliga (so no chao). */
    fun alternarAgachar() {
        if (tempoDeslize > 0f) return
        if (sprintando && vida > 0) { // correndo: em vez de agachar parado, desliza
            tempoDeslize = Constantes.DESLIZE_S
            agachado = true
            return
        }
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
            if (!facaAtiva) {
                tempoClarao = Constantes.CLARAO_DURACAO_S
                claraoVisivel = true
            }
            if (animado && !andando && !agachado && noChao && corpo.getAnimation(nomeAnim("atirar")) != null) {
                animador.tocar(nomeAnim("atirar"), 1, 1f, Constantes.ANIM_TRANSICAO_TIRO_S)
            }
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
        if (estavaNoAr && noChao) tempoPouso = Constantes.PULO_DURACAO_POUSO_S
        estavaNoAr = !noChao
        if (tempoPouso > 0f) tempoPouso -= dt
        // inclinacao do corpo nas curvas (suavizada)
        val taxaGiro = MathUtils.clamp((((yaw - yawAnterior + 540f) % 360f + 360f) % 360f - 180f) / maxOf(dt, 0.001f), -540f, 540f)
        yawAnterior = yaw
        val alvoIncl = if (noChao && andando) MathUtils.clamp(taxaGiro * Constantes.INCLINACAO_CURVA_K, -Constantes.INCLINACAO_CURVA_MAX, Constantes.INCLINACAO_CURVA_MAX) else 0f
        inclinacao += (alvoIncl - inclinacao) * minOf(1f, 8f * dt)
        // velocidade da animacao de marcha acompanha a velocidade real (inclui sprint/agachado/mirando)
        val ref = if (correndo) Constantes.JOGADOR_ANIM_REF_CORRER else Constantes.JOGADOR_ANIM_REF_ANDAR
        val alvo = MathUtils.clamp(intensidadeSuave * Constantes.JOGADOR_VELOCIDADE * fatorMov / ref, 0.6f, 1.6f)
        fatorAnim += (alvo - fatorAnim) * minOf(1f, Constantes.ANIM_FILTRO_VELOCIDADE * dt)

        if (vida <= 0) {
            animador.tocarSe("morrer", 1, 1f, t)
        } else if (tempoDeslize > 0f && noChao && corpo.getAnimation("deslizar") != null) {
            animador.tocarSe("deslizar", 1, 0.667f / Constantes.DESLIZE_S, 0.08f) // clipe de 20 quadros (0,667 s) esticado ao deslize
        } else if (!noChao) {
            // pulo em fases pela fisica: subida -> apice/queda (clipes seguram a ultima pose)
            if (velY > Constantes.PULO_LIMIAR_SUBIDA) animador.tocarSe(nomeAnimOu("pular", "parado"), 1, 1f, Constantes.PULO_TRANSICAO_SUBIDA_S)
            else animador.tocarSe(nomeAnimOu("cair", "pular"), 1, 1f, Constantes.PULO_TRANSICAO_QUEDA_S)
        } else if (tempoPouso > 0f && !andando && !agachado && !mirando && !recarregandoArma &&
            corpo.getAnimation(nomeAnim("pousar")) != null) {
            animador.tocarSe(nomeAnim("pousar"), 1, 1f, Constantes.PULO_TRANSICAO_POUSO_S)
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
        // desfaz a reacao do quadro anterior (a animacao reescreve os ossos; se ela terminou, volta a pose base)
        var restaurou = false
        if (reacaoAplicada) {
            for (i in nosReacao.indices) nosReacao[i]?.localTransform?.set(baseReacao[i])
            reacaoAplicada = false
            restaurou = true
        }
        if (tempoGolpe > 0f) tempoGolpe -= dt
        reacao.atualizar(dt)
        recuo.atualizar(dt)
        animador.atualizar(dt)
        if (animado) aplicarReacao(restaurou)
        atualizarArma()
    }

    /** Como nomeAnim, mas se a versao armada de `base` nao existir usa a de `alternativa`. */
    private fun nomeAnimOu(base: String, alternativa: String): String {
        if (facaAtiva) return nomeAnim(alternativa)
        val nome = base + "_" + armaAtual.sufixo
        return if (corpo.getAnimation(nome) != null) nome else nomeAnim(alternativa)
    }

    /** Reposiciona os modelos conforme `posicao` e `yaw`. Sem alocação. */
    fun atualizarTransformacao() {
        if (animado) {
            // Modelo do F2: origem nos pes, frente = -Z.
            corpo.transform.setToTranslation(posicao.x, posicao.y, posicao.z).rotate(Vector3.Y, yaw)
                .rotate(Vector3.X, -(reacao.pitch + recuo.pitch) * 0.4f).rotate(Vector3.Z, inclinacao + (reacao.roll + recuo.roll) * 0.4f)
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
        modeloFacaLamina.dispose()
        modeloFacaCabo.dispose()
    }
}
