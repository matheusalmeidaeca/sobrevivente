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
    private val liberadas = BooleanArray(tipos.size) { it == 0 } // so a pistola no comeco; as outras sao achadas na vila

    var tipo = TipoArma.PISTOLA
        private set

    val carregador: Int
        get() = carregadores[tipo.ordinal]
    val reserva: Int
        get() = reservas[tipo.ordinal]

    /** Faca equipada (4o slot): sem municao, golpe corpo a corpo. */
    var faca = false
        private set

    /** True so no frame em que a faca golpeou. */
    var golpeFaca = false
        private set

    /** Slot ativo no HUD: 0..2 = armas de fogo, 3 = faca. */
    val slotAtivo: Int
        get() = if (faca) 3 else tipo.ordinal

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
        golpeFaca = false
        if (tempoCadencia > 0f) tempoCadencia -= dt
        if (faca) {
            if (atirando && tempoCadencia <= 0f) {
                tempoCadencia = 1f / Constantes.FACA_CADENCIA
                golpeFaca = true
            }
            return false
        }
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

    /** Passa para o proximo slot disponivel (pistola, fuzil, escopeta, faca). Nao troca durante a recarga. */
    fun trocar(): Boolean {
        if (recarregando) return false
        val atual = slotAtivo
        for (k in 1..4) {
            val c = (atual + k) % 4
            if (slotDisponivel(c)) return selecionarSlot(c)
        }
        return false
    }

    fun slotDisponivel(slot: Int): Boolean = if (slot == 3) true else slot in 0..2 && liberadas[slot]

    /** Equipa o slot (0..2 arma de fogo, 3 faca). Devolve true se mudou. */
    fun selecionarSlot(slot: Int): Boolean {
        if (recarregando || !slotDisponivel(slot) || slot == slotAtivo) return false
        if (slot == 3) {
            faca = true
        } else {
            faca = false
            tipo = tipos[slot]
        }
        tempoCadencia = Constantes.TROCAR_ARMA_ATRASO_S
        return true
    }

    fun estaLiberada(t: TipoArma): Boolean = liberadas[t.ordinal]

    /** Pegou a arma no chao: libera, enche o carregador e ja equipa. Devolve false se ja tinha (ai so ganha municao). */
    fun liberar(t: TipoArma): Boolean {
        val i = t.ordinal
        val nova = !liberadas[i]
        liberadas[i] = true
        if (nova) carregadores[i] = t.carregador else reservas[i] += t.reservaInicial / 2
        if (!recarregando) {
            tipo = t
            faca = false
            tempoCadencia = Constantes.TROCAR_ARMA_ATRASO_S
        }
        return nova
    }

    /** Volta ao estado inicial (carregadores cheios, reservas iniciais, pistola). Usado ao reiniciar depois da morte. */
    fun reiniciar() {
        for (i in tipos.indices) {
            carregadores[i] = tipos[i].carregador
            reservas[i] = tipos[i].reservaInicial
        }
        for (i in liberadas.indices) liberadas[i] = i == 0
        tipo = TipoArma.PISTOLA
        faca = false
        golpeFaca = false
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
