// core/src/main/kotlin/com/sobrevivente/jogo/inventario/Maleta.kt
package com.sobrevivente.jogo.inventario

import com.sobrevivente.jogo.Constantes
import com.sobrevivente.jogo.mundo.TipoArma

/**
 * Maleta em grade, estilo Resident Evil (8 x 5 celulas). Armas e faca ocupam varias celulas
 * (pistola 2x2, fuzil/escopeta 4x2, faca 1x2); municao empilha ate 36 por celula; cura e 1 por celula.
 * Comeca com a pistola e a faca. Só lógica: quem desenha é hud/Hud.kt.
 */
class Maleta {

    val itens = ArrayList<Item>()
    private val celulas = arrayOfNulls<Item>(Constantes.MALETA_SLOTS)

    init {
        iniciarPadrao()
    }

    private fun limitePorCelula(tipo: TipoItem): Int = when (tipo) {
        TipoItem.MUNICAO -> Constantes.MALETA_MUNICAO_POR_SLOT
        else -> Constantes.MALETA_CURA_POR_SLOT
    }

    /** Item que cobre a celula `celula` (linha * colunas + coluna), ou null. */
    fun itemNaCelula(celula: Int): Item? {
        if (celula < 0 || celula >= celulas.size) return null
        return celulas[celula]
    }

    private fun livre(col: Int, lin: Int, larg: Int, alt: Int): Boolean {
        if (col + larg > Constantes.MALETA_COLUNAS || lin + alt > Constantes.MALETA_LINHAS) return false
        for (l in lin until lin + alt) {
            for (c in col until col + larg) {
                if (celulas[l * Constantes.MALETA_COLUNAS + c] != null) return false
            }
        }
        return true
    }

    /** Primeiro lugar (varrendo linha por linha) onde cabe larg x alt, como col + lin * 100; -1 se nao cabe. */
    private fun acharLugar(larg: Int, alt: Int): Int {
        for (l in 0 until Constantes.MALETA_LINHAS) {
            for (c in 0 until Constantes.MALETA_COLUNAS) {
                if (livre(c, l, larg, alt)) return c + l * 100
            }
        }
        return -1
    }

    private fun colocar(item: Item, col: Int, lin: Int, larg: Int, alt: Int) {
        item.col = col
        item.lin = lin
        item.larg = larg
        item.alt = alt
        for (l in lin until lin + alt) {
            for (c in col until col + larg) celulas[l * Constantes.MALETA_COLUNAS + c] = item
        }
        itens.add(item)
    }

    private fun celulasLivres(): Int {
        var n = 0
        for (i in celulas.indices) if (celulas[i] == null) n++
        return n
    }

    /** True se a quantidade INTEIRA de municao/cura cabe (completando pilhas e/ou usando celulas vazias). */
    fun cabe(tipo: TipoItem, quantidade: Int): Boolean {
        if (quantidade <= 0) return false
        if (tipo == TipoItem.ARMA || tipo == TipoItem.FACA) return false
        val limite = limitePorCelula(tipo)
        var falta = quantidade
        for (i in itens.indices) {
            val atual = itens[i]
            if (atual.tipo == tipo) falta -= limite - atual.quantidade
        }
        if (falta <= 0) return true
        val precisa = (falta + limite - 1) / limite
        return celulasLivres() >= precisa
    }

    /** Guarda municao/cura. So guarda se couber inteiro; senao nao muda nada e devolve false. */
    fun adicionar(tipo: TipoItem, quantidade: Int): Boolean {
        if (!cabe(tipo, quantidade)) return false
        val limite = limitePorCelula(tipo)
        var resta = quantidade
        for (i in itens.indices) {
            val atual = itens[i]
            if (atual.tipo != tipo) continue
            val mover = minOf(limite - atual.quantidade, resta)
            atual.quantidade += mover
            resta -= mover
            if (resta <= 0) return true
        }
        while (resta > 0) {
            val lugar = acharLugar(1, 1)
            if (lugar < 0) return false
            val mover = minOf(limite, resta)
            colocar(Item(tipo, mover), lugar % 100, lugar / 100, 1, 1)
            resta -= mover
        }
        return true
    }

    fun temArma(t: TipoArma): Boolean {
        for (i in itens.indices) if (itens[i].tipo == TipoItem.ARMA && itens[i].arma == t) return true
        return false
    }

    /** Guarda a arma (pistola 2x2, fuzil/escopeta 4x2). True se ja tinha ou se coube; false se nao ha espaco. */
    fun adicionarArma(t: TipoArma): Boolean {
        if (temArma(t)) return true
        val larg = if (t == TipoArma.PISTOLA) Constantes.MALETA_ARMA_PISTOLA_L else Constantes.MALETA_ARMA_LONGA_L
        val alt = if (t == TipoArma.PISTOLA) Constantes.MALETA_ARMA_PISTOLA_A else Constantes.MALETA_ARMA_LONGA_A
        val lugar = acharLugar(larg, alt)
        if (lugar < 0) return false
        colocar(Item(TipoItem.ARMA, 1, t), lugar % 100, lugar / 100, larg, alt)
        return true
    }

    private fun adicionarFaca() {
        val lugar = acharLugar(Constantes.MALETA_FACA_L, Constantes.MALETA_FACA_A)
        if (lugar >= 0) colocar(Item(TipoItem.FACA, 1), lugar % 100, lugar / 100, Constantes.MALETA_FACA_L, Constantes.MALETA_FACA_A)
    }

    /** Total de itens de um tipo (soma das quantidades). */
    fun total(tipo: TipoItem): Int {
        var n = 0
        for (i in itens.indices) if (itens[i].tipo == tipo) n += itens[i].quantidade
        return n
    }

    /** Primeiro item do tipo (ou null). */
    fun primeiro(tipo: TipoItem): Item? {
        for (i in itens.indices) if (itens[i].tipo == tipo) return itens[i]
        return null
    }

    /** Tira `quantidade` do item; se a pilha acabar, libera as celulas. */
    fun remover(item: Item, quantidade: Int) {
        item.quantidade -= quantidade
        if (item.quantidade > 0) return
        for (l in item.lin until item.lin + item.alt) {
            for (c in item.col until item.col + item.larg) celulas[l * Constantes.MALETA_COLUNAS + c] = null
        }
        itens.remove(item)
    }

    /** Esvazia e volta ao inicio (pistola + faca). Usado no reinicio depois da morte. */
    fun limpar() {
        itens.clear()
        for (i in celulas.indices) celulas[i] = null
        iniciarPadrao()
    }

    private fun iniciarPadrao() {
        adicionarArma(TipoArma.PISTOLA)
        adicionarFaca()
    }
}
