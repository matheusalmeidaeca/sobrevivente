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
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.utils.Disposable
import com.sobrevivente.jogo.Constantes
import com.sobrevivente.jogo.inventario.TipoItem

/** Cena: luz, chão, vila (paredes, casas, andar alto), inimigos (com respawn) e itens no chão. Sem desenho de HUD aqui. */
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
    private val modeloArmaFuzil: Model? = CarregadorModelos.carregar(Constantes.MODELO_ARMA_FUZIL)
    private val modeloArmaEscopeta: Model? = CarregadorModelos.carregar(Constantes.MODELO_ARMA_ESCOPETA)
    val itens: Array<ItemMundo>
    private val modelosParedes: Array<Model>
    val paredes: Array<ModelInstance>
    private val caixasParedes: Array<BoundingBox> // para o tiro (hitscan) não atravessar paredes
    private val modelosPisos = ArrayList<Model>()
    /** Pisos das casas, andares altos e rampas (so desenho). */
    val pisos = ArrayList<ModelInstance>()

    private val raio = Ray() // reutilizado nos tiros: zero alocação por frame
    private val ponto = Vector3()
    private var tempoSpawn = Constantes.SPAWN_INTERVALO_S

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

        // Pool de zumbis: os primeiros ja nascem na vila; o resto fica "morto e invisivel" esperando o respawn.
        modeloInimigo = modeloZumbi ?: criarModeloInimigo()
        val iniciais = Constantes.INIMIGOS_XZ.size / 2
        inimigos = Array(maxOf(Constantes.INIMIGOS_POOL, iniciais)) { i ->
            val ativo = i < iniciais
            val inimigo = Inimigo(
                modeloInimigo,
                if (ativo) Constantes.INIMIGOS_XZ[i * 2] else 0f,
                if (ativo) Constantes.INIMIGOS_XZ[i * 2 + 1] else 0f,
                modeloZumbi != null
            )
            if (!ativo) inimigo.desativar()
            inimigo
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
            val cor = corDaParede(Constantes.PAREDES_TIPO[i])
            criarModeloCaixa(largura, Constantes.PAREDES_ALTURA[i], profundidade, cor[0], cor[1], cor[2])
        }
        paredes = Array(totalParedes) { i ->
            val instancia = ModelInstance(modelosParedes[i])
            val centroX = (Constantes.PAREDES[i * 4] + Constantes.PAREDES[i * 4 + 2]) * 0.5f
            val centroZ = (Constantes.PAREDES[i * 4 + 1] + Constantes.PAREDES[i * 4 + 3]) * 0.5f
            instancia.transform.setToTranslation(
                centroX,
                Constantes.PAREDES_BASE[i] + Constantes.PAREDES_ALTURA[i] * 0.5f,
                centroZ
            )
            instancia
        }
        caixasParedes = Array(totalParedes) { i ->
            BoundingBox(
                Vector3(Constantes.PAREDES[i * 4], Constantes.PAREDES_BASE[i], Constantes.PAREDES[i * 4 + 1]),
                Vector3(
                    Constantes.PAREDES[i * 4 + 2],
                    Constantes.PAREDES_BASE[i] + Constantes.PAREDES_ALTURA[i],
                    Constantes.PAREDES[i * 4 + 3]
                )
            )
        }

        criarPisos()

        val totalItens = minOf(Constantes.ITENS_XZ.size / 2, Constantes.ITENS_TIPO.size)
        itens = Array(totalItens) { i ->
            val tipoItem = Constantes.ITENS_TIPO[i]
            val x = Constantes.ITENS_XZ[i * 2]
            val z = Constantes.ITENS_XZ[i * 2 + 1]
            val y = Constantes.ITENS_Y[i]
            when (tipoItem) {
                Constantes.ITEM_TIPO_CURA ->
                    ItemMundo(modeloItemCura, TipoItem.CURA, Constantes.ITEM_CURA_QUANTIDADE, x, z, y, null)
                Constantes.ITEM_TIPO_FUZIL ->
                    ItemMundo(modeloArmaFuzil ?: modeloItemMunicao, TipoItem.MUNICAO, 0, x, z, y, TipoArma.FUZIL)
                Constantes.ITEM_TIPO_ESCOPETA ->
                    ItemMundo(modeloArmaEscopeta ?: modeloItemMunicao, TipoItem.MUNICAO, 0, x, z, y, TipoArma.ESCOPETA)
                else ->
                    ItemMundo(modeloItemMunicao, TipoItem.MUNICAO, Constantes.ITEM_MUNICAO_QUANTIDADE, x, z, y, null)
            }
        }
        Navegacao.livre(0f, 0f) // monta a grade de navegacao agora (e nao no meio do jogo)
    }

    private fun corDaParede(tipo: Int): FloatArray = when (tipo) {
        1 -> floatArrayOf(Constantes.PAREDE_COR_PEDRA_R, Constantes.PAREDE_COR_PEDRA_G, Constantes.PAREDE_COR_PEDRA_B)
        2 -> floatArrayOf(Constantes.PAREDE_COR_CERCA_R, Constantes.PAREDE_COR_CERCA_G, Constantes.PAREDE_COR_CERCA_B)
        3 -> floatArrayOf(Constantes.PAREDE_COR_CELEIRO_R, Constantes.PAREDE_COR_CELEIRO_G, Constantes.PAREDE_COR_CELEIRO_B)
        4 -> floatArrayOf(Constantes.PAREDE_COR_GRADE_R, Constantes.PAREDE_COR_GRADE_G, Constantes.PAREDE_COR_GRADE_B)
        5 -> floatArrayOf(Constantes.PAREDE_COR_TRONCO_R, Constantes.PAREDE_COR_TRONCO_G, Constantes.PAREDE_COR_TRONCO_B)
        else -> floatArrayOf(Constantes.PAREDE_COR_CASA_R, Constantes.PAREDE_COR_CASA_G, Constantes.PAREDE_COR_CASA_B)
    }

    /** Pisos internos das casas, andares altos e rampas: caixas finas, so para ver. */
    private fun criarPisos() {
        val p = Constantes.PISOS
        for (i in 0 until p.size / 4) {
            val m = criarModeloCaixa(
                p[i * 4 + 2] - p[i * 4], Constantes.PISO_INTERNO_ESPESSURA, p[i * 4 + 3] - p[i * 4 + 1],
                Constantes.PISO_INTERNO_COR_R, Constantes.PISO_INTERNO_COR_G, Constantes.PISO_INTERNO_COR_B
            )
            modelosPisos.add(m)
            val inst = ModelInstance(m)
            inst.transform.setToTranslation(
                (p[i * 4] + p[i * 4 + 2]) * 0.5f, Constantes.PISO_INTERNO_ESPESSURA * 0.5f, (p[i * 4 + 1] + p[i * 4 + 3]) * 0.5f
            )
            pisos.add(inst)
        }
        val a = Constantes.ALTOS
        for (i in 0 until a.size / 4) {
            val h = Constantes.ALTOS_ALTURA[i]
            val m = criarModeloCaixa(
                a[i * 4 + 2] - a[i * 4], Constantes.ALTO_ESPESSURA, a[i * 4 + 3] - a[i * 4 + 1],
                Constantes.ALTO_COR_R, Constantes.ALTO_COR_G, Constantes.ALTO_COR_B
            )
            modelosPisos.add(m)
            val inst = ModelInstance(m)
            inst.transform.setToTranslation(
                (a[i * 4] + a[i * 4 + 2]) * 0.5f, h - Constantes.ALTO_ESPESSURA * 0.5f, (a[i * 4 + 1] + a[i * 4 + 3]) * 0.5f
            )
            pisos.add(inst)
        }
        val r = Constantes.RAMPAS
        for (i in 0 until r.size / 4) {
            val eixo = Constantes.RAMPAS_EIXO[i]
            if (eixo > 1) continue // so rampas ao longo de Z tem desenho
            val h0 = Constantes.RAMPAS_ALTURAS[i * 2]
            val h1 = Constantes.RAMPAS_ALTURAS[i * 2 + 1]
            val largura = r[i * 4 + 2] - r[i * 4]
            val comprimento = r[i * 4 + 3] - r[i * 4 + 1]
            val subida = h1 - h0
            val m = criarModeloCaixa(
                largura, 0.15f, kotlin.math.sqrt(comprimento * comprimento + subida * subida),
                Constantes.RAMPA_COR_R, Constantes.RAMPA_COR_G, Constantes.RAMPA_COR_B
            )
            modelosPisos.add(m)
            val inst = ModelInstance(m)
            val angulo = MathUtils.atan2(subida, comprimento) * MathUtils.radiansToDegrees
            inst.transform
                .setToTranslation((r[i * 4] + r[i * 4 + 2]) * 0.5f, (h0 + h1) * 0.5f - 0.075f, (r[i * 4 + 1] + r[i * 4 + 3]) * 0.5f)
                .rotate(Vector3.X, if (eixo == 0) angulo else -angulo)
            pisos.add(inst)
        }
    }

    /**
     * Atualiza a IA dos inimigos e o respawn. Chamar uma vez por frame (jogo rodando).
     * (dirX, dirZ) = frente da camera no plano XZ (para nascer fora da vista).
     */
    fun atualizar(dt: Float, jogador: Jogador, dirX: Float, dirZ: Float) {
        for (i in inimigos.indices) {
            inimigos[i].atualizar(dt, jogador)
        }
        if (jogador.vida <= 0) return
        tempoSpawn -= dt
        if (tempoSpawn <= 0f) {
            tempoSpawn = Constantes.SPAWN_INTERVALO_S
            tentarNascer(jogador, dirX, dirZ)
        }
    }

    /**
     * Respawn: se ha poucos zumbis vivos, escolhe um ponto de nascimento LONGE (>= SPAWN_DIST_MIN) e fora da vista
     * (atras/ao lado da camera, ou escondido por parede), e usa um zumbi do pool que ja sumiu.
     */
    private fun tentarNascer(jogador: Jogador, dirX: Float, dirZ: Float) {
        if (contarInimigosVivos() >= Constantes.SPAWN_MAX_VIVOS) return
        var livre: Inimigo? = null
        for (i in inimigos.indices) {
            if (inimigos[i].disponivelParaNascer) {
                livre = inimigos[i]
                break
            }
        }
        val zumbi = livre ?: return
        val pontos = Constantes.SPAWNS_XZ
        val total = pontos.size / 2
        val inicio = MathUtils.random(0, total - 1)
        for (k in 0 until total) {
            val idx = (inicio + k) % total
            val sx = pontos[idx * 2]
            val sz = pontos[idx * 2 + 1]
            val dx = sx - jogador.posicao.x
            val dz = sz - jogador.posicao.z
            val d = kotlin.math.sqrt(dx * dx + dz * dz)
            if (d < Constantes.SPAWN_DIST_MIN) continue
            val cosAngulo = (dx * dirX + dz * dirZ) / d
            val naVista = cosAngulo > Constantes.SPAWN_COS_VISAO &&
                !Navegacao.paredeEntre(jogador.posicao.x, jogador.posicao.z, sx, sz)
            if (naVista) continue
            zumbi.nascer(sx, sz)
            return
        }
    }

    /** Volta ao inicio: zumbis iniciais vivos, resto some, itens no chao. */
    fun reiniciar() {
        val iniciais = Constantes.INIMIGOS_XZ.size / 2
        for (i in inimigos.indices) {
            if (i < iniciais) {
                inimigos[i].redefinirPosicao(Constantes.INIMIGOS_XZ[i * 2], Constantes.INIMIGOS_XZ[i * 2 + 1])
                inimigos[i].reiniciar()
            } else {
                inimigos[i].desativar()
            }
        }
        for (i in itens.indices) {
            itens[i].coletado = false
        }
        tempoSpawn = Constantes.SPAWN_INTERVALO_S
    }

    /** Item no chão mais perto de (x, z) dentro de `raio` metros (plano XZ), ou null. Sem alocação. */
    fun itemMaisProximo(x: Float, z: Float, y: Float, raio: Float): ItemMundo? {
        var melhor: ItemMundo? = null
        var melhorQuadrado = raio * raio
        for (i in itens.indices) {
            val item = itens[i]
            if (item.coletado) continue
            val dx = item.x - x
            val dz = item.z - z
            if (kotlin.math.abs(item.y - y) > 1.8f) continue // outro andar
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
        for (mp in modelosPisos) mp.dispose()
        modeloArmaFuzil?.dispose()
        modeloArmaEscopeta?.dispose()
    }
}
