// core/src/main/kotlin/com/sobrevivente/jogo/mundo/ModeloAnimado.kt
package com.sobrevivente.jogo.mundo

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader
import com.badlogic.gdx.graphics.g3d.utils.AnimationController
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.UBJsonReader

/** Carrega modelos .g3dj (Marco F3). Se o arquivo faltar ou falhar, devolve null e o jogo usa a capsula. */
object CarregadorModelos {

    fun carregar(caminho: String): Model? {
        // Preferimos o binario (.g3db, ferramentas/g3dj_para_g3db.py): carrega bem mais rapido que o JSON.
        val bin = Gdx.files.internal(caminho.removeSuffix(".g3dj") + ".g3db")
        val arquivo = if (caminho.endsWith(".g3dj") && bin.exists()) bin else Gdx.files.internal(caminho)
        if (!arquivo.exists()) {
            Gdx.app.log("MODELO", "nao encontrado (usando capsula): " + caminho)
            return null
        }
        return try {
            val modelo = G3dModelLoader(if (arquivo.extension() == "g3db") UBJsonReader() else JsonReader()).loadModel(arquivo)
            // Sem descarte de faces: garante que nenhuma face apareca invisivel por ordem de vertices.
            for (i in 0 until modelo.materials.size) {
                modelo.materials.get(i).set(IntAttribute.createCullFace(GL20.GL_NONE))
            }
            Gdx.app.log("MODELO", "carregado: " + caminho + " (" + modelo.animations.size + " animacoes)")
            modelo
        } catch (e: Exception) {
            Gdx.app.error("MODELO", "falha ao carregar " + caminho + ": " + e)
            null
        }
    }
}

/** Toca animacoes de uma ModelInstance. `tocarSe` so troca quando o nome muda; `tocar` sempre reinicia. */
class Animador(private val instancia: ModelInstance) {

    private val controle = AnimationController(instancia)
    private var atual = ""

    init {
        controle.allowSameAnimation = true
    }

    /** Reinicia a animacao `id`. `vezes` = -1 para loop infinito, 1 para tocar uma vez (fica na ultima pose). */
    fun tocar(id: String, vezes: Int, velocidade: Float, transicao: Float) {
        if (instancia.getAnimation(id) == null) return
        atual = id
        controle.animate(id, vezes, velocidade, null, transicao)
    }

    fun tocarSe(id: String, vezes: Int, velocidade: Float, transicao: Float) {
        if (id == atual) return
        tocar(id, vezes, velocidade, transicao)
    }

    /** Ajusta a velocidade de reproducao da animacao em andamento (acompanhar a velocidade real). */
    fun definirVelocidade(velocidade: Float) {
        controle.current?.speed = velocidade
    }

    fun atualizar(dt: Float) {
        controle.update(dt)
    }
}
