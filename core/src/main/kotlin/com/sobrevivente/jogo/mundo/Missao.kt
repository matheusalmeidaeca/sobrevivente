// core/src/main/kotlin/com/sobrevivente/jogo/mundo/Missao.kt
package com.sobrevivente.jogo.mundo

import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.utils.Disposable

/** O que a tela e o HUD precisam de qualquer modo historia (a antiga `Historia` por fases e a `Cidade`). */
interface Missao : Disposable {
    val objetivoTexto: String
    val dicaTexto: String
    var aoMensagem: ((String) -> Unit)?
    var aoAbrirLoja: (() -> Unit)?
    var aoConcluir: (() -> Unit)?
    fun chefeAtual(): Inimigo?
    fun nomeChefeAtual(): String
    fun atualizar(dt: Float)
    fun interagir(x: Float, z: Float): Boolean
    fun dicaProximo(x: Float, z: Float): String?
    fun desenhar(lote: ModelBatch, ambiente: Environment)
}
