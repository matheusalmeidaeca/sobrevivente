// core/src/main/kotlin/com/sobrevivente/jogo/telas/TelaMenu.kt
package com.sobrevivente.jogo.telas

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.ScreenUtils
import com.sobrevivente.jogo.mundo.Fases
import com.sobrevivente.jogo.mundo.Progresso

/**
 * F11: menu inicial (antes das fases). JOGAR abre a sobrevivencia na vila; MODO HISTORIA ainda e "em breve"
 * (fases, chefes, puzzles, chaves, tesouros e mercador entram na onda D); SAIR fecha o app.
 */
class TelaMenu(private val jogo: Game) : ScreenAdapter() {

    private val forma = ShapeRenderer()
    private val lote = SpriteBatch()
    private val fonte = BitmapFont()
    private val rotulos = arrayOf("JOGAR", "MODO HISTORIA", "SAIR")
    private fun rotulo(i: Int): String =
        if (i == 1 && Progresso.faseMaxima > 0) "HISTORIA - FASE " + (Progresso.faseMaxima + 1).coerceAtMost(Fases.total) else rotulos[i]
    private val bx = FloatArray(3)
    private val by = FloatArray(3)
    private var bw = 1f
    private var bh = 1f
    private var w = 1f
    private var h = 1f
    private var d = 1f
    private var apertado = -1
    private var ponteiro = -1
    private var acao = -1 // botao confirmado, tratado no proximo render
    private var aviso = 0f
    private var tempo = 0f
    private val cor = Color()

    private val entrada = object : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (ponteiro != -1) return false
            val b = botaoEm(screenX.toFloat(), h - screenY)
            if (b < 0) return false
            ponteiro = pointer
            apertado = b
            return true
        }

        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (pointer != ponteiro) return false
            if (botaoEm(screenX.toFloat(), h - screenY) == apertado) acao = apertado
            ponteiro = -1
            apertado = -1
            return true
        }
    }

    private fun botaoEm(x: Float, y: Float): Int {
        for (i in 0..2) if (x >= bx[i] && x <= bx[i] + bw && y >= by[i] && y <= by[i] + bh) return i
        return -1
    }

    override fun show() {
        Gdx.input.inputProcessor = entrada
    }

    override fun hide() {
        Gdx.input.inputProcessor = null
    }

    override fun resize(width: Int, height: Int) {
        w = width.toFloat()
        h = height.toFloat()
        d = Gdx.graphics.density
        bw = 260f * d
        bh = 52f * d
        for (i in 0..2) {
            bx[i] = (w - bw) * 0.5f
            by[i] = h * 0.46f - i * (bh + 14f * d)
        }
        lote.projectionMatrix.setToOrtho2D(0f, 0f, w, h)
        forma.projectionMatrix.setToOrtho2D(0f, 0f, w, h)
        forma.updateMatrices()
    }

    override fun render(delta: Float) {
        tempo += minOf(delta, 0.05f)
        if (aviso > 0f) aviso -= delta
        when (acao) {
            0 -> {
                acao = -1
                jogo.setScreen(TelaJogo(jogo))
                dispose()
                return
            }
            1 -> {
                acao = -1
                // Modo historia: comeca na fase liberada (a memoria dura ate fechar o app; ainda sem salvar em disco)
                if (Progresso.faseMaxima == 0) Progresso.novo()
                jogo.setScreen(TelaJogo(jogo, true, Progresso.faseMaxima.coerceAtMost(Fases.total - 1)))
                dispose()
                return
            }
            2 -> {
                acao = -1
                Gdx.app.exit()
            }
        }

        ScreenUtils.clear(0.04f, 0.05f, 0.07f, 1f)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        forma.begin(ShapeRenderer.ShapeType.Filled)
        desenharFundo()
        for (i in 0..2) {
            val e = if (apertado == i) 0.6f else 1f
            forma.setColor(1f, 1f, 1f, 0.7f)
            forma.rect(bx[i], by[i], bw, bh)
            when (i) {
                0 -> forma.setColor(0.16f * e, 0.42f * e, 0.20f * e, 0.96f)
                1 -> forma.setColor(0.30f * e, 0.26f * e, 0.12f * e, 0.96f)
                else -> forma.setColor(0.40f * e, 0.15f * e, 0.14f * e, 0.96f)
            }
            forma.rect(bx[i] + 2f * d, by[i] + 2f * d, bw - 4f * d, bh - 4f * d)
        }
        forma.end()

        lote.begin()
        fonte.data.setScale(3.2f * d)
        fonte.setColor(0.92f, 0.86f, 0.74f, 1f)
        fonte.draw(lote, "SOBREVIVENTE", 0f, h * 0.88f, w, Align.center, false)
        fonte.data.setScale(0.95f * d)
        fonte.setColor(0.75f, 0.75f, 0.78f, 1f)
        fonte.draw(lote, "Sobreviva a vila. Ache as armas. Nao pare de correr.", 0f, h * 0.88f - 56f * d, w, Align.center, false)
        fonte.data.setScale(1.3f * d)
        fonte.setColor(1f, 1f, 1f, 1f)
        for (i in 0..2) {
            fonte.draw(lote, rotulo(i), bx[i], by[i] + bh * 0.5f + fonte.capHeight * 0.5f, bw, Align.center, false)
        }
        if (aviso > 0f) {
            fonte.data.setScale(1.0f * d)
            fonte.setColor(1f, 0.85f, 0.3f, 1f)
            fonte.draw(lote, "Modo historia", 0f, by[2] - 30f * d, w, Align.center, false)
        }
        fonte.setColor(1f, 1f, 1f, 1f)
        lote.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    /** Ceu de crepusculo em degrade, lua, silhuetas de casas e arvores e neblina rasteira. */
    private fun desenharFundo() {
        val topo = Color(0.05f, 0.07f, 0.12f, 1f)
        val meio = Color(0.30f, 0.16f, 0.14f, 1f)
        forma.rect(0f, h * 0.35f, w, h * 0.65f, meio, meio, topo, topo)
        forma.setColor(0.12f, 0.07f, 0.07f, 1f)
        forma.rect(0f, 0f, w, h * 0.35f)
        forma.setColor(0.95f, 0.88f, 0.70f, 0.9f)
        forma.circle(w * 0.80f, h * 0.74f, 30f * d, 32)
        forma.setColor(0.05f, 0.04f, 0.05f, 1f)
        val base = h * 0.35f
        val casas = floatArrayOf(0.04f, 0.14f, 0.10f, 0.20f, 0.30f, 0.16f, 0.12f, 0.64f, 0.18f, 0.12f, 0.80f, 0.20f, 0.14f)
        // blocos: x, largura, altura (fracoes), repetidos
        var k = 0
        while (k + 2 < casas.size) {
            val x = casas[k] * w
            val cw = casas[k + 1] * w
            val ch = casas[k + 2] * h
            forma.rect(x, base - 2f, cw, ch)
            forma.triangle(x - 0.01f * w, base + ch - 2f, x + cw + 0.01f * w, base + ch - 2f, x + cw * 0.5f, base + ch + 0.08f * h)
            k += 3
        }
        for (i in 0 until 9) {
            val x = (i * 0.125f + 0.03f) * w
            val ah = (0.10f + 0.04f * MathUtils.sin(i * 2.3f)) * h
            forma.triangle(x - 0.03f * w, base, x + 0.03f * w, base, x, base + ah)
        }
        for (i in 0 until 5) { // neblina andando devagar
            val nx = ((i * 0.27f + tempo * 0.01f * (1 + i)) % 1.3f - 0.15f) * w
            forma.setColor(0.7f, 0.7f, 0.75f, 0.06f)
            forma.circle(nx, base + 0.02f * h * i, 0.18f * w, 24)
        }
    }

    override fun dispose() {
        forma.dispose()
        lote.dispose()
        fonte.dispose()
    }
}
