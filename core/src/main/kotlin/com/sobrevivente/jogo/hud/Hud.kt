// core/src/main/kotlin/com/sobrevivente/jogo/hud/Hud.kt
package com.sobrevivente.jogo.hud

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Disposable
import com.sobrevivente.jogo.Constantes
import com.sobrevivente.jogo.entrada.ControlesToque
import com.sobrevivente.jogo.inventario.Item
import com.sobrevivente.jogo.inventario.Maleta
import com.sobrevivente.jogo.inventario.TipoItem
import com.sobrevivente.jogo.mundo.Mundo
import com.sobrevivente.jogo.mundo.TipoArma

/**
 * Reúne tudo que fica sobre o jogo 3D (menos o joystick, que tem classe própria). F10: visual estilo Free Fire
 * (avatar + barra de vida, contadores ZUMBIS/ABATES, minimapa, vinheta vermelha de dor, fileira de 4 slots de arma:
 * 3 armas + faca, munição acima dos slots, CURAR rápido) e maleta estilo Resident Evil (grade 8 x 5, peças de vários tamanhos).
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

    // F10: slots de arma, abates, cura rapida, dor
    private var slotAtivo = 0
    private val slotLiberado = booleanArrayOf(true, false, false, true)
    private var abates = 0
    private var curas = 0
    private var dor = 0f
    private val textoPilula = StringBuilder(24)
    private val vermelhoForte = Color(0.85f, 0.02f, 0.02f, 0f)
    private val vermelhoNada = Color(0.85f, 0.02f, 0.02f, 0f)

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

    /** Slot ativo (0..2 = armas de fogo, 3 = faca) e quais armas ja foram achadas (a faca e a pistola sempre). */
    fun definirArmas(ativo: Int, fuzilLiberado: Boolean, escopetaLiberada: Boolean) {
        slotAtivo = ativo
        slotLiberado[1] = fuzilLiberado
        slotLiberado[2] = escopetaLiberada
    }

    fun definirAbates(valor: Int) {
        abates = valor
    }

    fun definirCuras(valor: Int) {
        curas = valor
    }

    /** 0..1: dor atual do jogador (vinheta vermelha nas bordas). */
    fun definirDor(valor: Float) {
        dor = valor
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

        // Munição: centrada, logo acima da fileira de slots de arma
        municaoLargura = Constantes.MUNICAO_LARGURA_DP * 1.4f * densidadeTela
        municaoX = largura * 0.5f - municaoLargura * 0.5f
        municaoY = controles.acaoY(ControlesToque.ACAO_ARMA0) + controles.acaoRaio(ControlesToque.ACAO_ARMA0) +
            16f * densidadeTela

        debugX = Constantes.MARGEM_SEGURA_DP * densidadeTela
        debugYTopo = altura - (Constantes.MARGEM_SEGURA_DP + Constantes.HUD_AVATAR_DP) * densidadeTela -
            Constantes.DEBUG_TEXTO_DESLOCAMENTO_DP * densidadeTela
    }

    /** Chamar entre forma.begin(Filled) e forma.end(), com blending ligado. */
    fun desenharFormas(forma: ShapeRenderer) {
        desenharVinhetaDor(forma)
        minimapa.desenhar(forma, jogadorX, jogadorZ, mundo)
        barraVida.desenharFormas(forma)
        desenharAvatar(forma)
        desenharPilulas(forma)
        desenharMira(forma)
        desenharMarcadorAcerto(forma)
        botoes.desenharFormas(forma)
        desenharSlotsArma(forma)
        if (pausado) {
            forma.setColor(0f, 0f, 0f, Constantes.PAUSA_ALFA_FUNDO)
            forma.rect(0f, 0f, largura, altura)
        } else if (morto) {
            desenharFormasMorte(forma)
        } else if (maletaAberta) {
            desenharFormasMaleta(forma)
        }
    }

    // ---------- F10: HUD estilo Free Fire ----------

    /** Bordas vermelhas que pulsam quando o jogador leva um golpe (alfa = dor). */
    private fun desenharVinhetaDor(forma: ShapeRenderer) {
        if (dor <= 0.02f) return
        val a = minOf(0.55f, dor * 0.5f)
        val t = minOf(largura, altura) * 0.22f
        vermelhoForte.a = a
        vermelhoNada.a = 0f
        forma.rect(0f, 0f, t, altura, vermelhoForte, vermelhoNada, vermelhoNada, vermelhoForte)
        forma.rect(largura - t, 0f, t, altura, vermelhoNada, vermelhoForte, vermelhoForte, vermelhoNada)
        forma.rect(0f, 0f, largura, t, vermelhoForte, vermelhoForte, vermelhoNada, vermelhoNada)
        forma.rect(0f, altura - t, largura, t, vermelhoNada, vermelhoNada, vermelhoForte, vermelhoForte)
    }

    /** Medalhao do personagem ao lado da barra de vida (cabeca + ombros sobre disco escuro, aro claro). */
    private fun desenharAvatar(forma: ShapeRenderer) {
        val r = Constantes.HUD_AVATAR_DP * 0.5f * densidade
        val cx = Constantes.MARGEM_SEGURA_DP * densidade + r
        val cy = altura - Constantes.MARGEM_SEGURA_DP * densidade - r
        forma.setColor(1f, 1f, 1f, 0.75f)
        forma.circle(cx, cy, r * 1.06f, 32)
        forma.setColor(0.10f, 0.12f, 0.16f, 0.95f)
        forma.circle(cx, cy, r, 32)
        forma.setColor(0.45f, 0.30f, 0.20f, 1f) // capuz/capa
        forma.circle(cx, cy - r * 0.78f, r * 0.80f, 24)
        forma.setColor(0.86f, 0.70f, 0.58f, 1f) // rosto
        forma.circle(cx, cy + r * 0.12f, r * 0.34f, 20)
        forma.setColor(0.25f, 0.17f, 0.12f, 1f) // capuz em volta do rosto
        forma.circle(cx, cy + r * 0.30f, r * 0.27f, 20)
    }

    /** Pilula escura com ponta redonda (largura w, altura h) em (x, y) = canto inferior esquerdo. */
    private fun pilula(forma: ShapeRenderer, x: Float, y: Float, w: Float, h: Float, alfa: Float) {
        forma.setColor(0f, 0f, 0f, alfa)
        forma.rect(x + h * 0.5f, y, w - h, h)
        forma.circle(x + h * 0.5f, y + h * 0.5f, h * 0.5f, 16)
        forma.circle(x + w - h * 0.5f, y + h * 0.5f, h * 0.5f, 16)
    }

    private fun pilulaX(): Float =
        largura - (Constantes.MARGEM_SEGURA_DP + Constantes.MINIMAPA_LADO_DP + 10f + Constantes.HUD_PILULA_LARGURA_DP) * densidade

    private fun pilulaY(linha: Int): Float =
        altura - (Constantes.MARGEM_SEGURA_DP + Constantes.HUD_PILULA_ALTURA_DP + linha * (Constantes.HUD_PILULA_ALTURA_DP + 6f)) * densidade

    /** Contadores no topo, a esquerda do minimapa: ZUMBIS vivos e ABATES. */
    private fun desenharPilulas(forma: ShapeRenderer) {
        val w = Constantes.HUD_PILULA_LARGURA_DP * densidade
        val h = Constantes.HUD_PILULA_ALTURA_DP * densidade
        pilula(forma, pilulaX(), pilulaY(0), w, h, 0.55f)
        pilula(forma, pilulaX(), pilulaY(1), w, h, 0.55f)
        forma.setColor(0.90f, 0.25f, 0.22f, 1f) // bolinha: zumbis
        forma.circle(pilulaX() + h * 0.5f, pilulaY(0) + h * 0.5f, h * 0.22f, 12)
        forma.setColor(1.00f, 0.80f, 0.20f, 1f) // bolinha: abates
        forma.circle(pilulaX() + h * 0.5f, pilulaY(1) + h * 0.5f, h * 0.22f, 12)
    }

    /** Silhueta simples da arma k (0 pistola, 1 fuzil, 2 escopeta, 3 faca) dentro de uma caixa meia-largura hw, meia-altura hh. */
    private fun desenharIconeArma(forma: ShapeRenderer, k: Int, cx: Float, cy: Float, hw: Float, hh: Float) {
        when (k) {
            0 -> { // pistola
                forma.rect(cx - hw * 0.8f, cy + hh * 0.05f, hw * 1.5f, hh * 0.42f)
                forma.rect(cx - hw * 0.55f, cy - hh * 0.7f, hw * 0.4f, hh * 0.8f)
                forma.rect(cx - hw * 0.1f, cy - hh * 0.15f, hw * 0.35f, hh * 0.22f)
            }
            1 -> { // fuzil
                forma.rect(cx - hw * 0.95f, cy - hh * 0.05f, hw * 1.9f, hh * 0.28f)
                forma.rect(cx - hw * 1.0f, cy - hh * 0.35f, hw * 0.45f, hh * 0.55f)
                forma.rect(cx - hw * 0.1f, cy - hh * 0.65f, hw * 0.28f, hh * 0.65f)
                forma.rect(cx + hw * 0.55f, cy + hh * 0.18f, hw * 0.4f, hh * 0.12f)
            }
            2 -> { // escopeta
                forma.rect(cx - hw * 0.95f, cy + hh * 0.02f, hw * 1.9f, hh * 0.22f)
                forma.rect(cx - hw * 0.95f, cy - hh * 0.25f, hw * 1.3f, hh * 0.16f)
                forma.rect(cx - hw * 1.0f, cy - hh * 0.45f, hw * 0.5f, hh * 0.65f)
                forma.rect(cx + hw * 0.0f, cy - hh * 0.30f, hw * 0.5f, hh * 0.22f)
            }
            else -> { // faca
                forma.rectLine(cx - hw * 0.15f, cy - hh * 0.7f, cx + hw * 0.55f, cy + hh * 0.7f, hh * 0.32f)
                forma.rectLine(cx - hw * 0.55f, cy - hh * 0.9f, cx - hw * 0.25f, cy - hh * 0.35f, hh * 0.3f)
            }
        }
    }

    /** Fileira de 4 slots (3 armas + faca), estilo Free Fire: o ativo tem moldura amarela; os nao achados ficam apagados. */
    private fun desenharSlotsArma(forma: ShapeRenderer) {
        val borda = 2f * densidade
        for (k in 0..3) {
            val i = ControlesToque.ACAO_ARMA0 + k
            val pressionado = controles.acaoPressionada(i)
            val r = controles.acaoRaio(i) * (if (pressionado) Constantes.BOTAO_ESCALA_PRESSIONADO else 1f)
            val x = controles.acaoX(i)
            val y = controles.acaoY(i)
            val ativo = k == slotAtivo
            val ok = slotLiberado[k]
            if (ativo) {
                forma.setColor(1f, 0.82f, 0.20f, 0.95f)
            } else {
                forma.setColor(1f, 1f, 1f, if (ok) 0.55f else 0.22f)
            }
            forma.rect(x - r, y - r, 2f * r, 2f * r)
            forma.setColor(0.08f, 0.09f, 0.12f, if (ok) 0.82f else 0.60f)
            forma.rect(x - r + borda, y - r + borda, 2f * (r - borda), 2f * (r - borda))
            if (ativo) {
                forma.setColor(1f, 0.82f, 0.20f, 0.16f)
                forma.rect(x - r + borda, y - r + borda, 2f * (r - borda), 2f * (r - borda))
            }
            forma.setColor(1f, 1f, 1f, if (ok) 0.95f else 0.25f)
            desenharIconeArma(forma, k, x, y, r * 0.72f, r * 0.62f)
        }
        // CURAR rapido: contador "xN" (texto) e o botao sao desenhados em BotoesAcao
    }

    // ---------- F10: maleta estilo Resident Evil ----------

    private fun corItem(item: Item, escurecer: Float) {
        when (item.tipo) {
            TipoItem.CURA -> forma3(0.16f, 0.52f, 0.26f, escurecer)
            TipoItem.MUNICAO -> forma3(0.62f, 0.48f, 0.16f, escurecer)
            TipoItem.ARMA -> forma3(0.27f, 0.31f, 0.28f, escurecer)
            TipoItem.FACA -> forma3(0.33f, 0.34f, 0.38f, escurecer)
        }
    }

    private var formaAtual: ShapeRenderer? = null

    private fun forma3(r: Float, g: Float, b: Float, e: Float) {
        formaAtual?.setColor(r * e, g * e, b * e, 1f)
    }

    /** Veu, maleta de couro verde com cantos de latao, grade 8 x 5 e as pecas (armas 2x2/4x2, faca 1x2, municao, cura). */
    private fun desenharFormasMaleta(forma: ShapeRenderer) {
        formaAtual = forma
        forma.setColor(0f, 0f, 0f, Constantes.MALETA_FUNDO_ALFA)
        forma.rect(0f, 0f, largura, altura)

        val lado = controles.slotLado
        val espaco = Constantes.MALETA_SLOT_ESPACO_DP * densidade
        val esq0 = controles.slotEsquerda(0)
        val gradeL = Constantes.MALETA_COLUNAS * lado + (Constantes.MALETA_COLUNAS - 1) * espaco
        val gradeA = controles.gradeAltura
        val base0 = controles.gradeBase
        val pad = 12f * densidade
        // corpo da maleta: latao por fora, couro verde por dentro
        forma.setColor(0.60f, 0.48f, 0.22f, 1f)
        forma.rect(esq0 - pad - 4f * densidade, base0 - pad - 4f * densidade, gradeL + 2f * pad + 8f * densidade, gradeA + 2f * pad + 8f * densidade)
        forma.setColor(0.09f, 0.13f, 0.08f, 1f)
        forma.rect(esq0 - pad, base0 - pad, gradeL + 2f * pad, gradeA + 2f * pad)
        // fechos de latao nos cantos
        forma.setColor(0.80f, 0.68f, 0.30f, 1f)
        val c = 9f * densidade
        forma.rect(esq0 - pad - 4f * densidade, base0 + gradeA + pad - c + 4f * densidade, c, c)
        forma.rect(esq0 + gradeL + pad - c + 4f * densidade, base0 + gradeA + pad - c + 4f * densidade, c, c)
        forma.rect(esq0 - pad - 4f * densidade, base0 - pad - 4f * densidade, c, c)
        forma.rect(esq0 + gradeL + pad - c + 4f * densidade, base0 - pad - 4f * densidade, c, c)

        // celulas
        for (i in 0 until Constantes.MALETA_SLOTS) {
            forma.setColor(0.17f, 0.24f, 0.15f, 1f)
            forma.rect(controles.slotEsquerda(i), controles.slotBase(i), lado, lado)
            forma.setColor(0.05f, 0.08f, 0.05f, 1f)
            forma.rect(controles.slotEsquerda(i) + densidade, controles.slotBase(i) + densidade, lado - 2f * densidade, lado - 2f * densidade)
        }

        // pecas
        val apertada = maleta.itemNaCelula(controles.slotApertado)
        for (n in 0 until maleta.itens.size) {
            val item = maleta.itens[n]
            val cel = item.col + item.lin * Constantes.MALETA_COLUNAS
            val x = controles.slotEsquerda(cel)
            val yTopo = controles.slotBase(cel) + lado
            val w = item.larg * lado + (item.larg - 1) * espaco
            val h = item.alt * lado + (item.alt - 1) * espaco
            val y = yTopo - h
            val escurecer = if (apertada === item) Constantes.BOTAO_ESCURECER else 1f
            val equipada = (item.tipo == TipoItem.ARMA && item.arma != null && !facaEquipada() && item.arma.ordinal == slotAtivo) ||
                (item.tipo == TipoItem.FACA && facaEquipada())
            forma.setColor(if (equipada) 1f else 0.75f, if (equipada) 0.82f else 0.80f, if (equipada) 0.20f else 0.62f, 1f)
            forma.rect(x, y, w, h)
            corItem(item, escurecer)
            forma.rect(x + 2f * densidade, y + 2f * densidade, w - 4f * densidade, h - 4f * densidade)
            forma.setColor(1f, 1f, 1f, 0.85f)
            when (item.tipo) {
                TipoItem.ARMA -> desenharIconeArma(forma, item.arma!!.ordinal, x + w * 0.5f, y + h * 0.58f, w * 0.40f, h * 0.30f)
                TipoItem.FACA -> desenharIconeArma(forma, 3, x + w * 0.5f, y + h * 0.58f, w * 0.40f, h * 0.30f)
                TipoItem.MUNICAO -> {
                    val bw = w * 0.14f
                    for (k in -1..1) {
                        forma.rect(x + w * 0.5f + k * bw * 1.6f - bw * 0.5f, y + h * 0.40f, bw, h * 0.34f)
                    }
                }
                TipoItem.CURA -> {
                    forma.rect(x + w * 0.5f - w * 0.09f, y + h * 0.36f, w * 0.18f, h * 0.40f)
                    forma.rect(x + w * 0.5f - w * 0.20f, y + h * 0.50f - h * 0.09f, w * 0.40f, h * 0.18f)
                }
            }
        }
    }

    private fun facaEquipada(): Boolean = slotAtivo == 3

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
        if (maletaAberta) {
            desenharTextosMaleta(lote)
        } else {
            desenharMunicao(lote)
            desenharTextosPilulas(lote)
            botoes.desenharTextos(lote)
            desenharContadorCura(lote)
        }
        desenharMensagem(lote)
    }

    /** Textos das duas pilulas do topo: ZUMBIS e ABATES. */
    private fun desenharTextosPilulas(lote: SpriteBatch) {
        definirEscala(Constantes.FONTE_ESCALA_HUD * 0.85f)
        val w = Constantes.HUD_PILULA_LARGURA_DP * densidade
        val h = Constantes.HUD_PILULA_ALTURA_DP * densidade
        textoPilula.setLength(0)
        textoPilula.append("ZUMBIS ").append(inimigosVivos)
        fonte.draw(lote, textoPilula, pilulaX() + h * 0.9f, pilulaY(0) + h * 0.5f + fonte.capHeight * 0.5f, w - h * 1.1f, Align.left, false)
        textoPilula.setLength(0)
        textoPilula.append("ABATES ").append(abates)
        fonte.draw(lote, textoPilula, pilulaX() + h * 0.9f, pilulaY(1) + h * 0.5f + fonte.capHeight * 0.5f, w - h * 1.1f, Align.left, false)
    }

    /** "xN" ao lado do botao CURAR (quantas curas ha na maleta). */
    private fun desenharContadorCura(lote: SpriteBatch) {
        definirEscala(Constantes.FONTE_ESCALA_HUD * 0.9f)
        textoSlot.setLength(0)
        textoSlot.append('x').append(curas)
        val i = ControlesToque.ACAO_CURAR
        val r = controles.acaoRaio(i)
        fonte.draw(lote, textoSlot, controles.acaoX(i) - r, controles.acaoY(i) + r + fonte.capHeight + 4f * densidade, 2f * r, Align.center, false)
    }

    /** Rotulo de cada peca (nome/quantidade), titulo e dica de uso. */
    private fun desenharTextosMaleta(lote: SpriteBatch) {
        val lado = controles.slotLado
        val espaco = Constantes.MALETA_SLOT_ESPACO_DP * densidade
        definirEscala(Constantes.FONTE_ESCALA_HUD * 0.62f)
        for (n in 0 until maleta.itens.size) {
            val item = maleta.itens[n]
            val cel = item.col + item.lin * Constantes.MALETA_COLUNAS
            val x = controles.slotEsquerda(cel)
            val yBase = controles.slotBase(cel) + lado - item.alt * lado - (item.alt - 1) * espaco
            val w = item.larg * lado + (item.larg - 1) * espaco
            textoSlot.setLength(0)
            when (item.tipo) {
                TipoItem.CURA -> textoSlot.append(Constantes.ROTULO_CURA)
                TipoItem.MUNICAO -> textoSlot.append(item.quantidade)
                TipoItem.ARMA -> textoSlot.append(item.arma!!.nome)
                TipoItem.FACA -> textoSlot.append(Constantes.ROTULO_FACA)
            }
            fonte.draw(lote, textoSlot, x, yBase + fonte.capHeight + 4f * densidade, w, Align.center, false)
        }

        val gradeTopo = controles.gradeBase + controles.gradeAltura
        definirEscala(Constantes.MALETA_ESCALA_TITULO)
        fonte.draw(
            lote,
            Constantes.TEXTO_MALETA,
            0f,
            gradeTopo + (Constantes.MALETA_TITULO_ESPACO_DP + 12f) * densidade + fonte.capHeight,
            largura,
            Align.center,
            false
        )

        definirEscala(Constantes.FONTE_ESCALA_HUD)
        fonte.draw(
            lote,
            Constantes.TEXTO_MALETA_DICA,
            0f,
            controles.gradeBase - (Constantes.MALETA_DICA_ESPACO_DP + 12f) * densidade,
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
        definirEscala(Constantes.FONTE_ESCALA_HUD)
        textoMunicao.setLength(0)
        textoMunicao.append(nomeDaArma)
        if (carregador >= 0) {
            textoMunicao.append("   ")
            if (recarregando) {
                textoMunicao.append("Recarregando...")
            } else {
                textoMunicao.append(carregador).append(" / ").append(reserva)
            }
        }
        fonte.draw(lote, textoMunicao, municaoX, municaoY + fonte.capHeight * 0.5f, municaoLargura, Align.center, false)
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
