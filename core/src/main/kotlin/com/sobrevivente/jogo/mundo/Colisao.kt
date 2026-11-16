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
        for (passo in 0 until PASSOS) {
            var empurrou = false
            for (i in 0 until total) {
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
