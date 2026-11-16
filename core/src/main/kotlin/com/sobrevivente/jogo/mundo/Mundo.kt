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
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.utils.Disposable
import com.sobrevivente.jogo.Constantes
import com.sobrevivente.jogo.inventario.TipoItem

/** Cena: luz, chão, paredes, inimigos e itens no chão. Sem desenho de HUD aqui. */
class Mundo : Disposable {

    val ambiente = Environment()
    val chao: ModelInstance
    private val modeloChao: Model
    /** Modelo animado do zumbi (F2); null = capsula placeholder. */
    private val modeloZumbi: Model? = CarregadorModelos.carregar(Constantes.MODELO_ZUMBI)
    private val modeloInimigo: Model
    val inimigos: Array<Inimigo>
    private val modeloItemCura: Model
    private val modeloItemMunicao: Model
    val itens: Array<ItemMundo>
    private val modelosParedes: Array<Model>
    val paredes: Array<ModelInstance>
    private val caixasParedes: Array<BoundingBox> // para o tiro (hitscan) não atravessar paredes
    private val modeloPisoInterno: Model
    val pisoInterno: ModelInstance

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

        modeloInimigo = modeloZumbi ?: criarModeloInimigo()
        val quantidade = minOf(Constantes.INIMIGOS_ATIVOS, Constantes.INIMIGOS_XZ.size / 2)
        inimigos = Array(quantidade) { i ->
            Inimigo(
                modeloInimigo,
                Constantes.INIMIGOS_XZ[i * 2],
                Constantes.INIMIGOS_XZ[i * 2 + 1],
                modeloZumbi != null
            )
        }

        modeloItemCura = criarModeloItem(
            Constantes.ITEM_COR_CURA_R, Constantes.ITEM_COR_CURA_G, Constantes.ITEM_COR_CURA_B
        )
        modeloItemMunicao = criarModeloItem(
            Constantes.ITEM_COR_MUNICAO_R, Constantes.ITEM_COR_MUNICAO_G, Constantes.ITEM_COR_MUNICAO_B
        )
        val totalParedes = Constantes.PAREDES.size / 4
        modelosParedes = Array(totalParedes) { i ->
            val largura = Constantes.PAREDES[i * 4 + 2] - Constantes.PAREDES[i * 4]
            val profundidade = Constantes.PAREDES[i * 4 + 3] - Constantes.PAREDES[i * 4 + 1]
            criarModeloCaixa(
                largura, Constantes.PAREDE_ALTURA, profundidade,
                Constantes.PAREDE_COR_R, Constantes.PAREDE_COR_G, Constantes.PAREDE_COR_B
            )
        }
        paredes = Array(totalParedes) { i ->
            val instancia = ModelInstance(modelosParedes[i])
            val centroX = (Constantes.PAREDES[i * 4] + Constantes.PAREDES[i * 4 + 2]) * 0.5f
            val centroZ = (Constantes.PAREDES[i * 4 + 1] + Constantes.PAREDES[i * 4 + 3]) * 0.5f
            instancia.transform.setToTranslation(centroX, Constantes.PAREDE_ALTURA * 0.5f, centroZ)
            instancia
        }
        caixasParedes = Array(totalParedes) { i ->
            BoundingBox(
                Vector3(Constantes.PAREDES[i * 4], 0f, Constantes.PAREDES[i * 4 + 1]),
                Vector3(Constantes.PAREDES[i * 4 + 2], Constantes.PAREDE_ALTURA, Constantes.PAREDES[i * 4 + 3])
            )
        }

        val piso = Constantes.PISO_INTERNO
        modeloPisoInterno = criarModeloCaixa(
            piso[2] - piso[0], Constantes.PISO_INTERNO_ESPESSURA, piso[3] - piso[1],
            Constantes.PISO_INTERNO_COR_R, Constantes.PISO_INTERNO_COR_G, Constantes.PISO_INTERNO_COR_B
        )
        pisoInterno = ModelInstance(modeloPisoInterno)
        pisoInterno.transform.setToTranslation(
            (piso[0] + piso[2]) * 0.5f,
            Constantes.PISO_INTERNO_ESPESSURA * 0.5f,
            (piso[1] + piso[3]) * 0.5f
        )

        val totalItens = minOf(Constantes.ITENS_XZ.size / 2, Constantes.ITENS_TIPO.size)
        itens = Array(totalItens) { i ->
            val ehCura = Constantes.ITENS_TIPO[i] == Constantes.ITEM_TIPO_CURA
            ItemMundo(
                if (ehCura) modeloItemCura else modeloItemMunicao,
                if (ehCura) TipoItem.CURA else TipoItem.MUNICAO,
                if (ehCura) Constantes.ITEM_CURA_QUANTIDADE else Constantes.ITEM_MUNICAO_QUANTIDADE,
                Constantes.ITENS_XZ[i * 2],
                Constantes.ITENS_XZ[i * 2 + 1]
            )
        }
    }

    /** Atualiza a IA dos inimigos. Chamar uma vez por frame (jogo rodando). */
    fun atualizar(dt: Float, jogador: Jogador) {
        for (i in inimigos.indices) {
            inimigos[i].atualizar(dt, jogador)
        }
    }

    /** Coloca todos os inimigos de volta no início (vivos e parados) e devolve os itens ao chão. */
    fun reiniciar() {
        for (i in inimigos.indices) {
            inimigos[i].reiniciar()
        }
        for (i in itens.indices) {
            itens[i].coletado = false
        }
    }

    /** Item no chão mais perto de (x, z) dentro de `raio` metros (plano XZ), ou null. Sem alocação. */
    fun itemMaisProximo(x: Float, z: Float, raio: Float): ItemMundo? {
        var melhor: ItemMundo? = null
        var melhorQuadrado = raio * raio
        for (i in itens.indices) {
            val item = itens[i]
            if (item.coletado) continue
            val dx = item.x - x
            val dz = item.z - z
            val quadrado = dx * dx + dz * dz
            if (quadrado <= melhorQuadrado) {
                melhorQuadrado = quadrado
                melhor = item
            }
        }
        return melhor
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
        // Vale o primeiro acerto: uma parede mais perto que o inimigo bloqueia o tiro.
        for (i in caixasParedes.indices) {
            if (Intersector.intersectRayBounds(raio, caixasParedes[i], ponto)) {
                val distanciaParede = ponto.dst(origem)
                if (distanciaParede < melhorDistancia) melhorDistancia = distanciaParede
            }
        }
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

    private fun criarModeloCaixa(
        largura: Float, altura: Float, profundidade: Float,
        r: Float, g: Float, b: Float
    ): Model {
        val atributos = (Usage.Position or Usage.Normal).toLong()
        return ModelBuilder().createBox(
            largura, altura, profundidade,
            Material(ColorAttribute.createDiffuse(r, g, b, 1f)),
            atributos
        )
    }

    private fun criarModeloItem(r: Float, g: Float, b: Float): Model {
        val atributos = (Usage.Position or Usage.Normal).toLong()
        return ModelBuilder().createBox(
            Constantes.ITEM_LADO,
            Constantes.ITEM_LADO,
            Constantes.ITEM_LADO,
            Material(ColorAttribute.createDiffuse(r, g, b, 1f)),
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
        modeloItemCura.dispose()
        modeloItemMunicao.dispose()
        for (i in modelosParedes.indices) modelosParedes[i].dispose()
        modeloPisoInterno.dispose()
    }
}
