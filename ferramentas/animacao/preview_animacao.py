#!/usr/bin/env python3
"""Gera uma folha de imagens (PNG) com poses do .g3dj aplicadas na malha (pele calculada com os pesos).
Nao precisa de GPU nem de Blender. Uso: python3 preview_animacao.py [--g3dj saida/humano.g3dj] [--saida saida/previa.png]
Cada quadro mostra a pose de LADO (esquerda) e de FRENTE (direita)."""
import argparse, json, math, sys
import numpy as np
from PIL import Image, ImageDraw
sys.path.insert(0, ".")
import teste_animacao as T

POSES = [("parado", 0.0), ("andar", 0.00), ("andar", 0.25), ("andar", 0.50), ("correr", 0.10), ("correr", 0.35),
         ("agachar", 0.5), ("pular", 0.40), ("pular", 0.62), ("atacar", 0.30), ("atacar", 0.50), ("dano", 0.25),
         ("morrer", 0.45), ("morrer", 1.00), ("interagir", 0.5), ("interagir", 0.8)]


def pele(g, nomes, pai, trans, BIND, por_osso, k, passo=3):
    quats = {n: por_osso[n][k]["rotation"] for n in nomes}
    fk = T.fk_quadro(nomes, pai, trans, por_osso["quadril"][k]["translation"], quats)
    P = np.array([fk[n][0] for n in nomes])
    R = np.array([fk[n][1] for n in nomes])
    pts, nrm, cor = [], [], []
    cores = [(205, 170, 150), (225, 190, 170), (215, 180, 160)]
    for mi, m in enumerate(g["meshes"]):
        V = np.array(m["vertices"], dtype=float).reshape(-1, 16)[::passo]
        pos, nor = V[:, 0:3], V[:, 3:6]
        o = np.zeros_like(pos)
        on = np.zeros_like(nor)
        for j in range(4):
            ii = V[:, 8 + 2 * j].astype(int)
            w = V[:, 9 + 2 * j][:, None]
            o += w * (np.einsum("nij,nj->ni", R[ii], pos - BIND[ii]) + P[ii])
            on += w * np.einsum("nij,nj->ni", R[ii], nor)
        pts.append(o)
        nrm.append(on)
        cor.append(np.tile(np.array(cores[mi % 3]), (len(o), 1)))
    return np.concatenate(pts), np.concatenate(nrm), np.concatenate(cor)


def desenhar(pts, nrm, cor, eixo, escala, W, H, x0, y0):
    img = np.full((H, W, 3), 245, np.uint8)
    zb = np.full((H, W), -1e9)
    if eixo == "lado":      # olha de +X: horizontal = z (frente -Z fica para a direita), vertical = y
        u = -pts[:, 2]; prof = pts[:, 0]
        luz = np.array([0.5, 0.6, -0.6])
    else:                   # de frente: horizontal = -x, vertical = y
        u = -pts[:, 0]; prof = -pts[:, 2]
        luz = np.array([-0.3, 0.6, -0.8])
    n = nrm / (np.linalg.norm(nrm, axis=1, keepdims=True) + 1e-9)
    sh = np.clip(n @ (luz / np.linalg.norm(luz)), 0, 1) * 0.65 + 0.35
    px = (x0 + u * escala).astype(int)
    py = (y0 - pts[:, 1] * escala).astype(int)
    ordem = np.argsort(prof)
    for i in ordem:
        x, y = px[i], py[i]
        if 0 <= x < W - 1 and 0 <= y < H - 1:
            if prof[i] >= zb[y, x] - 0.02:
                img[y:y + 2, x:x + 2] = (cor[i] * sh[i]).astype(np.uint8)
                zb[y:y + 2, x:x + 2] = prof[i]
    # chao
    cy = int(y0)
    img[cy:cy + 2, :, :] = (90, 90, 90)
    return img


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--g3dj", default="saida/humano.g3dj")
    ap.add_argument("--saida", default="saida/previa.png")
    ap.add_argument("--poses", default="", help="indices das poses separados por virgula (padrao: todas)")
    a = ap.parse_args()
    meta = json.load(open(a.g3dj.rsplit(".", 1)[0] + "_meta.json"))
    g, pai, trans = T.carregar(a.g3dj)
    nomes = meta["ossos"]
    BIND = np.zeros((len(nomes), 3))
    for i, n in enumerate(nomes):
        p, acc = n, np.zeros(3)
        while p is not None:
            acc = acc + trans[p]; p = pai[p]
        BIND[i] = acc
    W, H = 230, 330
    esc = 150.0
    poses = [POSES[int(i)] for i in a.poses.split(",")] if a.poses else POSES
    cols = 4 if len(poses) >= 4 else len(poses)
    linhas = math.ceil(len(poses) / cols)
    folha = Image.new("RGB", (cols * 2 * W, linhas * (H + 18)), (255, 255, 255))
    d = ImageDraw.Draw(folha)
    animacoes = {an["id"]: {b["boneId"]: b["keyframes"] for b in an["bones"]} for an in g["animations"]}
    for i, (nome, frac) in enumerate(poses):
        po = animacoes[nome]
        tempos = [kf["keytime"] / 1000.0 for kf in po["quadril"]]
        dur = tempos[-1]
        alvo = frac * dur if nome in ("pular", "atacar", "morrer", "dano", "interagir", "agachar") else frac * dur
        k = int(np.argmin([abs(t - alvo) for t in tempos]))
        pts, nrm, cor = pele(g, nomes, pai, trans, BIND, po, k)
        i1 = desenhar(pts, nrm, cor, "lado", esc, W, H, W * 0.5 + 0.0, H - 20)
        i2 = desenhar(pts, nrm, cor, "frente", esc, W, H, W * 0.5, H - 20)
        cx, cy = (i % cols) * 2 * W, (i // cols) * (H + 18)
        folha.paste(Image.fromarray(i1), (cx, cy + 18))
        folha.paste(Image.fromarray(i2), (cx + W, cy + 18))
        d.text((cx + 4, cy + 3), "%s  t=%.2fs" % (nome, tempos[k]), fill=(0, 0, 0))
    folha.save(a.saida)
    print("OK ->", a.saida, folha.size)


if __name__ == "__main__":
    main()
