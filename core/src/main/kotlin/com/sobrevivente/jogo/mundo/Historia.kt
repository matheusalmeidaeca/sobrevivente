// core/src/main/kotlin/com/sobrevivente/jogo/mundo/Historia.kt
package com.sobrevivente.jogo.mundo

import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import com.sobrevivente.jogo.Constantes

/** O que o jogador leva de uma fase para a outra no modo historia. */
object Progresso {
    var ouro = 0
    var fuzil = false
    var escopeta = false
    var reserva = 0
    var curas = 0
    /** Maior fase ja liberada (0 = so a primeira). */
    var faseMaxima = 0

    fun novo() {
        ouro = 0; fuzil = false; escopeta = false; reserva = 0; curas = 0
    }
}

/**
 * Modo historia de UMA fase (F17): mercador, fragmentos (chaves), alavancas (puzzle), baus de tesouro, altar, mini chefe e chefe.
 * Fluxo: achar o fragmento 1 -> o fragmento 2 acorda o mini chefe -> alavancas na ordem certa revelam o fragmento 3
 * -> levar os 3 ao altar chama o chefe -> derrota-lo conclui a fase. Os dados de cada fase vem de `DefFase`.
 */
class Historia(private val mundo: Mundo, val def: DefFase) : Missao {

    enum class Tipo { MERCADOR, BAU, ALAVANCA, FRAGMENTO, ALTAR }

    class Obj(val tipo: Tipo, val x: Float, val z: Float, val indice: Int, var instancia: ModelInstance, var haste: ModelInstance? = null) {
        var ativo = true
        var estado = false // bau aberto, alavanca puxada, fragmento coletado
    }

    val objetos = ArrayList<Obj>()
    private val modelos = ArrayList<Model>()

    // estado da fase
    var fragmentos = 0
        private set
    private val pegou = BooleanArray(3)
    private var puzzleResolvido = false
    private val sequencia = IntArray(3)
    private var puxadas = 0
    private var miniAcordado = false
    var mini: Inimigo? = null
        private set
    var chefe: Inimigo? = null
        private set
    private var miniMorto = false
    var concluida = false
        private set
    private var tempo = 0f
    private val m4 = Matrix4()

    /** Callbacks para a tela: mensagem curta, abrir a loja, fase concluida. */
    override var aoMensagem: ((String) -> Unit)? = null
    override var aoAbrirLoja: (() -> Unit)? = null
    override var aoConcluir: (() -> Unit)? = null

    private val modeloBauFechado: Model
    private val modeloBauAberto: Model
    private val modeloMercador: Model
    private val modeloBase: Model
    private val modelosHaste = arrayOfNulls<Model>(3)
    private val modeloFragmento: Model
    private val modeloAltar: Model

    init {
        modeloBauFechado = criar(partes(
            floatArrayOf(0f, 0.30f, 0f, 0.90f, 0.60f, 0.60f, 0.45f, 0.28f, 0.12f),
            floatArrayOf(0f, 0.68f, 0f, 0.94f, 0.16f, 0.64f, 0.40f, 0.24f, 0.10f),
            floatArrayOf(0f, 0.40f, 0.31f, 0.14f, 0.18f, 0.04f, 0.85f, 0.70f, 0.20f)
        ), false)
        modeloBauAberto = criar(partes(
            floatArrayOf(0f, 0.30f, 0f, 0.90f, 0.60f, 0.60f, 0.45f, 0.28f, 0.12f),
            floatArrayOf(0f, 0.62f, 0f, 0.80f, 0.04f, 0.50f, 0.10f, 0.06f, 0.04f),
            floatArrayOf(0f, 0.95f, -0.30f, 0.94f, 0.16f, 0.64f, 0.40f, 0.24f, 0.10f)
        ), false)
        modeloMercador = criar(partes(
            floatArrayOf(0f, 0.55f, 0f, 0.62f, 1.10f, 0.40f, 0.34f, 0.24f, 0.16f),       // casaco
            floatArrayOf(0f, 1.32f, 0f, 0.36f, 0.34f, 0.34f, 0.78f, 0.60f, 0.48f),       // cabeca
            floatArrayOf(0f, 1.56f, 0f, 0.58f, 0.10f, 0.58f, 0.14f, 0.10f, 0.08f),       // aba do chapeu
            floatArrayOf(0f, 1.68f, 0f, 0.34f, 0.22f, 0.34f, 0.14f, 0.10f, 0.08f),       // chapeu
            floatArrayOf(0f, 0.85f, 0.34f, 0.78f, 0.90f, 0.32f, 0.20f, 0.34f, 0.18f),     // mochila enorme
            floatArrayOf(0.46f, 0.30f, 0.30f, 0.30f, 0.60f, 0.30f, 0.55f, 0.46f, 0.22f)   // caixa de mercadorias
        ), false)
        modeloBase = criar(partes(floatArrayOf(0f, 0.12f, 0f, 0.50f, 0.24f, 0.50f, 0.30f, 0.30f, 0.32f)), false)
        val cores = arrayOf(floatArrayOf(0.20f, 0.45f, 0.95f), floatArrayOf(0.92f, 0.20f, 0.18f), floatArrayOf(0.96f, 0.80f, 0.15f))
        for (i in 0..2) {
            val c = cores[i]
            modelosHaste[i] = criar(partes(
                floatArrayOf(0f, 0.40f, 0f, 0.07f, 0.80f, 0.07f, 0.55f, 0.55f, 0.58f),
                floatArrayOf(0f, 0.86f, 0f, 0.22f, 0.22f, 0.22f, c[0], c[1], c[2])
            ), false)
        }
        modeloFragmento = criar(partes(
            floatArrayOf(0f, 0f, 0f, 0.26f, 0.26f, 0.26f, 0.35f, 0.95f, 1.00f),
            floatArrayOf(0f, 0f, 0f, 0.20f, 0.46f, 0.20f, 0.55f, 1.00f, 0.95f)
        ), true)
        modeloAltar = criar(partes(
            floatArrayOf(0f, 0.40f, 0f, 1.60f, 0.80f, 1.00f, 0.50f, 0.50f, 0.52f),
            floatArrayOf(0f, 0.86f, 0f, 1.80f, 0.12f, 1.20f, 0.36f, 0.36f, 0.38f),
            floatArrayOf(-0.5f, 1.05f, 0f, 0.24f, 0.30f, 0.24f, 0.20f, 0.20f, 0.22f),
            floatArrayOf(0f, 1.05f, 0f, 0.24f, 0.30f, 0.24f, 0.20f, 0.20f, 0.22f),
            floatArrayOf(0.5f, 1.05f, 0f, 0.24f, 0.30f, 0.24f, 0.20f, 0.20f, 0.22f)
        ), false)

        val f = def.fragmentos
        val a = def.alavancas
        objetos.add(Obj(Tipo.MERCADOR, def.mercador[0], def.mercador[1], 0, colocar(modeloMercador, def.mercador[0], def.mercador[1], 180f)))
        for (i in 0..2) {
            val o = Obj(Tipo.FRAGMENTO, f[i * 2], f[i * 2 + 1], i, ModelInstance(modeloFragmento))
            if (i == 2) o.ativo = false // aparece quando as alavancas estiverem certas
            objetos.add(o)
        }
        for (i in 0..2) {
            val base = colocar(modeloBase, a[i * 2], a[i * 2 + 1], 0f)
            val o = Obj(Tipo.ALAVANCA, a[i * 2], a[i * 2 + 1], i, base, ModelInstance(modelosHaste[i]!!))
            posicionarHaste(o)
            objetos.add(o)
        }
        for (i in 0 until def.baus.size / 2) {
            objetos.add(Obj(Tipo.BAU, def.baus[i * 2], def.baus[i * 2 + 1], i, colocar(modeloBauFechado, def.baus[i * 2], def.baus[i * 2 + 1], MathUtils.random(0f, 360f))))
        }
        objetos.add(Obj(Tipo.ALTAR, def.altar[0], def.altar[1], 0, colocar(modeloAltar, def.altar[0], def.altar[1], 0f)))
        mundo.aoMatar = { z -> aoMatar(z) }
        recalcular()
    }

    // ------------------------------------------------------------------ modelos
    private fun partes(vararg p: FloatArray): List<FloatArray> = p.toList()

    /** Um modelo com uma caixa por parte: (cx, cy, cz, sx, sy, sz, r, g, b). `brilho` acende a cor (emissiva). */
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

    // ------------------------------------------------------------------ estado e texto do HUD
    /** Chefe que a barra de vida mostra agora (mini chefe ou chefe vivo), ou null. */
    override fun chefeAtual(): Inimigo? {
        val c = chefe
        if (c != null && c.vivo) return c
        val m = mini
        if (m != null && m.vivo) return m
        return null
    }

    override fun nomeChefeAtual(): String = if (chefe?.vivo == true) def.chefeNome else def.miniNome

    /** Textos do HUD: recalculados so quando o estado muda (sem alocacao por frame). */
    override var objetivoTexto = ""
        private set
    override var dicaTexto = ""
        private set
    private val tPegar = "INTERAGIR: pegar " + def.artefato

    private fun recalcular() {
        objetivoTexto = objetivo()
        dicaTexto = dica()
    }

    private fun objetivo(): String = when {
        concluida -> "Fase concluida!"
        chefe != null -> "Derrote " + def.chefeNome
        fragmentos >= 3 -> "Leve os fragmentos ao altar"
        miniAcordado && !miniMorto -> "Derrote " + def.miniNome
        else -> def.artefato + ": " + fragmentos + "/3"
    }

    private fun dica(): String = when {
        concluida -> ""
        chefe != null -> def.dicas[4]
        fragmentos >= 3 -> def.dicas[3]
        miniAcordado && !miniMorto -> def.dicas[1]
        miniMorto && !puzzleResolvido -> def.dicas[2] + " " + ordemTexto()
        puzzleResolvido && !pegou[2] -> "O ultimo " + def.artefato + " apareceu: procure o brilho."
        !pegou[0] && !pegou[1] -> def.dicas[0]
        else -> def.dicas[0]
    }

    private fun ordemTexto(): String {
        val nomes = arrayOf("azul", "vermelha", "amarela")
        return "(ordem: " + nomes[def.ordem[0]] + ", " + nomes[def.ordem[1]] + ", " + nomes[def.ordem[2]] + ")"
    }

    // ------------------------------------------------------------------ jogo
    override fun atualizar(dt: Float) {
        tempo += dt
        for (o in objetos) {
            if (!o.ativo || o.tipo != Tipo.FRAGMENTO) continue
            o.instancia.transform.setToTranslation(o.x, 1.1f + MathUtils.sin(tempo * 2.2f) * 0.12f, o.z)
                .rotate(Vector3.Y, tempo * 80f).rotate(Vector3.X, 35f)
        }
        if (def === Fases.atual && chefe != null && !concluida && chefe?.vivo == false) concluir()
    }

    private fun concluir() {
        if (concluida) return
        concluida = true
        Progresso.faseMaxima = maxOf(Progresso.faseMaxima, Fases.indice + 1)
        recalcular()
        aoMensagem?.invoke("FASE CONCLUIDA!")
        aoConcluir?.invoke()
    }

    private fun aoMatar(z: Inimigo) {
        val ouro = when (z.tipo) {
            TipoZumbi.NORMAL -> 5
            TipoZumbi.RAPIDO -> 4
            TipoZumbi.LENTO -> 12
            TipoZumbi.FORTE -> 8
            TipoZumbi.MINICHEFE -> 60 + 25 * Fases.indice
            TipoZumbi.CHEFE -> 200 + 100 * Fases.indice
        }
        Progresso.ouro += ouro
        if (z === mini && !miniMorto) {
            miniMorto = true
            recalcular()
            aoMensagem?.invoke(def.miniNome + " caiu! +" + ouro + " ouro")
        }
        if (z === chefe) concluir()
    }

    /** Objeto interativo mais perto (dentro de `raio` m), ou null. */
    private fun maisPerto(x: Float, z: Float, raio: Float): Obj? {
        var melhor: Obj? = null
        var d2m = raio * raio
        for (o in objetos) {
            if (!o.ativo) continue
            if (o.tipo == Tipo.BAU && o.estado) continue
            if (o.tipo == Tipo.FRAGMENTO && o.estado) continue
            val dx = o.x - x
            val dz = o.z - z
            val d2 = dx * dx + dz * dz
            if (d2 <= d2m) { d2m = d2; melhor = o }
        }
        return melhor
    }

    /** Texto de ajuda perto de um objeto ("Toque em INTERAGIR..."), ou null. */
    override fun dicaProximo(x: Float, z: Float): String? {
        val o = maisPerto(x, z, RAIO) ?: return null
        return when (o.tipo) {
            Tipo.MERCADOR -> "INTERAGIR: falar com o Mercador"
            Tipo.BAU -> "INTERAGIR: abrir bau"
            Tipo.ALAVANCA -> if (o.estado) null else "INTERAGIR: puxar alavanca"
            Tipo.FRAGMENTO -> tPegar
            Tipo.ALTAR -> "INTERAGIR: altar"
        }
    }

    /** INTERAGIR: age no objeto mais perto. Devolve true se havia um (a tela entao nao procura item no chao). */
    override fun interagir(x: Float, z: Float): Boolean {
        val o = maisPerto(x, z, RAIO) ?: return false
        when (o.tipo) {
            Tipo.MERCADOR -> aoAbrirLoja?.invoke()
            Tipo.BAU -> {
                o.estado = true
                o.instancia = ModelInstance(modeloBauAberto).also { it.transform.set(o.instancia.transform) }
                val ouro = MathUtils.random(35, 70) + 15 * Fases.indice
                Progresso.ouro += ouro
                aoMensagem?.invoke("Tesouro! +" + ouro + " ouro")
            }
            Tipo.ALAVANCA -> puxar(o)
            Tipo.FRAGMENTO -> pegar(o)
            Tipo.ALTAR -> altar()
        }
        return true
    }

    private fun pegar(o: Obj) {
        o.estado = true
        o.ativo = false
        pegou[o.indice] = true
        fragmentos++
        recalcular()
        aoMensagem?.invoke("Pegou: " + def.artefato + " (" + fragmentos + "/3)")
        if (o.indice == 1 && !miniAcordado) {
            miniAcordado = true
            mini = mundo.nascerEspecial(TipoZumbi.MINICHEFE, def.miniSpawn[0], def.miniSpawn[1], def.fatorVida)
            recalcular()
            aoMensagem?.invoke(def.miniNome + " acordou! Cuidado!")
        }
    }

    private fun puxar(o: Obj) {
        if (puzzleResolvido) { aoMensagem?.invoke("As alavancas ja foram usadas"); return }
        if (o.estado) return
        val esperado = def.ordem[puxadas]
        if (o.indice != esperado) {
            for (x in objetos) if (x.tipo == Tipo.ALAVANCA) { x.estado = false; posicionarHaste(x) }
            puxadas = 0
            aoMensagem?.invoke("Ordem errada! As alavancas voltaram.")
            return
        }
        o.estado = true
        posicionarHaste(o)
        sequencia[puxadas] = o.indice
        puxadas++
        if (puxadas >= 3) {
            puzzleResolvido = true
            recalcular()
            for (x in objetos) if (x.tipo == Tipo.FRAGMENTO && x.indice == 2) x.ativo = true
            aoMensagem?.invoke("Algo destrancou! O ultimo " + def.artefato + " apareceu.")
        } else {
            aoMensagem?.invoke("Alavanca " + puxadas + "/3")
        }
    }

    private fun altar() {
        if (fragmentos < 3) {
            aoMensagem?.invoke("Faltam " + (3 - fragmentos) + " " + def.artefato + "(s)")
            return
        }
        if (chefe == null) {
            chefe = mundo.nascerEspecial(TipoZumbi.CHEFE, def.chefeSpawn[0], def.chefeSpawn[1], def.fatorVida)
            recalcular()
            aoMensagem?.invoke(def.chefeNome + " surgiu!")
        } else if (!concluida) {
            aoMensagem?.invoke("Derrote " + def.chefeNome + "!")
        }
    }

    /** Desenha os objetos da fase (chamar dentro de um begin/end do ModelBatch). */
    override fun desenhar(lote: ModelBatch, ambiente: Environment) {
        for (o in objetos) {
            if (!o.ativo) continue
            lote.render(o.instancia, ambiente)
            o.haste?.let { lote.render(it, ambiente) }
        }
    }

    override fun dispose() {
        mundo.aoMatar = null
        for (m in modelos) m.dispose()
    }

    companion object {
        const val RAIO = 2.2f
    }
}
