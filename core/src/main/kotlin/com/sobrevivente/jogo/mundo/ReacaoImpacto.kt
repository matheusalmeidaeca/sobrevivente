// core/src/main/kotlin/com/sobrevivente/jogo/mundo/ReacaoImpacto.kt
package com.sobrevivente.jogo.mundo

import com.badlogic.gdx.math.MathUtils
import com.sobrevivente.jogo.Constantes

/**
 * F10: desequilibrio e dor por impacto (golpe de zumbi, tiro, recuo, atropelo).
 * `disparar` recebe a direcao em que o golpe EMPURRA (plano XZ, mundo). `calcular(yaw)` devolve, em graus e no referencial
 * do corpo, `pitch` (+ = inclina para a FRENTE; aplicar rotate(X, -pitch)), `roll` (graus em Z, aplicar direto: negativo = direita) e `giro` (torcao do tronco, em Y). O corpo
 * se inclina PARA A MESMA DIRECAO do golpe, volta oscilando (amortecido) e o personagem continua podendo andar.
 */
class ReacaoImpacto {
    private var dirX = 0f
    private var dirZ = 0f
    private var forca = 0f
    private var duracao = 1f
    private var decorrido = 99f

    var pitch = 0f
        private set
    var roll = 0f
        private set
    var giro = 0f
        private set
    /** 0..1: intensidade atual (para tremer cabeca/bracos de dor). */
    var intensidade = 0f
        private set

    val ativa: Boolean
        get() = decorrido < duracao

    fun disparar(dx: Float, dz: Float, f: Float, dur: Float = Constantes.IMPACTO_TEMPO_S) {
        val l = kotlin.math.sqrt(dx * dx + dz * dz)
        if (l < 1e-4f) return
        // golpes seguidos somam (ate o limite) em vez de reiniciar do zero
        val restante = if (ativa) intensidade else 0f
        dirX = dx / l
        dirZ = dz / l
        forca = minOf(1.4f, f + restante * 0.5f)
        duracao = dur
        decorrido = 0f
    }

    fun limpar() {
        decorrido = 99f
        pitch = 0f; roll = 0f; giro = 0f; intensidade = 0f
    }

    fun atualizar(dt: Float) {
        if (!ativa) { pitch = 0f; roll = 0f; giro = 0f; intensidade = 0f; return }
        decorrido += dt
    }

    /** Converte a direcao do golpe para o referencial do corpo (yaw em graus) e aplica o envelope. */
    fun calcular(yawGraus: Float) {
        if (!ativa) return
        val a = yawGraus * MathUtils.degreesToRadians
        val seno = MathUtils.sin(a)
        val cosseno = MathUtils.cos(a)
        // frente do corpo = (-seno, -cosseno); direita = (cosseno, -seno)
        val f = dirX * -seno + dirZ * -cosseno
        val r = dirX * cosseno + dirZ * -seno
        val ataque = minOf(1f, decorrido / 0.09f)
        val env = ataque * kotlin.math.exp(-2.7f * decorrido) * (1f + 0.30f * MathUtils.cos(13f * decorrido))
        intensidade = MathUtils.clamp(env * forca, 0f, 1.4f)
        val k = Constantes.IMPACTO_INCLINA_GRAUS * forca * env
        pitch = f * k          // empurrado para a frente (f>0): tronco vai para a frente = rotacao negativa em X
        roll = -r * k          // empurrado para a direita: tronco vai para a direita = rotacao negativa em Z
        giro = r * k * 0.8f + MathUtils.sin(21f * decorrido) * k * 0.25f
    }
}
