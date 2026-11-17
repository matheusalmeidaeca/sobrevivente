# ferramentas/hd/texturas.py
"""F8: pinta o atlas 1024x1024 do jogador/zumbi (pele com poros, denim com trama, couro, cabelo, olhos, sangue...).
Cada regiao e pintada no espaco (theta, y) do proprio Tubo, usando o MESMO deslocamento da malha para sombrear vincos."""
import math

import numpy as np
from PIL import Image

import kit
from kit import fbm, ruido, suave
from personagem import R, TILE, COR, _c, _dist_feridas


def grade(t, reg):
    x0, y0, w, h = reg
    u = (np.arange(w) + 0.5) / w
    v = 1.0 - (np.arange(h) + 0.5) / h
    U, V = np.meshgrid(u, v)
    TH = U * 2 * math.pi
    Y = t.y_de(TH, V)
    P, _ = t.ponto(TH, Y)
    d = t.sombra(TH, Y)
    return TH, Y, P, d, U, V


def sombrear(cor, d, ganho=26.0):
    f = np.clip(1.0 + ganho * d, 0.55, 1.18)
    return cor * f[..., None]


def denim(h, w, base, sem, fade=0.10, contraste=0.05):
    i, j = np.mgrid[0:h, 0:w]
    tw = 0.5 + 0.5 * np.sin((i + j) * 2 * math.pi / 3.6)
    fio = ruido(h, w, 1, sem)
    macro = fbm(h, w, 18, sem + 3)
    f = 1.0 + contraste * (tw - 0.5) + 0.06 * (fio - 0.5) + fade * (macro - 0.5)
    return base[None, None, :] * f[..., None]


def aplicar_feridas(cor, P, p, base_carne, base_sangue, intensidade=1.0):
    if not p.z:
        return cor
    dist, tipo = _dist_feridas(P)
    h, w = dist.shape
    ruidoso = fbm(h, w, 6, 91)
    dd = dist + (ruidoso - 0.5) * 0.45
    carne = np.where((tipo == 0)[..., None], base_carne[None, None, :], (base_sangue * 1.2)[None, None, :])
    nucleo = suave((0.75 - dd) / 0.15)
    borda = suave((1.30 - dd) / 0.40) * (1 - nucleo)
    escuro = suave((0.35 - dd) / 0.3)
    cor = cor * (1 - 0.55 * borda[..., None] * np.array([0.3, 1.0, 1.0])) + borda[..., None] * 0.20 * np.array([0.45, 0.02, 0.03])
    c2 = carne * (1.0 - 0.45 * escuro[..., None])
    return cor * (1 - nucleo[..., None]) + c2 * nucleo[..., None]


def respingos(cor, h, w, sem, quant=0.10, p=None):
    if p is None or not p.z:
        return cor
    n = fbm(h, w, 10, sem, 4)
    m = suave((n - (0.80 - quant * 0.5)) / 0.04)
    return cor * (1 - 0.8 * m[..., None]) + m[..., None] * 0.8 * np.array([0.38, 0.03, 0.04])


# ============================================================================================ pecas
def pintar_cabeca(p, A):
    t = p.tubos["cabeca"]
    reg = R["cabeca"]
    TH, Y, P, d, U, V = grade(t, reg)
    h, w = U.shape
    x, y, z = P[..., 0], P[..., 1], P[..., 2]
    pele = _c(p.cor["pele"])
    n1 = fbm(h, w, 22, 1)
    poro = ruido(h, w, 1, 2)
    cor = pele[None, None, :] * (0.93 + 0.14 * n1[..., None]) * (1 - 0.06 * (poro[..., None] - 0.5))
    frente = suave((-z - 0.02) / 0.05)
    # bochechas / nariz coradas, olheiras
    for s in (-1, 1):
        bl = np.exp(-(((np.abs(x) - 0.052) / 0.020) ** 2 + ((y - 1.648) / 0.024) ** 2)) * frente
        cor = cor + bl[..., None] * np.array([0.035, -0.02, -0.015]) * (1 if not p.z else 0.2)
    nariz = np.exp(-((x / 0.014) ** 2 + ((y - 1.652) / 0.014) ** 2)) * frente
    cor = cor + nariz[..., None] * np.array([0.04, -0.02, -0.015])
    for s in (-1, 1):
        ol = np.exp(-(((x - 0.033 * s) / 0.020) ** 2 + ((y - 1.688) / 0.014) ** 2)) * frente
        if p.z:
            cor = cor * (1 - 0.55 * ol[..., None]) + ol[..., None] * 0.15 * np.array([0.15, 0.1, 0.25])
        else:
            cor = cor * (1 - 0.12 * ol[..., None]) + ol[..., None] * 0.04 * np.array([0.1, 0.0, 0.2])
    # linha da boca e barba por fazer
    boca = (np.abs(x) < 0.0235) & (np.abs(y - 1.6095) < 0.0011) & (z < -0.05)
    cor = np.where(boca[..., None], _c((120, 54, 54)), cor)
    if p.z:
        cav = np.exp(-(((x / 0.020) ** 8) + (((y - 1.604) / 0.014) ** 6))) * frente
        cor = cor * (1 - cav[..., None]) + cav[..., None] * _c((46, 8, 10))
        sang = suave((1.60 - y) / 0.02) * suave((y - 1.545) / 0.02) * (np.abs(x) < 0.03) * frente
        sg = fbm(h, w, 5, 40) > 0.45
        cor = cor * (1 - 0.8 * (sang * sg)[..., None]) + (sang * sg)[..., None] * 0.8 * _c((110, 12, 14))
    else:
        barba = suave((1.628 - y) / 0.02) * suave((y - 1.556) / 0.012) * frente * suave((0.066 - np.abs(x)) / 0.01)
        barba = barba * (ruido(h, w, 1, 7) > 0.42)
        cor = cor * (1 - 0.18 * barba[..., None]) + barba[..., None] * 0.18 * _c((70, 62, 70))
    # sombra do queixo no pescoco
    pesc = suave((1.572 - y) / 0.020) * suave((y - 1.49) / 0.01)
    cor = cor * (1 - 0.28 * (pesc * suave((-z + 0.0) / 0.04 + 0.5))[..., None])
    cor = sombrear(cor, d, 24.0)
    if p.z:
        veias = fbm(h, w, 5, 21, 4)
        vm = suave((veias - 0.62) / 0.06)
        cor = cor * (1 - 0.35 * vm[..., None]) + 0.35 * vm[..., None] * _c((70, 90, 110))
        cor = aplicar_feridas(cor, P, p, _c((150, 40, 40)), _c((90, 10, 12)))
    # couro cabeludo (cabelo cobre; evita pele aparecer entre as mechas)
    fr = suave((-z - 0.015) / 0.045)
    m_frente = suave((y - 1.777) / 0.012)
    m_atras = np.maximum(suave((y - 1.735) / 0.012), suave((z - 0.030) / 0.020) * suave((y - 1.665) / 0.012))
    m_atras = np.maximum(m_atras, suave((np.abs(x) - 0.070) / 0.006) * suave((y - 1.715) / 0.01) * suave((z + 0.01) / 0.02))
    mh = m_frente * fr + m_atras * (1 - fr)
    if p.z:
        mh = mh * (fbm(h, w, 9, 3) > 0.38)
    cab = _c(p.cor["cabelo"])[None, None, :] * (0.7 + 0.5 * fbm(h, w, 3, 4, 2)[..., None])
    cor = cor * (1 - mh[..., None]) + cab * mh[..., None]
    A[reg[1]:reg[1] + h, reg[0]:reg[0] + w] = cor


def pintar_tronco(p, A):
    t = p.tubos["tronco"]
    reg = R["tronco"]
    TH, Y, P, d, U, V = grade(t, reg)
    h, w = U.shape
    x, y, z = P[..., 0], P[..., 1], P[..., 2]
    cam = _c(p.cor["camisa"])
    jea = _c(p.cor["jeans"])
    dn_c = denim(h, w, cam, 11, 0.16 if not p.z else 0.30)
    dn_j = denim(h, w, jea, 12, 0.14 if not p.z else 0.20)
    front = z < -0.03
    if not p.z:
        # camisa jeans desbotada: ombros e cotovelos mais claros, cintura mais escura
        cl = np.clip((Y - 1.1) / 0.4, 0, 1)
        dn_c = dn_c * (0.92 + 0.12 * cl[..., None])
        # costuras: lateral, pestana, cavas, pala nas costas, pespontos
        linha = _c((178, 150, 110))
        dash = (np.mod(Y / 0.0042, 1.0) < 0.62)
        for sg in (-1.0, 1.0):
            lat = (np.abs(TH - (math.pi / 2 if sg > 0 else 3 * math.pi / 2)) < 0.012) & (Y > 1.04) & (Y < 1.42)
            dn_c = np.where((lat & dash)[..., None], linha, dn_c)
            pes = (np.abs(x - sg * 0.0075) < 0.0007) & front & (Y > 1.045) & (Y < 1.495) & dash
            dn_c = np.where(pes[..., None], linha, dn_c)
        pala = ((np.abs(Y - 1.392) < 0.0007) | (np.abs(Y - 1.398) < 0.0007)) & (z > 0.03)
        dn_c = np.where(pala[..., None], linha, dn_c)
        # pespontos dos bolsos (retangulos), tras e peito
        for s in (-1, 1):
            th0 = math.pi + s * 0.52
            arc = (TH - th0) * 0.105
            inx = np.abs(arc) < 0.022
            for (ya, yb) in ((1.296, 1.358),):
                borda = (((np.abs(np.abs(arc) - 0.020) < 0.0007) & (Y > ya + 0.002) & (Y < yb - 0.002))
                         | ((np.abs(Y - (yb - 0.003)) < 0.0007) & inx) | ((np.abs(Y - (ya + 0.003)) < 0.0007) & inx))
                dn_c = np.where((borda & dash)[..., None], linha * 0.95, dn_c)
        # calca: cos, passadores, bolsos, braguilha
        costura = _c((205, 150, 62))
        fl = (np.abs(x) < 0.0022) & front & (Y > 0.865) & (Y < 0.992) & dash
        dn_j = np.where(fl[..., None], costura, dn_j)
        for sg in (-1.0, 1.0):
            xs = sg * (0.108 - 0.30 * np.clip(0.997 - Y, 0, 0.12))
            bol = (np.abs(x - xs) < 0.0010) & front & (Y > 0.90) & (Y < 0.992) & dash
            dn_j = np.where(bol[..., None], costura, dn_j)
            rebite = (np.hypot(x - sg * 0.109, Y - 0.985) < 0.0026) & front
            dn_j = np.where(rebite[..., None], _c((184, 110, 62)), dn_j)
        cos_ = (np.abs(Y - 0.986) < 0.0009) | (np.abs(Y - 0.972) < 0.0009)
        dn_j = np.where((cos_ & dash)[..., None], costura, dn_j)
        for s in (-1, 1):
            th0 = (s * 0.40) % (2 * math.pi)
            arc = (TH - th0) * 0.16
            inx = np.abs(arc) < 0.030
            borda = (((np.abs(np.abs(arc) - 0.027) < 0.0008) & (Y > 0.866) & (Y < 0.950))
                     | ((np.abs(Y - 0.948) < 0.0008) & inx) | ((np.abs(Y - 0.868) < 0.0008) & inx))
            v_ = (np.abs(Y - (0.944 - np.abs(arc) * 1.4)) < 0.0008) & inx
            dn_j = np.where(((borda | v_) & dash)[..., None], costura, dn_j)
        dn_j = dn_j * (1.0 - 0.14 * suave((0.9 - Y) / 0.05)[..., None] * 0)
        # bigodes de desgaste na virilha
        big = np.sin((Y - 0.88) * 190 + (x * 120)) > 0.92
        dn_j = np.where((big & front & (Y < 0.95) & (Y > 0.84))[..., None], dn_j * 1.22, dn_j)
        base = np.where((Y < 1.012)[..., None], dn_j, dn_c)
        cor = base
    else:
        # camiseta rasgada cinza
        rag = 0.972 + 0.028 * np.sin(TH * 3 + 1.0) + 0.020 * (fbm(h, 1, 1, 5, 1)[:, 0:1] * 0 + np.interp(TH[0], np.linspace(0, 2 * math.pi, 40), ruido(1, 40, 3, 9)[0]))[None, :]
        rag = rag + 0 * Y
        fr_ = (np.abs(Y - rag) < 0.006)
        tx = denim(h, w, cam, 13, 0.30, 0.02) * (0.95 + 0.1 * fbm(h, w, 8, 6)[..., None])
        base = np.where((Y < rag)[..., None], dn_j, tx)
        base = np.where(fr_[..., None], base * 1.18, base)
        cor = respingos(base, h, w, 33, 0.16, p)
        cor = cor * (1 - 0.35 * suave((fbm(h, w, 14, 8) - 0.5) / 0.2)[..., None] * np.array([0.5, 0.6, 0.9])[None, None, :])
    cor = sombrear(cor, d, 26.0)
    if p.z:
        cor = aplicar_feridas(cor, P, p, _c((150, 40, 40)), _c((90, 10, 12)))
    A[reg[1]:reg[1] + h, reg[0]:reg[0] + w] = cor


def pintar_braco(p, A, s):
    nome = "braco_esq" if s < 0 else "braco_dir"
    t = p.tubos[nome]
    reg = R[nome]
    TH, Y, P, d, U, V = grade(t, reg)
    h, w = U.shape
    pele = _c(p.cor["pele"])
    cam = _c(p.cor["camisa"])
    n1 = fbm(h, w, 18, 31)
    sk = pele[None, None, :] * (0.93 + 0.14 * n1[..., None]) * (1 - 0.05 * (ruido(h, w, 1, 32)[..., None] - 0.5))
    # veias e pelos finos no antebraco
    veia = np.exp(-(((np.sin(TH * 2.0 + 1.2 * np.sin(Y * 30) * 0.2)) / 0.10) ** 2)) * suave((1.05 - Y) / 0.2) * suave((Y - 0.82) / 0.05)
    sk = sk * (1 - 0.10 * veia[..., None] * (0 if p.z else 1)) + veia[..., None] * 0.07 * np.array([-0.2, 0.0, 0.2])
    pelos = (ruido(h, w, 1, 33) > 0.80) & (Y < 1.12) & (Y > 0.84)
    sk = np.where(pelos[..., None], sk * 0.80, sk)
    if p.z:
        veias = suave((fbm(h, w, 5, 34, 4) - 0.6) / 0.06)
        sk = sk * (1 - 0.35 * veias[..., None]) + 0.35 * veias[..., None] * _c((70, 90, 110))
    if not p.z:
        linha = _c((178, 150, 110))
        dn = denim(h, w, cam, 35, 0.16)
        dash = (np.mod(Y / 0.0042, 1.0) < 0.62)
        # costura da cava e do ombro; lateral da manga
        dn = np.where(((np.abs(Y - 1.455) < 0.0008) & dash)[..., None], linha, dn)
        dn = np.where(((np.abs(TH - math.pi) < 0.02) | (TH < 0.02) | (TH > 2 * math.pi - 0.02))[..., None] & (Y > 1.17)[..., None] & dash[..., None], linha, dn)
        cor = np.where((Y > 1.125)[..., None], dn, sk)
        # sombra do punho arregacado e do cotovelo
        cor = cor * (1 - 0.18 * suave((1.125 - Y) / 0.02)[..., None] * suave((Y - 1.07) / 0.02)[..., None])
    else:
        lim = 1.30 + 0.025 * np.interp(TH[0], np.linspace(0, 2 * math.pi, 30), ruido(1, 30, 2, 41)[0])[None, :] - 0.02
        tx = denim(h, w, cam, 36, 0.30, 0.02)
        cor = np.where((Y > lim)[..., None], tx, sk)
        cor = np.where((np.abs(Y - lim) < 0.005)[..., None], cor * 1.15, cor)
        cor = respingos(cor, h, w, 37, 0.12, p)
    cor = sombrear(cor, d, 28.0)
    if p.z:
        cor = aplicar_feridas(cor, P, p, _c((150, 40, 40)), _c((90, 10, 12)))
    A[reg[1]:reg[1] + h, reg[0]:reg[0] + w] = cor


def pintar_mao(p, A, s):
    reg = R["mao_esq" if s < 0 else "mao_dir"]
    # a mao e um Tubo proprio criado em personagem.mao(): reconstroi a grade de forma simples
    x0, y0, w, h = reg
    pele = _c(p.cor["pele"])
    n1 = fbm(h, w, 12, 51)
    cor = pele[None, None, :] * (0.92 + 0.16 * n1[..., None]) * (1 - 0.05 * (ruido(h, w, 1, 52)[..., None] - 0.5))
    v = 1.0 - (np.arange(h) + 0.5) / h
    # nos dos dedos e dobras da palma (linhas escuras sutis)
    linhas = np.exp(-(((v - 0.30) / 0.03) ** 2))[:, None] * 0.12
    cor = cor * (1 - linhas[..., None])
    if p.z:
        cor = cor * np.array([0.95, 1.0, 0.95])
    A[y0:y0 + h, x0:x0 + w] = cor


def pintar_perna(p, A, s):
    nome = "perna_esq" if s < 0 else "perna_dir"
    t = p.tubos[nome]
    reg = R[nome]
    TH, Y, P, d, U, V = grade(t, reg)
    h, w = U.shape
    x, y, z = P[..., 0], P[..., 1], P[..., 2]
    jea = _c(p.cor["jeans"])
    cor = denim(h, w, jea, 61 + s, 0.16 if not p.z else 0.28)
    dash = (np.mod(Y / 0.0042, 1.0) < 0.62)
    costura = _c((205, 150, 62))
    # costuras externa e interna, bainha com pesponto duplo
    for th0 in (math.pi / 2, 3 * math.pi / 2):
        m = (np.abs(TH - th0) < 0.015) & dash
        cor = np.where(m[..., None], costura * 0.95, cor)
        m2 = (np.abs(TH - th0) < 0.045) & (np.abs(TH - th0) > 0.030) & dash
        cor = np.where((m2 & (not p.z))[..., None], costura * 0.7, cor)
    bar = ((np.abs(Y - 0.138) < 0.0008) | (np.abs(Y - 0.130) < 0.0008)) & dash
    cor = np.where((bar & (not p.z))[..., None], costura, cor)
    cor = cor * (1 + 0.07 * suave((0.16 - Y) / 0.05)[..., None])
    # desbotado: joelho e coxa mais claros; atras do joelho "favos"
    cl = np.exp(-(((Y - 0.50) / 0.08) ** 2)) * (np.cos(TH) < 0.0)
    cor = cor * (1 + 0.16 * cl[..., None])
    th_ = np.exp(-(((Y - 0.76) / 0.10) ** 2)) * (np.cos(TH) < -0.3)
    cor = cor * (1 + 0.10 * th_[..., None])
    favo = (np.abs(np.sin((Y - 0.55) * 140) + np.sin(TH * 12)) < 0.12) & (np.cos(TH) > 0.5) & (Y > 0.47) & (Y < 0.62)
    cor = np.where(favo[..., None], cor * 1.2, cor)
    # sujeira na barra
    suj = suave((0.22 - Y) / 0.10) * (0.35 + 0.65 * fbm(h, w, 6, 63))
    cor = cor * (1 - 0.35 * suj[..., None]) + 0.35 * suj[..., None] * _c((84, 72, 60))
    if p.z:
        cor = respingos(cor, h, w, 64, 0.12, p)
        if s < 0:  # perna esquerda rasgada acima do tornozelo, mostra a pele
            lim = 0.34 + 0.05 * np.interp(TH[0], np.linspace(0, 2 * math.pi, 24), ruido(1, 24, 2, 65)[0])[None, :]
            pele = _c(p.cor["pele"])[None, None, :] * (0.9 + 0.2 * fbm(h, w, 10, 66)[..., None])
            cor = np.where((Y < lim)[..., None], pele, cor)
            cor = np.where((np.abs(Y - lim) < 0.005)[..., None], cor * 0.8 + 0.2 * jea * 1.3, cor)
    cor = sombrear(cor, d, 30.0)
    if p.z:
        cor = aplicar_feridas(cor, P, p, _c((150, 40, 40)), _c((90, 10, 12)))
    A[reg[1]:reg[1] + h, reg[0]:reg[0] + w] = cor


def pintar_bota(p, A, s):
    lado = "esq" if s < 0 else "dir"
    bota = _c(p.cor["bota"])
    # cano
    reg = R["cano_" + lado]
    x0, y0, w, h = reg
    n = fbm(h, w, 8, 71)
    A[y0:y0 + h, x0:x0 + w] = bota[None, None, :] * (0.85 + 0.30 * n[..., None]) * np.array([1.0, 0.96, 0.92])
    # pe: v = ao longo de Z (1 = ponta); u = em volta; sola nos 20% de baixo da secao
    reg = R["pe_" + lado]
    x0, y0, w, h = reg
    u = (np.arange(w) + 0.5) / w
    v = 1.0 - (np.arange(h) + 0.5) / h
    U, V = np.meshgrid(u, v)
    phi = U * 2 * math.pi
    n = fbm(h, w, 7, 72)
    couro = bota[None, None, :] * (0.92 + 0.16 * n[..., None])
    # phi ~ pi = parte de baixo da secao
    baixo = suave((np.cos(phi) * -1 - 0.62) / 0.06)
    sola = _c(p.cor["sola"])[None, None, :] * (0.9 + 0.2 * ruido(h, w, 1, 73)[..., None])
    cor = couro * (1 - baixo[..., None]) + sola * baixo[..., None]
    # frisos (welt) claro entre couro e sola e bico reforcado
    fris = (np.abs(np.cos(phi) * -1 - 0.64) < 0.02)
    cor = np.where(fris[..., None], _c((150, 118, 80)) if not p.z else _c((90, 80, 66)), cor)
    bico = suave((V - 0.86) / 0.03)
    cor = cor * (1 - 0.10 * bico[..., None])
    # riscos e marcas de uso
    ris = suave((ruido(h, w, 2, 74) - 0.90) / 0.05)
    cor = cor * (1 - 0.18 * ris[..., None])
    if p.z:
        cor = respingos(cor, h, w, 75, 0.10, p)
    A[y0:y0 + h, x0:x0 + w] = cor


def pintar_olho(p, A):
    x0, y0, w, h = R["olho"]
    i, j = np.mgrid[0:h, 0:w]
    px = (j + 0.5) / w - 0.5
    py = 0.5 - (i + 0.5) / h
    r = np.hypot(px, py)
    z = p.z
    escl = _c((206, 200, 150)) if z else _c((240, 238, 232))
    cor = np.tile(escl, (h, w, 1))
    cor = cor * (1 - 0.35 * suave((r - 0.38) / 0.12)[..., None]) + suave((r - 0.40) / 0.1)[..., None] * 0.1 * _c((200, 80, 80))
    ir_r = 0.235
    ang = np.arctan2(py, px)
    estr = 0.5 + 0.5 * np.sin(ang * 22 + ruido(h, w, 3, 81) * 4)
    ir_ext = _c((150, 158, 140)) if z else _c((86, 56, 34))
    ir_int = _c((170, 168, 150)) if z else _c((150, 108, 60))
    t = np.clip(r / ir_r, 0, 1)
    ir = ir_ext[None, None, :] * (1 - t[..., None]) * 0 + (ir_int[None, None, :] * (1 - t[..., None]) + ir_ext[None, None, :] * t[..., None])
    ir = ir * (0.85 + 0.25 * estr[..., None])
    dentro = (r < ir_r)[..., None]
    cor = np.where(dentro, ir, cor)
    limbo = suave((r - ir_r + 0.03) / 0.03) * (r < ir_r + 0.01)
    cor = cor * (1 - 0.55 * limbo[..., None])
    pupila = r < (0.075 if z else 0.105)
    cor = np.where(pupila[..., None], _c((14, 12, 12)), cor)
    br = np.hypot(px + 0.075, py - 0.085) < 0.034
    cor = np.where(br[..., None], _c((250, 250, 250)), cor)
    if z:
        veias = suave((ruido(h, w, 3, 82) - 0.70) / 0.06) * (r > ir_r)
        cor = cor * (1 - 0.6 * veias[..., None]) + veias[..., None] * 0.6 * _c((160, 30, 30))
    A[y0:y0 + h, x0:x0 + w] = cor


def pintar_cabelo(p, A):
    x0, y0, w, h = R["cabelo"]
    base = _c(p.cor["cabelo"])
    u = (np.arange(w) + 0.5) / w
    v = 1.0 - (np.arange(h) + 0.5) / h
    U, V = np.meshgrid(u, v)
    # fios: faixas verticais finas (ao longo da mecha) com brilho no meio e ponta mais clara
    fio = ruido(h, w, 1, 91)
    faixa = np.repeat(ruido(1, w, 2, 92), h, axis=0)
    brilho = np.exp(-(((V - 0.5) / 0.3) ** 2))
    cor = base[None, None, :] * (0.55 + 0.9 * faixa[..., None]) * (0.85 + 0.3 * fio[..., None])
    cor = cor * (0.6 + 0.4 * suave(V / 0.3)[..., None] * 0 + 0.4 * (1 - suave((V - 0.7) / 0.3))[..., None] + 0.2)
    cor = cor + brilho[..., None] * 0.06 * np.array([1.0, 0.85, 0.7]) * faixa[..., None]
    cor = cor * (1 - 0.35 * suave((V - 0.85) / 0.15)[..., None])   # raiz mais escura
    cor = cor * (1 + 0.12 * suave((0.2 - V) / 0.2)[..., None])
    A[y0:y0 + h, x0:x0 + w] = cor


def pintar_tiles(p, A):
    c = p.cor
    z = p.z
    rs = np.random.RandomState(100)

    def tile(nome, cor, rug=0.06, fade=0.05):
        x0, y0, w, h = TILE[nome]
        n = ruido(h, w, 1, hash(nome) % 1000)
        f = fbm(h, w, 12, (hash(nome) + 7) % 1000)
        A[y0:y0 + h, x0:x0 + w] = _c(cor)[None, None, :] * (1 + rug * (n[..., None] - 0.5) + fade * 2 * (f[..., None] - 0.5))

    tile("camisa", c["camisa"], 0.06, 0.10)
    tile("jeans", c["jeans"], 0.06, 0.10)
    tile("couro", c["couro"], 0.12, 0.12)
    tile("metal", (176, 170, 158) if not z else (120, 112, 100), 0.10, 0.12)
    tile("bota", c["bota"], 0.10, 0.12)
    tile("sola", c["sola"], 0.10, 0.05)
    tile("cadarco", c["cadarco"], 0.12, 0.05)
    tile("pele", c["pele"], 0.04, 0.06)
    tile("labio", (176, 98, 98) if not z else (104, 76, 84), 0.05, 0.08)
    tile("cabelo_t", c["cabelo"], 0.12, 0.10)
    tile("botao", (50, 44, 40), 0.05, 0.05)
    tile("escuro", (38, 18, 18), 0.05, 0.05)
    tile("dente", (232, 226, 206) if not z else (190, 176, 120), 0.06, 0.08)
    tile("sangue", (118, 10, 12), 0.08, 0.1)
    tile("carne", (150, 40, 42), 0.08, 0.1)
    tile("tecido", c["camisa"], 0.08, 0.1)
    tile("unha", (226, 196, 184) if not z else (170, 170, 140), 0.04, 0.05)
    tile("bota_clara", (140, 98, 64), 0.1, 0.1)
    # trama de denim nos tiles de tecido
    for nome, base in (("camisa", c["camisa"]), ("jeans", c["jeans"])):
        x0, y0, w, h = TILE[nome]
        A[y0:y0 + h, x0:x0 + w] = denim(h, w, _c(base), 7, 0.10)
    x0, y0, w, h = TILE["cadarco"]
    i, j = np.mgrid[0:h, 0:w]
    A[y0:y0 + h, x0:x0 + w] *= (0.85 + 0.15 * np.sin(j * 1.2))[..., None]


def pintar(p):
    A = p.atlas
    A[:] = _c((120, 120, 120))
    pintar_tiles(p, A)
    pintar_olho(p, A)
    pintar_cabelo(p, A)
    if "cabeca" in p.tubos:   # F14: a cabeca realista tem atlas proprio
        pintar_cabeca(p, A)
    pintar_tronco(p, A)
    for s in (-1, 1):
        pintar_braco(p, A, s)
        pintar_mao(p, A, s)
        pintar_perna(p, A, s)
        pintar_bota(p, A, s)
    return A


def salvar(A, caminho):
    img = Image.fromarray((np.clip(A, 0, 1) * 255).astype(np.uint8))
    img.save(caminho, optimize=True)
