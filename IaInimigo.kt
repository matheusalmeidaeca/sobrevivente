import kotlin.math.hypot
import kotlin.random.Random

// ===== IA estilo RE4: máquina de estados + fichas de ataque + dificuldade dinâmica =====

enum class Estado { PARADO, PATRULHA, PERSEGUE, CERCA, ATACA, ATORDOADO, VOANDO, CAIDO, MORTO }

/** Rank oculto 1..10: sobe se o jogador vai bem, desce se vai mal. */
class DificuldadeDinamica {
    var rank = 5f
        private set

    fun jogadorMorreu()      { rank = (rank - 1.5f).coerceAtLeast(1f) }
    fun jogadorLevouDano()   { rank = (rank - 0.2f).coerceAtLeast(1f) }
    fun jogadorMatouInimigo(){ rank = (rank + 0.15f).coerceAtMost(10f) }

    // multiplicadores usados pelos inimigos
    val agressividade get() = 0.6f + rank * 0.08f   // 0.68 .. 1.4
    val velocidade    get() = 0.85f + rank * 0.03f
    val vidaExtra     get() = 0.8f + rank * 0.04f
}

/** Só alguns inimigos atacam ao mesmo tempo; os outros cercam. */
class FichasDeAtaque(private val max: Int = 2) {
    private val usando = mutableSetOf<Inimigo>()
    fun pedir(i: Inimigo): Boolean = i in usando || (usando.size < max).also { if (it) usando += i }
    fun devolver(i: Inimigo) { usando -= i }
}

enum class ParteAtingida { CABECA, CORPO, PERNA }

/** impacto: 0 = quase nada, 1 = leve, 2 = médio, 3 = forte. Ajuste os números ao seu jogo. */
/** empurrao: velocidade de arremesso (0 = não empurra). Perto de 3+ o inimigo sai voando. */
enum class Arma(val dano: Float, val impacto: Int, val empurrao: Float) {
    PISTOLA(dano = 25f, impacto = 1, empurrao = 0f),
    SUBMETRALHADORA(dano = 12f, impacto = 0, empurrao = 0f),
    SHOTGUN(dano = 60f, impacto = 3, empurrao = 7f),
    RIFLE(dano = 80f, impacto = 2, empurrao = 2f),
    MAGNUM(dano = 150f, impacto = 3, empurrao = 5f),
    FACA(dano = 15f, impacto = 1, empurrao = 0f),
    GRANADA(dano = 100f, impacto = 3, empurrao = 9f)   // explosão: arremessa todos ao redor
}

class Inimigo(
    var x: Float, var y: Float,
    private val dif: DificuldadeDinamica,
    private val fichas: FichasDeAtaque,
    var vida: Float = 100f
) {
    var estado = Estado.PARADO
        private set
    private var tempo = 0f            // cronômetro do estado atual
    private val visao = 12f
    private val alcanceAtaque = 1.6f
    private val velBase = 2.2f

    private fun mudar(novo: Estado) {
        if (estado == Estado.ATACA) fichas.devolver(this)
        estado = novo; tempo = 0f
    }

    /** Chame a cada frame. (px, py) = posição do jogador; barulho = jogador atirou/correu. */
    fun atualizar(dt: Float, px: Float, py: Float, barulho: Boolean) {
        tempo += dt
        val dist = hypot(px - x, py - y)

        // arremesso: move e vai perdendo velocidade (vale até para inimigo morto)
        if (vx != 0f || vy != 0f) {
            x += vx * dt; y += vy * dt
            val atrito = (1f - 4f * dt).coerceAtLeast(0f)
            vx *= atrito; vy *= atrito
            if (hypot(vx, vy) < 0.3f) { vx = 0f; vy = 0f }
        }

        when (estado) {
            Estado.PARADO, Estado.PATRULHA ->
                if (dist < visao || (barulho && dist < visao * 1.5f)) mudar(Estado.PERSEGUE)

            Estado.PERSEGUE -> {
                andarAte(px, py, dt)
                if (dist <= alcanceAtaque * 2.5f) mudar(Estado.CERCA)
            }

            Estado.CERCA -> {
                // espera a vez: se conseguir ficha, ataca; senão fica rondando
                if (dist > alcanceAtaque * 3f) mudar(Estado.PERSEGUE)
                else if (tempo > 0.4f / dif.agressividade && fichas.pedir(this)) mudar(Estado.ATACA)
                else andarAte(px, py, dt * 0.3f)
            }

            Estado.ATACA -> {
                if (dist > alcanceAtaque) andarAte(px, py, dt)
                else if (tempo > 0.6f) {
                    escolherGolpe()            // aqui você causa dano no jogador
                    mudar(Estado.CERCA)
                }
            }

            Estado.VOANDO    -> if (tempo > 0.5f) mudar(Estado.CAIDO)   // voa, cai e depois levanta
            Estado.ATORDOADO -> if (tempo > 1.2f) mudar(Estado.PERSEGUE)
            Estado.CAIDO     -> if (tempo > 1.5f) mudar(Estado.PERSEGUE)
            Estado.MORTO     -> {}
        }
    }

    private fun andarAte(px: Float, py: Float, dt: Float) {
        val dx = px - x; val dy = py - y
        val d = hypot(dx, dy).coerceAtLeast(0.001f)
        val v = velBase * dif.velocidade * dt
        x += dx / d * v; y += dy / d * v
    }

    /** Golpe sorteado: dá variedade. Troque pelos seus ataques/animações. */
    private fun escolherGolpe(): String =
        when (Random.nextInt(3)) { 0 -> "foice"; 1 -> "soco"; else -> "agarrao" }

    /** Reação depende da arma (impacto) e de onde acertou. distancia = do jogador até o inimigo. */
    fun levarTiro(
        arma: Arma, parte: ParteAtingida, distancia: Float = 5f,
        origemX: Float = x, origemY: Float = y - 1f   // de onde veio o tiro/explosão
    ) {
        if (estado == Estado.MORTO) return

        var dano = arma.dano
        if (parte == ParteAtingida.CABECA) dano *= 2f
        if (arma == Arma.SHOTGUN) dano *= (1.6f - distancia / 8f).coerceIn(0.4f, 1.6f) // perto = forte
        vida -= dano

        // força do arremesso: cai com a distância (shotgun/explosão de perto = voa longe)
        val forca = arma.empurrao * (1.2f - distancia / 10f).coerceIn(0f, 1.2f)
        val voa = forca >= 3f && !pesado
        if (voa) {
            val dx = x - origemX; val dy = y - origemY
            val d = hypot(dx, dy).coerceAtLeast(0.001f)
            vx = dx / d * forca; vy = dy / d * forca
        }

        if (vida <= 0f) { mudar(Estado.MORTO); return }
        if (voa) { mudar(Estado.VOANDO); return }

        // impacto efetivo: cabeça/perna aumentam, armadura diminui
        var impacto = arma.impacto
        if (parte != ParteAtingida.CORPO) impacto += 1
        if (temArmadura) impacto -= 1

        when {
            impacto <= 0 -> {}                                  // só treme
            impacto == 1 -> if (parte == ParteAtingida.CORPO) {} else mudar(Estado.ATORDOADO)
            impacto == 2 -> mudar(if (parte == ParteAtingida.PERNA) Estado.CAIDO else Estado.ATORDOADO)
            else         -> mudar(Estado.CAIDO)                 // impacto alto derruba
        }
    }

    var temArmadura = false
    var pesado = false          // inimigos grandes (chefes, brutamontes) não voam
    private var vx = 0f
    private var vy = 0f
}
