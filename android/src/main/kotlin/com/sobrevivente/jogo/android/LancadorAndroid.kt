// android/src/main/kotlin/com/sobrevivente/jogo/android/LancadorAndroid.kt
package com.sobrevivente.jogo.android

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.sobrevivente.jogo.Jogo

class LancadorAndroid : AndroidApplication() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ocupa a tela toda, inclusive ao redor do furo da câmera (API 28+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

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
