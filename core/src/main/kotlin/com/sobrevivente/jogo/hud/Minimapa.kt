// core/src/main/kotlin/com/sobrevivente/jogo/hud/Minimapa.kt
package com.sobrevivente.jogo.hud

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.sobrevivente.jogo.Constantes
import com.sobrevivente.jogo.mundo.Mundo

/**
 * Mapa da cidade (estilo Free Fire). Com a imagem `mapa/ravenport.jpg` (fase cidade): o minimapa e uma JANELA da imagem que
 * acompanha o jogador; tocar nele abre o mapa inteiro, com a janela desenhada em cima. Sem a imagem (fases antigas): o
 * minimapa de paredes de sempre. Norte fixo: -Z em cima, +X a direita.
 */
class Minimapa {

    private var x = 0f
    private var y = 0f
    private var lado = 0f
    private var borda = 0f
    private var raioPonto = 0f
    private var pixelsPorMetro = 0f
    private var largTela = 0f
    private var altTela = 0f

    private var textura: Texture? = null
    var aberto = false

    // retangulo do mapa grande (pixels de tela)
    var grandeX = 0f
        private set
    var grandeY = 0f
        private set
    var grandeW = 0f
        private set
    var grandeH = 0f
        private set

    val temImagem: Boolean get() = textura != null
    val minX: Float get() = x
    val minY: Float get() = y
    val minLado: Float get() = lado

    /** Carrega (ou descarta) a imagem conforme a fase. */
    fun configurar(usarImagem: Boolean) {
        textura?.dispose()
        textura = null
        aberto = false
        if (usarImagem) {
            val f = Gdx.files.internal(ARQUIVO)
            if (f.exists()) {
                val t = Texture(f, true)
                t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
                textura = t
            }
        }
    }

    fun dispose() {
        textura?.dispose()
        textura = null
    }

    fun redimensionar(larguraTela: Int, alturaTela: Int, densidade: Float) {
        largTela = larguraTela.toFloat()
        altTela = alturaTela.toFloat()
        lado = Constantes.MINIMAPA_LADO_DP * densidade * (if (textura != null) 1.25f else 1f)
        borda = Constantes.MINIMAPA_BORDA_DP * densidade
        raioPonto = Constantes.MINIMAPA_PONTO_DP * densidade
        pixelsPorMetro = lado / Constantes.CHAO_TAMANHO
        val margem = Constantes.MARGEM_SEGURA_DP * densidade
        x = larguraTela - margem - lado
        y = alturaTela - margem - lado
        // mapa grande: a imagem inteira, centralizada, no maximo 90% da tela
        val proporcao = IMG_W / IMG_H
        var w = larguraTela * 0.90f
        var h = w / proporcao
        if (h > alturaTela * 0.88f) { h = alturaTela * 0.88f; w = h * proporcao }
        grandeW = w; grandeH = h
        grandeX = (larguraTela - w) * 0.5f
        grandeY = (alturaTela - h) * 0.5f
    }

    // ---- mundo -> pixel da imagem
    private fun imgX(mundoX: Float): Float = (mundoX + Constantes.CHAO_TAMANHO * 0.5f) / Constantes.CHAO_TAMANHO * IMG_W
    private fun imgZ(mundoZ: Float): Float = (mundoZ + Constantes.CHAO_TAMANHO * 0.5f) / Constantes.CHAO_TAMANHO * IMG_H

    // janela quadrada (em pixels da imagem) que acompanha o jogador
    private var janX0 = 0f
    private var janY0 = 0f
    private fun atualizarJanela(jx: Float, jz: Float) {
        janX0 = (imgX(jx) - JANELA_PX * 0.5f).coerceIn(0f, IMG_W - JANELA_PX)
        janY0 = (imgZ(jz) - JANELA_PX * 0.5f).coerceIn(0f, IMG_H - JANELA_PX)
    }

    // mundo -> tela (modo janela)
    private fun telaXJan(mx: Float): Float = x + (imgX(mx) - janX0) / JANELA_PX * lado
    private fun telaYJan(mz: Float): Float = y + (1f - (imgZ(mz) - janY0) / JANELA_PX) * lado
    // mundo -> tela (mapa grande)
    private fun telaXGr(mx: Float): Float = grandeX + imgX(mx) / IMG_W * grandeW
    private fun telaYGr(mz: Float): Float = grandeY + (1f - imgZ(mz) / IMG_H) * grandeH

    /** Chamar entre lote.begin() e lote.end(), ANTES das formas do HUD. */
    fun desenharImagem(lote: SpriteBatch, jogadorX: Float, jogadorZ: Float) {
        val t = textura ?: return
        atualizarJanela(jogadorX, jogadorZ)
        lote.setColor(1f, 1f, 1f, 1f)
        if (aberto) {
            lote.draw(t, grandeX, grandeY, grandeW, grandeH, 0, 0, IMG_W.toInt(), IMG_H.toInt(), false, false)
        }
        lote.draw(
            t, x, y, lado, lado,
            janX0.toInt(), janY0.toInt(), JANELA_PX.toInt(), JANELA_PX.toInt(), false, false
        )
    }

    /** Chamar entre forma.begin(Filled) e forma.end(), com blending ligado. */
    fun desenhar(forma: ShapeRenderer, jogadorX: Float, jogadorZ: Float, jogadorYaw: Float, mundo: Mundo) {
        if (textura != null) { desenharComImagem(forma, jogadorX, jogadorZ, jogadorYaw, mundo); return }
        forma.setColor(1f, 1f, 1f, Constantes.MINIMAPA_BORDA_ALFA)
        forma.rect(x, y, lado, lado)

        val fundo = Constantes.MINIMAPA_FUNDO_COR
        forma.setColor(fundo, fundo, fundo, Constantes.MINIMAPA_FUNDO_ALFA)
        forma.rect(x + borda, y + borda, lado - 2f * borda, lado - 2f * borda)

        val parede = Constantes.MINIMAPA_PAREDE_COR
        forma.setColor(parede, parede, parede, Constantes.MINIMAPA_PAREDE_ALFA)
        val caixas = Constantes.PAREDES
        for (i in 0 until caixas.size / 4) {
            val esquerda = x + (caixas[i * 4] + Constantes.CHAO_TAMANHO * 0.5f) * pixelsPorMetro
            val direita = x + (caixas[i * 4 + 2] + Constantes.CHAO_TAMANHO * 0.5f) * pixelsPorMetro
            val baixo = y + (Constantes.CHAO_TAMANHO * 0.5f - caixas[i * 4 + 3]) * pixelsPorMetro
            val cima = y + (Constantes.CHAO_TAMANHO * 0.5f - caixas[i * 4 + 1]) * pixelsPorMetro
            forma.rect(esquerda, baixo, direita - esquerda, cima - baixo)
        }
        forma.setColor(Constantes.MINIMAPA_INIMIGO_COR_R, Constantes.MINIMAPA_INIMIGO_COR_G, Constantes.MINIMAPA_INIMIGO_COR_B, 1f)
        for (i in mundo.inimigos.indices) {
            val inimigo = mundo.inimigos[i]
            if (!inimigo.vivo) continue
            forma.circle(
                x + (inimigo.posicao.x + Constantes.CHAO_TAMANHO * 0.5f) * pixelsPorMetro,
                y + (Constantes.CHAO_TAMANHO * 0.5f - inimigo.posicao.z) * pixelsPorMetro, raioPonto, Constantes.MINIMAPA_SEGMENTOS
            )
        }
        forma.setColor(1f, 1f, 1f, 1f)
        forma.circle(
            x + (jogadorX + Constantes.CHAO_TAMANHO * 0.5f) * pixelsPorMetro,
            y + (Constantes.CHAO_TAMANHO * 0.5f - jogadorZ) * pixelsPorMetro, raioPonto, Constantes.MINIMAPA_SEGMENTOS
        )
    }

    private fun moldura(forma: ShapeRenderer, rx: Float, ry: Float, rw: Float, rh: Float, e: Float) {
        forma.rect(rx, ry, rw, e); forma.rect(rx, ry + rh - e, rw, e)
        forma.rect(rx, ry, e, rh); forma.rect(rx + rw - e, ry, e, rh)
    }

    /** Seta do jogador apontando para onde ele olha (yaw 0 = -Z = cima na tela). */
    private fun seta(forma: ShapeRenderer, cx: Float, cy: Float, r: Float, yaw: Float) {
        val a = yaw * 0.0174532925f
        val fx = -kotlin.math.sin(a); val fy = kotlin.math.cos(a)
        val px = -fy; val py = fx
        forma.triangle(
            cx + fx * r * 1.7f, cy + fy * r * 1.7f,
            cx - fx * r + px * r * 1.1f, cy - fy * r + py * r * 1.1f,
            cx - fx * r - px * r * 1.1f, cy - fy * r - py * r * 1.1f
        )
    }

    private fun desenharComImagem(forma: ShapeRenderer, jx: Float, jz: Float, yaw: Float, mundo: Mundo) {
        // moldura do minimapa
        forma.setColor(1f, 1f, 1f, 0.85f)
        moldura(forma, x - borda, y - borda, lado + 2f * borda, lado + 2f * borda, borda * 1.5f)
        // inimigos proximos (radar) dentro da janela
        forma.setColor(Constantes.MINIMAPA_INIMIGO_COR_R, Constantes.MINIMAPA_INIMIGO_COR_G, Constantes.MINIMAPA_INIMIGO_COR_B, 1f)
        for (i in mundo.inimigos.indices) {
            val e = mundo.inimigos[i]
            if (!e.vivo) continue
            val ex = telaXJan(e.posicao.x); val ey = telaYJan(e.posicao.z)
            if (ex < x || ex > x + lado || ey < y || ey > y + lado) continue
            forma.circle(ex, ey, raioPonto * 0.8f, 10)
        }
        forma.setColor(0.2f, 0.8f, 1f, 1f)
        seta(forma, telaXJan(jx), telaYJan(jz), raioPonto, yaw)
        if (!aberto) return
        // ---- mapa grande ----
        forma.setColor(1f, 1f, 1f, 0.95f)
        moldura(forma, grandeX - borda, grandeY - borda, grandeW + 2f * borda, grandeH + 2f * borda, borda * 1.5f)
        // a "telinha" (janela do minimapa) sobre o mapa
        val jw = JANELA_PX / IMG_W * grandeW
        val jh = JANELA_PX / IMG_H * grandeH
        val jxp = grandeX + janX0 / IMG_W * grandeW
        val jyp = grandeY + (1f - (janY0 + JANELA_PX) / IMG_H) * grandeH
        forma.setColor(1f, 0.85f, 0.15f, 1f)
        moldura(forma, jxp, jyp, jw, jh, borda * 1.2f)
        forma.setColor(Constantes.MINIMAPA_INIMIGO_COR_R, Constantes.MINIMAPA_INIMIGO_COR_G, Constantes.MINIMAPA_INIMIGO_COR_B, 0.9f)
        for (i in mundo.inimigos.indices) {
            val e = mundo.inimigos[i]
            if (!e.vivo) continue
            forma.circle(telaXGr(e.posicao.x), telaYGr(e.posicao.z), raioPonto * 0.7f, 8)
        }
        forma.setColor(0.2f, 0.8f, 1f, 1f)
        seta(forma, telaXGr(jx), telaYGr(jz), raioPonto * 1.3f, yaw)
    }

    companion object {
        const val ARQUIVO = "mapa/ravenport.jpg"
        const val IMG_W = 1408f
        const val IMG_H = 768f
        const val JANELA_PX = 300f
    }
}
