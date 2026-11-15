// core/src/main/kotlin/com/sobrevivente/jogo/Jogo.kt
package com.sobrevivente.jogo

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.sobrevivente.jogo.telas.TelaJogo

class Jogo : Game() {

    override fun create() {
        Gdx.app.log("JOGO", "create")
        setScreen(TelaJogo())
    }

    override fun dispose() {
        screen?.dispose()
        super.dispose()
        Gdx.app.log("JOGO", "dispose")
    }
}
