// core/src/main/kotlin/com/sobrevivente/jogo/mundo/Cenario.kt
package com.sobrevivente.jogo.mundo

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import com.sobrevivente.jogo.Constantes

/**
 * Visual da vila (F6): ceu em degrade que segue a camera, decoracao (janelas, vigas, caminhos, copas de arvore, pedras, capim)
 * fundida em UM modelo com cor por vertice (uma chamada de desenho), telhados (um por casa; somem quando o jogador entra)
 * e sombras redondas sob os personagens. So desenho: nada aqui tem colisao.
 */
class Cenario : Disposable {

    private val modelos = ArrayList<Model>()

    val ceu: ModelInstance
    val decoracao: ModelInstance
    val telhados = ArrayList<ModelInstance>()
    private val telhadoVisivel = ArrayList<Boolean>()
    private val telhadoOculta = ArrayList<Boolean>()
    private val telhadoCaixa = ArrayList<FloatArray>() // x0, z0, x1, z1, yBase

    // temporarios (so usados na criacao)
    private val va = VertexInfo()
    private val vb = VertexInfo()
    private val vc = VertexInfo()
    private val pa = Vector3()
    private val pb = Vector3()
    private val pc = Vector3()
    private val nrm = Vector3()
    private val ref = Vector3()
    private val cor = Color()

    init {
        ceu = ModelInstance(criarCeu().also { modelos.add(it) })
        decoracao = ModelInstance(criarDecoracao().also { modelos.add(it) })
        val t = VilaDecoracao.TELHADOS
        for (i in 0 until t.size / 11) {
            val m = criarTelhado(t, i * 11)
            modelos.add(m)
            telhados.add(ModelInstance(m))
            telhadoVisivel.add(true)
            telhadoOculta.add(t[i * 11 + 10] > 0.5f)
            telhadoCaixa.add(floatArrayOf(t[i * 11], t[i * 11 + 1], t[i * 11 + 2], t[i * 11 + 3], t[i * 11 + 4]))
        }
    }

    /** Esconde o telhado da casa em que o jogador esta (para a camera enxergar o interior). */
    fun atualizar(x: Float, y: Float, z: Float) {
        for (i in telhados.indices) {
            if (!telhadoOculta[i]) continue
            val c = telhadoCaixa[i]
            val dentro = x > c[0] + 0.3f && x < c[2] - 0.3f && z > c[1] + 0.3f && z < c[3] - 0.3f
            telhadoVisivel[i] = !dentro
        }
    }

    fun telhadoVisivel(i: Int): Boolean = telhadoVisivel[i]

    /** O ceu acompanha a camera (parece infinito). */
    fun seguirCamera(camera: PerspectiveCamera) {
        ceu.transform.setToTranslation(camera.position.x, camera.position.y, camera.position.z)
    }

    // ------------------------------------------------------------------ ceu
    private fun criarCeu(): Model {
        val atributos = (Usage.Position or Usage.ColorPacked).toLong()
        val material = Material(
            IntAttribute.createCullFace(GL20.GL_NONE),
            DepthTestAttribute(GL20.GL_LEQUAL, false) // nao escreve profundidade: tudo desenha por cima
        )
        val mb = ModelBuilder()
        mb.begin()
        val mp = mb.part("ceu", GL20.GL_TRIANGLES, atributos, material)
        val elevacoes = floatArrayOf(-0.35f, 0f, 0.10f, 0.25f, 0.5f, 0.85f, 1.2f, 1.5708f)
        val segmentos = 20
        val raio = Constantes.CEU_RAIO
        for (k in 0 until elevacoes.size - 1) {
            for (s in 0 until segmentos) {
                val a0 = s * MathUtils.PI2 / segmentos
                val a1 = (s + 1) * MathUtils.PI2 / segmentos
                pontoCeu(pa, raio, elevacoes[k], a0); corCeu(va, elevacoes[k])
                pontoCeu(pb, raio, elevacoes[k], a1); corCeu(vb, elevacoes[k])
                pontoCeu(pc, raio, elevacoes[k + 1], a1); corCeu(vc, elevacoes[k + 1])
                triangulo(mp, pa, pb, pc)
                pontoCeu(pa, raio, elevacoes[k], a0); corCeu(va, elevacoes[k])
                pontoCeu(pb, raio, elevacoes[k + 1], a1); corCeu(vb, elevacoes[k + 1])
                pontoCeu(pc, raio, elevacoes[k + 1], a0); corCeu(vc, elevacoes[k + 1])
                triangulo(mp, pa, pb, pc)
            }
        }
        return mb.end()
    }

    private fun pontoCeu(p: Vector3, raio: Float, elev: Float, ang: Float) {
        p.set(MathUtils.cos(elev) * MathUtils.cos(ang) * raio, MathUtils.sin(elev) * raio, MathUtils.cos(elev) * MathUtils.sin(ang) * raio)
    }

    /** Degrade: horizonte claro e nublado -> zenite azul-acinzentado. */
    private fun corCeu(v: VertexInfo, elev: Float) {
        val t = MathUtils.clamp(elev / 1.2f, 0f, 1f)
        val e = t * t * (3f - 2f * t)
        cor.set(
            MathUtils.lerp(Constantes.CEU_HORIZONTE_R, Constantes.CEU_ZENITE_R, e),
            MathUtils.lerp(Constantes.CEU_HORIZONTE_G, Constantes.CEU_ZENITE_G, e),
            MathUtils.lerp(Constantes.CEU_HORIZONTE_B, Constantes.CEU_ZENITE_B, e),
            1f
        )
        v.setCol(cor)
    }

    /** Triangulo com as posicoes/cores ja em va..vc (normal nao importa: o ceu nao tem luz). */
    private fun triangulo(mp: MeshPartBuilder, p1: Vector3, p2: Vector3, p3: Vector3) {
        va.setPos(p1); vb.setPos(p2); vc.setPos(p3)
        mp.triangle(va, vb, vc)
    }

    // ------------------------------------------------------------------ decoracao (caixas coloridas fundidas)
    private fun criarDecoracao(): Model {
        val atributos = (Usage.Position or Usage.Normal or Usage.ColorPacked).toLong()
        val mb = ModelBuilder()
        mb.begin()
        val mp = mb.part("decor", GL20.GL_TRIANGLES, atributos, Material())
        val d = VilaDecoracao.CAIXAS
        val m = Matrix4()
        for (i in 0 until d.size / 10) {
            val o = i * 10
            m.setToTranslation(d[o], d[o + 1], d[o + 2]).rotate(Vector3.Y, d[o + 6])
            mp.setVertexTransform(m)
            mp.setColor(d[o + 7], d[o + 8], d[o + 9], 1f)
            mp.box(d[o + 3], d[o + 4], d[o + 5])
        }
        return mb.end()
    }

    // ------------------------------------------------------------------ telhado
    private fun criarTelhado(t: FloatArray, o: Int): Model {
        val x0 = t[o]; val z0 = t[o + 1]; val x1 = t[o + 2]; val z1 = t[o + 3]
        val yb = t[o + 4]; val h = t[o + 5]; val forma = t[o + 6].toInt()
        val r = t[o + 7]; val g = t[o + 8]; val b = t[o + 9]
        val atributos = (Usage.Position or Usage.Normal or Usage.ColorPacked).toLong()
        val mb = ModelBuilder()
        mb.begin()
        val mp = mb.part("telhado", GL20.GL_TRIANGLES, atributos, Material(IntAttribute.createCullFace(GL20.GL_NONE)))
        val yr = yb + h
        val xm = (x0 + x1) * 0.5f
        val zm = (z0 + z1) * 0.5f
        ref.set(xm, yb + h * 0.3f, zm) // centro do telhado: as normais apontam para longe daqui
        val beiral = 0.72f // o beiral e mais escuro que a cumeeira (parece telha em fileiras)
        val cume = 1.0f
        val parede = 0.85f // empena (triangulo da frente/tras) um tom de parede
        when (forma) {
            0 -> {
                tri(mp, x0, yb, z0, beiral, x1, yb, z0, beiral, x1, yr, zm, cume, r, g, b)
                tri(mp, x0, yb, z0, beiral, x1, yr, zm, cume, x0, yr, zm, cume, r, g, b)
                tri(mp, x0, yb, z1, beiral, x1, yr, zm, cume, x1, yb, z1, beiral, r, g, b)
                tri(mp, x0, yb, z1, beiral, x0, yr, zm, cume, x1, yr, zm, cume, r, g, b)
                tri(mp, x0, yb, z0, beiral, x0, yb, z1, beiral, x0, yr, zm, cume, r * parede, g * parede, b * parede)
                tri(mp, x1, yb, z0, beiral, x1, yr, zm, cume, x1, yb, z1, beiral, r * parede, g * parede, b * parede)
            }
            1 -> {
                tri(mp, x0, yb, z0, beiral, x0, yb, z1, beiral, xm, yr, z1, cume, r, g, b)
                tri(mp, x0, yb, z0, beiral, xm, yr, z1, cume, xm, yr, z0, cume, r, g, b)
                tri(mp, x1, yb, z0, beiral, xm, yr, z1, cume, x1, yb, z1, beiral, r, g, b)
                tri(mp, x1, yb, z0, beiral, xm, yr, z0, cume, xm, yr, z1, cume, r, g, b)
                tri(mp, x0, yb, z0, beiral, xm, yr, z0, cume, x1, yb, z0, beiral, r * parede, g * parede, b * parede)
                tri(mp, x0, yb, z1, beiral, x1, yb, z1, beiral, xm, yr, z1, cume, r * parede, g * parede, b * parede)
            }
            else -> { // piramide (torre do sino)
                tri(mp, x0, yb, z0, beiral, x1, yb, z0, beiral, xm, yr, zm, cume, r, g, b)
                tri(mp, x1, yb, z0, beiral, x1, yb, z1, beiral, xm, yr, zm, cume, r, g, b)
                tri(mp, x1, yb, z1, beiral, x0, yb, z1, beiral, xm, yr, zm, cume, r, g, b)
                tri(mp, x0, yb, z1, beiral, x0, yb, z0, beiral, xm, yr, zm, cume, r, g, b)
            }
        }
        return mb.end()
    }

    /** Triangulo com normal calculada (apontando para fora do telhado) e cor por vertice (fator de brilho). */
    private fun tri(
        mp: MeshPartBuilder,
        x1: Float, y1: Float, z1: Float, f1: Float,
        x2: Float, y2: Float, z2: Float, f2: Float,
        x3: Float, y3: Float, z3: Float, f3: Float,
        r: Float, g: Float, b: Float
    ) {
        pa.set(x1, y1, z1); pb.set(x2, y2, z2); pc.set(x3, y3, z3)
        nrm.set(pb).sub(pa).crs(pc.x - pa.x, pc.y - pa.y, pc.z - pa.z).nor()
        val cx = (x1 + x2 + x3) / 3f - ref.x
        val cy = (y1 + y2 + y3) / 3f - ref.y
        val cz = (z1 + z2 + z3) / 3f - ref.z
        if (nrm.x * cx + nrm.y * cy + nrm.z * cz < 0f) nrm.scl(-1f)
        va.setPos(pa).setNor(nrm).setCol(r * f1, g * f1, b * f1, 1f)
        vb.setPos(pb).setNor(nrm).setCol(r * f2, g * f2, b * f2, 1f)
        vc.setPos(pc).setNor(nrm).setCol(r * f3, g * f3, b * f3, 1f)
        mp.triangle(va, vb, vc)
    }

    override fun dispose() {
        for (m in modelos) m.dispose()
    }

    companion object {
        /** Modelo da sombra redonda (disco escuro translucido). */
        fun criarModeloSombra(): Model {
            val atributos = (Usage.Position or Usage.Normal).toLong()
            return ModelBuilder().createCylinder(
                1f, 0.01f, 1f, 16,
                Material(
                    com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(0f, 0f, 0f, 1f),
                    BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, Constantes.SOMBRA_ALFA),
                    DepthTestAttribute(GL20.GL_LEQUAL, false)
                ),
                atributos
            )
        }
    }
}
