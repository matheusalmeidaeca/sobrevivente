// core/src/main/kotlin/com/sobrevivente/jogo/mundo/Arma.kt
package com.sobrevivente.jogo.mundo

import com.sobrevivente.jogo.Constantes

/**
 * Pistola (placeholder): munição, cadência e recarga. Só lógica; não conhece desenho nem raio.
 * Quem chama decide o que fazer quando `atualizar` devolve true (o tiro foi disparado).
 */
class Arma {

    var carregador = Constantes.PISTOLA_CARREGADOR
        private set
    var reserva = Constantes.PISTOLA_RESERVA_INICIAL
        private set
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

        if (recarregando) {
            tempoRecarga -= dt
            if (tempoRecarga <= 0f) concluirRecarga()
            return false // não atira durante a recarga
        }

        if (pedidoRecarga && carregador < Constantes.PISTOLA_CARREGADOR && reserva > 0) {
            iniciarRecarga()
            return false
        }

        if (atirando && tempoCadencia <= 0f) {
            if (carregador > 0) {
                carregador--
                tempoCadencia = 1f / Constantes.PISTOLA_CADENCIA
                return true
            }
            // Carregador vazio: recarrega sozinho se houver reserva.
            if (reserva > 0) iniciarRecarga()
        }
        return false
    }

    /** Volta ao estado inicial (carregador cheio, reserva inicial). Usado ao reiniciar depois da morte. */
    fun reiniciar() {
        carregador = Constantes.PISTOLA_CARREGADOR
        reserva = Constantes.PISTOLA_RESERVA_INICIAL
        recarregando = false
        tempoRecarga = 0f
        tempoCadencia = 0f
    }

    private fun iniciarRecarga() {
        recarregando = true
        tempoRecarga = Constantes.PISTOLA_RECARGA_S
    }

    private fun concluirRecarga() {
        val falta = Constantes.PISTOLA_CARREGADOR - carregador
        val movida = minOf(falta, reserva)
        carregador += movida
        reserva -= movida
        recarregando = false
    }
}
