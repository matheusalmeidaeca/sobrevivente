// core/src/main/kotlin/com/sobrevivente/jogo/mundo/Mundo.kt
package com.sobrevivente.jogo.mundo

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.utils.Disposable
import com.sobrevivente.jogo.Constantes

/** Cena: luz, chão e inimigos. Sem desenho de HUD aqui. */
class Mundo : Disposable {

    val ambiente = Environment()
    val chao: ModelInstance
    private val modeloChao: Model
    private val modeloInimigo: Model
    val inimigos: Array<Inimigo>

    private val raio = Ray() // reutilizado nos tiros: zero alocação por frame
    private val ponto = Vector3()

    init {
        ambiente.set(
            ColorAttribute(
                ColorAttribute.AmbientLight,
                Constantes.LUZ_AMBIENTE_R,
                Constantes.LUZ_AMBIENTE_G,
                Constantes.LUZ_AMBIENTE_B,
                1f
            )
        )
        ambiente.add(
            DirectionalLight().set(
                Constantes.LUZ_SOL_R,
                Constantes.LUZ_SOL_G,
                Constantes.LUZ_SOL_B,
                Constantes.LUZ_SOL_DIR_X,
                Constantes.LUZ_SOL_DIR_Y,
                Constantes.LUZ_SOL_DIR_Z
            )
        )

        modeloChao = criarModeloChao()
        chao = ModelInstance(modeloChao)

        modeloInimigo = criarModeloInimigo()
        val quantidade = minOf(Constantes.INIMIGOS_ATIVOS, Constantes.INIMIGOS_XZ.size / 2)
        inimigos = Array(quantidade) { i ->
            Inimigo(modeloInimigo, Constantes.INIMIGOS_XZ[i * 2], Constantes.INIMIGOS_XZ[i * 2 + 1])
        }
    }

    /** Atualiza a IA dos inimigos. Chamar uma vez por frame (jogo rodando). */
    fun atualizar(dt: Float, jogador: Jogador) {
        for (i in inimigos.indices) {
            inimigos[i].atualizar(dt, jogador)
        }
    }

    /** Coloca todos os inimigos de volta no início (vivos e parados). */
    fun reiniciar() {
        for (i in inimigos.indices) {
            inimigos[i].reiniciar()
        }
    }

    fun contarInimigosVivos(): Int {
        var total = 0
        for (i in inimigos.indices) {
            if (inimigos[i].vivo) total++
        }
        return total
    }

    /**
     * Hitscan: raio de `origem` na `direcao`; vale o primeiro inimigo vivo dentro do `alcance`.
     * Devolve true se acertou. Sem alocação.
     */
    fun atirar(origem: Vector3, direcao: Vector3, alcance: Float, dano: Int): Boolean {
        raio.set(origem, direcao)
        var melhor: Inimigo? = null
        var melhorDistancia = alcance
        for (i in inimigos.indices) {
            val inimigo = inimigos[i]
            if (!inimigo.vivo) continue
            if (Intersector.intersectRayBounds(raio, inimigo.caixa, ponto)) {
                val distancia = ponto.dst(origem)
                if (distancia <= melhorDistancia) {
                    melhorDistancia = distancia
                    melhor = inimigo
                }
            }
        }
        melhor?.receberDano(dano)
        return melhor != null
    }

    private fun criarMaterial(r: Float, g: Float, b: Float): Material {
        // Sem descarte de faces: evita chão invisível por ordem de vértices.
        return Material(
            ColorAttribute.createDiffuse(r, g, b, 1f),
            IntAttribute.createCullFace(GL20.GL_NONE)
        )
    }

    private fun criarModeloInimigo(): Model {
        val atributos = (Usage.Position or Usage.Normal).toLong()
        return ModelBuilder().createCapsule(
            Constantes.ENIMIGO_RAIO,
            Constantes.ENIMIGO_ALTURA,
            Constantes.ENIMIGO_DIVISOES,
            Material(
                ColorAttribute.createDiffuse(
                    Constantes.ENIMIGO_COR_PARADO_R,
                    Constantes.ENIMIGO_COR_PARADO_G,
                    Constantes.ENIMIGO_COR_PARADO_B,
                    1f
                )
            ),
            atributos
        )
    }

    private fun criarModeloChao(): Model {
        val atributos = (Usage.Position or Usage.Normal).toLong()
        val materialClaro = criarMaterial(
            Constantes.CHAO_CLARO_R, Constantes.CHAO_CLARO_G, Constantes.CHAO_CLARO_B
        )
        val materialEscuro = criarMaterial(
            Constantes.CHAO_ESCURO_R, Constantes.CHAO_ESCURO_G, Constantes.CHAO_ESCURO_B
        )

        val lado = Constantes.CHAO_LADO_QUADRADO
        val quantidade = (Constantes.CHAO_TAMANHO / lado).toInt()
        val inicio = -Constantes.CHAO_TAMANHO / 2f

        val construtor = ModelBuilder()
        construtor.begin()
        // Cada parte é escrita por completo antes de começar a próxima.
        adicionarQuadrados(
            construtor.part("chao_claro", GL20.GL_TRIANGLES, atributos, materialClaro),
            quantidade, lado, inicio, 0
        )
        adicionarQuadrados(
            construtor.part("chao_escuro", GL20.GL_TRIANGLES, atributos, materialEscuro),
            quantidade, lado, inicio, 1
        )
        return construtor.end()
    }

    private fun adicionarQuadrados(
        parte: MeshPartBuilder,
        quantidade: Int,
        lado: Float,
        inicio: Float,
        paridade: Int
    ) {
        for (i in 0 until quantidade) {
            for (j in 0 until quantidade) {
                if ((i + j) % 2 != paridade) continue
                val x0 = inicio + i * lado
                val x1 = x0 + lado
                val z0 = inicio + j * lado
                val z1 = z0 + lado
                // Sentido anti-horário visto de cima; normal para cima (+Y).
                parte.rect(
                    x0, 0f, z0,
                    x0, 0f, z1,
                    x1, 0f, z1,
                    x1, 0f, z0,
                    0f, 1f, 0f
                )
            }
        }
    }

    override fun dispose() {
        modeloChao.dispose()
        modeloInimigo.dispose()
    }
}
