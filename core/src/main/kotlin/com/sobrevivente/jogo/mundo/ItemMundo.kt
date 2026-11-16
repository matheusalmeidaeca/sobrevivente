// core/src/main/kotlin/com/sobrevivente/jogo/mundo/ItemMundo.kt
package com.sobrevivente.jogo.mundo

import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.sobrevivente.jogo.Constantes
import com.sobrevivente.jogo.inventario.TipoItem

/**
 * Item largado no chão (cubo colorido de teste). O modelo é compartilhado e pertence ao Mundo,
 * por isso esta classe não tem dispose(). (x, z) é a posição no chão, em metros.
 */
class ItemMundo(
    modelo: Model,
    val tipo: TipoItem,
    val quantidade: Int,
    val x: Float,
    val z: Float
) {
    val instancia = ModelInstance(modelo)

    /** True depois de guardado na maleta (some da cena até o reinício). */
    var coletado = false

    init {
        instancia.transform.setToTranslation(
            x,
            Constantes.ITEM_LADO * 0.5f + Constantes.ITEM_FOLGA_CHAO,
            z
        )
    }
}
