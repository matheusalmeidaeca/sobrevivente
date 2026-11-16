// ferramentas/gerador-boneco-kotlin/src/main/kotlin/gerador/Parametros.kt
package gerador

/** Retangulo do atlas de textura, em pixels (origem no canto superior esquerdo). */
class Regiao(val x0: Int, val y0: Int, val largura: Int, val altura: Int)

/** Anel (secao transversal eliptica) de uma peca. Tudo em metros. */
class Anel(val y: Double, val rx: Double, val rz: Double, val cx: Double, val cz: Double)

/**
 * Todos os numeros ajustaveis do boneco ficam aqui.
 * Sistema: Y para cima, 1 unidade = 1 metro, origem nos pes, FRENTE = -Z (yaw 0 do jogo).
 * Lado esquerdo do personagem = -X; lado direito = +X.
 */
object Parametros {
    const val LADOS = 10
    const val TAMANHO_ATLAS = 512
    const val ALTURA_ESPERADA = 1.8
    const val TOLERANCIA_ALTURA = 0.005
    const val LIMITE_TRIANGULOS = 2000
    const val SEMENTE_FERIMENTOS = 7L

    // Previa (frente, lado, costas)
    const val VISTA_LARGURA = 300
    const val VISTA_ALTURA = 600
    const val VISTA_ESCALA = 300.0
    const val VISTA_MARGEM_PES = 30.0
    const val SUPER_AMOSTRAGEM = 2

    // Pe (caixa)
    const val PERNA_EIXO_X = 0.095
    const val PE_MEIA_LARGURA = 0.045
    const val PE_ALTURA = 0.08
    const val PE_PONTA_Z = -0.13
    const val PE_CALCANHAR_Z = 0.05

    val NOMES_PECAS: List<String> =
        listOf("cabeca", "tronco", "braco_esq", "braco_dir", "perna_esq", "perna_dir")

    val REGIOES: Map<String, Regiao> = mapOf(
        "cabeca" to Regiao(0, 0, 256, 160),
        "tronco" to Regiao(256, 0, 256, 160),
        "braco_esq" to Regiao(0, 160, 128, 352),
        "perna_esq" to Regiao(128, 160, 128, 352),
        "perna_dir" to Regiao(256, 160, 128, 352),
        "braco_dir" to Regiao(384, 160, 128, 352)
    )

    val ANEIS_CABECA: List<Anel> = listOf(
        Anel(1.490, 0.052, 0.055, 0.0, 0.010),
        Anel(1.545, 0.050, 0.053, 0.0, 0.012),
        Anel(1.560, 0.062, 0.080, 0.0, 0.000),
        Anel(1.600, 0.080, 0.095, 0.0, 0.010),
        Anel(1.655, 0.088, 0.105, 0.0, 0.010),
        Anel(1.705, 0.090, 0.108, 0.0, 0.012),
        Anel(1.760, 0.078, 0.098, 0.0, 0.012),
        Anel(1.800, 0.040, 0.055, 0.0, 0.010)
    )

    val ANEIS_TRONCO: List<Anel> = listOf(
        Anel(0.86, 0.150, 0.095, 0.0, 0.000),
        Anel(0.95, 0.175, 0.105, 0.0, 0.000),
        Anel(1.08, 0.155, 0.095, 0.0, 0.000),
        Anel(1.22, 0.165, 0.100, 0.0, 0.000),
        Anel(1.36, 0.185, 0.115, 0.0, -0.004),
        Anel(1.45, 0.205, 0.105, 0.0, 0.000),
        Anel(1.50, 0.085, 0.065, 0.0, 0.000)
    )

    /** lado: -1.0 = esquerdo (-X), +1.0 = direito (+X). */
    fun aneisBraco(lado: Double): List<Anel> {
        val x = lado * 0.255
        return listOf(
            Anel(1.50, 0.050, 0.050, x, 0.0),
            Anel(1.46, 0.058, 0.058, x, 0.0),
            Anel(1.40, 0.056, 0.056, x, 0.0),
            Anel(1.22, 0.050, 0.052, x, 0.0),
            Anel(1.17, 0.043, 0.045, x, 0.0),
            Anel(1.04, 0.046, 0.046, x, 0.0),
            Anel(0.83, 0.030, 0.024, x, 0.0),
            Anel(0.80, 0.038, 0.022, x, 0.0),
            Anel(0.75, 0.042, 0.020, x, 0.0),
            Anel(0.68, 0.030, 0.014, x, 0.0)
        )
    }

    fun aneisPerna(lado: Double): List<Anel> {
        val x = lado * PERNA_EIXO_X
        return listOf(
            Anel(0.93, 0.085, 0.085, x, 0.00),
            Anel(0.88, 0.088, 0.090, x, 0.00),
            Anel(0.72, 0.075, 0.078, x, 0.00),
            Anel(0.52, 0.058, 0.062, x, 0.00),
            Anel(0.48, 0.052, 0.056, x, 0.00),
            Anel(0.34, 0.055, 0.060, x, 0.01),
            Anel(0.12, 0.038, 0.040, x, 0.00),
            Anel(0.07, 0.040, 0.042, x, 0.00)
        )
    }

    /** Faixa de altura (y minimo e maximo) coberta pela regiao de textura de cada tipo de peca. */
    fun extremos(nome: String): DoubleArray {
        return when (nome.substringBefore('_')) {
            "cabeca" -> doubleArrayOf(1.49, 1.80)
            "tronco" -> doubleArrayOf(0.86, 1.50)
            "braco" -> doubleArrayOf(0.68, 1.50)
            "perna" -> doubleArrayOf(0.07, 0.93)
            else -> throw IllegalArgumentException("peca desconhecida: $nome")
        }
    }
}

/** Conversao de (u, v) normalizados da peca para pixel no atlas. v = 1 em cima, 0 embaixo. */
object Atlas {
    fun pixelX(r: Regiao, u: Double): Double = r.x0 + 0.5 + u * (r.largura - 1)
    fun pixelY(r: Regiao, v: Double): Double = r.y0 + 0.5 + (1.0 - v) * (r.altura - 1)
}
