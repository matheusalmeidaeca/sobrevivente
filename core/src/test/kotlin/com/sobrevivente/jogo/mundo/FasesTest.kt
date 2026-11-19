package com.sobrevivente.jogo.mundo

import com.badlogic.gdx.math.Vector3
import com.sobrevivente.jogo.Constantes
import org.junit.Assert.assertTrue
import org.junit.Test

/** Confere cada fase: pontos importantes fora de paredes e alcancaveis a pe a partir do inicio (mesma grade dos zumbis). */
class FasesTest {

    private fun dentroDeParede(x: Float, z: Float): Boolean {
        val p = Vector3(x, 0f, z)
        Colisao.resolver(p, Constantes.JOGADOR_RAIO)
        val dx = p.x - x
        val dz = p.z - z
        return dx * dx + dz * dz > 0.0025f
    }

    private fun alcancaveis(ix: Float, iz: Float): BooleanArray {
        val c = Constantes.NAV_CELULA
        val lado = (Constantes.CHAO_TAMANHO / c).toInt()
        val metade = Constantes.CHAO_TAMANHO / 2f
        val vis = BooleanArray(lado * lado)
        fun cel(v: Float) = kotlin.math.floor((v + metade) / c).toInt().coerceIn(0, lado - 1)
        val fila = ArrayDeque<Int>()
        val inicio = cel(iz) * lado + cel(ix)
        vis[inicio] = true
        fila.add(inicio)
        while (fila.isNotEmpty()) {
            val a = fila.removeFirst()
            val gx = a % lado
            val gz = a / lado
            for (dz in -1..1) for (dx in -1..1) {
                if (dx == 0 && dz == 0) continue
                val nx = gx + dx
                val nz = gz + dz
                if (nx < 0 || nz < 0 || nx >= lado || nz >= lado) continue
                val cxm = -metade + (nx + 0.5f) * c
                val czm = -metade + (nz + 0.5f) * c
                if (!Navegacao.livre(cxm, czm)) continue
                val k = nz * lado + nx
                if (vis[k]) continue
                vis[k] = true
                fila.add(k)
            }
        }
        return vis
    }

    @Test
    fun candidatos() {
        Fases.carregar(0)
        val vis = alcancaveis(0f, 0f)
        val c = Constantes.NAV_CELULA
        val lado = (Constantes.CHAO_TAMANHO / c).toInt()
        val metade = Constantes.CHAO_TAMANHO / 2f
        for (p in listOf(6f to 2f, 8f to 0f, -7f to 4f, 0f to 8f, 5f to 8f, 10f to 5f, 8f to -4f, -8f to 0f)) {
            val gx = kotlin.math.floor((p.first + metade) / c).toInt().coerceIn(0, lado - 1)
            val gz = kotlin.math.floor((p.second + metade) / c).toInt().coerceIn(0, lado - 1)
            println("CAND " + p + " alcanca=" + vis[gz * lado + gx] + " parede=" + dentroDeParede(p.first, p.second))
        }
    }

    @Test
    fun todasAsFases() {
        val problemas = ArrayList<String>()
        for (i in 0 until Fases.total) {
            Fases.carregar(i)
            val d = Fases.atual
            // cidade: portoes fechados dividem o mapa; abre todos para checar o alcance (a ordem das chaves e logica da missao)
            if (d.cidade) for (g in Fases.portoes) for (q in 0..3) Constantes.PAREDES[g * 4 + q] = 5000f
            if (d.cidade) Navegacao.reconstruir()
            val vis = alcancaveis(d.inicio[0], d.inicio[1])
            val c = Constantes.NAV_CELULA
            val lado = (Constantes.CHAO_TAMANHO / c).toInt()
            val metade = Constantes.CHAO_TAMANHO / 2f
            fun ok(nome: String, x: Float, z: Float, dentro: Boolean = true) {
                if (dentroDeParede(x, z)) problemas.add(d.nome + ": " + nome + " (" + x + ", " + z + ") dentro de parede")
                val gx = kotlin.math.floor((x + metade) / c).toInt().coerceIn(0, lado - 1)
                val gz = kotlin.math.floor((z + metade) / c).toInt().coerceIn(0, lado - 1)
                if (dentro && !vis[gz * lado + gx]) problemas.add(d.nome + ": " + nome + " (" + x + ", " + z + ") inalcancavel")
            }
            ok("inicio", d.inicio[0], d.inicio[1])
            ok("mercador", d.mercador[0], d.mercador[1])
            if (!d.cidade) for (k in 0..2) ok("fragmento" + k, d.fragmentos[k * 2], d.fragmentos[k * 2 + 1])
            for (k in 0..2) ok("alavanca" + k, d.alavancas[k * 2], d.alavancas[k * 2 + 1])
            ok("altar", d.altar[0], d.altar[1])
            for (k in 0 until d.baus.size / 2) ok("bau" + k, d.baus[k * 2], d.baus[k * 2 + 1])
            ok("mini", d.miniSpawn[0], d.miniSpawn[1])
            ok("chefe", d.chefeSpawn[0], d.chefeSpawn[1])
            for (k in 0 until Constantes.INIMIGOS_XZ.size / 2) ok("zumbi" + k, Constantes.INIMIGOS_XZ[k * 2], Constantes.INIMIGOS_XZ[k * 2 + 1])
            for (k in 0 until Constantes.SPAWNS_XZ.size / 2) ok("spawn" + k, Constantes.SPAWNS_XZ[k * 2], Constantes.SPAWNS_XZ[k * 2 + 1])
            for (k in 0 until Constantes.ITENS_XZ.size / 2) if (Constantes.ITENS_Y[k] < 0.5f) ok("item" + k, Constantes.ITENS_XZ[k * 2], Constantes.ITENS_XZ[k * 2 + 1])
            // arrays do mapa coerentes
            assertTrue(d.nome + " tamanhos", Constantes.PAREDES.size / 4 == Constantes.PAREDES_TIPO.size)
            assertTrue(d.nome + " base", Constantes.PAREDES_BASE.size == Constantes.PAREDES_TIPO.size)
            assertTrue(d.nome + " altura", Constantes.PAREDES_ALTURA.size == Constantes.PAREDES_TIPO.size)
            assertTrue(d.nome + " itens", Constantes.ITENS_XZ.size / 2 == Constantes.ITENS_TIPO.size && Constantes.ITENS_Y.size == Constantes.ITENS_TIPO.size)
        }
        Fases.carregar(0)
        for (p in problemas) println("PROBLEMA " + p)
        assertTrue("problemas: " + problemas.size, problemas.isEmpty())
    }
}
