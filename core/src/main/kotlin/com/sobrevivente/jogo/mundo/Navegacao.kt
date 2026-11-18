// core/src/main/kotlin/com/sobrevivente/jogo/mundo/Navegacao.kt
package com.sobrevivente.jogo.mundo

import com.sobrevivente.jogo.Constantes
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Navegacao dos inimigos: grade de celulas no plano XZ + A* (8 vizinhos, sem cortar quina) + "puxar o fio"
 * (suaviza o caminho mantendo so os pontos necessarios). As paredes de Constantes.PAREDES sao "engordadas"
 * pelo raio do inimigo, entao quem segue o caminho nao raspa nas paredes.
 *
 * Tudo e pre-alocado na criacao: buscar caminho NAO aloca memoria (regra de desempenho do jogo).
 * Uso: val n = Navegacao.buscar(x0, z0, x1, z1); pontos em Navegacao.caminhoX/caminhoZ [0 until n]
 * (o ultimo ponto e o destino; o primeiro NAO e a origem).
 */
object Navegacao {
    private const val CELULA = Constantes.NAV_CELULA
    private val metade = Constantes.CHAO_TAMANHO / 2f
    private val lado = (Constantes.CHAO_TAMANHO / CELULA).toInt()
    private const val MAX_PONTOS = 96

    private val bloqueada = BooleanArray(lado * lado)
    private val custo = FloatArray(lado * lado)
    private val pai = IntArray(lado * lado)
    private val visita = IntArray(lado * lado) // carimbo da busca em que a celula foi tocada
    private val fechada = IntArray(lado * lado)
    private var carimbo = 0
    private val heap = IntArray(lado * lado * 2)
    private val heapF = FloatArray(lado * lado * 2)
    private var heapN = 0
    private val bruto = IntArray(lado * lado)

    val caminhoX = FloatArray(MAX_PONTOS)
    val caminhoZ = FloatArray(MAX_PONTOS)

    private val dxViz = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
    private val dzViz = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)

    init {
        reconstruir()
    }

    /** Refaz a grade com as paredes atuais (chamado ao trocar de fase). */
    fun reconstruir() {
        java.util.Arrays.fill(bloqueada, false)
        val folga = Constantes.ENIMIGO_RAIO + Constantes.NAV_MARGEM
        marcar(Constantes.PAREDES, folga, true)
        marcar(Constantes.BLOQUEIOS_INIMIGO, folga, false)
        // borda do mapa
        for (gz in 0 until lado) {
            for (gx in 0 until lado) {
                val cx = centroX(gx)
                val cz = centroX(gz)
                if (cx < -Constantes.MAPA_LIMITE || cx > Constantes.MAPA_LIMITE ||
                    cz < -Constantes.MAPA_LIMITE || cz > Constantes.MAPA_LIMITE
                ) bloqueada[gz * lado + gx] = true
            }
        }
    }

    /** Marca as celulas cobertas por cada caixa engordada. `comAltura`: ignora paredes do andar alto (base > 0,5). */
    private fun marcar(caixas: FloatArray, folga: Float, comAltura: Boolean) {
        for (i in 0 until caixas.size / 4) {
            if (comAltura && Constantes.PAREDES_BASE[i] > 0.5f) continue
            val x0 = caixas[i * 4] - folga
            val z0 = caixas[i * 4 + 1] - folga
            val x1 = caixas[i * 4 + 2] + folga
            val z1 = caixas[i * 4 + 3] + folga
            val gx0 = max(0, floor((x0 + metade) / CELULA).toInt() - 1)
            val gx1 = min(lado - 1, floor((x1 + metade) / CELULA).toInt() + 1)
            val gz0 = max(0, floor((z0 + metade) / CELULA).toInt() - 1)
            val gz1 = min(lado - 1, floor((z1 + metade) / CELULA).toInt() + 1)
            for (gz in gz0..gz1) {
                for (gx in gx0..gx1) {
                    val cx = centroX(gx)
                    val cz = centroX(gz)
                    if (cx > x0 && cx < x1 && cz > z0 && cz < z1) bloqueada[gz * lado + gx] = true
                }
            }
        }
    }

    private fun centroX(g: Int) = -metade + (g + 0.5f) * CELULA
    private fun celula(v: Float): Int = max(0, min(lado - 1, floor((v + metade) / CELULA).toInt()))

    /** O ponto (x, z) esta num lugar onde o inimigo cabe? */
    fun livre(x: Float, z: Float): Boolean = !bloqueada[celula(z) * lado + celula(x)]

    /** Segmento sem parede (considerando a folga do inimigo)? Amostra a cada meia celula. */
    fun linhaLivre(x0: Float, z0: Float, x1: Float, z1: Float): Boolean {
        val dx = x1 - x0
        val dz = z1 - z0
        val d = sqrt(dx * dx + dz * dz)
        val passos = max(1, (d / (CELULA * 0.5f)).toInt())
        for (k in 0..passos) {
            val t = k.toFloat() / passos
            if (!livre(x0 + dx * t, z0 + dz * t)) return false
        }
        return true
    }

    /** Ha parede de verdade (sem folga) entre dois pontos? Serve para o ataque so acontecer com visao livre. */
    fun paredeEntre(x0: Float, z0: Float, x1: Float, z1: Float): Boolean {
        val p = Constantes.PAREDES
        val dx = x1 - x0
        val dz = z1 - z0
        val d = sqrt(dx * dx + dz * dz)
        val passos = max(1, (d / 0.1f).toInt())
        for (k in 0..passos) {
            val t = k.toFloat() / passos
            val x = x0 + dx * t
            val z = z0 + dz * t
            var i = 0
            while (i < p.size) {
                // cercas baixas (1 m) e o guarda-corpo nao bloqueiam o ataque; paredes de verdade sim
                if (Constantes.PAREDES_ALTURA[i / 4] >= 2f && Constantes.PAREDES_BASE[i / 4] < 0.5f &&
                    x > p[i] && x < p[i + 2] && z > p[i + 1] && z < p[i + 3]
                ) return true
                i += 4
            }
        }
        return false
    }

    /** Celula livre mais proxima (espiral de aneis) da celula `c`; devolve -1 se nao achar. */
    private fun celulaLivrePerto(c: Int): Int {
        if (!bloqueada[c]) return c
        val cx = c % lado
        val cz = c / lado
        for (r in 1..8) {
            var melhor = -1
            var melhorD = Int.MAX_VALUE
            for (oz in -r..r) {
                for (ox in -r..r) {
                    if (max(abs(ox), abs(oz)) != r) continue
                    val gx = cx + ox
                    val gz = cz + oz
                    if (gx < 0 || gz < 0 || gx >= lado || gz >= lado) continue
                    val idx = gz * lado + gx
                    if (bloqueada[idx]) continue
                    val d = ox * ox + oz * oz
                    if (d < melhorD) {
                        melhorD = d
                        melhor = idx
                    }
                }
            }
            if (melhor >= 0) return melhor
        }
        return -1
    }

    // ---- heap binario (menor f no topo) ----
    private fun empurrar(idx: Int, f: Float) {
        var i = heapN++
        heap[i] = idx
        heapF[i] = f
        while (i > 0) {
            val pa = (i - 1) / 2
            if (heapF[pa] <= heapF[i]) break
            trocar(pa, i)
            i = pa
        }
    }

    private fun tirar(): Int {
        val topo = heap[0]
        heapN--
        if (heapN > 0) {
            heap[0] = heap[heapN]
            heapF[0] = heapF[heapN]
            var i = 0
            while (true) {
                val e = 2 * i + 1
                val d = e + 1
                var m = i
                if (e < heapN && heapF[e] < heapF[m]) m = e
                if (d < heapN && heapF[d] < heapF[m]) m = d
                if (m == i) break
                trocar(m, i)
                i = m
            }
        }
        return topo
    }

    private fun trocar(a: Int, b: Int) {
        val ti = heap[a]; heap[a] = heap[b]; heap[b] = ti
        val tf = heapF[a]; heapF[a] = heapF[b]; heapF[b] = tf
    }

    private fun heuristica(c: Int, alvo: Int): Float {
        val dx = abs(c % lado - alvo % lado)
        val dz = abs(c / lado - alvo / lado)
        val menor = min(dx, dz)
        return (max(dx, dz) + 0.41421356f * menor) * CELULA
    }

    /**
     * Procura caminho de (x0, z0) ate (x1, z1). Devolve quantos pontos ha em caminhoX/caminhoZ
     * (0 = sem caminho). Os pontos sao em coordenadas de mundo; o ultimo e o destino (ou o ponto livre mais perto dele).
     */
    fun buscar(x0: Float, z0: Float, x1: Float, z1: Float): Int {
        // Caminho reto livre: nem precisa de A*.
        if (linhaLivre(x0, z0, x1, z1)) {
            caminhoX[0] = x1
            caminhoZ[0] = z1
            return 1
        }
        val inicio = celulaLivrePerto(celula(z0) * lado + celula(x0))
        val fim = celulaLivrePerto(celula(z1) * lado + celula(x1))
        if (inicio < 0 || fim < 0) return 0
        carimbo++
        heapN = 0
        custo[inicio] = 0f
        pai[inicio] = -1
        visita[inicio] = carimbo
        empurrar(inicio, heuristica(inicio, fim))
        var achou = false
        while (heapN > 0) {
            val c = tirar()
            if (fechada[c] == carimbo) continue
            fechada[c] = carimbo
            if (c == fim) {
                achou = true
                break
            }
            val cx = c % lado
            val cz = c / lado
            for (k in 0 until 8) {
                val nx = cx + dxViz[k]
                val nz = cz + dzViz[k]
                if (nx < 0 || nz < 0 || nx >= lado || nz >= lado) continue
                val n = nz * lado + nx
                if (bloqueada[n] || fechada[n] == carimbo) continue
                if (k >= 4) { // diagonal: nao corta quina de parede
                    if (bloqueada[cz * lado + nx] || bloqueada[nz * lado + cx]) continue
                }
                val passo = if (k >= 4) 1.41421356f * CELULA else CELULA
                val novo = custo[c] + passo
                if (visita[n] != carimbo || novo < custo[n]) {
                    visita[n] = carimbo
                    custo[n] = novo
                    pai[n] = c
                    empurrar(n, novo + heuristica(n, fim))
                }
            }
        }
        if (!achou) return 0

        // Reconstroi (do fim para o inicio) em `bruto`, depois inverte.
        var nBruto = 0
        var c = fim
        while (c != -1 && nBruto < bruto.size) {
            bruto[nBruto++] = c
            c = pai[c]
        }
        for (i in 0 until nBruto / 2) {
            val t = bruto[i]; bruto[i] = bruto[nBruto - 1 - i]; bruto[nBruto - 1 - i] = t
        }

        // "Puxar o fio": a partir do ponto atual, pula para o ponto mais distante que ainda tem linha livre.
        var n = 0
        var ax = x0
        var az = z0
        var i = 0
        while (i < nBruto && n < MAX_PONTOS - 1) {
            var j = nBruto - 1
            while (j > i && !linhaLivre(ax, az, centroX(bruto[j] % lado), centroX(bruto[j] / lado))) j--
            val px = centroX(bruto[j] % lado)
            val pz = centroX(bruto[j] / lado)
            caminhoX[n] = px
            caminhoZ[n] = pz
            n++
            ax = px
            az = pz
            i = j + 1
        }
        // Destino real no fim (se o ponto estiver livre), senao fica a celula livre mais perto.
        if (livre(x1, z1)) {
            if (n > 0 && n < MAX_PONTOS && linhaLivre(caminhoX[n - 1], caminhoZ[n - 1], x1, z1)) {
                caminhoX[n] = x1
                caminhoZ[n] = z1
                n++
            }
        }
        return n
    }
}
