# ferramentas/hd/texturas_garoto.py
"""F9: pinta o atlas do garoto. A sujeira/AO/curvatura vem do relevo da ALTA (kit.FINO=True) = 'baked' no difuso."""
import math

import numpy as np

import kit
import texturas as T
from garoto import TEARS, barra_u, gap_frente, CASACO_Y0, CASACO_Y1
from kit import fbm, ruido, suave
from personagem import R, TILE, _c
from texturas import grade, sombrear


def box(a, r):
    """Desfoque de caixa separavel (envolve em theta, borda em y)."""
    k = 2 * r + 1
    p = np.pad(a, ((0, 0), (r, r)), mode="wrap")
    c = np.cumsum(np.pad(p, ((0, 0), (1, 0))), axis=1)
    a = (c[:, k:] - c[:, :-k]) / k
    p = np.pad(a, ((r, r), (0, 0)), mode="edge")
    c = np.cumsum(np.pad(p, ((1, 0), (0, 0))), axis=0)
    return (c[k:] - c[:-k]) / k


def relevo(t, TH, Y, ganho=16.0, r=4):
    """Cavidade (AO) e curvatura a partir do relevo da ALTA. Retorna (d, cav)."""
    d = t.sombra(TH, Y)
    cav = d - box(d, r)
    return d, cav * ganho


def assar(cor, cav, forca=1.0):
    f = np.clip(1.0 + forca * cav, 0.55, 1.28)
    return cor * f[..., None]


def pano(h, w, base, sem, mot=0.20):
    i, j = np.mgrid[0:h, 0:w]
    tw = 0.5 + 0.5 * np.sin(i * 2 * math.pi / 3.0) * np.sin(j * 2 * math.pi / 3.0)
    f = 1.0 + 0.05 * (tw - 0.5) + mot * (fbm(h, w, 24, sem) - 0.5) + 0.08 * (fbm(h, w, 5, sem + 4) - 0.5)
    return base[None, None, :] * f[..., None]


def manchas(cor, h, w, sem, quant=1.0):
    m1 = suave((fbm(h, w, 30, sem, 5) - 0.50) / 0.25)
    cor = cor * (1 - 0.30 * quant * m1[..., None]) + 0.30 * quant * m1[..., None] * _c((66, 56, 44))
    m2 = suave((fbm(h, w, 22, sem + 50, 5) - 0.58) / 0.20)
    cor = cor * (1 - 0.14 * m2[..., None]) + 0.14 * m2[..., None] * _c((160, 146, 122))
    return cor


def tiles_extra(p, A):
    def tile(nome, cor, rug=0.10, fade=0.10, sem=1):
        x0, y0, w, h = TILE[nome]
        A[y0:y0 + h, x0:x0 + w] = pano(h, w, _c(cor), sem, fade * 2) * (1 + rug * (ruido(h, w, 1, sem + 3)[..., None] - 0.5))
    tile("casaco", p.cor["casaco"], 0.08, 0.20, 301)
    x0, y0, w, h = TILE["casaco"]
    A[y0:y0 + h, x0:x0 + w] = manchas(A[y0:y0 + h, x0:x0 + w], h, w, 311, 0.8)
    tile("forro", (54, 46, 40), 0.10, 0.12, 321)
    tile("tira", (62, 46, 34), 0.14, 0.14, 331)
    tile("bolsa", (98, 68, 42), 0.14, 0.18, 341)
    tile("brilho", (255, 190, 140), 0.05, 0.10, 351)
    tile("fio", (192, 178, 150), 0.10, 0.06, 361)
    tile("sujo", (70, 58, 46), 0.10, 0.10, 371)


def sardas(p, A):
    t = p.tubos["cabeca"]
    reg = R["cabeca"]
    TH, Y, P, d, U, V = grade(t, reg)
    h, w = U.shape
    x, y, z = P[..., 0], P[..., 1], P[..., 2]
    cor = A[reg[1]:reg[1] + h, reg[0]:reg[0] + w].copy()
    frente = suave((-z - 0.02) / 0.05)
    # sardas em volta do nariz e macas do rosto
    zona = np.exp(-(((np.abs(x) - 0.040) / 0.030) ** 2 + ((y - 1.652) / 0.026) ** 2)) * frente
    pontos = ruido(h, w, 1, 411) > 0.80
    cor = np.where((pontos * (zona > 0.25))[..., None], cor * np.array([0.80, 0.72, 0.68]), cor)
    # palidez e olheiras fundas, labios rachados
    for s in (-1, 1):
        ol = np.exp(-(((x - 0.033 * s) / 0.020) ** 2 + ((y - 1.684) / 0.013) ** 2)) * frente
        cor = cor * (1 - 0.22 * ol[..., None]) + ol[..., None] * 0.10 * _c((90, 70, 100))
    # sujeira: fuligem na testa/bochechas, arranhoes finos
    suj = suave((fbm(h, w, 14, 421, 4) - 0.55) / 0.08) * frente
    cor = cor * (1 - 0.30 * suj[..., None]) + 0.30 * suj[..., None] * _c((84, 72, 62))
    arr = (np.abs(np.sin(TH * 26 + Y * 90) * 0.5 + (y - 1.66) * 12) < 0.02) & (np.abs(x - 0.045) < 0.02) & (y > 1.62) & (y < 1.69) & (z < -0.04)
    cor = np.where(arr[..., None], cor * np.array([1.0, 0.7, 0.65]), cor)
    d2, cav = relevo(t, TH, Y, 10.0, 3)
    A[reg[1]:reg[1] + h, reg[0]:reg[0] + w] = assar(cor, cav, 0.6)


def pintar_tronco(p, A):
    t = p.tubos["tronco"]
    reg = R["tronco"]
    TH, Y, P, d, U, V = grade(t, reg)
    h, w = U.shape
    x, y, z = P[..., 0], P[..., 1], P[..., 2]
    cam = pano(h, w, _c(p.cor["camisa"]), 501, 0.30)
    cal = pano(h, w, _c(p.cor["jeans"]), 502, 0.30)
    cam = manchas(cam, h, w, 503, 0.8)
    # gola em V da camisa
    frente = (z < -0.03)
    v = (np.abs(x) < (1.50 - Y) * 0.30) & frente & (Y > 1.40)
    cam = np.where(v[..., None], cam * 0.7 + _c((150, 120, 104)) * 0.3, cam)
    cor = np.where((Y < 1.0)[..., None], cal, cam)
    cor = np.where((np.abs(Y - 1.0) < 0.002)[..., None], cor * 0.6, cor)
    d, cav = relevo(t, TH, Y, 14.0, 3)
    cor = assar(sombrear(cor, d, 14.0), cav)
    A[reg[1]:reg[1] + h, reg[0]:reg[0] + w] = cor


def pintar_casaco(p, A):
    t = p.tubos["casaco"]
    reg = R["casaco"]
    TH, Y, P, d, U, V = grade(t, reg)
    h, w = U.shape
    base = _c(p.cor["casaco"])
    cor = pano(h, w, base, 601, 0.26)
    cor = manchas(cor, h, w, 611, 1.0)
    # lama e umidade na parte de baixo; desbotado nos ombros
    lama = suave((0.98 - Y) / 0.35) * (0.45 + 0.55 * fbm(h, w, 10, 621, 4))
    cor = cor * (1 - 0.50 * lama[..., None]) + 0.50 * lama[..., None] * _c((74, 60, 46))
    cor = cor * (1 + 0.10 * suave((Y - 1.30) / 0.15)[..., None])
    # remendos costurados
    for (tc, yc, a, b, c_) in ((2.0, 1.00, 0.34, 0.085, (108, 100, 84)), (4.6, 0.84, 0.28, 0.075, (146, 130, 104)),
                               (0.9, 1.26, 0.26, 0.065, (98, 94, 82))):
        inx = (np.abs(TH - tc) < a) & (np.abs(Y - yc) < b)
        cor = np.where(inx[..., None], pano(h, w, _c(c_), 640 + int(tc * 10), 0.3), cor)
        borda = ((np.abs(np.abs(TH - tc) - a * 0.9) < 0.012) & (np.abs(Y - yc) < b * 0.95)) | \
                ((np.abs(np.abs(Y - yc) - b * 0.85) < 0.0025) & (np.abs(TH - tc) < a * 0.95))
        dash = np.mod(Y / 0.0045 + TH * 8, 1.0) < 0.62
        cor = np.where((borda & dash)[..., None], _c((196, 182, 150)) * 0.8, cor)
        sombra = (np.abs(TH - tc) < a * 1.05) & (np.abs(Y - yc) < b * 1.1) & ~inx
        cor = np.where(sombra[..., None], cor * 0.82, cor)
    # costuras verticais (costas e laterais)
    dash = np.mod(Y / 0.0045, 1.0) < 0.70
    for th0 in (math.pi, math.pi / 2, 3 * math.pi / 2):
        dth = ((TH - th0 + math.pi) % (2 * math.pi)) - math.pi
        m = (np.abs(dth) < 0.012) & dash & (Y > 0.55) & (Y < 1.42)
        cor = np.where(m[..., None], _c((172, 156, 124)) * 0.8, cor)
    # rasgos com forro escuro e bordas desfiadas
    for k, (tc, yc, a, b) in enumerate(TEARS):
        dd = np.sqrt(((TH - tc) / a) ** 2 + ((Y - yc) / b) ** 2) + 0.35 * (fbm(h, w, 5, 700 + k, 3) - 0.5)
        dentro = dd < 1.0
        cor = np.where(dentro[..., None], _c((46, 38, 32)) * (0.8 + 0.4 * fbm(h, w, 4, 710 + k)[..., None]), cor)
        franja = (dd >= 1.0) & (dd < 1.22)
        cor = np.where(franja[..., None], _c((158, 144, 118)) * (0.7 + 0.5 * ruido(h, w, 1, 720 + k)[..., None]), cor)
        halo = suave((1.7 - dd) / 0.5) * (dd >= 1.22)
        cor = cor * (1 - 0.35 * halo[..., None])
    # barra rasgada: franja desfiada clara + sombra
    g = gap_frente(Y)
    u = (TH - g) / (2 * math.pi - 2 * g)
    dist = Y - barra_u(np.clip(u, 0, 1))
    fr = (dist < 0.014) * (0.6 + 0.4 * ruido(h, w, 1, 731))
    cor = cor * (1 - fr[..., None]) + fr[..., None] * _c((170, 156, 128))
    cor = cor * (1 - 0.30 * suave((0.05 - dist) / 0.05)[..., None] * suave(dist / 0.01)[..., None])
    # bordas da abertura frontal
    cor = np.where(((u < 0.012) | (u > 0.988))[..., None], cor * 1.15, cor)
    d, cav = relevo(t, TH, Y, 18.0, 4)
    cor = assar(sombrear(cor, d, 16.0), cav)
    A[reg[1]:reg[1] + h, reg[0]:reg[0] + w] = cor


def pintar_braco(p, A, s):
    nome = "braco_esq" if s < 0 else "braco_dir"
    t = p.tubos[nome]
    reg = R[nome]
    TH, Y, P, d, U, V = grade(t, reg)
    h, w = U.shape
    pele = _c(p.cor["pele"])
    sk = pele[None, None, :] * (0.92 + 0.16 * fbm(h, w, 18, 31)[..., None]) * (1 - 0.06 * (ruido(h, w, 1, 32)[..., None] - 0.5))
    suj = suave((fbm(h, w, 10, 33, 4) - 0.52) / 0.08) * suave((0.95 - Y) / 0.1)
    sk = sk * (1 - 0.35 * suj[..., None]) + 0.35 * suj[..., None] * _c((88, 74, 62))
    tec = manchas(pano(h, w, _c(p.cor["casaco"]), 801 + int(s), 0.26), h, w, 811 + int(s), 1.0)
    lama = suave((1.05 - Y) / 0.2) * fbm(h, w, 8, 821, 3)
    tec = tec * (1 - 0.4 * lama[..., None]) + 0.4 * lama[..., None] * _c((74, 60, 46))
    lim = 0.905 + 0.012 * (np.interp(TH[0], np.linspace(0, 2 * math.pi, 24), ruido(1, 24, 2, 41 + int(s))[0]))[None, :] - 0.006
    cor = np.where((Y > lim)[..., None], tec, sk)
    fr = (np.abs(Y - lim) < 0.008) * (Y > lim - 0.004)
    cor = np.where(fr[..., None], _c((168, 154, 126)), cor)
    # remendo no cotovelo
    inx = (np.abs(((TH - (math.pi if s > 0 else 0.0)) + math.pi) % (2 * math.pi) - math.pi) < 0.5) & (np.abs(Y - 1.18) < 0.06)
    cor = np.where(inx[..., None], pano(h, w, _c((110, 100, 84)), 831, 0.3), cor)
    dash = np.mod(Y / 0.0045, 1.0) < 0.62
    cor = np.where(((np.abs(Y - 1.118) < 0.0014) & (inx | (np.abs(((TH - (math.pi if s > 0 else 0.0)) + math.pi) % (2 * math.pi) - math.pi) < 0.5)) & dash)[..., None], _c((190, 176, 148)) * 0.8, cor)
    d, cav = relevo(t, TH, Y, 16.0, 4)
    A[reg[1]:reg[1] + h, reg[0]:reg[0] + w] = assar(sombrear(cor, d, 18.0), cav)


def pintar_perna(p, A, s):
    nome = "perna_esq" if s < 0 else "perna_dir"
    t = p.tubos[nome]
    reg = R[nome]
    TH, Y, P, d, U, V = grade(t, reg)
    h, w = U.shape
    cor = manchas(pano(h, w, _c(p.cor["jeans"]), 901 + int(s), 0.30), h, w, 911 + int(s), 1.0)
    lama = suave((0.40 - Y) / 0.30) * (0.4 + 0.6 * fbm(h, w, 9, 921 + int(s), 4))
    cor = cor * (1 - 0.55 * lama[..., None]) + 0.55 * lama[..., None] * _c((70, 58, 46))
    cl = np.exp(-(((Y - 0.52) / 0.07) ** 2)) * (np.cos(TH) < 0)
    cor = cor * (1 + 0.14 * cl[..., None])
    dash = np.mod(Y / 0.0045, 1.0) < 0.66
    for th0 in (math.pi / 2, 3 * math.pi / 2):
        m = (np.abs(TH - th0) < 0.012) & dash & (Y > 0.2)
        cor = np.where(m[..., None], _c((176, 160, 126)) * 0.75, cor)
    # bolso cargo com pesponto
    th0 = math.pi / 2 if s > 0 else 3 * math.pi / 2
    pk = (np.abs(TH - th0) < 0.38) & (Y > 0.60) & (Y < 0.74)
    bd = ((np.abs(np.abs(TH - th0) - 0.34) < 0.012) & (Y > 0.61) & (Y < 0.73)) | ((np.abs(Y - 0.628) < 0.0016) & (np.abs(TH - th0) < 0.35))
    cor = np.where((bd & dash)[..., None], _c((186, 172, 140)) * 0.8, cor)
    cor = np.where((pk & ~bd)[..., None], cor * 0.94, cor)
    if s < 0:  # ferida brilhante no joelho esquerdo
        dd = np.sqrt(((TH - math.pi) / 0.42) ** 2 + ((Y - 0.60) / 0.14) ** 2) + 0.30 * (fbm(h, w, 6, 931, 3) - 0.5)
        rr = np.clip(dd, 0, 1)
        carne = _c((255, 214, 170)) * (1 - rr[..., None]) + _c((206, 112, 92)) * rr[..., None]
        veias = suave((fbm(h, w, 5, 941, 4) - 0.55) / 0.08)
        carne = carne * (1 - 0.25 * veias[..., None]) + 0.25 * veias[..., None] * _c((150, 50, 50))
        franja = (dd >= 1.0) & (dd < 1.20)
        cor = np.where(franja[..., None], _c((150, 134, 108)) * (0.7 + 0.5 * ruido(h, w, 1, 951)[..., None]), cor)
        queim = suave((1.6 - dd) / 0.4) * (dd >= 1.20)
        cor = cor * (1 - 0.40 * queim[..., None])
        halo = np.exp(-(np.clip(dd - 1.0, 0, None) / 0.35) ** 2) * (dd >= 1.0)
        cor = cor + halo[..., None] * 0.22 * _c((255, 160, 100))
        cor = np.where((dd < 1.0)[..., None], carne, cor)
    d, cav = relevo(t, TH, Y, 16.0, 4)
    cor = assar(sombrear(cor, d, 16.0), cav)
    if s < 0:  # o brilho nao escurece com o AO: devolve a luz dentro da ferida
        dd2 = np.sqrt(((TH - math.pi) / 0.42) ** 2 + ((Y - 0.60) / 0.14) ** 2)
        cor = np.where((dd2 < 0.85)[..., None], cor * 0.4 + 0.6 * np.clip(_c((255, 214, 170)) * (1.05 - 0.4 * dd2[..., None]), 0, 1), cor)
    A[reg[1]:reg[1] + h, reg[0]:reg[0] + w] = cor


def pintar(p):
    kit.FINO = True
    try:
        A = p.atlas
        A[:] = _c((120, 120, 120))
        T.pintar_tiles(p, A)
        tiles_extra(p, A)
        T.pintar_olho(p, A)
        T.pintar_cabelo(p, A)
        T.pintar_cabeca(p, A)
        sardas(p, A)
        pintar_tronco(p, A)
        pintar_casaco(p, A)
        for s in (-1, 1):
            pintar_braco(p, A, s)
            T.pintar_mao(p, A, s)
            pintar_perna(p, A, s)
            T.pintar_bota(p, A, s)
    finally:
        kit.FINO = False
    return A
