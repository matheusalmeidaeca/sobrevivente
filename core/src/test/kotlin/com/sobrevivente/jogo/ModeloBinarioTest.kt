package com.sobrevivente.jogo

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.UBJsonReader
import java.io.File
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** O .g3db gerado por ferramentas/g3dj_para_g3db.py precisa carregar igual ao .g3dj. */
class ModeloBinarioTest {
    private fun raiz(): File {
        var d: File? = File("").absoluteFile
        while (d != null && !File(d, "android/assets/modelos").exists()) d = d.parentFile
        return File(d!!, "android/assets/modelos")
    }

    @Test
    fun binarioIgualAoJson() {
        for (nome in listOf("jogador_humano", "zumbi")) {
            val j = File(raiz(), "$nome.g3dj"); val b = File(raiz(), "$nome.g3db")
            if (!j.exists() || !b.exists()) continue
            val a = G3dModelLoader(JsonReader()).loadModelData(FileHandle(j))
            val c = G3dModelLoader(UBJsonReader()).loadModelData(FileHandle(b))
            assertEquals(a.meshes.size, c.meshes.size)
            for (i in 0 until a.meshes.size) {
                val va = a.meshes.get(i).vertices; val vc = c.meshes.get(i).vertices
                assertEquals(va.size, vc.size)
                for (k in va.indices) assertTrue("$nome malha $i v$k", kotlin.math.abs(va[k] - vc[k]) < 1e-5f)
                assertEquals(a.meshes.get(i).parts.size, c.meshes.get(i).parts.size)
            }
            assertEquals(a.animations.size, c.animations.size)
            assertEquals(a.nodes.size, c.nodes.size)
            assertEquals(a.materials.size, c.materials.size)
        }
    }
}
