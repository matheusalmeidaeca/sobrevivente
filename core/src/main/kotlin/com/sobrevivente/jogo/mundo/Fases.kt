// core/src/main/kotlin/com/sobrevivente/jogo/mundo/Fases.kt
package com.sobrevivente.jogo.mundo

import com.sobrevivente.jogo.Constantes

/**
 * Modo historia (F17): as FASES. O mapa de cada fase e montado aqui, em codigo, com `MapaBuilder`, e entra no jogo
 * trocando os arrays de `Constantes` (paredes, pisos, itens, zumbis) e da `VilaDecoracao` (desenho) antes de criar o `Mundo`.
 * A fase 1 e a vila original (arrays guardados na primeira vez que este objeto e usado).
 */
class DefFase(
    val nome: String,
    val subtitulo: String,
    val artefato: String,
    val miniNome: String,
    val chefeNome: String,
    val inicio: FloatArray,
    val mercador: FloatArray,
    /** Fragmentos: 0 = livre, 1 = livre e acorda o mini chefe, 2 = aparece quando as alavancas estao certas. (x, z) cada. */
    val fragmentos: FloatArray,
    /** Alavancas 0 (azul), 1 (vermelha), 2 (amarela). (x, z) cada. */
    val alavancas: FloatArray,
    /** Ordem certa (indices das alavancas). */
    val ordem: IntArray,
    val altar: FloatArray,
    val baus: FloatArray,
    val miniSpawn: FloatArray,
    val chefeSpawn: FloatArray,
    val dicas: Array<String>,
    val spawnMax: Int,
    val spawnDist: Float,
    val spawnIntervalo: Float,
    /** Multiplica a vida dos chefes (fases mais dificeis). */
    val fatorVida: Float,
    val construir: ((MapaBuilder) -> Unit)?,
    /** Cidade metropolitana (chao de asfalto, missao `Cidade` em vez de `Historia`). */
    val cidade: Boolean = false
)

/** Monta paredes, pisos, telhados, decoracao, itens e zumbis de uma fase e despeja tudo em `Constantes`. */
class MapaBuilder {
    private val par = ArrayList<Float>()
    private val parBase = ArrayList<Float>()
    private val parAlt = ArrayList<Float>()
    private val parTipo = ArrayList<Int>()
    private val pisos = ArrayList<Float>()
    private val decor = ArrayList<Float>()
    private val telhados = ArrayList<Float>()
    private val itensXZ = ArrayList<Float>()
    private val itensY = ArrayList<Float>()
    private val itensTipo = ArrayList<Int>()
    private val zumbis = ArrayList<Float>()
    private val spawns = ArrayList<Float>()
    private val portoes = ArrayList<Int>()
    private val parRot = ArrayList<Float>()
    private val parObb = ArrayList<Float>()
    private var girou = false

    /** Deformacao do mapa (x, z) -> (x', z', giro em radianos). Null = mapa reto. */
    var warp: ((Float, Float) -> FloatArray)? = null
    /** Transformacao rigida ativa (grupo): origem x/z, destino x/z, giro. */
    private var rig: FloatArray? = null
    /** Altura do chao mais alto sob o grupo ativo (topo dos predios acompanha). */
    private var rigTopo = 0f

    /** Tudo o que for criado dentro de `corpo` sofre UMA transformacao rigida (centrada em cx, cz): predios inteiros giram juntos. */
    fun grupo(cx: Float, cz: Float, escala: Float = 1f, corpo: () -> Unit) {
        val w = warp
        if (w == null || rig != null) { corpo(); return }
        val r = w(cx, cz)
        rig = floatArrayOf(cx, cz, r[0], r[1], r[2], escala)
        rigTopo = if (Terreno.ativo) Terreno.maxEm(r[0] - 12f * escala, r[1] - 12f * escala, r[0] + 12f * escala, r[1] + 12f * escala) else 0f
        corpo()
        rig = null
    }

    /** Ponto do mapa reto -> mundo. Devolve (x, z, giro). */
    private fun tp(x: Float, z: Float): FloatArray {
        val g = rig
        if (g != null) {
            val c = kotlin.math.cos(g[4]); val s = kotlin.math.sin(g[4])
            val lx = (x - g[0]) * g[5]; val lz = (z - g[1]) * g[5]
            return floatArrayOf(g[2] + lx * c + lz * s, g[3] - lx * s + lz * c, g[4])
        }
        val w = warp ?: return floatArrayOf(x, z, 0f)
        return w(x, z)
    }

    /** Posicao (x, z) no mundo. */
    private fun px(x: Float, z: Float) = tp(x, z)

    private fun adicionarGirada(cx: Float, cz: Float, hx: Float, hz: Float, ang: Float, alt: Float, tipo: Int, base: Float) {
        val c = kotlin.math.abs(kotlin.math.cos(ang)); val s = kotlin.math.abs(kotlin.math.sin(ang))
        val ex = hx * c + hz * s; val ez = hx * s + hz * c
        par.add(cx - ex); par.add(cz - ez); par.add(cx + ex); par.add(cz + ez)
        parBase.add(base); parAlt.add(alt); parTipo.add(tipo)
        parRot.add(ang); parObb.add(cx); parObb.add(cz); parObb.add(hx); parObb.add(hz)
        if (ang != 0f) girou = true
    }

    /** Caixa de colisao e desenho (tipo: 0 casa, 1 pedra, 3 celeiro, 4 grade, 6 creme, 7 ocre, 8 cinza azulado; 5 tronco e 9 caixote so colidem). */
    fun caixa(x0: Float, z0: Float, x1: Float, z1: Float, alt: Float = 3f, tipo: Int = 0, base: Float = 0f) {
        if (warp != null) {
            val ax = minOf(x0, x1); val az = minOf(z0, z1); val bx = maxOf(x0, x1); val bz = maxOf(z0, z1)
            if (rig != null) {
                val r = tp((ax + bx) / 2f, (az + bz) / 2f)
                adicionarGirada(r[0], r[1], (bx - ax) / 2f * rig!![5], (bz - az) / 2f * rig!![5], r[2], alt, tipo, base)
            } else {
                // peca solta: fatia em trechos de ate ~10 m, cada um com o giro do lugar (a parede acompanha a curva)
                val nx = kotlin.math.ceil((bx - ax) / 10f).toInt().coerceAtLeast(1)
                val nz = kotlin.math.ceil((bz - az) / 10f).toInt().coerceAtLeast(1)
                for (i in 0 until nx) for (j in 0 until nz) {
                    val xa = ax + (bx - ax) * i / nx - (if (i > 0) 0.15f else 0f); val xb = ax + (bx - ax) * (i + 1) / nx + (if (i < nx - 1) 0.15f else 0f)
                    val za = az + (bz - az) * j / nz - (if (j > 0) 0.15f else 0f); val zb = az + (bz - az) * (j + 1) / nz + (if (j < nz - 1) 0.15f else 0f)
                    val r = tp((xa + xb) / 2f, (za + zb) / 2f)
                    adicionarGirada(r[0], r[1], (xb - xa) / 2f, (zb - za) / 2f, r[2], alt, tipo, base)
                }
            }
            return
        }
        parRot.add(0f); parObb.add(0f); parObb.add(0f); parObb.add(0f); parObb.add(0f)
        par.add(minOf(x0, x1)); par.add(minOf(z0, z1)); par.add(maxOf(x0, x1)); par.add(maxOf(z0, z1))
        parBase.add(base); parAlt.add(alt); parTipo.add(tipo)
    }

    /** Caixa de colisao que o jogo pode REMOVER depois (portao trancado). Devolve o indice da parede. */
    fun portao(x0: Float, z0: Float, x1: Float, z1: Float, alt: Float = 3.4f, tipo: Int = 16): Int {
        caixa(x0, z0, x1, z1, alt, tipo)
        val idx = par.size / 4 - 1
        portoes.add(idx)
        return idx
    }

    /** Parede reta (espessura 0,4) de (xa, za) a (xb, zb), horizontal ou vertical, com vaos (centros) de largura `larg`. */
    fun muro(xa: Float, za: Float, xb: Float, zb: Float, alt: Float, tipo: Int, vaos: FloatArray = floatArrayOf(), larg: Float = 3f) {
        val t = 0.4f
        if (kotlin.math.abs(zb - za) < 0.01f) { // ao longo de X
            val z = za
            var ini = minOf(xa, xb)
            val fim = maxOf(xa, xb)
            for (v in vaos.sorted()) {
                if (v - larg / 2 > ini) caixa(ini, z, v - larg / 2, z + t, alt, tipo)
                if (alt > 2.6f) caixa(v - larg / 2, z, v + larg / 2, z + t, alt - 2.4f, tipo, 2.4f)
                ini = v + larg / 2
            }
            if (fim > ini) caixa(ini, z, fim, z + t, alt, tipo)
        } else { // ao longo de Z
            val x = xa
            var ini = minOf(za, zb)
            val fim = maxOf(za, zb)
            for (v in vaos.sorted()) {
                if (v - larg / 2 > ini) caixa(x, ini, x + t, v - larg / 2, alt, tipo)
                if (alt > 2.6f) caixa(x, v - larg / 2, x + t, v + larg / 2, alt - 2.4f, tipo, 2.4f)
                ini = v + larg / 2
            }
            if (fim > ini) caixa(x, ini, x + t, fim, alt, tipo)
        }
    }

    /** Predio com 1 porta. lado: 0 = norte (z menor), 1 = leste (x maior), 2 = sul (z maior), 3 = oeste. `pos` = centro da porta no eixo da parede. */
    fun predio(
        x0: Float, z0: Float, x1: Float, z1: Float, lado: Int, pos: Float,
        larg: Float = 3f, alt: Float = 3f, tipo: Int = 0, telhado: Boolean = true
    ) {
        if (warp != null && rig == null) {
            grupo((x0 + x1) / 2f, (z0 + z1) / 2f, 0.88f) { predio(x0, z0, x1, z1, lado, pos, larg, alt, tipo, telhado) }
            return
        }
        if (warp != null) {
            // girado: sem piso/telhado de modelo (so ficam alinhados aos eixos); laje fina no lugar do piso
            predioParedes(x0, z0, x1, z1, lado, pos, larg, alt, tipo)
            return
        }
        val t = 0.4f
        val nada = floatArrayOf()
        val v = floatArrayOf(pos)
        muro(x0, z0, x1, z0, alt, tipo, if (lado == 0) v else nada, larg)
        muro(x0, z1 - t, x1, z1 - t, alt, tipo, if (lado == 2) v else nada, larg)
        muro(x0, z0 + t, x0, z1 - t, alt, tipo, if (lado == 3) v else nada, larg)
        muro(x1 - t, z0 + t, x1 - t, z1 - t, alt, tipo, if (lado == 1) v else nada, larg)
        pisos.add(x0 + 0.2f); pisos.add(z0 + 0.2f); pisos.add(x1 - 0.2f); pisos.add(z1 - 0.2f)
        if (telhado) {
            val c = when (tipo) { 6 -> floatArrayOf(0.42f, 0.22f, 0.16f); 7 -> floatArrayOf(0.30f, 0.28f, 0.26f); 8 -> floatArrayOf(0.26f, 0.30f, 0.34f); else -> floatArrayOf(0.30f, 0.18f, 0.12f) }
            telhados.add(x0 - 0.3f); telhados.add(z0 - 0.3f); telhados.add(x1 + 0.3f); telhados.add(z1 + 0.3f)
            telhados.add(alt); telhados.add(2.2f); telhados.add(if (x1 - x0 >= z1 - z0) 0f else 1f)
            telhados.add(c[0]); telhados.add(c[1]); telhados.add(c[2]); telhados.add(1f)
        }
    }

    private fun predioParedes(x0: Float, z0: Float, x1: Float, z1: Float, lado: Int, pos: Float, larg: Float, alt: Float, tipo: Int) {
        val t = 0.4f
        val nada = floatArrayOf()
        val v = floatArrayOf(pos)
        muro(x0, z0, x1, z0, alt, tipo, if (lado == 0) v else nada, larg)
        muro(x0, z1 - t, x1, z1 - t, alt, tipo, if (lado == 2) v else nada, larg)
        muro(x0, z0 + t, x0, z1 - t, alt, tipo, if (lado == 3) v else nada, larg)
        muro(x1 - t, z0 + t, x1 - t, z1 - t, alt, tipo, if (lado == 1) v else nada, larg)
    }

    /** Cerca/muro ao redor da area jogavel. */
    fun cerco(x0: Float, z0: Float, x1: Float, z1: Float, alt: Float = 3f, tipo: Int = 4) {
        muro(x0, z0, x1, z0, alt, tipo)
        muro(x0, z1 - 0.4f, x1, z1 - 0.4f, alt, tipo)
        muro(x0, z0 + 0.4f, x0, z1 - 0.4f, alt, tipo)
        muro(x1 - 0.4f, z0 + 0.4f, x1 - 0.4f, z1 - 0.4f, alt, tipo)
    }

    /** Forma so de desenho: forma 0 caixa, 1 esfera, 2 cilindro, 3 cone. */
    fun dec(cx: Float, cy: Float, cz: Float, sx0: Float, sy: Float, sz0: Float, r: Float, g: Float, b: Float, forma: Int = 0, rot: Float = 0f) {
        var cx = cx; var cz = cz; var rot = rot; var sx = sx0; var sz = sz0
        if (warp != null) {
            val r = tp(cx, cz); cx = r[0]; cz = r[1]; rot += r[2] * 57.29578f
            val e = rig?.get(5) ?: 1f
            if (e != 1f) { sx *= e; sz *= e }
        }
        var cy = cy
        if (Terreno.ativo) cy += if (rig != null && cy > 2f) rigTopo else Terreno.alturaChao(cx, cz)
        decor.add(cx); decor.add(cy); decor.add(cz); decor.add(sx); decor.add(sy); decor.add(sz)
        decor.add(rot); decor.add(r); decor.add(g); decor.add(b); decor.add(forma.toFloat())
    }

    fun arvore(x: Float, z: Float, pinheiro: Boolean = false) {
        if (warp != null && rig == null) { grupo(x, z) { arvore(x, z, pinheiro) }; return }
        caixa(x - 0.3f, z - 0.3f, x + 0.3f, z + 0.3f, 3f, 5)
        // variacao deterministica por posicao: cada arvore e diferente
        val h = kotlin.math.abs(kotlin.math.sin(x * 12.9898f + z * 78.233f) * 43758.547f)
        val f = h - kotlin.math.floor(h)
        val f2 = (f * 7.13f) - kotlin.math.floor(f * 7.13f)
        val tom = 0.85f + f2 * 0.3f
        // raizes e tronco afunilado (casca escura, rugosa)
        dec(x, 0.35f, z, 0.95f, 0.7f, 0.95f, 0.22f * tom, 0.16f * tom, 0.11f * tom, 3)
        dec(x, 1.9f, z, 0.46f, 3.2f, 0.46f, 0.26f * tom, 0.19f * tom, 0.13f * tom, 2)
        if (pinheiro) {
            // pinheiro em camadas irregulares, verde bem escuro
            var y = 2.4f; var w = 3.1f
            for (k in 0 until 5) {
                val d = (f2 - 0.5f) * 0.4f
                dec(x + d, y, z - d, w, 1.9f, w, 0.05f + 0.02f * k, 0.15f + 0.03f * k + f * 0.04f, 0.08f + 0.01f * k, 3)
                y += 1.15f; w *= 0.8f
            }
        } else if (f < 0.4f) {
            // arvore seca (estilo survival horror): galhos finos tortos, sem folhas
            dec(x, 4.0f, z, 0.30f, 2.2f, 0.30f, 0.20f, 0.17f, 0.14f, 2)
            dec(x + 0.7f, 4.6f, z + 0.2f, 0.16f, 2.0f, 0.16f, 0.19f, 0.16f, 0.13f, 2)
            dec(x - 0.8f, 4.3f, z - 0.3f, 0.16f, 1.8f, 0.16f, 0.19f, 0.16f, 0.13f, 2)
            dec(x + 0.3f, 5.4f, z - 0.9f, 0.12f, 1.5f, 0.12f, 0.18f, 0.15f, 0.12f, 2)
            dec(x - 0.4f, 5.6f, z + 0.8f, 0.12f, 1.4f, 0.12f, 0.18f, 0.15f, 0.12f, 2)
            dec(x + 1.3f, 5.0f, z + 0.9f, 0.10f, 1.1f, 0.10f, 0.17f, 0.14f, 0.11f, 2)
        } else {
            // copa irregular: varios tufos de tamanhos e tons diferentes, sombra por baixo
            dec(x, 3.9f, z, 2.4f * tom, 1.4f, 2.4f * tom, 0.07f, 0.14f, 0.07f, 1)
            dec(x + 0.9f, 4.7f, z + 0.5f, 1.9f, 1.7f, 1.8f, 0.11f * tom, 0.24f * tom, 0.10f * tom, 1)
            dec(x - 0.8f, 4.9f, z - 0.4f, 2.0f, 1.8f, 1.9f, 0.13f * tom, 0.27f * tom, 0.11f * tom, 1)
            dec(x + 0.1f, 5.7f, z + 0.1f, 1.7f, 1.5f, 1.7f, 0.15f * tom, 0.30f * tom, 0.12f * tom, 1)
            dec(x - 0.2f, 4.6f, z + 1.0f, 1.3f, 1.2f, 1.3f, 0.10f, 0.21f, 0.09f, 1)
        }
    }

    fun container(cx: Float, cz: Float, aoLongoX: Boolean, tipo: Int = 8) {
        if (warp != null && rig == null) { grupo(cx, cz) { container(cx, cz, aoLongoX, tipo) }; return }
        val c = if (aoLongoX) floatArrayOf(3f, 1.25f) else floatArrayOf(1.25f, 3f)
        caixa(cx - c[0], cz - c[1], cx + c[0], cz + c[1], 2.6f, tipo)
        dec(cx, 2.68f, cz, c[0] * 2 + 0.1f, 0.12f, c[1] * 2 + 0.1f, 0.20f, 0.20f, 0.20f)
    }

    fun carro(cx: Float, cz: Float, aoLongoX: Boolean, tipo: Int = 8) {
        if (warp != null && rig == null) { grupo(cx, cz) { carro(cx, cz, aoLongoX, tipo) }; return }
        val a = if (aoLongoX) floatArrayOf(2.1f, 0.9f) else floatArrayOf(0.9f, 2.1f)
        caixa(cx - a[0], cz - a[1], cx + a[0], cz + a[1], 1.0f, tipo)
        val b = if (aoLongoX) floatArrayOf(2.0f, 1.7f) else floatArrayOf(1.7f, 2.0f)
        dec(cx, 1.3f, cz, b[0], 0.6f, b[1], 0.10f, 0.12f, 0.15f)
    }

    fun bancada(x0: Float, z0: Float, x1: Float, z1: Float, tipo: Int = 1) = caixa(x0, z0, x1, z1, 1.0f, tipo)

    fun item(x: Float, z: Float, tipo: Int, y: Float = 0f) {
        var x = x; var z = z
        if (warp != null) { val r = tp(x, z); x = r[0]; z = r[1] }
        itensXZ.add(x); itensXZ.add(z); itensY.add(y); itensTipo.add(tipo)
    }

    fun zumbi(x: Float, z: Float) {
        if (warp != null) { val r = tp(x, z); zumbis.add(r[0]); zumbis.add(r[1]); return }
        zumbis.add(x); zumbis.add(z)
    }

    fun nascedouros(pares: FloatArray) {
        if (warp != null) {
            for (k in 0 until pares.size / 2) { val r = tp(pares[k * 2], pares[k * 2 + 1]); spawns.add(r[0]); spawns.add(r[1]) }
            return
        }
        for (v in pares) spawns.add(v)
    }

    /** Despeja o que foi montado nas constantes do jogo (antes de criar o Mundo). */
    fun aplicar(relevo: Boolean = false) {
        Constantes.PAREDES = par.toFloatArray()
        Constantes.PAREDES_BASE = parBase.toFloatArray()
        Constantes.PAREDES_ALTURA = parAlt.toFloatArray()
        Constantes.PAREDES_TIPO = parTipo.toIntArray()
        Constantes.PAREDES_ROT = if (girou) parRot.toFloatArray() else FloatArray(0)
        Constantes.PAREDES_OBB = if (girou) parObb.toFloatArray() else FloatArray(0)
        Constantes.PISOS = pisos.toFloatArray()
        Constantes.ALTOS = FloatArray(0)
        Constantes.ALTOS_ALTURA = FloatArray(0)
        Constantes.RAMPAS = FloatArray(0)
        Constantes.RAMPAS_ALTURAS = FloatArray(0)
        Constantes.RAMPAS_EIXO = IntArray(0)
        Constantes.BLOQUEIOS_INIMIGO = FloatArray(0)
        Constantes.ITENS_XZ = itensXZ.toFloatArray()
        Constantes.ITENS_Y = itensY.toFloatArray()
        Constantes.ITENS_TIPO = itensTipo.toIntArray()
        Constantes.INIMIGOS_XZ = zumbis.toFloatArray()
        Constantes.SPAWNS_XZ = spawns.toFloatArray()
        VilaDecoracao.CAIXAS = decor.toFloatArray()
        VilaDecoracao.TELHADOS = telhados.toFloatArray()
        Fases.portoes = portoes.toIntArray()
        if (relevo && Terreno.ativo) aplicarRelevo()
    }

    /** Sobe paredes, decoracao, telhados e itens para o relevo (predios ficam em plataformas planas). */
    private fun aplicarRelevo() {
        val p = Constantes.PAREDES
        for (k in 0 until p.size / 4) {
            if (Constantes.PAREDES_BASE[k] != 0f) {
                Constantes.PAREDES_BASE[k] += Terreno.alturaChao((p[k * 4] + p[k * 4 + 2]) / 2f, (p[k * 4 + 1] + p[k * 4 + 3]) / 2f) // verga de porta
                continue
            }
            Constantes.PAREDES_ALTURA[k] += Terreno.maxEm(p[k * 4], p[k * 4 + 1], p[k * 4 + 2], p[k * 4 + 3])
        }
        val t = VilaDecoracao.TELHADOS
        for (k in 0 until t.size / 11) t[k * 11 + 4] += Terreno.maxEm(t[k * 11], t[k * 11 + 1], t[k * 11 + 2], t[k * 11 + 3])
        val it = Constantes.ITENS_XZ
        for (k in 0 until it.size / 2) Constantes.ITENS_Y[k] += Terreno.alturaChao(it[k * 2], it[k * 2 + 1])
    }
}

object Fases {
    /** Copia da vila original (fase 1), tirada antes de qualquer troca. */
    private class Original(
        val par: FloatArray, val base: FloatArray, val alt: FloatArray, val tipo: IntArray, val pisos: FloatArray,
        val altos: FloatArray, val altosAlt: FloatArray, val rampas: FloatArray, val rampasAlt: FloatArray, val rampasEixo: IntArray,
        val bloq: FloatArray, val itXZ: FloatArray, val itY: FloatArray, val itTipo: IntArray, val zum: FloatArray, val spawns: FloatArray,
        val caixas: FloatArray, val telhados: FloatArray
    )

    private val original = Original(
        Constantes.PAREDES, Constantes.PAREDES_BASE, Constantes.PAREDES_ALTURA, Constantes.PAREDES_TIPO, Constantes.PISOS,
        Constantes.ALTOS, Constantes.ALTOS_ALTURA, Constantes.RAMPAS, Constantes.RAMPAS_ALTURAS, Constantes.RAMPAS_EIXO,
        Constantes.BLOQUEIOS_INIMIGO, Constantes.ITENS_XZ, Constantes.ITENS_Y, Constantes.ITENS_TIPO, Constantes.INIMIGOS_XZ,
        Constantes.SPAWNS_XZ, VilaDecoracao.CAIXAS, VilaDecoracao.TELHADOS
    )

    private fun restaurarVila() {
        val o = original
        Constantes.PAREDES = o.par; Constantes.PAREDES_BASE = o.base; Constantes.PAREDES_ALTURA = o.alt; Constantes.PAREDES_TIPO = o.tipo
        Constantes.PAREDES_ROT = FloatArray(0); Constantes.PAREDES_OBB = FloatArray(0)
        Constantes.PISOS = o.pisos; Constantes.ALTOS = o.altos; Constantes.ALTOS_ALTURA = o.altosAlt
        Constantes.RAMPAS = o.rampas; Constantes.RAMPAS_ALTURAS = o.rampasAlt; Constantes.RAMPAS_EIXO = o.rampasEixo
        Constantes.BLOQUEIOS_INIMIGO = o.bloq; Constantes.ITENS_XZ = o.itXZ; Constantes.ITENS_Y = o.itY; Constantes.ITENS_TIPO = o.itTipo
        Constantes.INIMIGOS_XZ = o.zum; Constantes.SPAWNS_XZ = o.spawns
        VilaDecoracao.CAIXAS = o.caixas; VilaDecoracao.TELHADOS = o.telhados
    }

    /** Fase carregada agora (a que o proximo `Mundo` vai usar). */
    var atual: DefFase = definicoes()[0]
        private set
    var indice = 0
        private set

    /** Indices (em Constantes.PAREDES) das caixas que sao portoes da fase carregada. */
    var portoes = IntArray(0)

    val total: Int
        get() = definicoes().size

    private var cache: List<DefFase>? = null

    fun definicoes(): List<DefFase> {
        val c = cache
        if (c != null) return c
        val lista = listOf(CidadeMapa.definicao())
        cache = lista
        return lista
    }

    /** Troca o mapa das constantes pelo da fase `i` (0 = vila). Chamar ANTES de criar o Mundo. */
    fun carregar(i: Int) {
        val d = definicoes()[i.coerceIn(0, definicoes().size - 1)]
        indice = definicoes().indexOf(d)
        atual = d
        val construir = d.construir
        portoes = IntArray(0)
        Terreno.limpar()
        if (construir == null) {
            restaurarVila()
        } else {
            val b = MapaBuilder()
            construir(b)
            b.aplicar(d.cidade)
        }
        Navegacao.reconstruir()
    }

    // =========================================================================================== FASE 1: A VILA
    private fun vila() = DefFase(
        nome = "A VILA", subtitulo = "Capitulo 1", artefato = "Fragmento do Sino",
        miniNome = "Capataz", chefeNome = "O Pastor",
        inicio = floatArrayOf(0f, 0f), mercador = floatArrayOf(8f, 0f),
        fragmentos = floatArrayOf(21f, -27f, -26f, 15f, 24f, 18f),
        alavancas = floatArrayOf(-3f, 46f, 44f, -3f, -52f, -2f),
        ordem = intArrayOf(2, 0, 1),
        altar = floatArrayOf(0f, -52f),
        baus = floatArrayOf(-43f, 40f, 62f, -26f, 0f, -56f, 6f, -57f, 48f, -46f),
        miniSpawn = floatArrayOf(12f, -22f), chefeSpawn = floatArrayOf(0f, -44f),
        dicas = arrayOf(
            "Procure os fragmentos nas casas da vila.",
            "O Capataz guarda o proximo fragmento!",
            "O ultimo fragmento esta trancado. Ache as 3 alavancas.",
            "Leve os 3 fragmentos ao altar da igreja.",
            "Derrote o chefe!"
        ),
        spawnMax = 9, spawnDist = 45f, spawnIntervalo = 7f, fatorVida = 1f, construir = null
    )

    // =========================================================================================== FASE 2: O POSTO
    private fun posto() = DefFase(
        nome = "O POSTO", subtitulo = "Capitulo 2", artefato = "Engrenagem",
        miniNome = "O Mecanico", chefeNome = "O Caminhoneiro",
        inicio = floatArrayOf(0f, 40f), mercador = floatArrayOf(8f, 40f),
        fragmentos = floatArrayOf(-36f, 35f, 32f, -32f, -32f, 15f),
        alavancas = floatArrayOf(16f, 40f, -20f, -8f, 24f, -10f),
        ordem = intArrayOf(1, 2, 0),
        altar = floatArrayOf(0f, -30f),
        baus = floatArrayOf(-44f, -44f, 44f, 44f, 44f, -44f, -45f, 0f),
        miniSpawn = floatArrayOf(26f, -18f), chefeSpawn = floatArrayOf(0f, -40f),
        dicas = arrayOf(
            "Procure as engrenagens: motel e oficina.",
            "O Mecanico guarda a proxima engrenagem!",
            "A ultima engrenagem esta no quarto trancado. Ache as 3 alavancas.",
            "Leve as 3 engrenagens ao guincho, no norte.",
            "Derrote o chefe!"
        ),
        spawnMax = 10, spawnDist = 30f, spawnIntervalo = 6f, fatorVida = 1.3f,
        construir = { b ->
            b.cerco(-48f, -48f, 48f, 48f, 3f, 4)
            b.predio(-12f, -16f, 12f, -4f, 2, 0f, 4f, 3.4f, 6)              // loja do posto
            for (x in floatArrayOf(-6f, -2f, 2f, 6f)) b.caixa(x - 0.6f, 1.6f, x + 0.6f, 2.4f, 1.6f, 8)
            b.caixa(-9f, 0f, -8.6f, 0.4f, 4.6f, 1); b.caixa(8.6f, 0f, 9f, 0.4f, 4.6f, 1)
            b.dec(0f, 4.7f, 3f, 20f, 0.3f, 7f, 0.55f, 0.18f, 0.14f)         // cobertura das bombas
            b.predio(22f, -40f, 42f, -24f, 3, -32f, 4f, 3.4f, 7)             // oficina
            b.predio(-42f, 8f, -22f, 22f, 1, 15f, 3f, 3f, 8)                 // motel A
            b.predio(-42f, 28f, -30f, 42f, 0, -36f, 3f, 3f, 8)               // motel B
            b.container(20f, 18f, true, 3); b.container(30f, 18f, true, 8); b.container(20f, 24f, true, 7); b.container(34f, 28f, false, 3)
            b.container(14f, 32f, false, 8)
            b.carro(-20f, -34f, true); b.carro(-30f, -26f, false, 6); b.carro(8f, 30f, true, 7); b.carro(-8f, 36f, false, 6)
            b.carro(36f, 4f, false); b.carro(36f, -8f, false, 7); b.carro(-16f, 14f, true, 6)
            for (x in -44..44 step 11) { b.arvore(x.toFloat(), -45f, true); b.arvore(x.toFloat() + 5f, 45f, true) }
            b.arvore(-45f, -20f, true); b.arvore(-45f, 20f, true); b.arvore(45f, -20f, true); b.arvore(45f, 20f, true)
            b.item(-2f, 36f, 0); b.item(2f, 36f, 1); b.item(-6f, -2f, 1, 0f); b.item(30f, -30f, 0); b.item(-34f, 14f, 1); b.item(18f, 28f, 0)
            b.item(0f, 34f, 1); b.item(-26f, 36f, 1); b.item(24f, 8f, 0)
            b.zumbi(-4f, 6f); b.zumbi(10f, -12f); b.zumbi(-20f, 4f); b.zumbi(30f, 10f); b.zumbi(-30f, -12f); b.zumbi(14f, 20f); b.zumbi(0f, -22f)
            b.nascedouros(floatArrayOf(-44f, -44f, 44f, -44f, -44f, 44f, 44f, 44f, 0f, -44f, -44f, 0f, 44f, 0f, 20f, -44f, -20f, -44f))
        }
    )

    // =========================================================================================== FASE 3: O HOSPITAL
    private fun hospital() = DefFase(
        nome = "O HOSPITAL", subtitulo = "Capitulo 3", artefato = "Cartao de acesso",
        miniNome = "O Enfermeiro", chefeNome = "O Doutor",
        inicio = floatArrayOf(0f, 40f), mercador = floatArrayOf(-8f, 40f),
        fragmentos = floatArrayOf(-35f, 17f, 36f, 0f, 0f, -28f),
        alavancas = floatArrayOf(-24f, 6f, 24f, 6f, 0f, -16f),
        ordem = intArrayOf(0, 2, 1),
        altar = floatArrayOf(0f, -40f),
        baus = floatArrayOf(-44f, -44f, 44f, -44f, 44f, 44f, -44f, 44f, 14f, -16f),
        miniSpawn = floatArrayOf(30f, 14f), chefeSpawn = floatArrayOf(0f, -42f),
        dicas = arrayOf(
            "Procure os cartoes de acesso: garagem e necroterio.",
            "O Enfermeiro guarda o proximo cartao!",
            "O ultimo cartao esta no corredor. Ache as 3 alavancas.",
            "Leve os 3 cartoes a sala do Doutor, no norte.",
            "Derrote o chefe!"
        ),
        spawnMax = 11, spawnDist = 28f, spawnIntervalo = 5.5f, fatorVida = 1.7f,
        construir = { b ->
            b.cerco(-48f, -48f, 48f, 48f, 3.5f, 1)
            b.predio(-26f, -34f, 26f, -10f, 2, 0f, 5f, 4.5f, 6)              // hospital
            b.muro(-8f, -33.6f, -8f, -24f, 3f, 6, floatArrayOf(), 3f)         // divisorias internas
            b.muro(8f, -33.6f, 8f, -24f, 3f, 6, floatArrayOf(), 3f)
            b.muro(-25.6f, -22f, -14f, -22f, 3f, 6, floatArrayOf(-19f), 3f)
            b.muro(14f, -22f, 25.6f, -22f, 3f, 6, floatArrayOf(19f), 3f)
            b.predio(28f, -8f, 44f, 8f, 3, 0f, 4f, 3.4f, 8)                  // necroterio
            b.predio(-44f, 10f, -26f, 24f, 1, 17f, 4f, 3.4f, 7)              // garagem
            b.carro(-12f, 30f, true, 6); b.carro(14f, 34f, false, 8); b.carro(30f, 24f, true, 7); b.carro(-32f, -2f, false, 6)
            b.bancada(-14f, 16f, -8f, 17f); b.bancada(8f, 16f, 14f, 17f); b.bancada(-4f, 24f, 4f, 25f)
            b.container(-34f, 36f, true, 8); b.container(36f, 38f, true, 3)
            for (x in -44..44 step 11) b.arvore(x.toFloat() + 3f, 45f)
            for (z in -30..30 step 15) { b.arvore(-45f, z.toFloat()); b.arvore(45f, z.toFloat() + 2f) }
            b.item(-4f, 36f, 0); b.item(4f, 38f, 1); b.item(-10f, -12f, 1); b.item(0f, 2f, 0); b.item(30f, 0f, 1); b.item(-34f, 18f, 0)
            b.item(10f, -16f, 1); b.item(-14f, 24f, 1); b.item(20f, 20f, 3)
            b.zumbi(-6f, 10f); b.zumbi(8f, 4f); b.zumbi(-18f, 26f); b.zumbi(20f, 30f); b.zumbi(0f, -4f); b.zumbi(34f, -20f); b.zumbi(-34f, -22f); b.zumbi(12f, -16f)
            b.nascedouros(floatArrayOf(-44f, -44f, 44f, -44f, -44f, 44f, 44f, 44f, 0f, -44f, -44f, 0f, 44f, 20f, 20f, 44f, -24f, 44f))
        }
    )

    // =========================================================================================== FASE 4: O BUNKER
    private fun bunker() = DefFase(
        nome = "O BUNKER", subtitulo = "Capitulo 4 - final", artefato = "Nucleo",
        miniNome = "A Sentinela", chefeNome = "O Hospedeiro",
        inicio = floatArrayOf(0f, 34f), mercador = floatArrayOf(-6f, 34f),
        fragmentos = floatArrayOf(-28f, 28f, 28f, -28f, 0f, 3f),
        alavancas = floatArrayOf(-28f, -2f, 28f, 2f, 2f, 20f),
        ordem = intArrayOf(2, 1, 0),
        altar = floatArrayOf(0f, -8f),
        baus = floatArrayOf(-34f, -34f, 34f, -34f, 34f, 34f, -34f, 34f),
        miniSpawn = floatArrayOf(18f, -20f), chefeSpawn = floatArrayOf(7f, -9f),
        dicas = arrayOf(
            "Procure os nucleos nas salas do bunker.",
            "A Sentinela guarda o proximo nucleo!",
            "O ultimo nucleo esta no cofre central. Ache as 3 alavancas.",
            "Leve os 3 nucleos ao reator, no centro.",
            "Derrote o Hospedeiro!"
        ),
        spawnMax = 12, spawnDist = 22f, spawnIntervalo = 5f, fatorVida = 2.2f,
        construir = { b ->
            b.cerco(-40f, -40f, 40f, 40f, 4f, 8)
            // paredes internas: 3 x 3 salas com portas
            b.muro(-40f, -13f, 40f, -13f, 3.5f, 8, floatArrayOf(-27f, 0f, 27f), 4f)
            b.muro(-40f, 13f, 40f, 13f, 3.5f, 8, floatArrayOf(-20f, 0f, 27f), 4f)
            b.muro(-13f, -40f, -13f, 40f, 3.5f, 8, floatArrayOf(-27f, 0f, 27f), 4f)
            b.muro(13f, -40f, 13f, 40f, 3.5f, 8, floatArrayOf(-20f, 0f, 27f), 4f)
            b.caixa(-2f, -4f, 2f, -0.5f, 1.2f, 1)                             // reator
            b.container(-28f, -33f, true, 3); b.container(28f, -33f, true, 7); b.container(-28f, 20f, true, 8); b.container(28f, 22f, false, 3)
            b.carro(-6f, 22f, true, 6)
            b.item(-2f, 30f, 0); b.item(2f, 30f, 1); b.item(-30f, 0f, 1); b.item(30f, 0f, 0); b.item(0f, -30f, 1); b.item(-30f, -30f, 0)
            b.item(30f, -30f, 1); b.item(0f, 8f, 3)
            b.zumbi(-30f, 18f); b.zumbi(30f, 18f); b.zumbi(-30f, -6f); b.zumbi(30f, -6f); b.zumbi(0f, -22f); b.zumbi(-20f, -30f); b.zumbi(20f, -30f); b.zumbi(0f, 18f)
            b.nascedouros(floatArrayOf(-34f, -34f, 34f, -34f, -34f, 34f, 34f, 34f, 0f, -34f, -34f, 0f, 34f, 0f, 0f, 34f))
        }
    )
}
