# ferramentas/blender/teste_f1.py
"""Marco F1: prova que o Blender roda sem tela no CI.
Cria um cubo, renderiza preview.png e registra a versao do Blender.
Uso: blender --background --factory-startup --python teste_f1.py -- <pasta_saida>
"""
import os
import sys
import traceback

import bpy
from mathutils import Vector

# ---- Parametros (constantes no topo) ----
RESOLUCAO_X = 640
RESOLUCAO_Y = 360
AMOSTRAS = 32
COR_FUNDO = (0.16, 0.17, 0.20, 1.0)
COR_CUBO = (0.80, 0.25, 0.20, 1.0)
LADO_CUBO = 1.0
POSICAO_CAMERA = (3.0, -4.0, 2.5)
ALVO_CAMERA = (0.0, 0.0, 0.5)
POSICAO_SOL = (4.0, -2.0, 6.0)
ENERGIA_SOL = 3.0
PASTA_PADRAO = "saida"


def log(mensagem):
    print("[ETAPA] " + mensagem, flush=True)


def falhar(mensagem):
    print("[ERRO] " + mensagem, flush=True)
    sys.exit(1)


def pasta_saida():
    argumentos = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    pasta = argumentos[0] if argumentos else PASTA_PADRAO
    os.makedirs(pasta, exist_ok=True)
    return os.path.abspath(pasta)


def olhar_para(objeto, alvo):
    direcao = Vector(alvo) - Vector(objeto.location)
    objeto.rotation_euler = direcao.to_track_quat('-Z', 'Y').to_euler()


def montar_cena():
    log("Limpando a cena (arquivo de fabrica vazio)")
    bpy.ops.wm.read_factory_settings(use_empty=True)
    cena = bpy.context.scene

    log("Criando o cubo")
    bpy.ops.mesh.primitive_cube_add(size=LADO_CUBO, location=(0.0, 0.0, LADO_CUBO / 2.0))
    cubo = bpy.context.active_object
    material = bpy.data.materials.new("cubo_material")
    material.use_nodes = True
    bsdf = material.node_tree.nodes.get("Principled BSDF")
    if bsdf is None:
        print("[AVISO] No 'Principled BSDF' nao encontrado; cubo fica com a cor padrao", flush=True)
    else:
        bsdf.inputs["Base Color"].default_value = COR_CUBO
    cubo.data.materials.append(material)

    log("Criando camera e luz")
    dados_camera = bpy.data.cameras.new("camera")
    camera = bpy.data.objects.new("camera", dados_camera)
    cena.collection.objects.link(camera)
    camera.location = POSICAO_CAMERA
    olhar_para(camera, ALVO_CAMERA)
    cena.camera = camera

    dados_sol = bpy.data.lights.new("sol", type='SUN')
    dados_sol.energy = ENERGIA_SOL
    sol = bpy.data.objects.new("sol", dados_sol)
    cena.collection.objects.link(sol)
    sol.location = POSICAO_SOL
    olhar_para(sol, (0.0, 0.0, 0.0))

    mundo = bpy.data.worlds.new("mundo")
    mundo.use_nodes = True
    fundo = mundo.node_tree.nodes.get("Background")
    if fundo is not None:
        fundo.inputs["Color"].default_value = COR_FUNDO
    cena.world = mundo
    return cena


def renderizar(cena, pasta):
    log("Configurando o render (Cycles, CPU)")
    cena.render.engine = 'CYCLES'
    cena.cycles.device = 'CPU'
    cena.cycles.samples = AMOSTRAS
    cena.cycles.use_denoising = False
    cena.render.resolution_x = RESOLUCAO_X
    cena.render.resolution_y = RESOLUCAO_Y
    cena.render.resolution_percentage = 100
    cena.render.image_settings.file_format = 'PNG'
    destino = os.path.join(pasta, "preview.png")
    cena.render.filepath = destino
    log("Renderizando")
    try:
        bpy.ops.render.render(write_still=True)
    except Exception:
        traceback.print_exc()
        falhar("o render falhou")
    if not os.path.isfile(destino):
        falhar("preview.png nao foi criado em " + destino)
    return destino


def main():
    print("[BLENDER] versao " + bpy.app.version_string, flush=True)
    print("[PYTHON] " + sys.version.replace("\n", " "), flush=True)
    pasta = pasta_saida()
    with open(os.path.join(pasta, "versao_blender.txt"), "w") as arquivo:
        arquivo.write("Blender " + bpy.app.version_string + "\n")
        arquivo.write("Python " + sys.version.replace("\n", " ") + "\n")
    cena = montar_cena()
    destino = renderizar(cena, pasta)
    print("[OK] preview gravado em " + destino, flush=True)


main()
