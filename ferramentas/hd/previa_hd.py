# ferramentas/hd/previa_hd.py
"""Previa HD sem GPU (numpy + PIL): rasteriza o modelo skinado com a textura, luz difusa por normal suave.
Uso: desenhar(tris, textura_png, estilo, animacao, quadro, ang, janela=(x0,y0,x1,y1) em metros, px_por_m)."""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "blender"))
import animacao as an  # noqa: E402
import exportar as ex  # noqa: E402


def preparar(tris):
    vertices, indices = ex.montar_vertices(tris)
    pontos = [(v["p"][0], v["p"][1], v["p"][2], v["pesos"]) for v in vertices][::6]
    return vertices, np.array(indices).reshape(-1, 3), pontos


def posar(vertices, pontos, estilo, animacao, quadro):
    pose = an.pose_final(estilo, animacao, float(quadro), pontos)
    w = an.matrizes_mundo(pose)
    W = {k: np.array(v) for k, v in w.items()}
    P = np.array([v["p"] for v in vertices])
    N = np.array([v["n"] for v in vertices])
    outP = np.zeros_like(P)
    outN = np.zeros_like(N)
    P4 = np.hstack([P, np.ones((len(P), 1))])
    for i, v in enumerate(vertices):
        for osso, peso in v["pesos"]:
            outP[i] += peso * (W[osso] @ P4[i])[:3]
            outN[i] += peso * (W[osso][:3, :3] @ N[i])
    outN /= np.maximum(np.linalg.norm(outN, axis=1, keepdims=True), 1e-9)
    return outP, outN, pose, W


def atlas_duplo(vertices, textura, textura_cabeca, destino):
    """F13: junta a textura do corpo e a da cabeca lado a lado (so para a previa) e devolve copias dos vertices
    com o u remapeado: corpo na metade esquerda, grupo "cabeca" na direita."""
    a = Image.open(textura).convert("RGB")
    b = Image.open(textura_cabeca).convert("RGB").resize(a.size)
    j = Image.new("RGB", (a.width * 2, a.height))
    j.paste(a, (0, 0))
    j.paste(b, (a.width, 0))
    j.save(destino)
    novos = []
    for v in vertices:
        u, w = v["uv"]
        nv = dict(v)
        nv["uv"] = (u * 0.5 + (0.5 if v["grupo"] == "cabeca" else 0.0), w)
        novos.append(nv)
    return novos


def desenhar(vertices, idx, P, N, textura, ang=125.0, janela=(-0.7, -0.05, 0.7, 1.95), pxm=330, fundo=(0.78, 0.82, 0.86),
             extra=None):
    x0, y0, x1, y1 = janela
    Wd, Hd = int((x1 - x0) * pxm), int((y1 - y0) * pxm)
    a = np.radians(ang)
    c, s = np.cos(a), np.sin(a)
    X = P[:, 0] * c + P[:, 2] * s
    Z = -P[:, 0] * s + P[:, 2] * c
    px = (X - x0) * pxm
    py = Hd - (P[:, 1] - y0) * pxm
    Nx = N[:, 0] * c + N[:, 2] * s
    Nz = -N[:, 0] * s + N[:, 2] * c
    Nv = np.stack([Nx, N[:, 1], Nz], 1)
    img = np.zeros((Hd, Wd, 3), np.float32)
    img[:] = np.array(fundo, np.float32)
    yy = np.linspace(0, 1, Hd)[:, None, None]
    img *= (1.0 - 0.18 * yy)
    prof = np.full((Hd, Wd), -1e9, np.float32)
    tex = np.asarray(Image.open(textura).convert("RGB")).astype(np.float32) / 255.0
    th, tw = tex.shape[:2]
    uv = np.array([v["uv"] for v in vertices])
    L1 = np.array([-0.45, 0.65, 0.60])   # chave (camera olha para -Z na vista; luz do lado da camera)
    L1 /= np.linalg.norm(L1)
    L2 = np.array([0.6, 0.15, 0.4])
    L2 /= np.linalg.norm(L2)
    for (i0, i1, i2) in idx:
        x0_, x1_, x2_ = px[i0], px[i1], px[i2]
        y0_, y1_, y2_ = py[i0], py[i1], py[i2]
        den = (y1_ - y2_) * (x0_ - x2_) + (x2_ - x1_) * (y0_ - y2_)
        if abs(den) < 1e-9:
            continue
        mnx, mxx = int(max(0, np.floor(min(x0_, x1_, x2_)))), int(min(Wd - 1, np.ceil(max(x0_, x1_, x2_))))
        mny, mxy = int(max(0, np.floor(min(y0_, y1_, y2_)))), int(min(Hd - 1, np.ceil(max(y0_, y1_, y2_))))
        if mnx > mxx or mny > mxy:
            continue
        gx, gy = np.meshgrid(np.arange(mnx, mxx + 1) + 0.5, np.arange(mny, mxy + 1) + 0.5)
        l0 = ((y1_ - y2_) * (gx - x2_) + (x2_ - x1_) * (gy - y2_)) / den
        l1 = ((y2_ - y0_) * (gx - x2_) + (x0_ - x2_) * (gy - y2_)) / den
        l2 = 1.0 - l0 - l1
        dentro = (l0 >= -0.0005) & (l1 >= -0.0005) & (l2 >= -0.0005)
        if not dentro.any():
            continue
        z = l0 * Z[i0] + l1 * Z[i1] + l2 * Z[i2]
        sub = prof[mny:mxy + 1, mnx:mxx + 1]
        ok = dentro & (z > sub)
        if not ok.any():
            continue
        u = l0 * uv[i0, 0] + l1 * uv[i1, 0] + l2 * uv[i2, 0]
        v = 1.0 - (l0 * uv[i0, 1] + l1 * uv[i1, 1] + l2 * uv[i2, 1])
        ti = np.clip((u * tw).astype(int), 0, tw - 1)
        tj = np.clip((v * th).astype(int), 0, th - 1)
        cor = tex[tj, ti]
        nn = (l0[..., None] * Nv[i0] + l1[..., None] * Nv[i1] + l2[..., None] * Nv[i2])
        nn = nn / np.maximum(np.linalg.norm(nn, axis=-1, keepdims=True), 1e-9)
        dif = 0.34 + 0.62 * np.clip(nn @ L1, 0, 1) + 0.16 * np.clip(nn @ L2, 0, 1)
        # a camera olha de +Z (z do espaco de vista) para o personagem: normais com nz<0 sao de costas
        sub[ok] = z[ok]
        prof[mny:mxy + 1, mnx:mxx + 1] = sub
        img[mny:mxy + 1, mnx:mxx + 1][ok] = (cor * dif[..., None])[ok]
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))
