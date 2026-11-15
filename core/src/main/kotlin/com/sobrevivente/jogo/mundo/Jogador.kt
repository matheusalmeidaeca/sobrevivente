// core/src/main/kotlin/com/sobrevivente/jogo/mundo/Jogador.kt
package com.sobrevivente.jogo.mundo

import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import com.sobrevivente.jogo.Constantes

/**
 * Jogador placeholder: cápsula + "nariz" amarelo que mostra a frente.
 * Convenção (seção 8.1 do mapa): Y para cima; yaw 0 olha para -Z;
 * yaw positivo = anti-horário visto de cima. `posicao` é a posição dos PÉS.
 */
class Jogador : Disposable {

    val posicao = Vector3(0f, 0f, 0f)
    var yaw = 0f

    private val modeloCorpo: Model
    private val modeloNariz: Model
    val corpo: ModelInstance
    val nariz: ModelInstance

    init {
        val construtor = ModelBuilder()
        val atributos = (Usage.Position or Usage.Normal).toLong()

        modeloCorpo = construtor.createCapsule(
            Constantes.JOGADOR_RAIO,
            Constantes.JOGADOR_ALTURA,
            Constantes.JOGADOR_DIVISOES,
            Material(
                ColorAttribute.createDiffuse(
                    Constantes.JOGADOR_COR_R,
                    Constantes.JOGADOR_COR_G,
                    Constantes.JOGADOR_COR_B,
                    1f
                )
            ),
            atributos
        )
        modeloNariz = construtor.createBox(
            Constantes.NARIZ_LARGURA,
            Constantes.NARIZ_ALTURA,
            Constantes.NARIZ_COMPRIMENTO,
            Material(
                ColorAttribute.createDiffuse(
                    Constantes.NARIZ_COR_R,
                    Constantes.NARIZ_COR_G,
                    Constantes.NARIZ_COR_B,
                    1f
                )
            ),
            atributos
        )

        corpo = ModelInstance(modeloCorpo)
        nariz = ModelInstance(modeloNariz)
        atualizarTransformacao()
    }

    /** Reposiciona os modelos conforme `posicao` e `yaw`. Sem alocação. */
    fun atualizarTransformacao() {
        corpo.transform.setToTranslation(
            posicao.x,
            posicao.y + Constantes.JOGADOR_ALTURA / 2f,
            posicao.z
        )
        nariz.transform
            .setToTranslation(posicao.x, posicao.y + Constantes.NARIZ_ALTURA_DO_CHAO, posicao.z)
            .rotate(Vector3.Y, yaw)
            .translate(0f, 0f, -Constantes.NARIZ_DESLOCAMENTO_FRENTE)
    }

    override fun dispose() {
        modeloCorpo.dispose()
        modeloNariz.dispose()
    }
}
