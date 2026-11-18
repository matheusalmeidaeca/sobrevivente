// core/src/main/kotlin/com/sobrevivente/jogo/telas/TelaJogo.kt
package com.sobrevivente.jogo.telas

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.graphics.g3d.shaders.DepthShader
import com.badlogic.gdx.graphics.g3d.utils.DepthShaderProvider
import com.sobrevivente.jogo.grafico.Particulas
import com.sobrevivente.jogo.grafico.PosProcessamento
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.ScreenUtils
import com.sobrevivente.jogo.Constantes
import com.sobrevivente.jogo.camera.CameraTerceiraPessoa
import com.sobrevivente.jogo.entrada.ControlesToque
import com.sobrevivente.jogo.hud.Hud
import com.sobrevivente.jogo.hud.Joystick
import com.sobrevivente.jogo.inventario.Maleta
import com.sobrevivente.jogo.inventario.TipoItem
import com.sobrevivente.jogo.mundo.Arma
import com.sobrevivente.jogo.mundo.Jogador
import com.sobrevivente.jogo.mundo.Mundo
import com.sobrevivente.jogo.mundo.Fases
import com.sobrevivente.jogo.mundo.Historia
import com.sobrevivente.jogo.mundo.Progresso
import com.sobrevivente.jogo.mundo.TipoArma

/** Marco F3: personagens animados (F2) no lugar das capsulas. Marco 1.8: mapa de teste (paredes, colisão, minimapa), sobre maleta (1.7), morte/reinício (1.6), inimigos (1.5), tiro (1.4) e HUD (1.3). */
class TelaJogo(
    private val jogo: com.badlogic.gdx.Game,
    private val modoHistoria: Boolean = false,
    private val fase: Int = 0
) : ScreenAdapter() {

    /** F17: troca o mapa das constantes (vila na sobrevivencia; fase N na historia) ANTES de criar o Mundo. */
    private val cargaFase: Unit = Fases.carregar(if (modoHistoria) fase else 0)

    private val mundo = Mundo()
    private var historia: Historia? = null
    private var ouroNoInicio = 0
    /** Menu modal aberto: 0 = pausa, 1 = loja do mercador, 2 = fase concluida. */
    private var modo = 0
    private val rotulosLoja = arrayOf("", "", "", "", "FECHAR")
    private var jogador = Jogador() // var: é recriado ao reiniciar depois da morte
    private val arma = Arma()
    private val direcaoTiro = Vector3() // reaproveitado: dispersao dos bagos da escopeta
    private val cameraJogo = CameraTerceiraPessoa()
    private val controles = ControlesToque()
    private val joystick = Joystick(controles)
    private val maleta = Maleta()
    private val hud = Hud(controles, maleta, mundo)

    private val loteModelos = ModelBatch(criarProvedorShader())
    private val loteSombra = ModelBatch(criarProvedorSombra())
    private val pos = PosProcessamento()
    private val particulas = Particulas()
    private val tmp = Vector3()
    private var tempoPoeira = 0f
    private val lote = SpriteBatch()
    private val forma = ShapeRenderer()

    private var pausado = false
    private var morto = false
    private var maletaAberta = false
    private var voltarAoMenu = false

    init {
        mundo.spawnMax = Fases.atual.spawnMax
        mundo.spawnDist = Fases.atual.spawnDist
        mundo.spawnIntervalo = Fases.atual.spawnIntervalo
        jogador.posicao.set(Fases.atual.inicio[0], 0f, Fases.atual.inicio[1])
        cameraJogo.seguir(jogador.posicao)
        if (modoHistoria) {
            ouroNoInicio = Progresso.ouro
            aplicarProgresso()
            iniciarHistoria()
        }
    }

    /** Arma/municao/curas que o jogador leva de uma fase para a seguinte. */
    private fun aplicarProgresso() {
        if (Progresso.fuzil && maleta.adicionarArma(TipoArma.FUZIL)) arma.liberar(TipoArma.FUZIL)
        if (Progresso.escopeta && maleta.adicionarArma(TipoArma.ESCOPETA)) arma.liberar(TipoArma.ESCOPETA)
        arma.adicionarReserva(Progresso.reserva)
        if (Progresso.curas > 0) maleta.adicionar(TipoItem.CURA, Progresso.curas)
    }

    private fun iniciarHistoria() {
        val h = Historia(mundo, Fases.atual)
        h.aoMensagem = { t -> hud.mostrarMensagem(t) }
        h.aoAbrirLoja = { abrirMenu(1) }
        h.aoConcluir = { concluirFase() }
        historia = h
        hud.historia = h
        hud.mostrarMensagem(Fases.atual.subtitulo + ": " + Fases.atual.nome)
    }

    private fun concluirFase() {
        Progresso.fuzil = arma.estaLiberada(TipoArma.FUZIL)
        Progresso.escopeta = arma.estaLiberada(TipoArma.ESCOPETA)
        Progresso.reserva = arma.reserva
        Progresso.curas = maleta.total(TipoItem.CURA)
        abrirMenu(2)
    }

    /** O boneco do F2 tem 13 ossos; o DefaultShader do LibGDX aceita 12 por padrao. */
    private fun criarProvedorShader(): DefaultShaderProvider {
        val config = DefaultShader.Config()
        config.numBones = Constantes.NUM_OSSOS_SHADER
        return DefaultShaderProvider(config)
    }

    /** Sombra: o shader de profundidade tambem precisa aceitar os ossos do boneco. */
    private fun criarProvedorSombra(): DepthShaderProvider {
        val config = DepthShader.Config()
        config.numBones = Constantes.NUM_OSSOS_SHADER
        return DepthShaderProvider(config)
    }

    /** Desenha no mapa de sombra tudo que projeta sombra (personagens, casas, decoracao). */
    private fun desenharSombras() {
        val luz = mundo.luzSol ?: return
        luz.begin(jogador.posicao, cameraJogo.camera.direction)
        loteSombra.begin(luz.camera)
        for (i in mundo.paredes.indices) {
            if (mundo.paredeVisivel(i)) loteSombra.render(mundo.paredes[i])
        }
        loteSombra.render(mundo.cenario.decoracao)
        for (i in mundo.cenario.telhados.indices) {
            if (mundo.cenario.telhadoVisivel(i)) loteSombra.render(mundo.cenario.telhados[i])
        }
        loteSombra.render(jogador.corpo)
        jogador.armaVisivel?.let { loteSombra.render(it) }
        jogador.facaCabo?.let { loteSombra.render(it) }
        for (i in mundo.inimigos.indices) {
            val inimigo = mundo.inimigos[i]
            if (inimigo.visivel) loteSombra.render(inimigo.instancia)
        }
        loteSombra.end()
        luz.end()
    }

    override fun show() {
        Gdx.input.inputProcessor = controles
        Gdx.input.setCatchKey(Input.Keys.BACK, true) // Voltar vira pausa, não fecha o app
    }

    override fun hide() {
        Gdx.input.inputProcessor = null
        Gdx.input.setCatchKey(Input.Keys.BACK, false)
    }

    override fun pause() {
        controles.liberarTudo()
    }

    private fun alternarPausa() {
        if (pausado) fecharMenu() else abrirMenu(0)
    }

    /** Abre um menu modal (jogo parado): 0 = pausa, 1 = loja do mercador, 2 = fase concluida. */
    private fun abrirMenu(m: Int) {
        modo = m
        when (m) {
            0 -> {
                hud.menuTitulo = "PAUSADO"
                hud.menuSubtitulo = if (modoHistoria) Fases.atual.subtitulo + ": " + Fases.atual.nome else ""
                if (modoHistoria) {
                    hud.menuRotulos = arrayOf("CONTINUAR", "REINICIAR FASE", "MENU PRINCIPAL")
                    hud.menuCores = intArrayOf(0, 2, 1)
                } else {
                    hud.menuRotulos = arrayOf("CONTINUAR", "MENU PRINCIPAL")
                    hud.menuCores = intArrayOf(0, 1)
                }
            }
            1 -> {
                hud.menuTitulo = "MERCADOR"
                atualizarLoja("Escolha o que comprar")
                hud.menuRotulos = rotulosLoja
                hud.menuCores = intArrayOf(2, 2, 2, 2, 1)
            }
            else -> {
                hud.menuTitulo = Fases.atual.nome + " CONCLUIDA"
                hud.menuSubtitulo = "Ouro: " + Progresso.ouro
                if (Fases.indice + 1 < Fases.total) {
                    hud.menuRotulos = arrayOf("PROXIMA FASE", "MENU PRINCIPAL")
                    hud.menuCores = intArrayOf(0, 1)
                } else {
                    hud.menuTitulo = "FIM DA HISTORIA"
                    hud.menuRotulos = arrayOf("MENU PRINCIPAL")
                    hud.menuCores = intArrayOf(1)
                }
            }
        }
        controles.definirMenu(hud.menuRotulos.size)
        pausado = true
        hud.pausado = true
        controles.definirBloqueado(true)
        Gdx.app.log("TELA", "menu " + m)
    }

    private fun fecharMenu() {
        pausado = false
        hud.pausado = false
        controles.definirBloqueado(false)
        Gdx.app.log("TELA", "menu fechado")
    }

    private val precosLoja = intArrayOf(20, 35, 150, 180)

    private fun atualizarLoja(aviso: String) {
        rotulosLoja[0] = "MUNICAO +30  (20)"
        rotulosLoja[1] = "CURA  (35)"
        rotulosLoja[2] = if (arma.estaLiberada(TipoArma.FUZIL)) "FUZIL  (ja tem)" else "FUZIL  (150)"
        rotulosLoja[3] = if (arma.estaLiberada(TipoArma.ESCOPETA)) "ESCOPETA  (ja tem)" else "ESCOPETA  (180)"
        hud.menuSubtitulo = "Ouro: " + Progresso.ouro + "  -  " + aviso
    }

    /** Toque num botao da loja (0..3 = itens, 4 = fechar). */
    private fun comprar(i: Int) {
        if (i == 4) { fecharMenu(); return }
        if (Progresso.ouro < precosLoja[i]) { atualizarLoja("Ouro insuficiente"); return }
        val resultado: String = when (i) {
            0 -> { arma.adicionarReserva(30); "Comprou municao" }
            1 -> if (maleta.adicionar(TipoItem.CURA, 1)) "Comprou cura" else { atualizarLoja(Constantes.MSG_MALETA_CHEIA); return }
            2 -> if (arma.estaLiberada(TipoArma.FUZIL)) { atualizarLoja("Voce ja tem o fuzil"); return }
                 else if (maleta.adicionarArma(TipoArma.FUZIL)) { arma.liberar(TipoArma.FUZIL); "Comprou o fuzil" }
                 else { atualizarLoja(Constantes.MSG_MALETA_CHEIA); return }
            else -> if (arma.estaLiberada(TipoArma.ESCOPETA)) { atualizarLoja("Voce ja tem a escopeta"); return }
                 else if (maleta.adicionarArma(TipoArma.ESCOPETA)) { arma.liberar(TipoArma.ESCOPETA); "Comprou a escopeta" }
                 else { atualizarLoja(Constantes.MSG_MALETA_CHEIA); return }
        }
        Progresso.ouro -= precosLoja[i]
        atualizarLoja(resultado)
    }

    /** Botao tocado no menu modal (indice na lista do menu aberto). */
    private fun tratarMenu(b: Int) {
        when (modo) {
            0 -> {
                if (modoHistoria) {
                    when (b) {
                        0 -> fecharMenu()
                        1 -> { fecharMenu(); reiniciar() }
                        else -> voltarAoMenu = true
                    }
                } else {
                    if (b == 0) fecharMenu() else voltarAoMenu = true
                }
            }
            1 -> comprar(b)
            else -> {
                if (b == 0 && Fases.indice + 1 < Fases.total) irParaFase(Fases.indice + 1) else voltarAoMenu = true
            }
        }
    }

    private var proximaFase = -1

    private fun irParaFase(n: Int) {
        proximaFase = n
    }

    /** Câmera, movimento e tiro (só com o jogador vivo e o jogo rodando). */
    private fun atualizarJogador(dt: Float, densidade: Float) {
        cameraJogo.girar(
            controles.consumirGiroX() / densidade,
            controles.consumirGiroY() / densidade
        )
        jogador.mover(dt, controles.movimentoX, controles.movimentoY, cameraJogo.yaw)
        cameraJogo.seguir(jogador.posicao)

        // Tiro: o raio sai da câmera, atravessa o centro da tela (onde fica a mira)
        val atirando = controles.acaoPressionada(ControlesToque.ACAO_ATIRAR) ||
            controles.acaoPressionada(ControlesToque.ACAO_ATIRAR_ESQ)
        val atirou = arma.atualizar(dt, atirando, controles.consumirPressao(ControlesToque.ACAO_RECARREGAR))
        if (atirou) {
            val tipo = arma.tipo
            var acertou = false
            for (i in 0 until tipo.projeteis) {
                direcaoTiro.set(cameraJogo.camera.direction)
                if (tipo.dispersao > 0f) {
                    val d = tipo.dispersao
                    direcaoTiro.add(MathUtils.random(-d, d), MathUtils.random(-d, d), MathUtils.random(-d, d)).nor()
                }
                if (mundo.atirar(cameraJogo.camera.position, direcaoTiro, tipo.alcance, tipo.dano)) acertou = true
            }
            if (acertou) hud.mostrarAcerto()
            // recuo: o tiro desequilibra um pouco o personagem para tras (escopeta bem mais)
            val cx = cameraJogo.camera.direction.x
            val cz = cameraJogo.camera.direction.z
            val cl = kotlin.math.sqrt(cx * cx + cz * cz)
            if (cl > 0.001f) {
                jogador.receberRecuo(-cx / cl, -cz / cl, if (tipo.projeteis > 1) Constantes.IMPACTO_TIRO_ESCOPETA else Constantes.IMPACTO_TIRO_JOGADOR)
            }
            efeitosDoTiro(tipo)
        }
        controles.consumirPressao(ControlesToque.ACAO_ATIRAR_ESQ)
        if (arma.golpeFaca) golpearComFaca()
        if (controles.consumirPressao(ControlesToque.ACAO_TROCAR)) {
            if (arma.trocar()) hud.mostrarMensagem(nomeEquipado())
            else hud.mostrarMensagem("Sem outra arma")
        }
        for (k in 0..3) {
            if (controles.consumirPressao(ControlesToque.ACAO_ARMA0 + k)) escolherSlotArma(k)
        }
        if (controles.consumirPressao(ControlesToque.ACAO_CURAR)) usarCuraRapida()
        if (controles.consumirPressao(ControlesToque.ACAO_PULAR)) jogador.pular()
        if (controles.consumirPressao(ControlesToque.ACAO_AGACHAR)) jogador.alternarAgachar()
        jogador.definirArma(arma.tipo)
        jogador.definirFaca(arma.faca)
        jogador.definirRecarga(arma.recarregando)
        if (controles.consumirPressao(ControlesToque.ACAO_INTERAGIR)) interagir()
        jogador.atualizarTiro(dt, cameraJogo.yaw, atirou || arma.golpeFaca)
    }

    private fun nomeEquipado(): String = if (arma.faca) "Faca" else arma.tipo.nome

    /** Toque num dos 4 slots do HUD (3 armas + faca). */
    private fun escolherSlotArma(k: Int) {
        if (!arma.slotDisponivel(k)) {
            hud.mostrarMensagem("Arma nao encontrada")
        } else if (arma.selecionarSlot(k)) {
            hud.mostrarMensagem(nomeEquipado())
        }
    }

    /** Botao CURAR: usa a primeira cura da maleta sem abrir a maleta. */
    private fun usarCuraRapida() {
        val item = maleta.primeiro(TipoItem.CURA)
        if (item == null) {
            hud.mostrarMensagem("Sem cura")
            return
        }
        usarItem(item)
    }

    /** Golpe de faca a frente da camera: acerta todos no arco (alcance curto), com sangue e recuo/avanco do corpo. */
    private fun golpearComFaca() {
        var fx = cameraJogo.camera.direction.x
        var fz = cameraJogo.camera.direction.z
        val fl = kotlin.math.sqrt(fx * fx + fz * fz)
        if (fl < 0.001f) return
        fx /= fl
        fz /= fl
        jogador.golpearFaca(fx, fz)
        if (mundo.golpeFaca(jogador.posicao.x, jogador.posicao.z, jogador.posicao.y, fx, fz)) {
            hud.mostrarAcerto()
            particulas.sangue(
                mundo.impacto.x, mundo.impacto.y, mundo.impacto.z,
                fx, 0f, fz, mundo.impactoMorreu
            )
        }
    }

    /** Fumaca/faiscas no cano, capsula ejetada e o que o ultimo raio acertou (sangue ou faiscas). */
    private fun efeitosDoTiro(tipo: com.sobrevivente.jogo.mundo.TipoArma) {
        val c = cameraJogo.camera
        if (jogador.armaVisivel != null) {
            jogador.clarao.transform.getTranslation(tmp)
            particulas.tiro(tmp.x, tmp.y, tmp.z, c.direction.x, c.direction.y, c.direction.z, tipo.projeteis > 1)
            particulas.capsula(tmp.x - c.direction.x * 0.3f, tmp.y - 0.05f, tmp.z - c.direction.z * 0.3f, -c.direction.z, c.direction.x)
        }
        when (mundo.impactoTipo) {
            1 -> particulas.sangue(
                mundo.impacto.x, mundo.impacto.y, mundo.impacto.z,
                c.direction.x, c.direction.y, c.direction.z, mundo.impactoMorreu
            )
            2 -> particulas.impacto(
                mundo.impacto.x, mundo.impacto.y, mundo.impacto.z,
                c.direction.x, c.direction.y, c.direction.z
            )
        }
    }

    /** Poeira nos pes ao correr e ao pousar do pulo. */
    private fun efeitosDeMovimento(dt: Float) {
        if (morto) return
        if (jogador.consumirPouso()) particulas.poeira(jogador.posicao.x, jogador.posicao.y, jogador.posicao.z, 7)
        tempoPoeira -= dt
        if (jogador.sprintando && tempoPoeira <= 0f) {
            tempoPoeira = 0.16f
            particulas.poeira(jogador.posicao.x, jogador.posicao.y, jogador.posicao.z, 1)
        }
    }

    /** INTERAGIR: guarda na maleta o item mais perto (raio 1,5 m). Se não couber, recusa. */
    private fun interagir() {
        if (historia?.interagir(jogador.posicao.x, jogador.posicao.z) == true) return
        val item = mundo.itemMaisProximo(
            jogador.posicao.x,
            jogador.posicao.z,
            jogador.posicao.y,
            Constantes.INTERAGIR_RAIO
        ) ?: return
        val armaChao = item.arma
        if (armaChao != null) { // arma no chao: ocupa espaco na maleta (estilo Resident Evil) e ja equipa
            if (!maleta.adicionarArma(armaChao)) {
                hud.mostrarMensagem(Constantes.MSG_MALETA_CHEIA)
                return
            }
            item.coletado = true
            arma.liberar(armaChao)
            hud.mostrarMensagem("Pegou: " + armaChao.nome)
            return
        }
        if (maleta.adicionar(item.tipo, item.quantidade)) {
            item.coletado = true
            hud.mostrarMensagem(
                if (item.tipo == TipoItem.CURA) Constantes.MSG_PEGOU_CURA else Constantes.MSG_PEGOU_MUNICAO
            )
        } else {
            hud.mostrarMensagem(Constantes.MSG_MALETA_CHEIA)
        }
    }

    /** Toque numa peca da maleta (indice = celula tocada): arma/faca equipa; cura +30 de vida; municao vai para a reserva. */
    private fun usarSlot(celula: Int) {
        val item = maleta.itemNaCelula(celula) ?: return
        usarItem(item)
    }

    private fun usarItem(item: com.sobrevivente.jogo.inventario.Item) {
        when (item.tipo) {
            TipoItem.CURA -> {
                if (jogador.vida >= Constantes.JOGADOR_VIDA_MAXIMA) {
                    hud.mostrarMensagem(Constantes.MSG_VIDA_CHEIA)
                    return
                }
                jogador.curar(Constantes.CURA_VALOR)
                maleta.remover(item, 1)
                hud.mostrarMensagem(Constantes.MSG_USOU_CURA)
            }
            TipoItem.MUNICAO -> {
                val quantidade = item.quantidade
                arma.adicionarReserva(quantidade)
                maleta.remover(item, quantidade)
                hud.mostrarMensagem(Constantes.MSG_USOU_MUNICAO)
            }
            TipoItem.ARMA -> {
                val t = item.arma ?: return
                if (arma.selecionarSlot(t.ordinal)) hud.mostrarMensagem("Equipou: " + t.nome)
                else hud.mostrarMensagem(if (arma.recarregando) "Recarregando..." else "Ja equipada")
            }
            TipoItem.FACA -> {
                if (arma.selecionarSlot(3)) hud.mostrarMensagem("Equipou: Faca")
                else hud.mostrarMensagem(if (arma.recarregando) "Recarregando..." else "Ja equipada")
            }
        }
    }

    /** Abre/fecha a maleta. Aberta, o jogo para (como a pausa) e o joystick fica desativado. */
    private fun alternarMaleta() {
        maletaAberta = !maletaAberta
        hud.maletaAberta = maletaAberta
        controles.definirModoMaleta(maletaAberta)
        Gdx.app.log("TELA", if (maletaAberta) "maleta aberta" else "maleta fechada")
    }

    private fun morrer() {
        morto = true
        hud.morto = true
        controles.definirMorto(true) // solta os dedos; só REINICIAR responde
        Gdx.app.log("TELA", "jogador morreu")
    }

    /** Restaura vida, munição e inimigos. O jogador é recriado (descarta o corpo antigo). */
    private fun reiniciar() {
        jogador.dispose()
        jogador = Jogador()
        arma.reiniciar()
        mundo.reiniciar()
        particulas.limpar()
        maleta.limpar()
        jogador.posicao.set(Fases.atual.inicio[0], 0f, Fases.atual.inicio[1])
        if (modoHistoria) {
            historia?.dispose()
            Progresso.ouro = ouroNoInicio
            aplicarProgresso()
            iniciarHistoria()
        }
        hud.limparMensagem()
        cameraJogo.seguir(jogador.posicao)
        morto = false
        hud.morto = false
        controles.definirMorto(false)
        Gdx.app.log("TELA", "reiniciado")
    }

    override fun render(delta: Float) {
        if (proximaFase >= 0) { // fase concluida -> proxima fase: nova tela com o mapa novo
            val n = proximaFase
            proximaFase = -1
            jogo.setScreen(TelaJogo(jogo, true, n))
            dispose()
            return
        }
        if (voltarAoMenu) { // menu de pausa -> MENU PRINCIPAL: troca de tela e descarta esta
            voltarAoMenu = false
            jogo.setScreen(TelaMenu(jogo))
            dispose()
            return
        }
        val dt = minOf(delta, Constantes.DELTA_MAXIMO)
        val densidade = Gdx.graphics.density

        // Entrada de alto nível
        controles.atualizar(dt)
        val apertouVoltar = controles.consumirVoltar()
        if (apertouVoltar) {
            if (maletaAberta) alternarMaleta() // Voltar fecha a maleta
            else if (pausado) { if (modo != 2) fecharMenu() } // Voltar fecha pausa/loja (fase concluida: so pelos botoes)
            else if (!morto) alternarPausa() // morto: Voltar não pausa
        }
        val botaoPausa = controles.consumirPausa()
        if (pausado && botaoPausa >= 0) tratarMenu(botaoPausa)
        if (controles.consumirPressao(ControlesToque.ACAO_MENU) && !pausado && !morto && !maletaAberta) alternarPausa()
        if (controles.consumirAlternarDebug()) hud.alternarDebug()
        if (controles.consumirAlternarMaleta() && !pausado && !morto) alternarMaleta()
        val slotUsado = controles.consumirUsarSlot()
        if (slotUsado >= 0 && maletaAberta) usarSlot(slotUsado)
        if (morto && controles.consumirReiniciar()) reiniciar()

        // Lógica (parada durante a pausa e com a maleta aberta)
        if (!pausado) {
            if (!maletaAberta) {
                if (morto) {
                    // Sem movimento nem tiro; só deixa um clarão em andamento terminar.
                    jogador.atualizarTiro(dt, cameraJogo.yaw, false)
                } else {
                    atualizarJogador(dt, densidade)
                }
                jogador.atualizarAnimacao(dt)
                var fx = cameraJogo.camera.direction.x
                var fz = cameraJogo.camera.direction.z
                val fl = kotlin.math.sqrt(fx * fx + fz * fz)
                if (fl > 0.001f) { fx /= fl; fz /= fl } else { fx = 0f; fz = -1f }
                mundo.atualizar(dt, jogador, fx, fz)
                historia?.atualizar(dt)
                efeitosDeMovimento(dt)
            }
            hud.atualizar(dt) // o tempo das mensagens corre também com a maleta aberta
            if (!morto && jogador.vida <= 0) morrer()
        }
        if (arma.faca) hud.definirMunicao("FACA", -1, 0, false)
        else hud.definirMunicao(arma.tipo.nome, arma.carregador, arma.reserva, arma.recarregando)
        hud.definirArmas(arma.slotAtivo, arma.estaLiberada(com.sobrevivente.jogo.mundo.TipoArma.FUZIL), arma.estaLiberada(com.sobrevivente.jogo.mundo.TipoArma.ESCOPETA))
        hud.definirAbates(mundo.abates)
        hud.definirCuras(maleta.total(TipoItem.CURA))
        hud.definirDor(jogador.dor)
        hud.definirVida(maxOf(jogador.vida, 0), Constantes.JOGADOR_VIDA_MAXIMA)
        hud.inimigosVivos = mundo.contarInimigosVivos()
        hud.definirJogador(jogador.posicao.x, jogador.posicao.z)
        hud.definirHistoria(Progresso.ouro, historia?.dicaProximo(jogador.posicao.x, jogador.posicao.z))

        // Desenho 3D (primeiro o mapa de sombra, depois a cena numa textura com pos-processamento)
        desenharSombras()
        pos.comecar()
        ScreenUtils.clear(
            Constantes.COR_CEU_R,
            Constantes.COR_CEU_G,
            Constantes.COR_CEU_B,
            1f,
            true
        )
        // Ceu (sem luz, sem escrever profundidade) em passada propria, depois o mundo
        mundo.cenario.seguirCamera(cameraJogo.camera)
        loteModelos.begin(cameraJogo.camera)
        loteModelos.render(mundo.cenario.ceu)
        loteModelos.end()
        loteModelos.begin(cameraJogo.camera)
        loteModelos.render(mundo.chao, mundo.ambiente)
        loteModelos.render(mundo.cenario.decoracao, mundo.ambiente)
        for (i in mundo.cenario.telhados.indices) {
            if (mundo.cenario.telhadoVisivel(i)) loteModelos.render(mundo.cenario.telhados[i], mundo.ambiente)
        }
        loteModelos.render(mundo.sombras[0], mundo.ambiente)
        for (i in mundo.inimigos.indices) {
            if (mundo.inimigos[i].visivel && mundo.sombraDoZumbiVisivel(i)) loteModelos.render(mundo.sombras[i + 1], mundo.ambiente)
        }
        for (i in mundo.pisos.indices) {
            loteModelos.render(mundo.pisos[i], mundo.ambiente)
        }
        for (i in mundo.paredes.indices) {
            if (mundo.paredeVisivel(i)) loteModelos.render(mundo.paredes[i], mundo.ambiente)
        }
        loteModelos.render(jogador.corpo, mundo.ambiente)
        if (!jogador.animado) loteModelos.render(jogador.nariz, mundo.ambiente)
        jogador.armaVisivel?.let { loteModelos.render(it, mundo.ambiente) }
        jogador.facaCabo?.let { loteModelos.render(it, mundo.ambiente) }
        if (jogador.claraoVisivel) loteModelos.render(jogador.clarao, mundo.ambiente)
        for (i in mundo.inimigos.indices) {
            val inimigo = mundo.inimigos[i]
            if (inimigo.visivel) loteModelos.render(inimigo.instancia, mundo.ambiente)
            mundo.marcadorLoot(i)?.let { loteModelos.render(it, mundo.ambiente) }
        }
        for (i in mundo.itens.indices) {
            val item = mundo.itens[i]
            if (!item.coletado) loteModelos.render(item.instancia, mundo.ambiente)
        }
        for (i in mundo.drops.indices) {
            val item = mundo.drops[i]
            if (!item.coletado) loteModelos.render(item.instancia, mundo.ambiente)
        }
        historia?.desenhar(loteModelos, mundo.ambiente)
        loteModelos.end()
        particulas.atualizar(if (pausado || maletaAberta) 0f else dt)
        particulas.desenhar(cameraJogo.camera)
        pos.terminar()

        // HUD: formas com transparência, depois textos
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        forma.begin(ShapeRenderer.ShapeType.Filled)
        if (!maletaAberta) joystick.desenhar(forma)
        hud.desenharFormas(forma)
        if (maletaAberta) joystick.desenhar(forma) // por cima do véu: é o botão que fecha a maleta
        forma.end()

        lote.begin()
        hud.desenharTextos(lote, Gdx.graphics.framesPerSecond)
        lote.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    override fun resize(width: Int, height: Int) {
        cameraJogo.redimensionar(width, height)
        pos.redimensionar(width, height)
        controles.redimensionar(width, height, Gdx.graphics.density)
        hud.redimensionar(width, height, Gdx.graphics.density)
        lote.projectionMatrix.setToOrtho2D(0f, 0f, width.toFloat(), height.toFloat())
        forma.projectionMatrix.setToOrtho2D(0f, 0f, width.toFloat(), height.toFloat())
        forma.updateMatrices()
        Gdx.app.log("TELA", "resize " + width + "x" + height)
    }

    override fun dispose() {
        loteModelos.dispose()
        loteSombra.dispose()
        pos.dispose()
        particulas.dispose()
        lote.dispose()
        forma.dispose()
        hud.dispose()
        historia?.dispose()
        jogador.dispose()
        mundo.dispose()
    }
}
