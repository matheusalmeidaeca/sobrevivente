// ferramentas/gerador-boneco-kotlin/src/main/kotlin/gerador/ExportadorObj.kt
package gerador

import java.io.File
import java.util.Locale
import kotlin.math.sqrt

/** Grava .obj + .mtl. Cada peca vira um grupo ("g nome"); normais planas por triangulo. */
object ExportadorObj {

    fun escrever(pasta: File, nomeBase: String, material: String, texturaArquivo: String, pecas: List<Malha>) {
        val obj = StringBuilder()
        obj.append("# Boneco low-poly gerado por codigo (F0). Y para cima, 1 unidade = 1 m, frente = -Z.\n")
        obj.append("mtllib ").append(nomeBase).append(".mtl\n")
        var baseVertice = 1
        var baseNormal = 1
        for (m in pecas) {
            obj.append("g ").append(m.nome).append('\n')
            obj.append("usemtl ").append(material).append('\n')
            val reg = Parametros.REGIOES.getValue(m.nome)
            val t = Parametros.TAMANHO_ATLAS.toDouble()
            for (v in m.vertices) {
                obj.append(String.format(Locale.US, "v %.5f %.5f %.5f\n", v.x, v.y, v.z))
            }
            for (v in m.vertices) {
                val u = Atlas.pixelX(reg, v.u) / t
                val w = 1.0 - Atlas.pixelY(reg, v.v) / t
                obj.append(String.format(Locale.US, "vt %.5f %.5f\n", u, w))
            }
            for (tri in m.triangulos) {
                val n = normal(m, tri)
                obj.append(String.format(Locale.US, "vn %.5f %.5f %.5f\n", n[0], n[1], n[2]))
            }
            for ((i, tri) in m.triangulos.withIndex()) {
                val a = baseVertice + tri[0]
                val b = baseVertice + tri[1]
                val c = baseVertice + tri[2]
                val nn = baseNormal + i
                obj.append("f ").append(a).append('/').append(a).append('/').append(nn)
                obj.append(' ').append(b).append('/').append(b).append('/').append(nn)
                obj.append(' ').append(c).append('/').append(c).append('/').append(nn).append('\n')
            }
            baseVertice += m.vertices.size
            baseNormal += m.triangulos.size
        }
        File(pasta, "$nomeBase.obj").writeText(obj.toString())

        val mtl = StringBuilder()
        mtl.append("newmtl ").append(material).append('\n')
        mtl.append("Ka 1.0 1.0 1.0\nKd 1.0 1.0 1.0\nKs 0.0 0.0 0.0\nd 1.0\nillum 1\n")
        mtl.append("map_Kd ").append(texturaArquivo).append('\n')
        File(pasta, "$nomeBase.mtl").writeText(mtl.toString())
    }

    private fun normal(m: Malha, tri: IntArray): DoubleArray {
        val a = m.vertices[tri[0]]
        val b = m.vertices[tri[1]]
        val c = m.vertices[tri[2]]
        val ux = b.x - a.x
        val uy = b.y - a.y
        val uz = b.z - a.z
        val vx = c.x - a.x
        val vy = c.y - a.y
        val vz = c.z - a.z
        val nx = uy * vz - uz * vy
        val ny = uz * vx - ux * vz
        val nz = ux * vy - uy * vx
        val l = sqrt(nx * nx + ny * ny + nz * nz)
        return if (l < 1e-12) doubleArrayOf(0.0, 1.0, 0.0) else doubleArrayOf(nx / l, ny / l, nz / l)
    }
}
