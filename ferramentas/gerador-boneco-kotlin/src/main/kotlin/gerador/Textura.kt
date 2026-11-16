// ferramentas/gerador-boneco-kotlin/src/main/kotlin/gerador/Textura.kt
package gerador

import java.util.Random
import kotlin.math.abs

/** Cores (RGB empacotado em Int) de uma variante do boneco. */
class Paleta(
    val pele: Int, val camisa: Int, val calca: Int,
    val sapato: Int, val cabelo: Int, val cinto: Int
)

/** Gera o atlas 512x512 por codigo (sem pintura a mao, sem asset externo). Deterministico. */
object Textura {

    fun rgb(r: Int, g: Int, b: Int): Int = (r shl 16) or (g shl 8) or b

    private fun escurecer(c: Int, f: Double): Int {
        val r = (((c shr 16) and 255) * f).toInt()
        val g = (((c shr 8) and 255) * f).toInt()
        val b = ((c and 255) * f).toInt()
        return rgb(r, g, b)
    }

    private fun somarRuido(c: Int, ruido: Int): Int {
        val r = ((c shr 16) and 255) + ruido
        val g = ((c shr 8) and 255) + ruido
        val b = (c and 255) + ruido
        return rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    /** Hash inteiro simples -> numero entre 0 e 1 (ruido de tecido). */
    private fun hash(x: Int, y: Int, semente: Int): Double {
        var n = x * 374761393 + y * 668265263 + semente * 2147483647
        n = (n xor (n ushr 13)) * 1274126177
        return ((n xor (n ushr 16)) and 0xffff) / 65535.0
    }

    fun paleta(zumbi: Boolean): Paleta {
        return if (!zumbi) {
            Paleta(rgb(224, 178, 146), rgb(62, 94, 130), rgb(52, 64, 92), rgb(58, 44, 34), rgb(66, 48, 34), rgb(48, 36, 28))
        } else {
            Paleta(rgb(138, 156, 122), rgb(104, 96, 78), rgb(66, 62, 54), rgb(40, 36, 30), rgb(48, 44, 40), rgb(40, 34, 28))
        }
    }

    /** Retorna os pixels do atlas (TAMANHO_ATLAS x TAMANHO_ATLAS), RGB empacotado, linha a linha. */
    fun pintar(zumbi: Boolean): IntArray {
        val t = Parametros.TAMANHO_ATLAS
        val pix = IntArray(t * t) { rgb(90, 90, 90) }
        val p = paleta(zumbi)
        for ((nome, reg) in Parametros.REGIOES) {
            val e = Parametros.extremos(nome)
            for (py in 0 until reg.altura) {
                for (px in 0 until reg.largura) {
                    val u = (px + 0.5) / reg.largura
                    val h = 1.0 - (py + 0.5) / reg.altura
                    val y = e[0] + h * (e[1] - e[0])
                    val x = reg.x0 + px
                    val yy = reg.y0 + py
                    pix[yy * t + x] = cor(nome, u, y, p, zumbi, x, yy)
                }
            }
        }
        if (zumbi) ferimentos(pix)
        return pix
    }

    private fun cor(nome: String, u: Double, y: Double, p: Paleta, zumbi: Boolean, px: Int, py: Int): Int {
        var c = p.pele
        var amp = 6
        val fx = abs(u - 0.5)
        when (nome.substringBefore('_')) {
            "cabeca" -> {
                if (y > 1.735 || (y > 1.64 && fx > 0.30) || (y > 1.70 && fx > 0.20)) {
                    c = p.cabelo
                    amp = 10
                }
                if (y < 1.55) c = escurecer(p.pele, 0.9)
                if (y > 1.708 && y < 1.72 && fx > 0.045 && fx < 0.115) c = escurecer(p.cabelo, 0.55)
                val olhoBranco = y > 1.692 && y < 1.708 && fx > 0.05 && fx < 0.105
                if (olhoBranco) c = if (zumbi) rgb(205, 205, 150) else rgb(235, 235, 225)
                if (y > 1.694 && y < 1.706 && fx > 0.065 && fx < 0.09) c = if (zumbi) rgb(30, 30, 20) else rgb(40, 60, 90)
                if (zumbi && y > 1.686 && y < 1.714 && fx > 0.04 && fx < 0.115 && !olhoBranco) c = rgb(70, 80, 64)
                if (y > 1.600 && y < 1.618 && fx < 0.07) c = if (zumbi) rgb(40, 10, 10) else rgb(150, 70, 70)
                if (zumbi && y > 1.585 && y < 1.620 && fx < 0.05) c = rgb(35, 8, 8)
            }
            "tronco" -> {
                c = if (y > 1.485) p.pele else if (y > 1.04) p.camisa else if (y > 1.00) p.cinto else p.calca
                if (abs(y - 1.02) < 0.016 && fx < 0.025) c = rgb(160, 140, 70)
                amp = 7
            }
            "braco" -> {
                c = if (y > 1.24) p.camisa else p.pele
                if (zumbi && y > 1.24 && y < 1.30) c = p.pele
            }
            "perna" -> {
                c = if (y > 0.14) p.calca else p.sapato
                amp = 7
            }
        }
        val ruido = ((hash(px, py, 3) - 0.5) * amp * 2).toInt()
        return somarRuido(c, ruido)
    }

    private fun ferimentos(pix: IntArray) {
        val rng = Random(Parametros.SEMENTE_FERIMENTOS)
        val sangue = rgb(96, 18, 18)
        val sujeira = rgb(60, 50, 36)
        val terra = rgb(58, 52, 40)
        mancha(pix, "tronco", 6, 384, 80, 16, sangue, rng)
        mancha(pix, "tronco", 5, 384, 110, 12, sujeira, rng)
        mancha(pix, "tronco", 3, 300, 60, 10, sangue, rng)
        mancha(pix, "braco_esq", 3, 64, 280, 10, sangue, rng)
        mancha(pix, "braco_dir", 3, 448, 300, 10, sangue, rng)
        mancha(pix, "perna_esq", 3, 192, 300, 12, terra, rng)
        mancha(pix, "perna_dir", 3, 320, 320, 12, sangue, rng)
        mancha(pix, "cabeca", 3, 128, 100, 8, sangue, rng)
    }

    /** Desenha n manchas irregulares perto de (cx, cy) (pixels), sem sair da regiao da peca. */
    private fun mancha(pix: IntArray, nome: String, n: Int, cx: Int, cy: Int, r: Int, cor: Int, rng: Random) {
        val reg = Parametros.REGIOES.getValue(nome)
        val t = Parametros.TAMANHO_ATLAS
        for (i in 0 until n) {
            val px = cx + (rng.nextDouble() * 2.0 - 1.0) * reg.largura * 0.35
            val py = cy + (rng.nextDouble() * 2.0 - 1.0) * reg.altura * 0.2
            val rr = r * (0.5 + rng.nextDouble() * 0.7)
            for (yy in (py - rr).toInt()..(py + rr).toInt()) {
                for (xx in (px - rr).toInt()..(px + rr).toInt()) {
                    val dentro = xx >= reg.x0 && xx < reg.x0 + reg.largura && yy >= reg.y0 && yy < reg.y0 + reg.altura
                    if (dentro) {
                        val dx = xx - px
                        val dy = yy - py
                        if (dx * dx + dy * dy <= rr * rr * (0.6 + 0.4 * rng.nextDouble())) pix[yy * t + xx] = cor
                    }
                }
            }
        }
    }
}
