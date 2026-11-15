// core/src/main/kotlin/com/sobrevivente/jogo/hud/Hud.kt
package com.sobrevivente.jogo.hud

import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Disposable
import com.sobrevivente.jogo.Constantes
import com.sobrevivente.jogo.entrada.ControlesToque

/**
 * Marco 1.3: reúne tudo que fica sobre o jogo 3D (menos o joystick, que tem classe própria):
 * barra de vida, minimapa placeholder, mira, 3 botões, overlay de debug e tela de pausa.
 * Só desenha; não decide nada de jogo.
 */
class Hud(controles: ControlesToque) : Disposable {

    private val fonte = BitmapFont()
    private val barraVida = BarraVida()
    private val minimapa = Minimapa()
    private val botoes = BotoesAcao(controles, fonte)
    private val overlayDebug = OverlayDebug()

    var pausado = false
    var inimigosVivos = 0

    private var largura = 1f
    private var altura = 1f
    private var densidade = 1f

    // Mira (calculada no resize)
    private var miraComprimento = 0f
    private var miraEspessura = 0f
    private var miraFolga = 0f

    // Posição do texto de debug (logo abaixo da barra de vida)
    private var debugX = 0f
    private var debugYTopo = 0f

    fun definirVida(vida: Int, vidaMaxima: Int) {
        barraVida.vida = vida
        barraVida.vidaMaxima = vidaMaxima
    }

    fun alternarDebug() {
        overlayDebug.alternar()
    }

    fun redimensionar(larguraPx: Int, alturaPx: Int, densidadeTela: Float) {
        largura = larguraPx.toFloat()
        altura = alturaPx.toFloat()
        densidade = densidadeTela

        barraVida.redimensionar(larguraPx, alturaPx, densidadeTela)
        minimapa.redimensionar(larguraPx, alturaPx, densidadeTela)
        botoes.redimensionar(densidadeTela)

        miraComprimento = Constantes.MIRA_COMPRIMENTO_DP * densidadeTela
        miraEspessura = Constantes.MIRA_ESPESSURA_DP * densidadeTela
        miraFolga = Constantes.MIRA_FOLGA_DP * densidadeTela

        debugX = Constantes.MARGEM_SEGURA_DP * densidadeTela
        debugYTopo = barraVida.baseY() - Constantes.DEBUG_TEXTO_DESLOCAMENTO_DP * densidadeTela
    }

    /** Chamar entre forma.begin(Filled) e forma.end(), com blending ligado. */
    fun desenharFormas(forma: ShapeRenderer) {
        minimapa.desenhar(forma)
        barraVida.desenharFormas(forma)
        desenharMira(forma)
        botoes.desenharFormas(forma)
        if (pausado) {
            forma.setColor(0f, 0f, 0f, Constantes.PAUSA_ALFA_FUNDO)
            forma.rect(0f, 0f, largura, altura)
        }
    }

    /** Chamar entre lote.begin() e lote.end(). */
    fun desenharTextos(lote: SpriteBatch, fps: Int) {
        if (pausado) {
            desenharTextoPausa(lote)
            return
        }
        definirEscala(Constantes.FONTE_ESCALA_HUD)
        barraVida.desenharTexto(lote, fonte)
        overlayDebug.desenhar(lote, fonte, fps, inimigosVivos, debugX, debugYTopo)
        botoes.desenharTextos(lote)
    }

    private fun desenharMira(forma: ShapeRenderer) {
        val cx = largura * 0.5f
        val cy = altura * 0.5f
        val meia = miraEspessura * 0.5f
        forma.setColor(1f, 1f, 1f, Constantes.MIRA_ALFA)
        forma.rect(cx + miraFolga, cy - meia, miraComprimento, miraEspessura)
        forma.rect(cx - miraFolga - miraComprimento, cy - meia, miraComprimento, miraEspessura)
        forma.rect(cx - meia, cy + miraFolga, miraEspessura, miraComprimento)
        forma.rect(cx - meia, cy - miraFolga - miraComprimento, miraEspessura, miraComprimento)
    }

    private fun desenharTextoPausa(lote: SpriteBatch) {
        definirEscala(Constantes.ESCALA_FONTE)
        val meioY = altura * 0.5f
        fonte.draw(lote, "PAUSADO", 0f, meioY + fonte.capHeight, largura, Align.center, false)

        definirEscala(Constantes.FONTE_ESCALA_HUD)
        val espaco = Constantes.PAUSA_ESPACO_DP * densidade
        fonte.draw(
            lote,
            "Aperte VOLTAR para continuar",
            0f,
            meioY - espaco,
            largura,
            Align.center,
            false
        )
    }

    private fun definirEscala(fator: Float) {
        fonte.data.setScale(fator * densidade)
    }

    override fun dispose() {
        fonte.dispose()
    }
}
