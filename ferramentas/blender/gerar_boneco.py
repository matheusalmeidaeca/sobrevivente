# ferramentas/blender/gerar_boneco.py
"""Marco F2: boneco com esqueleto e animacoes.
Le os OBJ do F0, monta no Blender a malha + armadura + pesos + 5 animacoes por personagem,
confere a pele do Blender contra a conta em Python, grava o modelo animado (g3dj) e os quadros de previa.
Uso: blender --background --factory-startup --python gerar_boneco.py -- <pasta_obj_do_F0> <pasta_saida>
Tudo que mexe com bpy esta SO neste arquivo; a matematica fica em animacao.py e exportar.py (Python puro).
"""
import os
import shutil
import sys
import traceback

import bpy
from mathutils import Matrix, Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import animacao as an  # noqa: E402
import exportar as ex  # noqa: E402

# ---- Parametros (constantes no topo) ----
PERSONAGENS = [("jogador", "boneco"), ("zumbi", "zumbi")]  # (estilo, nome do OBJ do F0)
PASTA_OBJ_PADRAO = "saida"
PASTA_SAIDA_PADRAO = "saida"
RESOLUCAO_X = 360
RESOLUCAO_Y = 400
AMOSTRAS = 16
COR_FUNDO = (0.80, 0.84, 0.88, 1.0)
ESCALA_ORTOGRAFICA = 2.4
POSICAO_CAMERA = (6.0, 0.0, 0.95)  # vista lateral, olhando para -X
ALVO_CAMERA = (0.0, 0.0, 0.95)
ENERGIA_SOL = 3.0
TOLERANCIA_PELE = 1e-3  # metros: diferenca aceita entre a pele do Blender e a conta em Python
LIMITE_TRIANGULOS = 2000  # secao 24.3
LIMITE_OSSOS = 25
LIMITE_TEXTURA = 512
ALTURA_ESPERADA = 1.8
ALTURA_TOLERANCIA = 0.05

# Conversao jogo (Y para cima, frente -Z) -> Blender (Z para cima, frente -Y): (x, y, z) -> (-x, z, y)
C = Matrix(((-1.0, 0.0, 0.0, 0.0), (0.0, 0.0, 1.0, 0.0), (0.0, 1.0, 0.0, 0.0), (0.0, 0.0, 0.0, 1.0)))
C_INV = C.inverted()


def log(mensagem):
    print("[ETAPA] " + mensagem, flush=True)


def aviso(mensagem):
    print("[AVISO] " + mensagem, flush=True)


def falhar(mensagem):
    print("[ERRO] " + mensagem, flush=True)
    sys.exit(1)


def argumentos():
    resto = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    pasta_obj = resto[0] if len(resto) > 0 else PASTA_OBJ_PADRAO
    pasta_saida = resto[1] if len(resto) > 1 else PASTA_SAIDA_PADRAO
    os.makedirs(pasta_saida, exist_ok=True)
    return os.path.abspath(pasta_obj), os.path.abspath(pasta_saida)


def jogo_para_blender(p):
    return (-p[0], p[2], p[1])


def olhar_para(objeto, alvo):
    direcao = Vector(alvo) - Vector(objeto.location)
    objeto.rotation_euler = direcao.to_track_quat('-Z', 'Y').to_euler()


# ---------------------------------------------------------------------------
# Cena
# ---------------------------------------------------------------------------
def cena_vazia():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    cena = bpy.context.scene
    cena.render.fps = an.FPS
    dados_camera = bpy.data.cameras.new("camera")
    dados_camera.type = 'ORTHO'
    dados_camera.ortho_scale = ESCALA_ORTOGRAFICA
    camera = bpy.data.objects.new("camera", dados_camera)
    cena.collection.objects.link(camera)
    camera.location = POSICAO_CAMERA
    olhar_para(camera, ALVO_CAMERA)
    cena.camera = camera
    dados_sol = bpy.data.lights.new("sol", type='SUN')
    dados_sol.energy = ENERGIA_SOL
    sol = bpy.data.objects.new("sol", dados_sol)
    cena.collection.objects.link(sol)
    sol.location = (4.0, -2.0, 6.0)
    olhar_para(sol, (0.0, 0.0, 0.0))
    mundo = bpy.data.worlds.new("mundo")
    mundo.use_nodes = True
    fundo = mundo.node_tree.nodes.get("Background")
    if fundo is not None:
        fundo.inputs["Color"].default_value = COR_FUNDO
    cena.world = mundo
    cena.render.engine = 'CYCLES'
    cena.cycles.device = 'CPU'
    cena.cycles.samples = AMOSTRAS
    cena.cycles.use_denoising = False
    cena.render.resolution_x = RESOLUCAO_X
    cena.render.resolution_y = RESOLUCAO_Y
    cena.render.resolution_percentage = 100
    cena.render.image_settings.file_format = 'PNG'
    return cena


def criar_malha(cena, estilo, vertices, indices, caminho_textura):
    malha = bpy.data.meshes.new(estilo + "_malha")
    posicoes = [jogo_para_blender(v["p"]) for v in vertices]
    # C tem determinante +1 (rotacao), entao a ordem dos vertices dos triangulos e mantida
    faces = [tuple(indices[i:i + 3]) for i in range(0, len(indices), 3)]
    malha.from_pydata(posicoes, [], faces)
    malha.update()
    camada = malha.uv_layers.new(name="UV")
    for poligono in malha.polygons:
        for k, laco in enumerate(poligono.loop_indices):
            v = vertices[faces[poligono.index][k]]
            camada.data[laco].uv = (v["uv"][0], v["uv"][1])  # Blender: v para cima, igual ao OBJ
    objeto = bpy.data.objects.new(estilo, malha)
    cena.collection.objects.link(objeto)
    material = bpy.data.materials.new(estilo + "_material")
    material.use_nodes = True
    bsdf = material.node_tree.nodes.get("Principled BSDF")
    imagem = bpy.data.images.load(caminho_textura)
    no_textura = material.node_tree.nodes.new("ShaderNodeTexImage")
    no_textura.image = imagem
    no_textura.interpolation = 'Closest'
    if bsdf is not None:
        material.node_tree.links.new(no_textura.outputs["Color"], bsdf.inputs["Base Color"])
    else:
        aviso("no 'Principled BSDF' nao encontrado; material sem textura ligada")
    malha.materials.append(material)
    for poligono in malha.polygons:
        poligono.use_smooth = False
    return objeto, imagem.size[0], imagem.size[1]


def criar_armadura(cena):
    dados = bpy.data.armatures.new("esqueleto")
    obj = bpy.data.objects.new("esqueleto", dados)
    cena.collection.objects.link(obj)
    bpy.context.view_layer.objects.active = obj
    obj.select_set(True)
    bpy.ops.object.mode_set(mode='EDIT')
    ossos = {}
    for nome, pai, cabeca, ponta in an.OSSOS:
        osso = dados.edit_bones.new(nome)
        osso.head = Vector(jogo_para_blender(cabeca))
        osso.tail = Vector(jogo_para_blender(ponta))
        osso.roll = 0.0
        if pai is not None:
            osso.parent = ossos[pai]
        ossos[nome] = osso
    bpy.ops.object.mode_set(mode='OBJECT')
    return obj


def ligar_pele(malha_obj, arm_obj, vertices):
    grupos = {nome: malha_obj.vertex_groups.new(name=nome) for nome in an.NOMES_OSSOS}
    for i, v in enumerate(vertices):
        for osso, peso in v["pesos"]:
            grupos[osso].add([i], peso, 'REPLACE')
    modificador = malha_obj.modifiers.new("Armature", 'ARMATURE')
    modificador.object = arm_obj
    malha_obj.parent = arm_obj


# ---------------------------------------------------------------------------
# Pose e animacao
# ---------------------------------------------------------------------------
def aplicar_pose(arm_obj, repouso, pose):
    """matrix_basis = repouso^-1 * C * D * C^-1 * repouso (nao depende do 'roll' do osso)."""
    for nome in an.NOMES_OSSOS:
        d = Matrix(an.matriz_relativa(pose, nome))
        pb = arm_obj.pose.bones[nome]
        pb.rotation_mode = 'QUATERNION'
        pb.matrix_basis = repouso[nome].inverted() @ C @ d @ C_INV @ repouso[nome]


def gravar_animacoes(arm_obj, repouso, estilo, pontos):
    arm_obj.animation_data_create()
    resumo = []
    for nome_anim, n_quadros, _loop in an.listar_animacoes(estilo):
        acao = bpy.data.actions.new(estilo + "_" + nome_anim)
        arm_obj.animation_data.action = acao
        for q in range(n_quadros + 1):
            pose = an.pose_final(estilo, nome_anim, float(q), pontos)
            aplicar_pose(arm_obj, repouso, pose)
            for nome in an.NOMES_OSSOS:
                pb = arm_obj.pose.bones[nome]
                pb.keyframe_insert("location", frame=q + 1)
                pb.keyframe_insert("rotation_quaternion", frame=q + 1)
        acao.use_fake_user = True
        # NAO TENHO CERTEZA: no Blender 4.4+ as curvas ficam em camadas/slots; so informamos o que existir.
        curvas = getattr(acao, "fcurves", None)
        resumo.append((nome_anim, n_quadros, len(curvas) if curvas is not None else -1))
    arm_obj.animation_data.action = None
    return resumo


def conferir_pele(malha_obj, arm_obj, repouso, estilo, nome_anim, quadro, vertices, pontos):
    """Compara a malha deformada pelo Blender com a conta em Python. Devolve a maior diferenca (m)."""
    pose = an.pose_final(estilo, nome_anim, float(quadro), pontos)
    aplicar_pose(arm_obj, repouso, pose)
    bpy.context.view_layer.update()
    grafo = bpy.context.evaluated_depsgraph_get()
    avaliado = malha_obj.evaluated_get(grafo)
    malha = bpy.data.meshes.new_from_object(avaliado)
    if len(malha.vertices) != len(vertices):
        bpy.data.meshes.remove(malha)
        falhar("a malha avaliada tem %d vertices, esperado %d" % (len(malha.vertices), len(vertices)))
    w = an.matrizes_mundo(pose)
    pior = 0.0
    for i, v in enumerate(vertices):
        esperado = jogo_para_blender(an.deformar(w, v["pesos"], v["p"]))
        real = malha.vertices[i].co
        pior = max(pior, abs(real.x - esperado[0]), abs(real.y - esperado[1]), abs(real.z - esperado[2]))
    bpy.data.meshes.remove(malha)
    return pior


def renderizar_quadros(cena, arm_obj, repouso, estilo, pontos, pasta):
    os.makedirs(pasta, exist_ok=True)
    caminhos = []
    for nome_anim, n_quadros, _loop in an.listar_animacoes(estilo):
        q = min(an.QUADRO_PREVIA[nome_anim], n_quadros)
        aplicar_pose(arm_obj, repouso, an.pose_final(estilo, nome_anim, float(q), pontos))
        bpy.context.view_layer.update()
        destino = os.path.join(pasta, "%s_%s.png" % (estilo, nome_anim))
        cena.render.filepath = destino
        try:
            bpy.ops.render.render(write_still=True)
        except Exception:
            traceback.print_exc()
            falhar("o render falhou em " + destino)
        if not os.path.isfile(destino):
            falhar("quadro nao foi criado: " + destino)
        caminhos.append(destino)
    return caminhos


# ---------------------------------------------------------------------------
# Principal
# ---------------------------------------------------------------------------
def processar(estilo, nome_obj, pasta_obj, pasta_saida, relatorio):
    log("[%s] lendo %s.obj" % (estilo, nome_obj))
    caminho_obj = os.path.join(pasta_obj, nome_obj + ".obj")
    if not os.path.isfile(caminho_obj):
        falhar("OBJ do F0 nao encontrado: " + caminho_obj + " (rode o F0 antes)")
    tris, texturas = ex.ler_obj(caminho_obj)
    if not texturas:
        falhar("o .mtl de %s nao informa textura (map_Kd)" % nome_obj)
    arquivo_textura = list(texturas.values())[0]
    caminho_textura = os.path.join(pasta_obj, arquivo_textura)
    if not os.path.isfile(caminho_textura):
        falhar("textura nao encontrada: " + caminho_textura)
    vertices, indices = ex.montar_vertices(tris)
    pontos = [(v["p"][0], v["p"][1], v["p"][2], v["pesos"]) for v in vertices]

    log("[%s] g3dj (matematica em Python)" % estilo)
    modelo = ex.montar_g3dj(estilo, tris, arquivo_textura, estilo)
    destino_g3dj = os.path.join(pasta_saida, estilo + ".g3dj")
    tamanho = ex.escrever_g3dj(destino_g3dj, modelo)
    shutil.copyfile(caminho_textura, os.path.join(pasta_saida, arquivo_textura))
    print("[OK] %s (%d bytes)" % (destino_g3dj, tamanho), flush=True)

    log("[%s] montando cena no Blender" % estilo)
    cena = cena_vazia()
    malha_obj, tex_w, tex_h = criar_malha(cena, estilo, vertices, indices, caminho_textura)
    arm_obj = criar_armadura(cena)
    ligar_pele(malha_obj, arm_obj, vertices)
    bpy.context.view_layer.update()
    repouso = {nome: arm_obj.data.bones[nome].matrix_local.copy() for nome in an.NOMES_OSSOS}

    log("[%s] conferindo a pele do Blender contra a conta em Python" % estilo)
    piores = []
    for nome_anim, n_quadros, _loop in an.listar_animacoes(estilo):
        for q in (0, n_quadros // 2, n_quadros):
            piores.append(conferir_pele(malha_obj, arm_obj, repouso, estilo, nome_anim, q, vertices, pontos))
    pior = max(piores)
    print("[PELE] maior diferenca Blender x Python: %.6f m" % pior, flush=True)
    if pior > TOLERANCIA_PELE:
        aviso("diferenca acima de %.4f m: a armadura do Blender NAO bate com o g3dj" % TOLERANCIA_PELE)

    log("[%s] gravando as animacoes na armadura" % estilo)
    resumo = gravar_animacoes(arm_obj, repouso, estilo, pontos)
    for nome_anim, n_quadros, curvas in resumo:
        print("[ANIM] %s: %d quadros, curvas visiveis na acao: %d" % (nome_anim, n_quadros, curvas), flush=True)

    log("[%s] renderizando 1 quadro por animacao" % estilo)
    caminhos = renderizar_quadros(cena, arm_obj, repouso, estilo, pontos, os.path.join(pasta_saida, "f2_quadros"))

    log("[%s] salvando .blend" % estilo)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.join(pasta_saida, estilo + "_animado.blend"))

    # ---- Orcamento (secao 24.3), medido no repouso ----
    ys = [v["p"][1] for v in vertices]
    altura = max(ys) - min(ys)
    pe_y = min(ys)
    n_tri = len(indices) // 3
    linhas = [
        "[%s]" % estilo,
        "  triangulos: %d (limite %d) %s" % (n_tri, LIMITE_TRIANGULOS, "OK" if n_tri <= LIMITE_TRIANGULOS else "ESTOUROU"),
        "  ossos: %d (limite %d) %s" % (len(an.OSSOS), LIMITE_OSSOS, "OK" if len(an.OSSOS) <= LIMITE_OSSOS else "ESTOUROU"),
        "  materiais: 1 (limite 1) OK",
        "  textura: %dx%d (limite %d) %s" % (tex_w, tex_h, LIMITE_TEXTURA,
                                            "OK" if max(tex_w, tex_h) <= LIMITE_TEXTURA else "ESTOUROU"),
        "  altura: %.3f m (esperado %.2f) %s" % (altura, ALTURA_ESPERADA,
                                                "OK" if abs(altura - ALTURA_ESPERADA) <= ALTURA_TOLERANCIA else "FORA"),
        "  origem nos pes: y minimo = %.3f %s" % (pe_y, "OK" if abs(pe_y) < 0.01 else "FORA"),
        "  vertices (apos juntar cantos iguais): %d" % len(vertices),
        "  pele Blender x Python: %.6f m %s" % (pior, "OK" if pior <= TOLERANCIA_PELE else "DIVERGE"),
        "  animacoes: " + ", ".join("%s(%d quadros, %s)" % (n, q, "loop" if lp else "sem loop")
                                    for n, q, lp in an.listar_animacoes(estilo)),
        "  g3dj: %s (%d bytes)" % (os.path.basename(destino_g3dj), tamanho),
    ]
    relatorio.extend(linhas)
    return caminhos


def main():
    print("[BLENDER] versao " + bpy.app.version_string, flush=True)
    print("[PYTHON] " + sys.version.replace("\n", " "), flush=True)
    pasta_obj, pasta_saida = argumentos()
    relatorio = ["F2 - relatorio do gerar_boneco.py", "Blender " + bpy.app.version_string, ""]
    for estilo, nome_obj in PERSONAGENS:
        processar(estilo, nome_obj, pasta_obj, pasta_saida, relatorio)
    with open(os.path.join(pasta_saida, "f2_relatorio.txt"), "w") as f:
        f.write("\n".join(relatorio) + "\n")
    print("\n".join(relatorio), flush=True)
    print("[OK] F2 gerado em " + pasta_saida, flush=True)


main()
