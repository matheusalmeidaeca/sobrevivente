// core/src/main/kotlin/com/sobrevivente/jogo/grafico/PosProcessamento.kt
package com.sobrevivente.jogo.grafico

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable
import com.sobrevivente.jogo.Constantes

/**
 * Pos-processamento (F7): a cena 3D vai para uma textura e depois sai na tela com
 * suavizacao de serrilhado (FXAA simplificado), brilho (bloom) nas partes claras, correcao de cor
 * (contraste, saturacao, tom quente) e vinheta nos cantos.
 * Se algo falhar (shader nao compila, FBO nao cria), `ativo` fica false e o jogo desenha direto na tela, como antes.
 */
class PosProcessamento : Disposable {

    var ativo = false
        private set

    private var largura = 0
    private var altura = 0
    private var cena: FrameBuffer? = null
    private var brilhoA: FrameBuffer? = null
    private var brilhoB: FrameBuffer? = null
    private val lote = SpriteBatch(4)
    private var sombreadorBrilho: ShaderProgram? = null
    private var sombreadorBorrar: ShaderProgram? = null
    private var sombreadorFinal: ShaderProgram? = null

    init {
        ShaderProgram.pedantic = false
        sombreadorBrilho = compilar(VERTICE, FRAG_BRILHO)
        sombreadorBorrar = compilar(VERTICE, FRAG_BORRAR)
        sombreadorFinal = compilar(VERTICE, FRAG_FINAL)
    }

    private fun compilar(vertice: String, fragmento: String): ShaderProgram? {
        val p = ShaderProgram(vertice, fragmento)
        if (!p.isCompiled) {
            Gdx.app.error("POS", "shader nao compilou: " + p.log)
            p.dispose()
            return null
        }
        return p
    }

    /** Chamar no resize (e na criacao). Recria as texturas de trabalho. */
    fun redimensionar(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        liberar()
        largura = w
        altura = h
        if (sombreadorBrilho == null || sombreadorBorrar == null || sombreadorFinal == null || !Constantes.POS_PROCESSAMENTO) {
            ativo = false
            return
        }
        try {
            cena = FrameBuffer(Pixmap.Format.RGBA8888, w, h, true)
            val bw = maxOf(1, w / 4)
            val bh = maxOf(1, h / 4)
            brilhoA = FrameBuffer(Pixmap.Format.RGBA8888, bw, bh, false)
            brilhoB = FrameBuffer(Pixmap.Format.RGBA8888, bw, bh, false)
            cena!!.colorBufferTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            brilhoA!!.colorBufferTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            brilhoB!!.colorBufferTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            lote.projectionMatrix.setToOrtho2D(0f, 0f, w.toFloat(), h.toFloat())
            ativo = true
        } catch (e: Exception) {
            Gdx.app.error("POS", "FBO nao criou: " + e.message)
            liberar()
            ativo = false
        }
    }

    /** Comeca a desenhar a cena (limpe a tela DEPOIS desta chamada). */
    fun comecar() {
        if (ativo) cena?.begin()
    }

    /** Termina a cena e escreve na tela com os efeitos. */
    fun terminar() {
        if (!ativo) return
        val c = cena ?: return
        val a = brilhoA ?: return
        val b = brilhoB ?: return
        c.end()
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        val bw = a.width
        val bh = a.height

        // 1) so as partes claras, em 1/4 do tamanho
        a.begin()
        lote.projectionMatrix.setToOrtho2D(0f, 0f, bw.toFloat(), bh.toFloat())
        lote.shader = sombreadorBrilho
        lote.disableBlending()
        lote.begin()
        sombreadorBrilho!!.setUniformf("u_limiar", Constantes.BLOOM_LIMIAR)
        lote.draw(c.colorBufferTexture, 0f, 0f, bw.toFloat(), bh.toFloat(), 0, 0, c.width, c.height, false, true)
        lote.end()
        a.end()

        // 2) borrao horizontal (a -> b) e vertical (b -> a)
        b.begin()
        lote.shader = sombreadorBorrar
        lote.begin()
        sombreadorBorrar!!.setUniformf("u_direcao", 1f / bw, 0f)
        lote.draw(a.colorBufferTexture, 0f, 0f, bw.toFloat(), bh.toFloat(), 0, 0, bw, bh, false, false)
        lote.end()
        b.end()
        a.begin()
        lote.begin()
        sombreadorBorrar!!.setUniformf("u_direcao", 0f, 1f / bh)
        lote.draw(b.colorBufferTexture, 0f, 0f, bw.toFloat(), bh.toFloat(), 0, 0, bw, bh, false, false)
        lote.end()
        a.end()

        // 3) final na tela: FXAA + bloom + cor + vinheta
        Gdx.gl.glViewport(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        lote.projectionMatrix.setToOrtho2D(0f, 0f, largura.toFloat(), altura.toFloat())
        lote.shader = sombreadorFinal
        a.colorBufferTexture.bind(1)
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)
        lote.begin()
        sombreadorFinal!!.setUniformi("u_bloom", 1)
        sombreadorFinal!!.setUniformf("u_pixel", 1f / largura, 1f / altura)
        sombreadorFinal!!.setUniformf("u_forcaBloom", Constantes.BLOOM_FORCA)
        sombreadorFinal!!.setUniformf("u_vinheta", Constantes.VINHETA_FORCA)
        sombreadorFinal!!.setUniformf("u_saturacao", Constantes.COR_SATURACAO)
        sombreadorFinal!!.setUniformf("u_contraste", Constantes.COR_CONTRASTE)
        lote.draw(c.colorBufferTexture, 0f, 0f, largura.toFloat(), altura.toFloat(), 0, 0, c.width, c.height, false, true)
        lote.end()
        lote.shader = null
        Gdx.gl.glEnable(GL20.GL_BLEND)
    }

    private fun liberar() {
        cena?.dispose(); cena = null
        brilhoA?.dispose(); brilhoA = null
        brilhoB?.dispose(); brilhoB = null
    }

    override fun dispose() {
        liberar()
        lote.dispose()
        sombreadorBrilho?.dispose()
        sombreadorBorrar?.dispose()
        sombreadorFinal?.dispose()
    }

    private companion object {
        const val VERTICE = """
attribute vec4 a_position;
attribute vec4 a_color;
attribute vec2 a_texCoord0;
uniform mat4 u_projTrans;
varying vec2 v_texCoords;
void main() {
    v_texCoords = a_texCoord0;
    gl_Position = u_projTrans * a_position;
}
"""
        const val FRAG_BRILHO = """
#ifdef GL_ES
precision mediump float;
#endif
varying vec2 v_texCoords;
uniform sampler2D u_texture;
uniform float u_limiar;
void main() {
    vec3 c = texture2D(u_texture, v_texCoords).rgb;
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    float k = smoothstep(u_limiar, u_limiar + 0.25, l);
    gl_FragColor = vec4(c * k, 1.0);
}
"""
        const val FRAG_BORRAR = """
#ifdef GL_ES
precision mediump float;
#endif
varying vec2 v_texCoords;
uniform sampler2D u_texture;
uniform vec2 u_direcao;
void main() {
    vec3 s = texture2D(u_texture, v_texCoords).rgb * 0.2270270270;
    s += texture2D(u_texture, v_texCoords + u_direcao * 1.3846153846).rgb * 0.3162162162;
    s += texture2D(u_texture, v_texCoords - u_direcao * 1.3846153846).rgb * 0.3162162162;
    s += texture2D(u_texture, v_texCoords + u_direcao * 3.2307692308).rgb * 0.0702702703;
    s += texture2D(u_texture, v_texCoords - u_direcao * 3.2307692308).rgb * 0.0702702703;
    gl_FragColor = vec4(s, 1.0);
}
"""
        const val FRAG_FINAL = """
#ifdef GL_ES
precision mediump float;
#endif
varying vec2 v_texCoords;
uniform sampler2D u_texture;
uniform sampler2D u_bloom;
uniform vec2 u_pixel;
uniform float u_forcaBloom;
uniform float u_vinheta;
uniform float u_saturacao;
uniform float u_contraste;

float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }

void main() {
    vec2 uv = v_texCoords;
    // --- FXAA simplificado (so mexe onde ha borda forte) ---
    vec3 rgbNW = texture2D(u_texture, uv + vec2(-1.0, -1.0) * u_pixel).rgb;
    vec3 rgbNE = texture2D(u_texture, uv + vec2(1.0, -1.0) * u_pixel).rgb;
    vec3 rgbSW = texture2D(u_texture, uv + vec2(-1.0, 1.0) * u_pixel).rgb;
    vec3 rgbSE = texture2D(u_texture, uv + vec2(1.0, 1.0) * u_pixel).rgb;
    vec3 rgbM = texture2D(u_texture, uv).rgb;
    float lNW = luma(rgbNW);
    float lNE = luma(rgbNE);
    float lSW = luma(rgbSW);
    float lSE = luma(rgbSE);
    float lM = luma(rgbM);
    float lMin = min(lM, min(min(lNW, lNE), min(lSW, lSE)));
    float lMax = max(lM, max(max(lNW, lNE), max(lSW, lSE)));
    vec2 dir;
    dir.x = -((lNW + lNE) - (lSW + lSE));
    dir.y = ((lNW + lSW) - (lNE + lSE));
    float reduz = max((lNW + lNE + lSW + lSE) * 0.03125, 0.0078125);
    float rcp = 1.0 / (min(abs(dir.x), abs(dir.y)) + reduz);
    dir = clamp(dir * rcp, vec2(-6.0), vec2(6.0)) * u_pixel;
    vec3 a = 0.5 * (texture2D(u_texture, uv + dir * (1.0 / 3.0 - 0.5)).rgb + texture2D(u_texture, uv + dir * (2.0 / 3.0 - 0.5)).rgb);
    vec3 b = a * 0.5 + 0.25 * (texture2D(u_texture, uv + dir * -0.5).rgb + texture2D(u_texture, uv + dir * 0.5).rgb);
    float lB = luma(b);
    vec3 c = (lB < lMin || lB > lMax) ? a : b;
    // --- bloom ---
    c += texture2D(u_bloom, uv).rgb * u_forcaBloom;
    // --- cor: contraste, saturacao, tom quente ---
    c = (c - 0.5) * u_contraste + 0.5;
    float l = luma(c);
    c = mix(vec3(l), c, u_saturacao);
    c *= vec3(1.03, 1.0, 0.95);
    // --- vinheta ---
    float d = distance(uv, vec2(0.5));
    c *= 1.0 - u_vinheta * smoothstep(0.35, 0.85, d);
    gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""
    }
}
