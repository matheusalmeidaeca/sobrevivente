// core/src/main/kotlin/com/sobrevivente/jogo/mundo/Cidade.kt
package com.sobrevivente.jogo.mundo

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.sobrevivente.jogo.Constantes

/**
 * A CIDADE: uma metropole de 4 bairros ligados por UMA avenida, cada passagem fechada por um portao trancado.
 * Todas as coordenadas importantes ficam aqui, para o mapa (`construir`) e a missao (`Cidade`) concordarem.
 * Norte = -Z. O jogador comeca no sul (z +138) e avanca para o norte.
 */
object CidadeMapa {
    const val INICIO_X = 0f
    const val INICIO_Z = 138f
    val MERCADOR = floatArrayOf(12f, 130f)

    // portoes (ordem fixa = ordem das chamadas `b.portao` em `construir`): A azul, B eletrico, C alavancas, D parceiro, R cela
    const val PORTAO_A_Z = 60f
    const val PORTAO_B_Z = -5f
    const val PORTAO_C_Z = -65f
    const val PORTAO_D_Z = -100f
    val CELA = floatArrayOf(-66f, -44.8f)

    val CHAVE_AZUL = floatArrayOf(-90f, 104f)
    val FUSIVEIS = floatArrayOf(-90f, 25f, 85f, 26f, 43f, 48f)
    val PAINEL = floatArrayOf(0f, 22f)
    val NOTAS = floatArrayOf(62f, -25f, 30f, -52f)
    /** 0 azul, 1 vermelha, 2 amarela. */
    val ALAVANCAS = floatArrayOf(-41f, -18f, 41f, -52f, 0f, -58f)
    val ORDEM = intArrayOf(1, 2, 0)
    val SOBREVIVENTE = floatArrayOf(-66f, -54f)
    val RADIO = floatArrayOf(0f, -118f)
    val MINI_SPAWN = floatArrayOf(60f, 32f)
    val CHEFE_SPAWN = floatArrayOf(0f, -132f)
    val BAUS = floatArrayOf(
        -118f, 125f, 70f, 120f, 110f, 95f, -60f, 90f, // bairro 1
        -114f, 48f, 103f, 48f, -75f, 50f, // bairro 2
        -98f, -18f, 96f, -20f, 70f, -55f, // bairro 3
        -50f, -130f, 50f, -135f // bairro 4
    )

    fun definicao() = DefFase(
        nome = "A CIDADE", subtitulo = "Capitulo unico", artefato = "Fusivel",
        miniNome = "O Seguranca", chefeNome = "O Hospedeiro",
        inicio = floatArrayOf(INICIO_X, INICIO_Z), mercador = MERCADOR,
        fragmentos = FloatArray(6), alavancas = ALAVANCAS, ordem = ORDEM, altar = RADIO,
        baus = BAUS, miniSpawn = MINI_SPAWN, chefeSpawn = CHEFE_SPAWN, dicas = arrayOf(),
        spawnMax = 13, spawnDist = 42f, spawnIntervalo = 6f, fatorVida = 1.8f,
        construir = { b -> construir(b) }, cidade = true
    )

    /** Predios altos e maciços com faixas de janelas nas faces norte e sul. */
    private fun torre(b: MapaBuilder, x0: Float, z0: Float, x1: Float, z1: Float, alt: Float, tipo: Int) {
        b.caixa(x0, z0, x1, z1, alt, tipo)
        b.dec((x0 + x1) / 2f, alt + 0.15f, (z0 + z1) / 2f, x1 - x0 + 0.4f, 0.3f, z1 - z0 + 0.4f, 0.16f, 0.16f, 0.18f)
        var y = 3f
        while (y < alt - 1f) {
            b.dec((x0 + x1) / 2f, y, z0 - 0.03f, (x1 - x0) * 0.86f, 1.1f, 0.06f, 0.55f, 0.72f, 0.82f)
            b.dec((x0 + x1) / 2f, y, z1 + 0.03f, (x1 - x0) * 0.86f, 1.1f, 0.06f, 0.55f, 0.72f, 0.82f)
            y += 4.2f
        }
    }

    /** Linha de bloqueio de lado a lado da cidade, com um unico vao (o portao) em x = 0. */
    private fun bloqueio(b: MapaBuilder, z: Float, tipoPortao: Int): Int {
        b.muro(-148f, z, 148f, z, 3.6f, 8, floatArrayOf(0f), 8f)
        for (x in floatArrayOf(-36f, 36f)) b.container(x, z + 0.2f, true, 3)
        val idx = b.portao(-4f, z, 4f, z + 0.4f, 3.6f, tipoPortao)
        val (r, g, bl) = when (tipoPortao) {
            16 -> Triple(0.2f, 0.45f, 0.95f)
            19 -> Triple(0.6f, 0.6f, 0.68f)
            18 -> Triple(0.92f, 0.8f, 0.18f)
            else -> Triple(0.3f, 0.82f, 0.45f)
        }
        b.dec(-4.3f, 2.2f, z + 0.2f, 0.5f, 4.4f, 0.5f, r, g, bl, 2)
        b.dec(4.3f, 2.2f, z + 0.2f, 0.5f, 4.4f, 0.5f, r, g, bl, 2)
        return idx
    }

    fun construir(b: MapaBuilder) {
        b.cerco(-148f, -148f, 148f, 148f, 4f, 12)

        // ---- postes de luz ao longo da avenida
        var z = 140f
        while (z > -140f) {
            for (x in floatArrayOf(-9f, 9f)) {
                b.dec(x, 3f, z, 0.25f, 6f, 0.25f, 0.20f, 0.20f, 0.22f, 2)
                b.dec(x, 6.1f, z, 0.6f, 0.4f, 0.6f, 1f, 0.9f, 0.6f, 1)
            }
            z -= 22f
        }

        // =================================================== BAIRRO 1: SUBURBIO (z 60..148)
        b.predio(-30f, 118f, -14f, 134f, 1, 126f, 3f, 3f, 6)
        b.predio(-30f, 92f, -14f, 108f, 1, 100f, 3f, 3f, 14)
        b.predio(-30f, 68f, -14f, 84f, 1, 76f, 3f, 3f, 7)
        b.predio(14f, 118f, 30f, 134f, 3, 126f, 3f, 3f, 14)
        b.predio(14f, 92f, 30f, 108f, 3, 100f, 3f, 3f, 6)
        b.predio(14f, 68f, 30f, 84f, 3, 76f, 3f, 3f, 15)
        b.predio(-70f, 110f, -52f, 128f, 1, 119f, 3f, 3f, 7)
        b.predio(-70f, 78f, -52f, 96f, 1, 87f, 3f, 3f, 6)
        b.predio(-104f, 94f, -80f, 114f, 1, 104f, 4f, 3.2f, 8)    // garagem: chave azul
        b.predio(52f, 108f, 72f, 128f, 3, 118f, 3f, 3f, 14)
        b.predio(52f, 76f, 72f, 94f, 3, 85f, 3f, 3f, 7)
        b.predio(96f, 84f, 118f, 104f, 3, 94f, 3f, 3f, 15)
        for (x in floatArrayOf(-125f, -100f, 90f, 125f)) { torre(b, x - 9f, 128f, x + 9f, 144f, 11f, 12) }
        b.carro(-6f, 112f, false, 8); b.carro(6f, 90f, false, 6); b.carro(-4f, 70f, false, 7); b.carro(40f, 120f, true, 8)
        b.carro(-42f, 100f, true, 6); b.container(-41f, 80f, false, 3); b.container(41f, 104f, false, 8)
        for (v in floatArrayOf(142f, 124f, 106f, 88f, 70f)) { b.arvore(-10.5f, v, true); b.arvore(10.5f, v - 9f, true) }
        b.item(-90f, 100f, 0); b.item(-22f, 126f, 1); b.item(22f, 100f, 1); b.item(-60f, 119f, 0); b.item(62f, 118f, 1); b.item(-22f, 76f, 1)
        b.item(8f, 126f, 1); b.item(106f, 94f, 0)
        b.zumbi(-4f, 112f); b.zumbi(5f, 98f); b.zumbi(-41f, 90f); b.zumbi(41f, 84f); b.zumbi(-3f, 80f); b.zumbi(-90f, 122f)

        // ---- linha de bloqueio A (portao azul)
        val pA = bloqueio(b, CidadeMapa.PORTAO_A_Z, 16)

        // =================================================== BAIRRO 2: CENTRO COMERCIAL (z -5..60)
        b.predio(-110f, 10f, -70f, 40f, 1, 25f, 4f, 4f, 13)         // supermercado: fusivel 1
        b.predio(70f, 14f, 100f, 38f, 3, 26f, 4f, 4f, 15)           // farmacia: fusivel 2 (acorda o seguranca)
        b.predio(56f, 38f, 80f, 56f, 2, 66f, 4f, 4f, 12)            // banco: fusivel 3 (porta ao sul)
        torre(b, -148f + 2f, 0f, -118f, 56f, 26f, 13)
        torre(b, 106f, 0f, 146f, 56f, 30f, 12)
        torre(b, -68f, 44f, -54f, 58f, 18f, 12)
        torre(b, 84f, 42f, 100f, 58f, 22f, 13)
        torre(b, -26f, 36f, -10f, 56f, 20f, 12)
        torre(b, 10f, 36f, 26f, 56f, 24f, 13)
        torre(b, -26f, 0f, -12f, 14f, 16f, 15)
        torre(b, 12f, 0f, 26f, 14f, 18f, 14)
        b.container(-41f, 30f, false, 3); b.container(41f, 6f, false, 8); b.carro(-6f, 44f, false, 6); b.carro(5f, 8f, false, 7)
        b.carro(-41f, 52f, true, 8); b.carro(41f, 24f, true, 6)
        b.dec(0f, 0.25f, 22f, 6f, 0.5f, 6f, 0.5f, 0.5f, 0.52f)         // praca da caixa de forca
        b.item(-90f, 20f, 1); b.item(85f, 20f, 0); b.item(43f, 44f, 2); b.item(-41f, 14f, 1); b.item(41f, 44f, 1); b.item(0f, 40f, 0)
        b.item(-80f, 34f, 1); b.item(92f, 30f, 1)
        b.zumbi(-4f, 50f); b.zumbi(4f, 34f); b.zumbi(-41f, 42f); b.zumbi(41f, 34f); b.zumbi(-3f, 14f)
        b.zumbi(41f, 16f); b.zumbi(-41f, 6f); b.zumbi(60f, 46f); b.zumbi(-60f, 28f)

        val pB = bloqueio(b, CidadeMapa.PORTAO_B_Z, 19)

        // =================================================== BAIRRO 3: METRO E DELEGACIA (z -65..-5)
        // delegacia (-95,-62)-(-55,-28), porta ao sul em x=-75; a cela fica ao norte da divisoria z=-45
        b.predio(-95f, -62f, -55f, -28f, 2, -75f, 4f, 4f, 15)
        b.muro(-94.6f, -45f, -55.4f, -45f, 3.4f, 12, floatArrayOf(-66f), 3f)
        val pR = b.portao(-67.5f, -45f, -64.5f, -44.6f, 3.4f, 17)
        b.predio(50f, -36f, 76f, -16f, 3, -25f, 3f, 3.6f, 8)       // entrada do metro: nota 1
        torre(b, -148f + 2f, -62f, -100f, -10f, 22f, 13)
        torre(b, 100f, -62f, 146f, -8f, 26f, 12)
        torre(b, -26f, -62f, -12f, -48f, 14f, 14)
        torre(b, 12f, -62f, 26f, -48f, 16f, 15)
        torre(b, -26f, -34f, -12f, -14f, 14f, 12)
        torre(b, 12f, -34f, 26f, -14f, 12f, 13)
        torre(b, 80f, -62f, 98f, -40f, 18f, 14)
        b.container(-41f, -58f, false, 3); b.container(41f, -20f, false, 8); b.carro(-6f, -20f, false, 8); b.carro(6f, -44f, false, 6)
        b.carro(41f, -38f, true, 7); b.carro(-41f, -30f, true, 6)
        b.item(-80f, -36f, 3); b.item(-66f, -36f, 1); b.item(62f, -30f, 0); b.item(-41f, -42f, 1); b.item(41f, -58f, 1); b.item(0f, -30f, 0)
        b.zumbi(-4f, -20f); b.zumbi(4f, -36f); b.zumbi(-41f, -18f); b.zumbi(41f, -30f); b.zumbi(-41f, -50f); b.zumbi(41f, -46f)
        b.zumbi(-3f, -52f); b.zumbi(-85f, -34f); b.zumbi(63f, -26f)

        val pC = bloqueio(b, CidadeMapa.PORTAO_C_Z, 18)

        // =================================================== BAIRRO 4: A TORRE (z -148..-100)
        torre(b, -26f, -148f, 26f, -138f, 46f, 13)                    // torre central ao fundo
        torre(b, -148f + 2f, -146f, -70f, -108f, 20f, 12)
        torre(b, 70f, -146f, 146f, -108f, 24f, 13)
        torre(b, -66f, -146f, -54f, -120f, 14f, 15)
        torre(b, 54f, -146f, 66f, -120f, 16f, 14)
        b.dec(0f, 0.3f, -118f, 10f, 0.6f, 10f, 0.62f, 0.55f, 0.20f)    // heliponto
        b.container(-41f, -108f, true, 3); b.container(41f, -108f, true, 8); b.carro(-20f, -126f, true, 7); b.carro(24f, -128f, true, 8)
        b.item(-30f, -125f, 1); b.item(30f, -125f, 1); b.item(-41f, -118f, 0); b.item(41f, -118f, 0)
        b.zumbi(-41f, -125f); b.zumbi(41f, -125f); b.zumbi(-20f, -112f); b.zumbi(20f, -112f)

        val pD = bloqueio(b, CidadeMapa.PORTAO_D_Z, 20)

        // ordem: A, B, R, C, D no builder -> reordena para o vetor fixo A, B, C, D, R
        Fases.portoes = intArrayOf(pA, pB, pC, pD, pR)

        // ---- pontos de nascimento (so em ruas livres: avenida e as duas colunas x = +-41)
        val sp = ArrayList<Float>()
        for (zz in floatArrayOf(140f, 118f, 95f, 72f, 48f, 30f, 10f, -20f, -35f, -50f, -80f, -90f)) {
            for (xx in floatArrayOf(-41f, 0f, 41f)) {
                if ((xx == -41f && zz == 30f) || (xx == 41f && zz == -20f)) continue // containers
                sp.add(xx); sp.add(zz)
            }
        }
        for (xx in floatArrayOf(-41f, 41f)) for (zz in floatArrayOf(-110f, -125f, -140f)) { sp.add(xx); sp.add(zz) }
        sp.add(0f); sp.add(-110f)
        b.nascedouros(sp.toFloatArray())
    }
}

/**
 * Missao da cidade. Fluxo: Chave Azul (portao A) -> 3 fusiveis na caixa de forca (portao B; o 2o fusivel acorda o Seguranca,
 * que larga a Chave Vermelha) -> notas e alavancas na ordem certa (portao C) -> Chave Vermelha abre a cela da delegacia e o
 * sobrevivente vira parceiro de IA -> ele abre o portao final -> radio no heliponto chama o Hospedeiro -> derrota-lo conclui.
 */
class Cidade(private val mundo: Mundo, val def: DefFase) : Missao {

    enum class Tipo { MERCADOR, BAU, CHAVE, FUSIVEL, PAINEL, NOTA, ALAVANCA, PORTAO, SOBREVIVENTE, RADIO }

    class Obj(val tipo: Tipo, val x: Float, val z: Float, val indice: Int, var instancia: ModelInstance, var haste: ModelInstance? = null) {
        var ativo = true
        var estado = false
    }

    val objetos = ArrayList<Obj>()
    private val modelos = ArrayList<Model>()
    private val m4 = Matrix4()
    private var tempo = 0f

    /** Preenchido pela tela: o jogador (para o parceiro seguir) e o som do tiro do parceiro. */
    var jogador: Jogador? = null
    var aoSom: ((String, Float) -> Unit)? = null
    override var aoMensagem: ((String) -> Unit)? = null
    override var aoAbrirLoja: (() -> Unit)? = null
    override var aoConcluir: (() -> Unit)? = null

    // ---- estado
    private var temChaveAzul = false
    private var temChaveVermelha = false
    private var fusiveis = 0
    private var painelLigado = false
    private val notaLida = BooleanArray(2)
    private val puxadas = IntArray(3)
    private var numPuxadas = 0
    private var puzzleResolvido = false
    private val portaoAberto = BooleanArray(5) // A, B, C, D, R
    private var miniAcordado = false
    private var miniMorto = false
    var mini: Inimigo? = null
        private set
    var chefe: Inimigo? = null
        private set
    var concluida = false
        private set
    var companheiro: Companheiro? = null
        private set
    private var salvo = false

    override var objetivoTexto = ""
        private set
    override var dicaTexto = ""
        private set

    private val mBauFechado: Model
    private val mBauAberto: Model
    private val mMercador: Model
    private val mBase: Model
    private val mHaste = arrayOfNulls<Model>(3)
    private val mFusivel: Model
    private val mChaveAzul: Model
    private val mChaveVerm: Model
    private val mNota: Model
    private val mPainel: Model
    private val mPainelLigado: Model
    private val mSobrev: Model
    private val mRadio: Model
    private val mAliado: Model

    init {
        mBauFechado = criar(listOf(
            floatArrayOf(0f, 0.30f, 0f, 0.90f, 0.60f, 0.60f, 0.45f, 0.28f, 0.12f),
            floatArrayOf(0f, 0.68f, 0f, 0.94f, 0.16f, 0.64f, 0.40f, 0.24f, 0.10f),
            floatArrayOf(0f, 0.40f, 0.31f, 0.14f, 0.18f, 0.04f, 0.85f, 0.70f, 0.20f)
        ), false)
        mBauAberto = criar(listOf(
            floatArrayOf(0f, 0.30f, 0f, 0.90f, 0.60f, 0.60f, 0.45f, 0.28f, 0.12f),
            floatArrayOf(0f, 0.62f, 0f, 0.80f, 0.04f, 0.50f, 0.10f, 0.06f, 0.04f),
            floatArrayOf(0f, 0.95f, -0.30f, 0.94f, 0.16f, 0.64f, 0.40f, 0.24f, 0.10f)
        ), false)
        mMercador = criar(listOf(
            floatArrayOf(0f, 0.55f, 0f, 0.62f, 1.10f, 0.40f, 0.34f, 0.24f, 0.16f),
            floatArrayOf(0f, 1.32f, 0f, 0.36f, 0.34f, 0.34f, 0.78f, 0.60f, 0.48f),
            floatArrayOf(0f, 1.56f, 0f, 0.58f, 0.10f, 0.58f, 0.14f, 0.10f, 0.08f),
            floatArrayOf(0f, 1.68f, 0f, 0.34f, 0.22f, 0.34f, 0.14f, 0.10f, 0.08f),
            floatArrayOf(0f, 0.85f, 0.34f, 0.78f, 0.90f, 0.32f, 0.20f, 0.34f, 0.18f),
            floatArrayOf(0.46f, 0.30f, 0.30f, 0.30f, 0.60f, 0.30f, 0.55f, 0.46f, 0.22f)
        ), false)
        mBase = criar(listOf(floatArrayOf(0f, 0.12f, 0f, 0.50f, 0.24f, 0.50f, 0.30f, 0.30f, 0.32f)), false)
        val cores = arrayOf(floatArrayOf(0.20f, 0.45f, 0.95f), floatArrayOf(0.92f, 0.20f, 0.18f), floatArrayOf(0.96f, 0.80f, 0.15f))
        for (i in 0..2) {
            val c = cores[i]
            mHaste[i] = criar(listOf(
                floatArrayOf(0f, 0.40f, 0f, 0.07f, 0.80f, 0.07f, 0.55f, 0.55f, 0.58f),
                floatArrayOf(0f, 0.86f, 0f, 0.22f, 0.22f, 0.22f, c[0], c[1], c[2])
            ), false)
        }
        mFusivel = criar(listOf(
            floatArrayOf(0f, 0f, 0f, 0.14f, 0.40f, 0.14f, 1.00f, 0.85f, 0.20f),
            floatArrayOf(0f, 0.22f, 0f, 0.20f, 0.06f, 0.20f, 0.70f, 0.70f, 0.72f),
            floatArrayOf(0f, -0.22f, 0f, 0.20f, 0.06f, 0.20f, 0.70f, 0.70f, 0.72f)
        ), true)
        mChaveAzul = criar(listOf(
            floatArrayOf(0f, 0f, 0f, 0.08f, 0.50f, 0.08f, 0.25f, 0.55f, 1.00f),
            floatArrayOf(0.10f, 0.22f, 0f, 0.20f, 0.08f, 0.08f, 0.25f, 0.55f, 1.00f),
            floatArrayOf(0f, -0.26f, 0f, 0.28f, 0.24f, 0.08f, 0.25f, 0.55f, 1.00f)
        ), true)
        mChaveVerm = criar(listOf(
            floatArrayOf(0f, 0f, 0f, 0.08f, 0.50f, 0.08f, 1.00f, 0.25f, 0.22f),
            floatArrayOf(0.10f, 0.22f, 0f, 0.20f, 0.08f, 0.08f, 1.00f, 0.25f, 0.22f),
            floatArrayOf(0f, -0.26f, 0f, 0.28f, 0.24f, 0.08f, 1.00f, 0.25f, 0.22f)
        ), true)
        mNota = criar(listOf(
            floatArrayOf(0f, 0f, 0f, 0.34f, 0.04f, 0.46f, 0.95f, 0.93f, 0.80f),
            floatArrayOf(0f, 0.03f, 0f, 0.26f, 0.02f, 0.02f, 0.30f, 0.30f, 0.32f)
        ), true)
        mPainel = criar(listOf(
            floatArrayOf(0f, 0.90f, 0f, 1.00f, 1.80f, 0.40f, 0.40f, 0.42f, 0.46f),
            floatArrayOf(-0.25f, 1.45f, 0.21f, 0.12f, 0.12f, 0.04f, 0.90f, 0.15f, 0.15f),
            floatArrayOf(0f, 1.45f, 0.21f, 0.12f, 0.12f, 0.04f, 0.90f, 0.15f, 0.15f),
            floatArrayOf(0.25f, 1.45f, 0.21f, 0.12f, 0.12f, 0.04f, 0.90f, 0.15f, 0.15f)
        ), false)
        mPainelLigado = criar(listOf(
            floatArrayOf(0f, 0.90f, 0f, 1.00f, 1.80f, 0.40f, 0.40f, 0.42f, 0.46f),
            floatArrayOf(-0.25f, 1.45f, 0.21f, 0.12f, 0.12f, 0.04f, 0.15f, 0.95f, 0.30f),
            floatArrayOf(0f, 1.45f, 0.21f, 0.12f, 0.12f, 0.04f, 0.15f, 0.95f, 0.30f),
            floatArrayOf(0.25f, 1.45f, 0.21f, 0.12f, 0.12f, 0.04f, 0.15f, 0.95f, 0.30f)
        ), true)
        mSobrev = criar(listOf(
            floatArrayOf(0f, 0.55f, 0f, 0.50f, 1.00f, 0.30f, 0.30f, 0.62f, 0.72f),
            floatArrayOf(0f, 1.28f, 0f, 0.30f, 0.30f, 0.30f, 0.78f, 0.60f, 0.48f),
            floatArrayOf(0f, 1.46f, 0f, 0.32f, 0.10f, 0.32f, 0.12f, 0.10f, 0.08f)
        ), false)
        mAliado = criar(listOf(
            floatArrayOf(0f, 0.55f, 0f, 0.50f, 1.00f, 0.30f, 0.30f, 0.62f, 0.72f),
            floatArrayOf(0f, 1.28f, 0f, 0.30f, 0.30f, 0.30f, 0.78f, 0.60f, 0.48f),
            floatArrayOf(0f, 1.46f, 0f, 0.32f, 0.10f, 0.32f, 0.12f, 0.10f, 0.08f),
            floatArrayOf(0.34f, 0.80f, 0.12f, 0.08f, 0.08f, 0.50f, 0.15f, 0.15f, 0.15f)
        ), false)
        mRadio = criar(listOf(
            floatArrayOf(0f, 0.40f, 0f, 1.20f, 0.80f, 0.80f, 0.30f, 0.34f, 0.30f),
            floatArrayOf(0f, 0.95f, 0f, 0.90f, 0.30f, 0.50f, 0.18f, 0.20f, 0.18f),
            floatArrayOf(0f, 2.40f, 0f, 0.06f, 3.00f, 0.06f, 0.70f, 0.70f, 0.74f),
            floatArrayOf(0f, 3.95f, 0f, 0.20f, 0.20f, 0.20f, 1.00f, 0.25f, 0.20f)
        ), false)

        val g = CidadeMapa
        objetos.add(Obj(Tipo.MERCADOR, g.MERCADOR[0], g.MERCADOR[1], 0, colocar(mMercador, g.MERCADOR[0], g.MERCADOR[1], 180f)))
        objetos.add(Obj(Tipo.CHAVE, g.CHAVE_AZUL[0], g.CHAVE_AZUL[1], 0, ModelInstance(mChaveAzul)))
        for (i in 0..2) objetos.add(Obj(Tipo.FUSIVEL, g.FUSIVEIS[i * 2], g.FUSIVEIS[i * 2 + 1], i, ModelInstance(mFusivel)))
        objetos.add(Obj(Tipo.PAINEL, g.PAINEL[0], g.PAINEL[1], 0, colocar(mPainel, g.PAINEL[0], g.PAINEL[1], 0f)))
        for (i in 0..1) objetos.add(Obj(Tipo.NOTA, g.NOTAS[i * 2], g.NOTAS[i * 2 + 1], i, ModelInstance(mNota)))
        for (i in 0..2) {
            val o = Obj(Tipo.ALAVANCA, g.ALAVANCAS[i * 2], g.ALAVANCAS[i * 2 + 1], i,
                colocar(mBase, g.ALAVANCAS[i * 2], g.ALAVANCAS[i * 2 + 1], 0f), ModelInstance(mHaste[i]!!))
            posicionarHaste(o)
            objetos.add(o)
        }
        for (i in 0 until g.BAUS.size / 2) {
            objetos.add(Obj(Tipo.BAU, g.BAUS[i * 2], g.BAUS[i * 2 + 1], i, colocar(mBauFechado, g.BAUS[i * 2], g.BAUS[i * 2 + 1], MathUtils.random(0f, 360f))))
        }
        // portoes: A, B, C, D no centro de cada linha de bloqueio; R na cela (indice = posicao em Fases.portoes)
        val gz = floatArrayOf(CidadeMapa.PORTAO_A_Z, CidadeMapa.PORTAO_B_Z, CidadeMapa.PORTAO_C_Z, CidadeMapa.PORTAO_D_Z)
        for (i in 0..3) objetos.add(Obj(Tipo.PORTAO, 0f, gz[i] + 0.2f, i, ModelInstance(mBase)).also { it.ativo = true; escondido(it) })
        objetos.add(Obj(Tipo.PORTAO, g.CELA[0], g.CELA[1], 4, ModelInstance(mBase)).also { escondido(it) })
        objetos.add(Obj(Tipo.SOBREVIVENTE, g.SOBREVIVENTE[0], g.SOBREVIVENTE[1], 0, colocar(mSobrev, g.SOBREVIVENTE[0], g.SOBREVIVENTE[1], 0f)))
        objetos.add(Obj(Tipo.RADIO, g.RADIO[0], g.RADIO[1], 0, colocar(mRadio, g.RADIO[0], g.RADIO[1], 0f)))

        mundo.aoMatar = { z -> aoMatar(z) }
        recalcular()
    }

    /** Portoes sao so logica (o desenho vem da parede colorida): a instancia existe para manter o tipo `Obj` simples. */
    private fun escondido(o: Obj) {
        o.instancia.transform.setToTranslation(0f, -100f, 0f)
    }

    // ------------------------------------------------------------------ modelos
    private fun criar(partes: List<FloatArray>, brilho: Boolean): Model {
        val mb = ModelBuilder()
        mb.begin()
        val atr = (Usage.Position or Usage.Normal).toLong()
        val m = Matrix4()
        for (i in partes.indices) {
            val p = partes[i]
            val mat = Material(ColorAttribute.createDiffuse(p[6], p[7], p[8], 1f))
            if (brilho) mat.set(ColorAttribute.createEmissive(p[6] * 0.55f, p[7] * 0.55f, p[8] * 0.55f, 1f))
            val mp = mb.part("p" + i, GL20.GL_TRIANGLES, atr, mat)
            m.setToTranslation(p[0], p[1], p[2])
            mp.setVertexTransform(m)
            mp.box(p[3], p[4], p[5])
        }
        val modelo = mb.end()
        modelos.add(modelo)
        return modelo
    }

    private fun colocar(m: Model, x: Float, z: Float, graus: Float): ModelInstance {
        val inst = ModelInstance(m)
        inst.transform.setToTranslation(x, 0f, z).rotate(Vector3.Y, graus)
        return inst
    }

    private fun posicionarHaste(o: Obj) {
        val h = o.haste ?: return
        h.transform.setToTranslation(o.x, 0.2f, o.z).rotate(Vector3.X, if (o.estado) 38f else -38f)
    }

    // ------------------------------------------------------------------ textos do HUD
    private fun recalcular() {
        objetivoTexto = objetivo()
        dicaTexto = dica()
    }

    private fun objetivo(): String = when {
        concluida -> "Cidade salva!"
        chefe != null -> "Derrote " + def.chefeNome
        portaoAberto[3] -> "Ligue o radio no heliponto"
        !portaoAberto[0] -> "Ache a Chave Azul"
        !portaoAberto[1] -> if (painelLigado) "Abra o portao eletrico" else "Fusiveis: " + fusiveis + "/3"
        miniAcordado && !miniMorto -> "Derrote " + def.miniNome
        !portaoAberto[2] -> if (puzzleResolvido) "Abra o portao do norte" else "Alavancas: ache as notas"
        !salvo -> if (temChaveVermelha) "Abra a cela da delegacia" else "Pegue a Chave Vermelha"
        else -> "Abra o portao final"
    }

    private fun dica(): String = when {
        concluida -> ""
        chefe != null -> "Seu parceiro ajuda a atirar!"
        portaoAberto[3] -> "O radio fica no heliponto, no norte."
        !portaoAberto[0] -> "A chave esta numa garagem no oeste do subúrbio. Siga a avenida ao norte depois."
        !portaoAberto[1] -> if (painelLigado) "A energia voltou: va ao portao eletrico, na avenida."
            else if (fusiveis >= 3) "Leve os fusiveis a caixa de forca, na praca central."
            else "Fusiveis: supermercado (oeste), farmacia (leste) e banco (sul)."
        miniAcordado && !miniMorto -> def.miniNome + " vai soltar a Chave Vermelha."
        !portaoAberto[2] -> if (puzzleResolvido) "O portao do norte destrancou." else "Leia as notas (metro e rua) e puxe as alavancas na ordem. " + ordemTexto()
        !salvo -> if (temChaveVermelha) "A cela fica dentro da delegacia, no oeste." else "O Seguranca guarda a Chave Vermelha da delegacia."
        else -> "O sobrevivente sabe o codigo do portao final."
    }

    private fun ordemTexto(): String {
        val nomes = arrayOf("azul", "vermelha", "amarela")
        val o = CidadeMapa.ORDEM
        val a = if (notaLida[0]) nomes[o[0]] else "?"
        val b = if (notaLida[1]) nomes[o[1]] else "?"
        val c = if (notaLida[1]) nomes[o[2]] else "?"
        return "(ordem: $a, $b, $c)"
    }

    override fun chefeAtual(): Inimigo? {
        val c = chefe
        if (c != null && c.vivo) return c
        val m = mini
        if (m != null && m.vivo) return m
        return null
    }

    override fun nomeChefeAtual(): String = if (chefe?.vivo == true) def.chefeNome else def.miniNome

    // ------------------------------------------------------------------ jogo
    override fun atualizar(dt: Float) {
        tempo += dt
        for (o in objetos) {
            if (!o.ativo) continue
            when (o.tipo) {
                Tipo.FUSIVEL, Tipo.CHAVE -> o.instancia.transform.setToTranslation(o.x, 1.0f + MathUtils.sin(tempo * 2.2f) * 0.12f, o.z)
                    .rotate(Vector3.Y, tempo * 80f)
                Tipo.NOTA -> o.instancia.transform.setToTranslation(o.x, 0.9f + MathUtils.sin(tempo * 2.0f) * 0.08f, o.z)
                    .rotate(Vector3.Y, tempo * 50f).rotate(Vector3.X, 20f)
                else -> {}
            }
        }
        val j = jogador
        val c = companheiro
        if (j != null && c != null) c.atualizar(dt, j, mundo) { aoSom?.invoke("tiro_pistola", 0.45f) }
        if (chefe != null && !concluida && chefe?.vivo == false) concluir()
    }

    private fun concluir() {
        if (concluida) return
        concluida = true
        Progresso.faseMaxima = maxOf(Progresso.faseMaxima, Fases.indice + 1)
        recalcular()
        aoMensagem?.invoke("O helicoptero chegou! Cidade salva!")
        aoConcluir?.invoke()
    }

    private fun aoMatar(z: Inimigo) {
        val ouro = when (z.tipo) {
            TipoZumbi.NORMAL -> 5
            TipoZumbi.RAPIDO -> 4
            TipoZumbi.LENTO -> 12
            TipoZumbi.FORTE -> 8
            TipoZumbi.MINICHEFE -> 120
            TipoZumbi.CHEFE -> 400
        }
        Progresso.ouro += ouro
        if (z === mini && !miniMorto) {
            miniMorto = true
            val k = Obj(Tipo.CHAVE, z.posicao.x, z.posicao.z, 1, ModelInstance(mChaveVerm))
            objetos.add(k)
            recalcular()
            aoMensagem?.invoke(def.miniNome + " caiu e largou a Chave Vermelha! +" + ouro + " ouro")
        }
        if (z === chefe) concluir()
    }

    private fun raioDe(o: Obj): Float = if (o.tipo == Tipo.PORTAO) 4.2f else RAIO

    private fun maisPerto(x: Float, z: Float): Obj? {
        var melhor: Obj? = null
        var d2m = Float.MAX_VALUE
        for (o in objetos) {
            if (!o.ativo) continue
            if (o.tipo == Tipo.BAU && o.estado) continue
            if (o.tipo == Tipo.PORTAO && portaoAberto[o.indice]) continue
            if (o.tipo == Tipo.SOBREVIVENTE && salvo) continue
            val dx = o.x - x
            val dz = o.z - z
            val d2 = dx * dx + dz * dz
            val r = raioDe(o)
            if (d2 <= r * r && d2 < d2m) { d2m = d2; melhor = o }
        }
        return melhor
    }

    override fun dicaProximo(x: Float, z: Float): String? {
        val o = maisPerto(x, z) ?: return null
        return when (o.tipo) {
            Tipo.MERCADOR -> "INTERAGIR: falar com o Mercador"
            Tipo.BAU -> "INTERAGIR: abrir bau"
            Tipo.CHAVE -> "INTERAGIR: pegar a chave"
            Tipo.FUSIVEL -> "INTERAGIR: pegar fusivel"
            Tipo.PAINEL -> if (painelLigado) null else "INTERAGIR: caixa de forca"
            Tipo.NOTA -> "INTERAGIR: ler nota"
            Tipo.ALAVANCA -> if (o.estado) null else "INTERAGIR: puxar alavanca"
            Tipo.PORTAO -> "INTERAGIR: abrir portao"
            Tipo.SOBREVIVENTE -> "INTERAGIR: libertar o sobrevivente"
            Tipo.RADIO -> "INTERAGIR: radio"
        }
    }

    override fun interagir(x: Float, z: Float): Boolean {
        val o = maisPerto(x, z) ?: return false
        when (o.tipo) {
            Tipo.MERCADOR -> aoAbrirLoja?.invoke()
            Tipo.BAU -> {
                o.estado = true
                o.instancia = ModelInstance(mBauAberto).also { it.transform.set(o.instancia.transform) }
                val ouro = MathUtils.random(40, 80)
                Progresso.ouro += ouro
                aoMensagem?.invoke("Tesouro! +" + ouro + " ouro")
            }
            Tipo.CHAVE -> pegarChave(o)
            Tipo.FUSIVEL -> pegarFusivel(o)
            Tipo.PAINEL -> usarPainel(o)
            Tipo.NOTA -> lerNota(o)
            Tipo.ALAVANCA -> puxar(o)
            Tipo.PORTAO -> abrirPortao(o.indice)
            Tipo.SOBREVIVENTE -> resgatar(o)
            Tipo.RADIO -> usarRadio()
        }
        return true
    }

    private fun pegarChave(o: Obj) {
        o.ativo = false
        if (o.indice == 0) {
            temChaveAzul = true
            aoMensagem?.invoke("Pegou a Chave Azul! Ela abre o portao da avenida.")
        } else {
            temChaveVermelha = true
            aoMensagem?.invoke("Pegou a Chave Vermelha (cela da delegacia).")
        }
        recalcular()
    }

    private fun pegarFusivel(o: Obj) {
        o.ativo = false
        fusiveis++
        recalcular()
        aoMensagem?.invoke("Pegou um fusivel (" + fusiveis + "/3)")
        if (o.indice == 1 && !miniAcordado) {
            miniAcordado = true
            mini = mundo.nascerEspecial(TipoZumbi.MINICHEFE, CidadeMapa.MINI_SPAWN[0], CidadeMapa.MINI_SPAWN[1], def.fatorVida)
            recalcular()
            aoMensagem?.invoke(def.miniNome + " apareceu! Cuidado!")
        }
    }

    private fun usarPainel(o: Obj) {
        if (painelLigado) return
        if (fusiveis < 3) {
            aoMensagem?.invoke("Faltam " + (3 - fusiveis) + " fusivel(is)")
            return
        }
        painelLigado = true
        o.instancia = ModelInstance(mPainelLigado).also { it.transform.set(o.instancia.transform) }
        recalcular()
        aoMensagem?.invoke("Energia restaurada! O portao eletrico destrancou.")
    }

    private fun lerNota(o: Obj) {
        notaLida[o.indice] = true
        val nomes = arrayOf("azul", "vermelha", "amarela")
        val ordem = CidadeMapa.ORDEM
        aoMensagem?.invoke(
            if (o.indice == 0) "Nota: a PRIMEIRA alavanca e a " + nomes[ordem[0]]
            else "Nota: depois a " + nomes[ordem[1]] + ", por fim a " + nomes[ordem[2]]
        )
        recalcular()
    }

    private fun puxar(o: Obj) {
        if (puzzleResolvido) { aoMensagem?.invoke("As alavancas ja foram usadas"); return }
        if (o.estado) return
        if (!notaLida[0] || !notaLida[1]) {
            // sem as 2 notas o jogador nao sabe a ordem: pode tentar, mas errar reinicia
        }
        val esperado = CidadeMapa.ORDEM[numPuxadas]
        if (o.indice != esperado) {
            for (x in objetos) if (x.tipo == Tipo.ALAVANCA) { x.estado = false; posicionarHaste(x) }
            numPuxadas = 0
            aoMensagem?.invoke("Ordem errada! As alavancas voltaram.")
            return
        }
        o.estado = true
        posicionarHaste(o)
        puxadas[numPuxadas] = o.indice
        numPuxadas++
        if (numPuxadas >= 3) {
            puzzleResolvido = true
            recalcular()
            aoMensagem?.invoke("Algo destrancou ao norte! (portao amarelo)")
        } else {
            aoMensagem?.invoke("Alavanca " + numPuxadas + "/3")
        }
    }

    private fun abrirPortao(i: Int) {
        val ok = when (i) {
            0 -> temChaveAzul
            1 -> painelLigado
            2 -> puzzleResolvido
            3 -> salvo
            else -> temChaveVermelha
        }
        if (!ok) {
            aoMensagem?.invoke(
                when (i) {
                    0 -> "Trancado: precisa da Chave Azul"
                    1 -> "Sem energia: ligue a caixa de forca (3 fusiveis)"
                    2 -> "Trancado: resolva as alavancas"
                    3 -> "Trancado: so o sobrevivente da delegacia sabe o codigo"
                    else -> "Cela trancada: precisa da Chave Vermelha"
                }
            )
            return
        }
        portaoAberto[i] = true
        val idx = Fases.portoes.getOrNull(i) ?: -1
        mundo.removerParede(idx)
        recalcular()
        aoMensagem?.invoke("Portao aberto!")
    }

    private fun resgatar(o: Obj) {
        if (salvo) return
        salvo = true
        o.ativo = false
        val modelo = jogador?.modeloAnimadoCompartilhado
        companheiro = if (modelo != null) Companheiro(modelo, true, o.x, o.z) else Companheiro(mAliado, false, o.x, o.z)
        recalcular()
        aoMensagem?.invoke("Sobrevivente resgatado! Ele vai lutar com voce.")
        aoSom?.invoke("item_pegar", 0.9f)
    }

    private fun usarRadio() {
        if (chefe == null) {
            chefe = mundo.nascerEspecial(TipoZumbi.CHEFE, CidadeMapa.CHEFE_SPAWN[0], CidadeMapa.CHEFE_SPAWN[1], def.fatorVida)
            recalcular()
            aoMensagem?.invoke("O radio atraiu " + def.chefeNome + "!")
        } else if (!concluida) {
            aoMensagem?.invoke("Derrote " + def.chefeNome + "!")
        }
    }

    override fun desenhar(lote: ModelBatch, ambiente: Environment) {
        for (o in objetos) {
            if (!o.ativo || o.tipo == Tipo.PORTAO) continue
            lote.render(o.instancia, ambiente)
            o.haste?.let { lote.render(it, ambiente) }
        }
        companheiro?.let { lote.render(it.instancia, ambiente) }
    }

    override fun dispose() {
        mundo.aoMatar = null
        for (m in modelos) m.dispose()
    }

    companion object {
        const val RAIO = 2.2f
    }
}
