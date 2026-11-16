// core/src/main/kotlin/com/sobrevivente/jogo/inventario/Item.kt
package com.sobrevivente.jogo.inventario

/** Tipos de item da Fase 1 (seção 14 do mapa). */
enum class TipoItem { MUNICAO, CURA }

/** Uma pilha de itens dentro da maleta: tipo + quantidade. Só dados; sem desenho. */
class Item(val tipo: TipoItem, var quantidade: Int)
