// core/src/main/kotlin/com/sobrevivente/jogo/grafico/Particulas.kt
package com.sobrevivente.jogo.grafico

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import com.sobrevivente.jogo.Constantes

/**
 * Particulas de combate (F7): fumaca e faiscas no cano, capsulas ejetadas, sangue, poeira de impacto e de corrida.
 * Tudo em arrays fixos (zero alocacao por frame) e desenhado em UMA chamada: quadrados sempre virados para a camera,
 * com borda suave feita no shader.
 */
class Particulas : Disposable {

    private val max = Constantes.PARTICULAS_MAX

    private val px = FloatArray(max)
    private val py = FloatArray(max)
    private val pz = FloatArray(max)
    private val vx = FloatArray(max)
    private val vy = FloatArray(max)
    private val vz = FloatArray(max)
    private val vida = FloatArray(max)
    private val vidaTotal = FloatArray(max)
    private val tam0 = FloatArray(max)
    private val tam1 = FloatArray(max)
    private val cr = FloatArray(max)
    private val cg = FloatArray(max)
    private val cb = FloatArray(max)
    private val ca = FloatArray(max)
    private val gravidade = FloatArray(max)
    private val arrasto = FloatArray(max)
    private val quica = FloatArray(max)
    private var n = 0

    private val vertices = FloatArray(max * 4 * TAM_VERTICE)
    private val mesh: Mesh
    private val sombreador: ShaderProgram?
    private val direita = Vector3()
    private val cima = Vector3()

    init {
        mesh = Mesh(
            false, max * 4, max * 6,
            VertexAttribute.Position(), VertexAttribute.ColorPacked(), VertexAttribute.TexCoords(0)
        )
        val indices = ShortArray(max * 6)
        for (i in 0 until max) {
            val b = (i * 4).toShort()
            indices[i * 6] = b
            indices[i * 6 + 1] = (b + 1).toShort()
            indices[i * 6 + 2] = (b + 2).toShort()
            indices[i * 6 + 3] = (b + 2).toShort()
            indices[i * 6 + 4] = (b + 3).toShort()
            indices[i * 6 + 5] = b
        }
        mesh.setIndices(indices)
        val p = ShaderProgram(VERTICE, FRAGMENTO)
        if (!p.isCompiled) {
            Gdx.app.error("PARTICULAS", "shader nao compilou: " + p.log)
            p.dispose()
            sombreador = null
        } else {
            sombreador = p
        }
    }

    // ------------------------------------------------------------------ emissao
    private fun emitir(
        x: Float, y: Float, z: Float, dx: Float, dy: Float, dz: Float,
        duracao: Float, t0: Float, t1: Float, r: Float, g: Float, b: Float, a: Float,
        grav: Float, atrito: Float, quicar: Float
    ) {
        if (n >= max) return
        val i = n++
        px[i] = x; py[i] = y; pz[i] = z
        vx[i] = dx; vy[i] = dy; vz[i] = dz
        vida[i] = duracao; vidaTotal[i] = duracao
        tam0[i] = t0; tam1[i] = t1
        cr[i] = r; cg[i] = g; cb[i] = b; ca[i] = a
        gravidade[i] = grav; arrasto[i] = atrito; quica[i] = quicar
    }

    private fun alea(a: Float) = MathUtils.random(-a, a)

    /** Fumaca e faiscas no cano. (dirX, dirY, dirZ) = para onde a arma aponta. */
    fun tiro(x: Float, y: Float, z: Float, dirX: Float, dirY: Float, dirZ: Float, forte: Boolean) {
        for (k in 0 until (if (forte) 4 else 2)) {
            emitir(
                x, y, z, dirX * 1.2f + alea(0.4f), dirY * 1.2f + 0.5f + alea(0.3f), dirZ * 1.2f + alea(0.4f),
                MathUtils.random(0.5f, 0.9f), 0.10f, MathUtils.random(0.5f, 0.8f), 0.72f, 0.72f, 0.74f, 0.45f, -0.4f, 1.6f, 0f
            )
        }
        for (k in 0 until (if (forte) 7 else 4)) {
            emitir(
                x, y, z, dirX * 9f + alea(2.2f), dirY * 9f + alea(2.2f), dirZ * 9f + alea(2.2f),
                MathUtils.random(0.06f, 0.14f), 0.07f, 0.01f, 1f, 0.82f, 0.35f, 1f, 0f, 2f, 0f
            )
        }
    }

    /** Capsula de latao ejetada para o lado (direita da camera). */
    fun capsula(x: Float, y: Float, z: Float, dirX: Float, dirZ: Float) {
        emitir(
            x, y, z, dirX * 2.2f + alea(0.4f), 2.4f + alea(0.4f), dirZ * 2.2f + alea(0.4f),
            1.4f, 0.045f, 0.045f, 0.85f, 0.68f, 0.25f, 1f, 14f, 0.5f, 0.35f
        )
    }

    /** Sangue: gotas que saem na direcao oposta ao tiro e caem. `intenso` = zumbi morreu. */
    fun sangue(x: Float, y: Float, z: Float, dirX: Float, dirY: Float, dirZ: Float, intenso: Boolean) {
        val total = if (intenso) 22 else 9
        for (k in 0 until total) {
            val v = MathUtils.random(1.5f, if (intenso) 6f else 4f)
            emitir(
                x, y, z, -dirX * v + alea(1.6f), -dirY * v + MathUtils.random(0.5f, 2.5f), -dirZ * v + alea(1.6f),
                MathUtils.random(0.45f, 0.9f), MathUtils.random(0.07f, 0.16f), 0.04f,
                MathUtils.random(0.45f, 0.65f), 0.02f, 0.03f, 0.95f, 14f, 0.4f, 0f
            )
        }
        emitir(x, y, z, 0f, 0.3f, 0f, 0.35f, 0.25f, 0.6f, 0.5f, 0.04f, 0.05f, 0.5f, 0f, 3f, 0f)
    }

    /** Impacto em parede/chao: faiscas e poeira. */
    fun impacto(x: Float, y: Float, z: Float, dirX: Float, dirY: Float, dirZ: Float) {
        for (k in 0 until 5) {
            emitir(
                x, y, z, -dirX * 3f + alea(2.5f), -dirY * 3f + alea(2.5f) + 1f, -dirZ * 3f + alea(2.5f),
                MathUtils.random(0.1f, 0.25f), 0.06f, 0.01f, 1f, 0.8f, 0.4f, 1f, 9f, 1f, 0f
            )
        }
        for (k in 0 until 3) {
            emitir(
                x, y, z, alea(0.6f), MathUtils.random(0.2f, 0.9f), alea(0.6f),
                MathUtils.random(0.5f, 0.9f), 0.15f, 0.7f, 0.62f, 0.58f, 0.5f, 0.5f, -0.2f, 2f, 0f
            )
        }
    }

    /** Poeira nos pes ao correr/pousar. */
    fun poeira(x: Float, y: Float, z: Float, quantidade: Int) {
        for (k in 0 until quantidade) {
            emitir(
                x + alea(0.2f), y + 0.05f, z + alea(0.2f), alea(0.7f), MathUtils.random(0.3f, 0.9f), alea(0.7f),
                MathUtils.random(0.45f, 0.8f), 0.14f, MathUtils.random(0.5f, 0.8f), 0.62f, 0.55f, 0.42f, 0.38f, -0.3f, 2.2f, 0f
            )
        }
    }

    // ------------------------------------------------------------------ vida e desenho
    fun atualizar(dt: Float) {
        var i = 0
        while (i < n) {
            vida[i] -= dt
            if (vida[i] <= 0f) {
                val u = n - 1 // troca com o ultimo
                if (i != u) copiar(u, i)
                n--
                continue
            }
            vy[i] -= gravidade[i] * dt
            val f = maxOf(0f, 1f - arrasto[i] * dt)
            vx[i] *= f; vy[i] *= f; vz[i] *= f
            px[i] += vx[i] * dt
            py[i] += vy[i] * dt
            pz[i] += vz[i] * dt
            if (py[i] < 0.03f && vy[i] < 0f) { // chao (as particulas nao atravessam o piso)
                py[i] = 0.03f
                vy[i] = -vy[i] * quica[i]
                vx[i] *= 0.6f
                vz[i] *= 0.6f
                if (quica[i] <= 0f) { vx[i] = 0f; vz[i] = 0f }
            }
            i++
        }
    }

    private fun copiar(de: Int, para: Int) {
        px[para] = px[de]; py[para] = py[de]; pz[para] = pz[de]
        vx[para] = vx[de]; vy[para] = vy[de]; vz[para] = vz[de]
        vida[para] = vida[de]; vidaTotal[para] = vidaTotal[de]
        tam0[para] = tam0[de]; tam1[para] = tam1[de]
        cr[para] = cr[de]; cg[para] = cg[de]; cb[para] = cb[de]; ca[para] = ca[de]
        gravidade[para] = gravidade[de]; arrasto[para] = arrasto[de]; quica[para] = quica[de]
    }

    /** Desenha todas as particulas vivas (chamar depois do ModelBatch.end, ainda na cena). */
    fun desenhar(camera: Camera) {
        val sh = sombreador ?: return
        if (n == 0) return
        direita.set(camera.direction).crs(camera.up).nor()
        cima.set(direita).crs(camera.direction).nor()
        var o = 0
        for (i in 0 until n) {
            val t = 1f - vida[i] / vidaTotal[i] // 0 = nasceu, 1 = vai sumir
            val meio = (tam0[i] + (tam1[i] - tam0[i]) * t) * 0.5f
            val alfa = ca[i] * (if (t < 0.15f) t / 0.15f else 1f - (t - 0.15f) / 0.85f)
            val cor = Color.toFloatBits(cr[i], cg[i], cb[i], MathUtils.clamp(alfa, 0f, 1f))
            val rx = direita.x * meio
            val ry = direita.y * meio
            val rz = direita.z * meio
            val ux = cima.x * meio
            val uy = cima.y * meio
            val uz = cima.z * meio
            o = quad(o, px[i] - rx - ux, py[i] - ry - uy, pz[i] - rz - uz, cor, 0f, 1f)
            o = quad(o, px[i] + rx - ux, py[i] + ry - uy, pz[i] + rz - uz, cor, 1f, 1f)
            o = quad(o, px[i] + rx + ux, py[i] + ry + uy, pz[i] + rz + uz, cor, 1f, 0f)
            o = quad(o, px[i] - rx + ux, py[i] - ry + uy, pz[i] - rz + uz, cor, 0f, 0f)
        }
        mesh.setVertices(vertices, 0, o)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL)
        Gdx.gl.glDepthMask(false)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        sh.bind()
        sh.setUniformMatrix("u_projView", camera.combined)
        mesh.render(sh, GL20.GL_TRIANGLES, 0, n * 6)
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
    }

    private fun quad(o: Int, x: Float, y: Float, z: Float, cor: Float, u: Float, v: Float): Int {
        vertices[o] = x; vertices[o + 1] = y; vertices[o + 2] = z
        vertices[o + 3] = cor; vertices[o + 4] = u; vertices[o + 5] = v
        return o + TAM_VERTICE
    }

    fun limpar() {
        n = 0
    }

    override fun dispose() {
        mesh.dispose()
        sombreador?.dispose()
    }

    private companion object {
        const val TAM_VERTICE = 6
        const val VERTICE = """
attribute vec4 a_position;
attribute vec4 a_color;
attribute vec2 a_texCoord0;
uniform mat4 u_projView;
varying vec4 v_cor;
varying vec2 v_uv;
void main() {
    v_cor = a_color;
    v_cor.a = v_cor.a * (255.0 / 254.0);
    v_uv = a_texCoord0;
    gl_Position = u_projView * a_position;
}
"""
        const val FRAGMENTO = """
#ifdef GL_ES
precision mediump float;
#endif
varying vec4 v_cor;
varying vec2 v_uv;
void main() {
    float d = length(v_uv - vec2(0.5)) * 2.0;
    float a = smoothstep(1.0, 0.3, d);
    gl_FragColor = vec4(v_cor.rgb, v_cor.a * a);
}
"""
    }
}
