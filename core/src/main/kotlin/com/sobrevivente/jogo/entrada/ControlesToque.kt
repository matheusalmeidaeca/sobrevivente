// core/src/main/kotlin/com/sobrevivente/jogo/entrada/ControlesToque.kt
package com.sobrevivente.jogo.entrada

import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.utils.TimeUtils
import com.sobrevivente.jogo.Constantes
import kotlin.math.sqrt

/**
 * Multitouch: cada dedo é "dono" de uma função pelo ID do ponteiro.
 * Prioridade no toque: zona de debug > botões de ação > joystick/câmera.
 * Metade esquerda (parte de baixo) = joystick; metade direita (fora dos botões) = girar câmera.
 * Com o jogador morto (Marco 1.6) só o botão REINICIAR aceita toque.
 * Maleta (Marco 1.7): toque curto no botão central abre/fecha; aberta, só slots e botão central respondem.
 * Todas as coordenadas daqui têm origem EMBAIXO-ESQUERDA (Y para cima),
 * já convertidas do Y do toque, que vem de cima (armadilha 5 do mapa).
 */
class ControlesToque : InputAdapter() {

    // Saída do joystick (-1..1). movimentoY positivo = empurrar para cima na tela.
    var movimentoX = 0f
        private set
    var movimentoY = 0f
        private set

    // Geometria do joystick em pixels (usada pelo desenho)
    var joystickAtivo = false
        private set
    var centroX = 0f
        private set
    var centroY = 0f
        private set
    var botaoX = 0f
        private set
    var botaoY = 0f
        private set
    var raioBase = 1f
        private set
    var raioBotao = 1f
        private set

    /** Quando true (pausa), nenhum toque novo é aceito. Use definirBloqueado(). */
    var bloqueado = false
        private set

    /** Quando true (jogador morto), só o botão REINICIAR aceita toque. Use definirMorto(). */
    var morto = false
        private set

    // Botão REINICIAR (retângulo, em pixels; canto inferior esquerdo + tamanho)
    var reiniciarEsquerda = 0f
        private set
    var reiniciarBase = 0f
        private set
    var reiniciarLargura = 1f
        private set
    var reiniciarAltura = 1f
        private set
    private var ponteiroReiniciar = SEM_PONTEIRO
    private var reiniciarPendente = false

    /** True enquanto um dedo está apertando o botão REINICIAR (para o feedback visual). */
    val reiniciarApertado: Boolean
        get() = ponteiroReiniciar != SEM_PONTEIRO

    /** Quando true (maleta aberta), só os slots e o botão central aceitam toque. Use definirModoMaleta(). */
    var maletaAberta = false
        private set

    // Grade da maleta (pixels; canto inferior esquerdo de cada slot)
    var slotLado = 1f
        private set
    var gradeBase = 0f
        private set
    var gradeAltura = 1f
        private set
    private val slotsEsquerda = FloatArray(Constantes.MALETA_SLOTS)
    private val slotsBase = FloatArray(Constantes.MALETA_SLOTS)
    private var ponteiroSlot = SEM_PONTEIRO
    private var usarSlotPendente = SEM_SLOT
    private var alternarMaletaPendente = false

    /** Índice do slot sob o dedo agora (feedback visual), ou -1. */
    var slotApertado = SEM_SLOT
        private set

    // Toque curto no botão central (seção 10.4): começou no botão, < 250 ms, deslocou < 8 dp
    private var ponteiroBotaoMaleta = SEM_PONTEIRO // só usado com a maleta aberta
    private var toqueNoBotao = false
    private var toqueBotaoInicioMs = 0L
    private var toqueBotaoInicioX = 0f
    private var toqueBotaoInicioY = 0f
    private var toqueBotaoDeslocMax = 0f
    private var densidadeTela = 1f

    private var largura = 1f
    private var altura = 1f
    private var ponteiroJoystick = SEM_PONTEIRO
    private var ponteiroCamera = SEM_PONTEIRO
    private var cameraUltimoX = 0f
    private var cameraUltimoY = 0f
    private var giroX = 0f
    private var giroY = 0f

    // Botões de ação (geometria em pixels e dono de cada um)
    private val acoesX = FloatArray(NUM_ACOES)
    private val acoesY = FloatArray(NUM_ACOES)
    private val acoesRaio = FloatArray(NUM_ACOES)
    private val acoesAlvo = FloatArray(NUM_ACOES)
    private val acoesPonteiro = IntArray(NUM_ACOES) { SEM_PONTEIRO }
    private val acoesPressionadas = BooleanArray(NUM_ACOES) // "apertou agora", lido uma vez

    // Menu de pausa (F11): botoes CONTINUAR e MENU PRINCIPAL (retangulos, canto inferior esquerdo + tamanho)
    var pausaLargura = 1f
        private set
    var pausaAltura = 1f
        private set
    private val pausaEsquerda = FloatArray(NUM_PAUSA)
    private val pausaBase = FloatArray(NUM_PAUSA)
    private var ponteiroPausa = SEM_PONTEIRO
    private var pausaApertado = SEM_SLOT
    private var pausaPendente = SEM_SLOT

    fun pausaEsq(i: Int): Float = pausaEsquerda[i]
    fun pausaBas(i: Int): Float = pausaBase[i]
    fun pausaApertadoIndice(): Int = pausaApertado

    /** Botao do menu de pausa tocado (0 = continuar, 1 = menu principal), ou -1. Lido uma unica vez. */
    fun consumirPausa(): Int {
        val v = pausaPendente
        pausaPendente = SEM_SLOT
        return v
    }

    private fun pausaEm(x: Float, y: Float): Int {
        for (i in 0 until NUM_PAUSA) {
            if (x >= pausaEsquerda[i] && x <= pausaEsquerda[i] + pausaLargura &&
                y >= pausaBase[i] && y <= pausaBase[i] + pausaAltura) return i
        }
        return SEM_SLOT
    }

    private fun calcularPausa(densidade: Float) {
        pausaLargura = Constantes.PAUSA_BOTAO_LARGURA_DP * densidade
        pausaAltura = Constantes.PAUSA_BOTAO_ALTURA_DP * densidade
        val folga = Constantes.PAUSA_BOTAO_FOLGA_DP * densidade
        for (i in 0 until NUM_PAUSA) {
            pausaEsquerda[i] = (largura - pausaLargura) * 0.5f
            pausaBase[i] = altura * 0.5f - i * (pausaAltura + folga) - pausaAltura * 0.3f
        }
    }

    // Zona e toque longo do overlay de debug
    private var debugLargura = 0f
    private var debugAltura = 0f
    private var ponteiroDebug = SEM_PONTEIRO
    private var tempoDebug = 0f
    private var debugDisparado = false
    private var alternarDebugPendente = false

    // Botão Voltar do Android
    private var voltarPendente = false

    fun redimensionar(larguraPx: Int, alturaPx: Int, densidade: Float) {
        largura = larguraPx.toFloat()
        altura = alturaPx.toFloat()
        raioBase = Constantes.JOYSTICK_RAIO_BASE_DP * densidade
        raioBotao = Constantes.JOYSTICK_RAIO_BOTAO_DP * densidade
        val margem = (Constantes.MARGEM_SEGURA_DP + Constantes.JOYSTICK_MARGEM_DP) * densidade
        centroX = margem + raioBase
        centroY = margem + raioBase
        calcularAcoes(densidade)
        calcularReiniciar(densidade)
        calcularPausa(densidade)
        densidadeTela = densidade
        calcularMaleta(densidade)
        debugLargura = Constantes.DEBUG_ZONA_LARGURA_DP * densidade
        debugAltura = Constantes.DEBUG_ZONA_ALTURA_DP * densidade
        liberarTudo()
    }

    /**
     * Layout estilo controle de PS2 (F10): losango de 4 botoes a direita (triangulo cima = INTERAGIR, circulo direita = AGACHAR,
     * X baixo = PULAR, quadrado esquerda = RECARREGAR), ATIRAR grande ao lado do losango (R1), um segundo ATIRAR e TROCAR
     * acima do joystick (L2/L1), CURAR rapido e os 4 slots de arma (3 armas + faca) embaixo, no centro.
     */
    private fun calcularAcoes(densidade: Float) {
        val margem = Constantes.MARGEM_SEGURA_DP * densidade
        val raioAtirar = Constantes.BOTAO_ATIRAR_DIAMETRO_DP * 0.5f * densidade
        val raioPequeno = Constantes.BOTAO_PEQUENO_DIAMETRO_DP * 0.5f * densidade
        val folga = Constantes.BOTAO_FOLGA_DP * densidade
        val alvoMinimo = Constantes.BOTAO_ALVO_MINIMO_DP * 0.5f * densidade
        val passo = Constantes.PS2_PASSO_DP * densidade

        // losango (centro fx, fy)
        val fx = largura - margem - passo - raioPequeno
        val fy = margem + passo + raioPequeno
        definirAcao(ACAO_PULAR, fx, fy - passo, raioPequeno, alvoMinimo)       // X
        definirAcao(ACAO_AGACHAR, fx + passo, fy, raioPequeno, alvoMinimo)     // O
        definirAcao(ACAO_RECARREGAR, fx - passo, fy, raioPequeno, alvoMinimo)  // quadrado
        definirAcao(ACAO_INTERAGIR, fx, fy + passo, raioPequeno, alvoMinimo)   // triangulo
        // R1: ATIRAR grande, a esquerda do losango e um pouco acima
        definirAcao(ACAO_ATIRAR, fx - passo - raioPequeno - folga - raioAtirar, fy + passo * 0.55f, raioAtirar, alvoMinimo)

        // lado esquerdo, acima do joystick: segundo ATIRAR (L2) e TROCAR (L1)
        val raioEsq = Constantes.ATIRAR_ESQ_DIAMETRO_DP * 0.5f * densidade
        val topoMax = altura - margem - Constantes.HUD_AVATAR_DP * densidade - 12f * densidade - raioEsq
        val yEsq = minOf(centroY + raioBase + 14f * densidade + raioEsq, topoMax)
        definirAcao(ACAO_ATIRAR_ESQ, margem + raioEsq, yEsq, raioEsq, alvoMinimo)
        definirAcao(ACAO_TROCAR, margem + 2f * raioEsq + folga + raioPequeno, yEsq, raioPequeno, alvoMinimo)

        // slots de arma (3 armas + faca) e CURAR rapido: fileira embaixo, centrada
        val raioSlot = Constantes.SLOT_ARMA_LADO_DP * 0.5f * densidade
        val passoSlot = 2f * raioSlot + Constantes.SLOT_ARMA_ESPACO_DP * densidade
        val ySlot = Constantes.SLOT_ARMA_BASE_DP * densidade + raioSlot
        for (k in 0..3) {
            definirAcao(ACAO_ARMA0 + k, largura * 0.5f + (k - 1.5f) * passoSlot, ySlot, raioSlot, raioSlot)
        }
        // icone do menu (start): topo, centro da tela
        val raioMenu = Constantes.MENU_ICONE_DIAMETRO_DP * 0.5f * densidade
        definirAcao(ACAO_MENU, largura * 0.5f, altura - margem - raioMenu, raioMenu, alvoMinimo)
        val raioCura = Constantes.CURAR_RAPIDO_DIAMETRO_DP * 0.5f * densidade
        definirAcao(ACAO_CURAR, largura * 0.5f - 2.5f * passoSlot, ySlot, raioCura, alvoMinimo)
    }

    /** Botão REINICIAR: centrado na largura, logo abaixo do meio da tela (o título fica acima). */
    private fun calcularReiniciar(densidade: Float) {
        reiniciarLargura = Constantes.REINICIAR_LARGURA_DP * densidade
        reiniciarAltura = Constantes.REINICIAR_ALTURA_DP * densidade
        reiniciarEsquerda = (largura - reiniciarLargura) * 0.5f
        reiniciarBase = altura * 0.5f - Constantes.REINICIAR_ESPACO_DP * densidade - reiniciarAltura
    }

    private fun dentroReiniciar(x: Float, y: Float): Boolean {
        return x >= reiniciarEsquerda && x <= reiniciarEsquerda + reiniciarLargura &&
            y >= reiniciarBase && y <= reiniciarBase + reiniciarAltura
    }

    /** Grade da maleta (8 x 5) centrada na tela; a linha 0 fica em cima. */
    private fun calcularMaleta(densidade: Float) {
        slotLado = Constantes.MALETA_SLOT_LADO_DP * densidade
        val espaco = Constantes.MALETA_SLOT_ESPACO_DP * densidade
        val gradeLargura = Constantes.MALETA_COLUNAS * slotLado + (Constantes.MALETA_COLUNAS - 1) * espaco
        gradeAltura = Constantes.MALETA_LINHAS * slotLado + (Constantes.MALETA_LINHAS - 1) * espaco
        val gradeEsquerda = (largura - gradeLargura) * 0.5f
        val gradeTopo = altura * Constantes.MALETA_CENTRO_ALTURA + gradeAltura * 0.5f
        gradeBase = gradeTopo - gradeAltura
        for (i in 0 until Constantes.MALETA_SLOTS) {
            val coluna = i % Constantes.MALETA_COLUNAS
            val linha = i / Constantes.MALETA_COLUNAS
            slotsEsquerda[i] = gradeEsquerda + coluna * (slotLado + espaco)
            slotsBase[i] = gradeTopo - (linha + 1) * slotLado - linha * espaco
        }
    }

    fun slotEsquerda(indice: Int): Float = slotsEsquerda[indice]
    fun slotBase(indice: Int): Float = slotsBase[indice]

    private fun slotEm(x: Float, y: Float): Int {
        for (i in 0 until Constantes.MALETA_SLOTS) {
            val esquerda = slotsEsquerda[i]
            val base = slotsBase[i]
            if (x >= esquerda && x <= esquerda + slotLado && y >= base && y <= base + slotLado) return i
        }
        return SEM_SLOT
    }

    private fun dentroBotaoCentral(x: Float, y: Float): Boolean {
        val dx = x - centroX
        val dy = y - centroY
        return dx * dx + dy * dy <= raioBotao * raioBotao
    }

    private fun iniciarToqueBotao(x: Float, y: Float) {
        toqueNoBotao = true
        toqueBotaoInicioMs = TimeUtils.millis()
        toqueBotaoInicioX = x
        toqueBotaoInicioY = y
        toqueBotaoDeslocMax = 0f
    }

    private fun acompanharToqueBotao(x: Float, y: Float) {
        if (!toqueNoBotao) return
        val dx = x - toqueBotaoInicioX
        val dy = y - toqueBotaoInicioY
        val distancia = sqrt(dx * dx + dy * dy)
        if (distancia > toqueBotaoDeslocMax) toqueBotaoDeslocMax = distancia
    }

    /** True se o dedo que acabou de soltar fez um toque curto válido no botão central. */
    private fun toqueCurtoValido(): Boolean {
        if (!toqueNoBotao) return false
        val duracao = TimeUtils.millis() - toqueBotaoInicioMs
        return duracao < Constantes.MALETA_TOQUE_CURTO_MS &&
            toqueBotaoDeslocMax < Constantes.MALETA_TOQUE_DESLOC_DP * densidadeTela
    }

    private fun definirAcao(indice: Int, x: Float, y: Float, raio: Float, alvoMinimo: Float) {
        acoesX[indice] = x
        acoesY[indice] = y
        acoesRaio[indice] = raio
        acoesAlvo[indice] = maxOf(raio, alvoMinimo) // alvo de toque nunca menor que 48 dp
    }

    fun acaoX(indice: Int): Float = acoesX[indice]
    fun acaoY(indice: Int): Float = acoesY[indice]
    fun acaoRaio(indice: Int): Float = acoesRaio[indice]
    fun acaoPressionada(indice: Int): Boolean = acoesPonteiro[indice] != SEM_PONTEIRO

    /** True uma única vez a cada novo aperto do botão (para ações de um toque, como recarregar). */
    fun consumirPressao(indice: Int): Boolean {
        val valor = acoesPressionadas[indice]
        acoesPressionadas[indice] = false
        return valor
    }

    /** Pausa: bloqueia toques novos e solta todos os dedos que estavam em uso. */
    fun definirBloqueado(valor: Boolean) {
        bloqueado = valor
        if (valor) liberarTudo()
    }

    /** Morte: solta todos os dedos e deixa só o botão REINICIAR aceitar toque. */
    fun definirMorto(valor: Boolean) {
        morto = valor
        liberarTudo()
        reiniciarPendente = false
    }

    /** True uma única vez depois de soltar o dedo dentro do botão REINICIAR. */
    fun consumirReiniciar(): Boolean {
        val valor = reiniciarPendente
        reiniciarPendente = false
        return valor
    }

    /** Maleta aberta/fechada: solta todos os dedos; aberta, o joystick fica desativado (seção 10.4). */
    fun definirModoMaleta(valor: Boolean) {
        maletaAberta = valor
        liberarTudo()
        alternarMaletaPendente = false
        usarSlotPendente = SEM_SLOT
    }

    /** True uma única vez depois de um toque curto válido no botão central. */
    fun consumirAlternarMaleta(): Boolean {
        val valor = alternarMaletaPendente
        alternarMaletaPendente = false
        return valor
    }

    /** Índice do slot tocado (soltou o dedo em cima dele), ou -1. Lido uma única vez. */
    fun consumirUsarSlot(): Int {
        val valor = usarSlotPendente
        usarSlotPendente = SEM_SLOT
        return valor
    }

    /** Chamar uma vez por frame (conta o toque longo do debug). */
    fun atualizar(dt: Float) {
        if (ponteiroDebug == SEM_PONTEIRO || debugDisparado) return
        tempoDebug += dt
        if (tempoDebug >= Constantes.DEBUG_TOQUE_LONGO_S) {
            debugDisparado = true
            alternarDebugPendente = true
        }
    }

    /** True uma única vez depois de um toque longo completo na zona de debug. */
    fun consumirAlternarDebug(): Boolean {
        val valor = alternarDebugPendente
        alternarDebugPendente = false
        return valor
    }

    /** True uma única vez depois de apertar o botão Voltar do Android. */
    fun consumirVoltar(): Boolean {
        val valor = voltarPendente
        voltarPendente = false
        return valor
    }

    /** Giro acumulado da câmera (pixels) desde a última leitura. */
    fun consumirGiroX(): Float {
        val valor = giroX
        giroX = 0f
        return valor
    }

    fun consumirGiroY(): Float {
        val valor = giroY
        giroY = 0f
        return valor
    }

    fun liberarTudo() {
        liberarJoystick()
        ponteiroCamera = SEM_PONTEIRO
        giroX = 0f
        giroY = 0f
        for (i in 0 until NUM_ACOES) {
            acoesPonteiro[i] = SEM_PONTEIRO
            acoesPressionadas[i] = false
        }
        ponteiroDebug = SEM_PONTEIRO
        tempoDebug = 0f
        debugDisparado = false
        ponteiroReiniciar = SEM_PONTEIRO
        ponteiroPausa = SEM_PONTEIRO
        pausaApertado = SEM_SLOT
        ponteiroSlot = SEM_PONTEIRO
        slotApertado = SEM_SLOT
        ponteiroBotaoMaleta = SEM_PONTEIRO
        toqueNoBotao = false
    }

    private fun liberarJoystick() {
        ponteiroJoystick = SEM_PONTEIRO
        joystickAtivo = false
        movimentoX = 0f
        movimentoY = 0f
        botaoX = centroX
        botaoY = centroY
    }

    private fun atualizarJoystick(x: Float, y: Float) {
        val dx = x - centroX
        val dy = y - centroY
        val distancia = sqrt(dx * dx + dy * dy)
        if (distancia < 0.0001f) {
            movimentoX = 0f
            movimentoY = 0f
            botaoX = centroX
            botaoY = centroY
            return
        }

        // O botão central fica preso na borda da base.
        val fator = if (distancia > raioBase) raioBase / distancia else 1f
        botaoX = centroX + dx * fator
        botaoY = centroY + dy * fator

        val magnitude = minOf(distancia, raioBase) / raioBase
        if (magnitude < Constantes.JOYSTICK_ZONA_MORTA) {
            movimentoX = 0f
            movimentoY = 0f
        } else {
            // Reescala para 0..1 depois da zona morta.
            val escala = (magnitude - Constantes.JOYSTICK_ZONA_MORTA) /
                (1f - Constantes.JOYSTICK_ZONA_MORTA)
            movimentoX = dx / distancia * escala
            movimentoY = dy / distancia * escala
        }
    }

    /** Índice do botão de ação sob o ponto (x, y), ou -1 se nenhum. */
    private fun acaoEm(x: Float, y: Float): Int {
        for (i in 0 until NUM_ACOES) {
            val dx = x - acoesX[i]
            val dy = y - acoesY[i]
            val alvo = acoesAlvo[i]
            if (dx * dx + dy * dy <= alvo * alvo) return i
        }
        return -1
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val x = screenX.toFloat()
        val y = altura - screenY
        if (bloqueado) { // pausa: so os botoes do menu de pausa respondem
            if (ponteiroPausa == SEM_PONTEIRO) {
                val b = pausaEm(x, y)
                if (b != SEM_SLOT) {
                    ponteiroPausa = pointer
                    pausaApertado = b
                    return true
                }
            }
            return false
        }

        // 0) Morto: só o botão REINICIAR responde.
        if (morto) {
            if (ponteiroReiniciar == SEM_PONTEIRO && dentroReiniciar(x, y)) {
                ponteiroReiniciar = pointer
                return true
            }
            return false
        }

        // 0b) Maleta aberta: só o botão central (fecha) e os slots (usam o item) respondem.
        if (maletaAberta) {
            if (ponteiroBotaoMaleta == SEM_PONTEIRO && dentroBotaoCentral(x, y)) {
                ponteiroBotaoMaleta = pointer
                iniciarToqueBotao(x, y)
                return true
            }
            if (ponteiroSlot == SEM_PONTEIRO) {
                val slot = slotEm(x, y)
                if (slot != SEM_SLOT) {
                    ponteiroSlot = pointer
                    slotApertado = slot
                    return true
                }
            }
            return false
        }

        // 1) Zona de debug (canto superior esquerdo): só conta o toque longo.
        if (x <= debugLargura && y >= altura - debugAltura) {
            if (ponteiroDebug == SEM_PONTEIRO) {
                ponteiroDebug = pointer
                tempoDebug = 0f
                debugDisparado = false
            }
            return true
        }

        // 2) Botões de ação: nunca viram câmera.
        val acao = acaoEm(x, y)
        if (acao >= 0) {
            if (acoesPonteiro[acao] == SEM_PONTEIRO) {
                acoesPonteiro[acao] = pointer
                acoesPressionadas[acao] = true
            }
            return true
        }

        // 3) Joystick (esquerda) ou câmera (direita).
        if (x < largura * 0.5f) {
            if (ponteiroJoystick == SEM_PONTEIRO && y <= altura * Constantes.ZONA_JOYSTICK_ALTURA) {
                ponteiroJoystick = pointer
                joystickAtivo = true
                if (dentroBotaoCentral(x, y)) iniciarToqueBotao(x, y) else toqueNoBotao = false
                atualizarJoystick(x, y)
                return true
            }
        } else if (ponteiroCamera == SEM_PONTEIRO) {
            ponteiroCamera = pointer
            cameraUltimoX = x
            cameraUltimoY = y
            return true
        }
        return false
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        val x = screenX.toFloat()
        val y = altura - screenY

        if (pointer == ponteiroJoystick) {
            acompanharToqueBotao(x, y)
            atualizarJoystick(x, y)
            return true
        }
        if (pointer == ponteiroBotaoMaleta) {
            acompanharToqueBotao(x, y)
            return true
        }
        if (pointer == ponteiroSlot) return true
        if (pointer == ponteiroCamera) {
            giroX += x - cameraUltimoX
            giroY += y - cameraUltimoY
            cameraUltimoX = x
            cameraUltimoY = y
            return true
        }
        if (pointer == ponteiroDebug) return true
        if (pointer == ponteiroReiniciar) return true
        for (i in 0 until NUM_ACOES) {
            if (acoesPonteiro[i] == pointer) return true // botão segue apertado até soltar
        }
        return false
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (pointer == ponteiroPausa) {
            val solto = pausaEm(screenX.toFloat(), altura - screenY)
            if (solto != SEM_SLOT && solto == pausaApertado) pausaPendente = solto
            ponteiroPausa = SEM_PONTEIRO
            pausaApertado = SEM_SLOT
            return true
        }
        if (pointer == ponteiroReiniciar) {
            ponteiroReiniciar = SEM_PONTEIRO
            // Só vale se soltar o dedo ainda em cima do botão.
            if (dentroReiniciar(screenX.toFloat(), altura - screenY)) reiniciarPendente = true
            return true
        }
        if (pointer == ponteiroSlot) {
            val solto = slotEm(screenX.toFloat(), altura - screenY)
            if (solto != SEM_SLOT && solto == slotApertado) usarSlotPendente = solto
            ponteiroSlot = SEM_PONTEIRO
            slotApertado = SEM_SLOT
            return true
        }
        if (pointer == ponteiroBotaoMaleta) {
            acompanharToqueBotao(screenX.toFloat(), altura - screenY)
            if (toqueCurtoValido()) alternarMaletaPendente = true
            ponteiroBotaoMaleta = SEM_PONTEIRO
            toqueNoBotao = false
            return true
        }
        if (pointer == ponteiroJoystick) {
            acompanharToqueBotao(screenX.toFloat(), altura - screenY)
            if (toqueCurtoValido()) alternarMaletaPendente = true
            toqueNoBotao = false
            liberarJoystick()
            return true
        }
        if (pointer == ponteiroCamera) {
            ponteiroCamera = SEM_PONTEIRO
            return true
        }
        if (pointer == ponteiroDebug) {
            ponteiroDebug = SEM_PONTEIRO
            tempoDebug = 0f
            debugDisparado = false
            return true
        }
        for (i in 0 until NUM_ACOES) {
            if (acoesPonteiro[i] == pointer) {
                acoesPonteiro[i] = SEM_PONTEIRO
                return true
            }
        }
        return false
    }

    override fun keyDown(keycode: Int): Boolean {
        if (keycode == Input.Keys.BACK) {
            voltarPendente = true
            return true
        }
        return false
    }

    companion object {
        const val ACAO_ATIRAR = 0
        const val ACAO_RECARREGAR = 1
        const val ACAO_INTERAGIR = 2
        const val ACAO_TROCAR = 3
        const val ACAO_PULAR = 4
        const val ACAO_AGACHAR = 5
        const val ACAO_CURAR = 6
        const val ACAO_ATIRAR_ESQ = 7
        const val ACAO_ARMA0 = 8 // 8..11 = slots de arma (pistola, fuzil, escopeta, faca)
        const val NUM_BOTOES_CIRCULARES = 8 // 0..7 sao desenhados por BotoesAcao; 8..11 pelo Hud
        const val ACAO_MENU = 12 // icone de menu (start) no topo; desenhado pelo Hud
        const val NUM_ACOES = 13
        const val NUM_PAUSA = 2
        private const val SEM_PONTEIRO = -1
        private const val SEM_SLOT = -1
    }
}
