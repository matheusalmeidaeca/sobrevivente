// core/src/main/kotlin/com/sobrevivente/jogo/mundo/Terreno.kt
package com.sobrevivente.jogo.mundo

import com.sobrevivente.jogo.Constantes

/**
 * Altura do chao sob um ponto (F5): chao (0), rampas/escadas e andares altos (Constantes.RAMPAS / ALTOS).
 * Sem alocacao. `yAtual` evita "grudar" no andar de cima quando se anda por baixo dele:
 * so conta a superficie que esta no maximo DEGRAU_MAXIMO acima dos pes.
 */
object Terreno {

    fun alturaSuporte(x: Float, z: Float, yAtual: Float): Float {
        var melhor = 0f
        val limite = yAtual + Constantes.DEGRAU_MAXIMO

        val rampas = Constantes.RAMPAS
        for (i in 0 until rampas.size / 4) {
            val minX = rampas[i * 4]
            val minZ = rampas[i * 4 + 1]
            val maxX = rampas[i * 4 + 2]
            val maxZ = rampas[i * 4 + 3]
            if (x < minX || x > maxX || z < minZ || z > maxZ) continue
            val h0 = Constantes.RAMPAS_ALTURAS[i * 2]
            val h1 = Constantes.RAMPAS_ALTURAS[i * 2 + 1]
            val t = when (Constantes.RAMPAS_EIXO[i]) {
                0 -> (maxZ - z) / (maxZ - minZ) // sobe para -Z
                1 -> (z - minZ) / (maxZ - minZ)
                2 -> (x - minX) / (maxX - minX)
                else -> (maxX - x) / (maxX - minX)
            }
            val h = h0 + (h1 - h0) * t
            if (h <= limite && h > melhor) melhor = h
        }

        val altos = Constantes.ALTOS
        for (i in 0 until altos.size / 4) {
            if (x < altos[i * 4] || x > altos[i * 4 + 2] || z < altos[i * 4 + 1] || z > altos[i * 4 + 3]) continue
            val h = Constantes.ALTOS_ALTURA[i]
            if (h <= limite && h > melhor) melhor = h
        }
        return melhor
    }
}
