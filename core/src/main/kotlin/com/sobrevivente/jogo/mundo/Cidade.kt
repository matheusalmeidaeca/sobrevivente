// core/src/main/kotlin/com/sobrevivente/jogo/mundo/Cidade.kt
package com.sobrevivente.jogo.mundo

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.sobrevivente.jogo.Constantes

/**
 * A CIDADE: vielas estreitas (5,3 m) entre predios altos, estilo Resident Evil. Quatro bairros em faixas (sul -> norte),
 * separados por muralhas com UM portao trancado cada; dentro de cada bairro, barricadas (containers) forcam um caminho
 * em zigue-zague. Tudo e calculado a partir da grade de ruas (`xs`, `Faixa.zc`) para o mapa e a missao concordarem.
 * Norte = -Z. O jogador comeca no sul (z ~ +143).
 */
object CidadeMapa {
    private const val W = 148f
    const val SW = 5.3f
    private const val BW = 27f

    /** Centro da rua vertical k (0..9). */
    fun xs(k: Int): Float = -W + SW / 2f + k * (BW + SW)
    /** Borda oeste do bloco da coluna c (0..8). */
    fun bx0(c: Int): Float = -W + SW + c * (BW + SW)

    /** Deformacao organica do mapa: ruas curvas, quarteiroes inclinados. Devolve (x, z, giro em radianos). Deslocamento maximo ~10 m, inclinacao ~0,2. */
    fun w(x: Float, z: Float): FloatArray {
        fun fx(a: Float, b: Float) = a + 7f * kotlin.math.sin(b / 55f + 0.7f) + 3f * kotlin.math.sin((a + b) / 40f + 2.1f)
        fun fz(a: Float, b: Float) = b + 7f * kotlin.math.sin(a / 62f + 1.9f) + 3f * kotlin.math.sin((a - b) / 47f + 0.4f)
        val e = 1.5f
        // direcao do eixo +x e do eixo +z deformados; o giro medio vira o giro rigido do quarteirao
        val ax = fx(x + e, z) - fx(x - e, z); val az = fz(x + e, z) - fz(x - e, z)
        val bx = fx(x, z + e) - fx(x, z - e); val bz = fz(x, z + e) - fz(x, z - e)
        val g1 = kotlin.math.atan2(-az, ax)
        val g2 = kotlin.math.atan2(bx, bz)
        return floatArrayOf(fx(x, z), fz(x, z), (g1 + g2) / 2f)
    }

    /** Pares (x, z) do mapa reto -> mundo. */
    fun wa(a: FloatArray): FloatArray {
        val o = FloatArray(a.size)
        for (k in 0 until a.size / 2) { val r = w(a[k * 2], a[k * 2 + 1]); o[k * 2] = r[0]; o[k * 2 + 1] = r[1] }
        return o
    }

    /** Faixa de z com `n` fileiras de blocos e n+1 ruas horizontais. */
    class Faixa(val zN: Float, val zS: Float, val n: Int) {
        val bd: Float = ((zS - zN) - (n + 1) * SW) / n
        fun zc(r: Int): Float = zN + SW / 2f + r * (bd + SW)
        fun z0(r: Int): Float = zN + SW + r * (bd + SW)
        fun z1(r: Int): Float = z0(r) + bd
    }

    val B1 = Faixa(62f, 146f, 3)
    val B2 = Faixa(-3f, 58f, 2)
    val B3 = Faixa(-63f, -7f, 2)
    val B4 = Faixa(-98f, -67f, 1)

    // portoes A (azul), B (eletrico), C (alavancas), D (parceiro): sempre abordados pelo sul (+z)
    val PORTAO_Z = floatArrayOf(60f, -5f, -65f, -100f)
    val PORTAO_X = floatArrayOf(xs(8), xs(1), xs(7), xs(5))

    val INICIO_X = xs(5)
    val INICIO_Z = B1.zc(3)
    val MERCADOR = floatArrayOf(xs(4), B1.zc(3))

    // garagem (B1, linha 1, coluna 0)
    val CHAVE_AZUL = floatArrayOf(bx0(0) + BW / 2f, (B1.z0(1) + B1.z1(1)) / 2f)
    // supermercado, farmacia, banco
    val FUSIVEIS = floatArrayOf(
        bx0(1) + BW / 2f, (B2.z0(0) + B2.z1(0)) / 2f,
        bx0(7) + BW / 2f, (B2.z0(0) + B2.z1(0)) / 2f,
        bx0(4) + BW / 2f, (B2.z0(1) + B2.z1(1)) / 2f
    )
    val PAINEL = floatArrayOf(xs(5), B2.zc(1))
    val MINI_SPAWN = floatArrayOf(xs(8), B2.zc(1))
    // metro (B3, linha 1, coluna 6) e uma nota na rua
    val NOTAS = floatArrayOf(bx0(6) + BW / 2f, (B3.z0(1) + B3.z1(1)) / 2f, xs(3), B3.zc(2))
    /** 0 azul, 1 vermelha, 2 amarela. */
    val ALAVANCAS = floatArrayOf(xs(2), B3.zc(0), xs(8), B3.zc(1), xs(5), B3.zc(2))
    val ORDEM = intArrayOf(1, 2, 0)
    // delegacia (B3, linha 0, coluna 1): cela ao norte, divisoria em zp
    private val DX0 = bx0(1)
    private val DZ0 = B3.z0(0)
    private val DZP = DZ0 + 8f
    private val DXG = DX0 + 7f
    val CELA = floatArrayOf(DXG, DZP + 2f)
    val SOBREVIVENTE = floatArrayOf(DXG, DZ0 + 4f)
    val RADIO = floatArrayOf(0f, -124f)
    val CHEFE_SPAWN = floatArrayOf(0f, -141f)

    /** Intersecoes (faixa, rua horizontal, rua vertical) com bau. */
    private val BAUS_INTERSECOES = arrayOf(
        intArrayOf(0, 3, 1), intArrayOf(0, 0, 2), intArrayOf(0, 1, 9), intArrayOf(0, 3, 9),
        intArrayOf(1, 0, 9), intArrayOf(1, 2, 0), intArrayOf(1, 1, 0),
        intArrayOf(2, 0, 0), intArrayOf(2, 2, 9), intArrayOf(2, 1, 9),
        intArrayOf(3, 0, 1), intArrayOf(3, 1, 8), intArrayOf(3, 0, 8)
    )

    val MERCADOR_W by lazy { wa(MERCADOR) }
    val CHAVE_AZUL_W by lazy { wa(CHAVE_AZUL) }
    val FUSIVEIS_W by lazy { wa(FUSIVEIS) }
    val PAINEL_W by lazy { wa(PAINEL) }
    val NOTAS_W by lazy { wa(NOTAS) }
    val ALAVANCAS_W by lazy { wa(ALAVANCAS) }
    val CELA_W by lazy { wa(CELA) }
    val SOBREVIVENTE_W by lazy { wa(SOBREVIVENTE) }
    val RADIO_W by lazy { wa(RADIO) }
    val MINI_SPAWN_W by lazy { wa(MINI_SPAWN) }
    val CHEFE_SPAWN_W by lazy { wa(CHEFE_SPAWN) }
    val BAUS_W by lazy { wa(BAUS) }
    /** Objetos dos portoes (um pouco ao sul de cada muralha). */
    val PORTAO_OBJ_W by lazy { wa(FloatArray(8) { if (it % 2 == 0) PORTAO_X[it / 2] else PORTAO_Z[it / 2] + 3.5f }) }

    private fun faixas() = listOf(B1, B2, B3, B4)

    val BAUS: FloatArray by lazy {
        val l = ArrayList<Float>()
        for (p in BAUS_INTERSECOES) {
            l.add(xs(p[2]) + 1.3f); l.add(faixas()[p[0]].zc(p[1]) + 1.3f)
        }
        l.add(-35f); l.add(-135f); l.add(35f); l.add(-135f)
        l.toFloatArray()
    }

    fun definicao() = DefFase(
        nome = "A CIDADE", subtitulo = "Capitulo unico", artefato = "Fusivel",
        miniNome = "O Seguranca", chefeNome = "O Hospedeiro",
        inicio = wa(floatArrayOf(INICIO_X, INICIO_Z)), mercador = wa(MERCADOR),
        fragmentos = FloatArray(6), alavancas = wa(ALAVANCAS), ordem = ORDEM, altar = wa(RADIO),
        baus = wa(BAUS), miniSpawn = wa(MINI_SPAWN), chefeSpawn = wa(CHEFE_SPAWN), dicas = arrayOf(),
        spawnMax = 12, spawnDist = 38f, spawnIntervalo = 6f, fatorVida = 1.8f,
        construir = { b -> construir(b) }, cidade = true
    )

    /** Bairros (imagem Ravenport): 0 comercial em L, 1 residencial, 2 industrial, 3 centro (torres), 4 hospital, 5 estadio, 6 parque. */
    private fun estilo(bi: Int, r: Int, c: Int, f: Faixa): Int {
        val cx = bx0(c) + BW / 2f
        val cz = (f.z0(r) + f.z1(r)) / 2f
        if (bi == 1 && r == 0 && c == 3) return 4
        if (bi == 0 && r == 2 && c == 7) return 5
        if (bi == 0 && r == 2 && c == 1) return 6
        if (cx > 50f && cz < 30f) return 2
        if (kotlin.math.abs(cx) < 60f && cz > -70f && cz < 30f) return 3
        if (cx < -40f && cz < 30f) return 1
        return 0
    }

    private fun bloco(b: MapaBuilder, f: Faixa, banda: Int, r: Int, c: Int) {
        val e = estilo(banda, r, c, f)
        if (e != 0) { blocoBairro(b, f, banda, r, c, e); return }
        blocoL(b, f, banda, r, c)
    }

    private fun blocoBairro(b: MapaBuilder, f: Faixa, banda: Int, r: Int, c: Int, e: Int) {
        val x0 = bx0(c); val x1 = x0 + BW; val z0 = f.z0(r); val z1 = f.z1(r)
        val cx = (x0 + x1) / 2f; val cz = (z0 + z1) / 2f
        val dz = z1 - z0
        val v = (c * 3 + r * 5 + banda * 7)
        when (e) {
            1 -> { // casas em quatro lotes, com jardim
                val w = (BW - 6f) / 2f; val d = (dz - 6f) / 2f
                for (i in 0..1) for (j in 0..1) {
                    val hx0 = x0 + 1.5f + i * (w + 3f); val hz0 = z0 + 1.5f + j * (d + 3f)
                    val alt = 5.5f + ((v + i * 2 + j) % 3) * 1.2f
                    val tipo = if ((v + i + j) % 2 == 0) 14 else 15
                    b.caixa(hx0, hz0, hx0 + w, hz0 + d, alt, tipo)
                    // telhado: laje larga + cumeeira
                    b.dec(hx0 + w / 2f, alt + 0.3f, hz0 + d / 2f, w + 1.2f, 0.6f, d + 1.2f, 0.32f, 0.17f, 0.13f)
                    b.dec(hx0 + w / 2f, alt + 1.2f, hz0 + d / 2f, w * 0.7f, 1.2f, d * 0.55f, 0.28f, 0.15f, 0.12f)
                    if ((v + i + j) % 3 != 0) b.arvore(hx0 + w / 2f, hz0 + d + 1.4f)
                }
            }
            2 -> { // galpao + silos + containers
                val dg = dz * 0.55f
                b.caixa(x0 + 1f, z0 + 1f, x1 - 1f, z0 + 1f + dg, 9f, 12)
                b.dec(cx, 9.3f, z0 + 1f + dg / 2f, BW - 1.4f, 0.6f, dg + 0.6f, 0.30f, 0.30f, 0.32f)
                for (k in 0..1) {
                    val sx = x0 + 6f + k * 9f; val sz = z1 - 5f
                    b.caixa(sx - 2.5f, sz - 2.5f, sx + 2.5f, sz + 2.5f, 12f, 12)
                    b.dec(sx, 12.3f, sz, 5.2f, 0.6f, 5.2f, 0.45f, 0.45f, 0.47f, 2)
                }
                b.container(x1 - 6f, z1 - 4.5f, true, 3)
                b.container(x1 - 6f, z1 - 7.5f, true, 8)
                b.dec(x0 + 5f, 14f, z0 + 4f, 0.8f, 8f, 0.8f, 0.22f, 0.20f, 0.20f, 2)
            }
            3 -> { // centro: podio + torre
                val alt = 10f + (v % 3) * 2f
                b.caixa(x0, z0, x1, z1, alt, 13)
                val tx = if (v % 2 == 0) x0 + 1.5f else x1 - 15f
                val th = 28f + (v % 4) * 6f
                b.caixa(tx, z0 + 3f, tx + 13.5f, z1 - 3f, th, 13 + (v % 2))
                b.dec(tx + 6.75f, th + 0.25f, cz, 14f, 0.5f, dz - 5.5f, 0.16f, 0.16f, 0.18f)
                b.dec(tx + 6.75f, th + 5f, cz, 0.4f, 10f, 0.4f, 0.55f, 0.1f, 0.1f, 2)
                b.dec(cx, 0.45f, cz, BW + 0.35f, 0.9f, dz + 0.35f, 0.14f, 0.14f, 0.15f)
            }
            4 -> { // hospital: duas alas e corredor, cruz vermelha e heliponto
                val alt = 17f
                b.caixa(x0 + 1f, z0 + 1f, x0 + 9f, z1 - 1f, alt, 13)
                b.caixa(x1 - 9f, z0 + 1f, x1 - 1f, z1 - 1f, alt, 13)
                b.caixa(x0 + 9f, cz - 4f, x1 - 9f, cz + 4f, 10f, 13)
                b.dec(x0 + 5f, alt + 0.2f, cz, 8.4f, 0.4f, dz - 1.6f, 0.85f, 0.85f, 0.88f)
                b.dec(x1 - 5f, alt + 0.1f, cz, 7f, 0.2f, 7f, 0.20f, 0.45f, 0.30f)
                // cruz vermelha na fachada sul
                b.dec(x0 + 5f, 9f, z1 - 0.5f, 3f, 1f, 0.3f, 0.85f, 0.1f, 0.1f)
                b.dec(x0 + 5f, 9f, z1 - 0.5f, 1f, 3f, 0.3f, 0.85f, 0.1f, 0.1f)
            }
            5 -> { // estadio: anel de arquibancadas com abertura ao sul e gramado
                val alt = 10f
                b.caixa(x0 + 1f, z0 + 1f, x1 - 1f, z0 + 5f, alt, 14)
                b.caixa(x0 + 1f, z0 + 5f, x0 + 5f, z1 - 1f, alt, 14)
                b.caixa(x1 - 5f, z0 + 5f, x1 - 1f, z1 - 1f, alt, 14)
                b.caixa(x0 + 5f, z1 - 5f, cx - 4f, z1 - 1f, alt, 14)
                b.caixa(cx + 4f, z1 - 5f, x1 - 5f, z1 - 1f, alt, 14)
                b.dec(cx, 0.12f, cz, BW - 10f, 0.24f, dz - 10f, 0.16f, 0.45f, 0.18f)
                for (k in 0..3) { val a = if (k % 2 == 0) x0 + 1f else x1 - 1f; val bz = if (k < 2) z0 + 1f else z1 - 1f
                    b.dec(a, 14f, bz, 0.5f, 8f, 0.5f, 0.6f, 0.6f, 0.62f, 2); b.dec(a, 18.3f, bz, 1.6f, 0.5f, 1.6f, 0.95f, 0.95f, 0.8f, 1) }
            }
            6 -> { // parque: gramado, lago e arvores
                b.dec(cx, 0.1f, cz, BW - 1f, 0.2f, dz - 1f, 0.17f, 0.38f, 0.16f)
                b.dec(cx, 0.22f, cz, 10f, 0.1f, 7f, 0.10f, 0.26f, 0.40f, 2)
                for (k in 0 until 9) {
                    val tx = x0 + 3f + ((k * 7 + v) % 5) * 5.2f; val tz = z0 + 3f + ((k * 3 + v) % 3) * (dz - 6f) / 2f
                    if (kotlin.math.abs(tx - cx) < 7f && kotlin.math.abs(tz - cz) < 5f) continue
                    b.arvore(tx, tz, k % 3 == 0)
                }
                b.bancada(x0 + 3f, cz - 0.5f, x0 + 5.5f, cz + 0.5f, 1)
            }
        }
    }

    private fun blocoL(b: MapaBuilder, f: Faixa, banda: Int, r: Int, c: Int) {
        val alt = 12f + 4f * ((c * 3 + r * 5 + banda) % 4)
        val tipo = 12 + (c + r + banda) % 4
        val x0 = bx0(c); val x1 = x0 + BW; val z0 = f.z0(r); val z1 = f.z1(r)
        val cx = (x0 + x1) / 2f; val cz = (z0 + z1) / 2f
        val ele = (c + r * 2 + banda) % 3 == 0
        if (ele) { // forma em L: ala principal + ala mais baixa
            b.caixa(x0, z0, x1, z0 + (z1 - z0) * 0.6f, alt, tipo)
            b.caixa(x0, z0 + (z1 - z0) * 0.6f - 0.2f, x0 + BW * 0.5f, z1, alt * 0.6f, tipo)
        } else b.caixa(x0, z0, x1, z1, alt, tipo)
        if (!ele) b.dec(cx, alt + 0.15f, cz, BW + 0.4f, 0.3f, (z1 - z0) + 0.4f, 0.16f, 0.16f, 0.18f)
        // Detalhes que quebram o "bloco": soco escuro na base, cornija que sobressai, quinas, caixa d'agua e ar-condicionado no topo.
        val hx = BW / 2f; val hz = (z1 - z0) / 2f
        if (!ele) b.dec(cx, 0.45f, cz, BW + 0.35f, 0.9f, (z1 - z0) + 0.35f, 0.14f, 0.14f, 0.15f)
        if (!ele) b.dec(cx, alt - 0.25f, cz, BW + 0.7f, 0.5f, (z1 - z0) + 0.7f, 0.50f, 0.49f, 0.47f)
        if (!ele) b.dec(cx, alt - 0.7f, cz, BW + 0.45f, 0.25f, (z1 - z0) + 0.45f, 0.22f, 0.22f, 0.23f)
        if (!ele) for (sx in floatArrayOf(-1f, 1f)) for (sz in floatArrayOf(-1f, 1f)) {
            b.dec(cx + sx * hx, alt * 0.5f, cz + sz * hz, 0.5f, alt, 0.5f, 0.20f, 0.20f, 0.21f)
        }
        val h = (c * 5 + r * 11 + banda * 3) % 4
        if (h == 0) { // caixa d'agua
            b.dec(cx + 5f, alt + 1.5f, cz, 3.2f, 2.4f, 3.2f, 0.34f, 0.26f, 0.18f, 2)
        } else if (h == 1) { // casa de maquinas + chamine
            b.dec(cx - 6f, alt + 1.2f, cz + 2f, 4f, 2.2f, 3.2f, 0.28f, 0.28f, 0.30f)
            b.dec(cx + 4f, alt + 2f, cz - 3f, 1.0f, 4f, 1.0f, 0.22f, 0.20f, 0.20f, 2)
        } else if (h == 2) { // ar-condicionado em fileira
            for (k in -2..2) b.dec(cx + k * 3.5f, alt + 0.6f, cz, 2.2f, 1.1f, 1.8f, 0.55f, 0.56f, 0.58f)
        }
        // letreiro/toldo na base voltado para a rua (face sul), alguns blocos
        if ((c + r + banda) % 3 == 1) {
            val cor = floatArrayOf(0.62f, 0.14f, 0.12f, 0.12f, 0.30f, 0.46f, 0.55f, 0.45f, 0.10f)
            val q = ((c + r) % 3) * 3
            b.dec(cx, 3.1f, z1 + 0.7f, 9f, 0.35f, 1.4f, cor[q], cor[q + 1], cor[q + 2])
        }
    }

    /** Muralha de lado a lado com um unico vao (o portao) em gx. Devolve o indice do portao. */
    private fun separador(b: MapaBuilder, z: Float, gx: Float, tipo: Int): Int {
        b.caixa(-W + 0.4f, z - 2f, gx - 2f, z + 2f, 7f, 12)
        b.caixa(gx + 2f, z - 2f, W - 0.4f, z + 2f, 7f, 12)
        val idx = b.portao(gx - 2f, z - 1.2f, gx + 2f, z + 1.2f, 7f, tipo)
        val (r, g, bl) = when (tipo) {
            16 -> Triple(0.2f, 0.45f, 0.95f)
            19 -> Triple(0.6f, 0.6f, 0.68f)
            18 -> Triple(0.92f, 0.8f, 0.18f)
            else -> Triple(0.3f, 0.82f, 0.45f)
        }
        b.dec(gx - 2.1f, 3.5f, z + 2.1f, 0.4f, 7f, 0.4f, r, g, bl, 2)
        b.dec(gx + 2.1f, 3.5f, z + 2.1f, 0.4f, 7f, 0.4f, r, g, bl, 2)
        return idx
    }

    /** Barricada na rua vertical k, no meio da fileira r (entre as ruas horizontais r e r+1). */
    private fun barV(b: MapaBuilder, f: Faixa, k: Int, r: Int) = b.container(xs(k), (f.z0(r) + f.z1(r)) / 2f, true, 8)

    /** Barricada na rua horizontal r, no meio da coluna k (entre as ruas verticais k e k+1). */
    private fun barH(b: MapaBuilder, f: Faixa, r: Int, k: Int) = b.container(bx0(k) + BW / 2f, f.zc(r), false, 3)

    private fun distInicio(x: Float, z: Float): Float {
        val dx = x - INICIO_X; val dz = z - INICIO_Z
        return kotlin.math.sqrt(dx * dx + dz * dz)
    }

    fun construir(b: MapaBuilder) {
        b.warp = { x, z -> w(x, z) }
        // rios (decoracao + vale) ao longo das muralhas dos portoes A e C: o portao vira a ponte
        val rios = ArrayList<FloatArray>()
        for (zl in floatArrayOf(PORTAO_Z[0], PORTAO_Z[2])) {
            val pts = ArrayList<Float>()
            var xl = -W
            while (xl <= W + 0.1f) { val r = w(xl, zl); pts.add(r[0]); pts.add(r[1]); xl += 6f }
            rios.add(pts.toFloatArray())
        }
        Terreno.construir(rios)
        b.cerco(-W, -W, W, W, 4f, 12)
        val fs = faixas()

        // ---- blocos solidos (menos os predios que se pode entrar)
        val entravel = setOf(
            Triple(0, 1, 0),   // garagem
            Triple(1, 0, 1), Triple(1, 0, 7), Triple(1, 1, 4),   // supermercado, farmacia, banco
            Triple(2, 1, 6), Triple(2, 0, 1)                     // metro, delegacia
        )
        for (bi in fs.indices) {
            val f = fs[bi]
            for (r in 0 until f.n) for (c in 0..8) {
                if (Triple(bi, r, c) in entravel) continue
                b.grupo(bx0(c) + BW / 2f, (f.z0(r) + f.z1(r)) / 2f, 0.88f) { bloco(b, f, bi, r, c) }
            }
        }

        // ---- predios enteraveis (porta para a viela)
        val g0 = Triple(B1, 1, 0)
        b.predio(bx0(0), B1.z0(1), bx0(0) + BW, B1.z1(1), 1, (B1.z0(1) + B1.z1(1)) / 2f, 3f, 5f, 14)
        b.predio(bx0(1), B2.z0(0), bx0(1) + BW, B2.z1(0), 2, bx0(1) + BW / 2f, 3f, 5f, 13)
        b.predio(bx0(7), B2.z0(0), bx0(7) + BW, B2.z1(0), 2, bx0(7) + BW / 2f, 3f, 5f, 15)
        b.predio(bx0(4), B2.z0(1), bx0(4) + BW, B2.z1(1), 0, bx0(4) + BW / 2f, 3f, 5f, 12)
        b.predio(bx0(6), B3.z0(1), bx0(6) + BW, B3.z1(1), 0, bx0(6) + BW / 2f, 3f, 5f, 12)
        b.predio(DX0, DZ0, DX0 + BW, B3.z1(0), 2, DX0 + BW / 2f, 3f, 5f, 15)
        // cela da delegacia
        b.muro(DX0 + 0.4f, DZP, DX0 + BW - 0.4f, DZP, 4.2f, 15, floatArrayOf(DXG), 3f)
        val pR = b.portao(DXG - 1.5f, DZP - 0.2f, DXG + 1.5f, DZP + 0.2f, 4.2f, 17)
        b.item(DX0 + 20f, B3.z1(0) - 4f, 3)
        b.item(bx0(4) + 6f, (B2.z0(1) + B2.z1(1)) / 2f, 2)
        b.item(g0.first.zc(0) * 0f + CHAVE_AZUL[0] + 5f, CHAVE_AZUL[1] + 3f, 1)
        b.item(FUSIVEIS[0] + 5f, FUSIVEIS[1] + 3f, 0)
        b.item(FUSIVEIS[2] + 5f, FUSIVEIS[3] + 3f, 1)
        b.item(NOTAS[0] + 5f, NOTAS[1] + 3f, 1)

        // ---- barricadas: forcam o zigue-zague
        for (p in arrayOf(intArrayOf(3, 0), intArrayOf(4, 0), intArrayOf(5, 1), intArrayOf(6, 0))) barV(b, B1, p[0], p[1])
        for (p in arrayOf(intArrayOf(0, 7), intArrayOf(1, 4), intArrayOf(2, 5), intArrayOf(2, 6), intArrayOf(2, 8), intArrayOf(3, 5))) barH(b, B1, p[0], p[1])
        for (p in arrayOf(intArrayOf(1, 0), intArrayOf(4, 0), intArrayOf(8, 1))) barV(b, B2, p[0], p[1])
        for (p in arrayOf(intArrayOf(0, 1), intArrayOf(0, 8), intArrayOf(1, 6), intArrayOf(2, 3), intArrayOf(2, 7))) barH(b, B2, p[0], p[1])
        for (p in arrayOf(intArrayOf(1, 1), intArrayOf(7, 0), intArrayOf(8, 0))) barV(b, B3, p[0], p[1])
        for (p in arrayOf(intArrayOf(0, 6), intArrayOf(1, 4), intArrayOf(1, 8), intArrayOf(2, 1), intArrayOf(2, 7))) barH(b, B3, p[0], p[1])
        for (p in arrayOf(intArrayOf(4, 0), intArrayOf(5, 0), intArrayOf(7, 0))) barV(b, B4, p[0], p[1])
        for (p in arrayOf(intArrayOf(0, 5), intArrayOf(1, 6))) barH(b, B4, p[0], p[1])

        // ---- carros encostados nas vielas (sobra ~3 m para passar)
        for (bi in fs.indices) {
            val f = fs[bi]
            for (r in 0 until f.n) for (k in intArrayOf(2, 4, 6, 8)) {
                if ((k + r + bi) % 3 != 0) continue
                val zm = (f.z0(r) + f.z1(r)) / 2f + 5f
                b.carro(xs(k) + 1.4f, zm, false, if (k % 4 == 0) 6 else 7)
            }
        }

        // ---- itens, zumbis e nascedouros nas intersecoes
        val sp = ArrayList<Float>()
        for (bi in fs.indices) {
            val f = fs[bi]
            for (r in 0..f.n) for (k in 0..9) {
                val x = xs(k); val z = f.zc(r)
                if (distInicio(x, z) < 32f) continue
                val idx = k + r * 3 + bi
                if (idx % 4 == 0) b.zumbi(x, z)
                if (idx % 4 == 2) b.item(x - 1.2f, z + 1.2f, if ((k + r) % 6 == 0) 0 else 1)
                if (idx % 3 == 1) { sp.add(x); sp.add(z) }
            }
        }

        // ---- muralhas e portoes
        val pA = separador(b, PORTAO_Z[0], PORTAO_X[0], 16)
        val pB = separador(b, PORTAO_Z[1], PORTAO_X[1], 19)
        val pC = separador(b, PORTAO_Z[2], PORTAO_X[2], 18)
        val pD = separador(b, PORTAO_Z[3], PORTAO_X[3], 20)

        // ---- arena final: heliponto entre dois paredoes
        b.caixa(-W + 0.4f, -W + 0.4f, -45f, -102f, 26f, 12)
        b.caixa(45f, -W + 0.4f, W - 0.4f, -102f, 26f, 13)
        b.caixa(-45f, -W + 0.4f, 45f, -145f, 40f, 13)
        b.dec(0f, 0.3f, -124f, 12f, 0.6f, 12f, 0.62f, 0.55f, 0.20f)
        b.container(-26f, -112f, true, 3); b.container(26f, -112f, true, 8)
        b.container(-30f, -134f, false, 8); b.container(30f, -134f, false, 3)
        b.zumbi(-30f, -122f); b.zumbi(30f, -122f); b.zumbi(-14f, -110f); b.zumbi(14f, -110f)
        b.item(-36f, -122f, 1); b.item(36f, -122f, 1); b.item(0f, -110f, 0)
        for (v in floatArrayOf(-30f, 30f)) { sp.add(v); sp.add(-110f); sp.add(v); sp.add(-142f) }

        Fases.portoes = intArrayOf(pA, pB, pC, pD, pR)
        b.nascedouros(sp.toFloatArray())
    }
}

/**
 * Missao da cidade. Fluxo: Chave Azul (portao A) -> 3 fusiveis na caixa de forca (portao B; o 2o fusivel acorda o Seguranca,
 * que larga a Chave Vermelha) -> notas e alavancas na ordem certa (portao C) -> Chave Vermelha abre a cela da delegacia e o
 * sobrevivente vira parceiro de IA -> ele abre o portao final -> radio no heliponto chama o Hospedeiro -> derrota-lo conclui.
 */
class Cidade(private val mundo: Mundo, val def: DefFase) : Missao {

    enum class Tipo { MERCADOR, BAU, CHAVE, FUSIVEL, PAINEL, NOTA, ALAVANCA, PORTAO, SOBREVIVENTE, RADIO }

    class Obj(val tipo: Tipo, val x: Float, val z: Float, val indice: Int, var instancia: ModelInstance, var haste: ModelInstance? = null) {
        var ativo = true
        var estado = false
    }

    val objetos = ArrayList<Obj>()
    private val modelos = ArrayList<Model>()
    private val m4 = Matrix4()
    private var tempo = 0f

    /** Preenchido pela tela: o jogador (para o parceiro seguir) e o som do tiro do parceiro. */
    var jogador: Jogador? = null
    var aoSom: ((String, Float) -> Unit)? = null
    override var aoMensagem: ((String) -> Unit)? = null
    override var aoAbrirLoja: (() -> Unit)? = null
    override var aoConcluir: (() -> Unit)? = null

    // ---- estado
    private var temChaveAzul = false
    private var temChaveVermelha = false
    private var fusiveis = 0
    private var painelLigado = false
    private val notaLida = BooleanArray(2)
    private val puxadas = IntArray(3)
    private var numPuxadas = 0
    private var puzzleResolvido = false
    private val portaoAberto = BooleanArray(5) // A, B, C, D, R
    private var miniAcordado = false
    private var miniMorto = false
    var mini: Inimigo? = null
        private set
    var chefe: Inimigo? = null
        private set
    var concluida = false
        private set
    var companheiro: Companheiro? = null
        private set
    private var salvo = false

    override var objetivoTexto = ""
        private set
    override var dicaTexto = ""
        private set

    private val mBauFechado: Model
    private val mBauAberto: Model
    private val mMercador: Model
    private val mBase: Model
    private val mHaste = arrayOfNulls<Model>(3)
    private val mFusivel: Model
    private val mChaveAzul: Model
    private val mChaveVerm: Model
    private val mNota: Model
    private val mPainel: Model
    private val mPainelLigado: Model
    private val mSobrev: Model
    private val mRadio: Model
    private val mAliado: Model

    init {
        mBauFechado = criar(listOf(
            floatArrayOf(0f, 0.30f, 0f, 0.90f, 0.60f, 0.60f, 0.45f, 0.28f, 0.12f),
            floatArrayOf(0f, 0.68f, 0f, 0.94f, 0.16f, 0.64f, 0.40f, 0.24f, 0.10f),
            floatArrayOf(0f, 0.40f, 0.31f, 0.14f, 0.18f, 0.04f, 0.85f, 0.70f, 0.20f)
        ), false)
        mBauAberto = criar(listOf(
            floatArrayOf(0f, 0.30f, 0f, 0.90f, 0.60f, 0.60f, 0.45f, 0.28f, 0.12f),
            floatArrayOf(0f, 0.62f, 0f, 0.80f, 0.04f, 0.50f, 0.10f, 0.06f, 0.04f),
            floatArrayOf(0f, 0.95f, -0.30f, 0.94f, 0.16f, 0.64f, 0.40f, 0.24f, 0.10f)
        ), false)
        mMercador = criar(listOf(
            floatArrayOf(0f, 0.55f, 0f, 0.62f, 1.10f, 0.40f, 0.34f, 0.24f, 0.16f),
            floatArrayOf(0f, 1.32f, 0f, 0.36f, 0.34f, 0.34f, 0.78f, 0.60f, 0.48f),
            floatArrayOf(0f, 1.56f, 0f, 0.58f, 0.10f, 0.58f, 0.14f, 0.10f, 0.08f),
            floatArrayOf(0f, 1.68f, 0f, 0.34f, 0.22f, 0.34f, 0.14f, 0.10f, 0.08f),
            floatArrayOf(0f, 0.85f, 0.34f, 0.78f, 0.90f, 0.32f, 0.20f, 0.34f, 0.18f),
            floatArrayOf(0.46f, 0.30f, 0.30f, 0.30f, 0.60f, 0.30f, 0.55f, 0.46f, 0.22f)
        ), false)
        mBase = criar(listOf(floatArrayOf(0f, 0.12f, 0f, 0.50f, 0.24f, 0.50f, 0.30f, 0.30f, 0.32f)), false)
        val cores = arrayOf(floatArrayOf(0.20f, 0.45f, 0.95f), floatArrayOf(0.92f, 0.20f, 0.18f), floatArrayOf(0.96f, 0.80f, 0.15f))
        for (i in 0..2) {
            val c = cores[i]
            mHaste[i] = criar(listOf(
                floatArrayOf(0f, 0.40f, 0f, 0.07f, 0.80f, 0.07f, 0.55f, 0.55f, 0.58f),
                floatArrayOf(0f, 0.86f, 0f, 0.22f, 0.22f, 0.22f, c[0], c[1], c[2])
            ), false)
        }
        mFusivel = criar(listOf(
            floatArrayOf(0f, 0f, 0f, 0.14f, 0.40f, 0.14f, 1.00f, 0.85f, 0.20f),
            floatArrayOf(0f, 0.22f, 0f, 0.20f, 0.06f, 0.20f, 0.70f, 0.70f, 0.72f),
            floatArrayOf(0f, -0.22f, 0f, 0.20f, 0.06f, 0.20f, 0.70f, 0.70f, 0.72f)
        ), true)
        mChaveAzul = criar(listOf(
            floatArrayOf(0f, 0f, 0f, 0.08f, 0.50f, 0.08f, 0.25f, 0.55f, 1.00f),
            floatArrayOf(0.10f, 0.22f, 0f, 0.20f, 0.08f, 0.08f, 0.25f, 0.55f, 1.00f),
            floatArrayOf(0f, -0.26f, 0f, 0.28f, 0.24f, 0.08f, 0.25f, 0.55f, 1.00f)
        ), true)
        mChaveVerm = criar(listOf(
            floatArrayOf(0f, 0f, 0f, 0.08f, 0.50f, 0.08f, 1.00f, 0.25f, 0.22f),
            floatArrayOf(0.10f, 0.22f, 0f, 0.20f, 0.08f, 0.08f, 1.00f, 0.25f, 0.22f),
            floatArrayOf(0f, -0.26f, 0f, 0.28f, 0.24f, 0.08f, 1.00f, 0.25f, 0.22f)
        ), true)
        mNota = criar(listOf(
            floatArrayOf(0f, 0f, 0f, 0.34f, 0.04f, 0.46f, 0.95f, 0.93f, 0.80f),
            floatArrayOf(0f, 0.03f, 0f, 0.26f, 0.02f, 0.02f, 0.30f, 0.30f, 0.32f)
        ), true)
        mPainel = criar(listOf(
            floatArrayOf(0f, 0.90f, 0f, 1.00f, 1.80f, 0.40f, 0.40f, 0.42f, 0.46f),
            floatArrayOf(-0.25f, 1.45f, 0.21f, 0.12f, 0.12f, 0.04f, 0.90f, 0.15f, 0.15f),
            floatArrayOf(0f, 1.45f, 0.21f, 0.12f, 0.12f, 0.04f, 0.90f, 0.15f, 0.15f),
            floatArrayOf(0.25f, 1.45f, 0.21f, 0.12f, 0.12f, 0.04f, 0.90f, 0.15f, 0.15f)
        ), false)
        mPainelLigado = criar(listOf(
            floatArrayOf(0f, 0.90f, 0f, 1.00f, 1.80f, 0.40f, 0.40f, 0.42f, 0.46f),
            floatArrayOf(-0.25f, 1.45f, 0.21f, 0.12f, 0.12f, 0.04f, 0.15f, 0.95f, 0.30f),
            floatArrayOf(0f, 1.45f, 0.21f, 0.12f, 0.12f, 0.04f, 0.15f, 0.95f, 0.30f),
            floatArrayOf(0.25f, 1.45f, 0.21f, 0.12f, 0.12f, 0.04f, 0.15f, 0.95f, 0.30f)
        ), true)
        mSobrev = criar(listOf(
            floatArrayOf(0f, 0.55f, 0f, 0.50f, 1.00f, 0.30f, 0.30f, 0.62f, 0.72f),
            floatArrayOf(0f, 1.28f, 0f, 0.30f, 0.30f, 0.30f, 0.78f, 0.60f, 0.48f),
            floatArrayOf(0f, 1.46f, 0f, 0.32f, 0.10f, 0.32f, 0.12f, 0.10f, 0.08f)
        ), false)
        mAliado = criar(listOf(
            floatArrayOf(0f, 0.55f, 0f, 0.50f, 1.00f, 0.30f, 0.30f, 0.62f, 0.72f),
            floatArrayOf(0f, 1.28f, 0f, 0.30f, 0.30f, 0.30f, 0.78f, 0.60f, 0.48f),
            floatArrayOf(0f, 1.46f, 0f, 0.32f, 0.10f, 0.32f, 0.12f, 0.10f, 0.08f),
            floatArrayOf(0.34f, 0.80f, 0.12f, 0.08f, 0.08f, 0.50f, 0.15f, 0.15f, 0.15f)
        ), false)
        mRadio = criar(listOf(
            floatArrayOf(0f, 0.40f, 0f, 1.20f, 0.80f, 0.80f, 0.30f, 0.34f, 0.30f),
            floatArrayOf(0f, 0.95f, 0f, 0.90f, 0.30f, 0.50f, 0.18f, 0.20f, 0.18f),
            floatArrayOf(0f, 2.40f, 0f, 0.06f, 3.00f, 0.06f, 0.70f, 0.70f, 0.74f),
            floatArrayOf(0f, 3.95f, 0f, 0.20f, 0.20f, 0.20f, 1.00f, 0.25f, 0.20f)
        ), false)

        val g = CidadeMapa
        objetos.add(Obj(Tipo.MERCADOR, g.MERCADOR_W[0], g.MERCADOR_W[1], 0, colocar(mMercador, g.MERCADOR_W[0], g.MERCADOR_W[1], 180f)))
        objetos.add(Obj(Tipo.CHAVE, g.CHAVE_AZUL_W[0], g.CHAVE_AZUL_W[1], 0, ModelInstance(mChaveAzul)))
        for (i in 0..2) objetos.add(Obj(Tipo.FUSIVEL, g.FUSIVEIS_W[i * 2], g.FUSIVEIS_W[i * 2 + 1], i, ModelInstance(mFusivel)))
        objetos.add(Obj(Tipo.PAINEL, g.PAINEL_W[0], g.PAINEL_W[1], 0, colocar(mPainel, g.PAINEL_W[0], g.PAINEL_W[1], 0f)))
        for (i in 0..1) objetos.add(Obj(Tipo.NOTA, g.NOTAS_W[i * 2], g.NOTAS_W[i * 2 + 1], i, ModelInstance(mNota)))
        for (i in 0..2) {
            val o = Obj(Tipo.ALAVANCA, g.ALAVANCAS_W[i * 2], g.ALAVANCAS_W[i * 2 + 1], i,
                colocar(mBase, g.ALAVANCAS_W[i * 2], g.ALAVANCAS_W[i * 2 + 1], 0f), ModelInstance(mHaste[i]!!))
            posicionarHaste(o)
            objetos.add(o)
        }
        for (i in 0 until g.BAUS_W.size / 2) {
            objetos.add(Obj(Tipo.BAU, g.BAUS_W[i * 2], g.BAUS_W[i * 2 + 1], i, colocar(mBauFechado, g.BAUS_W[i * 2], g.BAUS_W[i * 2 + 1], MathUtils.random(0f, 360f))))
        }
        // portoes: A, B, C, D no centro de cada linha de bloqueio; R na cela (indice = posicao em Fases.portoes)
        for (i in 0..3) objetos.add(Obj(Tipo.PORTAO, g.PORTAO_OBJ_W[i * 2], g.PORTAO_OBJ_W[i * 2 + 1], i, ModelInstance(mBase)).also { escondido(it) })
        objetos.add(Obj(Tipo.PORTAO, g.CELA_W[0], g.CELA_W[1], 4, ModelInstance(mBase)).also { escondido(it) })
        objetos.add(Obj(Tipo.SOBREVIVENTE, g.SOBREVIVENTE_W[0], g.SOBREVIVENTE_W[1], 0, colocar(mSobrev, g.SOBREVIVENTE_W[0], g.SOBREVIVENTE_W[1], 0f)))
        objetos.add(Obj(Tipo.RADIO, g.RADIO_W[0], g.RADIO_W[1], 0, colocar(mRadio, g.RADIO_W[0], g.RADIO_W[1], 0f)))

        mundo.aoMatar = { z -> aoMatar(z) }
        recalcular()
    }

    /** Portoes sao so logica (o desenho vem da parede colorida): a instancia existe para manter o tipo `Obj` simples. */
    private fun escondido(o: Obj) {
        o.instancia.transform.setToTranslation(0f, -100f, 0f)
    }

    // ------------------------------------------------------------------ modelos
    private fun criar(partes: List<FloatArray>, brilho: Boolean): Model {
        val mb = ModelBuilder()
        mb.begin()
        val atr = (Usage.Position or Usage.Normal).toLong()
        val m = Matrix4()
        for (i in partes.indices) {
            val p = partes[i]
            val mat = Material(ColorAttribute.createDiffuse(p[6], p[7], p[8], 1f))
            if (brilho) mat.set(ColorAttribute.createEmissive(p[6] * 0.55f, p[7] * 0.55f, p[8] * 0.55f, 1f))
            val mp = mb.part("p" + i, GL20.GL_TRIANGLES, atr, mat)
            m.setToTranslation(p[0], p[1], p[2])
            mp.setVertexTransform(m)
            mp.box(p[3], p[4], p[5])
        }
        val modelo = mb.end()
        modelos.add(modelo)
        return modelo
    }

    private fun colocar(m: Model, x: Float, z: Float, graus: Float): ModelInstance {
        val inst = ModelInstance(m)
        inst.transform.setToTranslation(x, Terreno.alturaChao(x, z), z).rotate(Vector3.Y, graus)
        return inst
    }

    private fun posicionarHaste(o: Obj) {
        val h = o.haste ?: return
        h.transform.setToTranslation(o.x, 0.2f + Terreno.alturaChao(o.x, o.z), o.z).rotate(Vector3.X, if (o.estado) 38f else -38f)
    }

    // ------------------------------------------------------------------ textos do HUD
    private fun recalcular() {
        objetivoTexto = objetivo()
        dicaTexto = dica()
    }

    private fun objetivo(): String = when {
        concluida -> "Cidade salva!"
        chefe != null -> "Derrote " + def.chefeNome
        portaoAberto[3] -> "Ligue o radio no heliponto"
        !portaoAberto[0] -> "Ache a Chave Azul"
        !portaoAberto[1] -> if (painelLigado) "Abra o portao eletrico" else "Fusiveis: " + fusiveis + "/3"
        miniAcordado && !miniMorto -> "Derrote " + def.miniNome
        !portaoAberto[2] -> if (puzzleResolvido) "Abra o portao do norte" else "Alavancas: ache as notas"
        !salvo -> if (temChaveVermelha) "Abra a cela da delegacia" else "Pegue a Chave Vermelha"
        else -> "Abra o portao final"
    }

    private fun dica(): String = when {
        concluida -> ""
        chefe != null -> "Seu parceiro ajuda a atirar!"
        portaoAberto[3] -> "O radio fica no heliponto, no norte."
        !portaoAberto[0] -> "A Chave Azul esta numa garagem no extremo oeste. O portao azul fica no leste, no fim das vielas."
        !portaoAberto[1] -> if (painelLigado) "A energia voltou: o portao eletrico fica no extremo oeste."
            else if (fusiveis >= 3) "Leve os fusiveis a caixa de forca, na praca central."
            else "Fusiveis: supermercado (oeste), farmacia (leste) e banco (centro)."
        miniAcordado && !miniMorto -> def.miniNome + " vai soltar a Chave Vermelha."
        !portaoAberto[2] -> if (puzzleResolvido) "O portao do norte destrancou (lado leste)." else "Leia as notas (metro e rua) e puxe as alavancas na ordem. " + ordemTexto()
        !salvo -> if (temChaveVermelha) "A cela fica dentro da delegacia, no oeste." else "O Seguranca guarda a Chave Vermelha da delegacia."
        else -> "O sobrevivente sabe o codigo do portao final."
    }

    private fun ordemTexto(): String {
        val nomes = arrayOf("azul", "vermelha", "amarela")
        val o = CidadeMapa.ORDEM
        val a = if (notaLida[0]) nomes[o[0]] else "?"
        val b = if (notaLida[1]) nomes[o[1]] else "?"
        val c = if (notaLida[1]) nomes[o[2]] else "?"
        return "(ordem: $a, $b, $c)"
    }

    override fun chefeAtual(): Inimigo? {
        val c = chefe
        if (c != null && c.vivo) return c
        val m = mini
        if (m != null && m.vivo) return m
        return null
    }

    override fun nomeChefeAtual(): String = if (chefe?.vivo == true) def.chefeNome else def.miniNome

    // ------------------------------------------------------------------ jogo
    override fun atualizar(dt: Float) {
        tempo += dt
        for (o in objetos) {
            if (!o.ativo) continue
            when (o.tipo) {
                Tipo.FUSIVEL, Tipo.CHAVE -> o.instancia.transform.setToTranslation(o.x, Terreno.alturaChao(o.x, o.z) + 1.0f + MathUtils.sin(tempo * 2.2f) * 0.12f, o.z)
                    .rotate(Vector3.Y, tempo * 80f)
                Tipo.NOTA -> o.instancia.transform.setToTranslation(o.x, Terreno.alturaChao(o.x, o.z) + 0.9f + MathUtils.sin(tempo * 2.0f) * 0.08f, o.z)
                    .rotate(Vector3.Y, tempo * 50f).rotate(Vector3.X, 20f)
                else -> {}
            }
        }
        val j = jogador
        val c = companheiro
        if (j != null && c != null) c.atualizar(dt, j, mundo) { aoSom?.invoke("tiro_pistola", 0.45f) }
        if (chefe != null && !concluida && chefe?.vivo == false) concluir()
    }

    private fun concluir() {
        if (concluida) return
        concluida = true
        Progresso.faseMaxima = maxOf(Progresso.faseMaxima, Fases.indice + 1)
        recalcular()
        aoMensagem?.invoke("O helicoptero chegou! Cidade salva!")
        aoConcluir?.invoke()
    }

    private fun aoMatar(z: Inimigo) {
        val ouro = when (z.tipo) {
            TipoZumbi.NORMAL -> 5
            TipoZumbi.RAPIDO -> 4
            TipoZumbi.LENTO -> 12
            TipoZumbi.FORTE -> 8
            TipoZumbi.MINICHEFE -> 120
            TipoZumbi.CHEFE -> 400
        }
        Progresso.ouro += ouro
        if (z === mini && !miniMorto) {
            miniMorto = true
            val k = Obj(Tipo.CHAVE, z.posicao.x, z.posicao.z, 1, ModelInstance(mChaveVerm))
            objetos.add(k)
            recalcular()
            aoMensagem?.invoke(def.miniNome + " caiu e largou a Chave Vermelha! +" + ouro + " ouro")
        }
        if (z === chefe) concluir()
    }

    private fun raioDe(o: Obj): Float = if (o.tipo == Tipo.PORTAO) 4.2f else RAIO

    private fun maisPerto(x: Float, z: Float): Obj? {
        var melhor: Obj? = null
        var d2m = Float.MAX_VALUE
        for (o in objetos) {
            if (!o.ativo) continue
            if (o.tipo == Tipo.BAU && o.estado) continue
            if (o.tipo == Tipo.PORTAO && portaoAberto[o.indice]) continue
            if (o.tipo == Tipo.SOBREVIVENTE && salvo) continue
            val dx = o.x - x
            val dz = o.z - z
            val d2 = dx * dx + dz * dz
            val r = raioDe(o)
            if (d2 <= r * r && d2 < d2m) { d2m = d2; melhor = o }
        }
        return melhor
    }

    override fun dicaProximo(x: Float, z: Float): String? {
        val o = maisPerto(x, z) ?: return null
        return when (o.tipo) {
            Tipo.MERCADOR -> "INTERAGIR: falar com o Mercador"
            Tipo.BAU -> "INTERAGIR: abrir bau"
            Tipo.CHAVE -> "INTERAGIR: pegar a chave"
            Tipo.FUSIVEL -> "INTERAGIR: pegar fusivel"
            Tipo.PAINEL -> if (painelLigado) null else "INTERAGIR: caixa de forca"
            Tipo.NOTA -> "INTERAGIR: ler nota"
            Tipo.ALAVANCA -> if (o.estado) null else "INTERAGIR: puxar alavanca"
            Tipo.PORTAO -> "INTERAGIR: abrir portao"
            Tipo.SOBREVIVENTE -> "INTERAGIR: libertar o sobrevivente"
            Tipo.RADIO -> "INTERAGIR: radio"
        }
    }

    override fun interagir(x: Float, z: Float): Boolean {
        val o = maisPerto(x, z) ?: return false
        when (o.tipo) {
            Tipo.MERCADOR -> aoAbrirLoja?.invoke()
            Tipo.BAU -> {
                o.estado = true
                o.instancia = ModelInstance(mBauAberto).also { it.transform.set(o.instancia.transform) }
                val ouro = MathUtils.random(40, 80)
                Progresso.ouro += ouro
                aoMensagem?.invoke("Tesouro! +" + ouro + " ouro")
            }
            Tipo.CHAVE -> pegarChave(o)
            Tipo.FUSIVEL -> pegarFusivel(o)
            Tipo.PAINEL -> usarPainel(o)
            Tipo.NOTA -> lerNota(o)
            Tipo.ALAVANCA -> puxar(o)
            Tipo.PORTAO -> abrirPortao(o.indice)
            Tipo.SOBREVIVENTE -> resgatar(o)
            Tipo.RADIO -> usarRadio()
        }
        return true
    }

    private fun pegarChave(o: Obj) {
        o.ativo = false
        if (o.indice == 0) {
            temChaveAzul = true
            aoMensagem?.invoke("Pegou a Chave Azul! Ela abre o portao da avenida.")
        } else {
            temChaveVermelha = true
            aoMensagem?.invoke("Pegou a Chave Vermelha (cela da delegacia).")
        }
        recalcular()
    }

    private fun pegarFusivel(o: Obj) {
        o.ativo = false
        fusiveis++
        recalcular()
        aoMensagem?.invoke("Pegou um fusivel (" + fusiveis + "/3)")
        if (o.indice == 1 && !miniAcordado) {
            miniAcordado = true
            mini = mundo.nascerEspecial(TipoZumbi.MINICHEFE, CidadeMapa.MINI_SPAWN_W[0], CidadeMapa.MINI_SPAWN_W[1], def.fatorVida)
            recalcular()
            aoMensagem?.invoke(def.miniNome + " apareceu! Cuidado!")
        }
    }

    private fun usarPainel(o: Obj) {
        if (painelLigado) return
        if (fusiveis < 3) {
            aoMensagem?.invoke("Faltam " + (3 - fusiveis) + " fusivel(is)")
            return
        }
        painelLigado = true
        o.instancia = ModelInstance(mPainelLigado).also { it.transform.set(o.instancia.transform) }
        recalcular()
        aoMensagem?.invoke("Energia restaurada! O portao eletrico destrancou.")
    }

    private fun lerNota(o: Obj) {
        notaLida[o.indice] = true
        val nomes = arrayOf("azul", "vermelha", "amarela")
        val ordem = CidadeMapa.ORDEM
        aoMensagem?.invoke(
            if (o.indice == 0) "Nota: a PRIMEIRA alavanca e a " + nomes[ordem[0]]
            else "Nota: depois a " + nomes[ordem[1]] + ", por fim a " + nomes[ordem[2]]
        )
        recalcular()
    }

    private fun puxar(o: Obj) {
        if (puzzleResolvido) { aoMensagem?.invoke("As alavancas ja foram usadas"); return }
        if (o.estado) return
        if (!notaLida[0] || !notaLida[1]) {
            // sem as 2 notas o jogador nao sabe a ordem: pode tentar, mas errar reinicia
        }
        val esperado = CidadeMapa.ORDEM[numPuxadas]
        if (o.indice != esperado) {
            for (x in objetos) if (x.tipo == Tipo.ALAVANCA) { x.estado = false; posicionarHaste(x) }
            numPuxadas = 0
            aoMensagem?.invoke("Ordem errada! As alavancas voltaram.")
            return
        }
        o.estado = true
        posicionarHaste(o)
        puxadas[numPuxadas] = o.indice
        numPuxadas++
        if (numPuxadas >= 3) {
            puzzleResolvido = true
            recalcular()
            aoMensagem?.invoke("Algo destrancou ao norte! (portao amarelo)")
        } else {
            aoMensagem?.invoke("Alavanca " + numPuxadas + "/3")
        }
    }

    private fun abrirPortao(i: Int) {
        val ok = when (i) {
            0 -> temChaveAzul
            1 -> painelLigado
            2 -> puzzleResolvido
            3 -> salvo
            else -> temChaveVermelha
        }
        if (!ok) {
            aoMensagem?.invoke(
                when (i) {
                    0 -> "Trancado: precisa da Chave Azul"
                    1 -> "Sem energia: ligue a caixa de forca (3 fusiveis)"
                    2 -> "Trancado: resolva as alavancas"
                    3 -> "Trancado: so o sobrevivente da delegacia sabe o codigo"
                    else -> "Cela trancada: precisa da Chave Vermelha"
                }
            )
            return
        }
        portaoAberto[i] = true
        val idx = Fases.portoes.getOrNull(i) ?: -1
        mundo.removerParede(idx)
        recalcular()
        aoMensagem?.invoke("Portao aberto!")
    }

    private fun resgatar(o: Obj) {
        if (salvo) return
        salvo = true
        o.ativo = false
        val modelo = jogador?.modeloAnimadoCompartilhado
        companheiro = if (modelo != null) Companheiro(modelo, true, o.x, o.z) else Companheiro(mAliado, false, o.x, o.z)
        recalcular()
        aoMensagem?.invoke("Sobrevivente resgatado! Ele vai lutar com voce.")
        aoSom?.invoke("item_pegar", 0.9f)
    }

    private fun usarRadio() {
        if (chefe == null) {
            chefe = mundo.nascerEspecial(TipoZumbi.CHEFE, CidadeMapa.CHEFE_SPAWN_W[0], CidadeMapa.CHEFE_SPAWN_W[1], def.fatorVida)
            recalcular()
            aoMensagem?.invoke("O radio atraiu " + def.chefeNome + "!")
        } else if (!concluida) {
            aoMensagem?.invoke("Derrote " + def.chefeNome + "!")
        }
    }

    override fun desenhar(lote: ModelBatch, ambiente: Environment) {
        for (o in objetos) {
            if (!o.ativo || o.tipo == Tipo.PORTAO) continue
            lote.render(o.instancia, ambiente)
            o.haste?.let { lote.render(it, ambiente) }
        }
        companheiro?.let { lote.render(it.instancia, ambiente) }
    }

    override fun dispose() {
        mundo.aoMatar = null
        for (m in modelos) m.dispose()
    }

    companion object {
        const val RAIO = 2.2f
    }
}
