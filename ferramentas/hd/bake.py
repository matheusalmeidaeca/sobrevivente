# ferramentas/hd/bake.py
"""F9: BAKE da ALTA para a BAIXA, na UV da BAIXA (como Substance/xNormal, mas analitico).
Para cada texel da regiao de um Tubo: ponto da ALTA (kit.FINO=True) e da BAIXA (FINO=False) pela MESMA parametrizacao
(theta, y) = projecao ao longo da normal. Saidas (atlas 1024x1024):
  normal  : normal map em espaco tangente (convencao OpenGL, Y+ = cima na UV), derivado da ALTA, na base TBN da BAIXA;
  ao      : oclusao ambiente / cavidade (1 = aberto, 0 = fechado);
  curv    : curvatura (0.5 = plano; >0.5 = quina convexa; <0.5 = vinco)."""
import math

import numpy as np

import kit
from kit import norm
from personagem import R
from texturas_garoto import box

REGIOES = ["cabeca", "tronco", "casaco", "braco_esq", "braco_dir", "perna_esq", "perna_dir"]


def _P(t, th, y, fino):
    kit.FINO = fino
    P, _ = t.ponto(th, y)
    return P


def bake_tubo(t, reg, N, AO, CV):
    x0, y0, w, h = reg
    u = (np.arange(w) + 0.5) / w
    v = 1.0 - (np.arange(h) + 0.5) / h
    U, V = np.meshgrid(u, v)
    TH = U * 2 * math.pi
    Y = t.y_de(TH, V)
    dth = (2 * math.pi / w) * 0.5
    dy = ((t.y1 - t.y0) / h) * 0.5
    sai = {}
    for fino in (False, True):
        ptc = _P(t, TH, Y, fino)
        a = _P(t, TH + dth, Y, fino) - _P(t, TH - dth, Y, fino)
        b = _P(t, TH, Y + dy, fino) - _P(t, TH, Y - dy, fino)
        sai[fino] = (ptc, a, b)
    pL, aL, bL = sai[False]
    pH, aH, bH = sai[True]
    nH = norm(np.cross(aH, bH))
    T_ = norm(aL)
    B_ = norm(bL - T_ * (bL * T_).sum(-1, keepdims=True))
    N_ = norm(np.cross(T_, B_))
    ts = np.stack([(nH * T_).sum(-1), (nH * B_).sum(-1), (nH * N_).sum(-1)], -1)
    ts = norm(ts)
    N[y0:y0 + h, x0:x0 + w] = 0.5 + 0.5 * ts
    # AO e curvatura: altura radial da ALTA sobre a BAIXA, filtrada
    dh = ((pH - pL) * N_).sum(-1)
    cav = dh - box(dh, 4)
    AO[y0:y0 + h, x0:x0 + w] = np.clip(1.0 + 14.0 * cav, 0.45, 1.0)
    CV[y0:y0 + h, x0:x0 + w] = np.clip(0.5 + 30.0 * (dh - box(dh, 2)), 0.0, 1.0)


def mapas(p):
    """Retorna (normal, ao, curv) como arrays float [0,1] do tamanho do atlas."""
    A = kit.ATLAS
    N = np.zeros((A, A, 3), np.float32)
    N[:] = (0.5, 0.5, 1.0)
    AO = np.ones((A, A), np.float32)
    CV = np.full((A, A), 0.5, np.float32)
    try:
        for nome in REGIOES:
            if nome in p.tubos:
                bake_tubo(p.tubos[nome], R[nome], N, AO, CV)
    finally:
        kit.FINO = False
    return N, AO, CV
