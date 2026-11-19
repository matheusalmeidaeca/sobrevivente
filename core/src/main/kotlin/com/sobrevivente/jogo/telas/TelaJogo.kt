// core/src/main/kotlin/com/sobrevivente/jogo/telas/TelaJogo.kt
package com.sobrevivente.jogo.telas

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.VertexAttributes.Usage
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
import com.sobrevivente.jogo.Desempenho
import com.sobrevivente.jogo.audio.Som
import com.sobrevivente.jogo.audio.Trilha
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
import com.sobrevivente.jogo.mundo.Cidade
import com.sobrevivente.jogo.mundo.Historia
import com.sobrevivente.jogo.mundo.Missao
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
    private var historia: Missao? = null
    private var ouroNoInicio = 0
    /** Menu modal aberto: 0 = pausa, 1 = loja do mercador, 2 = fase concluida. */
    private var modo = 0
    private var jogador = Jogador() // var: é recriado ao reiniciar depois da morte
    private val arma = Arma()
    private val direcaoTiro = Vector3() // reaproveitado: dispersao dos bagos da escopeta
    private val cameraJogo = CameraTerceiraPessoa()
    /** F18: mira ligada (arma levantada, laser, personagem parado). */
    private var mirando = false
    private val som = Som()
    private var tempoPasso = 0f
    private var vidaAnterior = Constantes.JOGADOR_VIDA_MAXIMA
    private var abatesAnteriores = 0
    private var tempoGemido = 2f
    private var tempoPerigo = 0f
    private var tempoDorSom = 0f
    private var tempoCliqueVazio = 0f
    private var recarregandoAntes = false
    private var mirandoAntes = false
    private var mirandoManual = false
    private var tempoSemAtirar = 0f
    private var chefeAvisado = false
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
        hud.configurarMapa(Fases.atual.cidade)
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
        if (Progresso.smg && maleta.adicionarArma(TipoArma.SUBMETRALHADORA)) arma.liberar(TipoArma.SUBMETRALHADORA)
        if (Progresso.sniper && maleta.adicionarArma(TipoArma.SNIPER)) arma.liberar(TipoArma.SNIPER)
        arma.importar(Progresso.mods)
        arma.adicionarReserva(Progresso.reserva)
        if (Progresso.curas > 0) maleta.adicionar(TipoItem.CURA, Progresso.curas)
    }

    private fun iniciarHistoria() {
        val h: Missao = if (Fases.atual.cidade) {
            Cidade(mundo, Fases.atual).also {
                it.jogador = jogador
                it.aoSom = { nome, vol -> som.tocar(nome, vol, 0f, 0.05f) }
            }
        } else Historia(mundo, Fases.atual)
        h.aoMensagem = { t -> hud.mostrarMensagem(t) }
        h.aoAbrirLoja = { abrirMenu(1) }
        mundo.aoOnda = { n -> if (n > 1) hud.mostrarMensagem("ONDA " + n + " - os zumbis estao mais rapidos!") }
        h.aoConcluir = { concluirFase() }
        historia = h
        hud.historia = h
        hud.mostrarMensagem(Fases.atual.subtitulo + ": " + Fases.atual.nome)
    }

    private fun concluirFase() {
        Progresso.fuzil = arma.estaLiberada(TipoArma.FUZIL)
        Progresso.escopeta = arma.estaLiberada(TipoArma.ESCOPETA)
        Progresso.smg = arma.estaLiberada(TipoArma.SUBMETRALHADORA)
        Progresso.sniper = arma.estaLiberada(TipoArma.SNIPER)
        Progresso.reserva = arma.reserva
        arma.exportar(Progresso.mods)
        Progresso.curas = maleta.total(TipoItem.CURA)
        som.tocar("fase_completa", 0.9f)
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
    private fun perto(x: Float, z: Float): Boolean {
        val r = Desempenho.raioDesenho
        val dx = x - jogador.posicao.x
        val dz = z - jogador.posicao.z
        return dx * dx + dz * dz <= r * r
    }

    private fun desenharSombras() {
        val luz = mundo.luzSol ?: return
        if (!Desempenho.sombras) return
        luz.begin(jogador.posicao, cameraJogo.camera.direction)
        loteSombra.begin(luz.camera)
        for (i in mundo.paredes.indices) {
            if (mundo.paredeVisivel(i) && mundo.paredePerto(i, jogador.posicao.x, jogador.posicao.z, Desempenho.raioSombra)) loteSombra.render(mundo.paredes[i])
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
                lojaPagina = 0
                montarLoja("Escolha o que comprar")
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

    /** Pagina da loja: 0 principal, 1 armas, 2 equipamentos da arma equipada. */
    private var lojaPagina = 0
    private val PRECO_MUNICAO = 20
    private val PRECO_CURA = 35
    private val PRECO_FUZIL = 150
    private val PRECO_ESCOPETA = 180
    private val PRECO_SMG = 220
    private val PRECO_SNIPER = 320
    private val PRECO_PENTE = 120
    private val PRECO_CORONHA = 100
    private val PRECO_MIRA = 110

    private fun montarLoja(aviso: String) {
        val r: Array<String>
        val c: IntArray
        when (lojaPagina) {
            1 -> {
                r = arrayOf(
                    if (arma.estaLiberada(TipoArma.FUZIL)) "FUZIL  (ja tem)" else "FUZIL  ($PRECO_FUZIL)",
                    if (arma.estaLiberada(TipoArma.ESCOPETA)) "ESCOPETA  (ja tem)" else "ESCOPETA  ($PRECO_ESCOPETA)",
                    if (arma.estaLiberada(TipoArma.SUBMETRALHADORA)) "SUBMETRALHADORA  (ja tem)" else "SUBMETRALHADORA  ($PRECO_SMG)",
                    if (arma.estaLiberada(TipoArma.SNIPER)) "RIFLE DE PRECISAO  (ja tem)" else "RIFLE DE PRECISAO  ($PRECO_SNIPER)",
                    "VOLTAR"
                )
                c = intArrayOf(2, 2, 2, 2, 1)
            }
            2 -> {
                val f = arma.faca
                r = arrayOf(
                    if (f) "PENTE MAIOR  (sem arma)" else if (arma.temPente) "PENTE MAIOR  (comprado)" else "PENTE MAIOR  ($PRECO_PENTE)",
                    if (f) "CORONHA  (sem arma)" else if (arma.temCoronha) "CORONHA  (comprada)" else "CORONHA  ($PRECO_CORONHA)",
                    if (f) "MIRA  (sem arma)" else if (arma.temMira) "MIRA  (comprada)" else "MIRA  ($PRECO_MIRA)",
                    if (f) "MELHORAR ARMA" else if (arma.nivelAtual >= 3) "MELHORAR  (maximo)" else "MELHORAR ARMA +" + (arma.nivelAtual + 1) + "  (" + arma.precoNivel() + ")",
                    "VOLTAR"
                )
                c = intArrayOf(2, 2, 2, 0, 1)
            }
            else -> {
                r = arrayOf("MUNICAO +30  ($PRECO_MUNICAO)", "CURA  ($PRECO_CURA)", "ARMAS >", "EQUIPAMENTOS >", "FECHAR")
                c = intArrayOf(2, 2, 2, 2, 1)
            }
        }
        hud.menuRotulos = r
        hud.menuCores = c
        controles.definirMenu(r.size)
        val arm = if (lojaPagina == 2 && !arma.faca) "  -  " + arma.nomeComNivel else ""
        hud.menuSubtitulo = "Ouro: " + Progresso.ouro + arm + (if (aviso.isNotEmpty()) "  -  $aviso" else "")
    }

    private fun atualizarLoja(aviso: String) = montarLoja(aviso)

    /** Gasta `preco` se der e roda `efeito` (devolve a mensagem, ou null se nao pode). */
    private fun gastar(preco: Int, efeito: () -> String?) {
        if (Progresso.ouro < preco) { montarLoja("Ouro insuficiente"); return }
        val msg = efeito()
        if (msg == null) return
        Progresso.ouro -= preco
        montarLoja(msg)
    }

    /** Toque num botao da loja (indice na pagina atual). */
    private fun comprar(i: Int) {
        when (lojaPagina) {
            0 -> when (i) {
                0 -> gastar(PRECO_MUNICAO) { arma.adicionarReserva(30); "Comprou municao" }
                1 -> gastar(PRECO_CURA) { if (maleta.adicionar(TipoItem.CURA, 1)) "Comprou cura" else { montarLoja(Constantes.MSG_MALETA_CHEIA); null } }
                2 -> { lojaPagina = 1; montarLoja("") }
                3 -> { lojaPagina = 2; montarLoja("") }
                else -> fecharMenu()
            }
            1 -> when (i) {
                0 -> if (arma.estaLiberada(TipoArma.FUZIL)) montarLoja("Voce ja tem o fuzil")
                     else gastar(PRECO_FUZIL) { if (maleta.adicionarArma(TipoArma.FUZIL)) { arma.liberar(TipoArma.FUZIL); "Comprou o fuzil" } else { montarLoja(Constantes.MSG_MALETA_CHEIA); null } }
                1 -> if (arma.estaLiberada(TipoArma.ESCOPETA)) montarLoja("Voce ja tem a escopeta")
                     else gastar(PRECO_ESCOPETA) { if (maleta.adicionarArma(TipoArma.ESCOPETA)) { arma.liberar(TipoArma.ESCOPETA); "Comprou a escopeta" } else { montarLoja(Constantes.MSG_MALETA_CHEIA); null } }
                2 -> if (arma.estaLiberada(TipoArma.SUBMETRALHADORA)) montarLoja("Voce ja tem a submetralhadora")
                     else gastar(PRECO_SMG) { if (maleta.adicionarArma(TipoArma.SUBMETRALHADORA)) { arma.liberar(TipoArma.SUBMETRALHADORA); "Comprou a submetralhadora" } else { montarLoja(Constantes.MSG_MALETA_CHEIA); null } }
                3 -> if (arma.estaLiberada(TipoArma.SNIPER)) montarLoja("Voce ja tem o rifle")
                     else gastar(PRECO_SNIPER) { if (maleta.adicionarArma(TipoArma.SNIPER)) { arma.liberar(TipoArma.SNIPER); "Comprou o rifle de precisao" } else { montarLoja(Constantes.MSG_MALETA_CHEIA); null } }
                else -> { lojaPagina = 0; montarLoja("") }
            }
            else -> {
                if (i == 4) { lojaPagina = 0; montarLoja(""); return }
                if (arma.faca) { montarLoja("Equipe uma arma de fogo"); return }
                when (i) {
                    0 -> if (arma.temPente) montarLoja("Ja comprado") else gastar(PRECO_PENTE) { arma.comprarPente(); "Pente maior instalado" }
                    1 -> if (arma.temCoronha) montarLoja("Ja comprada") else gastar(PRECO_CORONHA) { arma.comprarCoronha(); "Coronha instalada" }
                    2 -> if (arma.temMira) montarLoja("Ja comprada") else gastar(PRECO_MIRA) { arma.comprarMira(); "Mira instalada" }
                    else -> if (arma.nivelAtual >= 3) montarLoja("Ja esta no maximo") else gastar(arma.precoNivel()) { arma.comprarNivel(); "Arma melhorada" }
                }
            }
        }
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
        // Camera livre no ombro: o toque direito gira a camera; o analogico anda relativo a ela.
        val giroX = controles.consumirGiroX() / densidade
        val giroY = controles.consumirGiroY() / densidade
        cameraJogo.girar(giroX, giroY)
        cameraJogo.fovMira = arma.fovMira
        cameraJogo.velMira = arma.velMira
        if (controles.consumirPressao(ControlesToque.ACAO_MIRAR) && !arma.faca) { mirando = !mirando; mirandoManual = mirando }
        val apertouAtirar = controles.acaoPressionada(ControlesToque.ACAO_ATIRAR) ||
            controles.acaoPressionada(ControlesToque.ACAO_ATIRAR_ESQ)
        if (arma.faca) { mirando = false; mirandoManual = false }
        else if (apertouAtirar && !mirando) mirando = true
        // mira levantada so por causa do tiro baixa sozinha depois de um tempo sem atirar
        if (apertouAtirar) tempoSemAtirar = 0f else tempoSemAtirar += dt
        if (mirando && !mirandoManual && tempoSemAtirar > 1.6f) mirando = false
        // correr abaixa a arma (como no Call of Duty): sprint cancela a mira se nao esta atirando
        if (mirando && !apertouAtirar && kotlin.math.sqrt(controles.movimentoX * controles.movimentoX + controles.movimentoY * controles.movimentoY) >= Constantes.JOGADOR_SPRINT_LIMIAR) {
            mirando = false; mirandoManual = false
        }
        // recarregar abaixa a mira e nao atrasa: da para fugir recarregando
        if (arma.recarregando && !apertouAtirar) { mirando = false; mirandoManual = false }
        mirandoAntes = mirando
        jogador.mover(dt, controles.movimentoX, controles.movimentoY, mirando, cameraJogo.yaw)
        if (mirando) jogador.manterMira()
        cameraJogo.atualizar(dt, jogador.posicao, mirando)
        hud.mirando = mirando && cameraJogo.mira > 0.5f
        controles.pularAtivo = jogador.podePular

        // Tiro: o raio sai da câmera, atravessa o centro da tela (onde fica a mira)
        val atirando = apertouAtirar && (arma.faca || cameraJogo.mira >= Constantes.RE4_MIRA_PRONTA)
        val atirou = arma.atualizar(dt, atirando, controles.consumirPressao(ControlesToque.ACAO_RECARREGAR))
        if (atirou) {
            val tipo = arma.tipo
            som.tocar(
                when (tipo) {
                    com.sobrevivente.jogo.mundo.TipoArma.PISTOLA -> "tiro_pistola"
                    com.sobrevivente.jogo.mundo.TipoArma.FUZIL, com.sobrevivente.jogo.mundo.TipoArma.SUBMETRALHADORA, com.sobrevivente.jogo.mundo.TipoArma.SNIPER -> "tiro_fuzil"
                    else -> "tiro_escopeta"
                },
                1f, 0f, 0.03f
            )
            var acertou = false
            for (i in 0 until tipo.projeteis) {
                direcaoTiro.set(cameraJogo.camera.direction)
                // andando a mira abre (tiro menos preciso); coronha segura
                val abre = if (jogador.intensidadeMov > 0.3f) 0.010f * arma.coronhaFator else 0f
                val d = arma.dispersaoAtual + abre
                if (d > 0f) {
                    direcaoTiro.add(MathUtils.random(-d, d), MathUtils.random(-d, d), MathUtils.random(-d, d)).nor()
                }
                if (mundo.atirar(cameraJogo.camera.position, direcaoTiro, tipo.alcance, arma.danoAtual, tipo)) acertou = true
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
        if (arma.golpeFaca) {
            som.tocar("faca_ar", 0.8f)
            golpearComFaca()
        }
        tempoCliqueVazio -= dt
        if (apertouAtirar && !arma.faca && !arma.recarregando && arma.carregador <= 0 &&
            cameraJogo.mira >= Constantes.RE4_MIRA_PRONTA && tempoCliqueVazio <= 0f
        ) {
            som.tocar("clique_vazio", 0.8f)
            tempoCliqueVazio = 0.45f
        }
        if (arma.recarregando && !recarregandoAntes) som.tocar("recarga", 0.9f)
        recarregandoAntes = arma.recarregando
        if (controles.consumirPressao(ControlesToque.ACAO_TROCAR)) {
            if (arma.trocar()) hud.mostrarMensagem(nomeEquipado())
            else hud.mostrarMensagem("Sem outra arma")
        }
        for (k in 0..5) {
            if (controles.consumirPressao(ControlesToque.ACAO_ARMA0 + k)) escolherSlotArma(k)
        }
        if (controles.consumirPressao(ControlesToque.ACAO_CURAR)) usarCuraRapida()
        if (controles.consumirPressao(ControlesToque.ACAO_PULAR)) jogador.pular()
        if (controles.consumirPressao(ControlesToque.ACAO_AGACHAR)) jogador.alternarAgachar()
        jogador.definirArma(arma.tipo)
        jogador.definirFaca(arma.faca)
        jogador.definirRecarga(arma.recarregando)
        if (controles.consumirPressao(ControlesToque.ACAO_INTERAGIR)) interagir()
        jogador.atualizarTiro(dt, jogador.yaw, atirou || arma.golpeFaca)
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
            som.tocar("faca_acerto", 0.9f)
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
    /** F18: passos, dor, gemidos dos zumbis (com direcao/volume pela distancia) e troca de trilha conforme o perigo. */
    private fun atualizarSom(dt: Float) {
        if (morto) {
            som.atualizar(dt)
            return
        }
        // passos
        if (jogador.andandoNoChao) {
            tempoPasso -= dt
            if (tempoPasso <= 0f) {
                val correndo = jogador.sprintando
                tempoPasso = if (correndo) 0.30f else 0.52f
                som.tocarVariante(if (correndo) "corrida" else "passo", 3, if (correndo) 0.7f else 0.5f, 0f, 0.08f)
            }
        } else {
            tempoPasso = 0.08f
        }
        // dor
        tempoDorSom -= dt
        if (jogador.vida < vidaAnterior && tempoDorSom <= 0f) {
            som.tocarVariante("jogador_dor", 2, 0.9f)
            som.tocar("zumbi_ataque", 0.45f)
            tempoDorSom = 0.5f
        }
        vidaAnterior = jogador.vida
        // abates
        if (mundo.abates > abatesAnteriores) som.tocar("zumbi_morre", 0.8f, 0f, 0.08f)
        abatesAnteriores = mundo.abates
        // zumbis proximos
        var perto = 999f
        var dxP = 0f
        var dzP = 0f
        for (i in mundo.inimigos.indices) {
            val z = mundo.inimigos[i]
            if (!z.vivo) continue
            val dx = z.posicao.x - jogador.posicao.x
            val dz = z.posicao.z - jogador.posicao.z
            val d = kotlin.math.sqrt(dx * dx + dz * dz)
            if (d < perto) {
                perto = d
                dxP = dx
                dzP = dz
            }
        }
        tempoGemido -= dt
        if (perto < 18f && tempoGemido <= 0f) {
            val ry = cameraJogo.yaw * MathUtils.degreesToRadians
            val pan = if (perto > 0.1f) (dxP * MathUtils.cos(ry) - dzP * MathUtils.sin(ry)) / perto else 0f
            som.tocarVariante("zumbi_gemido", 3, (1f - perto / 18f) * 0.95f + 0.05f, pan * 0.8f, 0.10f)
            tempoGemido = 1.8f + MathUtils.random(0f, 3.5f) * (0.4f + perto / 18f)
        }
        // chefe: rugido ao aparecer
        val chefe = historia?.chefeAtual()
        if (chefe != null && !chefeAvisado) {
            chefeAvisado = true
            som.tocar("chefe_rugido", 1f)
        }
        // trilha
        tempoPerigo -= dt
        if (perto < 11f || (chefe != null && chefe.vivo)) tempoPerigo = 5f
        som.definirTrilha(
            when {
                tempoPerigo > 0f -> Trilha.COMBATE
                perto < 26f -> Trilha.TENSAO
                else -> Trilha.AMBIENTE
            }
        )
        som.atualizar(dt)
    }

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
        if (historia?.interagir(jogador.posicao.x, jogador.posicao.z) == true) {
            som.tocar("alavanca", 0.8f)
            return
        }
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
            som.tocar("item_pegar", 0.8f)
            arma.liberar(armaChao)
            hud.mostrarMensagem("Pegou: " + armaChao.nome)
            return
        }
        if (maleta.adicionar(item.tipo, item.quantidade)) {
            item.coletado = true
            som.tocar("item_pegar", 0.8f)
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
                som.tocar("curar", 0.8f)
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
        som.tocar("derrota", 0.9f)
        som.definirTrilha(Trilha.TENSAO)
    }

    /** Restaura vida, munição e inimigos. O jogador é recriado (descarta o corpo antigo). */
    private fun reiniciar() {
        jogador.dispose()
        jogador = Jogador()
        mirando = false
        mirandoAntes = false
        vidaAnterior = Constantes.JOGADOR_VIDA_MAXIMA
        abatesAnteriores = 0
        chefeAvisado = false
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
            jogo.setScreen(TelaCarregando(jogo, true, n))
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
        Desempenho.medir(delta)
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
                    jogador.atualizarTiro(dt, jogador.yaw, false)
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
                atualizarSom(dt)
            }
            hud.atualizar(dt) // o tempo das mensagens corre também com a maleta aberta
            if (!morto && jogador.vida <= 0) morrer()
        }
        if (arma.faca) hud.definirMunicao("FACA", -1, 0, false)
        else hud.definirMunicao(arma.nomeComNivel, arma.carregador, arma.reserva, arma.recarregando)
        hud.definirArmas(arma.slotAtivo, arma.estaLiberada(TipoArma.FUZIL), arma.estaLiberada(TipoArma.ESCOPETA), arma.estaLiberada(TipoArma.SUBMETRALHADORA), arma.estaLiberada(TipoArma.SNIPER))
        hud.definirAbates(mundo.abates)
        hud.definirCuras(maleta.total(TipoItem.CURA))
        hud.definirDor(jogador.dor)
        hud.definirVida(maxOf(jogador.vida, 0), Constantes.JOGADOR_VIDA_MAXIMA)
        hud.inimigosVivos = mundo.contarInimigosVivos()
        hud.definirJogador(jogador.posicao.x, jogador.posicao.z, jogador.yaw)
        if (controles.consumirMapa()) hud.alternarMapa()
        controles.definirMapa(
            hud.minimapaTemImagem, hud.minimapaX, hud.minimapaY, hud.minimapaLado,
            hud.mapaAberto, hud.mapaGrandeX, hud.mapaGrandeY, hud.mapaGrandeW, hud.mapaGrandeH
        )
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
        mundo.agua?.let { loteModelos.render(it, mundo.ambiente) }
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
            if (mundo.paredeVisivel(i) && mundo.paredePerto(i, jogador.posicao.x, jogador.posicao.z, Desempenho.raioDesenho) && mundo.paredeNoCampo(i, cameraJogo.camera.frustum)) loteModelos.render(mundo.paredes[i], mundo.ambiente)
        }
        loteModelos.render(jogador.corpo, mundo.ambiente)
        if (!jogador.animado) loteModelos.render(jogador.nariz, mundo.ambiente)
        jogador.armaVisivel?.let { loteModelos.render(it, mundo.ambiente) }
        jogador.facaCabo?.let { loteModelos.render(it, mundo.ambiente) }
        if (jogador.claraoVisivel) loteModelos.render(jogador.clarao, mundo.ambiente)
        for (i in mundo.inimigos.indices) {
            val inimigo = mundo.inimigos[i]
            if (inimigo.visivel && perto(inimigo.posicao.x, inimigo.posicao.z)) loteModelos.render(inimigo.instancia, mundo.ambiente)
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
        if (hud.minimapaTemImagem) {
            lote.begin()
            hud.desenharImagemMapa(lote, jogador.posicao.x, jogador.posicao.z)
            lote.end()
        }
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
        som.dispose()
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
