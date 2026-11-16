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
 * barra de vida, minimapa placeholder, mira, marcador de acerto, munição, 3 botões,
 * overlay de debug, tela de pausa e tela de morte (Marco 1.6).
 * Só desenha; não decide nada de jogo.
 */
class Hud(private val controles: ControlesToque) : Disposable {

    private val fonte = BitmapFont()
    private val barraVida = BarraVida()
    private val minimapa = Minimapa()
    private val botoes = BotoesAcao(controles, fonte)
    private val overlayDebug = OverlayDebug()

    var pausado = false
    var morto = false
    var inimigosVivos = 0

    // Munição (Marco 1.4)
    private var carregador = 0
    private var reserva = 0
    private var recarregando = false
    private val textoMunicao = StringBuilder(24) // reutilizado: zero alocação por frame
    private var municaoX = 0f
    private var municaoY = 0f
    private var municaoLargura = 0f

    // Marcador de acerto na mira
    private var tempoAcerto = 0f
    private var acertoFolga = 0f
    private var acertoComprimento = 0f
    private var acertoEspessura = 0f

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

    fun definirMunicao(carregadorAtual: Int, reservaAtual: Int, emRecarga: Boolean) {
        carregador = carregadorAtual
        reserva = reservaAtual
        recarregando = emRecarga
    }

    fun mostrarAcerto() {
        tempoAcerto = Constantes.ACERTO_DURACAO_S
    }

    /** Chamar todo frame com o jogo rodando. */
    fun atualizar(dt: Float) {
        if (tempoAcerto > 0f) tempoAcerto -= dt
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

        acertoFolga = Constantes.ACERTO_FOLGA_DP * densidadeTela
        acertoComprimento = Constantes.ACERTO_COMPRIMENTO_DP * densidadeTela
        acertoEspessura = Constantes.ACERTO_ESPESSURA_DP * densidadeTela

        // Munição: à esquerda do botão RECARREGAR, alinhada à direita e centrada na altura dele
        municaoLargura = Constantes.MUNICAO_LARGURA_DP * densidadeTela
        val direita = controles.acaoX(ControlesToque.ACAO_RECARREGAR) -
            controles.acaoRaio(ControlesToque.ACAO_RECARREGAR) -
            Constantes.MUNICAO_ESPACO_DP * densidadeTela
        municaoX = direita - municaoLargura
        municaoY = controles.acaoY(ControlesToque.ACAO_RECARREGAR)

        debugX = Constantes.MARGEM_SEGURA_DP * densidadeTela
        debugYTopo = barraVida.baseY() - Constantes.DEBUG_TEXTO_DESLOCAMENTO_DP * densidadeTela
    }

    /** Chamar entre forma.begin(Filled) e forma.end(), com blending ligado. */
    fun desenharFormas(forma: ShapeRenderer) {
        minimapa.desenhar(forma)
        barraVida.desenharFormas(forma)
        desenharMira(forma)
        desenharMarcadorAcerto(forma)
        botoes.desenharFormas(forma)
        if (pausado) {
            forma.setColor(0f, 0f, 0f, Constantes.PAUSA_ALFA_FUNDO)
            forma.rect(0f, 0f, largura, altura)
        } else if (morto) {
            desenharFormasMorte(forma)
        }
    }

    /** Véu vermelho sobre a tela e o botão REINICIAR (mais escuro enquanto o dedo está nele). */
    private fun desenharFormasMorte(forma: ShapeRenderer) {
        forma.setColor(
            Constantes.MORTE_FUNDO_R,
            Constantes.MORTE_FUNDO_G,
            Constantes.MORTE_FUNDO_B,
            Constantes.MORTE_FUNDO_ALFA
        )
        forma.rect(0f, 0f, largura, altura)

        val escurecer = if (controles.reiniciarApertado) Constantes.BOTAO_ESCURECER else 1f
        forma.setColor(
            Constantes.REINICIAR_COR_R * escurecer,
            Constantes.REINICIAR_COR_G * escurecer,
            Constantes.REINICIAR_COR_B * escurecer,
            1f
        )
        forma.rect(
            controles.reiniciarEsquerda,
            controles.reiniciarBase,
            controles.reiniciarLargura,
            controles.reiniciarAltura
        )
    }

    /** Chamar entre lote.begin() e lote.end(). */
    fun desenharTextos(lote: SpriteBatch, fps: Int) {
        if (pausado) {
            desenharTextoPausa(lote)
            return
        }
        if (morto) {
            desenharTextosMorte(lote)
            return
        }
        definirEscala(Constantes.FONTE_ESCALA_HUD)
        barraVida.desenharTexto(lote, fonte)
        overlayDebug.desenhar(lote, fonte, fps, inimigosVivos, debugX, debugYTopo)
        desenharMunicao(lote)
        botoes.desenharTextos(lote)
    }

    private fun desenharMunicao(lote: SpriteBatch) {
        textoMunicao.setLength(0)
        if (recarregando) {
            textoMunicao.append("Recarregando...")
        } else {
            textoMunicao.append(carregador).append(" / ").append(reserva)
        }
        fonte.draw(
            lote,
            textoMunicao,
            municaoX,
            municaoY + fonte.capHeight * 0.5f,
            municaoLargura,
            Align.right,
            false
        )
    }

    /** X vermelho em volta do centro da mira, por ACERTO_DURACAO_S depois de acertar. */
    private fun desenharMarcadorAcerto(forma: ShapeRenderer) {
        if (tempoAcerto <= 0f) return
        val cx = largura * 0.5f
        val cy = altura * 0.5f
        val f = acertoFolga
        val c = acertoComprimento
        forma.setColor(
            Constantes.ACERTO_COR_R,
            Constantes.ACERTO_COR_G,
            Constantes.ACERTO_COR_B,
            1f
        )
        forma.rectLine(cx + f, cy + f, cx + f + c, cy + f + c, acertoEspessura)
        forma.rectLine(cx - f, cy + f, cx - f - c, cy + f + c, acertoEspessura)
        forma.rectLine(cx + f, cy - f, cx + f + c, cy - f - c, acertoEspessura)
        forma.rectLine(cx - f, cy - f, cx - f - c, cy - f - c, acertoEspessura)
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

    private fun desenharTextosMorte(lote: SpriteBatch) {
        // Título acima do meio da tela; o botão fica logo abaixo (posição vem de ControlesToque).
        definirEscala(Constantes.MORTE_ESCALA_TITULO)
        val meioY = altura * 0.5f
        fonte.draw(
            lote,
            Constantes.TEXTO_MORTE,
            0f,
            meioY + fonte.capHeight,
            largura,
            Align.center,
            false
        )

        definirEscala(Constantes.REINICIAR_ESCALA_TEXTO)
        val centroBotaoY = controles.reiniciarBase + controles.reiniciarAltura * 0.5f
        fonte.draw(
            lote,
            Constantes.TEXTO_REINICIAR,
            controles.reiniciarEsquerda,
            centroBotaoY + fonte.capHeight * 0.5f,
            controles.reiniciarLargura,
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
