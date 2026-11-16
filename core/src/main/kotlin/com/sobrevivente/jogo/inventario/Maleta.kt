// core/src/main/kotlin/com/sobrevivente/jogo/inventario/Maleta.kt
package com.sobrevivente.jogo.inventario

import com.sobrevivente.jogo.Constantes

/**
 * Inventário em grade (seção 14): 2 x 4 = 8 slots.
 * Munição empilha até 36 por slot; cura é 1 por slot.
 * Só lógica: não conhece desenho (quem exibe é hud/Hud.kt).
 */
class Maleta {

    val slots: Array<Item?> = arrayOfNulls(Constantes.MALETA_SLOTS)

    private fun limitePorSlot(tipo: TipoItem): Int {
        return when (tipo) {
            TipoItem.MUNICAO -> Constantes.MALETA_MUNICAO_POR_SLOT
            TipoItem.CURA -> Constantes.MALETA_CURA_POR_SLOT
        }
    }

    /** Pilha do slot `indice`, ou null se vazio (ou índice inválido). */
    fun item(indice: Int): Item? {
        if (indice < 0 || indice >= slots.size) return null
        return slots[indice]
    }

    /** True se a quantidade INTEIRA cabe (completando pilhas e/ou usando slots vazios). */
    fun cabe(tipo: TipoItem, quantidade: Int): Boolean {
        if (quantidade <= 0) return false
        val limite = limitePorSlot(tipo)
        var falta = quantidade
        var vazios = 0
        for (i in slots.indices) {
            val atual = slots[i]
            if (atual == null) {
                vazios++
            } else if (atual.tipo == tipo) {
                falta -= limite - atual.quantidade
            }
        }
        if (falta <= 0) return true
        val precisa = (falta + limite - 1) / limite
        return vazios >= precisa
    }

    /** Guarda o item. Só guarda se couber inteiro; senão não muda nada e devolve false. */
    fun adicionar(tipo: TipoItem, quantidade: Int): Boolean {
        if (!cabe(tipo, quantidade)) return false
        val limite = limitePorSlot(tipo)
        var resta = quantidade

        // 1º: completa pilhas do mesmo tipo
        for (i in slots.indices) {
            val atual = slots[i] ?: continue
            if (atual.tipo != tipo) continue
            val mover = minOf(limite - atual.quantidade, resta)
            atual.quantidade += mover
            resta -= mover
            if (resta <= 0) return true
        }
        // 2º: abre pilhas novas nos slots vazios
        for (i in slots.indices) {
            if (slots[i] != null) continue
            val mover = minOf(limite, resta)
            slots[i] = Item(tipo, mover)
            resta -= mover
            if (resta <= 0) return true
        }
        return resta <= 0
    }

    /** Tira `quantidade` do slot; se a pilha acabar, o slot fica vazio. */
    fun remover(indice: Int, quantidade: Int) {
        val atual = item(indice) ?: return
        atual.quantidade -= quantidade
        if (atual.quantidade <= 0) slots[indice] = null
    }

    /** Esvazia todos os slots (reinício depois da morte). */
    fun limpar() {
        for (i in slots.indices) slots[i] = null
    }
}
