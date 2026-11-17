# ferramentas/hd/gerar_garoto.py
"""F9: gera o GAROTO (protagonista) pelo pipeline ALTA -> BAIXA -> BAKE.
Uso: python3 gerar_garoto.py <pasta_saida> [--alta]   (--alta tambem gera a malha de alta poligonagem em .obj.gz)
Saidas: jogador.g3dj + textura_jogador.png (difusa com AO/sujeira assados) + textura_rosto.png (F13: atlas da cabeca)
+ normal_jogador.png, ao_jogador.png, curv_jogador.png"""
import gzip
import os
import sys
import time

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)
sys.path.insert(0, os.path.join(AQUI, "..", "blender"))
import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

import bake  # noqa: E402
import exportar as ex  # noqa: E402
import garoto  # noqa: E402
import kit  # noqa: E402
import maos  # noqa: E402
import rosto  # noqa: E402
import texturas  # noqa: E402
import texturas_garoto as tg  # noqa: E402


def salvar_png(a, caminho):
    a = np.clip(a, 0, 1)
    Image.fromarray((a * 255).astype(np.uint8)).save(caminho, optimize=True)


def exportar_obj(m, caminho):
    P, N, UV = np.vstack(m.P), np.vstack(m.N), np.vstack(m.UV)
    with gzip.open(caminho, "wt", compresslevel=6) as f:
        f.write("# GAROTO alta poligonagem (kit.FINO=True)\n")
        for p in P:
            f.write("v %.5f %.5f %.5f\n" % tuple(p))
        for n in N:
            f.write("vn %.4f %.4f %.4f\n" % tuple(n))
        for t in UV:
            f.write("vt %.5f %.5f\n" % tuple(t))
        for i in range(1, len(P) + 1, 3):
            f.write("f %d/%d/%d %d/%d/%d %d/%d/%d\n" % (i, i, i, i + 1, i + 1, i + 1, i + 2, i + 2, i + 2))


if __name__ == "__main__":
    saida = sys.argv[1]
    os.makedirs(saida, exist_ok=True)
    t0 = time.time()
    # ---- BAIXA (vai para o jogo)
    kit.FINO = False
    # perfil do jogo: --leve (~24k tris, celular fraco) ou o padrao (~150k tris, SEM micro-relevo; malha dividida em partes)
    g = (garoto.Garoto(False) if "--leve" in sys.argv else garoto.Garoto(False, lod=2.0, cabelo=170)).tudo()
    tris = g.m.triangulos()
    print("BAIXA:", len(tris), "triangulos (%.0fs)" % (time.time() - t0))
    # ---- difusa (relevo/AO/sujeira da ALTA assados) + mapas
    A = tg.pintar(g)
    salvar_png(A, os.path.join(saida, "textura_jogador.png"))
    N, AO, CV = bake.mapas(g)
    salvar_png(N, os.path.join(saida, "normal_jogador.png"))
    salvar_png(AO, os.path.join(saida, "ao_jogador.png"))
    salvar_png(CV, os.path.join(saida, "curv_jogador.png"))
    print("bake ok (%.0fs)" % (time.time() - t0))
    tex_cabeca = None
    if "cabeca_real" in g.tubos:   # F13: a cabeca realista tem atlas proprio
        tex_cabeca = rosto.ARQUIVO_TEXTURA
        salvar_png(rosto.pintar(g.tubos["cabeca_real"]), os.path.join(saida, tex_cabeca))
        print("cabeca:", {k: v for k, v in g.m.contagem.items() if k in ("pele", "olho", "orelha", "cabelo", "mecha")})
    tex_mao = "textura_maos_jogador.png"   # F15: maos com textura propria (3o material)
    salvar_png(maos.pintar(g), os.path.join(saida, tex_mao))
    modelo = ex.montar_g3dj("jogador", tris, "textura_jogador.png", "jogador", textura_cabeca=tex_cabeca, textura_mao=tex_mao)
    nv = sum(len(mm["vertices"]) // 12 for mm in modelo["meshes"])
    tam = ex.escrever_g3dj(os.path.join(saida, "jogador.g3dj"), modelo)
    print("jogador.g3dj:", len(modelo["meshes"]), "malhas,", nv, "vertices,", len(modelo["animations"]), "animacoes,", tam // 1024, "KB (%.0fs)" % (time.time() - t0))
    if "--alta" in sys.argv:
        kit.FINO = True
        h = garoto.Garoto(True).tudo()
        kit.FINO = False
        n_tri = sum(h.m.contagem.values())
        exportar_obj(h.m, os.path.join(saida, "garoto_alta.obj.gz"))
        print("ALTA:", n_tri, "triangulos (geometria) + micro-relevo analitico no bake (%.0fs)" % (time.time() - t0))
