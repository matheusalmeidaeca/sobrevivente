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
import com.badlogic.gdx.utils.Disposable
import com.sobrevivente.jogo.Constantes

/** Cena: luz e chão. Sem desenho de HUD aqui. */
class Mundo : Disposable {

    val ambiente = Environment()
    val chao: ModelInstance
    private val modeloChao: Model

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
    }

    private fun criarMaterial(r: Float, g: Float, b: Float): Material {
        // Sem descarte de faces: evita chão invisível por ordem de vértices.
        return Material(
            ColorAttribute.createDiffuse(r, g, b, 1f),
            IntAttribute.createCullFace(GL20.GL_NONE)
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
    }
}
