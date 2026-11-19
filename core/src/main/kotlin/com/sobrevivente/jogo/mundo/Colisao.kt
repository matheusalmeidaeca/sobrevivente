// core/src/main/kotlin/com/sobrevivente/jogo/mundo/Colisao.kt
package com.sobrevivente.jogo.mundo

import com.badlogic.gdx.math.Vector3
import com.sobrevivente.jogo.Constantes
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Colisão no plano XZ: círculo contra caixas alinhadas aos eixos (AABB), com deslizamento.
 * As caixas vêm de Constantes.PAREDES (4 números por caixa: minX, minZ, maxX, maxZ).
 * Sem alocação: serve para jogador, inimigos e câmera.
 */
object Colisao {

    private const val PASSOS = 3 // repete a correção (quinas entre duas caixas)
    private const val EPSILON = 0.00001f

    /** Empurra o círculo (centro em `posicao.x/z`, `raio`) para fora de todas as paredes. */
    fun resolver(posicao: Vector3, raio: Float) {
        val caixas = Constantes.PAREDES
        val total = caixas.size / 4
        val y = posicao.y
        for (passo in 0 until PASSOS) {
            var empurrou = false
            for (i in 0 until total) {
                // Altura: so colide se o corpo (y .. y+1,8) cruza a parede (base .. base+altura).
                // Da para pular cercas de 1 m e andar por baixo do andar alto.
                val base = Constantes.PAREDES_BASE[i]
                if (y >= base + Constantes.PAREDES_ALTURA[i] - 0.2f || y + 1.8f <= base + 0.2f) continue
                val minX = caixas[i * 4]
                val minZ = caixas[i * 4 + 1]
                val maxX = caixas[i * 4 + 2]
                val maxZ = caixas[i * 4 + 3]

                val maisPertoX = minOf(maxOf(posicao.x, minX), maxX)
                val maisPertoZ = minOf(maxOf(posicao.z, minZ), maxZ)
                val dx = posicao.x - maisPertoX
                val dz = posicao.z - maisPertoZ
                val quadrado = dx * dx + dz * dz
                if (quadrado >= raio * raio) continue
                val rots = Constantes.PAREDES_ROT
                if (i < rots.size && rots[i] != 0f) {
                    if (empurrarObb(posicao, raio, i, rots[i])) empurrou = true
                    continue
                }

                if (quadrado > EPSILON) {
                    // Centro fora da caixa: empurra ao longo da normal até encostar.
                    val distancia = sqrt(quadrado)
                    val penetracao = raio - distancia
                    posicao.x += dx / distancia * penetracao
                    posicao.z += dz / distancia * penetracao
                } else {
                    empurrarParaFora(posicao, raio, minX, minZ, maxX, maxZ)
                }
                empurrou = true
            }
            if (!empurrou) return
        }
    }

    /** Circulo contra a caixa girada `i` (centro, meias-medidas em PAREDES_OBB). Devolve true se empurrou. */
    private fun empurrarObb(posicao: Vector3, raio: Float, i: Int, ang: Float): Boolean {
        val o = Constantes.PAREDES_OBB
        val cx = o[i * 4]; val cz = o[i * 4 + 1]; val hx = o[i * 4 + 2]; val hz = o[i * 4 + 3]
        val c = kotlin.math.cos(ang); val s = kotlin.math.sin(ang)
        val dx = posicao.x - cx; val dz = posicao.z - cz
        val lx = dx * c - dz * s
        val lz = dx * s + dz * c
        val px = minOf(maxOf(lx, -hx), hx)
        val pz = minOf(maxOf(lz, -hz), hz)
        val ex = lx - px; val ez = lz - pz
        val q = ex * ex + ez * ez
        if (q >= raio * raio) return false
        var nx: Float; var nz: Float
        if (q > EPSILON) {
            val d = sqrt(q); val pen = raio - d
            nx = ex / d * pen; nz = ez / d * pen
        } else {
            // centro dentro: sai pelo lado mais perto
            val a = hx - abs(lx); val b = hz - abs(lz)
            if (a < b) { nx = (if (lx >= 0f) a + raio else -(a + raio)); nz = 0f }
            else { nx = 0f; nz = (if (lz >= 0f) b + raio else -(b + raio)) }
        }
        posicao.x += nx * c + nz * s
        posicao.z += -nx * s + nz * c
        return true
    }

    /** O ponto (x, z) esta dentro da parede `i` (considera o giro)? Sem folga. */
    fun dentroDaParede(i: Int, x: Float, z: Float): Boolean {
        val p = Constantes.PAREDES
        if (x <= p[i * 4] || x >= p[i * 4 + 2] || z <= p[i * 4 + 1] || z >= p[i * 4 + 3]) return false
        val rots = Constantes.PAREDES_ROT
        if (i >= rots.size || rots[i] == 0f) return true
        val o = Constantes.PAREDES_OBB
        val c = kotlin.math.cos(rots[i]); val s = kotlin.math.sin(rots[i])
        val dx = x - o[i * 4]; val dz = z - o[i * 4 + 1]
        return abs(dx * c - dz * s) < o[i * 4 + 2] && abs(dx * s + dz * c) < o[i * 4 + 3]
    }

    /** Obstaculo baixo (base no chao, topo entre 0,35 e 1,1 m) a ate ~1,1 m a frente e nada alto no mesmo ponto? */
    fun obstaculoPulavel(x: Float, z: Float, dx: Float, dz: Float, y: Float): Boolean {
        val caixas = Constantes.PAREDES
        val total = caixas.size / 4
        var baixo = false
        for (passo in 1..3) {
            val px = x + dx * 0.4f * passo
            val pz = z + dz * 0.4f * passo
            for (i in 0 until total) {
                val minX = caixas[i * 4]; val minZ = caixas[i * 4 + 1]
                val maxX = caixas[i * 4 + 2]; val maxZ = caixas[i * 4 + 3]
                if (px < minX - 0.35f || px > maxX + 0.35f || pz < minZ - 0.35f || pz > maxZ + 0.35f) continue
                val base = Constantes.PAREDES_BASE[i]
                val topo = base + Constantes.PAREDES_ALTURA[i]
                if (y >= topo - 0.2f || y + 1.8f <= base + 0.2f) continue
                if (base <= 0.05f && topo >= 0.35f && topo <= 1.1f) baixo = true else return false
            }
            if (baixo) return true
        }
        return false
    }

    /** Zumbis nao pisam na escada (Constantes.BLOQUEIOS_INIMIGO): empurra para fora como uma parede. */
    fun resolverBloqueiosInimigo(posicao: Vector3, raio: Float) {
        val caixas = Constantes.BLOQUEIOS_INIMIGO
        for (i in 0 until caixas.size / 4) {
            val minX = caixas[i * 4]
            val minZ = caixas[i * 4 + 1]
            val maxX = caixas[i * 4 + 2]
            val maxZ = caixas[i * 4 + 3]
            val maisPertoX = minOf(maxOf(posicao.x, minX), maxX)
            val maisPertoZ = minOf(maxOf(posicao.z, minZ), maxZ)
            val dx = posicao.x - maisPertoX
            val dz = posicao.z - maisPertoZ
            val quadrado = dx * dx + dz * dz
            if (quadrado >= raio * raio) continue
            if (quadrado > EPSILON) {
                val distancia = sqrt(quadrado)
                val penetracao = raio - distancia
                posicao.x += dx / distancia * penetracao
                posicao.z += dz / distancia * penetracao
            } else {
                empurrarParaFora(posicao, raio, minX, minZ, maxX, maxZ)
            }
        }
    }

    /** Centro dentro da caixa (não deveria acontecer): sai pelo lado mais perto. */
    private fun empurrarParaFora(
        posicao: Vector3, raio: Float,
        minX: Float, minZ: Float, maxX: Float, maxZ: Float
    ) {
        val esquerda = posicao.x - minX
        val direita = maxX - posicao.x
        val cima = posicao.z - minZ
        val baixo = maxZ - posicao.z
        val menor = minOf(minOf(esquerda, direita), minOf(cima, baixo))
        if (menor == esquerda) posicao.x = minX - raio
        else if (menor == direita) posicao.x = maxX + raio
        else if (menor == cima) posicao.z = minZ - raio
        else posicao.z = maxZ + raio
    }

    /**
     * Quanto dá para andar a partir de (origemX, origemZ) na direção NORMALIZADA (dirX, dirZ)
     * antes de encostar numa parede (engordada por `margem`), no máximo `maximo` metros.
     * Usado pela câmera para não atravessar paredes.
     */
    fun distanciaLivreXZ(
        origemX: Float, origemZ: Float,
        dirX: Float, dirZ: Float,
        maximo: Float, margem: Float
    ): Float {
        val caixas = Constantes.PAREDES
        val total = caixas.size / 4
        var melhor = maximo
        for (i in 0 until total) {
            if (Constantes.PAREDES_ALTURA[i] < 2f) continue // cercas baixas nao seguram a camera
            val minX = caixas[i * 4] - margem
            val minZ = caixas[i * 4 + 1] - margem
            val maxX = caixas[i * 4 + 2] + margem
            val maxZ = caixas[i * 4 + 3] + margem

            var entra = 0f
            var sai = melhor
            if (abs(dirX) < EPSILON) {
                if (origemX < minX || origemX > maxX) continue
            } else {
                val a = (minX - origemX) / dirX
                val b = (maxX - origemX) / dirX
                entra = maxOf(entra, minOf(a, b))
                sai = minOf(sai, maxOf(a, b))
            }
            if (abs(dirZ) < EPSILON) {
                if (origemZ < minZ || origemZ > maxZ) continue
            } else {
                val a = (minZ - origemZ) / dirZ
                val b = (maxZ - origemZ) / dirZ
                entra = maxOf(entra, minOf(a, b))
                sai = minOf(sai, maxOf(a, b))
            }
            if (entra <= sai && entra < melhor) melhor = entra
        }
        return melhor
    }
}
