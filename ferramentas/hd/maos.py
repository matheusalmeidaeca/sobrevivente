# ferramentas/hd/maos.py
"""F15: textura das maos (1024x1024, 3o material "material_mao"). Palma e dorso (Tubo da palma), dedos (tubo_caminho,
u = em volta do dedo, v = base -> ponta) e unha. A pele combina com a do rosto (rosto.COR_PELE)."""
import math

import numpy as np

import kit
import rosto
from kit import fbm, ruido, suave
from personagem import RM, XB
from texturas import grade


def _c(t):
    return np.array(t, np.float32) / 255.0


def _g(t):
    return np.exp(-t * t)


def _mix(cor, alvo, m):
    return cor * (1.0 - m[..., None]) + np.asarray(alvo, np.float32) * m[..., None]


def _pele_base(h, w, sem, zumbi):
    pele = _c(rosto.COR_PELE) if not zumbi else _c((132, 142, 118))
    n1 = fbm(h, w, 40, sem)
    n2 = fbm(h, w, 9, sem + 1, 3)
    poro = ruido(h, w, 1, sem + 2)
    cor = pele[None, None, :] * (0.95 + 0.10 * n1[..., None]) * (0.98 + 0.04 * n2[..., None])
    cor = cor * (1.0 - 0.05 * (poro[..., None] - 0.5))
    rosa = np.array([0.045, -0.020, -0.018], np.float32)
    return cor, rosa, n1, n2


def _palma(t, reg, s, A, zumbi):
    x0, y0, w, h = reg
    TH, Y, P, d, U, V = grade(t, reg)
    sin_ = -s
    lado = (P[..., 0] - XB * s) * sin_            # >0 = lado da palma
    pal = suave(lado / 0.006)
    cor, rosa, n1, n2 = _pele_base(h, w, 300 + s, zumbi)
    z, y = P[..., 2], P[..., 1]
    # palma: mais rosada, linhas de flexao
    cor = cor + (pal * (0.55 + 0.45 * n2))[..., None] * rosa * 1.3
    ond = fbm(h, w, 90, 310, 2)
    # linhas principais (z para frente = polegar em -z): vida, cabeca, coracao
    vida = _g(((np.hypot((z + 0.030) / 0.034, (y - 0.760) / 0.040)) - 1.0) / 0.075) * suave((0.790 - y) / 0.01)
    cab = _g((y - (0.742 + 0.12 * (z + 0.012) + 0.008 * (ond - 0.5))) / 0.0030) * suave((z + 0.034) / 0.01) * suave((0.032 - z) / 0.01)
    cor_ = _g((y - (0.724 + 0.05 * (z - 0.020) + 0.008 * (ond - 0.5))) / 0.0028) * suave((z + 0.025) / 0.01) * suave((0.034 - z) / 0.01)
    linhas = np.clip(vida * 0.8 + cab * 0.9 + cor_ * 0.9, 0, 1) * pal
    # micro-vincos finos cruzando a palma
    vinc = np.zeros((h, w), np.float32)
    cor = cor * (1.0 - 0.30 * linhas[..., None] * np.array([0.60, 1.0, 1.0], np.float32))
    cor = cor * (1.0 - 0.06 * (vinc * pal)[..., None])
    # coxins (eminencias) levemente mais claros no centro
    cor = cor * (1.0 + 0.04 * pal[..., None] * _g((y - 0.755) / 0.03)[..., None])
    # dorso: veias azuladas, tendoes (listras claras), pelinhos, vincos dos nos dos dedos
    dor = 1.0 - pal
    rid = 1.0 - np.abs(2.0 * fbm(h, w, 14, 320, 3) - 1.0)
    veia = suave((rid - 0.93) / 0.04) * dor * suave((0.80 - y) / 0.05) * suave((y - 0.72) / 0.02)
    cor = _mix(cor, _c((140, 130, 150)), veia * 0.35)
    for zf in (-0.0285, -0.0095, 0.0095, 0.0275):
        no = _g((np.hypot(z - zf, (y - 0.720) * 0.9) - 0.0) / 0.0085) * dor
        cor = cor * (1.0 - 0.07 * no[..., None])
        cor = cor + (no * 0.04)[..., None] * rosa * 3.0                    # nos mais corados
        for k in (0.0, 1.0, -1.0):
            lin = _g((y - (0.717 + 0.0025 * k)) / 0.0007) * _g((z - zf) / 0.0075) * dor
            cor = cor * (1.0 - 0.14 * lin[..., None])
        tend = _g((z - zf) / 0.0030) * dor * suave((y - 0.735) / 0.01) * suave((0.80 - y) / 0.02)
        cor = cor * (1.0 + 0.03 * tend[..., None])
    pel = (ruido(h, w, 1, 321) > 0.93).astype(np.float32) * dor * suave((y - 0.70) / 0.02) * 0.10
    cor = cor * (1.0 - pel[..., None])
    # pulso: leve escurecimento e prega
    cor = cor * (1.0 - 0.10 * suave((y - 0.815) / 0.02)[..., None])
    if zumbi:
        suj = suave((fbm(h, w, 30, 330, 4) - 0.50) / 0.20)
        cor = cor * (1.0 - 0.38 * suj[..., None] * np.array([0.7, 0.9, 1.1], np.float32))
        sg = suave((fbm(h, w, 14, 331, 3) - 0.60) / 0.06)
        cor = _mix(cor, _c((70, 12, 12)), sg * 0.70)
    else:
        suj = suave((fbm(h, w, 60, 340, 4) - 0.56) / 0.25)
        cor = cor * (1.0 - 0.06 * suj[..., None] * np.array([0.8, 1.0, 1.2], np.float32))
    # sombra assada: cavidades escurecem puxando para o vermelho
    cor = cor * (1.0 - 0.0 * d[..., None])
    A[y0:y0 + h, x0:x0 + w] = np.clip(cor, 0, 1)


def _dedos(reg, s, A, zumbi):
    x0, y0, w, h = reg
    u = (np.arange(w) + 0.5) / w
    v = 1.0 - (np.arange(h) + 0.5) / h              # v=1 em cima (base do dedo) ... 0 na ponta
    U, V = np.meshgrid(u, v)
    tt = 1.0 - V                                    # 0 = base, 1 = ponta
    ang = (U - 0.05) / 0.9 * 2.0 * math.pi
    pal = suave(s * np.cos(ang) / 0.35)             # 1 = lado da palma
    cor, rosa, n1, n2 = _pele_base(h, w, 400 + s, zumbi)
    cor = cor + (pal * (0.4 + 0.6 * n2))[..., None] * rosa * 1.1
    cor = cor + (suave((tt - 0.80) / 0.18) * 0.6)[..., None] * rosa                # ponta mais corada
    # articulacoes (1/3 e 2/3 do comprimento): vincos transversais; no dorso varios, na palma um fundo
    for pos in (1.0 / 3.0, 2.0 / 3.0):
        for k, intens in ((-1.0, 0.10), (0.0, 0.16), (1.0, 0.10)):
            d_ = tt - (pos + 0.012 * k)
            dor = 1.0 - pal
            cor = cor * (1.0 - intens * (_g(d_ / 0.0065) * (0.5 + 0.5 * dor))[..., None])
        nodo = _g((tt - pos) / 0.05) * (1.0 - pal)
        cor = cor * (1.0 - 0.05 * nodo[..., None]) + (nodo * 0.025)[..., None] * rosa * 3.0
        cor = cor * (1.0 - 0.10 * (_g((tt - pos) / 0.012) * pal)[..., None])
    # rugas finas em torno da articulacao
    rug = 0.5 + 0.5 * np.sin(tt / 0.0070 + 5.0 * fbm(h, w, 6, 410, 3))
    cor = cor * (1.0 - 0.05 * (rug * _g((tt - 1.0 / 3.0) / 0.10))[..., None])
    cor = cor * (1.0 - 0.05 * (rug * _g((tt - 2.0 / 3.0) / 0.10))[..., None])
    # polpa da ponta: digital (aneis concentricos suaves) no lado da palma
    dig = 0.5 + 0.5 * np.sin((1.0 - tt) / 0.0085 + 2.5 * np.cos(ang) * s + 2.0 * fbm(h, w, 5, 411, 3))
    cor = cor * (1.0 - 0.07 * (dig * pal * suave((tt - 0.80) / 0.10))[..., None])
    # pelinhos no dorso da falange proximal
    pel = (ruido(h, w, 1, 412) > 0.92).astype(np.float32) * (1.0 - pal) * suave((0.55 - tt) / 0.1)
    cor = cor * (1.0 - 0.10 * pel[..., None])
    # pele em volta da unha (dobra) mais clara perto da ponta no dorso
    if zumbi:
        suj = suave((fbm(h, w, 24, 420, 4) - 0.48) / 0.20)
        cor = cor * (1.0 - 0.40 * suj[..., None] * np.array([0.7, 0.9, 1.1], np.float32))
        cor = _mix(cor, _c((70, 12, 12)), suave((tt - 0.85) / 0.06) * suave((fbm(h, w, 8, 421) - 0.5) / 0.1) * 0.7)
    else:
        suj = suave((fbm(h, w, 24, 422, 4) - 0.58) / 0.20) * suave((tt - 0.78) / 0.12)
        cor = cor * (1.0 - 0.12 * suj[..., None] * np.array([0.8, 1.0, 1.3], np.float32))
    A[y0:y0 + h, x0:x0 + w] = np.clip(cor, 0, 1)


def _unha(A, zumbi):
    x0, y0, w, h = RM["unha"]
    u = (np.arange(w) + 0.5) / w
    v = 1.0 - (np.arange(h) + 0.5) / h
    U, V = np.meshgrid(u, v)
    tt = 1.0 - V                                    # 0 = raiz, 1 = borda livre
    base = _c((232, 176, 168)) if not zumbi else _c((176, 160, 130))
    cor = np.tile(base, (h, w, 1)) * (0.96 + 0.08 * fbm(h, w, 6, 430)[..., None])
    cor = cor * (1.0 - 0.05 * (0.5 + 0.5 * np.sin(U * 2 * math.pi * 9 + 3 * fbm(h, w, 5, 431, 3)))[..., None])   # estrias
    lun = suave((0.30 - tt) / 0.08) * suave((tt - 0.04) / 0.04)
    cor = _mix(cor, _c((244, 226, 220)), lun * 0.80)                                # lunula
    cor = _mix(cor, _c((246, 244, 238)) if not zumbi else _c((120, 100, 70)), suave((tt - 0.82) / 0.07) * 0.95)  # borda livre
    cor = _mix(cor, _c((150, 100, 92)), _g((tt - 0.02) / 0.03) * 0.6)               # cuticula
    cor = cor * (1.0 + 0.08 * _g((U - 0.5) / 0.18)[..., None] * _g((tt - 0.5) / 0.30)[..., None])   # brilho
    A[y0:y0 + h, x0:x0 + w] = np.clip(cor, 0, 1)


def pintar(p):
    """Atlas 1024x1024 (float 0..1) das maos do personagem `p` (precisa de p.tubos['mao_esq'/'mao_dir'])."""
    A = np.zeros((kit.ATLAS, kit.ATLAS, 3), np.float32)
    A[:] = _c(rosto.COR_PELE)
    for s in (-1, 1):
        nome = "mao_esq" if s < 0 else "mao_dir"
        _palma(p.tubos[nome], RM["palma_esq" if s < 0 else "palma_dir"], s, A, p.z)
        _dedos(RM["dedos_esq" if s < 0 else "dedos_dir"], s, A, p.z)
    _unha(A, p.z)
    return A
