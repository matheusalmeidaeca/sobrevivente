# ferramentas/hd/gerar_modelos_hd.py
"""F8: gera jogador.g3dj / zumbi.g3dj em ALTA DEFINICAO + texturas 1024x1024 (substitui gerar_modelos_redondos.py).
Uso: python3 ferramentas/hd/gerar_modelos_hd.py <pasta_saida>   (normalmente android/assets/modelos)
Leva ~5 min (a pele e calculada em Python puro para as ~900 quadros de animacao)."""
import os
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)
sys.path.insert(0, os.path.join(AQUI, "..", "blender"))
import exportar as ex  # noqa: E402
import personagem  # noqa: E402
import texturas  # noqa: E402

if __name__ == "__main__":
    saida = sys.argv[1]
    os.makedirs(saida, exist_ok=True)
    for estilo in ("jogador", "zumbi"):
        p = personagem.Personagem(estilo).tudo()
        atlas = texturas.pintar(p)
        png = os.path.join(saida, "textura_%s.png" % estilo)
        texturas.salvar(atlas, png)
        tris = p.m.triangulos()
        modelo = ex.montar_g3dj(estilo, tris, "textura_%s.png" % estilo, estilo)
        nv = len(modelo["meshes"][0]["vertices"]) // 12
        assert nv < 65000, "vertices demais para indices de 16 bits: %d" % nv
        destino = os.path.join(saida, estilo + ".g3dj")
        tam = ex.escrever_g3dj(destino, modelo)
        print(estilo, len(tris), "triangulos,", nv, "vertices,", len(modelo["animations"]), "animacoes,", tam // 1024, "KB ->", destino)
