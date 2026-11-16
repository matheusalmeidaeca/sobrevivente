// core/src/main/kotlin/com/sobrevivente/jogo/mundo/Arma.kt
package com.sobrevivente.jogo.mundo

import com.sobrevivente.jogo.Constantes

/** As 3 armas do protagonista (Marco F4). Valores em Constantes.kt (seção 12 do mapa). */
enum class TipoArma(
    val nome: String,
    val sufixo: String, // sufixo das animacoes: andar_<sufixo>, atirar_<sufixo>...
    val modelo: String,
    val dano: Int,
    val projeteis: Int,
    val dispersao: Float, // radianos, por projetil (0 = sem espalhar)
    val cadencia: Float,
    val carregador: Int,
    val reservaInicial: Int,
    val recargaS: Float,
    val alcance: Float,
    val tempoEncarar: Float,
    val bocaX: Float,
    val bocaY: Float,
    val bocaZ: Float
) {
    PISTOLA(
        "Pistola", "pistola", Constantes.MODELO_ARMA_PISTOLA,
        Constantes.PISTOLA_DANO, 1, 0f, Constantes.PISTOLA_CADENCIA, Constantes.PISTOLA_CARREGADOR,
        Constantes.PISTOLA_RESERVA_INICIAL, Constantes.PISTOLA_RECARGA_S, Constantes.PISTOLA_ALCANCE,
        Constantes.PISTOLA_TEMPO_ENCARAR_S, 0f, 0.040f, -0.175f
    ),
    FUZIL(
        "Fuzil", "fuzil", Constantes.MODELO_ARMA_FUZIL,
        Constantes.FUZIL_DANO, 1, 0.006f, Constantes.FUZIL_CADENCIA, Constantes.FUZIL_CARREGADOR,
        Constantes.FUZIL_RESERVA_INICIAL, Constantes.FUZIL_RECARGA_S, Constantes.FUZIL_ALCANCE,
        Constantes.PISTOLA_TEMPO_ENCARAR_S, 0f, 0.075f, -0.690f
    ),
    ESCOPETA(
        "Escopeta", "escopeta", Constantes.MODELO_ARMA_ESCOPETA,
        Constantes.ESCOPETA_DANO, Constantes.ESCOPETA_PROJETEIS, Constantes.ESCOPETA_DISPERSAO,
        Constantes.ESCOPETA_CADENCIA, Constantes.ESCOPETA_CARREGADOR, Constantes.ESCOPETA_RESERVA_INICIAL,
        Constantes.ESCOPETA_RECARGA_S, Constantes.ESCOPETA_ALCANCE, Constantes.ESCOPETA_TEMPO_ENCARAR_S,
        0f, 0.090f, -0.850f
    )
}

/**
 * Munição, cadência e recarga das 3 armas (cada uma com o seu carregador e a sua reserva).
 * Só lógica; não conhece desenho nem raio. Quem chama decide o que fazer quando `atualizar` devolve true.
 */
class Arma {

    private val tipos = TipoArma.values()
    private val carregadores = IntArray(tipos.size) { tipos[it].carregador }
    private val reservas = IntArray(tipos.size) { tipos[it].reservaInicial }

    var tipo = TipoArma.PISTOLA
        private set

    val carregador: Int
        get() = carregadores[tipo.ordinal]
    val reserva: Int
        get() = reservas[tipo.ordinal]

    var recarregando = false
        private set

    private var tempoRecarga = 0f
    private var tempoCadencia = 0f

    /**
     * Chamar uma vez por frame (jogo rodando).
     * `atirando`: ATIRAR está apertado (segurar = tiro contínuo).
     * `pedidoRecarga`: RECARREGAR foi apertado neste frame.
     * Devolve true se um tiro saiu neste frame.
     */
    fun atualizar(dt: Float, atirando: Boolean, pedidoRecarga: Boolean): Boolean {
        if (tempoCadencia > 0f) tempoCadencia -= dt
        val i = tipo.ordinal

        if (recarregando) {
            tempoRecarga -= dt
            if (tempoRecarga <= 0f) concluirRecarga()
            return false // não atira durante a recarga
        }

        if (pedidoRecarga && carregadores[i] < tipo.carregador && reservas[i] > 0) {
            iniciarRecarga()
            return false
        }

        if (atirando && tempoCadencia <= 0f) {
            if (carregadores[i] > 0) {
                carregadores[i]--
                tempoCadencia = 1f / tipo.cadencia
                return true
            }
            // Carregador vazio: recarrega sozinho se houver reserva.
            if (reservas[i] > 0) iniciarRecarga()
        }
        return false
    }

    /** Passa para a proxima arma. Nao troca durante a recarga. Devolve true se trocou. */
    fun trocar(): Boolean {
        if (recarregando) return false
        tipo = tipos[(tipo.ordinal + 1) % tipos.size]
        tempoCadencia = Constantes.TROCAR_ARMA_ATRASO_S // sacar a arma leva um instante
        return true
    }

    /** Volta ao estado inicial (carregadores cheios, reservas iniciais, pistola). Usado ao reiniciar depois da morte. */
    fun reiniciar() {
        for (i in tipos.indices) {
            carregadores[i] = tipos[i].carregador
            reservas[i] = tipos[i].reservaInicial
        }
        tipo = TipoArma.PISTOLA
        recarregando = false
        tempoRecarga = 0f
        tempoCadencia = 0f
    }

    /** Soma munição à reserva da arma em uso (usar um item MUNICAO da maleta, Marco 1.7). */
    fun adicionarReserva(quantidade: Int) {
        if (quantidade > 0) reservas[tipo.ordinal] += quantidade
    }

    private fun iniciarRecarga() {
        recarregando = true
        tempoRecarga = tipo.recargaS
    }

    private fun concluirRecarga() {
        val i = tipo.ordinal
        val falta = tipo.carregador - carregadores[i]
        val movida = minOf(falta, reservas[i])
        carregadores[i] += movida
        reservas[i] -= movida
        recarregando = false
    }
}
