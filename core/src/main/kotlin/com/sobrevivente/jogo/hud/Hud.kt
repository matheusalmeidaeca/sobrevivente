// core/src/main/kotlin/com/sobrevivente/jogo/hud/Hud.kt
package com.sobrevivente.jogo.hud

import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Disposable
import com.sobrevivente.jogo.Constantes
import com.sobrevivente.jogo.entrada.ControlesToque
import com.sobrevivente.jogo.inventario.Maleta
import com.sobrevivente.jogo.inventario.TipoItem
import com.sobrevivente.jogo.mundo.Mundo

/**
 * Marco 1.3: reúne tudo que fica sobre o jogo 3D (menos o joystick, que tem classe própria):
 * barra de vida, minimapa placeholder, mira, marcador de acerto, munição, 3 botões,
 * overlay de debug, tela de pausa, tela de morte (Marco 1.6) e grade da maleta (Marco 1.7).
 * Só desenha; não decide nada de jogo.
 */
class Hud(
    private val controles: ControlesToque,
    private val maleta: Maleta,
    private val mundo: Mundo
) : Disposable {

    private val fonte = BitmapFont()
    private val barraVida = BarraVida()
    private val minimapa = Minimapa()
    private val botoes = BotoesAcao(controles, fonte)
    private val overlayDebug = OverlayDebug()

    var pausado = false
    var morto = false
    var maletaAberta = false
    var inimigosVivos = 0
    private var jogadorX = 0f
    private var jogadorZ = 0f

    // Munição (Marco 1.4)
    private var carregador = 0
    private var reserva = 0
    private var recarregando = false
    private var nomeDaArma = ""
    private val textoMunicao = StringBuilder(24) // reutilizado: zero alocação por frame
    private var municaoX = 0f
    private var municaoY = 0f
    private var municaoLargura = 0f

    // Marcador de acerto na mira
    private var tempoAcerto = 0f
    private var acertoFolga = 0f
    private var acertoComprimento = 0f
    private var acertoEspessura = 0f

    // Mensagem curta no centro (ex.: "Maleta cheia"); o texto é sempre uma constante, sem alocação
    private var mensagem: String? = null
    private var tempoMensagem = 0f
    private val textoSlot = StringBuilder(16) // reutilizado: zero alocação por frame

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

    fun definirMunicao(nomeArma: String, carregadorAtual: Int, reservaAtual: Int, emRecarga: Boolean) {
        nomeDaArma = nomeArma
        carregador = carregadorAtual
        reserva = reservaAtual
        recarregando = emRecarga
    }

    /** Posição do jogador no plano XZ (para o minimapa). Chamar todo frame. */
    fun definirJogador(x: Float, z: Float) {
        jogadorX = x
        jogadorZ = z
    }

    fun mostrarAcerto() {
        tempoAcerto = Constantes.ACERTO_DURACAO_S
    }

    fun mostrarMensagem(texto: String) {
        mensagem = texto
        tempoMensagem = Constantes.MENSAGEM_DURACAO_S
    }

    fun limparMensagem() {
        tempoMensagem = 0f
    }

    /** Chamar todo frame (jogo rodando ou maleta aberta). */
    fun atualizar(dt: Float) {
        if (tempoAcerto > 0f) tempoAcerto -= dt
        if (tempoMensagem > 0f) tempoMensagem -= dt
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
        minimapa.desenhar(forma, jogadorX, jogadorZ, mundo)
        barraVida.desenharFormas(forma)
        desenharMira(forma)
        desenharMarcadorAcerto(forma)
        botoes.desenharFormas(forma)
        if (pausado) {
            forma.setColor(0f, 0f, 0f, Constantes.PAUSA_ALFA_FUNDO)
            forma.rect(0f, 0f, largura, altura)
        } else if (morto) {
            desenharFormasMorte(forma)
        } else if (maletaAberta) {
            desenharFormasMaleta(forma)
        }
    }

    /** Véu escuro e a grade 2 x 4. Item: quadrado colorido por tipo; slot vazio: só moldura. */
    private fun desenharFormasMaleta(forma: ShapeRenderer) {
        forma.setColor(0f, 0f, 0f, Constantes.MALETA_FUNDO_ALFA)
        forma.rect(0f, 0f, largura, altura)

        val borda = Constantes.MALETA_SLOT_BORDA_DP * densidade
        val lado = controles.slotLado
        for (i in 0 until Constantes.MALETA_SLOTS) {
            val esquerda = controles.slotEsquerda(i)
            val base = controles.slotBase(i)
            val escurecer = if (controles.slotApertado == i) Constantes.BOTAO_ESCURECER else 1f

            val moldura = Constantes.MALETA_SLOT_BORDA_COR
            forma.setColor(moldura, moldura, moldura, 1f)
            forma.rect(esquerda, base, lado, lado)

            val fundo = Constantes.MALETA_SLOT_FUNDO_COR * escurecer
            forma.setColor(fundo, fundo, fundo, 1f)
            forma.rect(esquerda + borda, base + borda, lado - 2f * borda, lado - 2f * borda)

            val item = maleta.item(i) ?: continue
            val fator = Constantes.MALETA_ITEM_ESCURECER * escurecer
            when (item.tipo) {
                TipoItem.CURA -> forma.setColor(
                    Constantes.ITEM_COR_CURA_R * fator,
                    Constantes.ITEM_COR_CURA_G * fator,
                    Constantes.ITEM_COR_CURA_B * fator,
                    1f
                )
                TipoItem.MUNICAO -> forma.setColor(
                    Constantes.ITEM_COR_MUNICAO_R * fator,
                    Constantes.ITEM_COR_MUNICAO_G * fator,
                    Constantes.ITEM_COR_MUNICAO_B * fator,
                    1f
                )
            }
            forma.rect(esquerda + 2f * borda, base + 2f * borda, lado - 4f * borda, lado - 4f * borda)
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
        if (maletaAberta) desenharTextosMaleta(lote)
        desenharMensagem(lote)
    }

    /** Rótulo de cada item (CURA / MUN 36), título e dica de uso. */
    private fun desenharTextosMaleta(lote: SpriteBatch) {
        definirEscala(Constantes.FONTE_ESCALA_HUD) // os botões acima podem ter mudado a escala da fonte
        val lado = controles.slotLado
        for (i in 0 until Constantes.MALETA_SLOTS) {
            val item = maleta.item(i) ?: continue
            textoSlot.setLength(0)
            when (item.tipo) {
                TipoItem.CURA -> textoSlot.append(Constantes.ROTULO_CURA)
                TipoItem.MUNICAO -> textoSlot.append(Constantes.ROTULO_MUNICAO).append(' ').append(item.quantidade)
            }
            fonte.draw(
                lote,
                textoSlot,
                controles.slotEsquerda(i),
                controles.slotBase(i) + lado * 0.5f + fonte.capHeight * 0.5f,
                lado,
                Align.center,
                false
            )
        }

        val gradeTopo = controles.gradeBase + controles.gradeAltura
        definirEscala(Constantes.MALETA_ESCALA_TITULO)
        fonte.draw(
            lote,
            Constantes.TEXTO_MALETA,
            0f,
            gradeTopo + Constantes.MALETA_TITULO_ESPACO_DP * densidade + fonte.capHeight,
            largura,
            Align.center,
            false
        )

        definirEscala(Constantes.FONTE_ESCALA_HUD)
        fonte.draw(
            lote,
            Constantes.TEXTO_MALETA_DICA,
            0f,
            controles.gradeBase - Constantes.MALETA_DICA_ESPACO_DP * densidade,
            largura,
            Align.center,
            false
        )
    }

    private fun desenharMensagem(lote: SpriteBatch) {
        val texto = mensagem ?: return
        if (tempoMensagem <= 0f) return
        definirEscala(Constantes.MENSAGEM_ESCALA)
        fonte.draw(lote, texto, 0f, altura * Constantes.MENSAGEM_ALTURA, largura, Align.center, false)
    }

    private fun desenharMunicao(lote: SpriteBatch) {
        textoMunicao.setLength(0)
        textoMunicao.append(nomeDaArma).append("  ")
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
        // contorno escuro: a mira continua visivel contra o ceu claro e a nevoa
        val o = miraEspessura * 0.6f
        forma.setColor(0f, 0f, 0f, Constantes.MIRA_ALFA * 0.55f)
        forma.rect(cx + miraFolga - o, cy - meia - o, miraComprimento + 2f * o, miraEspessura + 2f * o)
        forma.rect(cx - miraFolga - miraComprimento - o, cy - meia - o, miraComprimento + 2f * o, miraEspessura + 2f * o)
        forma.rect(cx - meia - o, cy + miraFolga - o, miraEspessura + 2f * o, miraComprimento + 2f * o)
        forma.rect(cx - meia - o, cy - miraFolga - miraComprimento - o, miraEspessura + 2f * o, miraComprimento + 2f * o)
        forma.rect(cx - meia - o, cy - meia - o, miraEspessura + 2f * o, miraEspessura + 2f * o)
        forma.setColor(1f, 1f, 1f, Constantes.MIRA_ALFA)
        forma.rect(cx - meia, cy - meia, miraEspessura, miraEspessura) // ponto central
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
