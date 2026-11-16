// ferramentas/gerador-boneco-kotlin/src/main/kotlin/gerador/Main.kt
package gerador

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.system.exitProcess

private fun falhar(mensagem: String): Nothing {
    println("[ERRO] $mensagem")
    exitProcess(1)
}

private fun gravarPng(pixels: IntArray, arquivo: File) {
    val t = Parametros.TAMANHO_ATLAS
    val img = BufferedImage(t, t, BufferedImage.TYPE_INT_RGB)
    img.setRGB(0, 0, t, t, pixels, 0, t)
    ImageIO.write(img, "png", arquivo)
}

fun main(args: Array<String>) {
    System.setProperty("java.awt.headless", "true")
    val pasta = File(if (args.isNotEmpty()) args[0] else "saida")
    pasta.mkdirs()
    println("[ETAPA] Saida em ${pasta.absolutePath}")

    println("[ETAPA] Montando pecas")
    val pecas = Geometria.montarPecas()
    var total = 0
    var yMin = Double.MAX_VALUE
    var yMax = -Double.MAX_VALUE
    val relatorio = StringBuilder()
    for (m in pecas) {
        total += m.triangulos.size
        for (v in m.vertices) {
            if (v.y < yMin) yMin = v.y
            if (v.y > yMax) yMax = v.y
        }
        val linha = "peca ${m.nome}: ${m.vertices.size} vertices, ${m.triangulos.size} triangulos"
        println("[PECA] $linha")
        relatorio.append(linha).append('\n')
    }
    val nomes = pecas.map { it.nome }
    for (esperado in Parametros.NOMES_PECAS) {
        if (!nomes.contains(esperado)) falhar("peca ausente: $esperado")
    }
    val altura = yMax - yMin
    println("[CHECK] triangulos=$total (limite ${Parametros.LIMITE_TRIANGULOS}) yMin=$yMin yMax=$yMax altura=$altura")
    if (total > Parametros.LIMITE_TRIANGULOS) falhar("triangulos acima do orcamento")
    if (abs(yMin) > Parametros.TOLERANCIA_ALTURA) falhar("origem nao esta nos pes (yMin=$yMin)")
    if (abs(altura - Parametros.ALTURA_ESPERADA) > Parametros.TOLERANCIA_ALTURA) falhar("altura diferente de 1,8 m ($altura)")

    println("[ETAPA] Pintando texturas")
    val texJogador = Textura.pintar(false)
    val texZumbi = Textura.pintar(true)
    gravarPng(texJogador, File(pasta, "textura_jogador.png"))
    gravarPng(texZumbi, File(pasta, "textura_zumbi.png"))

    println("[ETAPA] Exportando OBJ/MTL")
    ExportadorObj.escrever(pasta, "boneco", "jogador", "textura_jogador.png", pecas)
    ExportadorObj.escrever(pasta, "zumbi", "zumbi", "textura_zumbi.png", pecas)

    println("[ETAPA] Gerando previa (frente, lado, costas)")
    Previa.gravar(pecas, texJogador, texZumbi, File(pasta, "preview.png"))

    relatorio.append("total: $total triangulos\n")
    relatorio.append("altura: $altura m (yMin=$yMin, yMax=$yMax)\n")
    relatorio.append("frente = -Z, Y para cima, 1 unidade = 1 m\n")
    File(pasta, "relatorio.txt").writeText(relatorio.toString())
    println("[OK] Arquivos gerados em ${pasta.absolutePath}")
}
