# ferramentas/blender/previa_python.py
"""Previa SEM Blender: rasteriza os modelos em Python (numpy + PIL) e monta a folha com 1 quadro por animacao.
Serve para conferir as poses sem depender do Blender. Uso:
  python3 previa_python.py <pasta_com_OBJ_e_texturas> <pasta_saida>
"""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

import animacao as an
import exportar as ex

LARGURA, ALTURA = 300, 400
ESCALA = 150.0  # pixels por metro
ANGULO_VISTA = 125.0  # graus em torno de Y (3/4 pela frente-esquerda)


def _projetar(p, ang):
    a = np.radians(ang)
    c, s = np.cos(a), np.sin(a)
    x = p[:, 0] * c + p[:, 2] * s
    z = -p[:, 0] * s + p[:, 2] * c
    return x, p[:, 1], z


def desenhar(tris, textura, estilo, animacao, quadro, ang=None, arma=None):
    ang = ANGULO_VISTA if ang is None else ang
    vertices, indices = ex.montar_vertices(tris)
    pontos = [(v["p"][0], v["p"][1], v["p"][2], v["pesos"]) for v in vertices]
    pose = an.pose_final(estilo, animacao, float(quadro), pontos)
    w = an.matrizes_mundo(pose)
    pos = np.array([an.deformar(w, v["pesos"], v["p"]) for v in vertices])
    nor = np.array([an.aplicar_normal(w, v["pesos"], v["n"]) if hasattr(an, "aplicar_normal") else v["n"]
                    for v in vertices])
    uv = np.array([v["uv"] for v in vertices])
    idx = np.array(indices).reshape(-1, 3)
    cores_fixas = {}
    if arma:  # arma presa ao osso mao_dir (mesma conta do Jogador.kt): W_mao * T(pivo + palma) * Rx(-90)
        import armas as ar
        h = np.eye(4)
        h[:3, 3] = np.array(an.PIVO["mao_dir"]) + np.array([0.0, -0.08, 0.0])
        h[:3, :3] = np.array(an.rotacao_euler(-90.0, 0.0, 0.0))[:3, :3]
        m = np.array(w["mao_dir"]) @ h
        extra_pos, extra_idx = [], []
        for tri in ar.triangulos(arma):
            base = len(pos) + len(extra_pos)
            for p_, n_, uv_ in tri:
                extra_pos.append((m @ np.array([p_[0], p_[1], p_[2], 1.0]))[:3])
            extra_idx.append([base, base + 1, base + 2])
            cores_fixas[len(idx) + len(extra_idx) - 1] = np.array(ar.PALETA[int(tri[0][2][0] * len(ar.PALETA))][1], np.float32)
        pos = np.vstack([pos, np.array(extra_pos)])
        uv = np.vstack([uv, np.zeros((len(extra_pos), 2))])
        idx = np.vstack([idx, np.array(extra_idx)])
    xs, ys, zs = _projetar(pos, ang)
    px = LARGURA / 2 + (xs - (xs.max() + xs.min()) / 2) * ESCALA
    py = ALTURA - 24 - ys * ESCALA
    img = np.zeros((ALTURA, LARGURA, 3), np.float32)
    img[:] = (0.80, 0.84, 0.88)
    prof = np.full((ALTURA, LARGURA), -1e9, np.float32)
    # sombra simples no chao
    desenho = Image.fromarray((img * 255).astype(np.uint8))
    d = ImageDraw.Draw(desenho)
    d.line([(10, ALTURA - 24), (LARGURA - 10, ALTURA - 24)], fill=(120, 130, 120), width=1)
    img = np.asarray(desenho).astype(np.float32) / 255.0
    tex = np.asarray(Image.open(textura).convert("RGB")).astype(np.float32) / 255.0
    th, tw = tex.shape[:2]
    luz = np.array([0.4, 0.8, -0.5])
    luz /= np.linalg.norm(luz)
    for ti_, (a, b, c) in enumerate(idx):
        x0, x1, x2 = px[a], px[b], px[c]
        y0, y1, y2 = py[a], py[b], py[c]
        den = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
        if abs(den) < 1e-9:
            continue
        # culling: descarta faces voltadas para tras (area com sinal)
        if den > 0:
            continue
        minx, maxx = int(max(0, np.floor(min(x0, x1, x2)))), int(min(LARGURA - 1, np.ceil(max(x0, x1, x2))))
        miny, maxy = int(max(0, np.floor(min(y0, y1, y2)))), int(min(ALTURA - 1, np.ceil(max(y0, y1, y2))))
        if minx > maxx or miny > maxy:
            continue
        gx, gy = np.meshgrid(np.arange(minx, maxx + 1) + 0.5, np.arange(miny, maxy + 1) + 0.5)
        l0 = ((y1 - y2) * (gx - x2) + (x2 - x1) * (gy - y2)) / den
        l1 = ((y2 - y0) * (gx - x2) + (x0 - x2) * (gy - y2)) / den
        l2 = 1.0 - l0 - l1
        dentro = (l0 >= -0.001) & (l1 >= -0.001) & (l2 >= -0.001)
        if not dentro.any():
            continue
        z = l0 * zs[a] + l1 * zs[b] + l2 * zs[c]
        sub = prof[miny:maxy + 1, minx:maxx + 1]
        ok = dentro & (z > sub)
        if not ok.any():
            continue
        u = l0 * uv[a, 0] + l1 * uv[b, 0] + l2 * uv[c, 0]
        v = 1.0 - (l0 * uv[a, 1] + l1 * uv[b, 1] + l2 * uv[c, 1])
        ti = np.clip((u * tw).astype(int), 0, tw - 1)
        tj = np.clip((v * th).astype(int), 0, th - 1)
        cor = tex[tj, ti] if ti_ not in cores_fixas else np.broadcast_to(cores_fixas[ti_], (ti.shape[0], ti.shape[1], 3))
        n = np.cross(pos[b] - pos[a], pos[c] - pos[a])
        nn = np.linalg.norm(n)
        n = n / nn if nn > 0 else n
        difuso = 0.45 + 0.55 * max(0.0, float(np.dot(n, luz)))
        sub[ok] = z[ok]
        prof[miny:maxy + 1, minx:maxx + 1] = sub
        img[miny:maxy + 1, minx:maxx + 1][ok] = cor[ok] * difuso
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))


def folha(pasta_obj, pasta_saida):
    os.makedirs(pasta_saida, exist_ok=True)
    linhas = []
    for estilo, arq in (("jogador", "boneco"), ("zumbi", "zumbi")):
        tris, tex = ex.ler_obj(os.path.join(pasta_obj, arq + ".obj"))
        textura = os.path.join(pasta_obj, list(tex.values())[0])
        imgs = []
        for nome, n, _ in an.listar_animacoes(estilo):
            q = min(an.QUADRO_PREVIA[nome], n)
            im = desenhar(tris, textura, estilo, nome, q)
            ImageDraw.Draw(im).text((8, 6), "%s / %s (q%d)" % (estilo, nome, q), fill=(20, 20, 20))
            imgs.append(im)
        linhas.append(imgs)
    f = Image.new("RGB", (LARGURA * 5, ALTURA * 2))
    for r, imgs in enumerate(linhas):
        for c, im in enumerate(imgs):
            f.paste(im, (c * LARGURA, r * ALTURA))
    destino = os.path.join(pasta_saida, "previa_python.png")
    f.save(destino)
    return destino


if __name__ == "__main__":
    print(folha(sys.argv[1], sys.argv[2]))
