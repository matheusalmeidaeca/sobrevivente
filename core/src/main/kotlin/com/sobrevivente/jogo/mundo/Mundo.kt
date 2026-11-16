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
import com.badlogic.gdx.graphics.g3d.environment.DirectionalShadowLight
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
    /** Sol com sombra real (null se Constantes.SOMBRAS_REAIS = false). Quem desenha chama begin/end dela. */
    var luzSol: DirectionalShadowLight? = null
        private set
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
    /** Loot largado pelos zumbis mortos (F10). */
    val drops = ArrayList<ItemMundo>()
    private val marcadoresMunicao: Array<ModelInstance>
    private val marcadoresCura: Array<ModelInstance>
    private var tempoGlobal = 0f
    private val modelosParedes: Array<Model>
    val paredes: Array<ModelInstance>
    private val caixasParedes: Array<BoundingBox> // para o tiro (hitscan) não atravessar paredes
    private val modelosPisos = ArrayList<Model>()
    /** Pisos das casas, andares altos e rampas (so desenho). */
    val pisos = ArrayList<ModelInstance>()

    /** Visual da vila: ceu, decoracao, telhados (F6). */
    val cenario = Cenario()
    private val modeloSombra: Model = Cenario.criarModeloSombra()
    /** Sombras redondas: 0 = jogador, 1.. = zumbis (mesma ordem de `inimigos`). */
    val sombras: Array<ModelInstance>

    /** Ultimo tiro: 0 = nada, 1 = zumbi, 2 = parede/piso. `impacto` = ponto; `impactoMorreu` = o zumbi morreu. */
    var impactoTipo = 0
        private set
    var impactoMorreu = false
        private set
    val impacto = Vector3()

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
        if (Constantes.SOMBRAS_REAIS) {
            val sol = DirectionalShadowLight(
                Constantes.SOMBRA_MAPA_TAMANHO, Constantes.SOMBRA_MAPA_TAMANHO,
                Constantes.SOMBRA_AREA_M, Constantes.SOMBRA_AREA_M, 1f, Constantes.SOMBRA_PROFUNDIDADE_M
            )
            sol.set(
                Constantes.LUZ_SOL_R, Constantes.LUZ_SOL_G, Constantes.LUZ_SOL_B,
                Constantes.LUZ_SOL_DIR_X, Constantes.LUZ_SOL_DIR_Y, Constantes.LUZ_SOL_DIR_Z
            )
            ambiente.add(sol)
            ambiente.shadowMap = sol
            luzSol = sol
        } else {
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
        }

        ambiente.set(ColorAttribute(ColorAttribute.Fog, Constantes.NEVOA_R, Constantes.NEVOA_G, Constantes.NEVOA_B, 1f))
        sombras = Array(Constantes.INIMIGOS_POOL.coerceAtLeast(Constantes.INIMIGOS_XZ.size / 2) + 1) { ModelInstance(modeloSombra) }
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
        marcadoresMunicao = Array(inimigos.size) { ModelInstance(modeloItemMunicao) }
        marcadoresCura = Array(inimigos.size) { ModelInstance(modeloItemCura) }
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

    /** Cercas, troncos, caixotes/barris, poco e fardos so colidem; o desenho deles vem da decoracao (Cenario). */
    fun paredeVisivel(i: Int): Boolean {
        val t = Constantes.PAREDES_TIPO[i]
        return t != 2 && t != 5 && t != 9 && t != 10 && t != 11
    }

    private fun corDaParede(tipo: Int): FloatArray = when (tipo) {
        1 -> floatArrayOf(Constantes.PAREDE_COR_PEDRA_R, Constantes.PAREDE_COR_PEDRA_G, Constantes.PAREDE_COR_PEDRA_B)
        2 -> floatArrayOf(Constantes.PAREDE_COR_CERCA_R, Constantes.PAREDE_COR_CERCA_G, Constantes.PAREDE_COR_CERCA_B)
        3 -> floatArrayOf(Constantes.PAREDE_COR_CELEIRO_R, Constantes.PAREDE_COR_CELEIRO_G, Constantes.PAREDE_COR_CELEIRO_B)
        4 -> floatArrayOf(Constantes.PAREDE_COR_GRADE_R, Constantes.PAREDE_COR_GRADE_G, Constantes.PAREDE_COR_GRADE_B)
        5 -> floatArrayOf(Constantes.PAREDE_COR_TRONCO_R, Constantes.PAREDE_COR_TRONCO_G, Constantes.PAREDE_COR_TRONCO_B)
        6 -> floatArrayOf(0.74f, 0.66f, 0.50f) // reboco creme
        7 -> floatArrayOf(0.70f, 0.52f, 0.34f) // ocre
        8 -> floatArrayOf(0.56f, 0.60f, 0.60f) // cinza azulado
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
        atropelos()
        atualizarMarcadores(dt)
        cenario.atualizar(jogador.posicao.x, jogador.posicao.y, jogador.posicao.z)
        atualizarSombras(jogador)
        if (jogador.vida <= 0) return
        tempoSpawn -= dt
        if (tempoSpawn <= 0f) {
            tempoSpawn = Constantes.SPAWN_INTERVALO_S
            tentarNascer(jogador, dirX, dirZ)
        }
    }

    /** Zumbis mais rapidos atropelam os mais lentos: o lento cai (sem dano) e o rapido segue. */
    private fun atropelos() {
        val r2 = Constantes.ATROPELO_RAIO * Constantes.ATROPELO_RAIO
        for (i in inimigos.indices) {
            val a = inimigos[i]
            if (!a.vivo || a.caido || !a.emMovimento) continue
            for (j in inimigos.indices) {
                if (i == j) continue
                val b = inimigos[j]
                if (!b.vivo || b.imune) continue
                if (a.tipo.velocidade < b.tipo.velocidade * Constantes.ATROPELO_RAZAO_VEL) continue
                val dx = b.posicao.x - a.posicao.x
                val dz = b.posicao.z - a.posicao.z
                if (kotlin.math.abs(a.posicao.y - b.posicao.y) > 1f) continue
                val d2 = dx * dx + dz * dz
                if (d2 > r2 || d2 < 1e-6f) continue
                val d = kotlin.math.sqrt(d2)
                b.atropelado(dx / d, dz / d)
            }
        }
    }

    /** Cubinho girando sobre a cabeca dos zumbis que carregam loot (azul = municao, verde = cura). */
    private fun atualizarMarcadores(dt: Float) {
        tempoGlobal += dt
        for (i in inimigos.indices) {
            val z = inimigos[i]
            val l = z.loot
            if (!z.vivo || l == 0) continue
            val m = if (l == 2) marcadoresCura[i] else marcadoresMunicao[i]
            m.transform.setToTranslation(z.posicao.x, z.posicao.y + z.alturaMarcador + kotlin.math.sin(tempoGlobal * 3f) * 0.05f, z.posicao.z)
                .rotate(Vector3.Y, tempoGlobal * 90f).scale(0.55f, 0.55f, 0.55f)
        }
    }

    /** Marcador de loot do zumbi `i` (null se ele nao carrega nada ou nao esta vivo). */
    fun marcadorLoot(i: Int): ModelInstance? {
        val z = inimigos[i]
        if (!z.vivo || !z.visivel) return null
        return when (z.loot) {
            1 -> marcadoresMunicao[i]
            2 -> marcadoresCura[i]
            else -> null
        }
    }

    /** O zumbi morto larga o loot que carregava (municao aleatoria ou cura) onde caiu. */
    /** Zumbis mortos pelo jogador (mostrado no HUD). */
    var abates = 0
        private set

    /**
     * Golpe de faca: acerta todo zumbi vivo dentro do alcance e do arco a frente de (x, z) olhando para (dirX, dirZ).
     * Preenche impacto/impactoTipo/impactoMorreu (para o sangue). Devolve true se acertou alguem.
     */
    fun golpeFaca(x: Float, z: Float, y: Float, dirX: Float, dirZ: Float): Boolean {
        impactoTipo = 0
        impactoMorreu = false
        var acertou = false
        for (i in inimigos.indices) {
            val inimigo = inimigos[i]
            if (!inimigo.vivo) continue
            val dx = inimigo.posicao.x - x
            val dz = inimigo.posicao.z - z
            val d = kotlin.math.sqrt(dx * dx + dz * dz)
            if (d > Constantes.FACA_ALCANCE + Constantes.ENIMIGO_RAIO) continue
            if (d > 0.05f) {
                val cosA = (dx * dirX + dz * dirZ) / d
                if (cosA < Constantes.FACA_ARCO_COS) continue
            }
            acertou = true
            impactoTipo = 1
            impacto.set(inimigo.posicao.x, inimigo.posicao.y + 1.1f, inimigo.posicao.z)
            val morreu = inimigo.receberTiro(Constantes.FACA_DANO, 1.1f, dirX, dirZ)
            impactoMorreu = morreu
            if (morreu) soltarLoot(inimigo)
        }
        return acertou
    }

    private fun soltarLoot(z: Inimigo) {
        abates++
        val l = z.consumirLoot()
        if (l == 0) return
        var k = drops.size - 1
        while (k >= 0) {
            if (drops[k].coletado) drops.removeAt(k)
            k--
        }
        if (drops.size >= Constantes.LOOT_MAX_NO_CHAO) drops.removeAt(0)
        drops.add(
            if (l == 2) ItemMundo(modeloItemCura, TipoItem.CURA, Constantes.ITEM_CURA_QUANTIDADE, z.posicao.x, z.posicao.z, z.posicao.y)
            else ItemMundo(
                modeloItemMunicao, TipoItem.MUNICAO,
                MathUtils.random(Constantes.LOOT_MUNICAO_MIN, Constantes.LOOT_MUNICAO_MAX), z.posicao.x, z.posicao.z, z.posicao.y
            )
        )
    }

    /** Posiciona as sombras redondas sob o jogador e os zumbis visiveis. */
    private fun atualizarSombras(jogador: Jogador) {
        val r0 = Constantes.SOMBRA_RAIO_JOGADOR * 2f
        sombras[0].transform.setToTranslation(jogador.posicao.x, jogador.posicao.y + 0.05f, jogador.posicao.z).scale(r0, 1f, r0)
        val r1 = Constantes.SOMBRA_RAIO_ZUMBI * 2f
        for (i in inimigos.indices) {
            val z = inimigos[i]
            sombras[i + 1].transform.setToTranslation(z.posicao.x, z.posicao.y + 0.05f, z.posicao.z).scale(r1, 1f, r1)
        }
    }

    /** Sombra do zumbi `i` deve ser desenhada? (vivo e longe nao importa: so some quando o zumbi some). */
    fun sombraDoZumbiVisivel(i: Int): Boolean = inimigos[i].vivo

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
        abates = 0
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
        drops.clear()
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
        for (i in drops.indices) {
            val item = drops[i]
            if (item.coletado) continue
            val dx = item.x - x
            val dz = item.z - z
            if (kotlin.math.abs(item.y - y) > 1.8f) continue
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
        impactoTipo = 0
        impactoMorreu = false
        val alvo = melhor
        if (alvo != null) {
            impactoTipo = 1
            impacto.set(origem).mulAdd(direcao, melhorDistancia)
            impactoMorreu = alvo.receberTiro(dano, impacto.y - alvo.posicao.y, direcao.x, direcao.z)
            if (impactoMorreu) soltarLoot(alvo)
        } else if (melhorDistancia < alcance - 0.01f) {
            impactoTipo = 2 // parede ou piso
            impacto.set(origem).mulAdd(direcao, melhorDistancia)
        }
        return alvo != null
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

    /** Chao em quadrados de 2 m, cada um com um tom de verde/terra um pouco diferente (cor por vertice): sem cara de tabuleiro. */
    private fun criarModeloChao(): Model {
        val atributos = (Usage.Position or Usage.Normal or Usage.ColorPacked).toLong()
        val lado = Constantes.CHAO_LADO_QUADRADO
        val quantidade = (Constantes.CHAO_TAMANHO / lado).toInt()
        val inicio = -Constantes.CHAO_TAMANHO / 2f
        val construtor = ModelBuilder()
        construtor.begin()
        val parte = construtor.part(
            "chao", GL20.GL_TRIANGLES, atributos,
            Material(IntAttribute.createCullFace(GL20.GL_NONE))
        )
        val rnd = java.util.Random(42)
        for (i in 0 until quantidade) {
            for (j in 0 until quantidade) {
                val x0 = inicio + i * lado
                val z0 = inicio + j * lado
                // manchas grandes (ondas) + variacao pequena por quadrado; um pouco mais seco/amarelado em algumas zonas
                val onda = MathUtils.sin(i * 0.23f) * MathUtils.cos(j * 0.19f) * 0.5f + 0.5f
                val seco = MathUtils.clamp(onda * 0.8f + rnd.nextFloat() * 0.25f - 0.1f, 0f, 1f)
                val brilho = 0.86f + rnd.nextFloat() * 0.22f
                parte.setColor(
                    MathUtils.lerp(0.30f, 0.46f, seco) * brilho,
                    MathUtils.lerp(0.42f, 0.44f, seco) * brilho,
                    MathUtils.lerp(0.20f, 0.24f, seco) * brilho,
                    1f
                )
                parte.rect(
                    x0, 0f, z0,
                    x0, 0f, z0 + lado,
                    x0 + lado, 0f, z0 + lado,
                    x0 + lado, 0f, z0,
                    0f, 1f, 0f
                )
            }
        }
        return construtor.end()
    }

    override fun dispose() {
        modeloChao.dispose()
        luzSol?.dispose()
        modeloSombra.dispose()
        cenario.dispose()
        modeloInimigo.dispose()
        modeloItemCura.dispose()
        modeloItemMunicao.dispose()
        for (i in modelosParedes.indices) modelosParedes[i].dispose()
        for (mp in modelosPisos) mp.dispose()
        modeloArmaFuzil?.dispose()
        modeloArmaEscopeta?.dispose()
    }
}
