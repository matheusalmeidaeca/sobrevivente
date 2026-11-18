// core/src/main/kotlin/com/sobrevivente/jogo/audio/Som.kt
package com.sobrevivente.jogo.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.math.MathUtils

/** Faixas de fundo: o jogo troca entre elas conforme o perigo (F18). */
enum class Trilha(val arquivo: String, val volume: Float) {
    AMBIENTE("audio/musica/musica_ambiente.ogg", 0.55f),
    TENSAO("audio/musica/musica_tensao.ogg", 0.60f),
    COMBATE("audio/musica/musica_combate.ogg", 0.62f)
}

/**
 * F18: sons e trilha. Tudo sintetizado por `ferramentas/audio/gerar_audio.py` (sem arquivos de terceiros).
 * Uma instancia por TelaJogo; `dispose()` libera tudo. Se o dispositivo nao tiver audio, nada quebra (try/catch).
 */
class Som {
    private val efeitos = HashMap<String, Sound>()
    private val musicas = HashMap<Trilha, Music>()
    private val volumeAtual = HashMap<Trilha, Float>()
    private var alvo: Trilha = Trilha.AMBIENTE
    private var pausado = false
    var volumeEfeitos = 1f
    var volumeMusica = 1f

    init {
        try {
            for (t in Trilha.values()) {
                val m = Gdx.audio.newMusic(Gdx.files.internal(t.arquivo))
                m.isLooping = true
                m.volume = 0f
                musicas[t] = m
                volumeAtual[t] = 0f
            }
        } catch (e: Exception) {
            Gdx.app.log("SOM", "musica indisponivel: " + e.message)
        }
    }

    private fun obter(nome: String): Sound? {
        efeitos[nome]?.let { return it }
        return try {
            val s = Gdx.audio.newSound(Gdx.files.internal("audio/sfx/$nome.ogg"))
            efeitos[nome] = s
            s
        } catch (e: Exception) {
            Gdx.app.log("SOM", "efeito indisponivel: $nome")
            null
        }
    }

    /** Toca um efeito. `pan` -1 (esquerda) .. 1 (direita); `variacao` = quanto o tom varia (0.05 = 5%). */
    fun tocar(nome: String, volume: Float = 1f, pan: Float = 0f, variacao: Float = 0.04f) {
        if (pausado || volume <= 0.01f) return
        val s = obter(nome) ?: return
        val tom = 1f + MathUtils.random(-variacao, variacao)
        s.play(MathUtils.clamp(volume * volumeEfeitos, 0f, 1f), tom, MathUtils.clamp(pan, -1f, 1f))
    }

    /** Toca `prefixo` + numero aleatorio 0 until n (ex.: passo0..passo2). */
    fun tocarVariante(prefixo: String, n: Int, volume: Float = 1f, pan: Float = 0f, variacao: Float = 0.04f) {
        tocar(prefixo + MathUtils.random(0, n - 1), volume, pan, variacao)
    }

    fun definirTrilha(t: Trilha) {
        alvo = t
    }

    val trilhaAtual: Trilha get() = alvo

    /** Chamar todo frame: faz a transicao suave (cross-fade) entre as faixas. */
    fun atualizar(dt: Float) {
        for ((t, m) in musicas) {
            if (pausado) continue
            val v0 = volumeAtual[t] ?: 0f
            val meta = if (t == alvo) t.volume * volumeMusica else 0f
            val taxa = if (meta > v0) 0.5f else 0.8f // entra em ~2 s, sai em ~1,3 s
            val v = if (meta > v0) minOf(meta, v0 + taxa * dt) else maxOf(meta, v0 - taxa * dt)
            volumeAtual[t] = v
            if (v > 0.005f) {
                m.volume = v
                if (!m.isPlaying) m.play()
            } else if (m.isPlaying) {
                m.pause()
            }
        }
    }

    fun pausar() {
        pausado = true
        for (m in musicas.values) if (m.isPlaying) m.pause()
    }

    fun retomar() {
        pausado = false
    }

    fun dispose() {
        for (s in efeitos.values) s.dispose()
        for (m in musicas.values) m.dispose()
        efeitos.clear()
        musicas.clear()
    }
}
