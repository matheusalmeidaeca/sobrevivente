// core/src/main/kotlin/com/sobrevivente/jogo/mundo/Fachadas.kt
package com.sobrevivente.jogo.mundo

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import java.util.Random
import kotlin.math.abs
import kotlin.math.floor

/**
 * Fachadas procedurais da cidade: texturas geradas por codigo (concreto, tijolo, vidro, reboco sujo), cada uma com
 * janelas de moldura e peitoril, em 3 variantes (apagada, acesa, quebrada). Uma "casa" de textura = 1 vao (4 m x 3,6 m)
 * e se repete em metros reais, entao o predio tem andares e janelas de verdade em vez de um bloco liso.
 */
object Fachadas {
    const val TILE_W = 4.0f
    const val TILE_H = 3.6f
    private const val TAM = 256

    private val cache = HashMap<Int, Texture>()

    fun limpar() {
        for (t in cache.values) t.dispose()
        cache.clear()
    }

    private fun textura(tipo: Int, variante: Int): Texture {
        val chave = tipo * 10 + variante
        return cache.getOrPut(chave) { gerar(tipo, variante) }
    }

    private fun rgb(r: Float, g: Float, b: Float): Int {
        val ri = (r.coerceIn(0f, 1f) * 255f).toInt()
        val gi = (g.coerceIn(0f, 1f) * 255f).toInt()
        val bi = (b.coerceIn(0f, 1f) * 255f).toInt()
        return (ri shl 24) or (gi shl 16) or (bi shl 8) or 0xFF
    }

    private fun gerar(tipo: Int, variante: Int): Texture {
        val pm = Pixmap(TAM, TAM, Pixmap.Format.RGBA8888)
        val rnd = Random((tipo * 31 + variante * 7 + 5).toLong())
        // --- superficie base ---
        val cb = when (tipo) {
            14 -> floatArrayOf(0.50f, 0.25f, 0.20f)
            15 -> floatArrayOf(0.70f, 0.68f, 0.62f)
            13 -> floatArrayOf(0.20f, 0.27f, 0.33f)
            else -> floatArrayOf(0.40f, 0.41f, 0.43f)
        }
        val tijoloAltura = 12
        val tijoloLargura = 32
        for (y in 0 until TAM) for (x in 0 until TAM) {
            var r = cb[0]; var g = cb[1]; var b = cb[2]
            val ruido = (rnd.nextFloat() - 0.5f) * 0.07f
            if (tipo == 14) { // tijolos alternados com argamassa
                val linha = y / tijoloAltura
                val xo = (x + (if (linha % 2 == 0) 0 else tijoloLargura / 2)) % tijoloLargura
                val junta = (y % tijoloAltura) < 2 || xo < 2
                if (junta) { r = 0.62f; g = 0.60f; b = 0.56f }
                else {
                    val v = ((linha * 7 + (x + (if (linha % 2 == 0) 0 else 16)) / tijoloLargura * 13) % 5) * 0.018f
                    r += v; g += v * 0.5f; b += v * 0.4f
                }
            }
            r += ruido; g += ruido; b += ruido
            // sujeira: escurece para baixo e nas bordas do vao; escorridos verticais
            val baixo = y / TAM.toFloat()
            val escorrido = if ((x * 37 + 11) % 61 < 3) 0.05f * baixo else 0f
            val f = 1f - 0.18f * baixo * baixo - escorrido
            pm.drawPixel(x, y, rgb(r * f, g * f, b * f))
        }
        // junta de laje (linha horizontal discreta) no limite do vao
        if (tipo != 14 && tipo != 13) {
            pm.setColor(rgb(cb[0] * 0.65f, cb[1] * 0.65f, cb[2] * 0.65f))
            pm.fillRectangle(0, TAM - 4, TAM, 3)
            pm.setColor(rgb(cb[0] * 1.15f, cb[1] * 1.15f, cb[2] * 1.15f))
            pm.fillRectangle(0, 0, TAM, 2)
        }
        // --- janelas ---
        if (tipo == 13) {
            // fachada de vidro: 2 paineis por vao, montantes escuros
            for (px in 0..1) {
                val x0 = px * 128 + 6
                pintarVidro(pm, x0, 18, 116, 190, variante, rnd)
            }
            pm.setColor(rgb(0.10f, 0.11f, 0.13f))
            pm.fillRectangle(0, 0, TAM, 12)
            pm.fillRectangle(0, TAM - 40, TAM, 40)
            pm.fillRectangle(0, 0, 6, TAM)
            pm.fillRectangle(124, 0, 8, TAM)
        } else {
            val wx = 80; val wy = 56; val ww = 96; val wh = 118
            // sombra/reentrancia
            pm.setColor(rgb(cb[0] * 0.35f, cb[1] * 0.35f, cb[2] * 0.35f))
            pm.fillRectangle(wx - 7, wy - 7, ww + 14, wh + 14)
            // moldura
            pm.setColor(rgb(0.13f, 0.13f, 0.14f))
            pm.fillRectangle(wx - 4, wy - 4, ww + 8, wh + 8)
            pintarVidro(pm, wx, wy, ww, wh, variante, rnd)
            // travessas
            pm.setColor(rgb(0.15f, 0.15f, 0.16f))
            pm.fillRectangle(wx + ww / 2 - 2, wy, 4, wh)
            pm.fillRectangle(wx, wy + wh / 2 - 2, ww, 4)
            // peitoril claro
            pm.setColor(rgb(cb[0] * 1.35f + 0.05f, cb[1] * 1.35f + 0.05f, cb[2] * 1.35f + 0.05f))
            pm.fillRectangle(wx - 12, wy + wh + 4, ww + 24, 8)
            pm.setColor(rgb(cb[0] * 0.5f, cb[1] * 0.5f, cb[2] * 0.5f))
            pm.fillRectangle(wx - 12, wy + wh + 12, ww + 24, 3)
        }
        val tex = Texture(pm)
        pm.dispose()
        tex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat)
        tex.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
        return tex
    }

    /** Vidro: variante 0 apagado (azul escuro com reflexo), 1 aceso (ambar), 2 quebrado (trincas e escuridao). */
    private fun pintarVidro(pm: Pixmap, x0: Int, y0: Int, w: Int, h: Int, variante: Int, rnd: Random) {
        for (y in 0 until h) {
            val t = y / h.toFloat()
            for (x in 0 until w) {
                val d = (x + y * 0.6f) / (w + h).toFloat()
                var r: Float; var g: Float; var b: Float
                when (variante) {
                    1 -> { r = 0.95f - 0.25f * t; g = 0.72f - 0.28f * t; b = 0.32f - 0.14f * t }
                    2 -> { r = 0.05f; g = 0.06f; b = 0.07f }
                    else -> { r = 0.20f - 0.12f * t; g = 0.30f - 0.16f * t; b = 0.38f - 0.20f * t }
                }
                if (variante != 1 && d > 0.28f && d < 0.40f) { r += 0.10f; g += 0.12f; b += 0.14f } // reflexo diagonal
                val ru = (rnd.nextFloat() - 0.5f) * 0.03f
                pm.drawPixel(x0 + x, y0 + y, rgb(r + ru, g + ru, b + ru))
            }
        }
        if (variante == 2) { // trincas
            pm.setColor(rgb(0.55f, 0.57f, 0.60f))
            val cx = x0 + w / 3; val cy = y0 + h / 3
            pm.drawLine(cx, cy, x0 + w - 6, y0 + h - 8)
            pm.drawLine(cx, cy, x0 + 8, y0 + h - 10)
            pm.drawLine(cx, cy, x0 + w / 2, y0 + 6)
            pm.drawLine(cx, cy, x0 + w - 4, y0 + h / 4)
        }
    }

    /** Predio texturizado (4 faces com UV em metros) + topo liso escuro. Origem no centro, como createBox. */
    fun criarCaixa(largura: Float, altura: Float, profundidade: Float, tipo: Int, indice: Int): Model {
        val variante = when ((indice * 7 + tipo) % 11) { 0, 5 -> 1; 3 -> 2; else -> 0 }
        val tex = textura(tipo, variante)
        val mb = ModelBuilder()
        mb.begin()
        val matLado = Material(
            TextureAttribute.createDiffuse(tex),
            ColorAttribute.createDiffuse(1f, 1f, 1f, 1f),
            IntAttribute.createCullFace(GL20.GL_NONE)
        )
        val atr = (Usage.Position or Usage.Normal or Usage.TextureCoordinates).toLong()
        val lado = mb.part("lados", GL20.GL_TRIANGLES, atr, matLado)
        val hw = largura / 2f; val hh = altura / 2f; val hd = profundidade / 2f
        val offV = kotlin.math.ceil(altura / TILE_H) - altura / TILE_H
        fun v(x: Float, y: Float, z: Float, u: Float, nx: Float, nz: Float): VertexInfo {
            val vv = (hh - y) / TILE_H + offV
            return VertexInfo().setPos(x, y, z).setNor(nx, 0f, nz).setUV(u / TILE_W, vv)
        }
        // +Z
        lado.rect(v(-hw, -hh, hd, 0f, 0f, 1f), v(hw, -hh, hd, largura, 0f, 1f), v(hw, hh, hd, largura, 0f, 1f), v(-hw, hh, hd, 0f, 0f, 1f))
        // -Z
        lado.rect(v(hw, -hh, -hd, 0f, 0f, -1f), v(-hw, -hh, -hd, largura, 0f, -1f), v(-hw, hh, -hd, largura, 0f, -1f), v(hw, hh, -hd, 0f, 0f, -1f))
        // +X
        lado.rect(v(hw, -hh, hd, 0f, 1f, 0f), v(hw, -hh, -hd, profundidade, 1f, 0f), v(hw, hh, -hd, profundidade, 1f, 0f), v(hw, hh, hd, 0f, 1f, 0f))
        // -X
        lado.rect(v(-hw, -hh, -hd, 0f, -1f, 0f), v(-hw, -hh, hd, profundidade, -1f, 0f), v(-hw, hh, hd, profundidade, -1f, 0f), v(-hw, hh, -hd, 0f, -1f, 0f))
        val matTopo = Material(ColorAttribute.createDiffuse(0.16f, 0.16f, 0.17f, 1f), IntAttribute.createCullFace(GL20.GL_NONE))
        val topo = mb.part("topo", GL20.GL_TRIANGLES, (Usage.Position or Usage.Normal).toLong(), matTopo)
        topo.rect(
            Vector3(-hw, hh, hd), Vector3(hw, hh, hd), Vector3(hw, hh, -hd), Vector3(-hw, hh, -hd),
            Vector3(0f, 1f, 0f)
        )
        return mb.end()
    }
}
