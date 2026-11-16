// ferramentas/gerador-boneco-kotlin/src/main/kotlin/gerador/Previa.kt
package gerador

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Direcao do olhar (d) e direcao "direita da tela" (r), em coordenadas do mundo. */
class Vista(
    val nome: String,
    val dx: Double, val dy: Double, val dz: Double,
    val rx: Double, val ry: Double, val rz: Double
)

/** Rasterizador de software (ortografico, z-buffer, sombreamento simples) so para a previa. */
object Previa {
    private const val FUNDO = 0x282C34

    // Frente: observador em -Z olhando para +Z. Lado: observador em +X (personagem olha para a direita).
    val VISTAS: List<Vista> = listOf(
        Vista("frente", 0.0, 0.0, 1.0, -1.0, 0.0, 0.0),
        Vista("lado", -1.0, 0.0, 0.0, 0.0, 0.0, -1.0),
        Vista("costas", 0.0, 0.0, -1.0, 1.0, 0.0, 0.0)
    )

    /** Gera previa.png: linha 1 = jogador, linha 2 = zumbi; colunas = frente, lado, costas. */
    fun gravar(pecas: List<Malha>, atlasJogador: IntArray, atlasZumbi: IntArray, arquivo: File) {
        val w = Parametros.VISTA_LARGURA
        val h = Parametros.VISTA_ALTURA
        val saida = BufferedImage(w * VISTAS.size, h * 2, BufferedImage.TYPE_INT_RGB)
        val atlas = listOf(atlasJogador, atlasZumbi)
        for (linha in 0 until 2) {
            for (col in VISTAS.indices) {
                val px = renderizar(pecas, atlas[linha], VISTAS[col])
                saida.setRGB(col * w, linha * h, w, h, px, 0, w)
            }
        }
        ImageIO.write(saida, "png", arquivo)
    }

    private fun renderizar(pecas: List<Malha>, atlas: IntArray, vista: Vista): IntArray {
        val ss = Parametros.SUPER_AMOSTRAGEM
        val w = Parametros.VISTA_LARGURA * ss
        val h = Parametros.VISTA_ALTURA * ss
        val esc = Parametros.VISTA_ESCALA * ss
        val margem = Parametros.VISTA_MARGEM_PES * ss
        val t = Parametros.TAMANHO_ATLAS
        val cor = IntArray(w * h) { FUNDO }
        val prof = DoubleArray(w * h) { Double.MAX_VALUE }

        var lx = -vista.dx * 0.6 - vista.rx * 0.3
        var ly = -vista.dy * 0.6 + 0.8 - vista.ry * 0.3
        var lz = -vista.dz * 0.6 - vista.rz * 0.3
        val ln = sqrt(lx * lx + ly * ly + lz * lz)
        lx /= ln
        ly /= ln
        lz /= ln

        for (malha in pecas) {
            val reg = Parametros.REGIOES.getValue(malha.nome)
            for (tri in malha.triangulos) {
                val a = malha.vertices[tri[0]]
                val b = malha.vertices[tri[1]]
                val c = malha.vertices[tri[2]]
                val ux = b.x - a.x
                val uy = b.y - a.y
                val uz = b.z - a.z
                val vx = c.x - a.x
                val vy = c.y - a.y
                val vz = c.z - a.z
                var nx = uy * vz - uz * vy
                var ny = uz * vx - ux * vz
                var nz = ux * vy - uy * vx
                val nl = sqrt(nx * nx + ny * ny + nz * nz)
                if (nl < 1e-12) continue
                nx /= nl
                ny /= nl
                nz /= nl
                if (-(nx * vista.dx + ny * vista.dy + nz * vista.dz) <= 0.0) continue
                val luz = 0.45 + 0.55 * max(0.0, nx * lx + ny * ly + nz * lz)

                val vs = arrayOf(a, b, c)
                val sx = DoubleArray(3)
                val sy = DoubleArray(3)
                val sz = DoubleArray(3)
                val tx = DoubleArray(3)
                val ty = DoubleArray(3)
                for (i in 0 until 3) {
                    val q = vs[i]
                    sx[i] = (q.x * vista.rx + q.y * vista.ry + q.z * vista.rz) * esc + w / 2.0
                    sy[i] = h - (q.y * esc + margem)
                    sz[i] = q.x * vista.dx + q.y * vista.dy + q.z * vista.dz
                    tx[i] = Atlas.pixelX(reg, q.u)
                    ty[i] = Atlas.pixelY(reg, q.v)
                }
                val den = (sy[1] - sy[2]) * (sx[0] - sx[2]) + (sx[2] - sx[1]) * (sy[0] - sy[2])
                if (kotlin.math.abs(den) < 1e-9) continue
                val xMin = max(0, min(sx[0], min(sx[1], sx[2])).toInt())
                val xMax = min(w - 1, max(sx[0], max(sx[1], sx[2])).toInt() + 1)
                val yMin = max(0, min(sy[0], min(sy[1], sy[2])).toInt())
                val yMax = min(h - 1, max(sy[0], max(sy[1], sy[2])).toInt() + 1)
                for (yy in yMin..yMax) {
                    for (xx in xMin..xMax) {
                        val pxc = xx + 0.5
                        val pyc = yy + 0.5
                        val l1 = ((sy[1] - sy[2]) * (pxc - sx[2]) + (sx[2] - sx[1]) * (pyc - sy[2])) / den
                        val l2 = ((sy[2] - sy[0]) * (pxc - sx[2]) + (sx[0] - sx[2]) * (pyc - sy[2])) / den
                        val l3 = 1.0 - l1 - l2
                        if (l1 < -1e-6 || l2 < -1e-6 || l3 < -1e-6) continue
                        val z = l1 * sz[0] + l2 * sz[1] + l3 * sz[2]
                        val pos = yy * w + xx
                        if (z >= prof[pos]) continue
                        prof[pos] = z
                        val tu = l1 * tx[0] + l2 * tx[1] + l3 * tx[2]
                        val tv = l1 * ty[0] + l2 * ty[1] + l3 * ty[2]
                        val ix = min(t - 1, max(0, tu.toInt()))
                        val iy = min(t - 1, max(0, tv.toInt()))
                        val tc = atlas[iy * t + ix]
                        val r = min(255, (((tc shr 16) and 255) * luz).toInt())
                        val g = min(255, (((tc shr 8) and 255) * luz).toInt())
                        val bl = min(255, ((tc and 255) * luz).toInt())
                        cor[pos] = (r shl 16) or (g shl 8) or bl
                    }
                }
            }
        }
        return reduzir(cor, w, ss)
    }

    /** Media blocos ss x ss de pixels (suavizacao das bordas). */
    private fun reduzir(cor: IntArray, w: Int, ss: Int): IntArray {
        val w0 = Parametros.VISTA_LARGURA
        val h0 = Parametros.VISTA_ALTURA
        val saida = IntArray(w0 * h0)
        val n = ss * ss
        for (y in 0 until h0) {
            for (x in 0 until w0) {
                var r = 0
                var g = 0
                var b = 0
                for (j in 0 until ss) {
                    for (i in 0 until ss) {
                        val c = cor[(y * ss + j) * w + x * ss + i]
                        r += (c shr 16) and 255
                        g += (c shr 8) and 255
                        b += c and 255
                    }
                }
                saida[y * w0 + x] = ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
        return saida
    }
}
