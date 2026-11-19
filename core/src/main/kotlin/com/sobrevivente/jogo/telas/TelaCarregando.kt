// core/src/main/kotlin/com/sobrevivente/jogo/telas/TelaCarregando.kt
package com.sobrevivente.jogo.telas

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.ScreenUtils

/**
 * Tela de carregamento (estilo Resident Evil): aparece antes do jogo. Monta o mapa so depois de ja ter desenhado
 * alguns quadros, para a tela nao ficar preta durante a carga pesada.
 */
class TelaCarregando(
    private val jogo: Game,
    private val modoHistoria: Boolean = false,
    private val fase: Int = 0
) : ScreenAdapter() {

    private val forma = ShapeRenderer()
    private val lote = SpriteBatch()
    private val fonte = BitmapFont()
    private val dicas = arrayOf(
        "Mire na cabeca: tiro critico causa o dobro de dano.",
        "Escopeta de perto empurra os zumbis para longe.",
        "O fuzil atravessa ate dois inimigos.",
        "Fique atento a municao: nem todo zumbi vale uma bala.",
        "Use as ruas estreitas para controlar a horda.",
        "Toque no minimapa para abrir o mapa grande."
    )
    private var tempo = 0f
    private var quadros = 0
    private var progresso = 0f
    private val dica = dicas[MathUtils.random(dicas.size - 1)]
    private val cor = Color()

    override fun render(delta: Float) {
        tempo += minOf(delta, 0.05f)
        quadros++
        progresso = minOf(0.92f, progresso + (0.92f - progresso) * minOf(1f, delta * 1.8f))
        val w = Gdx.graphics.width.toFloat()
        val h = Gdx.graphics.height.toFloat()
        val d = minOf(w, h) / 480f

        ScreenUtils.clear(0.02f, 0.02f, 0.03f, 1f)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        forma.projectionMatrix.setToOrtho2D(0f, 0f, w, h)
        forma.begin(ShapeRenderer.ShapeType.Filled)
        // faixas escuras e vinheta simples
        forma.setColor(0.10f, 0.02f, 0.02f, 1f)
        forma.rect(0f, h * 0.60f, w, 3f * d)
        forma.rect(0f, h * 0.18f, w, 3f * d)
        // barra de progresso
        val bw = w * 0.5f; val bh = 8f * d; val bx = (w - bw) / 2f; val by = h * 0.26f
        forma.setColor(0.12f, 0.12f, 0.14f, 1f)
        forma.rect(bx, by, bw, bh)
        val pulso = 0.75f + 0.25f * MathUtils.sin(tempo * 5f)
        forma.setColor(0.75f * pulso, 0.10f, 0.08f, 1f)
        forma.rect(bx, by, bw * progresso, bh)
        forma.end()

        lote.projectionMatrix.setToOrtho2D(0f, 0f, w, h)
        lote.begin()
        fonte.data.setScale(3.2f * d)
        fonte.color = cor.set(0.85f, 0.12f, 0.10f, 1f)
        fonte.draw(lote, "PROJETO SOBREVIVENTE", 0f, h * 0.60f - 12f * d, w, Align.center, false)
        fonte.data.setScale(1.7f * d)
        fonte.color = cor.set(0.8f, 0.8f, 0.8f, 1f)
        val pontos = ".".repeat(1 + (tempo * 2.5f).toInt() % 3)
        fonte.draw(lote, "CARREGANDO" + pontos, 0f, by + bh + 36f * d, w, Align.center, false)
        fonte.data.setScale(1.3f * d)
        fonte.color = cor.set(0.55f, 0.55f, 0.58f, 1f)
        fonte.draw(lote, dica, w * 0.1f, h * 0.18f - 14f * d, w * 0.8f, Align.center, true)
        lote.end()

        // so monta o jogo depois de varios quadros mostrando esta tela (a carga trava um quadro)
        if (quadros >= 4 && tempo >= 0.7f) {
            jogo.setScreen(TelaJogo(jogo, modoHistoria, fase))
            dispose()
        }
    }

    override fun dispose() {
        forma.dispose(); lote.dispose(); fonte.dispose()
    }

    override fun hide() { /* quem troca a tela chama dispose */ }
}
