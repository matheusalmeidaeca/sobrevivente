// core/src/main/kotlin/com/sobrevivente/jogo/mundo/Alvo.kt
package com.sobrevivente.jogo.mundo

import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.sobrevivente.jogo.Constantes

/**
 * Alvo fixo de teste (cubo no chão). Marco 1.4: só reage ao acerto piscando.
 * Não tem vida nem morre; some quando o Inimigo (Marco 1.5) assumir o papel de alvo.
 */
class Alvo(modelo: Model, x: Float, z: Float) {

    val instancia = ModelInstance(modelo)
    val caixa = BoundingBox()

    private val cor: ColorAttribute
    private var tempoFlash = 0f

    init {
        val metade = Constantes.ALVO_LADO * 0.5f
        instancia.transform.setToTranslation(x, metade, z)
        caixa.set(
            Vector3(x - metade, 0f, z - metade),
            Vector3(x + metade, Constantes.ALVO_LADO, z + metade)
        )
        // Cada ModelInstance tem cópia própria do material: mudar a cor não afeta os outros cubos.
        cor = instancia.materials.get(0).get(ColorAttribute.Diffuse) as ColorAttribute
    }

    fun acertar() {
        tempoFlash = Constantes.ALVO_FLASH_S
        cor.color.set(
            Constantes.ALVO_FLASH_COR_R,
            Constantes.ALVO_FLASH_COR_G,
            Constantes.ALVO_FLASH_COR_B,
            1f
        )
    }

    fun atualizar(dt: Float) {
        if (tempoFlash <= 0f) return
        tempoFlash -= dt
        if (tempoFlash <= 0f) {
            cor.color.set(
                Constantes.ALVO_COR_R,
                Constantes.ALVO_COR_G,
                Constantes.ALVO_COR_B,
                1f
            )
        }
    }
}
