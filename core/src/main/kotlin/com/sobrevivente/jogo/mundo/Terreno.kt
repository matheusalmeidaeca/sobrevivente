// core/src/main/kotlin/com/sobrevivente/jogo/mundo/Terreno.kt
package com.sobrevivente.jogo.mundo

import com.sobrevivente.jogo.Constantes

/**
 * Altura do chao sob um ponto (F5): chao (0), rampas/escadas e andares altos (Constantes.RAMPAS / ALTOS).
 * Sem alocacao. `yAtual` evita "grudar" no andar de cima quando se anda por baixo dele:
 * so conta a superficie que esta no maximo DEGRAU_MAXIMO acima dos pes.
 */
object Terreno {
    private const val ORIGEM = -160f
    private const val N = 321
    private var raster: FloatArray? = null

    /** Relevo ligado (so na cidade). */
    val ativo: Boolean get() = raster != null

    /** Rios: cada um e uma lista de pontos (x, z, x, z, ...) no mundo. */
    var rios: List<FloatArray> = emptyList()
        private set
    const val NIVEL_AGUA = -0.3f

    fun limpar() { raster = null; rios = emptyList() }

    /** Relevo base: colinas suaves (inclinacao maxima ~0,26). */
    private fun base(x: Float, z: Float): Float {
        val h = 2.5f + 1.6f * kotlin.math.sin(x / 17f + z / 41f) + 1.4f * kotlin.math.cos(z / 13f - x / 53f) +
            1.1f * kotlin.math.sin((x + z) / 29f)
        return if (h < 0f) 0f else h
    }

    /** Monta o mapa de alturas (1 m). Chamar ANTES de criar paredes e decoracao (elas se ajustam ao relevo). */
    fun construir(riosMundo: List<FloatArray> = emptyList()) {
        rios = riosMundo
        val r = FloatArray(N * N)
        for (j in 0 until N) for (i in 0 until N) {
            val x = ORIGEM + i; val z = ORIGEM + j
            var h = base(x, z)
            for (rio in riosMundo) {
                val d = distancia(rio, x, z)
                if (d >= 13f) continue
                val t = if (d <= 5f) 0f else (d - 5f) / 8f
                val w = 1f - t * t * (3f - 2f * t)
                h += (-1.0f - h) * w // leito do rio
            }
            r[j * N + i] = h
        }
        raster = r
    }

    private fun distancia(rio: FloatArray, x: Float, z: Float): Float {
        var m = 1e9f
        for (k in 0 until rio.size / 2 - 1) {
            val ax = rio[k * 2]; val az = rio[k * 2 + 1]; val bx = rio[k * 2 + 2]; val bz = rio[k * 2 + 3]
            val dx = bx - ax; val dz = bz - az
            val l2 = dx * dx + dz * dz
            val t = if (l2 < 0.0001f) 0f else (((x - ax) * dx + (z - az) * dz) / l2).coerceIn(0f, 1f)
            val ex = ax + dx * t - x; val ez = az + dz * t - z
            val q = ex * ex + ez * ez
            if (q < m) m = q
        }
        return kotlin.math.sqrt(m)
    }

    /** Menor altura do chao dentro do retangulo. */
    fun minEm(x0: Float, z0: Float, x1: Float, z1: Float): Float {
        if (raster == null) return 0f
        var m = 1e9f
        val nx = ((x1 - x0) / 2f).toInt() + 1; val nz = ((z1 - z0) / 2f).toInt() + 1
        for (a in 0..nx) for (b in 0..nz) {
            val h = alturaChao(x0 + (x1 - x0) * a / nx, z0 + (z1 - z0) * b / nz)
            if (h < m) m = h
        }
        return m
    }

    /** Altura do chao natural em (x, z). 0 se nao ha relevo. */
    fun alturaChao(x: Float, z: Float): Float {
        val r = raster ?: return 0f
        val fx = (x - ORIGEM).coerceIn(0f, N - 1.001f); val fz = (z - ORIGEM).coerceIn(0f, N - 1.001f)
        val i = fx.toInt(); val j = fz.toInt(); val tx = fx - i; val tz = fz - j
        val a = r[j * N + i] + (r[j * N + i + 1] - r[j * N + i]) * tx
        val b = r[(j + 1) * N + i] + (r[(j + 1) * N + i + 1] - r[(j + 1) * N + i]) * tx
        return a + (b - a) * tz
    }

    /** Maior altura do chao dentro do retangulo (amostras a cada ~2 m). */
    fun maxEm(x0: Float, z0: Float, x1: Float, z1: Float): Float {
        if (raster == null) return 0f
        var m = 0f
        val nx = ((x1 - x0) / 2f).toInt() + 1; val nz = ((z1 - z0) / 2f).toInt() + 1
        for (a in 0..nx) for (b in 0..nz) {
            val h = alturaChao(x0 + (x1 - x0) * a / nx, z0 + (z1 - z0) * b / nz)
            if (h > m) m = h
        }
        return m
    }

    fun alturaSuporte(x: Float, z: Float, yAtual: Float): Float {
        var melhor = alturaChao(x, z)
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
