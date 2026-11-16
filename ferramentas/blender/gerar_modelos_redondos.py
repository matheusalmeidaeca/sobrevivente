# ferramentas/blender/gerar_modelos_redondos.py
"""F7: gera jogador.g3dj e zumbi.g3dj com a malha arredondada (redondear.py) e todas as animacoes (inclui as armadas).
Uso: python3 ferramentas/blender/gerar_modelos_redondos.py <pasta_com_boneco.obj_e_zumbi.obj> <pasta_saida>
(a pasta dos OBJ e a saida da etapa F0; a saida e normalmente android/assets/modelos)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import exportar as ex  # noqa: E402
import redondear as rd  # noqa: E402

CONFIG = [("jogador", "boneco", "textura_jogador.png", 2), ("zumbi", "zumbi", "textura_zumbi.png", 1)]

if __name__ == "__main__":
    pasta_obj, saida = sys.argv[1], sys.argv[2]
    for estilo, obj, textura, niveis in CONFIG:
        tris, _ = ex.ler_obj(os.path.join(pasta_obj, obj + ".obj"))
        redondo = rd.redondear(tris, niveis=niveis)
        modelo = ex.montar_g3dj(estilo, redondo, textura, estilo)
        destino = os.path.join(saida, estilo + ".g3dj")
        ex.escrever_g3dj(destino, modelo)
        print(estilo, len(redondo), "triangulos,", len(modelo["animations"]), "animacoes ->", destino)
