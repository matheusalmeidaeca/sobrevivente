// android/src/main/kotlin/com/sobrevivente/jogo/android/LancadorAndroid.kt
package com.sobrevivente.jogo.android

import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.sobrevivente.jogo.Jogo

class LancadorAndroid : AndroidApplication() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val configuracao = AndroidApplicationConfiguration().apply {
            useGL30 = false
            numSamples = 0
            useAccelerometer = false
            useCompass = false
            useImmersiveMode = true
            useWakelock = true
        }

        initialize(Jogo(), configuracao)
    }
}
