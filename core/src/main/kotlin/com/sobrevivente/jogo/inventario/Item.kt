// core/src/main/kotlin/com/sobrevivente/jogo/inventario/Item.kt
package com.sobrevivente.jogo.inventario

import com.sobrevivente.jogo.mundo.TipoArma

/** Tipos de item da maleta: municao e cura empilham; ARMA e FACA ocupam varias celulas (estilo Resident Evil). */
enum class TipoItem { MUNICAO, CURA, ARMA, FACA }

/** Uma peca dentro da maleta: tipo + quantidade + posicao/tamanho na grade (col, lin, larg, alt em celulas). */
class Item(val tipo: TipoItem, var quantidade: Int, val arma: TipoArma? = null) {
    var col = 0
    var lin = 0
    var larg = 1
    var alt = 1
}
