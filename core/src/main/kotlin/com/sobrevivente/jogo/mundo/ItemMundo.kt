// core/src/main/kotlin/com/sobrevivente/jogo/mundo/ItemMundo.kt
package com.sobrevivente.jogo.mundo

import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3
import com.sobrevivente.jogo.Constantes
import com.sobrevivente.jogo.inventario.TipoItem

/**
 * Item largado no chao (cubo colorido de teste) ou ARMA para pegar (modelo da arma deitado no chao).
 * O modelo e compartilhado e pertence ao Mundo, por isso esta classe nao tem dispose().
 * (x, z) e a posicao no chao e y a altura do piso (3,2 = andar alto da casa do fazendeiro).
 * `arma` != null: pegar com INTERAGIR libera a arma (nao entra na maleta).
 */
class ItemMundo(
    modelo: Model,
    val tipo: TipoItem,
    val quantidade: Int,
    val x: Float,
    val z: Float,
    val y: Float = 0f,
    val arma: TipoArma? = null
) {
    val instancia = ModelInstance(modelo)

    /** True depois de guardado na maleta / pego (some da cena ate o reinicio). */
    var coletado = false

    init {
        if (arma != null) {
            // arma deitada no chao, um pouco levantada e girada
            instancia.transform.setToTranslation(x, y + 0.12f, z).rotate(Vector3.Y, 35f)
        } else {
            instancia.transform.setToTranslation(x, y + Constantes.ITEM_LADO * 0.5f + Constantes.ITEM_FOLGA_CHAO, z)
        }
    }
}
