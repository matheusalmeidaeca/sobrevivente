// ferramentas/animacao/kotlin/ExemploAnimacaoHumana.kt
// Exemplo de uso do humano.g3dj gerado por gerar_g3dj.py.
// Copie para core/src/main/kotlin/com/sobrevivente/jogo/mundo/ (o pacote ja esta certo).
package com.sobrevivente.jogo.mundo

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.utils.AnimationController
import com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController
import com.badlogic.gdx.utils.JsonReader

/** Dados de uma animacao, lidos de humano_meta.json (gerado junto com o .g3dj). */
class InfoAnimacao(val duracao: Float, val loop: Boolean, val velocidade: Float)

/** Le humano_meta.json: duracao, loop e velocidade (m/s) de cada animacao + tempos de mistura. */
class MetaHumano(caminho: String) {
    val animacoes = HashMap<String, InfoAnimacao>()
    val blendMin: Float
    val blendMax: Float

    init {
        val raiz = JsonReader().parse(Gdx.files.internal(caminho))
        var a = raiz.get("animacoes").child
        while (a != null) {
            animacoes[a.name] = InfoAnimacao(a.getFloat("duracao_s"), a.getBoolean("loop"), a.getFloat("velocidade_m_s"))
            a = a.next
        }
        val b = raiz.get("blend_s")
        blendMin = b.getFloat(0)
        blendMax = b.getFloat(1)
    }
}

/**
 * Animador com mistura SUAVE (ease-in/out, "smootherstep") entre as animacoes do humano.
 * O AnimationController padrao mistura em linha reta; aqui o peso da nova animacao sobe em curva S,
 * entao a troca nao "estala". As animacoes andar/correr sao "no lugar": a velocidade real do jogador
 * escala o tempo da animacao (passo = velocidade real / velocidade da animacao) e o pe nao escorrega.
 */
class AnimadorHumano(instancia: ModelInstance, private val meta: MetaHumano) : BaseAnimationController(instancia) {

    private class Faixa(val nome: String, val info: InfoAnimacao) {
        var tempo = 0f
    }

    private var atual: Faixa? = null
    private var anterior: Faixa? = null
    private var misturaT = 0f
    private var misturaDur = 0f
    var escalaTempo = 1f
        private set

    val nomeAtual: String get() = atual?.nome ?: ""

    /** Terminou? (so faz sentido para animacoes sem loop: atacar, dano, morrer, interagir, pular). */
    val terminou: Boolean
        get() {
            val a = atual ?: return false
            return !a.info.loop && a.tempo >= a.info.duracao
        }

    /**
     * Troca de animacao com mistura. `blend` em segundos (use meta.blendMin..meta.blendMax: 0,10 a 0,25).
     * Se for a mesma animacao em andamento, nao faz nada (use `reiniciar` para forcar).
     */
    fun tocar(nome: String, blend: Float = 0.15f, reiniciar: Boolean = false) {
        val info = meta.animacoes[nome] ?: return
        if (getAnimation(nome) == null) return
        if (!reiniciar && atual?.nome == nome) return
        anterior = atual
        atual = Faixa(nome, info)
        misturaDur = blend.coerceIn(meta.blendMin, meta.blendMax)
        misturaT = if (anterior == null) misturaDur else 0f
    }

    /** Acompanha a velocidade real (m/s) em andar/correr: evita o pe deslizar no chao. */
    fun acompanharVelocidade(velocidadeReal: Float) {
        val a = atual ?: return
        escalaTempo = if (a.info.velocidade > 0.01f) (velocidadeReal / a.info.velocidade).coerceIn(0.5f, 1.8f) else 1f
    }

    fun atualizar(dt: Float) {
        val a = atual ?: return
        avancar(a, dt * (if (a.info.velocidade > 0.01f) escalaTempo else 1f))
        val ant = anterior
        if (ant != null) {
            avancar(ant, dt)
            misturaT += dt
            if (misturaT >= misturaDur) anterior = null
        }
        val w = if (anterior == null) 1f else suave(misturaT / misturaDur)
        begin()
        val an = anterior
        if (an != null) {
            apply(getAnimation(an.nome), an.tempo.coerceAtMost(an.info.duracao), 1f)
            apply(getAnimation(a.nome), a.tempo.coerceAtMost(a.info.duracao), w)
        } else {
            apply(getAnimation(a.nome), a.tempo.coerceAtMost(a.info.duracao), 1f)
        }
        end()
    }

    private fun avancar(f: Faixa, dt: Float) {
        f.tempo += dt
        if (f.info.loop) {
            while (f.tempo >= f.info.duracao) f.tempo -= f.info.duracao
        } else if (f.tempo > f.info.duracao) {
            f.tempo = f.info.duracao
        }
    }

    private fun getAnimation(nome: String) = target.getAnimation(nome)

    private fun suave(x: Float): Float {   // smootherstep: 6x^5 - 15x^4 + 10x^3 (comeca e termina devagar)
        val t = x.coerceIn(0f, 1f)
        return t * t * t * (t * (t * 6f - 15f) + 10f)
    }
}

/** Exemplo de ligacao com o jogo (copie o que precisar). */
object ExemploAnimacaoHumana {

    /** 1) Carregar o modelo e criar o animador. */
    fun criar(): Pair<ModelInstance, AnimadorHumano>? {
        val modelo: Model = CarregadorModelos.carregar("modelos/humano.g3dj") ?: return null
        val instancia = ModelInstance(modelo)
        val meta = MetaHumano("modelos/humano_meta.json")
        val animador = AnimadorHumano(instancia, meta)
        animador.tocar("parado", 0f)
        return Pair(instancia, animador)
    }

    /** 2) A cada quadro: escolher a animacao pelo estado do jogador. */
    fun escolher(a: AnimadorHumano, velocidade: Float, agachado: Boolean, noAr: Boolean, atacando: Boolean, machucado: Boolean, morto: Boolean) {
        when {
            morto -> a.tocar("morrer", 0.2f)
            machucado -> a.tocar("dano", 0.10f, reiniciar = true)
            atacando -> a.tocar("atacar", 0.10f)
            noAr -> a.tocar("pular", 0.10f)
            agachado -> a.tocar("agachar", 0.20f)
            velocidade > 3.0f -> { a.tocar("correr", 0.18f); a.acompanharVelocidade(velocidade) }
            velocidade > 0.2f -> { a.tocar("andar", 0.20f); a.acompanharVelocidade(velocidade) }
            else -> a.tocar("parado", 0.25f)
        }
    }

    /** 3) Versao simples com o AnimationController padrao do LibGDX (mistura em linha reta). */
    fun simples(instancia: ModelInstance): AnimationController {
        val c = AnimationController(instancia)
        c.allowSameAnimation = false
        c.animate("parado", -1, 1f, null, 0.2f)      // loop infinito
        // depois: c.animate("andar", -1, escala, null, 0.2f)   e   c.update(dt) todo quadro
        return c
    }
}
