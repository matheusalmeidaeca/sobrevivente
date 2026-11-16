// core/src/main/kotlin/com/sobrevivente/jogo/entrada/ControlesToque.kt
package com.sobrevivente.jogo.entrada

import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.sobrevivente.jogo.Constantes
import kotlin.math.sqrt

/**
 * Multitouch: cada dedo é "dono" de uma função pelo ID do ponteiro.
 * Prioridade no toque: zona de debug > botões de ação > joystick/câmera.
 * Metade esquerda (parte de baixo) = joystick; metade direita (fora dos botões) = girar câmera.
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
        debugLargura = Constantes.DEBUG_ZONA_LARGURA_DP * densidade
        debugAltura = Constantes.DEBUG_ZONA_ALTURA_DP * densidade
        liberarTudo()
    }

    /** Posição dos 3 botões (seção 10.5): ATIRAR no canto, RECARREGAR à esquerda, INTERAGIR acima. */
    private fun calcularAcoes(densidade: Float) {
        val margem = Constantes.MARGEM_SEGURA_DP * densidade
        val raioAtirar = Constantes.BOTAO_ATIRAR_DIAMETRO_DP * 0.5f * densidade
        val raioPequeno = Constantes.BOTAO_PEQUENO_DIAMETRO_DP * 0.5f * densidade
        val folga = Constantes.BOTAO_FOLGA_DP * densidade
        val alvoMinimo = Constantes.BOTAO_ALVO_MINIMO_DP * 0.5f * densidade

        val atirarX = largura - margem - raioAtirar
        val atirarY = margem + raioAtirar

        definirAcao(ACAO_ATIRAR, atirarX, atirarY, raioAtirar, alvoMinimo)
        definirAcao(
            ACAO_RECARREGAR,
            atirarX - raioAtirar - folga - raioPequeno,
            atirarY,
            raioPequeno,
            alvoMinimo
        )
        definirAcao(
            ACAO_INTERAGIR,
            atirarX,
            atirarY + raioAtirar + folga + raioPequeno,
            raioPequeno,
            alvoMinimo
        )
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
        if (bloqueado) return false
        val x = screenX.toFloat()
        val y = altura - screenY

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
            atualizarJoystick(x, y)
            return true
        }
        if (pointer == ponteiroCamera) {
            giroX += x - cameraUltimoX
            giroY += y - cameraUltimoY
            cameraUltimoX = x
            cameraUltimoY = y
            return true
        }
        if (pointer == ponteiroDebug) return true
        for (i in 0 until NUM_ACOES) {
            if (acoesPonteiro[i] == pointer) return true // botão segue apertado até soltar
        }
        return false
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (pointer == ponteiroJoystick) {
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
        const val NUM_ACOES = 3
        private const val SEM_PONTEIRO = -1
    }
}
