// ferramentas/gerador-boneco-kotlin/src/main/kotlin/gerador/Geometria.kt
package gerador

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Vertice com posicao (m) e UV normalizado dentro da regiao da peca (v = 1 em cima). */
class Vertice(val x: Double, val y: Double, val z: Double, val u: Double, val v: Double)

class Malha(val nome: String) {
    val vertices = ArrayList<Vertice>()
    val triangulos = ArrayList<IntArray>()

    fun adicionarVertice(x: Double, y: Double, z: Double, u: Double, v: Double): Int {
        vertices.add(Vertice(x, y, z, u, v))
        return vertices.size - 1
    }

    fun adicionarTriangulo(a: Int, b: Int, c: Int) {
        triangulos.add(intArrayOf(a, b, c))
    }

    /** Adiciona o triangulo com a face para fora do ponto (cx, cy, cz) (centro de um solido convexo). */
    fun adicionarOrientado(a: Int, b: Int, c: Int, cx: Double, cy: Double, cz: Double) {
        val pa = vertices[a]
        val pb = vertices[b]
        val pc = vertices[c]
        val ux = pb.x - pa.x
        val uy = pb.y - pa.y
        val uz = pb.z - pa.z
        val vx = pc.x - pa.x
        val vy = pc.y - pa.y
        val vz = pc.z - pa.z
        val nx = uy * vz - uz * vy
        val ny = uz * vx - ux * vz
        val nz = ux * vy - uy * vx
        val d = nx * (pa.x - cx) + ny * (pa.y - cy) + nz * (pa.z - cz)
        if (d < 0.0) adicionarTriangulo(a, c, b) else adicionarTriangulo(a, b, c)
    }
}

object Geometria {

    /** UV de amostra: ponto (u, y do mundo) convertido para (u, v) da regiao da peca. */
    fun uvAmostra(nome: String, u: Double, y: Double): DoubleArray {
        val e = Parametros.extremos(nome)
        return doubleArrayOf(u, (y - e[0]) / (e[1] - e[0]))
    }

    /**
     * Peca em "loft": aneis empilhados em Y, LADOS vertices por anel.
     * Vertice 0 fica atras (+Z); u = k / LADOS, entao a frente (-Z) cai em u = 0,5.
     * A emenda da textura fica nas costas.
     */
    fun loft(nome: String, aneis: List<Anel>): Malha {
        val m = Malha(nome)
        val n = Parametros.LADOS
        val indices = ArrayList<IntArray>()
        for (anel in aneis) {
            val linha = IntArray(n + 1)
            for (k in 0..n) {
                val teta = 2.0 * PI * k / n
                val uv = uvAmostra(nome, k.toDouble() / n, anel.y)
                linha[k] = m.adicionarVertice(
                    anel.cx + anel.rx * sin(teta), anel.y, anel.cz + anel.rz * cos(teta), uv[0], uv[1]
                )
            }
            indices.add(linha)
        }
        for (i in 0 until aneis.size - 1) {
            for (k in 0 until n) {
                val a = indices[i][k]
                val b = indices[i][k + 1]
                val c = indices[i + 1][k + 1]
                val d = indices[i + 1][k]
                m.adicionarTriangulo(a, b, c)
                m.adicionarTriangulo(a, c, d)
            }
        }
        tampa(m, aneis, indices, 0, false)
        tampa(m, aneis, indices, aneis.size - 1, true)
        return m
    }

    private fun tampa(m: Malha, aneis: List<Anel>, indices: List<IntArray>, i: Int, cima: Boolean) {
        val anel = aneis[i]
        val uv = uvAmostra(m.nome, 0.5, anel.y)
        val centro = m.adicionarVertice(anel.cx, anel.y, anel.cz, uv[0], uv[1])
        for (k in 0 until Parametros.LADOS) {
            val a = indices[i][k]
            val b = indices[i][k + 1]
            if (cima) m.adicionarTriangulo(centro, a, b) else m.adicionarTriangulo(centro, b, a)
        }
    }

    /** Caixa alinhada aos eixos; todos os vertices usam o mesmo UV de amostra (cor lisa). */
    fun caixa(m: Malha, x0: Double, x1: Double, y0: Double, y1: Double, z0: Double, z1: Double, uv: DoubleArray) {
        val ids = IntArray(8)
        var n = 0
        for (x in doubleArrayOf(x0, x1)) {
            for (y in doubleArrayOf(y0, y1)) {
                for (z in doubleArrayOf(z0, z1)) {
                    ids[n] = m.adicionarVertice(x, y, z, uv[0], uv[1])
                    n++
                }
            }
        }
        val cx = (x0 + x1) / 2.0
        val cy = (y0 + y1) / 2.0
        val cz = (z0 + z1) / 2.0
        val faces = arrayOf(
            intArrayOf(0, 1, 3, 2), intArrayOf(4, 6, 7, 5), intArrayOf(0, 4, 5, 1),
            intArrayOf(2, 3, 7, 6), intArrayOf(0, 2, 6, 4), intArrayOf(1, 5, 7, 3)
        )
        for (q in faces) {
            m.adicionarOrientado(ids[q[0]], ids[q[1]], ids[q[2]], cx, cy, cz)
            m.adicionarOrientado(ids[q[0]], ids[q[2]], ids[q[3]], cx, cy, cz)
        }
    }

    /** Nariz: piramide pequena na frente do rosto (-Z). */
    fun nariz(m: Malha) {
        val uv = uvAmostra("cabeca", 0.5, 1.62)
        val a = m.adicionarVertice(0.0, 1.695, -0.097, uv[0], uv[1])
        val b = m.adicionarVertice(0.0, 1.652, -0.124, uv[0], uv[1])
        val c = m.adicionarVertice(-0.017, 1.645, -0.093, uv[0], uv[1])
        val d = m.adicionarVertice(0.017, 1.645, -0.093, uv[0], uv[1])
        val e = m.adicionarVertice(0.0, 1.668, -0.080, uv[0], uv[1])
        val cx = (0.0 + 0.0 - 0.017 + 0.017 + 0.0) / 5.0
        val cy = (1.695 + 1.652 + 1.645 + 1.645 + 1.668) / 5.0
        val cz = (-0.097 - 0.124 - 0.093 - 0.093 - 0.080) / 5.0
        m.adicionarOrientado(a, b, c, cx, cy, cz)
        m.adicionarOrientado(a, d, b, cx, cy, cz)
        m.adicionarOrientado(b, d, c, cx, cy, cz)
        m.adicionarOrientado(a, c, e, cx, cy, cz)
        m.adicionarOrientado(a, e, d, cx, cy, cz)
        m.adicionarOrientado(c, d, e, cx, cy, cz)
    }

    private fun pe(m: Malha, lado: Double) {
        val x = lado * Parametros.PERNA_EIXO_X
        val uv = uvAmostra(m.nome, 0.5, 0.075)
        caixa(
            m, x - Parametros.PE_MEIA_LARGURA, x + Parametros.PE_MEIA_LARGURA,
            0.0, Parametros.PE_ALTURA, Parametros.PE_PONTA_Z, Parametros.PE_CALCANHAR_Z, uv
        )
    }

    /** Monta as 6 pecas nomeadas: cabeca, tronco, braco_esq, braco_dir, perna_esq, perna_dir. */
    fun montarPecas(): List<Malha> {
        val cabeca = loft("cabeca", Parametros.ANEIS_CABECA)
        nariz(cabeca)
        val tronco = loft("tronco", Parametros.ANEIS_TRONCO)
        val bracoEsq = loft("braco_esq", Parametros.aneisBraco(-1.0))
        val bracoDir = loft("braco_dir", Parametros.aneisBraco(1.0))
        val pernaEsq = loft("perna_esq", Parametros.aneisPerna(-1.0))
        pe(pernaEsq, -1.0)
        val pernaDir = loft("perna_dir", Parametros.aneisPerna(1.0))
        pe(pernaDir, 1.0)
        return listOf(cabeca, tronco, bracoEsq, bracoDir, pernaEsq, pernaDir)
    }
}
